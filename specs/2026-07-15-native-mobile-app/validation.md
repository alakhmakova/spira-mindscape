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

## Part 2 — Mobile auth (backend)

### Backend — unit (Mockito)

| Area | Scenario |
|---|---|
| Mobile sign-in | Valid Google ID token → verify → find-or-create `AppUser` on `sub` (reuses `AppUserService`) |
| Mobile sign-in | Returning user is not duplicated; profile refreshed |
| Mobile sign-in | Invalid/expired ID token → rejected (no user created) |

### Backend — integration (`@SpringBootTest`)

| Area | Scenario |
|---|---|
| Endpoint | `POST /api/auth/google/mobile` with a stubbed-valid token → `200` + `SESSION` cookie |
| Session reuse | Subsequent `GET /api/auth/me` with that cookie → user JSON |
| CSRF | Mutation with the cookie + `X-XSRF-TOKEN` → `200`; without token → `403` |
| No regression | Existing web OAuth + all current GraphQL integration tests still pass unchanged |

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
