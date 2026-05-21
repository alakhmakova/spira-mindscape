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

### Frontend Job

1. Install Node.js 20.
2. Run `npm ci`.
3. Run `npm test`.
4. Run `npm run build`.

### Backend Job

1. Install Java 17 (Temurin).
2. Run `cd backend && sh ./mvnw test`.
3. Upload Maven Surefire XML reports (`backend/target/surefire-reports`).
4. Upload Allure raw results (`backend/target/allure-results`).

## Allure Report Integration

The backend test suite is configured to produce Allure results via:

- Maven test dependency: `io.qameta.allure:allure-junit5`;
- test resource config: `backend/src/test/resources/allure.properties`.

Allure output directory:

```text
backend/target/allure-results
```

The report is generated in CI with:

```yaml
- name: Allure Report
  uses: simple-elf/allure-report-action@ec94841949c65c674aadd3c97a648218e0be1153 # v1
  with:
    allure_results: backend/target/allure-results
```

The generated HTML report is uploaded as the `allure-report` workflow artifact.

## How to Access Test Results Report

1. Open the target workflow run in GitHub Actions.
2. Download the `allure-report` artifact.
3. Open `index.html` from the artifact contents in a browser.

For raw backend test XML files, use the `backend-surefire-reports` artifact.
