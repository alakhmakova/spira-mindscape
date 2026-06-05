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

The pipeline has four jobs: `frontend`, `backend`, `e2e` (runs after `backend`),
and `allure-report` (aggregates results).

### Frontend Job

1. Install Node.js 20.
2. Run `npm ci`.
3. Run `npm test` (Vitest).
4. Run `npm run build`.

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
