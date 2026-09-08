# Leftover Playwright E2E goals clutter the local dev database

- **ID:** BUG-073
- **Status:** ✅ Fixed
- **Reported by:** Claude, while running a manual GROW session at the owner's request, 2026-09-02
- **Area:** Test infra (`e2e/`) / local dev environment — not a product defect
- **Severity:** Low — does not affect production or any real user; only clutters local manual
  testing

## Summary

13 of the 14 goals in the local `dev@local` database are leftover Playwright E2E fixtures with
names like "E2E img 1788185140243", "Filters A/B/C 1788185040841", "Attachments 1788185082133",
"E2E drag/delete/active…" — apparently created by `e2e/` specs and never cleaned up between runs
(or between test runs and manual sessions). They aren't wrong from the product's point of view,
but they make the All-goals list unusable as a realistic testing ground and would be confusing to
anyone reviewing the local DB by hand.

## Steps to reproduce

1. Run the E2E suite locally (`npm run test:e2e`) against the `local`-profile backend.
2. Open the app and look at "All goals" — fixture goals accumulate and are never removed.

## Root cause

The E2E specs (`e2e/helpers.ts` `createGoal` and friends) create real goals against the running
`local`-profile backend/DB and have no corresponding teardown step that deletes them after the
run.

## Fix approach

- Add a teardown (global `afterAll`/`afterEach` in the Playwright config, or a dedicated cleanup
  script) that deletes goals created by the E2E run, e.g. by tagging fixture goals with a
  recognizable title prefix and sweeping them at suite start/end.

## How to verify fixed

- Run the full E2E suite twice in a row; the goal count in the local DB should not grow between
  runs.

## Resolution

Fixed 2026-09-02. `e2e/helpers.ts`'s `createGoal` now appends `{id, title}` to
`e2e/.created-goals.ndjson` (gitignored) right after it captures the goal's real id from the URL.
A new `e2e/global-teardown.ts`, wired via `playwright.config.ts`'s `globalTeardown`, reads that
file once the whole run finishes and deletes exactly those ids via a plain `fetch` GraphQL
`deleteGoal` mutation — no title pattern-matching, no risk of touching a real goal. CSRF is
disabled under both the `local` and `e2e` backend profiles (`SecurityConfig.java`), so no token
dance is needed; CI's `X-E2E-Auth` header is sent the same way `playwright.config.ts` already sets
it for the browser. The file is removed unconditionally at the start of teardown, so a backend
that's down doesn't leave stale entries for the next run.

Verified: ran `e2e/composer-long-message.spec.ts` locally — the fixture goal it created was
confirmed gone via GraphQL immediately after the run, and `.created-goals.ndjson` no longer
existed. Also purged the 13 pre-existing stale fixtures from the local DB by hand (ids 3097–3109;
the one real goal, id 3110, was left alone). `npx eslint`/`tsc --noEmit` clean on the changed
files.
