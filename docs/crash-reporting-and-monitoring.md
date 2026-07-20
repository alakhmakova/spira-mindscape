# Crash reporting & monitoring

How Spira learns that something broke — on the Android app, and on the web. This doc explains
**what** each tool is, **why** we use it, and **how** to set it up and read it.

TL;DR:

| Surface | Tool | Catches | Status |
|---|---|---|---|
| Android app | **Firebase Crashlytics** | app crashes + non-fatal errors, automatically | ✅ wired |
| Backend (API) | **Google Cloud Logging** (Cloud Run) | server exceptions, request logs | ✅ built in |
| Web frontend (browser) | — | client-side JS errors | ❌ none yet (see §4) |

---

## 1. What is Crashlytics, and why?

**Firebase Crashlytics** is a crash-reporting service. When the Android app throws an uncaught
exception and dies ("Spira keeps stopping"), Crashlytics captures the stack trace, the device
model, OS version, and app version, and uploads it to the Firebase console the next time the
app has network. You see a grouped, de-duplicated list of crashes with counts and affected
users — **without the tester ever sending you a log**.

Why we want it: our testers (and an employer reviewing the app) run the build on real phones we
don't control. Before Crashlytics, the only way to diagnose a crash like BUG-004 was to ask the
user to reproduce it and read Logcat over their shoulder. With Crashlytics, the next crash
reports itself with a full stack trace. It's the mobile equivalent of "server logs for a phone
in someone else's pocket."

It also records **non-fatal** exceptions you log deliberately (`recordException(e)`) and
**breadcrumbs** (`log("...")`) that show what happened right before a crash.

---

## 2. How it's wired in this repo

Crashlytics needs the Firebase config file and two Gradle plugins. Because that config file is
gitignored, we apply the plugins **conditionally** so CI (which has no config) still builds.

- **`android/build.gradle.kts`** declares the plugin versions (`apply false`):
  `com.google.gms.google-services` and `com.google.firebase.crashlytics`.
- **`android/app/build.gradle.kts`**:
  - Applies both plugins **only if `app/google-services.json` exists**:
    ```kotlin
    val hasGoogleServices = file("google-services.json").exists()
    if (hasGoogleServices) {
        apply(plugin = "com.google.gms.google-services")
        apply(plugin = "com.google.firebase.crashlytics")
    }
    ```
  - Adds the SDKs via the Firebase BoM:
    ```kotlin
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    ```
- **No application code is needed.** Crashlytics auto-initializes through a Firebase
  `ContentProvider` at app startup and installs a default uncaught-exception handler, so every
  crash is captured out of the box. (There is no custom `Application` class to wire.)

### Why the conditional apply?

`google-services.json` holds a Firebase API key, so it is **gitignored** (see the mobile setup
guide). It's present on the developer's machine but **absent on CI and on a fresh clone**. The
google-services Gradle plugin fails the build if the file is missing. Applying the plugins only
when the file exists means:

- **Local / distributed builds** (file present) → Crashlytics active, crashes reported.
- **CI** (`.github/workflows/ci.yml`, file absent) → plugins skipped, build + tests still pass.
- **Fresh clone without the file** → still builds; you just won't get crash reports until you
  drop in the config (see the mobile setup guide, §C).

This was verified both ways: tests are green with the file present, and `compileDebugKotlin`
succeeds with the file temporarily removed.

---

## 3. How to set it up / use it

### There is NO "Enable" button — and that's expected
Crashlytics has **no on/off toggle** to click. It turns itself on automatically the moment the
**first crash report** arrives from a real device. So in the Firebase console
([Crashlytics](https://console.firebase.google.com/project/project-10702811-5962-4bf3-877/crashlytics))
you will see an onboarding screen titled **"Crashlytics"** with an **"Add SDK"** button and setup
steps. **Ignore that screen and do NOT click "Add SDK"** — that wizard is only for projects that
haven't added the SDK yet, and **we already added it in the app's code** (the Gradle plugins +
`firebase-crashlytics` dependency, see §2).

What actually happens: as soon as the app (from a build you distribute via App Distribution)
crashes somewhere and the report uploads, that "Add SDK" screen **automatically switches** to the
real dashboard listing the crashes. Nothing to press in between.

The only prerequisite (already true here) is that `android/app/google-services.json` is in place
— it is locally (it's gitignored). A new machine downloads it from the console: Project settings
→ your Android app → `google-services.json`.

### Viewing crashes
1. Build & distribute as usual: `.\gradlew.bat :app:distributeDebug -PreleaseNotes="..."`.
2. A tester installs it and uses the app. On a crash, the report uploads on the **next launch**
   with network (Crashlytics batches, so it's not always instant).
3. Firebase console → **Crashlytics** → the "Add SDK" onboarding is replaced by the dashboard,
   which lists crashes grouped by root cause, with device/OS/version breakdowns and the full
   stack trace.

### Verifying it works
We are **waiting for a real crash** rather than forcing one — the code is wired and will report
the next genuine crash automatically. (If you ever want to confirm the pipeline immediately, you
can temporarily add a line that throws — `throw RuntimeException("Test Crash")` — run it once, and
remove it; the report shows up in the console a few minutes later. Not needed for normal use.)

### Optional next steps (not done yet — add when useful)
- **Tie crashes to a user:** after sign-in, call
  `FirebaseCrashlytics.getInstance().setUserId(user.id)` so a crash report names who hit it.
- **Breadcrumbs:** `FirebaseCrashlytics.getInstance().log("opened goal $goalId")` before risky
  work; the last logs appear alongside the crash.
- **Non-fatals:** in a `catch` where we recover but want visibility,
  `FirebaseCrashlytics.getInstance().recordException(e)`.

These are deliberately deferred — auto crash capture is the high-value part; the rest we add
when a real need shows up (no telemetry theater).

---

## 4. Does the web have anything like Crashlytics?

Short answer: **the backend has server-side logging; the browser frontend has no crash/error
reporting yet.** Here's the honest picture.

### Backend (the GraphQL API) — yes, via Cloud Logging
The Spring Boot backend logs with SLF4J/Logback to stdout. It runs on **Google Cloud Run**,
which automatically ships stdout/stderr to **Google Cloud Logging**. So server-side exceptions,
stack traces, and request logs are all captured and searchable in the Google Cloud console
(Logging → Logs Explorer), and you can set **log-based alerts** on error-rate spikes or `401`/
`5xx` bursts (this is exactly the monitoring approach noted in
`specs/2026-06-12-security-hardening/additional-threats.md`). Errors returned to clients are
sanitized by a central handler (see `docs/security-model.md` §9 "Safe error handling"), so we
don't leak internals while still logging the detail server-side.

So for anything that breaks **on the server**, you already have visibility — it's Crashlytics's
equivalent for the backend, just built into Cloud Run rather than a separate SDK.

### Frontend (React SPA in the browser) — **no, this is a real gap**
If a user hits a JavaScript error in the browser, **you are not notified**. Today the frontend
only:
- logs sync failures to the browser console (`console.error("Spira sync failed", …)` in
  `src/lib/spira/store.ts`) — visible **only** in that user's own devtools, never sent anywhere;
- shows a friendly error state in the UI when a request fails.

There is **no** client-side error-tracking service (no Sentry, LogRocket, Bugsnag, etc.) and no
global `window.onerror` reporter. A crash in someone else's browser leaves no trace you can see.

### Recommendation (not yet implemented)
The web equivalent of Crashlytics is **[Sentry](https://sentry.io)** (or a similar
browser-error service). Adding `@sentry/react` would give the frontend the same
"errors report themselves" property the Android app now has — grouped JS exceptions with stack
traces, browser/OS, and the user action that led to them. It has a free tier and is a small,
additive change (init in `main.tsx` + an error boundary). This is worth doing before a wider
public launch, but it's **not wired today** — stating that plainly so the gap is known, not
hidden. Tracked in `backlog/web-frontend-no-error-tracking.md` (BUG-005). If/when we do it, it
belongs in this doc alongside Crashlytics.

---

## See also
- `docs/mobile-setup-guide.md` — Firebase project setup, `google-services.json`, App Distribution.
- `docs/security-model.md` §9 — safe error handling on the backend.
- `specs/2026-06-12-security-hardening/additional-threats.md` — Cloud Logging alerting.
