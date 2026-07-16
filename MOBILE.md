# Spira — Native Android App

A native **Kotlin / Jetpack Compose** Android app for Spira, the goal-tracking product. It
**reuses the exact same backend** as the web app — the GraphQL API and Google sign-in — so one
person has **one account and one set of data** across desktop web, responsive mobile web, and
this native app.

> **Status: MVP complete (2026-07-16).** You can sign in with Google, see your goals, open a
> goal, and update its targets, with data staying in sync with the web. Not yet built: resource
> viewing, the AI coach, push notifications, and visual design polish (see *Roadmap* below).

This document is the **overview**. Deeper docs are linked at the bottom.

---

## What it is (architecture in a paragraph)

The app is a separate module at [`android/`](android/). It talks to the same Spring Boot +
GraphQL backend the web app uses. Sign-in is Google-only: the app gets a Google **ID token**
on-device (Credential Manager), posts it to a small backend endpoint (`POST /api/auth/google/mobile`),
and gets back the **same server-side session** the web login uses — so everything after login
(GraphQL, CSRF, per-user isolation) is identical to the web. GraphQL is accessed with **Apollo
Kotlin** (type-safe generated models from the shared schema).

- **UI:** Jetpack Compose (Material 3), navigation via Navigation-Compose.
- **Data:** Apollo Kotlin (GraphQL) over OkHttp, with a cookie jar for the session + a CSRF
  interceptor.
- **Auth:** Credential Manager (Google) → backend token verification → server session.

## What's done (the MVP)

| Area | Status |
|---|---|
| Project scaffold, theme (mirrors the web's orange), adaptive icon | ✅ |
| GraphQL layer (Apollo Kotlin) from the shared schema | ✅ |
| Google sign-in → backend session, sign-out, auto-resume | ✅ |
| Goals **dashboard** (cards: title, progress, confidence, deadline) | ✅ |
| Goal **workspace** (the five sections) | ✅ |
| **Low-friction target updates** — done/not-done, numeric ± stepper, checklist | ✅ |
| Data freshness — re-fetch on app resume | ✅ |
| Distribution via **Firebase App Distribution** | ✅ |
| **Crashlytics** — automatic crash reporting from testers' devices | ✅ |
| **Push notifications (FCM)** — device registration + backend sender | ✅ pipeline (auto reminders TBD) |

## Roadmap (not yet built)

- Resource viewing (notes, links, files, email).
- AI coach chat + approval workflow.
- Push notification **reminders** — the delivery pipeline is built (see docs below); the
  scheduled triggers (deadlines / daily focus) are still to come.
- A release (Play-signed) build; optional Google Play listing.
- A **visual design pass** — the screens are a functional MVP and don't yet match the web's
  polished look (native UI is authored separately from the web; shared design *tokens* keep the
  colors aligned).

## Tests & quality

- **37 automated tests** (unit + Robolectric + Compose UI), all run on the JVM without a device:
  - Auth state machine, REST client (MockWebServer), CSRF header, cookie jar, network init.
  - Goals dashboard + goal workspace view models, and Compose UI tests for the screens.
- **Coverage:** a curated JaCoCo report (`:app:jacocoDebugReport`) that excludes generated +
  UI code. Note: Robolectric-run classes don't register in JaCoCo, so the raw % understates real
  coverage — see [`docs/reading-coverage-reports.md`](docs/reading-coverage-reports.md).
- **CI:** an `android` job in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs the
  unit tests + coverage on every push.
- **E2E:** a **Maestro** flow ([`android/.maestro/login.yaml`](android/.maestro/login.yaml)) for
  on-device journeys — see [`docs/maestro-e2e-guide.md`](docs/maestro-e2e-guide.md).

## Try it without building

- **Firebase App Distribution** — the app is distributed to invited testers; accept the emailed
  invite on an Android phone, install the "App Tester" app, then install Spira. Works from any
  network (the app talks to the production backend over the internet).
- **GitHub Release APK** *(when published)* — download `app-debug.apk` from the repo's
  **Releases** page and sideload it (enable "install unknown apps"). No invite needed.

## Run it locally from the repo

1. **Prerequisites:** Android Studio, and a JDK **17–21** (Android Studio's embedded JDK works;
   the system JDK 22 does not). Full one-time setup (SDK, Firebase, the Google OAuth client that
   sign-in needs) is in [`docs/mobile-setup-guide.md`](docs/mobile-setup-guide.md).
2. **Open the `android/` folder** in Android Studio (not the repo root), let Gradle sync.
3. **Run** on a device or an emulator (`Run ▶`). The emulator reaches a local backend at
   `http://10.0.2.2:8080`; by default the app uses the **production** backend, so it works with
   just internet.
4. **Terminal build:** `cd android; .\gradlew.bat :app:assembleDebug` (APK in
   `app/build/outputs/apk/debug/`); `:app:testDebugUnitTest` runs the tests.

See [`android/README.md`](android/README.md) for the exact toolchain versions and commands.

## Design decisions & docs

- **Why Gradle here, Maven for the backend, npm for the web:**
  [`docs/build-tools.md`](docs/build-tools.md).
- **Apollo / GraphQL on Android:** [`docs/apollo-graphql-guide.md`](docs/apollo-graphql-guide.md).
- **Mobile auth design (why token-based, session reuse, hardening):**
  [`docs/google-oauth-implementation-guide.md`](docs/google-oauth-implementation-guide.md) §10
  and [`docs/security-model.md`](docs/security-model.md) §2.
- **Crash reporting & monitoring (Crashlytics; and what the web has):**
  [`docs/crash-reporting-and-monitoring.md`](docs/crash-reporting-and-monitoring.md).
- **Push notifications (FCM pipeline, setup, verification):**
  [`docs/push-notifications-guide.md`](docs/push-notifications-guide.md).
- **Full plan / requirements / validation:**
  [`specs/2026-07-15-native-mobile-app/`](specs/2026-07-15-native-mobile-app/).
- **Known items / bugs:** [`backlog/`](backlog/) (e.g. the "error 10" sign-in fix and the
  release cleartext-HTTP config).
