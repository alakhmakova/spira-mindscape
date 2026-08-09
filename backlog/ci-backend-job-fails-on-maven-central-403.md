# CI backend job dies in 13s: Maven Central answers 403 to the wrapper's download

- **ID:** BUG-035
- **Status:** ✅ Fixed (2026-08-09) — see Resolution. Pending manual commit by the user.
- **Reported by:** User ("Backend tests failed при попытке PR and merge"), diagnosed by Claude
- **Area:** CI (`.github/workflows/ci.yml`), `backend/mvnw`, `backend/mvnw.cmd`
- **Severity:** High (a red backend job blocks the PR, and `e2e` / `web-e2e` / `deploy` all
  `needs: [backend]`, so nothing downstream runs)

## Summary

The `Backend tests` job on PR #20 (`mobile` → `main`) failed **13 seconds** in, before a single
test ran. The tests are fine: the same commit had passed on the `push` event 20 minutes earlier.
The job never got as far as Maven — `./mvnw` could not download Maven itself.

## Steps to reproduce

Not deterministic (it depends on which shared runner IP the job lands on):

1. Open CI run `31269816753` → job `Backend tests` (13s, red).
2. Step `Run backend tests` output is a single line: `curl: (22) The requested URL returned
   error: 403`, then `Process completed with exit code 22`.
3. The following steps confirm nothing ran: "No files were found with the provided path:
   `backend/target/surefire-reports`" (and the same for the JaCoCo and Allure paths).

## Root cause (confirmed)

`backend/.mvn/wrapper/` is gitignored (`.gitignore:32-33`), so the wrapper downloads the
Apache Maven distribution **on every CI run**. It fetched from a single host —
`repo.maven.apache.org` — which is fronted by a CDN that **rate-limits shared CI egress IPs
and answers 403**. `curl -f` exits 22 on that, and curl deliberately does **not** retry a 403
(only 408/429/5xx), so one throttled response failed the whole job.

`actions/setup-java`'s `cache: 'maven'` does not help: it caches `~/.m2` (the dependencies),
not the Maven distribution the wrapper installs.

The same hazard is already documented elsewhere in this workflow — the `dependency-scan` job
carries a comment about Maven Central rate-limiting the shared runner IP, which is why Trivy
was moved to `--offline-scan`.

## Fix approach

Two layers, so the failure mode disappears rather than becoming rarer:

1. **Don't download at all in the normal case** — cache the unpacked distribution in each of
   the four jobs that invoke `./mvnw` (`backend`, `dependency-scan`, `e2e`, `web-e2e`), keyed
   on `hashFiles('backend/mvnw')` (the file that pins the version).
2. **Survive a cache miss** — the wrapper now tries **two** mirrors (Maven Central, then the
   permanent `archive.apache.org`) and sweeps the list up to three times with a 5s pause,
   instead of failing on the first 403.

`dlcdn.apache.org` was tried as a third mirror and rejected: it only mirrors the *current*
release, so it 404s for a pinned older version (verified — 3.9.9 is not there).

## How to verify fixed

1. Fresh-download path: copy `backend/mvnw` (or `mvnw.cmd`) into an empty directory and run
   `sh ./mvnw -v` → downloads, unpacks, prints `Apache Maven 3.9.9`.
2. Failure path: same, with `MAVEN_VERSION` set to a bogus value → 3 sweeps × 2 mirrors, each
   logged as `Download failed: <url>`, then `Could not download Apache Maven … from any
   mirror.` and **exit 1** (not a silent success).
3. In CI, the `Cache Maven distribution (wrapper)` step reports a hit on the second run
   onward, and `Run backend tests` no longer performs any download.

## Resolution

Files changed:

- **`backend/mvnw`** — mirror list + retry sweeps replacing the single-URL `curl`; explicit
  error and non-zero exit when every mirror fails.
- **`backend/mvnw.cmd`** — the same two mirrors and retry loop. Also restructured from
  `if not exist (…)` to `if exist … goto run`: to *skip* a parenthesised block cmd must
  paren-match its way to the closing `)`, and the PowerShell one-liner is full of
  parentheses — that mis-parse chopped the `call` line and broke every invocation where
  Maven was already unpacked.
- **`.github/workflows/ci.yml`** — a `Cache Maven distribution (wrapper)` step in the four
  jobs that run `./mvnw`, plus a `timeout-minutes` cap on **every** job (see below).

### Also in this change: job timeouts

While diagnosing the above, the user asked about the other red run, `31127646063`
(2026-08-06, `workflow_dispatch` on `main`). Different cause, and **not a code defect**:
`Web E2E (Playwright)` there was **cancelled**, not failed, after 15 minutes — the API
records `conclusion: cancelled`, no step data, and the logs have since expired, so the stuck
step cannot be identified after the fact. The same commit `cc9a857` passed that job three
times afterwards (07-08, 08-08, 09-08) in ~2-2.5 min each, so it was a one-off stall that
someone had to cancel by hand.

The gap it exposed is real, though: no job declared `timeout-minutes`, so GitHub's **6-hour**
default applied. A job wedged on a network stall (npm, apt, or Maven — none of which fail
fast on their own) would sit there burning runner minutes until a human noticed. Every job
now caps at roughly 4x its observed duration (15-40 min), which never trips on a healthy run
but converts a hang into a fast, clearly labelled failure. The `--connect-timeout 20` added
to `backend/mvnw` closes one specific source of an indefinite stall.

Verified locally: both wrapper scripts on the fresh-download path (Maven 3.9.9 unpacked and
`-v` printed) and on the all-mirrors-fail path (exit 1 with the message above); `backend`
test suite green.

**Note for whoever revisits this:** `.mvn/wrapper/maven-wrapper.properties` still exists but
is **not read** by these scripts — the version and URLs live in `mvnw` / `mvnw.cmd`. If the
Maven version is ever bumped, change it there (the CI cache key follows `backend/mvnw`
automatically).
