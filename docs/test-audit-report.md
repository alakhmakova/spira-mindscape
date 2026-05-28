# Test Audit Report — Spira Mindscape

**Date**: 2026-05-28
**Auditor**: independent review of the test suite (unit, integration, E2E) and the CI pipeline
**Scope**: backend (`backend/`), E2E (`tests-e2e/`), CI (`.github/workflows/ci.yml`). Frontend tests were reviewed at inventory level only.

This report is written to be read by someone newer to testing. Each finding explains *what* it is, *why* it matters, and *how* to fix it.

---

## 1. Verdict at a glance

| Question | Answer |
|---|---|
| Are the tests written correctly? | **Yes.** The tests follow professional patterns. The unit tests in particular are textbook-quality. |
| Are you testing the right things? | **Mostly yes.** The "testing pyramid" is healthy: many fast unit tests, focused integration tests, a thin E2E layer on top. |
| Is the coverage good? | **Good for application logic. Two structural blind spots:** (a) the real database schema/migrations are never exercised, (b) the frontend has almost no test coverage. |
| Do all tests run in GitHub Actions? | **Not yet.** The unit + integration tests run on `main`. The new integration tests, the E2E suite, and the E2E CI job are **uncommitted** and have **never executed in CI**. As written, the E2E CI job **cannot pass** (see §5). |

**Bottom line:** The hard part — writing good tests — you did well. The remaining work is *plumbing*: get everything committed and actually running in CI, and add a real-database job so migrations are covered.

---

## 2. What was reviewed

**Application code (to know what *should* be tested):**
`GoalService`, `TargetService`, `ResourceService`, `RealityService`, `SpiraGraphqlController`, `schema.graphqls`, both `application*.properties`, `GraphQlExceptionHandler`, `pom.xml`.

**Tests read in full:**

| Layer | Files |
|---|---|
| Unit | `GoalServiceTest`, `TargetServiceTest`, `ResourceServiceTest`, `RealityServiceTest`, `GoalValidationTest`, `EntityTimestampTest` |
| Integration | `GoalConfidenceIntegrationTest`, `GoalCascadeDeleteIntegrationTest`, `GoalIsolationIntegrationTest`, `GoalListIntegrationTest` (others sampled) |
| E2E | `conftest.py`, `queries.py`, `test_health`, `test_error_envelope`, `test_goals`, `test_progress`, `test_resources`, `test_reality` (others sampled) |
| CI | `.github/workflows/ci.yml`, plus `gh run` history and git state |

---

## 3. What is done well (keep doing this)

These are genuinely good habits worth recognizing, because they are the things beginners usually get wrong:

1. **Correct test pyramid.** Pure logic (validation, progress math) is tested at the *unit* level where it is fast; API contract behaviour is tested at the *integration* level; only a thin slice is tested end-to-end. This is exactly the right shape.
2. **Tests assert behaviour, not implementation.** They check returned values, error messages, and error *classifications* — not internal call sequences for their own sake.
3. **"It rejects AND does not save."** Validation tests consistently assert both the exception *and* `verify(repository, never()).save(...)`. This catches the subtle bug where validation throws but a partial write already happened. Excellent instinct.
4. **Boundary testing.** Limits are tested on both sides — e.g. title at exactly 200 chars (accepted) and 201 (rejected); note body at 50 000 and 50 001. This is where real bugs hide.
5. **Floating-point comparisons use tolerances.** `isCloseTo(..., offset(...))` in Java and `pytest.approx(...)` in Python. Comparing floats with `==` is a classic beginner trap you avoided.
6. **Parameterized tests** (`GoalValidationTest`, `GoalConfidenceIntegrationTest`) keep many cases readable without copy-paste.
7. **E2E isolation via a fixture.** `created_goal` creates a goal before each test and deletes it after, and list assertions use membership (`created_goal in ids`) rather than exact counts — so tests don't interfere with each other on a shared, stateful backend. This is a mature choice.
8. **Real bugs were found and fixed during the test effort** (date-format handler missing `achievedAt`, option max-length, plural table names). That is exactly what good tests are *for*.

---

## 4. Test quality — issues found (small)

None of these are serious. They are polish items.

| # | Severity | Where | Issue | Fix |
|---|---|---|---|---|
| Q1 | Low | `EntityTimestampTest`, `GoalConfidenceIntegrationTest` | Rely on `Thread.sleep(50)` to force timestamps apart. Time-based tests can become flaky on a busy CI runner. | Acceptable for now. If they ever flake, inject a controllable clock (`Clock` bean) instead of sleeping. |
| Q2 | Low | `test_progress_e2e.py::test_goal_progress_with_checklist_target` | Passes an extra unused variable `{"id": created_goal, "goalId": created_goal}`; the query only declares `$goalId`. Harmless (servers ignore extra variables) but confusing. | Remove the stray `"id"` key. |
| Q3 | Low | several E2E files | `import pytest` is unused in files that don't call `pytest.*`. | Remove unused imports (or rely on a linter like `ruff`). |
| Q4 | Low | `test_goals_e2e.py` create tests | A few tests create a goal, then run several `assert`s, then delete at the end. If an early assert fails, the goal is **leaked** into the shared DB (no `try/finally`/fixture finalizer). Over many failed runs this accumulates. | Prefer the `created_goal` fixture, or wrap creation in a fixture with teardown, so cleanup always runs. |
| Q5 | Info | `HealthController` | No direct test for the health endpoint; `test_health.py` exercises `/graphql`, not the controller. | Add one tiny E2E hit on the health URL, or a `@WebMvcTest`. Optional. |

---

## 5. CI pipeline — the important findings

This is the section that needs action. The question was: *do all tests run through GitHub Actions?*

### 5.1 What the workflow file *intends* to do

The working-tree `ci.yml` defines a good 3-stage pipeline:

```
frontend  →  (npm test + build)
backend   →  (mvnw test  = all unit + integration tests)
e2e       →  needs[backend]: build jar, start backend, run pytest
allure    →  aggregate reports
```

That structure is correct and is what you want.

### 5.2 Finding C1 — the new tests and the E2E job are **not committed**, so they have **never run in CI**

Evidence:

```
git status  →  M .github/workflows/ci.yml        (modified, NOT committed)
git show HEAD:.github/workflows/ci.yml | grep "Python E2E"        → 0 matches
git show origin/main:.github/workflows/ci.yml | grep "Python E2E" → 0 matches
```

So the E2E job exists **only in your local working tree**. The committed workflow (on both your branch's `HEAD` and `main`) has no E2E job. The new integration tests and the whole `tests-e2e/` suite are also uncommitted/untracked.

The `gh run` history confirms it: every green run on `main` finishes in ~**1 minute** — far too short to package a jar, boot Spring, and run 110 Python tests. Those runs are the *old* pipeline on the *old* (smaller) test set. The branch's own pushes show `action_required` with **0s** duration — they were never approved/executed.

**Meaning:** right now, **none** of the work in this audit (new integration tests + E2E) is protected by CI. It only runs on your machine.

**Fix:** commit the workflow and the test files, push the branch, and (because the branch shows `action_required`) approve the workflow run in the GitHub Actions UI so it actually executes.

### 5.3 Finding C2 — as written, the E2E job **cannot start the backend**, so it will fail

Even once committed, the E2E job will fail. Here is the exact chain:

1. The job builds the app jar: `mvnw package -DskipTests`. A packaged Spring Boot jar contains `src/main/resources/**` but **not** `src/test/resources/**`.
2. `application-test.properties` (the H2 in-memory config) lives in **`src/test/resources`**, so it is **not inside the jar**.
3. The job starts the jar with `SPRING_PROFILES_ACTIVE=test`. Spring looks for `application-test.properties` on the classpath, doesn't find it, and falls back to the bundled `application.properties`.
4. The bundled `application.properties` points at **PostgreSQL** (`jdbc:postgresql://localhost:5432/spira`), with `flyway.enabled=true` and `ddl-auto=validate`.
5. The E2E job defines **no PostgreSQL service**. There is no database on `localhost:5432`.
6. → Startup fails (no DB connection / Flyway can't run) → the "Wait for backend to be ready" loop times out after 60s → **the job fails**.

In short: `SPRING_PROFILES_ACTIVE=test` has **no effect at runtime** because the test profile isn't shipped in the jar, and the real profile needs a database that the job never provides.

### 5.4 Finding C3 — the production database schema & migrations are **never tested** (related to C2)

This is the biggest *coverage* gap, and it connects to the fix for C2.

- Every Java test runs against **H2** with `ddl-auto=create-drop` and **Flyway disabled**. The schema the tests use is *generated by Hibernate*, not the real schema.
- Production uses **PostgreSQL** with **hand-written Flyway migrations** (`V1`–`V6`) and `ddl-auto=validate`.
- Nothing ever runs those migrations. If a migration has a typo, a missing column, a wrong type, or a missing `ON DELETE CASCADE`, **all 568 tests still pass** but production breaks on deploy. (This class of bug already bit you once — the plural-table-name issue.)

### 5.5 Recommended fix for C2 + C3 together (one change closes both)

Give the E2E job a real PostgreSQL service and point the backend at it. This starts the backend successfully (fixes C2) **and** runs the real Flyway migrations against real Postgres (fixes C3).

```yaml
  e2e:
    name: Python E2E tests
    needs: [backend]
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: spira
          POSTGRES_USER: spira
          POSTGRES_PASSWORD: spira
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      # ... checkout, setup-java, build jar (as today) ...
      - name: Start backend
        run: |
          java -jar backend/target/*.jar &
          echo $! > backend.pid
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/spira
          DATABASE_USERNAME: spira
          DATABASE_PASSWORD: spira
      # ... wait, setup-python, pytest (as today) ...
```

(Note: no `SPRING_PROFILES_ACTIVE=test` here — you *want* the real Postgres + Flyway path for a true end-to-end test.)

**Simpler alternative** (fixes C2 only, *not* C3): create `src/main/resources/application-e2e.properties` with the H2 settings and start with `SPRING_PROFILES_ACTIVE=e2e`. Because it lives under `main`, it ships inside the jar. This makes the job pass but still never tests the real database, so it does not address C3. The Postgres-service approach above is recommended.

---

## 6. Coverage gaps (what is not tested yet)

| # | Gap | Layer | Risk | Suggested action |
|---|---|---|---|---|
| G1 | Flyway migrations / real Postgres schema never run | Infra | **High** — schema drift invisible until deploy | Fix via §5.5 (Postgres in E2E). Optionally add a small `@SpringBootTest` that runs against Testcontainers Postgres. |
| G2 | Frontend has only ~30 unit tests; no component/integration/E2E | Frontend | Medium — UI wiring (forms, store→API) unverified | Add a few component tests (e.g. Testing Library) for the critical flows; long-term, a browser E2E (Playwright). |
| G3 | No line-coverage measurement | Infra | Low — you don't know which branches are untested | Add JaCoCo to the backend build; print a coverage summary in CI. Treat the number as a guide, not a target. |
| G4 | Confidence history not surfaced/tested in frontend | Frontend | Low | Covered fully on the backend; add when the UI exposes it. |
| G5 | `reorderOptions` happy path only covered at integration level, not unit | Unit | Very low | Optional: add one unit test asserting new positions. |
| G6 | Email resource `role`/`phone` fields and link-label refresh-on-update | Integration | Low | Verify these are in `ResourceIntegrationTest`; add if missing (the unit tests cover trimming, but the GraphQL field-allow matrix is the place to confirm `role`/`phone`). |

---

## 7. Prioritized action list

**P0 — do first (makes CI real):**
1. Commit the modified `ci.yml`, all new integration tests, and `tests-e2e/`. Push the branch.
2. Apply the Postgres-service fix from §5.5 so the E2E job can actually start the backend.
3. Approve/trigger the workflow run and confirm all three jobs go green. Until you *see* green, assume nothing runs.

**P1 — do soon (closes the big blind spot):**
4. Confirm the E2E job (now on real Postgres) exercises Flyway. This is your migration safety net (G1/C3).
5. Add JaCoCo coverage reporting (G3) so future gaps are visible.

**P2 — nice to have:**
6. Add a handful of frontend component tests for the main flows (G2).
7. Clean up the small polish items in §4 (Q2–Q4).

---

## 8. How to run everything (reference)

```powershell
# Frontend (repo root)
npm.cmd test
npm.cmd run build

# Backend unit + integration (H2, no DB needed)
cd backend
.\mvnw.cmd test

# E2E — needs a running backend on http://localhost:8080
cd tests-e2e
pip install -r requirements.txt
pytest
```

See `docs/testing-guide.md` for the per-file breakdown and `docs/test-coverage-report.md` for the coverage matrices.

---

## 9. Summary

You asked three things: is the coverage good, did you write the tests correctly, and do they all run in CI.

- **Did you write them correctly?** Yes — the craftsmanship is strong (pyramid shape, behaviour assertions, "never saved" checks, boundary cases, float tolerances, E2E isolation).
- **Is coverage good?** Good for application logic; two real blind spots — the production database/migrations (G1) and the frontend (G2).
- **Do they all run in CI?** No — the new integration + E2E work is uncommitted and has never run, and the E2E job as written cannot start the backend. §5 and §7 tell you exactly how to fix that.

Fix the CI plumbing (§7, P0) and add a real-database job (P1), and this becomes a genuinely solid, trustworthy test suite.
