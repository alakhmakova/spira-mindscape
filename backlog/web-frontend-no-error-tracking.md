# Web frontend has no client-side error tracking (no Crashlytics/Sentry equivalent)

- **ID:** BUG-005
- **Status:** 🐞 Open
- **Reported by:** User
- **Area:** Web frontend (React SPA)
- **Severity:** Low (no user-facing bug; an observability gap)

## Summary

The Android app now reports crashes automatically via Firebase Crashlytics
(`docs/crash-reporting-and-monitoring.md`). The web frontend has no equivalent: **if a user
hits a JavaScript error in their browser, nothing reaches us** — there is no Sentry / LogRocket /
Bugsnag, and no global `window.onerror` reporter. Today the frontend only logs sync failures to
the user's own browser console (`console.error("Spira sync failed", …)` in
`src/lib/spira/store.ts`) and shows a friendly error UI; none of that is ever sent anywhere we
can see. So a crash in someone else's browser leaves no trace we can inspect.

(The **backend** is covered — Cloud Run ships logs to Google Cloud Logging, and errors are
sanitized by `RestExceptionHandler` per `docs/security-model.md` §9. This gap is specifically
the **browser** side.)

## Steps to reproduce

1. Trigger a runtime JS error in the deployed SPA (e.g. an unexpected `undefined` in a render
   path).
2. Observe: the user may see a broken screen; we receive no report, alert, or stack trace.

## Root cause

No client-side error-monitoring service is wired into the frontend. There is no error boundary
reporting to a backend/service and no `window.onerror` / `unhandledrejection` handler that
forwards errors anywhere.

## Fix approach

Add **Sentry** (`@sentry/react`, free tier) — the web equivalent of what the Android app now
has: grouped JS exceptions with stack traces, browser/OS, and the user action that led to them.
It's a small, additive change:

- `Sentry.init(...)` in `src/main.tsx` (DSN from env, not committed).
- Wrap the app in a Sentry error boundary (or add `window.onerror` / `unhandledrejection`
  forwarding).
- Scrub PII before send (Spira's security model forbids leaking user content); send only what's
  needed to debug.

Recommended **before a wider public launch**. Not urgent while the user base is small and the
developer is close to the app.

## How to verify fixed

- Throw a deliberate error in a component in a staging build; confirm it appears in the Sentry
  dashboard with a usable stack trace and no PII.
- An `unhandledrejection` (e.g. a failed fetch not caught) is also captured.

## Resolution

_(empty — open)_
