# GitHub Actions CI

This repository uses GitHub Actions CI to validate frontend and backend changes and to publish a test report artifact.

## Workflow File

```text
.github/workflows/ci.yml
```

## When CI Runs

The workflow runs on:

- every push;
- every pull request;
- manual trigger (`workflow_dispatch`);
- nightly schedule (`0 3 * * *`, UTC).

## What CI Runs

The pipeline's jobs: `frontend`, `backend`, `android`, `dependency-scan`, `e2e` (Python, after
`backend`), `web-e2e` (Playwright, after `backend`), `allure-report` (aggregates results), and
`deploy` (continuous deployment to Cloud Run, push-to-`main` only). The test jobs are the gate;
`deploy` runs only if its `needs` pass.

### Frontend Job

1. Install Node.js 20.
2. Run `npm ci`.
3. Run `npm test` (Vitest — the `e2e/` Playwright specs are excluded from this).
4. Run `npm audit --audit-level=critical` — **blocks on CRITICAL**, then reports HIGH
   non-blocking. (Policy note: the standing HIGHs are build tooling — vite/esbuild/undici — that
   never ships in the bundle and is only fixable via a breaking Vite upgrade; CRITICALs must always
   be fixed. See **BUG-013** for the time this gate — correctly — held the line on a critical
   `seroval` advisory.)
5. Run `npm run build`.

### Backend Job

1. Install Java 17 (Temurin).
2. Run `cd backend && sh ./mvnw test` (all unit + integration tests, against H2).
3. Print a one-line line-coverage summary from the JaCoCo report.
4. Upload the JaCoCo HTML report (`backend-jacoco-report` artifact).
5. Upload Maven Surefire XML reports (`backend/target/surefire-reports`).
6. Upload Allure raw results (`backend/target/allure-results`).

### E2E Job (`needs: backend`)

This job runs the Python suite in `tests-e2e/` against a **real, running backend**
connected to a **real PostgreSQL** — so the Flyway migrations are exercised
end-to-end, exactly as in production (the unit/integration tests use H2 instead).

1. Start a `postgres:16` service container (database `spira`, user/password `spira`).
2. Install Java 17 and build the backend jar (`mvnw package -DskipTests`).
3. Start the jar under `SPRING_PROFILES_ACTIVE=e2e`, passing `DATABASE_URL` /
   `DATABASE_USERNAME` / `DATABASE_PASSWORD` (so it connects to Postgres and runs Flyway
   on startup) plus **dummy** `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
4. Wait until `GET /health` responds (the health endpoint is served at `/health`).
5. Install Python deps (`pip install -r tests-e2e/requirements.txt`) and run `pytest`.
6. Upload E2E Allure results and stop the backend.

> **Why the `e2e` profile?** Once the app gained Google OAuth, every request became
> auth-gated and user-scoped, so the previously-anonymous E2E tests all returned `401`
> and produced no reports. Real OAuth can't run headlessly in CI, so the `e2e` profile
> enables a test-only `X-E2E-Auth` header login (`E2eTestAuthFilter`) and disables CSRF.
> The dummy Google creds exist only so Spring Security's client config initializes and
> the jar boots — the app never calls Google in this profile. Full background:
> [testing-guide.md → Why the E2E tests had to be rewritten](testing-guide.md#why-the-e2e-tests-had-to-be-rewritten).

> Note: the bundled jar does **not** contain the H2 test profile (that lives under
> `src/test/resources`), so the E2E job deliberately uses real PostgreSQL via the
> production env vars rather than `SPRING_PROFILES_ACTIVE=test`.

### Web E2E Job — Playwright (`needs: backend`)

The browser twin of the Python E2E job: it drives the **real SPA in Chromium** against a real
backend, covering interactions neither Vitest nor the HTTP-level Python suite can see (the Options
drag-and-drop reorder; the inline PDF/image viewers).

1. Start the same `pgvector/pgvector:pg16` service container.
2. Install Java 17 + Node 20, `npm ci`, and `npx playwright install --with-deps chromium`
   (Chromium only — this is a functional check, not a cross-browser matrix).
3. Build and start the backend jar under `SPRING_PROFILES_ACTIVE=e2e`, exactly like the Python job,
   and wait for `GET /health`.
4. Run `npm run test:e2e`. `playwright.config.ts` starts the **Vite dev server** itself, which
   proxies `/graphql` and `/api` to the backend on `:8080`.
5. Upload `playwright-report` + `test-results` as an artifact; on failure, dump the last 200 lines
   of `backend.log`; always stop the backend.

> **Auth:** the `e2e` profile has no auto-login, so the job sets `SPIRA_E2E_AUTH=e2e@test.local` and
> `playwright.config.ts` turns that into `extraHTTPHeaders: { "X-E2E-Auth": … }` on every browser
> request — the same `E2eTestAuthFilter` mechanism the Python suite uses, so the SPA loads
> authenticated without a Google sign-in. Locally you don't set that variable: the `local` profile
> auto-logs-in `dev@local` instead.

### Deploy Job (`needs: frontend, backend, e2e`)

Continuous deployment to Google Cloud Run. It is tightly gated:

- `if: github.ref == 'refs/heads/main' && github.event_name == 'push'` — runs **only on a
  push to `main`** (never on PRs, the nightly schedule, or feature branches).
- `needs: [frontend, backend, e2e]` — runs **only after all three test jobs pass**, so a
  red build never ships.

Steps:

1. Authenticate to GCP with `google-github-actions/auth@v2` using **Workload Identity
   Federation** (keyless): the job requests an OIDC token (`permissions: id-token: write`)
   and GCP exchanges it for short-lived credentials. No service-account key is stored.
2. `gcloud run deploy spira --source . --region europe-west1` — builds the root
   `Dockerfile` and rolls out a new revision.
3. Print the live service URL.

The job reads three non-secret repo **Variables** — `GCP_PROJECT_ID`, `GCP_WIF_PROVIDER`,
`GCP_DEPLOY_SA` — created by the one-time setup script `deploy/setup-github-cd.ps1`. It
deliberately does **not** pass `--set-env-vars` / `--set-secrets`: those live on the
service and persist across revisions, so a deploy can't clobber `FRONTEND_URL`, CORS, or
the Secret Manager bindings. Full setup:
[deploy-gcp-cloud-run.md §11](deploy-gcp-cloud-run.md#11-continuous-deployment-from-github-auto-deploy-on-push).

## Allure Report Integration

The backend test suite is configured to produce Allure results via:

- Maven test dependency: `io.qameta.allure:allure-junit5`;
- test resource config: `backend/src/test/resources/allure.properties`.

Allure output directory:

```text
backend/target/allure-results
```

The `allure-report` job downloads both the backend and E2E raw results and
generates a single combined HTML report, uploaded as the `allure-report` artifact.

## Artifacts produced by a run

| Artifact | What it contains |
|---|---|
| `allure-report` | Combined backend + E2E HTML test report |
| `backend-jacoco-report` | Backend code-coverage HTML report (`index.html`) |
| `backend-surefire-reports` | Raw backend test XML (JUnit/Surefire) |
| `backend-allure-results` / `e2e-allure-results` | Raw Allure results (inputs to the combined report) |

## How to Access Test Results

1. Open the target workflow run in GitHub Actions.
2. Download the `allure-report` artifact and open its `index.html` in a browser.
3. For coverage, download `backend-jacoco-report` and open its `index.html`.
4. For raw backend test XML, use `backend-surefire-reports`.
