# Google Sign-In Authentication — Requirements

## Overview

Spira currently has no authentication: the backend serves one shared, ownerless set
of goals and the frontend talks to GraphQL anonymously. This phase adds
authentication so that a person signs in **only with their Google account**, becomes
a persisted user, and works in their **own private workspace**. There is no email/
password login of any kind — Google is the single identity provider.

The guiding constraints (from the product owner) are: **as simple as possible** and
**as secure as possible**, using current best practices.

## Decisions (locked for this phase)

These were decided up front and the rest of the spec assumes them:

1. **Per-user private data.** Every goal (and everything under it) belongs to exactly
   one user. A user can never read or modify another user's data.
2. **Any Google account may sign in.** No domain restriction, no email allowlist. The
   first time someone signs in, an account is created for them automatically.
3. **Existing ownerless goals are wiped** during the ownership migration (the database
   only holds dev/test data today).
4. **Auth model = OAuth 2.0 / OIDC Authorization Code flow handled server-side by
   Spring Security, with a server-side session and a secure `HttpOnly` cookie.** No
   tokens are ever exposed to or stored by JavaScript. This is the simplest model that
   is also XSS-safe for a server-backed SPA.

## Goals

- Let a user sign in with Google and sign out.
- Persist users in the database, keyed by their stable Google identity.
- Require authentication for all data operations (GraphQL).
- Scope all goal data to the authenticated user (private workspaces).
- Provide a login screen matching the supplied split-panel design (Google button only).
- Cover the whole feature with backend, E2E, and frontend tests.

## Non-Goals

- No email/password, magic links, or any non-Google identity provider.
- No multi-user collaboration or sharing of a goal between users.
- No roles/permissions beyond a single default `USER` role (an `ADMIN` role may exist
  in the schema for the future but is not exercised by features here).
- No account self-deletion / data export UI (can be a later phase).
- No organisation/tenant concept beyond the single owning user.

## Functional Requirements

### Sign-in

- The app has a public `/login` route. Everything else requires authentication.
- The login screen shows a single **"Continue with Google"** action. No other fields.
- Clicking it starts the Google OAuth2 Authorization Code flow handled by the backend
  (`/oauth2/authorization/google`).
- On success, the backend creates a session and redirects the browser back to the SPA.
- On the first successful sign-in for a Google account, a user row is created. On
  subsequent sign-ins, the existing user is loaded and `last_login_at` is updated; name
  and picture are refreshed from Google.

### Session & current user

- The backend exposes `GET /api/auth/me`:
  - `200` with `{ id, email, name, pictureUrl }` when authenticated.
  - `401` when not authenticated.
- The SPA calls `/api/auth/me` on load to decide whether to show the app or `/login`.
- The backend exposes `POST /api/auth/logout` which invalidates the server session,
  clears the session cookie, and returns `204`.
- The SPA shows the signed-in user (name/avatar) and a sign-out control.

### Authorization & data isolation

- Every GraphQL query/mutation requires an authenticated user.
- `goals` and `goalById` only ever return goals owned by the current user.
- Creating a goal assigns the current user as owner.
- Accessing a goal (or any child: option, reality item, target, resource, confidence
  history) that belongs to another user returns a **`NOT_FOUND`** error — never a
  permission error and never the data (do not reveal that the resource exists).
- All existing service-level rules (validation, progress, etc.) are unchanged except
  that they now operate within the owner scope.

### Data model

New table `app_user`:

| Column | Type | Notes |
|---|---|---|
| `id` | bigint PK | generated |
| `google_sub` | text, unique, not null | Google OIDC `sub` (stable id) |
| `email` | text, unique, not null | from Google |
| `name` | text | display name from Google |
| `picture_url` | text, nullable | avatar from Google |
| `role` | text, not null, default `'USER'` | future-proofing |
| `created_at` | timestamptz, not null | |
| `updated_at` | timestamptz, not null | |
| `last_login_at` | timestamptz, nullable | updated each sign-in |

Change to `goal`:

- Add `user_id` bigint, **not null**, FK → `app_user(id)` `ON DELETE CASCADE`.
- Index on `goal(user_id)`.
- Existing rows are deleted by the migration before the column is made `NOT NULL`.

No password column exists anywhere. Children of `goal` (option, reality_item, target,
resource, confidence_history) remain owned transitively through `goal` and are scoped
by joining/looking up via the owning goal.

## Non-Functional / Security Requirements

- **No passwords stored, ever.** Identity is delegated entirely to Google.
- **Stable identity = Google `sub`**, not email (email can change/transfer).
- **OIDC scopes are minimal**: `openid`, `email`, `profile`.
- **Session cookie** is `HttpOnly`, `Secure` (in production), `SameSite=Lax`. No auth
  material is readable by JavaScript (no `localStorage`/`sessionStorage` tokens).
- **CSRF protection** is enabled for state-changing requests. The SPA uses the
  double-submit cookie pattern: backend issues a readable `XSRF-TOKEN` cookie, the SPA
  echoes it in an `X-XSRF-TOKEN` header on every mutating request.
- **Session fixation protection**: a new session id is issued on login (Spring default).
- **Authorization is enforced per request at the data layer** (owner check), not only
  by "is authenticated" — defense in depth.
- **No open redirects**: after login the backend only redirects to the configured
  frontend URL.
- **Secrets** (Google client id/secret) come from environment variables and are never
  committed. `application.properties` references them via `${...}` only.
- **Transport**: production serves over HTTPS; the secure cookie flag is set in prod.
- **Same-origin in production** (SPA and API behind one domain); dev uses a Vite proxy
  so the browser sees a single origin and cookies are first-party (avoids cross-site
  cookie/CORS complexity). CORS with credentials is only a fallback.

## Acceptance Criteria

- A new Google account can sign in and lands in an empty, private workspace.
- A returning user sees only their own goals; `last_login_at` updates.
- Two different users cannot see or modify each other's goals (verified by test).
- Any GraphQL call without a valid session is rejected with `401`.
- A mutating request without a valid CSRF token is rejected with `403`.
- `/api/auth/me` and `/api/auth/logout` behave as specified.
- The login screen matches the split-panel design and offers only Google sign-in.
- Backend (unit + integration), E2E, and frontend tests for the feature pass in CI.
- No secret values are committed; Google credentials are read from the environment.
