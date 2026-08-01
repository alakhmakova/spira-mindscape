# Background sync re-fetches the full goal graph every poll (Neon egress / cost)

- **ID:** BUG-019
- **Status:** 🔧 In progress — fix implemented and unit/integration-tested; **awaiting real-world
  confirmation** after the user downgrades to the Neon free tier (watch the "public network
  transfer" metric). Pending manual commit by the user.
- **Reported by:** User (Neon free tier hit its 5 GB/month public-network-transfer allowance:
  "You're almost out of public network transfer — used 82% (4.1 GB)")
- **Area:** Frontend sync (`src/lib/spira/store.ts`, `src/lib/spira/api.ts`) + backend
  (`graphql/schema.graphqls`, `SpiraGraphqlController`, `GoalService`)
- **Severity:** Medium (no data bug — a hosting-cost / quota problem: exhausts Neon egress)

## Summary

On the Neon **free** tier the binding limit turned out to be **public network transfer** (egress),
not compute hours: ~4.1 GB of the 5 GB monthly allowance in a few weeks, while the database itself
holds only ~20 MB. So the cost is **the same data read out of Neon thousands of times**, not large
files. The dominant driver is background cross-device sync re-fetching the **entire goal graph** on
every poll.

## Steps to reproduce

1. Sign in and leave the web app open (the goals list re-fetches every 45s while the tab is
   visible — see BUG-001 / `AppShell.tsx`; the AI panel additionally polls the transcript).
2. In Neon Console → Monitoring, watch **public network transfer** over a day.
3. Observe it climb into the gigabytes even though the DB is tiny and little actually changed.

**Expected:** an idle session that changes nothing transfers almost nothing.
**Actual:** every poll runs the full `goals` query (goals + reality + options + targets +
checklist + resource metadata), so unchanged data is re-downloaded from Neon on every tick, across
every open device.

## Root cause (confirmed)

`store.ts → refreshGoalsIfIdle()` always called `spiraApi.fetchGoals()` — the full `Goals` query —
with **no "has anything changed?" check**. The goals list already omits file blobs (`dataUrl` is
lazy — see BUG-012), so a single response is not huge, but multiplied by the 45s poll × hours ×
devices it becomes gigabytes of Neon egress. The backend re-reads all the goal-graph rows from Neon
(GCP → Neon/AWS is public internet) on **every** poll to produce that response.

## Fix approach

Add a **cheap change-signature** and gate the full fetch on it (option #3 from the cost
investigation; the deferred alternatives were "just raise the interval" and "move file blobs to
Drive"):

- Backend: new `goalsRevision: String!` query returning `"<maxUpdatedAtMicros>:<totalRowCount>"`
  aggregated over the user's whole goal graph (goal, reality_item, option, target, checklist_item,
  resource), owner-scoped. `max(updatedAt)` catches edits; `count` catches inserts/deletes.
  Confidence history is intentionally omitted — a confidence change also bumps the parent goal, so
  `goal.updatedAt` already covers it. Implemented as six JPQL aggregates (portable across the H2
  test DB and Postgres — no Postgres-only SQL).
- Frontend: `refreshGoalsIfIdle()` fetches `goalsRevision` first; if it equals the last **applied**
  revision it returns without the full fetch. The revision is recorded only when a snapshot is
  actually applied, so a poll aborted by an in-flight local edit re-checks next time instead of
  masking a genuine cross-device change.

Net effect: while nothing changes, each poll transfers a few bytes instead of the full graph — the
vast majority of polls. Pairs with the idle-poll pause added alongside (the 45s goals poll and the
AI transcript poll now stop after ~3 min without user interaction) so a forgotten open tab stops
polling entirely.

## How to verify fixed

1. **Automated (done):**
   - Backend `GoalsRevisionIntegrationTest` — empty account is `"0:0"`; stable across polls;
     changes on create, on edit, on a nested option add, and on delete; strictly per-user
     (another user's writes never move this user's revision). 7 tests, green.
   - Frontend `store.test.ts` — idle poll skips the full `fetchGoals` when the revision is
     unchanged, and re-fetches when it changes. `npm test` green; `tsc`/lint clean.
2. **Real-world (pending — the user will confirm):** after downgrading to the Neon free tier, open
   the app normally for a few days and watch Neon Console → Monitoring → **public network
   transfer**. It should now sit far below the previous trajectory and stay within the 5 GB free
   allowance. Only when this is confirmed should the Status flip to ✅ Fixed.

## Resolution

_To be completed once real-world egress is confirmed on the free tier._ Files changed:

- `backend/src/main/resources/graphql/schema.graphqls` — added `goalsRevision: String!`.
- `backend/.../graphql/SpiraGraphqlController.java` — `@QueryMapping goalsRevision()`.
- `backend/.../goal/GoalService.java` — `currentRevision()` (six owner-scoped JPQL aggregates via
  `EntityManager`).
- `backend/.../graphql/GoalsRevisionIntegrationTest.java` — new integration test.
- `src/lib/spira/api.ts` — `fetchGoalsRevision()`.
- `src/lib/spira/store.ts` — `refreshGoalsIfIdle()` gates the full fetch on the revision;
  `__clearPendingWritesForTests()` resets it.
- `src/lib/spira/store.test.ts` — revision-gate tests.

## Related

- **BUG-001** (`cross-device-data-not-refreshing.md`) — introduced the 45s poll this optimizes.
- **BUG-012** (`goals-list-loads-all-file-contents.md`) — the file-blob egress angle (`dataUrl`
  lazy-load); complementary but separate.
- **BUG-018** (`chat-history-not-synced-across-devices.md`) — the AI transcript poll (every 4s
  while the panel is open) now gets the **same idle-pause** as the goals poll: after ~3 min without
  user interaction it stops, and any click/keypress or tab return revives it and catches up at once
  (`AiPanel.tsx`, the cross-device transcript-sync effect). Trade-off: while idle, a message sent
  from another device isn't adopted until you interact — acceptable for a personal app.
  A deeper **`updatedAt`-gate** for the transcript (fetch a cheap signature, pull the full
  transcript only when it changed — like `goalsRevision` here) remains an optional follow-up if
  transcript egress is still significant after the idle-pause.
