# Native Mobile App + Cross-Device Sync — Validation

## Overview

How each part is verified. Follows the project's test pyramid
(`docs/unit-vs-integration-tests.md`): rules at the unit level, contracts at integration,
full flows at E2E. Web E2E uses Playwright; native UI uses Compose UI Test + Maestro.

## Part 1 — Cross-device freshness (web) — ✅ done

### Frontend — unit (Vitest, `src/lib/spira/store.test.ts`)

| Area | Scenario | Status |
|---|---|---|
| `refreshGoalsIfIdle` | Re-fetches and replaces goals **after** `hasLoaded` (bypasses the stale-cache guard); loading banner stays off | ✅ |
| `refreshGoalsIfIdle` | Skips while a debounced write is in flight (`syncTimers.size > 0`) — never clobbers an unsaved edit | ✅ |
| `refreshGoalsIfIdle` | Skips while a create is in flight (a `local-` temp id is present) | ✅ |

### Manual (two surfaces)

1. Sign in as the same user on desktop + phone (or two tabs; use the ngrok setup in
   `README.md` for a real phone).
2. Edit a goal on desktop.
3. Return focus to the phone (switch tab / unlock) → the change appears within ~1s; two
   visible screens converge within the 45s poll.
4. Editing on the phone still works without losing in-flight local edits.

Result recorded in `backlog/cross-device-data-not-refreshing.md` (Status ✅ Fixed).

## Part 2 — Mobile auth (backend) — ✅ done

### Backend — unit (Mockito) — implemented

| Area | Scenario | Status |
|---|---|---|
| `AppUserService.findOrCreateFromGoogle` | First mobile sign-in creates the user from raw Google claims | ✅ |
| `AppUserService.findOrCreateFromGoogle` | Same `google_sub` reuses the row → web and mobile share one account | ✅ |
| `MobileAuthController` | Blank/missing `idToken` → `400`, no verification | ✅ |
| `MobileAuthController` | Invalid/unverifiable token → `401`, no user created | ✅ |
| `MobileAuthController` | Valid token → `200` + `UserDto`, find-or-create called, session holds an `AppUserOidcUser` principal | ✅ |

### Backend — integration (`@SpringBootTest` + MockMvc, `MobileAuthIntegrationTest`) — implemented

The `test` profile uses in-memory servlet sessions (spring-session-jdbc excluded), so the
MockMvc session can be captured and reused — the full round-trip is automated:

| Area | Scenario | Status |
|---|---|---|
| Login (CSRF-exempt) | `POST /api/auth/google/mobile` (no CSRF token) with a stubbed-valid token → `200` + `UserDto`, user persisted | ✅ |
| Session reuse | Reusing only that session → `GET /api/auth/me` → `200` + user (no per-request auth) | ✅ |
| Session authorizes | Same session + CSRF → `POST /graphql` → `200` | ✅ |
| Negative | Invalid token → `401`, no user created | ✅ |
| Negative | No session → `POST /graphql` → `401` (proves it's the session doing the auth) | ✅ |

Optional manual check against a running backend (real token): `curl` the endpoint, then reuse
the `SESSION` cookie on `/api/auth/me` and `/graphql`.

### No regression

Full backend suite green — including all existing web-OAuth security and GraphQL integration
tests, unchanged.

## Part 3 — Native Android MVP

### Unit (JUnit / Kotlin test)

| Area | Scenario |
|---|---|
| Progress | Kotlin port of numeric/binary/checklist + goal progress matches `src/lib/spira/progress.ts` |
| GraphQL mapping | Apollo responses map to domain models |

### UI components (Compose UI Test)

| Area | Scenario |
|---|---|
| Dashboard | Goals render with title, progress, confidence, deadline |
| Goal workspace | Five sections render; a target progress update calls the mutation and updates optimistically |
| Freshness | Returning to foreground triggers a refetch |

### E2E (Maestro, emulator)

| Flow | Scenario |
|---|---|
| Sign-in | Google sign-in → land on dashboard |
| Cross-device | A goal created on web appears in the app after a resume refetch; a target updated in the app appears on web after its refetch |

## Part 4 — Firebase / distribution

| Area | Check |
|---|---|
| App Distribution | A signed build installs on a physical device from the tester invite |
| Crashlytics | A forced test crash appears in the console |
| FCM | A test push arrives; deadline/overdue/daily-focus sender works against a test device token |
| Employer review | Live web URL loads; the signed APK downloads in one click from a GitHub Release |
