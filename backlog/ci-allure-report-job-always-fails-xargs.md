# CI: the Allure report job fails on every run ("xargs is not available")

- **ID:** BUG-015
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** User ("allure report сейчас единственная проблема для мерджа")
- **Area:** CI — `.github/workflows/ci.yml`, `allure-report` job
- **Severity:** Medium (job red on every run → blocks a clean merge; no test report is ever published)

## Summary

The `Allure report` job failed on **every** CI run — on `main`, branches and PRs alike — even when
the backend and Python E2E jobs both passed and uploaded their Allure results. No report artifact
was ever produced.

## Steps to reproduce

1. Open any CI run → the `Allure report` job is red.
2. Open its `Allure Report` step log and read past the container invocation.

## Root cause (confirmed)

The job delegated report generation to the third-party action
`simple-elf/allure-report-action@53ebb757…` (v1.13), which runs inside its own Docker image. The
**Allure command-line launcher refuses to run without `xargs`** — from `allure-2.x/bin/allure`:

```sh
# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
```

That action's image does not provide `xargs`, so the CI log shows:

```
generating report from allure-input to allure-report ...
xargs is not available
copy allure-report to allure-history/152
cp: cannot stat './allure-report/.': No such file or directory
copy allure-report history to /allure-history/last-history
cp: cannot stat './allure-report/history/.': No such file or directory
```

The generate silently aborted, `./allure-report` was never created, and every subsequent `cp` in
the action failed → the step exited non-zero. The failure is entirely inside the third-party
container; nothing about our tests or results was wrong.

A second, latent problem in the same job: both `download-artifact` steps would fail hard if an
upstream job died before uploading results — even though the job is declared `if: always()`.

## Fix approach

Stop using the third-party action and run the Allure CLI directly on the runner (which does have
`xargs`). Two commands — download/extract the CLI, then `allure generate` — are simpler than
debugging someone else's image, and drop a supply-chain dependency.

## How to verify fixed

1. The `Allure report` job is green and uploads an `allure-report` artifact containing
   `index.html`, `data/`, `widgets/`.
2. Downloading and opening `index.html` shows the aggregated backend + Python E2E results.
3. When an upstream job fails and uploads nothing, the job still succeeds and logs
   `No Allure results in … — skipping it.` instead of erroring.

## Resolution

Rewrote the `allure-report` job in `.github/workflows/ci.yml`:

- Removed `simple-elf/allure-report-action`.
- Added `actions/setup-java@v4` (the Allure CLI is a JVM tool).
- **Install Allure CLI** — downloads and extracts `allure-2.44.0.tgz` from the official
  `allure-framework/allure2` release and puts its `bin` on `$GITHUB_PATH`.
- **Generate Allure report** — passes each results directory **explicitly**
  (`allure generate allure-input/backend allure-input/e2e --clean -o allure-report`), because
  `allure generate` does **not** recurse into sub-folders of a parent directory (the old action was
  handed the parent `allure-input`). Directories that are missing or empty are skipped, and if
  there are no results at all the step exits 0 rather than failing the job.
- Both `download-artifact` steps got `continue-on-error: true`, so a missing upstream artifact no
  longer breaks the `if: always()` report job.

Verified locally (not just reasoned about): downloaded `allure-2.44.0.tgz` with the exact CI
command, ran the job's generate logic verbatim over the **9499 real result files** in
`backend/target/allure-results` → `Report successfully generated to allure-report`, exit 0, with a
valid `index.html` + `data/` + `widgets/`. The "skip empty directory" branch was exercised too (the
`allure-input/e2e` folder was absent and was skipped cleanly).
