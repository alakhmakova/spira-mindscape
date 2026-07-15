# Plan: Native Kotlin/Android app + fix cross-device data freshness

> Aligns with **Roadmap Phase 13: Native Mobile App**. See `requirements.md` and
> `validation.md` alongside this file.

---

## Context

Spira today is a single-origin web app: a React SPA served by the same Spring Boot
container that exposes the GraphQL API (`/graphql`), backed by Neon PostgreSQL. Auth is
Google OAuth2 (web redirect flow) with **server-side sessions stored in PostgreSQL**
(`spring_session`), so identity and all goal data are already **centralized per user**.

The goal is to add a **native Kotlin/Jetpack Compose Android app** while keeping the web app,
distributed via **Firebase** (App Distribution + FCM + Crashlytics/Analytics). Before that,
the cross-device staleness bug is fixed: edits made on desktop don't appear on the phone
until a full reload/re-login.

### Root cause of the "two sessions" feeling (investigated, confirmed)

It is **not** two sessions and **not** an auth problem. The web client fetches goals
**once per page load and never re-fetches**:

- `src/lib/spira/store.ts` → `loadGoals()` begins with `if (get().isLoading || get().hasLoaded) return;`
- `src/components/shell/AppShell.tsx` calls `loadGoals()` once on mount.
- The only runtime listeners are `offline`/`online`. There is **no** refetch on tab focus,
  visibility change, or app resume.

The backend session + DB are shared across desktop, responsive mobile web, and (future)
native app. So a desktop edit **is** saved centrally; the phone just shows its frozen
in-memory snapshot until something forces a reload. `refreshGoals()` already exists and
force-refetches — it was simply never triggered on return-to-app.

### Four questions, answered

| Question | Answer |
|---|---|
| **Change existing code or only add new?** | **Mostly add.** The web sync fix touches 2 existing frontend files. The native app is ~95% new code in a new `android/` module. The backend needs **one additive** change (a mobile sign-in endpoint) — GraphQL schema, services, entities, and the web OAuth flow are untouched. |
| **Will web ↔ mobile data sync?** | **Yes, automatically.** All surfaces read/write the same GraphQL API and per-user PostgreSQL data. After the refetch-on-return fix, all three (desktop, responsive web, native) stay consistent. |
| **Fix the "re-login to see changes" issue first?** | **Yes — Part 1.** Chosen approach: **refetch on return** (focus/visibility/app-resume + light poll). No backend work. |
| **Style web and mobile separately?** | **Yes.** Native Compose is a separate UI codebase; visual changes are done twice. Drift is reduced by sharing a single **design-token source** mirrored into Compose. See "Styling strategy". |

### Decisions locked in

- **Stack:** Native Kotlin + Jetpack Compose, consuming the existing GraphQL API via **Apollo Kotlin**.
- **Firebase:** App Distribution + Cloud Messaging (FCM) + Crashlytics/Analytics (no Hosting — native path).
- **Sync fix:** Refetch on return (+ light poll), no realtime SSE for now (deferred as a future upgrade).

---

## Part 0 — Governance & Quality Flow (`CLAUDE.md` + hooks) — ✅ done

Two layers: **hooks** = deterministic checks a computer enforces; **CLAUDE.md** =
judgment-based behaviors; **docs/backlog** = human context.

- **Hooks** (`.claude/settings.json`, scripts in `.claude/hooks/`):
  1. `block-git-write.js` (PreToolUse/Bash) — blocks commit/push/add/merge/rebase/reset
     --hard/cherry-pick/tag-create and `gh pr create|merge`; allows read-only git.
  2. `stop-lint-typecheck.js` (Stop) — `npm run lint` + `npx tsc --noEmit`, only when
     frontend `.ts/.tsx` changed; exits 2 to feed failures back.
  3. `stop-unit-tests.js` (Stop) — `npm test`, same change-guard.
  Heavy backend/E2E/Android tests stay in GitHub Actions CI.
- **`CLAUDE.md`** — manual-commit policy, Definition-of-Done loop, English-docs rule,
  build/run reference, project map, `backlog/` rules.
- **`backlog/`** — one Markdown file per user-reported bug (see `backlog/README.md`).

## Part 1 — Fix cross-device data freshness — ✅ done

Silent, guarded refetch-on-return. Backend-free. See `backlog/cross-device-data-not-refreshing.md`.

- **`src/lib/spira/store.ts`** — `refreshGoalsIfIdle()`: silent (no loading banner) refetch
  that bypasses the `hasLoaded` guard, skips while `isLoading` / a debounced write is queued
  (`syncTimers.size > 0`) / a create is in flight (`local-` id), and re-checks after the
  fetch so it never clobbers unsaved edits. `401` → `/login`; other errors swallowed.
- **`src/components/shell/AppShell.tsx`** — calls it on `focus` / `pageshow` /
  `visibilitychange` (visible) + a 45s poll while visible.
- **`src/lib/spira/store.test.ts`** — refetch-after-`hasLoaded`; skip on in-flight write;
  skip on in-flight create.

**Deferred:** realtime SSE (backend already streams SSE for AI in `ai/AiController.java`).

---

## Part 2 — Backend prep for the native app (one additive change) — ✅ done

**Implemented (2026-07-15).** New endpoint `POST /api/auth/google/mobile`
(`auth/MobileAuthController.java`) verifies a Google ID token via a `MobileTokenVerifier`
abstraction (`GoogleMobileTokenVerifier` uses Google's `GoogleIdTokenVerifier`; the accepted
audience is the `GOOGLE_CLIENT_ID` env var + optional `app.auth.mobile.extra-audiences`),
reuses `AppUserService.findOrCreateFromGoogle(...)` (refactored so web OIDC and mobile share
one find-or-create keyed on `google_sub`), and establishes the **same** session by storing an
`AppUserOidcUser` principal via `HttpSessionSecurityContextRepository` — so
`CurrentUserProvider`, `/graphql`, and `/api/auth/me` are unchanged. `SecurityConfig` permits
and CSRF-exempts just that path. `pom.xml` adds `google-api-client` (with `commons-logging`
excluded to avoid a JCL clash). Tests: `MobileAuthControllerTest` (unit: 400/401/200 + session
principal + session-id rotation + unique-violation race→200/conflict→409), `AppUserServiceTest`
(mobile find-or-create shares the row), and `MobileAuthIntegrationTest` (`@SpringBootTest` +
MockMvc: full round-trip login → session → `/api/auth/me` + `/graphql`, plus negative cases).
Full backend suite green. Post-review hardening applied: session-id rotation (session-fixation),
and unique-constraint recovery (concurrent first login → reload; email-transfer conflict → 409).

Original design notes below.

Native apps can't ride the browser's OAuth redirect + cookie session cleanly. Add a **mobile
sign-in endpoint**; leave the web flow untouched.

**New endpoint:** `POST /api/auth/google/mobile` with body `{ idToken }`.

- Android obtains a **Google ID token** via Credential Manager / Sign in with Google.
- Backend **verifies** the ID token (`GoogleIdTokenVerifier`, `google-api-client`) against
  the OAuth client ID(s), then **reuses existing** `AppUserService` /
  `AppUserOidcUserService` find-or-create keyed on the Google `sub`
  (`backend/src/main/java/com/spiramindscape/backend/auth/`).
- **Session strategy (recommended, least new infra):** establish the **same server-side
  session** the web uses and return the `SESSION` cookie. The Android client (OkHttp +
  persistent cookie jar) stores it, reusing the existing 14-day Postgres-backed session,
  `/api/auth/me`, `/api/auth/logout`, and CSRF model. The Android GraphQL client echoes the
  `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` on mutations, like the web
  (`src/lib/spira/auth.ts:getCsrfToken`).
  - *Alternative:* signed JWT + refresh token + Bearer filter (more infra; not for MVP).
- **Security config:** permit `POST /api/auth/google/mobile` and CSRF-exempt just that path
  in `SecurityConfig.java`. No change to `/graphql` or `/api/**` rules.

**Files:** new `MobileAuthController.java`; `SecurityConfig.java`; `backend/pom.xml`
(`google-api-client`). Test mirroring `AppUserServiceTest` for verify→find-or-create.

**Drive caveat (note):** the web flow also captures a Google **refresh token** for Drive
export (`OAuth2LoginSuccessHandler`, `V11`). The ID-token mobile flow won't obtain that;
mobile MVP omits Google Doc export.

---

## Part 3 — Native Kotlin/Compose app (each step shippable)

> **Prerequisites (one-time, done by the user):** Android tooling gaps, Firebase project +
> `google-services.json`, and the Google OAuth Android client — all in
> `docs/mobile-setup-guide.md`. Complete Parts A–C there before scaffolding.

New top-level module **`android/`** (Gradle, Kotlin DSL). No change to web/backend beyond Part 2.

**Stack:** Kotlin, Jetpack Compose (Material 3), **Apollo Kotlin**, OkHttp (+ persistent
cookie jar), Coroutines/Flow, Navigation-Compose, DataStore, Coil.

1. **Scaffold** — `android/`, min SDK ~26, `app` module, package `com.spiramindscape.android`;
   `local`/`prod` build config (emulator → `http://10.0.2.2:8080`).
2. **GraphQL via Apollo Kotlin** — point at `backend/.../schema.graphqls`; operations
   mirroring `src/lib/spira/api.ts`. Generated types are the shared contract.
3. **Auth** — Credential Manager → ID token → `POST /api/auth/google/mobile` → cookie jar;
   login screen, `/api/auth/me` check, logout.
4. **Goals dashboard** — reuse progress formulas from `src/lib/spira/progress.ts`
   (reimplemented in Kotlin, same math as `specs/tech-stack.md`).
5. **Goal workspace** — Goal / Reality / Resources / Options / Targets; prioritize
   low-friction target updates; optimistic update + refetch like the web store.
6. **Freshness parity** — refetch on `onResume`/foreground (native analogue of Part 1).
7. **Resource viewing** — notes, links (Custom Tab), files (open/download, PDF intent), email.
8. **AI chat + approval** (optional first release) — consume the SSE endpoint later.
9. **Push (FCM)** — see Part 4.

**MVP cut for first Firebase build:** Steps 1–6.

**Android testing:** JUnit/Kotlin (unit) + **Compose UI Test** (components) + **Maestro**
(E2E on emulator: sign-in → goals → update target). Web E2E stays Playwright. Android
emulator E2E runs by tag/manual dispatch in CI, not every push.

---

## Part 4 — Firebase (App Distribution + FCM + Crashlytics/Analytics)

- **Firebase project + `google-services.json`** in `android/app`; Gradle plugins.
- **App Distribution:** upload signed APK/AAB to a tester group
  (`firebase appdistribution:distribute` / Fastlane). This is "deploy via Firebase" for a
  native app (Play Store later).
- **FCM:** Android SDK + a **backend sender** (Firebase Admin SDK) for
  deadline/overdue/daily-focus pushes; store device tokens per user (new `device_token`
  table/migration). Dovetails with **Roadmap Phase 10**.
- **Crashlytics + Analytics:** add SDKs.
- **Optional CI:** GitHub Actions job builds the AAB and pushes to App Distribution on
  tag/manual dispatch.

### Distribution & cost (showing the work to an employer)

| Layer | Where | Cost |
|---|---|---|
| Backend (GraphQL API) | **Cloud Run** (unchanged) | free tier |
| Web app (SPA) | **Cloud Run** (same container) | free |
| Android app delivery | **Firebase App Distribution** (Spark free plan) | free |

Cloud Run cannot install a native APK — that's Firebase App Distribution's job. The app calls
the same Cloud Run backend over HTTP. The backend does **not** migrate.

**For an employer to review (least friction first):**
1. **Web link** — the deployed Cloud Run URL (zero install; responsive web shows the mobile
   experience in a phone browser).
2. **APK in GitHub Releases** — attach the signed APK to a release for one-click download.
3. Firebase App Distribution for invited testers; Google Play internal testing (**$25**
   one-time) only later for a public listing.

---

## Styling strategy — avoiding drifting UIs

- **Single design-token source:** extract palette/spacing/type scale (orange `#ea580c`
  primary, `primary-soft`, radii) into tokens and **mirror** into a Compose `Theme.kt`
  (Material 3). Values change once; layout is still authored per platform.
- **Shared contract, not shared markup:** the GraphQL schema + progress formulas are the true
  shared layer.
- Follow `specs/tech-stack.md` product rules on mobile (calm, inline editing, low modal use,
  destructive confirmation).

---

## Execution order

1. ✅ Governance (Part 0): `CLAUDE.md` + hooks + `backlog/`.
2. ✅ Web sync fix + tests (Part 1).
3. ✅ This spec.
4. Backend mobile-auth endpoint (Part 2).
5. Android module MVP: Steps 1–6 (Part 3).
6. Firebase App Distribution + Crashlytics; then FCM; then resources/AI (Part 4 / Steps 7–9).
