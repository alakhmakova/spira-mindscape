# Validation: AI-Delegated Tasks

Status: **planned** — to be checked off during/after implementation.

## Safety rails (the non-negotiables)

- [ ] A target with an active (QUEUED/RUNNING) job cannot be enqueued again —
      neither via the API nor by the session-end sweep (DB constraint AND
      service check both proven by tests).
- [ ] A DONE target is never enqueued.
- [ ] A job cannot run longer than the wall-clock cap; an over-limit job ends
      FAILED with a reason, not RUNNING forever.
- [ ] A job cannot make more than the allowed number of web searches.
- [ ] A failed job retries at most once (transient errors only), then stays
      FAILED until the user clicks Retry.
- [ ] Executor proposals are restricted to: one note + state changes for the
      job's own target/item. Any other kind (incl. deletions, new goals, new
      AI tasks) is dropped server-side — test with a hostile mock model.
- [ ] The `assignee:"ai"` field in a proposal coming FROM a job is ignored.
- [ ] Daily per-user cap blocks the (cap+1)-th enqueue with a clear message.
- [ ] "Pause AI tasks" stops both enqueueing and execution; resume continues.
- [ ] Zombie sweep: a RUNNING job left by a crash becomes FAILED on restart.
- [ ] Nothing is ever applied to goal data without the user approving a card.

## Functional

- [ ] Robot toggle on a target enqueues a job; toggling off cancels it.
- [ ] GROW coach can create a target with assignee=AI when asked to delegate.
- [ ] Session end enqueues all marked, not-done, not-yet-queued tasks of the
      goal — exactly once.
- [ ] Report list shows queued/running/done/failed with human-readable text;
      finished jobs' proposal cards appear (and survive reloads via the
      existing pending-proposal restore).
- [ ] Regular chat and GROW flows are byte-identical for unmarked tasks.

## Deployment

- [ ] Jobs execute on the compose deployment with the browser closed.
- [ ] Cloud Run: CPU throttling setting documented/applied; behavior when the
      instance scales to zero matches what plan.md §6 promises.
- [ ] H2 test suite green (no pgvector/partial-index dependency in entities).
