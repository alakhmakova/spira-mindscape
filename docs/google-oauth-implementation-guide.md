# Google OAuth Login — Implementation Guide

> **Purpose:** To understand how Google
> sign-in is implemented in this project and reproduce it in another Spring Boot + React application.
>
> **Stack:** Java 17, Spring Boot 3, Spring Security OAuth2/OIDC, PostgreSQL, Flyway,
> React 19, Vite, Zustand, TanStack Router.

---

## Table of contents

1. [How OAuth2 / OIDC works](#1-how-oauth2--oidc-works)
2. [Architecture overview](#2-architecture-overview)
3. [Backend — step-by-step](#3-backend--step-by-step)
4. [Frontend — step-by-step](#4-frontend--step-by-step)
5. [Security guarantees](#5-security-guarantees)
6. [Data isolation — how it works](#6-data-isolation--how-it-works)
7. [Tests](#7-tests)
8. [Making it run — what you need to do](#8-making-it-run--what-you-need-to-do)
9. [Checklist for a new project](#9-checklist-for-a-new-project)

---

## 1. How OAuth2 / OIDC works

### Key terms

| Term | Meaning |
|------|-----------------------|
| **OAuth2** | A protocol that lets users grant your app access to their account at a third party (Google, GitHub, …) without giving you their password. |
| **OIDC (OpenID Connect)** | An extension of OAuth2 that adds a standard way to get the user's identity — name, email, profile picture — as a signed **ID Token** (JWT). |
| **Authorization Code Flow** | The web-app flow. The browser only receives a short-lived "code". The server exchanges that code for tokens directly with Google — the browser never sees a secret token. |
| **Server-side session** | After login, the server creates a session and sends `JSESSIONID` as an `HttpOnly` cookie. The browser sends this cookie automatically on every request. JavaScript cannot read it (XSS protection). |
| **CSRF** | Cross-Site Request Forgery — a browser attack where another website tricks the browser into sending a request to your site using the victim's cookies. See [Section 5](#5-security-guarantees). |

### Login flow step by step

```
Browser           Vite (proxy)       Spring Boot            Google
  │                    │                  │                     │
  │─ click "Sign in" ─►│                  │                     │
  │  GET /oauth2/      │                  │                     │
  │  authorization/    │─ proxy ─────────►│                     │
  │  google            │                  │─ 302 redirect ─────►│
  │◄───────────────────┼──────────────────┼── redirect ─────────│
  │                    │                  │                     │
  │─ user logs in ────────────────────────────────────────────►│
  │◄───────────────────────────────────── redirect + code ──────│
  │                    │                  │                     │
  │─ GET /login/oauth2/│                  │                     │
  │   code/google?code=│─ proxy ─────────►│                     │
  │                    │                  │─ exchange code ─────►│
  │                    │                  │◄── ID Token (JWT) ───│
  │                    │                  │                     │
  │                    │                  │ parse ID Token       │
  │                    │                  │ upsert AppUser in DB │
  │                    │                  │ create session       │
  │                    │                  │                     │
  │◄───────────────────│◄── 302 → / ──────│                     │
  │                    │                  │                     │
  │─ GET /api/auth/me ─│─ proxy ─────────►│                     │
  │◄── {id,email,…} ───│◄── 200 JSON ─────│                     │
```

---

## 2. Architecture overview

### Design principles

1. **Server-managed session** — Spring owns the session. No JWT in `localStorage`, no XSS exposure.
2. **Single origin in dev** — Vite proxies all backend routes. The browser always talks to `localhost:5173`. No CORS issues with cookies.
3. **Per-user data isolation** — every goal row has a `user_id` FK. Fetching another user's goal by ID returns "not found", not "forbidden". The caller cannot tell whether the object exists at all.
4. **CSRF protection** — Spring writes a readable `XSRF-TOKEN` cookie; the client echoes it in the `X-XSRF-TOKEN` request header for every mutating call.

### New files — backend

```
backend/src/main/java/…/
├── auth/
│   ├── AppUser.java                — JPA entity, table app_user
│   ├── AppUserRepository.java      — JPA repository
│   ├── AppUserService.java         — find-or-create user from OIDC claims
│   ├── AppUserOidcUser.java        — custom principal: wraps OidcUser + AppUser
│   ├── AppUserOidcUserService.java — custom OAuth2UserService hook
│   ├── CurrentUserProvider.java    — reads AppUser from SecurityContextHolder
│   ├── AuthController.java         — GET /api/auth/me
│   └── UserDto.java                — JSON response shape
└── config/
    └── SecurityConfig.java         — all security rules in one place

backend/src/main/resources/db/migration/
├── V7__app_user.sql                — creates app_user table
└── V8__goal_owner.sql              — adds user_id FK to goal table
```

### New files — frontend

```
src/
├── lib/spira/auth.ts    — Zustand auth store: user, status, fetchMe(), logout()
└── routes/login.tsx     — login page with "Continue with Google" button
```

### Modified files (summary)

| File | What changed |
|------|-------------|
| `backend/pom.xml` | +3 dependencies: security, oauth2-client, security-test |
| `application.properties` | OAuth2 client config, frontend URL, cookie settings |
| `goal/Goal.java` | Added `@ManyToOne AppUser user` field |
| `goal/GoalRepository.java` | Added `findByUserId…` and `findByIdAndUserId` methods |
| `goal/GoalService.java` | Every DB query now scoped by `userId` |
| `target/TargetService.java` | Uses `goalService.findById()` instead of repository directly |
| `reality/RealityService.java` | Same pattern |
| `resource/ResourceService.java` | Same pattern |
| `vite.config.ts` | Proxy `/api`, `/oauth2`, `/login` to backend |
| `src/lib/spira/api.ts` | Added `credentials: "include"` and `X-XSRF-TOKEN` header |
| `src/lib/spira/store.ts` | On 401 → `window.location.replace("/login")` |
| `src/routes/__root.tsx` | Auth guard in `beforeLoad`, login page bypasses AppShell |
| `src/components/shell/AppShell.tsx` | Real user avatar/name + sign-out dropdown |

---

## 3. Backend — step-by-step

### Step 1 — Maven dependencies (`pom.xml`)

```xml
<!-- Spring Security: filters, sessions, CSRF -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- OAuth2 Client: Authorization Code Flow, OIDC token parsing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Test only: csrf(), authentication() MockMvc helpers -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

### Step 2 — `application.properties`

```properties
# Where to redirect the browser after a successful login
app.frontend.url=${FRONTEND_URL:http://localhost:5173}

# Google OAuth2 credentials — injected from environment variables at runtime.
# NEVER hard-code real values here; never commit them.
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
# Request the user's identity, email address and profile picture
spring.security.oauth2.client.registration.google.scope=openid,email,profile

# Session cookie settings
server.servlet.session.cookie.same-site=lax    # blocks cross-site cookie sending
server.servlet.session.cookie.http-only=true    # JS cannot read the session cookie
```

`${GOOGLE_CLIENT_ID}` is a Spring placeholder — the value comes from the `GOOGLE_CLIENT_ID`
environment variable at runtime. If the variable is not set and there is no default,
the app will refuse to start.

---

### Step 3 — Flyway migrations

**`V7__app_user.sql`**
```sql
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    google_sub    TEXT        NOT NULL,  -- Google's immutable user ID; never changes
    email         TEXT        NOT NULL,  -- may change; google_sub is the real identity key
    name          TEXT,
    picture_url   TEXT,
    role          TEXT        NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_app_user_google_sub ON app_user (google_sub);
CREATE UNIQUE INDEX idx_app_user_email      ON app_user (email);
```

**Why `google_sub` and not `email` as the identity key?**  
A user can change their email address on their Google account. `sub` (subject) is
Google's internal identifier and never changes. Always look users up by `sub`.

**`V8__goal_owner.sql`**
```sql
-- Rows without an owner cannot satisfy NOT NULL, so wipe the table first.
-- (In production you would backfill with a system/migration user instead.)
DELETE FROM goal;

ALTER TABLE goal
    ADD COLUMN user_id BIGINT NOT NULL
        REFERENCES app_user (id) ON DELETE CASCADE;
-- ON DELETE CASCADE: deleting a user automatically deletes all their goals.

CREATE INDEX idx_goal_user_id ON goal (user_id);
```

---

### Step 4 — `AppUser` entity

```java
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;
    private String pictureUrl;

    @Column(nullable = false)
    private String role = "USER";

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    @PrePersist  void onCreate()  { createdAt = updatedAt = Instant.now(); }
    @PreUpdate   void onUpdate()  { updatedAt = Instant.now(); }
    // getters / setters omitted for brevity
}
```

---

### Step 5 — `AppUserService`: find-or-create

```java
@Service
@RequiredArgsConstructor
public class AppUserService {
    private final AppUserRepository appUserRepository;

    public AppUser findOrCreateFromOidc(OidcUser oidcUser) {
        String sub   = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name  = oidcUser.getFullName();
        String pic   = oidcUser.getPicture();

        return appUserRepository.findByGoogleSub(sub)
                .map(existing -> {
                    // Returning user: refresh any fields that may have changed in Google
                    existing.setEmail(email);
                    existing.setName(name);
                    existing.setPictureUrl(pic);
                    existing.setLastLoginAt(Instant.now());
                    return appUserRepository.save(existing);
                })
                .orElseGet(() -> {
                    // First login: create a new row
                    AppUser user = new AppUser();
                    user.setGoogleSub(sub);
                    user.setEmail(email);
                    user.setName(name);
                    user.setPictureUrl(pic);
                    user.setLastLoginAt(Instant.now());
                    return appUserRepository.save(user);
                });
    }
}
```

---

### Step 6 — `AppUserOidcUser`: custom principal

Spring Security stores the authenticated user as a `Principal` object. The built-in
`DefaultOidcUser` contains only Google's claims. We need our own `AppUser` DB entity
to be available on every request without an extra database round-trip. The solution
is to wrap them together:

```java
// Implements OidcUser so Spring Security treats it as a proper OIDC principal
public class AppUserOidcUser implements OidcUser {
    private final OidcUser delegate;   // the standard Google principal
    private final AppUser appUser;     // our DB entity, pre-loaded at login time

    public AppUserOidcUser(OidcUser delegate, AppUser appUser) {
        this.delegate = delegate;
        this.appUser  = appUser;
    }

    /** The only method we add — everything else is delegated to the OidcUser. */
    public AppUser getAppUser() { return appUser; }

    // All OidcUser interface methods forward to delegate:
    @Override public Map<String, Object> getClaims()  { return delegate.getClaims(); }
    @Override public OidcIdToken getIdToken()          { return delegate.getIdToken(); }
    @Override public Map<String, Object> getAttributes(){ return delegate.getAttributes(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }
    @Override public String getName() { return delegate.getName(); }
    // … other OidcUser / OidcUserInfo methods
}
```

This object is stored in the session. Any service can reach it via
`SecurityContextHolder` without touching the database.

---

### Step 7 — `AppUserOidcUserService`: plugging into the OAuth2 flow

Spring Security calls this class after it has validated the ID Token from Google:

```java
@Service
@RequiredArgsConstructor
public class AppUserOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final AppUserService appUserService;
    private final OidcUserService delegate = new OidcUserService(); // Spring's built-in parser

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Parse and validate the OIDC token using the standard Spring mechanism
        OidcUser oidcUser = delegate.loadUser(userRequest);
        // 2. Upsert the user in our own database
        AppUser appUser = appUserService.findOrCreateFromOidc(oidcUser);
        // 3. Return a wrapper that carries both — Spring stores this in the session
        return new AppUserOidcUser(oidcUser, appUser);
    }
}
```

---

### Step 8 — `CurrentUserProvider`

Any service that needs to know "who is making this request" injects this component:

```java
@Component
public class CurrentUserProvider {
    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Java 17 pattern matching: checks the type and extracts the field in one step
        if (auth != null && auth.getPrincipal() instanceof AppUserOidcUser appUserOidcUser) {
            return appUserOidcUser.getAppUser();
        }
        throw new IllegalStateException("No authenticated AppUser in security context");
    }
}
```

---

### Step 9 — `SecurityConfig`

This is the central class. Every security rule lives here:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final AppUserOidcUserService appUserOidcUserService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF — double-submit cookie pattern.
        // Spring writes XSRF-TOKEN (HttpOnly=false so JS can read it).
        // The client must echo it in the X-XSRF-TOKEN request header.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null); // don't load token for safe methods

        http
            // ── URL authorization rules ──────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/oauth2/**", "/login/**").permitAll()  // OAuth2 handshake
                .requestMatchers("/api/health").permitAll()               // health probe
                .requestMatchers("/api/auth/me").permitAll()              // returns 401 itself
                .anyRequest().authenticated()                              // everything else: login required
            )

            // ── CSRF ─────────────────────────────────────────────────────────
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/api/health", "/oauth2/**", "/login/**")
            )

            // ── OAuth2 login ──────────────────────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.oidcUserService(appUserOidcUserService))
                .defaultSuccessUrl(frontendUrl, true)           // success → SPA home
                .failureUrl(frontendUrl + "/login?error")       // failure → login page
            )

            // ── Logout ────────────────────────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")          // POST here to log out
                .invalidateHttpSession(true)             // destroy session
                .deleteCookies("JSESSIONID")             // clear cookie
                .logoutSuccessHandler((req, res, auth) ->
                    res.setStatus(HttpServletResponse.SC_NO_CONTENT)) // 204, not 302
            )

            // ── 401 for API calls instead of a redirect to Google ────────────
            // Without this, Spring would redirect unauthenticated /graphql calls
            // to the Google login page. The SPA needs a 401 to detect "not logged in".
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    req -> req.getRequestURI().startsWith("/graphql")
                        || req.getRequestURI().startsWith("/api/")
                )
            );

        return http.build();
    }
}
```

---

### Step 10 — `AuthController` and `UserDto`

```java
// A Java record — immutable value object, auto-generates constructor, getters, equals/hashCode
public record UserDto(Long id, String email, String name, String pictureUrl) {
    public static UserDto from(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getPictureUrl());
    }
}

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /**
     * @AuthenticationPrincipal — Spring injects the current principal from the security
     * context. It will be null if the request is unauthenticated (because /api/auth/me
     * is in the permitAll list — Spring does not block anonymous access to it).
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AppUserOidcUser principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UserDto.from(principal.getAppUser()));
    }
}
```

---

## 4. Frontend — step-by-step

### Step 1 — Vite dev proxy

File: `vite.config.ts`

```typescript
server: {
    port: 5173,
    proxy: {
        // All backend routes go through the proxy.
        // The browser sees a single origin (localhost:5173), so session cookies work.
        "/graphql": { target: "http://localhost:8080", changeOrigin: true },
        "/api":     { target: "http://localhost:8080", changeOrigin: true },
        "/oauth2":  { target: "http://localhost:8080", changeOrigin: true },
        "/login":   { target: "http://localhost:8080", changeOrigin: true },
    },
},
```

Without the proxy the browser would make cross-origin requests (`5173 → 8080`). `SameSite=Lax`
cookies are not sent cross-origin, so the session would never reach the server.

`changeOrigin: true` rewrites the `Host` header so Spring Boot does not see a mismatch.

---

### Step 2 — `auth.ts` — Zustand auth store

File: `src/lib/spira/auth.ts`

```typescript
import { create } from "zustand";

export type AuthUser = { id: number; email: string; name: string; pictureUrl: string | null };
export type AuthStatus = "loading" | "authed" | "anonymous";

/** Reads the XSRF-TOKEN cookie that Spring writes (not HttpOnly). */
export function getCsrfToken(): string {
    if (typeof document === "undefined") return ""; // guard for Node.js / test environments
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : "";
}

export const useAuth = create<AuthStore>((set) => ({
    user: null,
    status: "loading",

    async fetchMe() {
        try {
            const res = await fetch("/api/auth/me", { credentials: "include" });
            if (!res.ok) { set({ user: null, status: "anonymous" }); return; }
            set({ user: await res.json() as AuthUser, status: "authed" });
        } catch {
            set({ user: null, status: "anonymous" }); // network error → show login
        }
    },

    async logout() {
        try {
            await fetch("/api/auth/logout", {
                method: "POST",
                credentials: "include",
                headers: { "X-XSRF-TOKEN": getCsrfToken() }, // required for POST
            });
        } finally {
            set({ user: null, status: "anonymous" });
        }
    },

    setAnonymous() { set({ user: null, status: "anonymous" }); },
}));
```

---

### Step 3 — Add credentials and CSRF to every GraphQL request

File: `src/lib/spira/api.ts`

```typescript
import { getCsrfToken } from "./auth";

// Before (no auth):
response = await fetch(GRAPHQL_ENDPOINT, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ query, variables }),
});

// After (with session cookie + CSRF header):
response = await fetch(GRAPHQL_ENDPOINT, {
    method: "POST",
    credentials: "include",            // send the JSESSIONID cookie
    headers: {
        "content-type": "application/json",
        "X-XSRF-TOKEN": getCsrfToken(), // echo the CSRF cookie in a header
    },
    body: JSON.stringify({ query, variables }),
});
```

`credentials: "include"` — without this flag `fetch` never sends cookies,
even for same-origin requests via the proxy.

---

### Step 4 — Auth guard in the root route

File: `src/routes/__root.tsx`

```typescript
export const Route = createRootRoute({
    beforeLoad: async ({ location }) => {
        if (location.pathname === "/login") return; // public route — skip

        const auth = useAuth.getState();
        // fetchMe() is called only once: status starts as "loading".
        // After the first check it becomes "authed" or "anonymous"
        // and subsequent navigations reuse the cached result.
        if (auth.status === "loading") {
            await auth.fetchMe();
        }
        if (useAuth.getState().status === "anonymous") {
            throw redirect({ to: "/login" });
        }
    },
    component: RootComponent,
});

function RootComponent() {
    const pathname = useRouterState({ select: (s) => s.location.pathname });

    // The login page must not render inside AppShell
    // (no nav bar, no goal loading, no auth-dependent UI)
    if (pathname === "/login") {
        return <><Outlet /><Toaster /></>;
    }

    return <AppShell><Outlet /><Toaster /></AppShell>;
}
```

---

### Step 5 — Login page

File: `src/routes/login.tsx`

```typescript
export const Route = createFileRoute("/login")({
    beforeLoad: async () => {
        // Already logged in? Send home immediately.
        const auth = useAuth.getState();
        if (auth.status === "loading") await auth.fetchMe();
        if (useAuth.getState().status === "authed") throw redirect({ to: "/" });
    },
    component: LoginPage,
});

function LoginPage() {
    return (
        <div className="flex min-h-screen">
            {/* Left panel: brand */}
            <div className="hidden lg:flex lg:w-1/2 bg-primary …">…</div>

            {/* Right panel: sign-in */}
            <div className="flex w-full lg:w-1/2 …">
                {/*
                  * This is a plain <a> tag, not a fetch() call.
                  * Spring Security intercepts /oauth2/authorization/google and
                  * starts the Authorization Code flow — the browser must follow
                  * a full redirect, not an XHR request.
                  */}
                <a href="/oauth2/authorization/google">
                    Continue with Google
                </a>

                {/* Show a message if the backend redirected back with ?error */}
                {window.location.search.includes("error") && (
                    <p>Sign-in failed. Please try again.</p>
                )}
            </div>
        </div>
    );
}
```

---

### Step 6 — Signed-in chrome in AppShell

File: `src/components/shell/AppShell.tsx`

```typescript
const authUser = useAuth((s) => s.user);
const logout   = useAuth((s) => s.logout);
const navigate  = useNavigate();

// Replace the hard-coded "SU" / "Spira User" placeholder with real data:
<DropdownMenu>
    <DropdownMenuTrigger>
        {authUser?.pictureUrl
            ? <img src={authUser.pictureUrl} referrerPolicy="no-referrer" />
            : <div>{getInitials(authUser?.name ?? "?")}</div>
        }
        <span>{authUser?.name}</span>
    </DropdownMenuTrigger>
    <DropdownMenuContent>
        <DropdownMenuLabel>
            <div>{authUser?.name}</div>
            <div>{authUser?.email}</div>
        </DropdownMenuLabel>
        <DropdownMenuItem onSelect={async () => {
            await logout();
            void navigate({ to: "/login" });
        }}>
            Sign out
        </DropdownMenuItem>
    </DropdownMenuContent>
</DropdownMenu>
```

---

## 5. Security guarantees

### HTTP layer — what is protected

| Route | Access | What happens if blocked |
|-------|--------|------------------------|
| `POST /graphql` | Authenticated only | 401 for anonymous callers |
| `GET /api/**` | Authenticated only | 401 for anonymous callers |
| `POST /api/**` (mutations) | Authenticated + valid CSRF token | 403 if CSRF token missing or wrong |
| `GET /api/auth/me` | Public | Returns 401 itself when no session |
| `POST /api/auth/logout` | Authenticated + CSRF | 204 after session is invalidated |
| `/oauth2/**`, `/login/**` | Public | OAuth2 handshake; no session needed |
| `/api/health` | Public | Health probe for load balancers |

### CSRF — double-submit cookie pattern

1. After login, Spring writes a cookie `XSRF-TOKEN` with `HttpOnly=false`.
2. The JavaScript client reads this cookie using `document.cookie`.
3. Every mutating request sends the value back as the `X-XSRF-TOKEN` header.
4. Spring compares the cookie value and the header value. If they match, the request is accepted.
5. An attacker on a different domain **cannot** read the cookie (enforced by the browser's
   same-origin policy) and therefore cannot forge the header.

### Why 401 instead of a redirect to Google for API calls?

By default Spring Security would redirect unauthenticated requests to the Google login
page (HTTP 302). A `fetch()` call in JavaScript would follow that redirect silently
and end up with the Google HTML page instead of an error — the app would not know the
session had expired.

`HttpStatusEntryPoint(UNAUTHORIZED)` makes Spring return `401 JSON` for `/graphql` and
`/api/**`, which the frontend can detect and handle (redirect to `/login`).

---

## 6. Data isolation — how it works

### Database level

Every `goal` row has a `user_id` column that is a non-nullable foreign key to `app_user`:

```sql
-- V8__goal_owner.sql
ALTER TABLE goal
    ADD COLUMN user_id BIGINT NOT NULL
        REFERENCES app_user (id) ON DELETE CASCADE;
```

`ON DELETE CASCADE` means: if a user account is deleted, all their goals (and all
child rows — targets, reality items, options, resources — through their own cascades)
are deleted automatically by the database.

### Repository level

`GoalRepository` exposes two user-scoped query methods:

```java
public interface GoalRepository extends JpaRepository<Goal, Long> {
    // Returns only the goals that belong to this user, sorted by creation date
    List<Goal> findByUserIdOrderByCreatedAtAsc(Long userId);

    // Returns a goal only if it belongs to this user.
    // Returns Optional.empty() if the goal doesn't exist OR belongs to someone else.
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
```

Spring Data JPA generates the SQL for both methods automatically from their names.

Generated SQL for `findByIdAndUserId`:
```sql
SELECT * FROM goal WHERE id = ? AND user_id = ?
```

### Service level — opaque not-found

`GoalService` always calls `findByIdAndUserId` and throws the same error for both
"not found" and "belongs to another user":

```java
public Goal findById(Long id) {
    Long userId = currentUserProvider.getCurrentUser().getId();
    return goalRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + id));
}
```

**Why the same error message?** If the error said "Access denied", an attacker could
enumerate goal IDs: try IDs 1–1000 and anything that says "Access denied" (instead of
"not found") reveals that a goal with that ID exists in the system — a data leak. The
"opaque not-found" pattern prevents this.

### Child entities — protection by indirection

Targets, reality items, options, and resources belong to goals — they have no `user_id`
of their own. Protection flows through the parent goal:

- `TargetService`, `RealityService`, `ResourceService` all inject **`GoalService`**
  (not `GoalRepository`) and call `goalService.findById(goalId)` before every mutation.
- `findById` already applies the `userId` filter.
- If the goal is not found or is owned by someone else, an error is thrown before
  any child data is touched.

```java
// Example from TargetService:
@Transactional
public Target createTarget(Long goalId, CreateTargetInput input) {
    Goal goal = goalService.findById(goalId); // ownership check happens here
    Target target = new Target();
    target.setGoal(goal);
    // … rest of the logic
}
```

This means **a user cannot add, update, read, or delete any child entity belonging to
another user's goal**, even if they know the exact ID.

---

## 7. Tests

### `AppUserServiceTest` — unit test (no Spring, no DB)

File: `backend/src/test/java/…/auth/AppUserServiceTest.java`  
Framework: JUnit 5 + Mockito

Tests the `findOrCreateFromOidc` business logic in isolation.

| Test | What it verifies |
|------|-----------------|
| `firstSignInCreatesNewUser` | New user is created with all fields from the OIDC token |
| `firstSignInLooksUpByGoogleSub` | Lookup uses `google_sub`, never `email` |
| `firstSignInAllowsNullPicture` | A user without a profile picture is allowed |
| `returningSignInRefreshesExistingUser` | On re-login: email, name, picture, lastLoginAt are updated |
| `returningSignInDoesNotDuplicate` | Re-login saves the *same* row, verified with `ArgumentCaptor` |
| `emailChangeWithSameSubUpdatesEmail` | Email update on the same row (same DB id) |

---

### `SecurityIntegrationTest` — HTTP security rules

File: `backend/src/test/java/…/graphql/SecurityIntegrationTest.java`  
Framework: `@SpringBootTest` + `@AutoConfigureMockMvc` + `spring-security-test`

> **Important:** `@AutoConfigureGraphQlTester` bypasses Spring Security HTTP filters and
> calls the GraphQL engine directly. To test "does Spring Security block unauthenticated
> requests?", you need `@AutoConfigureMockMvc`, which exercises the full HTTP filter chain.

| Test | What it verifies |
|------|-----------------|
| `anonymousGraphQlReturns401` | POST /graphql (with valid CSRF) but no session → 401 |
| `authenticatedGraphQlReturns200` | POST /graphql with session + CSRF → 200 |
| `getMeAnonymousReturns401` | GET /api/auth/me with no session → 401 |
| `getMeAuthenticatedReturnsUser` | GET /api/auth/me with session → JSON with email and name |
| `mutationWithoutCsrfTokenReturns403` | POST /graphql authenticated but no CSRF header → 403 |
| `mutationWithCsrfTokenReturns200` | POST /graphql authenticated + CSRF → 200 |
| `logoutInvalidatesSessionAndReturns204` | POST /api/auth/logout → 204 |

**Why does the anonymous test include `.with(csrf())`?**  
The CSRF filter runs *before* the authentication filter. An anonymous POST without a
CSRF token hits CSRF first → 403. Adding `.with(csrf())` lets the request pass the CSRF
gate, so the authentication filter runs and returns 401. Both 401 and 403 are correct
responses to an anonymous user, but the test is specifically checking the *authentication*
rule, so CSRF must be satisfied first.

**How the test creates an authenticated user without real Google:**

```java
private OAuth2AuthenticationToken buildAuth(AppUser user) {
    OidcIdToken token = OidcIdToken.withTokenValue("test-token")
            .subject(user.getGoogleSub())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("email", user.getEmail())
            .build();
    DefaultOidcUser oidcUser = new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
    AppUserOidcUser principal = new AppUserOidcUser(oidcUser, user);
    return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
}

// Used in tests:
mockMvc.perform(post("/graphql")
        .with(authentication(testAuth))  // inject auth into the request context
        .with(csrf())                    // supply a valid CSRF token
        …)
```

---

### `BaseGraphQlIntegrationTest` — shared base for GraphQL tests

File: `backend/src/test/java/…/support/BaseGraphQlIntegrationTest.java`

All 9 GraphQL integration test classes extend this. It:

1. **`@BeforeEach`** — saves a real `AppUser` to the H2 in-memory DB and populates
   `SecurityContextHolder` with a matching `OAuth2AuthenticationToken`.
2. **`@AfterEach`** — deletes all goals (cascade), deletes the user, clears the context.

**Why `SecurityContextHolder` and not HTTP session?**  
`@AutoConfigureGraphQlTester` calls Spring GraphQL directly, bypassing HTTP. There is no
real HTTP request, no session cookie. But `CurrentUserProvider` reads from
`SecurityContextHolder`, which exists independently of HTTP. Populating it manually in
`@BeforeEach` is the correct approach.

For tests that need a second user:
```java
// In BaseGraphQlIntegrationTest:
protected AppUser createAdditionalUser(String googleSub, String email) { … }
protected void setCurrentUser(AppUser user) { … }
```

---

### `CrossUserIsolationIntegrationTest` — cross-user data isolation

File: `backend/src/test/java/…/graphql/CrossUserIsolationIntegrationTest.java`

Tests that **user B cannot access or modify user A's data**, even when user B knows
the exact goal ID. Each test follows the same pattern:

1. Act as **userA** (the default `testUser`) — create a goal.
2. Switch to **userB** via `setCurrentUser(userB)`.
3. Attempt to access or mutate userA's goal.
4. Assert that an error is returned (not the data, and not a success response).

| Test | What it verifies |
|------|-----------------|
| `goalListIsFilteredByOwner` | `goals { }` query returns only the current user's goals |
| `goalListIsEmptyForNewUser` | A new user sees zero goals, even if goals exist for other users |
| `cannotReadAnotherUsersGoalById` | `goalById(id)` with a foreign goal ID → NOT_FOUND error |
| `cannotUpdateAnotherUsersGoal` | `updateGoal` on a foreign goal → error |
| `cannotDeleteAnotherUsersGoal` | `deleteGoal` on a foreign goal → error; goal still exists for its owner |
| `cannotAddTargetToAnotherUsersGoal` | `createTarget` for a foreign goal → error |
| `cannotAddOptionToAnotherUsersGoal` | `addOption` for a foreign goal → error |
| `cannotAddResourceToAnotherUsersGoal` | `createResource` for a foreign goal → error |
| `cannotAddRealityItemToAnotherUsersGoal` | `addRealityItem` for a foreign goal → error |

The key detail in `cannotDeleteAnotherUsersGoal`: after userB's delete attempt fails,
the test switches back to userA and confirms the goal still exists. This proves the
delete was genuinely rejected, not silently ignored.

---

### `GoalIsolationIntegrationTest` — within-user data isolation

File: `backend/src/test/java/…/graphql/GoalIsolationIntegrationTest.java`

Complementary to the above. Tests that data belonging to **one goal does not appear
in a different goal** of the same user. Covers: options, reality items, targets,
resources, confidence history, progress score, and option selection state.

---

## 8. Making it run — what you need to do

### 8.1 Google Cloud Console setup (one-time)

1. Go to [console.cloud.google.com](https://console.cloud.google.com).
2. Create a project (or select an existing one).
3. Navigate to **APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID**.
4. Application type: **Web application**.
5. Add **Authorized redirect URIs**:
   - Development: `http://localhost:5173/login/oauth2/code/google`
   - Production: `https://your-domain.com/login/oauth2/code/google`
6. Click **Create** and note down the **Client ID** and **Client Secret**.

### 8.2 Environment variables for the backend

Set these before running the backend. Never put real values in committed files.

```bash
export GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=your-client-secret
export FRONTEND_URL=http://localhost:5173   # default if omitted
```

Or in IntelliJ: **Run → Edit Configurations → Environment variables**.

### 8.3 Running locally

```bash
# Terminal 1 — backend
cd backend
GOOGLE_CLIENT_ID=xxx GOOGLE_CLIENT_SECRET=yyy ./mvnw spring-boot:run

# Terminal 2 — frontend
npm run dev
```

Open `http://localhost:5173`. You should see the login page.

### 8.4 Running tests (no Google credentials needed)

Tests use fake OIDC tokens and an H2 in-memory database — no real Google connection.

```bash
cd backend
.\mvnw.cmd test          # Windows PowerShell
./mvnw test              # macOS / Linux / Git Bash
```

### 8.5 Production checklist

- [ ] `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` set as server environment variables
- [ ] `FRONTEND_URL` set to `https://your-domain.com`
- [ ] `server.servlet.session.cookie.secure=true` added (requires HTTPS)
- [ ] Production redirect URI added in Google Cloud Console
- [ ] `JSESSIONID` cookie `SameSite=Lax` or `Strict` confirmed in browser DevTools

---

## 9. Checklist for a new project

### Backend

- [ ] Add to `pom.xml`: `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-security-test`
- [ ] Add to `application.properties`: `spring.security.oauth2.client.registration.google.*`, `app.frontend.url`, cookie settings
- [ ] Create `AppUser` entity with `googleSub` (unique), `email` (unique), `name`, `pictureUrl`, `role`, `@PrePersist`/`@PreUpdate` timestamps
- [ ] Create `AppUserRepository` with `findByGoogleSub` and `findByEmail`
- [ ] Create `AppUserService.findOrCreateFromOidc(OidcUser)` — look up by `sub`, create or update
- [ ] Create `AppUserOidcUser implements OidcUser` — wraps `OidcUser` + `AppUser`, exposes `getAppUser()`
- [ ] Create `AppUserOidcUserService implements OAuth2UserService` — delegates to `OidcUserService`, calls `findOrCreateFromOidc`, returns `AppUserOidcUser`
- [ ] Create `CurrentUserProvider` — reads `AppUser` from `SecurityContextHolder`
- [ ] Create `SecurityConfig`:
  - [ ] `permitAll` for `/oauth2/**`, `/login/**`, health endpoint
  - [ ] `authenticated` for everything else
  - [ ] `oauth2Login` pointing to the custom `oidcUserService`
  - [ ] `logout` → 204 (not a redirect)
  - [ ] CSRF with `CookieCsrfTokenRepository.withHttpOnlyFalse()`
  - [ ] `HttpStatusEntryPoint(401)` for `/graphql` and `/api/`
- [ ] Create `UserDto` record + `AuthController` with `GET /api/auth/me`
- [ ] Flyway: create `app_user` table with unique index on `google_sub` and `email`
- [ ] Flyway: add `user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE` to the main entity table
- [ ] Add `@ManyToOne AppUser user` to the main entity
- [ ] Repository: add `findByUserId…` and `findByIdAndUserId` methods
- [ ] Service: scope `findAll` and `findById` by `currentUserProvider.getCurrentUser().getId()`
- [ ] Child services: inject the parent **service** (not repository) and call its owner-scoped `findById`

### Frontend

- [ ] `vite.config.ts`: proxy `/api`, `/oauth2`, `/login` (and `/graphql`) to `http://localhost:8080`
- [ ] Create `auth.ts`: Zustand store with `user`, `status`, `fetchMe()`, `logout()`, `getCsrfToken()`
- [ ] `api.ts`: add `credentials: "include"` and `X-XSRF-TOKEN: getCsrfToken()` to every request
- [ ] `store.ts`: on `SpiraApiError` with `status === 401`, call `window.location.replace("/login")`
- [ ] Root route `beforeLoad`: skip if `/login`; call `fetchMe()` if status is `"loading"`; redirect if `"anonymous"`
- [ ] Root component: render without `AppShell` when pathname is `/login`
- [ ] Create `login.tsx` with `<a href="/oauth2/authorization/google">` button and optional `?error` message
- [ ] `AppShell.tsx`: use `useAuth` for real user name/avatar; add "Sign out" dropdown item

### Tests

- [ ] `AppUserServiceTest` — unit test `findOrCreateFromOidc`: first login, repeat login, null picture, email change
- [ ] `SecurityIntegrationTest` — HTTP rules: 401 for anonymous, 403 without CSRF, 204 for logout
- [ ] `BaseGraphQlIntegrationTest` — shared base: save `AppUser` to H2 in `@BeforeEach`, populate `SecurityContextHolder`, clean up in `@AfterEach`
- [ ] Update all existing GraphQL integration tests to extend `BaseGraphQlIntegrationTest`
- [ ] `CrossUserIsolationIntegrationTest` — user B cannot read, update, delete, or add children to user A's data
- [ ] `GoalIsolationIntegrationTest` — data of one goal does not leak into another goal of the same user

### Google Cloud

- [ ] Create OAuth 2.0 Client (Web application type)
- [ ] Add redirect URIs for dev (`localhost:5173`) and prod
- [ ] Provide `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` as environment variables — never in code
