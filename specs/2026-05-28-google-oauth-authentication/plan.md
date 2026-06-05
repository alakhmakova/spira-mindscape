# Google Sign-In Authentication — Plan

## Overview

Implement Google-only authentication with Spring Security (OAuth2/OIDC, server-side
session) on the backend, per-user data isolation in the database and services, a
split-panel login screen on the frontend, and full test coverage. Build it in the
order below; each step is shippable/testable before the next.

## Prerequisites (manual, one-time)

- In Google Cloud Console, create an OAuth 2.0 Client (type: Web application).
  - Authorized redirect URI (dev): `http://localhost:5173/login/oauth2/code/google`
    (served through the Vite proxy) — and/or `http://localhost:8080/login/oauth2/code/google`.
  - Authorized redirect URI (prod): `https://<domain>/login/oauth2/code/google`.
- Provide credentials to the backend via env vars:
  `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Never commit them.

## Steps

### 1. Backend dependencies & config

- Add to `backend/pom.xml`:
  - `spring-boot-starter-security`
  - `spring-boot-starter-oauth2-client`
  - test: `spring-security-test`
- Register the Google provider in `application.properties` using env placeholders:
  ```properties
  spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
  spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
  spring.security.oauth2.client.registration.google.scope=openid,email,profile
  app.frontend.url=${FRONTEND_URL:http://localhost:5173}
  ```

### 2. User domain & persistence

- New entity `AppUser` (table `app_user`) per the requirements data model, with
  `@PrePersist`/`@PreUpdate` timestamps consistent with existing entities.
- `AppUserRepository` with `findByGoogleSub(String)` and `findByEmail(String)`.
- `AppUserService`:
  - `findOrCreateFromOidc(OidcUser)` — look up by `google_sub`; create if absent;
    on existing, refresh `email`/`name`/`pictureUrl` and set `last_login_at`.
  - `getById` / current-user helpers.
- Flyway migrations (PostgreSQL):
  - `V7__app_user.sql` — create `app_user` table + unique indexes.
  - `V8__goal_owner.sql` — `DELETE FROM goal;` (cascades to children), then add
    `user_id` bigint, FK → `app_user(id)` `ON DELETE CASCADE`, `NOT NULL`, plus index.

### 3. Spring Security configuration

- `SecurityConfig` with a `SecurityFilterChain`:
  - `permitAll`: `/login/**`, `/oauth2/**`, `GET /api/auth/me` (returns 401 itself when
    anonymous — see note), health endpoint, static assets.
  - `authenticated`: `/graphql`, `POST /api/auth/logout`, everything else.
  - `.oauth2Login(...)`:
    - custom `OidcUserService` (or success handler) that calls
      `AppUserService.findOrCreateFromOidc(...)`.
    - success handler redirects to `app.frontend.url`.
    - failure handler redirects to `app.frontend.url + "/login?error"`.
  - `.logout(...)`: logout URL `POST /api/auth/logout`, invalidate session, delete
    cookie, return `204` (no redirect — SPA handles navigation).
  - CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` so the SPA can read
    `XSRF-TOKEN` and resend it; keep CSRF enabled for `/graphql` and logout.
  - Session: `SessionCreationPolicy.IF_REQUIRED`; rely on Spring's session-fixation
    protection. Configure cookie `SameSite=Lax`, `HttpOnly`, `Secure` (prod profile).
  - Make unauthenticated API calls return `401` (an `AuthenticationEntryPoint` that
    sends 401 instead of a 302 to Google), so the SPA can detect "not logged in"
    without following a redirect.
- Decide cookie security per profile: `server.servlet.session.cookie.secure=true` in
  prod; `same-site=lax`.

### 4. Auth REST endpoints

- `AuthController`:
  - `GET /api/auth/me` → current `AppUser` as a small DTO, or `401` if anonymous.
  - `POST /api/auth/logout` is handled by Spring Security's logout (returns `204`).

### 5. Per-user data isolation (services)

- Introduce a `CurrentUser` accessor (read the authenticated principal → `AppUser`).
- `GoalService.create` sets `goal.user = currentUser`.
- All read/update/delete paths resolve the goal **scoped to the current user**:
  - `goalRepository.findByIdAndUserId(id, userId)` → `NOT_FOUND` if absent.
  - `findAll` → `findByUserIdOrderByCreatedAtAsc(userId)`.
  - `@BatchMapping` resolvers and child services (`Target`, `Resource`, `Reality`,
    options, confidence history) must verify the parent goal is owned by the current
    user before returning/mutating.
- Keep the existing "not found" error message style so the GraphQL error classifier
  maps cross-user access to `NOT_FOUND`.

### 6. CORS / dev proxy

- Add a Vite dev proxy in `vite.config.ts` forwarding `/graphql`, `/api`, `/oauth2`,
  `/login` to `http://localhost:8080`, so the browser uses a single origin in dev.
- Lock down / simplify `CorsConfig`: it is no longer needed for the proxied happy path;
  if kept as a fallback it must use a specific origin and `allowCredentials(true)`
  (never `*` with credentials). (Also clean up the current duplicated lines / the
  malformed `package` declaration in `CorsConfig.java`.)

### 7. Frontend — auth plumbing

- `src/lib/spira/auth.ts` (or extend the store): `fetchMe()`, `logout()`, and a small
  auth store (`user`, `status: 'loading' | 'authed' | 'anonymous'`).
- `src/lib/spira/api.ts`:
  - add `credentials: "include"` to every `fetch`.
  - read `XSRF-TOKEN` cookie and send `X-XSRF-TOKEN` header on mutations.
  - on `401`, set auth state to anonymous and route to `/login`.
- Routing (TanStack Router):
  - add a public `/login` route.
  - guard the app routes (`beforeLoad` / root loader) — if `fetchMe()` is 401, redirect
    to `/login`; otherwise render.

### 8. Frontend — login screen (matches the design)

- Split-panel layout:
  - **Left panel** (deep teal, matching the app's brand teal): Spira logo/wordmark, a
    headline ("Plan goals you can actually measure" or similar), and a small product
    card echoing the screenshot's card.
  - **Right panel** (white): heading ("Sign in to Spira") + subtext, and a single
    **"Continue with Google"** button (Google "G" icon, accessible label). No inputs.
- The button links to `/oauth2/authorization/google` (full backend URL via proxy).
- Responsive: stacks to one column on small screens.
- Build with existing shadcn/ui + Tailwind tokens for visual consistency.

### 9. Frontend — signed-in chrome

- Show the user's name/avatar in the workspace header.
- Sign-out control → `logout()` → redirect to `/login`.

### 10. Tests (see validation.md for the full matrix)

- Backend unit: `AppUserService.findOrCreateFromOidc`, owner-scoping helpers.
- Backend integration: security rules (401/403), `/api/auth/me`, logout, per-user
  isolation through GraphQL using `spring-security-test` (`oidcLogin()` / mock user),
  CSRF enforcement.
- Update existing integration tests to run as an authenticated user (shared test
  helper that authenticates the GraphQL tester).
- E2E: add a mock OIDC provider (`navikt/mock-oauth2-server`) as a CI service so the
  Python suite can complete a real login flow and obtain a session; add an auth fixture;
  update existing E2E tests to log in first.
- Frontend: login page renders Google-only; auth guard redirects when anonymous;
  `api.ts` sends credentials + CSRF and handles 401; `me()`/`logout()` behavior.

### 11. Docs & CI

- Update `docs/github-actions-ci.md` (mock OIDC service in the E2E job, new env vars).
- Update `docs/testing-guide.md` and `docs/unit-vs-integration-tests.md` if auth changes
  how tests are run (authenticated GraphQL tester helper).
- Document required env vars (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONTEND_URL`)
  in the deployment docs.

## Risks

- **Cross-site cookies in dev.** A separate SPA origin + session cookie is fragile.
  Mitigation: the Vite proxy makes everything same-origin in dev; same-origin in prod.
- **OAuth redirect URI mismatch.** The redirect URI registered in Google must exactly
  match what Spring builds (host/scheme behind the proxy). Mitigation: set
  `server.forward-headers-strategy` appropriately and document exact URIs.
- **GraphQL + Spring Security test wiring.** Authenticating `GraphQlTester` needs the
  right test setup. Mitigation: use MockMvc-based GraphQL testing with
  `SecurityMockMvcRequestPostProcessors.oidcLogin()`, or `HttpGraphQlTester` with a
  pre-authenticated session; pick one helper and reuse it.
- **E2E real OAuth is impossible in CI.** Mitigation: mock OIDC server service container.
- **H2 vs PostgreSQL for the new migration.** Integration tests use H2 (`create-drop`,
  Flyway disabled), so the `app_user`/`user_id` schema must also be expressible via JPA
  entities; the E2E PostgreSQL path validates the real Flyway migration.
- **Large blast radius on existing services/tests.** Adding ownership touches every
  goal-scoped query and many tests. Mitigation: introduce a current-user abstraction and
  a single authenticated test helper to minimise churn.

## Definition of Done

- A user can sign in with Google, work in a private workspace, and sign out.
- Anonymous requests get 401; cross-user access returns NOT_FOUND; CSRF enforced.
- New `app_user` table and `goal.user_id` exist via Flyway; existing data wiped.
- Backend, E2E, and frontend tests for auth pass locally and in CI.
- No secrets committed; credentials read from env.
- Login screen matches the supplied split-panel design with Google-only sign-in.
