# Plan: AI-Delegated Tasks (background execution)

Status: **planned, not implemented**. Companion to `requirements.md`.

## 1. Schema (Flyway V14)

```sql
ALTER TABLE target ADD COLUMN assignee VARCHAR(8) NOT NULL DEFAULT 'USER';
ALTER TABLE checklist_item ADD COLUMN assignee VARCHAR(8) NOT NULL DEFAULT 'USER';

CREATE TABLE ai_job (
    id            BIGSERIAL    PRIMARY KEY,
    app_user_id   BIGINT       NOT NULL,
    goal_id       BIGINT       NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    target_id     BIGINT       REFERENCES target(id) ON DELETE CASCADE,
    checklist_item_id BIGINT   REFERENCES checklist_item(id) ON DELETE CASCADE,
    task_text     TEXT         NOT NULL,      -- snapshot of the task at enqueue time
    status        VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
                  -- QUEUED | RUNNING | DONE | FAILED | CANCELLED
    attempts      INT          NOT NULL DEFAULT 0,
    report        TEXT,                        -- human-readable outcome
    error         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);
-- One ACTIVE job per task (re-execution rail):
CREATE UNIQUE INDEX uq_ai_job_active_target
    ON ai_job(target_id) WHERE status IN ('QUEUED','RUNNING');
CREATE UNIQUE INDEX uq_ai_job_active_item
    ON ai_job(checklist_item_id) WHERE status IN ('QUEUED','RUNNING');
```

`ai_job` is a normal JPA entity (no pgvector types) → H2 tests stay green.
Partial unique indexes are Postgres-only; in H2 test config they don't exist
(Flyway is off in tests) — the service ALSO checks "no active job" in code, so
both layers enforce it.

## 2. Backend — new package `ai/jobs`

- `AiJob` (entity), `AiJobRepository`.
- `AiJobService`:
  - `enqueueForTarget(goalId, targetId)` / `enqueueForItem(...)` — ownership
    check (`findByIdAndUserId` pattern), refuse if target done, refuse if an
    active job exists, refuse if the user's daily cap is reached.
  - `enqueueSessionTasks(goalId)` — called at GROW session end (frontend
    sends one request; idempotent thanks to the active-job rule).
  - `cancel(jobId)`, `retry(jobId)`, `listForUser()`, `pause/resume` (a flag
    on app_user or a user-settings row).
- `AiJobExecutor` — the worker:
  - Spring `@Scheduled(fixedDelay = 15s)` poll: pick the oldest QUEUED job
    (`FOR UPDATE SKIP LOCKED` via a native query — safe even if two instances
    ever run), mark RUNNING + `started_at`.
  - Build an **executor prompt** (new, separate from CHAT/GROW): "You are
    completing ONE delegated research task… produce ONE note proposal with
    findings and sources; propose completing/updating ONLY target id=X; do
    not propose anything else; no deletions; at most N searches."
  - Run the existing agentic loop machinery **without SSE**: refactor
    `AiChatService.runAgenticLoop` so its core (provider call + tool loop)
    is reusable with a callback sink instead of an emitter
    (`AgenticRunner.run(messages, prompt, tools, sink)`); the chat path wraps
    it with SSE, the job path collects text + proposals into the job report.
  - Keys: decrypt the user's chat-provider key + Tavily key via the existing
    `AiKeyService` — but `getKey()` reads the security context; add explicit
    `getKeyForUser(userId, provider)` for the scheduler context (package-
    private, used only by the executor).
  - Enforce rails: wall-clock timeout (interrupt + FAILED), iteration cap,
    search-count cap (count `web_search` tool calls), single retry on
    429/5xx, then FAILED with the provider's message in `error`.
  - Server-side proposal validation for jobs: allow kinds
    `note | complete_target | target_progress | checklist_item` and only for
    the job's own target/item; reject others (log + drop).
  - Zombie sweep on startup: RUNNING older than the cap → FAILED("crashed").

## 3. API (`AiController`)

```
GET    /api/ai/jobs                 list my jobs (newest first, with reports)
POST   /api/ai/jobs/target/{id}     enqueue for a target
POST   /api/ai/jobs/item/{id}       enqueue for a checklist item
POST   /api/ai/jobs/goal/{goalId}/session-end   enqueue all marked tasks
POST   /api/ai/jobs/{id}/cancel
POST   /api/ai/jobs/{id}/retry
POST   /api/ai/jobs/pause | /resume
```

## 4. GraphQL / goal data

- `assignee` exposed on Target and ChecklistItem (schema + DTOs + mutations
  `updateTarget` / checklist mutations accept it).
- `propose_goal_change`: new optional property `assignee` (`"ai"`), valid for
  kinds `target`/`task`/`add_checklist_item`. The GROW prompt mentions it:
  "if the user asks to delegate a task to the AI, create it with
  assignee='ai'". **Executor mode strips/rejects this field** (rail: jobs
  must not breed jobs).

## 5. Frontend

- **Marking UI**: robot toggle on target rows (desktop table + mobile card)
  and checklist items; label copy "AI research task". Toggling ON asks for
  confirmation once ("AI will research this in the background using your
  API keys") and calls the enqueue endpoint; OFF cancels an active job.
- **Reports**: in the AI panel — a compact "AI tasks" section (badge with
  running/queued count; list with status, report text, links). Data from
  `GET /api/ai/jobs`, polled only while the panel is open (e.g. every 20s).
  Finished-job proposals appear through the existing pending-proposal restore.
- **Session end**: `closeSession` additionally calls
  `POST /api/ai/jobs/goal/{id}/session-end`; the wrap-up note mentions how
  many tasks were queued ("2 AI tasks queued — results will appear here").

## 6. Cloud Run caveat (scale-to-zero)

`@Scheduled` only runs while an instance is alive. Options, in order of
preference:

1. **v1 pragmatic**: set Cloud Run `min-instances=0` + rely on the fact that
   jobs are enqueued by user actions — the instance serving that request is
   alive; the executor drains the queue within the same instance's lifetime
   (Cloud Run keeps instances warm ~15 min after a request; always-on CPU
   must be enabled for background threads — `--no-cpu-throttling`).
   Document the limitation: a job enqueued seconds before shutdown may wait
   until the next visit.
2. Later: Cloud Scheduler hitting a `/api/ai/jobs/tick` endpoint every few
   minutes (also fixes the compose deployment for free, where this isn't an
   issue at all since the container is always on).

## 7. Tests

| Layer | Tests |
|---|---|
| `AiJobServiceTest` | enqueue happy path; refuses: foreign goal (404), done target, duplicate active job, daily cap; cancel/retry transitions; pause blocks enqueue+execution |
| `AiJobExecutorTest` (mocks) | picks QUEUED oldest first; RUNNING→DONE with report; provider error → single retry → FAILED; wall-clock cap kills; search cap enforced; out-of-scope proposal kinds dropped; assignee field stripped; zombie sweep |
| `AgenticRunner` refactor | existing `AiChatServiceGrowTest` must stay green (proves the SSE path is unchanged) |
| GraphQL | `assignee` round-trips on target/checklist mutations; defaults USER |
| Frontend (vitest) | toggle calls enqueue/cancel; report section renders states; session-end queues and reports count |
| E2E (python) | mark target → job row appears; (executor itself mocked or skipped in CI — no real keys) |

## 8. Open questions (decide before implementation)

1. Daily cap value and whether it's configurable (`spira.jobs.daily-cap`).
2. Should checklist items be in v1 at all, or targets only? (Targets only
   halves the schema/UI surface; items can follow.)
3. Report retention: keep last N jobs per user or forever?
4. Which provider executes jobs when the user has several — the active chat
   provider at enqueue time (snapshot on the job) vs always a fixed one.
