# BUG-093 — A developer can read every user's data straight from the database

**Status:** 🐞 Open
**Area:** Security / Privacy
**Severity:** High — by design today, and the wrong design once there are real users

> **Recorded only, at the owner's instruction (2026-09-24): think it through before anything is
> built.** Nothing in this file is to be implemented without her say-so.

## Summary

Anyone holding the production connection string can read **everything every user has written**:
goal titles and descriptions, reality items, options, targets, resource notes, uploaded files,
vacancy maps, AI chat transcripts and GROW session records. The application enforces per-user
ownership carefully (`findByIdAndUserId`, `CrossUserIsolationIntegrationTest`), but that boundary
lives in the application only. At the database it does not exist.

Today the only holder is the owner, and the data is mostly her own, so nothing is being violated.
The question is what the answer should be **before** there are users who are not her — because the
material is unusually personal: what someone is trying to change about their life, what stands in
their way, their CV, their salary, their fears.

## What is exposed, and to whom

| Holder | What they can read |
|---|---|
| Anyone with `DATABASE_URL` / `DATABASE_PASSWORD` (repo secrets, the owner's machine, a leaked env) | Every row of every table, for every user |
| Anyone with Neon console access | The same, plus point-in-time copies |
| A future contributor given production access to debug something | The same — there is no lesser access to give |

Two mitigations already exist and should be kept in mind when designing the answer: **AI provider
keys are encrypted at rest** (`ai_api_keys`, see `docs/security-model.md`), and the logging rules
forbid writing user text to logs (`LoggingConventionTest`) — so the *logs* are already clean. It is
the database itself that is open.

## Directions worth weighing (not decisions)

- **Encrypt the sensitive columns application-side**, the way the AI keys already are, with the key
  in Secret Manager rather than in the database. Cheapest to reason about; costs searching and
  sorting on those columns, which the dashboard filters rely on.
- **Per-user keys**, so even the application can only decrypt for a user who is currently signed
  in. Strongest, and it makes the AI coach, the RAG index and any future admin tooling much harder
  — a background job cannot read what it cannot decrypt.
- **Access discipline instead of cryptography**: a separate read-only role, no standing production
  access, an audited break-glass path, and a written policy. Weakest technically, but honest and
  cheap, and it is what most small products actually do.
- **Postgres row-level security** — real defence against an application bug, none at all against
  somebody holding the superuser string, which is the case here.

Whatever is chosen has to answer three practical questions: how support debugs a user's problem
without reading their goals, what the AI pipeline is allowed to see, and what a backup contains
(BUG-092's dumps inherit exactly this exposure — an encrypted dump in an artifact is still
plaintext rows to whoever can decrypt it).

## Next step

A decision from the owner on which direction to take, then a spec in `specs/`. Not to be started
before that.
