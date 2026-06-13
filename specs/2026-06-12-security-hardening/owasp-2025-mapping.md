# OWASP Top 10 (2025) — Coverage Mapping for Spira

This maps every OWASP Top 10 2025 category to Spira's current state and to the
work in `plan.md`. Read it as the audit lens over the same work; the phases
referenced are defined in `plan.md`.

Legend — **Now**: current posture. **Risk**: residual risk today.
**Action**: what closes it (→ plan phase).

---

## A01:2025 — Broken Access Control
- **Now:** Google-only OAuth; every domain query is user-scoped via
  `findByIdAndUserId`; unauthenticated API → `401`; `propose_goal_change` only
  *proposes*, the user approves. GROW memory write checks ownership (404 on a
  foreign goal).
- **Risk (medium):** access control is enforced per-query by convention, not
  centrally — a future query that forgets the user filter would leak data.
  Proposal payloads from the model carry ids that aren't all re-checked for
  ownership server-side.
- **Action:** server-side ownership re-validation of every id in a proposal
  (→ Phase 1.4); an invariant test that goal mutations require an approved,
  owned proposal; a review checklist that every new repository method is
  user-scoped. Consider a cross-cutting `@PreAuthorize`/owner-check helper.

## A02:2025 — Security Misconfiguration
- **Now:** CSRF on (double-submit cookie); session cookie `HttpOnly`/`Lax`/
  `Secure`-in-prod; `forward-headers-strategy=framework` for the proxy; secrets
  from Secret Manager in prod; no stack traces in errors (Boot default
  `include-stacktrace=never`).
- **Risk (high-ish):** CORS allows **LAN wildcard patterns**
  (`http://192.168.*:*`, …) for dev — if they ever leak into the prod
  `CORS_ALLOWED_ORIGINS`, it's a credentialed cross-origin hole. No security
  response headers (HSTS/CSP/nosniff/Referrer-Policy). The earlier nginx
  `/api` gap (now fixed) shows config drift is a live risk.
- **Action:** fail-fast startup assertion that prod (`COOKIE_SECURE=true`)
  has no wildcard/private-range CORS origin; add security headers + a
  conservative CSP (→ Phase 5).

## A03:2025 — Software Supply Chain Failures
- **Now:** npm + Maven lockfiles committed; GitHub Actions partly SHA-pinned;
  workflow `permissions: contents: read`; Docker base images are official.
- **Risk (medium):** no automated dependency-vulnerability scanning; not all
  Actions are SHA-pinned; no image scan; the `.gcloudignore`/`.dockerignore`
  pattern bug we just hit shows build-context fragility.
- **Action:** `npm audit` + OWASP Dependency-Check/Trivy in CI, fail on
  high/critical with an allowlist; SHA-pin all Actions (→ Phase 6).

## A04:2025 — Cryptographic Failures
- **Now:** API keys AES-256-GCM with a per-ciphertext random IV
  (`EncryptionService`); TLS terminated at Cloud Run; OAuth refresh token
  stored **encrypted** (`encRefreshToken`); CSRF/session cookies flagged.
- **Risk (medium):** the AES key is a single env-sourced secret (acceptable
  given the trust model, but no rotation story); sessions now serialize the
  principal (incl. the *encrypted* refresh token) into `spring_session` —
  a DB leak exposes ciphertext, not plaintext, but widens the surface.
- **Action:** document the at-rest model; keep the refresh token out of the
  serialized principal if not needed there; define an AES-key rotation
  procedure; regression test that ciphertext is non-deterministic (→ Phase 4).

## A05:2025 — Injection
- **Now:** JPA/parameterized queries (no string-built SQL); GraphQL typed
  schema; `book_chunk` vector literals are built from `float[]` we produce, not
  user input; React escapes by default; SSRF host-blocking exists.
- **Risk (high):** the headline injection risk here is **LLM prompt injection**
  (direct and indirect via `read_url`/`web_search`/`read_resource`) — the
  modern form of injection for an AI app, and currently undefended. Secondary:
  stored XSS if an uploaded file (`dataUrl`) is ever served as `text/html`.
- **Action:** untrusted-content delimiters + prompt hardening + input safety
  classifier (→ Phase 1.3/1.1); MIME-validate uploads, never serve as HTML
  (→ Phase 5). SSRF re-hardening (→ Phase 3).

## A06:2025 — Insecure Design
- **Now:** strong core design choices — BYOK (no central key trove to steal),
  human-in-the-loop for all AI changes (proposals), per-user data isolation,
  GROW refuses execution work.
- **Risk (medium):** no rate limiting (abuse/cost/DoS by design omission); the
  AI's purpose boundaries are prose-only, not enforced; no abuse-pattern
  visibility.
- **Action:** documented acceptable-use policy enforced at the safety layer
  (→ Phase 1.2), rate limits (→ Phase 2), abuse audit log (→ Phase 1.5). Keep
  the human-approval invariant as a designed control, test-locked.

## A07:2025 — Authentication Failures
- **Now:** delegated to Google OIDC (no passwords to leak/stuff); sessions now
  DB-backed and persistent; `401` on unauth; CSRF protects state changes;
  dev/E2E auth bypasses are profile-gated (`local`/`e2e` only) and never active
  in prod.
- **Risk (low-medium):** no rate limit on the OAuth-start endpoint (login
  flooding); session lifetime is long (14 days — intended for a personal app,
  but worth an explicit logout-everywhere story); confirm the profile-gated
  test filters can't be enabled in prod by env.
- **Action:** per-IP limit on `oauth2/authorization/**` (→ Phase 2); document
  session lifetime + revocation; a test asserting the bypass filters require
  their profile (→ validation).

## A08:2025 — Software or Data Integrity Failures
- **Now:** the approve/reject proposal flow is the integrity gate for goal
  data; proposals persist server-side; CI runs tests before deploy; deploy is
  keyless via Workload Identity Federation (no long-lived SA key).
- **Risk (medium):** proposal payloads are largely trusted as the model emits
  them (kind/fields not fully validated server-side); no integrity check that
  a deployed image matches a reviewed commit beyond branch protection.
- **Action:** server-side proposal schema/ownership validation (→ Phase 1.4);
  keep deploy gated on green tests + `main` only (already so); consider build
  provenance/attestation later.

## A09:2025 — Security Logging and Alerting Failures
- **Now:** Spring + Cloud Run request logs; unhandled GraphQL exceptions are
  logged with type (not full message); secrets are never logged.
- **Risk (high):** no security-event logging — refused AI requests, repeated
  `401`s, ownership-denied attempts, rate-limit hits leave no audit trail; no
  alerting on abuse; the all-`401` flood we diagnosed was only visible by
  manually reading raw request logs.
- **Action:** structured, privacy-safe security events (auth failures,
  AI refusals/crisis, access-denied, rate-limit `429`s) — user id + category +
  timestamp, **never** raw message content (→ Phase 1.5); document a simple
  Cloud Logging alert on spikes. Verify no secret ever appears in logs.

## A10:2025 — Mishandling of Exceptional Conditions
- **Now:** GraphQL has a `DataFetcherExceptionResolver`
  (`GraphQlExceptionHandler`) mapping known exceptions to typed errors;
  Spring hides stack traces by default.
- **Risk (medium):** the **REST/MVC side has no `@ControllerAdvice`** — an
  unexpected error yields the Whitelabel page ("no explicit mapping for
  /error") as seen during the session-serialization 500; unknown GraphQL
  exceptions resolve to `null` → generic error with no correlation id; safety
  classifier failure mode (open vs closed) must be deliberate; SSE streams must
  fail cleanly without leaking internals.
- **Action:** a REST `@ControllerAdvice` returning safe, structured problem
  responses (no internals) + a correlation id for support; explicit
  fail-open/closed policy for the safety classifier (→ Phase 1.1); ensure the
  AI SSE error path (already present) never serializes a stack trace to the
  client. A test that a forced unexpected error returns a clean response, not
  Whitelabel.

---

## Priority order (by exploitability today)
1. **A05 / A06 (AI prompt injection + misuse)** — Phase 1. Largest, most novel.
2. **A05 (SSRF) / A02 (CORS+headers)** — Phase 3 + Phase 5 lockdown.
3. **A06/A07 (rate limiting)** — Phase 2.
4. **A09 (security logging)** — Phase 1.5, woven through.
5. **A01/A08 (proposal validation)** — Phase 1.4.
6. **A04/A10 (secrets-at-rest, error handling)** — Phase 4 + REST advice.
7. **A03 (supply chain scanning)** — Phase 6.

Nothing here is implemented yet; this document is the audit map, not a record
of completed work.
