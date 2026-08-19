# CI: the Playwright job hangs installing the browser and burns its whole budget

- **ID:** BUG-043
- **Status:** ✅ Fixed (2026-08-19) — see Resolution. Watch the next few runs; the trigger is a
  third-party mirror, so the fix is about *failing fast*, not about making the mirror work.
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
- When a mirror stalls, **Install Playwright system libraries** fails after ~6 minutes and names
  itself, instead of the job being cancelled with no cause shown.
- The E2E tests themselves are unaffected — nothing about the suite changed.

## Resolution

**2026-08-19 — fixed** in `.github/workflows/ci.yml`:

- a new step writes `Acquire::Retries "3"` and 20-second `http`/`https` timeouts into
  `/etc/apt/apt.conf.d/99-spira-timeouts`;
- `actions/cache@v4` keeps `~/.cache/ms-playwright`, keyed on `package-lock.json`;
- the install is now **Install Playwright system libraries** (`playwright install-deps chromium`)
  and **Install Playwright browser** (`playwright install chromium`), each `timeout-minutes: 6`.

The job keeps its 20-minute budget: that is the backstop that surfaced this, and with the steps
capped it should never be what stops a run again.
