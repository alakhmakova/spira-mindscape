# Additional Modern Threats — beyond the OWASP Top 10 mapping

`owasp-2025-mapping.md` covers the web Top 10. This file captures the threats
that matter for an **AI app + API + OAuth + managed Postgres** that the Top 10
under-emphasizes: API-specific abuse, AI/LLM-specific risks, authn/authz edge
cases, **database backup & disaster recovery**, privacy/data-rights, and
operational resilience. Same format: **Now / Risk / Action**. Nothing here is
implemented yet.

---

## 1. API-specific (OWASP API Security Top 10 lens)

### 1.1 GraphQL abuse (no Top-10 entry fits cleanly)
- **Now:** single `/graphql` endpoint, typed schema, auth required.
- **Risk:** GraphQL-specific DoS — deeply nested queries, query aliasing/
  batching to multiply work, field duplication; schema **introspection** may
  be enabled in prod, advertising the attack surface; no query depth/complexity
  cap.
- **Action:** disable introspection in prod; add query depth + complexity
  limits and a max-aliases/batch guard; per-operation cost in the rate limiter
  (ties to Phase 2).

### 1.2 BOLA / IDOR (Broken Object-Level Authorization)
- **Now:** repository methods are user-scoped by convention.
- **Risk:** the #1 real-world API bug — any single query/mutation that forgets
  the owner filter leaks another user's object by id. Proposal payloads carry
  ids not all re-checked.
- **Action:** a centralized owner-check helper used by every by-id fetch; a
  test that probes each query/mutation with a foreign id and expects denial
  (overlaps A01 in the OWASP map).

### 1.3 Mass assignment / over-posting
- **Now:** DTOs + bean validation on inputs.
- **Risk:** a mutation input that binds fields the user shouldn't set (e.g.
  `assignee`, ownership, status, ids) lets a client escalate by sending extra
  fields.
- **Action:** explicit input types per mutation (no entity binding); server
  ignores/rejects client-supplied server-owned fields; test with crafted
  over-posted payloads.

### 1.4 Unrestricted resource consumption
- **Now:** note 50k chars, upload 5 MB, AI tool-iteration cap.
- **Risk:** no global rate limit (Phase 2); no per-user storage quota (a user
  could create unbounded goals/resources → DB bloat / cost); SSE connections
  not capped.
- **Action:** per-user quotas on object counts + total storage; cap concurrent
  SSE streams per user; rate limits (Phase 2).

## 2. AI / LLM-specific (OWASP LLM Top 10 lens)

### 2.1 Denial-of-wallet
- **Now:** BYOK (the *user* pays), tool-iteration cap.
- **Risk:** an authenticated user (or a hijacked session) can still burn the
  user's own provider budget, and the planned background AI jobs spend
  unattended; embedding the whole book corpus is one Mistral bill per first
  session.
- **Action:** per-user/day AI request + token budgets, the job caps from the
  delegated-tasks spec, and an owner-visible usage counter (Phase 2 + jobs
  spec).

### 2.2 RAG corpus poisoning / integrity
- **Now:** the GROW library is owner-curated `.txt` files, ingested at startup,
  embedded with the user's key. Not user-editable — low risk today.
- **Risk:** if book ingestion ever accepts user uploads, a poisoned passage
  could steer coaching; embeddings have no integrity check.
- **Action:** keep the corpus owner-only; if that changes, validate/scan
  sources and record a content hash per chunk.

### 2.3 Sensitive-information disclosure / system-prompt leak
- **Risk:** the model could be coaxed to reveal its system prompt, other
  resources, or another user's data echoed into context.
- **Action:** prompt hardening (Phase 1.3); never place another user's data in
  context (enforced by user-scoping); treat the system prompt as non-secret but
  instruct against verbatim disclosure; test exfiltration attempts.

### 2.4 Insecure output handling
- **Now:** AI output is rendered as markdown; changes go through proposals.
- **Risk:** AI-generated HTML/links rendered in notes could carry XSS or
  malicious links; markdown rendering must sanitize.
- **Action:** sanitize AI-produced HTML (the note path already expects simple
  HTML — enforce an allowlist sanitizer); mark external links `rel="noopener
  noreferrer"`; verify the markdown renderer escapes scripts.

### 2.5 Excessive agency
- **Now:** the AI can only *propose*; the user approves every change; GROW
  refuses execution.
- **Risk:** the planned background jobs increase agency — that spec already
  scopes tools and forbids out-of-scope proposals; keep it strict.
- **Action:** the delegated-tasks rails are the control; never let the model
  self-expand its toolset or create its own jobs.

### 2.6 Multi-turn jailbreak
- **Risk:** safety that only checks the latest message misses an attack
  assembled across turns.
- **Action:** the classifier should consider recent context, not just the last
  message, for the verdict (Phase 1.1).

## 3. Authentication & Authorization edge cases

- **Session fixation:** confirm Spring rotates the session id on login
  (`changeSessionId`, the default) — test it.
- **Logout-everywhere / revocation:** with 14-day DB sessions, add the ability
  to invalidate all of a user's sessions (delete their `spring_session` rows) —
  useful on suspected compromise.
- **OAuth flow integrity:** verify `state` (CSRF on the OAuth handshake) and,
  ideally, PKCE are enforced by Spring's client; confirm the redirect URI
  allowlist is exact (no open redirect); the success handler must not reflect
  an attacker-controlled `redirect` param.
- **Refresh-token handling:** stored encrypted — confirm it's only used
  server-side, never sent to the client, and rotated/revoked on logout.
- **Account lifecycle:** define what happens on Google account loss — there is
  no password recovery by design; document it. Email change in Google shouldn't
  fork identity (we key on `sub`, good — test it).
- **Privilege model:** `role` exists ("USER"); if an admin role is ever added,
  it needs server-side enforcement, not a client flag.

## 4. Database — backups & disaster recovery (currently unaddressed)

- **Now:** Neon PostgreSQL (free tier). Flyway migrations are versioned in the
  repo. No documented backup/restore or DR procedure. **This is a real gap:**
  all user goals, AI keys (encrypted), sessions, and the coaching memory live
  in one Neon project with no stated recovery plan.
- **Risks:** accidental destructive migration or `DELETE`; Neon project loss/
  suspension (free tier); ransomware/compromise; no point-in-time recovery
  story; no tested restore.
- **Action:**
  - **Automated backups:** enable/script regular `pg_dump` to a separate bucket
    (GCS) on a schedule (Cloud Scheduler → a small job), encrypted, with
    retention (e.g. daily 7 / weekly 4). Don't rely solely on Neon's tier-
    limited PITR.
  - **Restore drills:** document and *actually test* a restore into a scratch
    DB — an untested backup is not a backup.
  - **Migration safety:** review destructive Flyway migrations; take a backup
    before deploys that alter/drop columns; keep migrations forward-only and
    reversible-by-design where possible.
  - **Encryption-key backup:** `AI_ENCRYPTION_KEY` must be backed up securely
    and separately — losing it makes every stored API key unrecoverable; this
    is as critical as the data backup.
  - **DR runbook:** written steps + RTO/RPO targets for "Neon is gone" and
    "Cloud Run is gone".

## 5. Privacy & data rights

- **Now:** per-user data, Google sign-in, Drive scope for resources.
- **Risk:** no data export, no account-deletion path, no retention/PII policy —
  relevant if anyone beyond the owner uses it (GDPR-style expectations).
- **Action:** a user-data export and a hard-delete (cascade goals, resources,
  proposals, jobs, sessions, AI keys, memory); a short privacy note covering
  what's stored, where (Neon/GCP), and the AI provider data flow (BYOK — their
  ToS applies); minimize logs of personal content.

## 6. Operational resilience & incident response

- **Dependencies down:** AI provider / Tavily / Neon outages should degrade
  gracefully with clear messages (partly done — extend to all paths).
- **Secret rotation:** a documented procedure to rotate `AI_ENCRYPTION_KEY`
  (re-encrypt stored keys), Google client secret, and DB credentials.
- **Monitoring/alerting:** Cloud Logging alerts on error-rate spikes, `401`/
  `429` floods, and failed deploys (ties to A09).
- **Incident runbook:** "a key/secret leaked", "a user reports data loss",
  "abuse detected" — short, written, with the revocation steps above.
- **Backups of config:** Secret Manager values and env config documented so the
  service can be rebuilt from scratch (deploy docs partly cover this).

---

## Suggested incorporation into the phased plan
- **Into Phase 1 (AI):** 2.3, 2.4, 2.6, and the duty-to-refer.
- **Into Phase 2 (limits):** 1.1, 1.4, 2.1.
- **Into a new Phase 7 (data resilience):** Section 4 (backups/DR) + 5
  (privacy/export/delete) + 6 (rotation/incident runbooks). **Backups (§4) are
  high priority despite being "non-feature" work — data loss is unrecoverable.**
- **Into Phase 1.4 / A01:** 1.2 (BOLA), 1.3 (mass assignment).
- **Into Phase 4/5:** Section 3 auth edge cases.
