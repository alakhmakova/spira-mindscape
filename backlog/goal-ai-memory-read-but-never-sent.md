# A goal's GROW memory is read from the database on every goals fetch and never sent

- **ID:** BUG-036
- **Status:** 🐞 Open
- **Reported by:** Follow-up from BUG-019 (found while clearing the RAG/pgvector path as an egress
  suspect)
- **Area:** Backend (`goal/Goal.java`, `goal/GoalRepository.java`, `ai/grow/GoalMemoryService.java`)
- **Severity:** Low today, and it only grows — no user-visible symptom, purely wasted database egress

## Summary

`Goal.aiMemory` (`Goal.java:66`, column `ai_memory`, `TEXT`) holds the memory carried across GROW
coaching sessions, capped at **6000 characters** by `GoalMemoryService.MAX_MEMORY_CHARS`.

It is **not in the GraphQL schema at all** — no client can request it, and none does. But
`GoalRepository.findByUserIdOrderByCreatedAtAsc` loads whole `Goal` entities, so `SELECT g.*` pulls
`ai_memory` out of the database on every goals fetch, from every device, and the GraphQL layer
discards it during serialization.

This is the same defect class as BUG-019's cause 1 (`Resource.dataUrl`): a column that is read
across the network and never reaches anyone. It is smaller — a 6 KB cap against ~6.7 MB per
attached file — but it is unbounded in the number of goals and grows with GROW usage, and it is
invisible in every response-shaped test.

## Steps to reproduce

1. Run a GROW session on a goal and choose "Save memory" so `ai_memory` is non-empty.
2. Run the backend on the `dev` profile (`spring.jpa.show-sql=true`).
3. Load the goals list and read the SQL for the `goal` table.

**Expected:** the list query selects only columns some client can receive.
**Actual:** `ai_memory` is in the select list.

## Root cause

`aiMemory` is a plain eagerly-loaded field on the `Goal` entity, and the `goals` query returns
entities rather than a projection. Nothing distinguishes "a column the API exposes" from "a column
only one server-side service uses" — so every read of a goal pays for both.

## Fix approach

**Preferred: move the column out of `goal` into its own table**, e.g.
`goal_ai_memory (goal_id PK → goal, content TEXT, updated_at)`, with a Flyway migration that copies
existing values across. `GoalMemoryService` is the only reader/writer, so it is the only caller that
changes. After this no code path can accidentally read the memory again, which a projection cannot
guarantee — a future `findAll()` would reintroduce the problem.

Considered and rejected:

- **A `GoalView` projection for the list**, mirroring `ResourceView`. It works, but `Goal` is the
  key type of six `@BatchMapping` resolvers (`reality`, `options`, `targets`, `resources`,
  `confidenceHistory`, `progress`) plus `goalById` and every mutation payload, so the projection
  would ripple through the whole resolver layer for a 6 KB column. Disproportionate.
- **`@Basic(fetch = LAZY)`** — needs Hibernate bytecode enhancement, and with
  `spring.jpa.open-in-view=false` the entity is detached by serialization time. Same reason it was
  rejected for `Resource.dataUrl` in BUG-019.

## How to verify fixed

Extend `ResourceBytesReadIntegrationTest`'s approach: with `SqlCapture` active, run the `goals`
query and assert no statement selects `ai_memory`. Add a test that a saved GROW memory still
survives a round-trip through `GoalMemoryService` after the move.

## Resolution

_Not started._

## Related

- **BUG-019** (`background-sync-refetches-full-goals-egress.md`) — same defect class, three orders
  of magnitude larger (`Resource.dataUrl`). Its `SqlCapture` test harness is what this one should
  reuse.

## Note: `description` is NOT the same problem

`Goal.description` is also a `TEXT` column (up to 5000 characters), but it **is** in the schema and
the goal page renders it, so reading it is the API doing its job — not waste. The related question
is only ever whether a given *client query* needs it:

- **Web** keeps one full goal graph in the store and shows the description on the goal page, so
  `GOAL_FIELDS` is right to ask for it.
- **Android** did have the waste, on the client side: `GetGoals.graphql` selected `description`
  while `GoalSummary` has no such field, so every dashboard load downloaded up to 5000 characters
  per goal and dropped them. Fixed alongside BUG-019 (2026-08-09) by removing the field from that
  query.
