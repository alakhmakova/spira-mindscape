# Requirements: AI-Delegated Tasks (background execution)

Status: **planned, not implemented**.

## Problem

During GROW sessions users surface tasks they want to hand off to the AI
(e.g. "search the web for QA openings in Gothenburg"). The coach correctly
refuses execution work inside a session. Today there is no way to mark a
target as "for the AI" and have it executed — the user would have to sit in
the chat and drive it manually, which defeats the point of delegation.

## Goal

A target (or checklist item) can be marked **assignee = AI**. Marked tasks are
executed **in the background** — the user does not keep a tab open or watch
the process. When they next open the app, they see a report: what was done,
what was produced, what failed and why. Results enter the goal **only as
proposal cards** the user approves or rejects — the AI never silently mutates
goal data.

## What "execute" means (v1 scope)

The executor can do what the regular chat's tools can do:

| Capability | Tool | Deliverable |
|---|---|---|
| Web research | `web_search` (Tavily, user's key) | A note resource with findings + sources |
| Read a specific page | `read_url` | Content used in the note |
| Read the goal's resources | `read_resource` | Context for the task |
| Record outcomes | `propose_goal_change` | Proposals: the note, target completion / progress, checklist updates |

Explicitly **out of scope** (v1): any real-world action — sending emails,
submitting applications, purchases, calendar changes. The UI must not imply
otherwise: marking UI copy should say "AI research task".

## Functional requirements

1. **Marking.** A target and a checklist item have an `assignee` field
   (`USER` default | `AI`). Toggleable in the UI (robot icon next to the
   item). The GROW coach can create targets already marked for the AI when
   the user says so (a new optional field on `propose_goal_change`).
2. **Trigger.** Marking a task `AI` enqueues a job. At GROW session end, all
   marked-and-not-done tasks of that goal that have no job yet are enqueued.
   Queued ≠ running: execution happens server-side, asynchronously.
3. **Background execution.** Jobs run on the backend with the user's stored
   (encrypted) API keys — no browser needed. BYOK note: decryption is
   server-side (AES key is a server env var), so unattended execution is
   technically possible; because it spends the user's API credits unattended,
   the safety rails below are mandatory, not optional.
4. **Reporting.** Each finished job stores a short human-readable report.
   The AI panel shows an "AI tasks" report on next open (and a badge while
   jobs are pending/running): per task — done/failed, what was produced,
   links to the proposal cards. Pending proposals already survive reloads
   (restored from the server), so results are never lost.
5. **Control.** The user can cancel a queued/running job and un-mark a task.
   A global "pause AI tasks" switch in the panel settings.

## Safety rails (hard requirements)

The user's explicit concerns: no infinite execution, no re-execution, no
doing more than asked.

| Risk | Rail |
|---|---|
| Infinite / runaway execution | Per-job hard caps: max tool iterations (reuse the existing `MAX_TOOL_ITERATIONS`-style cap), max wall-clock time (e.g. 3 min), max web searches per job (e.g. 5). The scheduler kills over-limit jobs and marks them FAILED with reason. |
| Re-execution | One **active** job per target enforced by a DB constraint; completed targets are never enqueued; a finished job is re-run only by explicit user action ("Retry"). `attempted_at` recorded on the job, not inferred. |
| Doing extra ("выполнение лишнего") | The executor prompt is scoped to ONE named task; it may not create new AI tasks (the `assignee` field is stripped/rejected in executor mode), may not propose deletions, and produces at most: one note + state proposals for ITS target. Server-side validation rejects out-of-scope proposal kinds from executor jobs. |
| Unattended spending | Daily per-user job cap (e.g. 10) + per-job caps above. Failures don't auto-retry (max 1 automatic retry on transient 429/5xx, then FAILED). |
| Silent data corruption | Results are proposals only — nothing applies without the user's click. (Existing proposal mechanics, including reload restore.) |
| Stuck jobs after a crash | RUNNING jobs older than the wall-clock cap are reaped to FAILED on scheduler start ("zombie sweep"). |

## Non-functional

- Regular chat and GROW behavior unchanged for unmarked tasks.
- Works on the existing single-instance deployment (Cloud Run / compose) —
  no new infrastructure (no message broker; DB-backed queue + Spring
  scheduler). Cloud Run caveat: instances scale to zero — see plan.md §6 for
  the chosen polling/wakeup strategy.
- All new behavior covered by unit tests; job lifecycle covered by
  service-level tests (H2-safe: the job table is plain JPA, no pgvector).
