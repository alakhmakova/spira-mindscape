# Google Sign-In Authentication — Validation

## Overview

How this feature will be verified. Tests follow the project's test pyramid
(see `docs/unit-vs-integration-tests.md`): rules at the unit level, the security/API
contract at the integration level, the full login flow at E2E, and the UI on the
frontend. Aim for: a rule is proven once at the lowest level that can see it.

## Test Coverage

### Backend — unit (Mockito, no Spring)

| Area | Scenario |
|---|---|
| `AppUserService` | First sign-in creates a user from the OIDC `sub`/email/name |
| `AppUserService` | Returning sign-in loads existing user, refreshes name/picture, sets `last_login_at` |
| `AppUserService` | Email change with same `sub` updates email on the same user row |
| Owner scoping | `GoalService` sets the current user as owner on create |
| Owner scoping | Accessing a goal not owned by the current user throws "not found" (→ NOT_FOUND) |

### Backend — integration (`@SpringBootTest`, H2, `spring-security-test`)

| Area | Scenario |
|---|---|
| Security | Anonymous `POST /graphql` → `401` |
| Security | Authenticated `POST /graphql` → `200` |
| Security | `GET /api/auth/me` anonymous → `401`; authenticated → user JSON |
| Security | `POST /api/auth/logout` invalidates session → subsequent call `401` |
| CSRF | Mutation without `X-XSRF-TOKEN` → `403`; with valid token → `200` |
| Isolation | User A's `goals`/`goalById` never include user B's goals |
| Isolation | User B updating/deleting user A's goal → `NOT_FOUND` |
| Isolation | Child access (option/target/resource/reality) on another user's goal → `NOT_FOUND` |
| OIDC mapping | First login persists an `app_user`; second login does not duplicate |

> All existing GraphQL integration tests are updated to run as an authenticated user
> via a shared helper (e.g. `oidcLogin()` request post-processor / pre-authenticated
> `GraphQlTester`). They must continue to pass unchanged in behaviour, just authenticated.

### E2E (Python, real PostgreSQL + mock OIDC)

| Area | Scenario |
|---|---|
| Login flow | Complete the Authorization Code flow against the mock OIDC server, obtain a session |
| Gate | Unauthenticated GraphQL call → `401` |
| Happy path | After login, create/read a goal in the user's own workspace |
| Isolation | A second mock user cannot see the first user's goal |
| Logout | After logout, GraphQL call → `401` |

> CI adds a `navikt/mock-oauth2-server` service container; the backend's Google provider
> is pointed at it for the E2E profile. A pytest fixture performs the login and yields an
> authenticated `httpx` client (cookies persisted). Existing E2E tests adopt this fixture.

### Frontend (Vitest; component tests need React Testing Library setup)

| Area | Scenario |
|---|---|
| Login page | Renders the split layout and a single "Continue with Google" action; **no** email/password inputs |
| Login page | The Google button points at `/oauth2/authorization/google` |
| Auth guard | `status = anonymous` redirects to `/login`; `authed` renders the app |
| `api.ts` | Requests send `credentials: "include"` |
| `api.ts` | Mutations include the `X-XSRF-TOKEN` header read from the cookie |
| `api.ts` | A `401` response flips auth state to anonymous (triggers redirect) |
| Auth store | `fetchMe()` sets user on `200`, anonymous on `401`; `logout()` clears user |

## How to Run

```powershell
# Backend unit + integration (H2)
cd backend
.\mvnw.cmd test

# Backend coverage report (JaCoCo)
#   backend/target/site/jacoco/index.html

# Frontend
npm.cmd test

# E2E (needs running backend + mock OIDC; CI wires these as services)
cd tests-e2e
pip install -r requirements.txt
pytest
```

Local manual run requires real Google credentials in the environment:

```powershell
$env:GOOGLE_CLIENT_ID = "..."
$env:GOOGLE_CLIENT_SECRET = "..."
```

## Manual Verification

1. Start backend (with Google creds) and frontend (`npm run dev`).
2. Visit the app → redirected to `/login` (split panel, Google button only).
3. Click **Continue with Google**, complete Google consent → land in an empty workspace.
4. Create a goal; reload → it persists and is shown.
5. Open `GET /api/auth/me` in the browser → your user JSON.
6. Sign out → redirected to `/login`; visiting the app again requires sign-in.
7. Sign in as a **second** Google account → empty workspace; cannot see the first
   account's goal.
8. Confirm the session cookie is `HttpOnly` (not visible to `document.cookie`) and, in
   prod, `Secure`.

## Security Checklist (must all hold)

- [ ] No password field/column anywhere; Google is the only identity.
- [ ] Session cookie is `HttpOnly` (+ `Secure` in prod, `SameSite=Lax`).
- [ ] No tokens in `localStorage`/`sessionStorage` or readable JS.
- [ ] CSRF enforced on all state-changing requests.
- [ ] Every goal-scoped query filters by the owner; cross-user access → `NOT_FOUND`.
- [ ] Google `sub` (not email) is the stable identity key.
- [ ] OIDC scopes limited to `openid`, `email`, `profile`.
- [ ] Post-login redirect targets only the configured frontend URL (no open redirect).
- [ ] `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` read from env; nothing committed.

## Notes

- H2 integration tests exercise the JPA-mapped `app_user`/`goal.user_id` schema; the
  real Flyway migration (V7/V8) is exercised by the E2E PostgreSQL job.
- The data-wipe in V8 is acceptable only because the database holds dev/test data; this
  must be re-evaluated before any real production data exists.
