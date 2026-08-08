# Logging

How Spira records what happens, on all three surfaces, and the rules for adding to it.

Companion doc: `docs/crash-reporting-and-monitoring.md` (what catches crashes) — this one is
about deliberate logging.

| Surface | Sink | How it gets there |
|---|---|---|
| Backend | **Google Cloud Logging** | SLF4J/Logback → stdout as JSON → Cloud Run ingests it |
| Web SPA | **Google Cloud Logging** (via our own backend) | `src/lib/logger.ts` → `POST /api/client-errors` |
| Android | **logcat** + **Firebase Crashlytics** (non-fatals) | `core/SpiraLog.kt` |

---

## 1. Levels — what each one means here

| Level | Means | Examples |
|---|---|---|
| **ERROR** | A request failed in a way we have to fix. **Alertable.** | Unhandled REST/GraphQL exception. |
| **WARN** | Expected-but-notable. Worth looking at in aggregate, not individually. | Rejected sign-in, rate-limit block, a browser error report, a recoverable degradation, an auth-bypass filter being active. |
| **INFO** | Lifecycle facts. A handful per request at most. | Sign-in, logout, startup, GROW library ingestion. |
| **DEBUG** | Developer detail. **Off in production** (`logging.level.com.spiramindscape=INFO`). | SSE frames skipped, provider chunk shapes. |

Two rules that follow from this:

- **Always pass the throwable**, never `e.getMessage()`. SLF4J takes a trailing throwable with no
  `{}` placeholder: `log.warn("goal_save_failed", e)`. A `NullPointerException` logged via
  `getMessage()` prints literally `null` and nothing else.
- **DEBUG is not a safe place for secrets.** It is off today, but "off" is one env-var away from
  on. Anything you would not want in Cloud Logging must not be in a `log.debug` either.

---

## 2. The never-log list

**Never** put any of these into a log statement, at any level, on any surface:

- API keys and their plaintext, `ai.encryption.key`
- Google ID tokens, refresh tokens, access tokens
- Session ids, `SESSION` / `XSRF-TOKEN` cookie values, CSRF tokens
- **The user's own text** — goal titles and descriptions, reality items, options, target titles,
  resource note bodies, uploaded file contents
- **AI prompts and completions**, and raw provider request/response bodies (they are derived from
  the user's text)
- Email addresses and names — log the numeric `userId` instead

**Log a measurement instead of the content.** `chunks.size()`, `data.length()`, or the JSON
*field names* (`GeminiProvider.fieldNames`) answer "what shape was it?" without recording what it
said. This is not hypothetical: `GeminiProvider` shipped a raw model tool-call chunk at **WARN**,
so real users' goal text went to Cloud Logging in production until it was removed.

`LoggingConventionTest` enforces this by scanning the source — see §6.

---

## 3. Event style

Deliberate, greppable events use a `snake_case_event` first token followed by `key=value` pairs,
modelled on `ai/safety/AbuseAuditLogger.java`:

```
auth_signin_success method=mobile userId=42
rate_limit_block limit=ai-chat caller=u:42 path=/api/ai/chat
web_client_error kind=render name=TypeError url=https://spira.app/goals userId=anonymous
```

They go to **named loggers**, so they can be filtered independently of the class that emitted them:

| Logger | What |
|---|---|
| `security.auth` | Sign-in success/failure, logout, auth-bypass tripwire (`AuthAuditLogger`) |
| `security.ai-safety` | Safety verdicts (`AbuseAuditLogger`) |
| `security.ratelimit` | 429s (`RateLimitFilter`) |
| `client.web-error` | Browser error reports (`ClientErrorController`) |

`reason=` values must be **fixed codes** (`token_invalid`, `account_conflict`), never a message
built from user input.

---

## 4. Correlation: `traceId` and `userId`

`RequestLogContextFilter` puts two values in the SLF4J **MDC** at the very start of every request,
and clears them in a `finally` block:

| MDC key | Value |
|---|---|
| `traceId` | Cloud Run's inbound `X-Cloud-Trace-Context` id, or a generated 32-hex id |
| `userId` | The backend's numeric user id, set by `CurrentUserProvider` once the principal resolves |

`GoogleCloudStructuredLogFormatter` lifts both to **top-level JSON keys**, so they are queryable
as `jsonPayload.traceId`. The same id is:

- returned to the client in the `X-Trace-Id` response header,
- included in a REST 500's `correlationId` property and a GraphQL error's
  `extensions.correlationId`,
- shown to the user as `Reference: …`.

So a user quoting a reference gives you every line of their request, not one.

**The `finally` is not optional.** Tomcat pools threads; a missed clear puts one user's id on
another user's log line. `RequestLogContextFilterTest` covers both the normal path and the
throwing path.

### Two known gaps

- **SSE worker threads.** The AI chat streams on threads outside the request, so MDC is not
  present there. Pass the id explicitly where it matters rather than installing a task decorator.
- **Async data fetchers.** Every GraphQL resolver is synchronous JPA today, so MDC is live for
  fetchers and for `GraphQlExceptionHandler`. If one ever returns a `CompletableFuture`/`Mono`
  resolved on another executor, add a `WebGraphQlInterceptor` + Reactor context propagation then.

---

## 5. Why the backend writes JSON (and why it must)

Cloud Run pipes container stdout to Cloud Logging, which can only read a severity out of **JSON**.
With Logback's default plain-text pattern, every line — including `log.error` — ingests as
`DEFAULT`, and a multi-line stack trace splits into one entry per line. Log-based error alerting
silently does not work.

Spring Boot 3.5 ships structured formatters for ECS, GELF and Logstash but **not** for GCP, so
there is no `logging.structured.format.console=gcp` to set. The property does accept a class name,
which is how `GoogleCloudStructuredLogFormatter` is wired:

```properties
# application.properties (prod — CI deploys with no active profile)
logging.structured.format.console=com.spiramindscape.backend.logging.GoogleCloudStructuredLogFormatter
spira.gcp.project-id=${GOOGLE_CLOUD_PROJECT:}
```

The dev/local/e2e/test profiles set `logging.structured.format.console=` (empty) to restore the
human-readable console.

Two details in that class carry most of the value:

- **`WARN` is written as `"WARNING"`.** Cloud Logging does not recognise `WARN` and quietly
  downgrades the entry to `DEFAULT` — so every `log.warn` would be invisible to a
  `severity>=WARNING` alert.
- **The stack trace goes inside `message`**, not a sibling field. That keeps it in one entry and
  lets Cloud Error Reporting group occurrences of the same exception.

---

## 6. Reading the logs

Cloud console → **Logging → Logs Explorer**. All queries assume
`resource.type="cloud_run_revision"`.

```
# Everything that actually failed
severity>=ERROR

# One user's whole request, from the reference they quoted
jsonPayload.traceId="105445aa7843bc8bf206b12000100000"

# Everything one user did
jsonPayload.userId="42"

# Sign-in problems
jsonPayload.message:"auth_signin_rejected"

# Someone hitting a limit — is the limit too tight, or is this abuse?
jsonPayload.message:"rate_limit_block"

# Browser errors (BUG-005's replacement for Sentry)
jsonPayload.message:"web_client_error"

# CATASTROPHE ALERT: an auth-bypass filter is running in production
jsonPayload.message:"auth_bypass_filter_active"
```

**Worth setting as log-based alerts** (start with two, add more only when something bites):
a spike in `severity>=ERROR`, and *any* occurrence of `auth_bypass_filter_active`.

### Changing the level in production

```
gcloud run services update spira --region europe-west1 \
  --update-env-vars LOGGING_LEVEL_COM_SPIRAMINDSCAPE=DEBUG
```

Costs one revision restart (~10s). **Remember to revert it** — and re-read §2 first: DEBUG turns
on the provider diagnostics.

---

## 7. The web

`src/lib/logger.ts` is the only place the SPA logs. It has two modes:

- **Development** — console only, with full context including `SpiraApiError.details` (the raw
  backend GraphQL messages, which are invaluable when debugging and unsendable in production).
- **Production** — `logger.reportError` posts a fixed-shape record to `POST /api/client-errors`.

`logger.warn` / `debug` / `info` are **always local**. Only `reportError` reaches the server.

What is deliberately **not** reported:

| Case | Why |
|---|---|
| `status === 401` | The session expired; the store already redirects to `/login`. |
| `kind === "network"` | The user is offline or the backend is down — not a defect. |
| A repeat of an error already sent | A render loop would otherwise fire hundreds of beacons. |
| Anything past 5 reports in one page session | Same. |
| `SpiraApiError.details`, raw GraphQL messages | **They can echo what the user typed.** Only the classification and status code are sent. |

Transport is `navigator.sendBeacon` (the only one that survives a page unload) with a
`keepalive` fetch fallback. It never awaits and never throws — reporting an error must not be able
to cause one.

**A crash that kills the tab reports nothing.** If the OS terminates the renderer (the
attachment-OOM case, BUG-022), no JavaScript runs at all — no `catch`, no `onerror`, no beacon.
That needs crash-surviving breadcrumbs in `localStorage`, flushed after the reload; the endpoint
already accepts them as `kind: "crash-trail"`. See
`specs/2026-08-03-attachment-crash-diagnostics/requirements.md`.

### The endpoint

`POST /api/client-errors` is deliberately **unauthenticated** and **CSRF-exempt**. Both are
required — the SPA shell renders for logged-out users, and `sendBeacon` cannot set a header — and
both are safe because the endpoint's only effect is one log line. Three compensating controls:

1. A fixed-field `record` (no `Map<String,Object>`, no `@JsonAnySetter`) — a client cannot ship
   arbitrary data.
2. Length caps on every field, enforced by bean validation (400 on violation).
3. `RateLimitFilter` throttles it to 10/min per caller.

---

## 8. Android

`core/SpiraLog.kt` is the only place the app logs. One call writes to **logcat** and records a
**Crashlytics non-fatal**:

```kotlin
SpiraLog.w(TAG, "goal_delete_failed goalId=$goalId", e)
```

Crashlytics already captures crashes on its own. The problem `SpiraLog` solves is the opposite:
~30 `catch` blocks that recovered silently, so a failed save produced no signal anywhere.

**It can never throw.** Both the Crashlytics call and the `android.util.Log` call are guarded:
without `google-services.json` (CI, a fresh clone) `FirebaseApp` is never initialized and
`getInstance()` throws, and on a plain JVM `android.util.Log` is an unmocked stub that throws too.
A logging helper that propagated either would be the thing that breaks the app — or the build.

`SpiraLog.setUserId(user.id)` attributes crashes to a user, using the backend's **numeric** id (an
opaque surrogate key). Never the email or name.

### What to log, and what not to

Log when **failure is invisible to the user AND loses something**. Concretely:

| Log it | Leave it silent |
|---|---|
| `putTranscript` — the whole conversation silently unsaved | `getProvider` — falls back to a sane default |
| `listKeys` — a failure looks like "no key saved" | `saveKey` — the error already reaches the UI via `onResult` |
| `deleteGoal`, `createGoal` — the user acted and nothing happened | The refetch inside `mutateThenReload` — retry of a retry |
| `DateFormatting`'s parse failures — **a malformed date is expected input, not a defect; logging it is pure noise** |

### HTTP logging

Debug builds add an OkHttp `HttpLoggingInterceptor` at **`Level.BASIC`, never `BODY`** — request
and response bodies carry goal text, notes and whole AI conversations, and logcat is readable by
anything with `adb`. `Cookie`, `Set-Cookie`, `Authorization` and `X-XSRF-TOKEN` are redacted.
`NetworkTest` asserts the level, so nobody can flip it to `BODY` "just to debug something" and
ship it.

---

## 9. Tests that guard all this

| Test | Guards |
|---|---|
| `GoogleCloudStructuredLogFormatterTest` | `WARN`→`WARNING`; the stack trace inside `message`; exactly one newline; MDC lifted to top level; Boot can instantiate the class by name |
| `RequestLogContextFilterTest` | The id is set, reused across the async dispatch, and **cleared even when the chain throws** |
| `GraphQlExceptionHandlerTest` | The throwable is logged; the client gets a reference and no internals |
| `ClientErrorControllerTest` | Size/kind validation; extra JSON fields cannot smuggle data into the log |
| `SecurityIntegrationTest` | `/api/client-errors` is public for POST only; the rest of `/api/**` still 401s |
| **`LoggingConventionTest`** | Source scan: no secrets in a log call, no user content above DEBUG, no `System.out`/`printStackTrace`. **Catches the next leak, which no runtime test can.** |
| `logger.test.ts` | Dev vs prod behaviour, truncation, dedupe, the session cap, and that `details` never leaves the browser |
| `ErrorBoundary.test.tsx` | A thrown child renders the error screen and reports once |
| `SpiraLogTest` | Never throws when Firebase is unavailable; logcat still works |
| `NetworkTest` | HTTP logging is `BASIC`, never `BODY` |

`LoggingConventionTest` has an `ACCEPTED` map for a reviewed trade-off. **Prefer sharpening the
heuristic (or renaming a misleading variable) over adding an entry** — but when a trade-off is
genuinely intentional, write it down there with its reasoning rather than weakening the pattern.

---

## See also

- `docs/crash-reporting-and-monitoring.md` — Crashlytics, and the web gap this closed
- `docs/security-model.md` §9 — safe error handling
- `specs/2026-06-12-security-hardening/additional-threats.md` — the alerting plan
- `specs/2026-08-03-attachment-crash-diagnostics/requirements.md` — breadcrumbs for a crash that
  kills the tab
