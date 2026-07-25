# CI permanently red: a critical `seroval` advisory blocks the frontend and Trivy jobs

- **ID:** BUG-013
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** User ("при мердже все тесты провалились"), diagnosed by Claude
- **Area:** CI (`.github/workflows/ci.yml` — `frontend` + `dependency-scan` jobs), `package.json`
- **Severity:** High (every build red → the signal is worthless and deploys stay skipped)

## Summary

Every CI run — on `main` (daily schedule), on branches, and on PRs — failed. The user noticed it
at merge time and assumed the new changes broke the tests. They did not: **the tests passed**. Two
jobs died on a **security gate**, and they had been failing that way since at least 2026-07-21.

## Steps to reproduce

1. Open any CI run (e.g. the nightly one on `main`) → the `Frontend tests and build` job is red.
2. Look at its steps: `Run frontend tests` = **success**; `npm audit (block on critical)` =
   **failure**; `Build frontend` = skipped (never runs).
3. `Dependency vulnerability scan` is red the same way at `Trivy scan — block on CRITICAL`.

## Root cause (confirmed)

A single transitive dependency: **`seroval@1.5.2`**, pulled in by
`@tanstack/react-router → @tanstack/router-core`.

- Advisory **CVE-2026-59940 / GHSA-mv8w-475r-vwqw** — *"`seroval.fromJSON()` Promise resolver type
  confusion invokes attacker-controlled methods during deserialization"*, severity **CRITICAL**,
  affects `<=1.5.2`, fixed in **1.5.3**.
- The `frontend` job runs `npm audit --audit-level=critical` (policy: block on CRITICAL, report
  HIGH) → exit 1.
- The `dependency-scan` job runs `trivy fs --severity CRITICAL --exit-code 1` → it reports exactly
  **one** CRITICAL, the same `seroval` CVE → exit 1.

So one vulnerable package failed two jobs, and it looked like "all the tests are failing".

Notably `npm audit fix` does **not** resolve it: `router-core` pins `seroval@1.5.2`, so the fix
requires either an override or a `@tanstack/react-router` upgrade. (Verified with
`npm audit fix --dry-run`: still `critical: 1` afterwards.)

## Fix approach

Force the patched `seroval` via an npm `overrides` block — minimal, no framework upgrade, and it
satisfies both gates at once.

## How to verify fixed

1. `npm ls seroval` → `1.5.6 overridden` (and `seroval-plugins@1.5.6`).
2. `npm audit --audit-level=critical` → **exit 0** (remaining HIGHs are pre-existing build tooling
   that CI reports but does not block).
3. `npx tsc --noEmit`, `npm test`, `npm run build` all pass — the override is not breaking.
4. In CI the `Frontend tests and build` job now reaches `Build frontend`, and
   `Dependency vulnerability scan` passes.

## Resolution

Files changed:

- **`package.json`** — added an `overrides` block pinning `seroval` and `seroval-plugins` to
  `^1.5.6` (first patched release is 1.5.3).
- **`package-lock.json`** — regenerated; both entries now resolve to `1.5.6`.

Verification: `npm ls seroval` → 1.5.6 overridden; `npm audit --audit-level=critical` → exit 0
(7 vulnerabilities remain: 2 low + 5 high, all pre-existing build tooling — vite/esbuild/undici —
which the CI policy reports but does not block); `npx tsc --noEmit` clean; `npm test` → 75 passed;
`npm run build` succeeded.

**Note for whoever revisits this:** the remaining HIGHs are only fixable via a breaking Vite
upgrade, which is why the CI policy deliberately reports rather than blocks on them. Revisit when
Vite 8 lands.
