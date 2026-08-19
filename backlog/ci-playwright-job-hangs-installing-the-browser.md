# CI: the Playwright job hangs installing the browser and burns its whole budget

- **ID:** BUG-043
- **Status:** ✅ Fixed (2026-08-19, second attempt) — see Resolution. The first attempt capped apt
  and did **not** work; the trigger is a third-party mirror, so the fix is to stop depending on it.
- **Reported by:** User (2026-08-19) — "playwright застревает на браузере, я попробовала
  перезапустить - та же проблема"
- **Area:** CI — `.github/workflows/ci.yml`, the `web-e2e` job
- **Type:** Defect (infrastructure)

## Summary

**CI / Web E2E (Playwright)** sat on the **Install Playwright browser** step for 19m 29s and was
then cancelled by the job's 20-minute timeout. Re-running produced exactly the same result. No test
ever ran: `Build backend JAR`, `Start backend`, `Wait for backend` and `Run Playwright E2E` all show
0s and skipped.

The commit under test was healthy — every other job in the same run passed, Android included.

## Root cause

The step was one command:

```yaml
- name: Install Playwright browser
  run: npx playwright install --with-deps chromium
```

`--with-deps` shells out to **apt**, and the runner's apt mirror stopped answering. From the log:

```
Ign:2 http://azure.archive.ubuntu.com/ubuntu noble InRelease          ← repeatedly, all mirrors
Hit:2 https://archive.ubuntu.com/ubuntu noble InRelease               ← falls back
Get:3 https://archive.ubuntu.com/ubuntu noble-updates InRelease [126 kB]
Get:4 https://archive.ubuntu.com/ubuntu noble-backports InRelease [126 kB]
Get:5 https://archive.ubuntu.com/ubuntu noble-security InRelease [126 kB]
07:42:09 … then nothing at all …
08:00:58 ##[error]The operation was canceled.
```

Nineteen minutes of silence after the last byte. **apt has no timeout of its own** — a mirror that
accepts the connection and then stops sending will hold it open indefinitely, and apt waits.

Two things made it worse than it needed to be:

- **One step did two unrelated jobs.** The OS libraries (an Ubuntu mirror) and the browser binary
  (Playwright's CDN) fail for entirely different reasons, but shared one line, so the log could not
  say which had stalled.
- **The step had no budget of its own**, so a stall consumed the job's whole 20 minutes instead of
  failing in a minute and leaving room for the tests.

This is the second occurrence: a comment already in the workflow records a 15-minute stall on
2026-08-06 that "passed three times afterwards" — the same shape, written off as a one-off.

## Fix approach

The mirror is not ours to fix. Make a bad mirror cheap instead:

1. Give apt a timeout **globally**, via `/etc/apt/apt.conf.d/`, so it applies to Playwright's own
   apt call as well (options passed on our command line would not reach it).
2. **Split** the step into system libraries and browser, each with its own `timeout-minutes`.
3. **Cache** the browser binary, so on a hit the CDN half is a no-op and apt is the only network
   dependency left.

## How to verify fixed

- A healthy run still finishes in ~2.5 minutes, with the browser step reporting a cache hit.
- **`Run Playwright E2E` executes** — the step that has been skipped in every run since this began.
- When the mirror stalls, the libraries step goes amber after ≤4 minutes and the job **carries on**.
- The E2E tests themselves are unaffected — nothing about the suite changed.

## First attempt — capping apt. It did not work.

The first fix (commit `7405003`) split the step in two, cached the browser, and gave apt explicit
timeouts in `/etc/apt/apt.conf.d/99-spira-timeouts`:

```
Acquire::Retries "3";
Acquire::http::Timeout "20";
Acquire::https::Timeout "20";
```

The next run stalled in **exactly the same place** — after `Get:5 …noble-security InRelease`, silent
until the step's own 6-minute budget killed it:

```
09:14:14 Get:5 https://archive.ubuntu.com/ubuntu noble-security InRelease [126 kB]
09:19:57 ##[error]The action 'Install Playwright system libraries' has timed out after 6 minutes.
```

So `Acquire::*::Timeout` does not cover whatever apt is waiting on here. The split and the step
budget did their job — the failure was named and cost 6 minutes instead of 20 — but the run still
failed on a commit whose tests were fine.

## Resolution

**2026-08-19 — apt is no longer a gate.**

`.github/workflows/ci.yml`, the `web-e2e` job:

- `actions/cache@v4` keeps `~/.cache/ms-playwright`, keyed on `package-lock.json`;
- **Install Playwright system libraries (best-effort)** — `continue-on-error: true`,
  `timeout-minutes: 4`;
- **Install Playwright browser** — `playwright install chromium`, `timeout-minutes: 6`. This is the
  half that comes from Playwright's own CDN and the half that actually has to work.

The reasoning for demoting the libraries: the **`ubuntu-24.04` runner image ships Google Chrome and
Chromium**, so the shared libraries Playwright's bundled Chromium needs are already on the machine.
`install-deps` is insurance for the day that stops being true, not a prerequisite — and insurance
must not be able to fail the policy. If a library really is missing, the browser fails to launch in
`Run Playwright E2E` with a message naming it, which is a better failure than a silent apt stall.

The apt timeout config was dropped with the same change: it did not help, and a config file that
does nothing is worse than no config file.

The job keeps its 20-minute budget as the backstop.
