# BUG-091 — The E2E suite deletes every goal in the local database

**Status:** ✅ Fixed
**Area:** Tests / Web E2E
**Severity:** High — silent, irreversible loss of the developer's own data

## Summary

Running the full Playwright suite locally (`npx playwright test`, `npm run test:e2e`) **deletes
every goal on the `dev@local` account** — not just the fixtures the run created. It happened on
2026-09-24: a goal the owner had spent the morning on, with a vacancy map the CV agent had filled,
was gone by the end of a routine verification run. Nothing warned beforehand and nothing said
afterwards that anything had been removed.

The data is not recoverable. `deleteGoal` is a hard delete, the `resource` rows go with the goal
(`ON DELETE CASCADE`), and autovacuum had already reclaimed them by the time it was noticed:
`pg_stat_user_tables` reported `resource` at 0 live / 0 dead tuples. Postgres runs in Docker with
no WAL archiving and no base backup, so there is no point-in-time recovery either. What survived is
the account itself, the saved AI keys (`ai_api_keys`, 4 rows) and the one goal-less chat transcript.

## Steps to reproduce (before the fix)

1. Have any goal in the local dev database (the `local` profile's `dev@local` user).
2. Bring the stack up as usual — Docker Postgres, backend on `local`, Vite.
3. Run the whole suite: `npx playwright test`.
4. Open the app. The dashboard is empty; `select count(*) from goal` is 0.

## Root cause

`e2e/goal-cap.spec.ts` needs the account at zero goals in order to fill it to the 50-goal cap and
check that the fifty-first is refused. It got there by reading the account's **entire** goal list
and deleting all of it:

```ts
const ids = existing.data.goals.map((g) => g.id);
if (ids.length) {
  await gql(page, `mutation { ${ids.map((id, i) => `d${i}: deleteGoal(id: "${id}")`)} }`);
}
```

In CI that list is only the suite's own fixtures, so the line looked harmless. On a developer's
machine the same list is her real work. The teardown that BUG-073 added (`e2e/global-teardown.ts`)
is careful — it deletes exactly the ids `createGoal` recorded — but this spec bypassed it entirely.

## Fix

`goal-cap.spec.ts` now deletes **only goals this run created**, and stands down otherwise:

- `goalsCreatedThisRun()` (`e2e/helpers.ts`) reads the same `.created-goals.ndjson` the teardown
  uses and returns those ids.
- Any goal on the account that is not in that set means somebody's data, so the test calls
  `test.skip(...)` with a message saying how many goals it refused to touch, instead of clearing
  them.
- CI is unaffected: there every goal on the account was made by the run, so the set matches and the
  spec runs exactly as before.

## How to verify fixed

1. With at least one goal in the local database, run `npx playwright test e2e/goal-cap.spec.ts`.
   It is **skipped**, with the reason naming the number of goals it would have had to delete, and
   the goals are still there afterwards.
2. Against an empty database the spec runs and passes as before.
3. `grep -rn "deleteGoal" e2e/` — the only remaining callers are the guarded block above and
   `global-teardown.ts`, which deletes recorded ids only.

## Resolution

Fixed 2026-09-24 in `e2e/goal-cap.spec.ts` and `e2e/helpers.ts`. The lost goals could not be
recovered, for the reasons above.

**Worth doing separately:** the local dev database has no backup of any kind. A `pg_dump` before a
destructive run — or simply a documented one-liner in `README.md` — would have made this a
five-minute annoyance rather than a loss.
