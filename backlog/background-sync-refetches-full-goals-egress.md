# Neon egress: unchanged data read out of the database over and over

- **ID:** BUG-019
- **Status:** 🔧 In progress — the first fix (revision gate + idle pause) shipped 2026-08-01 and was
  live throughout the measured week, yet the burn continued. Three further causes were found on
  2026-08-09 and fixed; **awaiting real-world confirmation** on Neon's "public network transfer"
  metric.
- **Reported by:** User (Neon free tier: "used 82% (4.1 GB)" of the 5 GB/month allowance; then
  ~1.9 GB in the first 7 days of August, ≈270 MB/day, tracking to ~8 GB for the month)
- **Area:** Backend (`resource/`, `ai/chat/transcript/`, `application.properties`) + frontend
  (`src/components/ai/`, `src/lib/spira/`) + Android (`data/goals/`, `ui/goals/`)
- **Severity:** Medium (no data bug — a hosting-cost / quota problem: exhausts Neon egress)

## Summary

On the Neon **free** tier the binding limit is **public network transfer** (egress), not compute
hours. The database holds ~20 MB, so the cost is never "large data" — it is **the same small data
read out of Neon thousands of times**, plus one case of large data read and then thrown away.

The original diagnosis (below, under "First fix") was right but incomplete. After it shipped, the
decisive clue was a discrepancy: **Neon transfer ~1.9 GB vs Cloud Run egress ~292 MB** for the same
period. Most of what leaves the database never reaches a client, which points at reads the backend
performs and discards — not at anything visible in a response.

## Steps to reproduce

1. Attach a PDF of a few MB to a goal.
2. Open the web app and leave it running; open the Android app and switch away and back a few times.
3. In Neon Console → Monitoring, watch **public network transfer** over a day.
4. Observe gigabytes accumulate while the app's own responses stay small (~51 KB for the goals
   list) and Cloud Run's outbound egress stays an order of magnitude lower than Neon's.

**Expected:** an idle session that changes nothing transfers almost nothing, and no file is read
unless someone is looking at it.
**Actual:** every goals fetch read every attached file out of the database; every Android resume
downloaded them again; the chat panel pulled its whole history every 4 seconds.

## Root cause

### 1. The backend read file bytes it never sent (the dominant one)

`Resource.dataUrl` (`backend/.../resource/Resource.java`) is an **eagerly-loaded `TEXT`** column
holding a base64 data URL — up to 5 MB decoded, ~6.7 MB encoded. The `Goal.resources` batch loader
selected **whole `Resource` entities** (`ResourceService.findByGoalIds` →
`ResourceRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc`), so every `goals` query pulled
every attachment across the network into the JVM and then dropped it during serialization, because
no client asks for `dataUrl` there.

BUG-012 fixed the **response** (3 MB → ~51 KB) by removing `dataUrl` from the query's selection set.
It never touched the **database read**. That gap is exactly why the payload looked fixed while the
metric did not move, and why no existing test caught it: every assertion was on the response, and
the response was correct all along.

### 2. Android re-downloaded every attachment on every resume

`android/app/src/main/graphql/GetGoal.graphql` still selected `resources { … dataUrl … }`, and
`GoalWorkspaceScreen` calls `viewModel.refresh()` from `LifecycleResumeEffect` — so returning to a
goal re-fetched its files in full. Android also had **no revision gate**: `goalsRevision` existed in
its copied `schema.graphqls` but was never queried, so both the dashboard and the workspace refetched
unconditionally on every resume.

This is what the 3,080,090-byte `/graphql` responses at 17:51–17:54 on 2026-08-07 actually were —
four app resumes during the "AI on Android" / "Android redesign" work, not browser tabs and not a
missing deployment (the revision gate merged to `main` on 2026-08-01 and was live).

Related waste on the same screen: a metadata-only edit re-sent the file. `commitResource` defaulted
`dataUrl` to `res.dataUrl` and `ProposalApply` forwarded `existing.dataUrl`, so renaming a PDF
re-uploaded it.

### 3. The chat transcript poll transferred the whole conversation every tick

`AiChatTranscriptService.get()` loads the whole entity including the `content` TEXT blob, and
`AiPanel.tsx` compared `updatedAt` **client-side, after** the full body was already down the wire.
No projection, no ETag. So an open chat panel spent its own history every 4 seconds to learn that
nothing had happened. (`get()` was also `@Transactional` read-write rather than `readOnly`.)

### 4. Session sweep every minute

`spring.session.jdbc.cleanup-cron` was unset, so Spring's default of `0 * * * * *` ran
`DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?` **once a minute** for as long as an instance was
warm — 1440 pointless queries a day that also prevent Neon's compute from autosuspending.

### Not the cause: session storage in Postgres

An external analysis attributed ~90% of the traffic to `spring-session-jdbc` and recommended moving
to in-memory sessions or JWT. The mechanism is real — each authenticated request reads the
serialized `AppUserOidcUser` (OIDC token + `AppUser`) from `SPRING_SESSION_ATTRIBUTES` and writes
back `LAST_ACCESS_TIME` — but the magnitude is not: at ~5 KB per read, 1.9 GB would need ~380,000
requests in a week, roughly 38 per minute around the clock, which the idle-paused polls cannot
produce. The `UPDATE` is also a write, and the metered figure is egress.

Moving sessions in-memory would additionally undo the reason `V14__spring_session.sql` exists:
Cloud Run scale-to-zero was logging the user out mid-work. **Decision: keep JDBC sessions**, and
only trim the cron (cause 4 above).

## First fix (2026-08-01, shipped — kept)

A cheap **change-signature** gating the full fetch:

- Backend `goalsRevision: String!` returning `"<maxUpdatedAtMicros>:<totalRowCount>"` over the
  user's whole graph, owner-scoped; six JPQL aggregates (portable across H2 and Postgres).
  Confidence history is intentionally omitted — a confidence change also bumps the parent goal.
- `refreshGoalsIfIdle()` fetches the revision first and skips the full fetch when it is unchanged,
  recording it only when a snapshot is actually applied.
- Paired with an idle-poll pause: both polls stop after ~3 min without user interaction.

Known and deliberate limitation: a **local** write also moves the server's revision, so the first
poll after an edit burst refetches the graph even though that tab already has the data. Recording
the post-write revision would avoid it but would swallow a concurrent edit from another device —
the staleness BUG-001 exists to fix. One ~51 KB fetch per edit burst is the cheaper mistake.

## Fix approach (2026-08-09)

1. **Never select `data_url` on list paths.** New `ResourceView` projection + constructor-expression
   queries (`findViewsByGoalId`, `findViewsByGoalIdIn`) used by `Goal.resources` and
   `resourcesByGoal`; `dataUrl` resolves to null there, which the web store already treats as "not
   loaded yet". The full entity stays on `resourceById` (the lazy byte-loader) and on mutation
   payloads. A projection rather than `@Basic(fetch = LAZY)`: lazy basic attributes need bytecode
   enhancement *and* a live session, and with `open-in-view=false` these entities are detached by
   the time GraphQL serializes them.
2. **Android mirrors BUG-012.** `dataUrl` dropped from `GetGoal.graphql`; new `GetResourceFile`
   query loads bytes when a preview or full-screen actually renders them, de-duplicated per id in
   `GoalWorkspaceViewModel.loadResourceFile`. `commitResource` / `ProposalApply` now send
   `dataUrl = null` ("leave the file alone") on metadata edits.
3. **Android revision gate.** New `GoalsRevision.graphql`; `GoalsStore` keeps the last applied
   revision per fetch (`GOALS_KEY`, `goalKey(id)` — keyed separately because the dashboard and the
   workspace return different data), and both view models skip the refetch when it is unchanged.
   `load()` records the revision too, so the resume that fires right after a cold start no longer
   duplicates the fetch.
4. **Transcript revision endpoint.** `GET /api/ai/chat/transcript/revision` returns only
   `updatedAt`, via a repository projection so `content` is never selected. The web poll asks for
   that and pulls the transcript only when it moved; interval relaxed 4s → 10s. `get()` is now
   `readOnly`.
5. **Config.** `spring.session.jdbc.cleanup-cron=0 0 * * * *` (hourly) and
   `spring.datasource.hikari.minimum-idle=2` (the default opens ten cross-region TLS connections per
   cold start).

## How to verify fixed

1. **Automated:**
   - `ResourceBytesReadIntegrationTest` — captures the SQL Hibernate actually issues
     (`SqlCapture`, a Hibernate `StatementInspector`) and asserts that the `goals` and
     `resourcesByGoal` queries never `SELECT … data_url`, that list responses report `dataUrl` as
     null, and that `resourceById` still does read the bytes. **This is the assertion that was
     missing** — response-shaped tests cannot see this class of defect.
   - `GoalsRevisionIntegrationTest` (7 tests, from the first fix) — still green.
   - Frontend `store.test.ts` revision-gate tests; new coverage for the transcript revision poll.
   - Android: a metadata-only resource update sends no `dataUrl`; the resume gate skips the refetch
     on an unchanged revision.
2. **Manual:** run locally on the `dev` profile (`show-sql=true`) and confirm the `goals` query's
   SQL has no `data_url`, while opening a preview issues exactly one `resourceById` select. On the
   emulator, resume the goal screen repeatedly and watch `adb logcat` for multi-MB responses.
3. **Real-world (pending):** watch Neon Console → Monitoring → **public network transfer** for 2–3
   days. Expect the daily burn to fall by roughly an order of magnitude. Only then flip to ✅ Fixed.
   If it does not fall, the next unexamined suspect is the RAG/pgvector path.

## Resolution

_To be completed once real-world egress is confirmed on the free tier._

## Related

- **BUG-001** (`cross-device-data-not-refreshing.md`) — introduced the 45s poll this optimizes, and
  the reason the revision must not be recorded after a local write.
- **BUG-012** (`goals-list-loads-all-file-contents.md`) — fixed the same problem one layer up (the
  response). Cause 1 above is the half it could not see; Android is where it was never applied.
- **BUG-018** (`chat-history-not-synced-across-devices.md`) — the transcript poll; its follow-up
  "`updatedAt`-gate" is cause 3 above, now done.
- **BUG-036** (`goal-ai-memory-read-but-never-sent.md`) — the same defect class found while clearing
  RAG as a suspect: `Goal.aiMemory` is read on every goals fetch and is not in the schema at all.
  Left open deliberately (6 KB cap vs 6.7 MB per file); the client-side half of it, Android's
  dashboard query asking for a `description` it never renders, was fixed here.
