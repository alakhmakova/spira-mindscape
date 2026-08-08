# Web frontend has no client-side error tracking (no Crashlytics/Sentry equivalent)

- **ID:** BUG-005
- **Status:** ✅ Fixed
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

Fixed **2026-08-07** — with our **own backend endpoint instead of Sentry**.

The "Fix approach" above recommended `@sentry/react`. We went the other way, deliberately: the
reports now go to `POST /api/client-errors` on the same origin, and `ClientErrorController` logs
them at WARN into the same Cloud Logging the backend already writes to. That means zero external
services, zero third-party PII egress, zero cost, and — because the backend stamps every request
with a `traceId` — a browser report lands next to the server line for the same request. Sentry's
advantage would have been a ready-made grouping dashboard; at this scale a Logs Explorer query
does the job, and not shipping users' data to a third party is worth more.

**What was added**

- `src/lib/logger.ts` — the SPA's only logging path. Dev: console, with `SpiraApiError.details`
  (computed since forever and displayed nowhere until now). Prod: `navigator.sendBeacon` →
  `/api/client-errors`, with a `keepalive` fetch fallback.
- `src/components/ErrorBoundary.tsx` + `src/components/spira/ErrorScreen.tsx` — wrapped around
  `<RouterProvider>` in `src/main.tsx`; `window.onerror` and `unhandledrejection` listeners catch
  what React never sees. `src/router.tsx`'s error component reports too (from an effect, so
  StrictMode's double render doesn't double-report).
- The 9 raw `console.*` calls were replaced; `store.ts`'s `setSyncError` — the funnel for ~20
  optimistic writes — is the single highest-value hook.
- `backend/.../web/ClientErrorController.java`, plus `permitAll` + CSRF exemption in
  `SecurityConfig` and a 10/min entry in `RateLimitFilter`.

**Privacy decisions (the part worth re-reading before changing it)**

- `SpiraApiError.details` and raw GraphQL messages are **never sent** — they can echo submitted
  field values. Only `classification` and `status` go over the wire; details stay dev-console-only.
- The reported URL is stripped of its query string.
- The request body is a fixed-field `record` — no `Map<String,Object>`, no `@JsonAnySetter` — so a
  client physically cannot ship extra data. Covered by a test that posts `{"password":"hunter2"}`
  and asserts it never reaches the log.
- Expected failures (401, offline) are not reported at all; identical errors are sent once; a page
  session sends at most 5.

**How to verify**

- `npm run build && npm run preview`, throw a deliberate error in a component → exactly one 204 to
  `/api/client-errors` in the Network tab and one WARN in the backend console.
- In production: Logs Explorer → `jsonPayload.message:"web_client_error"`.
- Automated: `src/lib/logger.test.ts`, `src/components/ErrorBoundary.test.tsx`,
  `ClientErrorControllerTest`, and `SecurityIntegrationTest` (the endpoint is public for POST only;
  the rest of `/api/**` still 401s).

**Still not covered — and it is not fixable this way:** a crash that kills the tab. When the OS
terminates the renderer (BUG-022's attachment OOM), no JavaScript runs, so no boundary, `onerror`
or beacon can fire. That needs crash-surviving breadcrumbs in `localStorage` flushed after the
reload — the endpoint already accepts them as `kind: "crash-trail"`. See
`specs/2026-08-03-attachment-crash-diagnostics/requirements.md`.

Documented in `docs/logging.md` and `docs/crash-reporting-and-monitoring.md` §4.
