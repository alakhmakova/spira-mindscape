# Plan: Backend Test Coverage — QA Audit & Expansion

**Author**: QA Lead (senior automation)
**Last Updated**: 2026-05-26
**Status**: Active plan — replaces previous task-tracking version

---

## 1. Current State Assessment

### 1.1 What Exists

**Unit tests (6 files, ~130 test cases)**

| File | Domain | Coverage |
|---|---|---|
| `GoalServiceTest` | Goal CRUD, options, confidence history | Partial — missing delete, findAll, findById, removeOption, updateOption success path |
| `RealityServiceTest` | Kind normalization, batched grouping | Very thin — only 3 tests; no add/update/remove coverage |
| `TargetServiceTest` | Progress calculation, create/update validation | Good — progress math is well covered |
| `ResourceServiceTest` | All resource types, validation, CRUD | Very thorough — best file in the suite |
| `GoalValidationTest` | Bean validation on `Goal` entity | Good — exhaustive boundary tests |
| `EntityTimestampTest` | `@PrePersist`/`@PreUpdate` hooks | Good — covers all entities |

**Integration tests (7 files, ~180 test cases)**

| File | Domain | Coverage |
|---|---|---|
| `GoalCreationIntegrationTest` | Goal CRUD via GraphQL | Good — happy paths + validation + timestamps |
| `GoalConfidenceIntegrationTest` | Confidence validation, history, cascade delete | Good |
| `GoalWorkspaceIntegrationTest` | Progress for all target types; unknown resource type | Good but thin; only 2 tests |
| `RealityIntegrationTest` | Reality CRUD, validation, isolation | Very thorough |
| `OptionIntegrationTest` | Option CRUD, select, reorder | Very thorough |
| `TargetIntegrationTest` | Target CRUD for all types, progress, deadlines | Very thorough |
| `ResourceIntegrationTest` | Resource CRUD for all types | Thorough |

### 1.2 Quality Problems Found

#### Critical (can cause false negatives or unreliable test runs)

1. **`Thread.sleep()` in 4 test files** — `GoalCreationIntegrationTest`, `GoalConfidenceIntegrationTest`, `EntityTimestampTest`, `GoalConfidenceIntegrationTest` all use `Thread.sleep()` to force timestamp differences. These tests are slow by design and flake when the machine is under load. Fix: use `InstantSource`/`Clock` injection or assert ordering without millisecond equality.

2. **String template injection in `GoalWorkspaceIntegrationTest.createTarget()`** — The method patches a raw GraphQL document string via `.replace("INPUT_PLACEHOLDER", input)`. This bypasses GraphQL variable binding and is effectively string injection. Any special character in the test data breaks the document. Fix: proper variable binding or structured helpers.

3. **`@BeforeEach` used for cleanup in `GoalConfidenceIntegrationTest`** — Cleanup runs *before* each test, meaning a failed test leaves dirty state that poisons the next test's count assertions. Fix: move cleanup to `@AfterEach`.

#### High (reduce maintainability or hide gaps)

4. **Missing `@DisplayName` on test methods in `RealityServiceTest` and `EntityTimestampTest`** — Test report output is method-name-only, which is hard to read for non-technical stakeholders.

5. **Magic IDs** — `"999999"` is used in many integration tests as a "non-existent" id. If the test DB ever auto-increments to that value, tests pass for the wrong reason. Fix: use `Long.MAX_VALUE` or a UUID that cannot collide.

6. **Weak count assertion in `assertNoGoalsCreated()`** — Calls `goalRepository.count() == 0`. If tests ever run in parallel this will fail spuriously. Fix: assert no goal was created matching the specific title used in the test.

7. **Inconsistent test structure** — Some classes use `@BeforeEach` for goal creation, others create goals inline per test. Pick one pattern and standardise.

8. **`GoalWorkspaceIntegrationTest` tests resource rejection and progress in the same class** — Two unrelated concerns. Should be split.

---

## 2. Coverage Gaps

### 2.1 Missing Unit Tests

**GoalService — untested methods:**
- `findAll()` — no unit test; not even a smoke check
- `findById()` — no unit test; NOT_FOUND exception path untested at service level
- `delete()` — no unit test; cascade behavior untested
- `removeOption()` — no unit test
- `updateOption()` success path — only rejection tested
- `addOption()` when no existing options (position = 0 base case)
- `findOptionsByGoalIds()` / `findConfidenceHistoryByGoalIds()` batch methods

**RealityService — untested methods:**
- `addItem()` — completely missing
- `updateItem()` — completely missing
- `removeItem()` — completely missing
- `findByGoal()` — not tested
- Blank text validation — missing at service level

**TargetService — untested methods:**
- `findByGoal()`, `findById()`, `delete()` — no unit tests
- `findByGoalIds()`, `findItemsByTargetIds()` — no unit tests
- Numeric: equal start and total rejection — not tested at unit level
- All `update()` branches (numeric range clamp, binary toggle, checklist merge)

**Entities without validation tests:**
- `Option` — no `@Size` or `@NotBlank` tests
- `ChecklistItem` — no `@Size` or `@NotBlank` tests
- `RealityItem` — no `@Size` tests
- `Resource` — no `@Size` tests (`body`, `dataUrl`)
- `Target` — no field-level validation tests

### 2.2 Missing Integration Tests

**Queries not covered:**
- `goals` (list all) — not tested; ordering not verified
- `goals` list with multiple goals — BatchMapping behavior untested
- `resourcesByGoal` and `resourceById` — not tested via standalone queries
- `targetById` for non-numeric types

**Cascade behaviour:**
- Delete goal → targets, resources, options, reality items, confidence_history all deleted — not verified end-to-end
- Delete target → checklist items deleted — not verified

**Error classification coverage:**
- `updateGoal` on non-existent id → NOT_FOUND — not tested
- `createResource` for non-existent goal → NOT_FOUND — not tested explicitly
- `realityByGoal` for non-existent goal — not tested

**Cross-entity behaviour:**
- Goal progress recalculates when target is deleted — not tested
- Option isolation: options of goal A do not appear in goal B query — not tested
- Resource isolation between goals — not tested

**Infrastructure:**
- `/actuator/health` or `/health` endpoint — not tested
- CORS headers — not tested
- GraphQL error envelope shape consistency — tested per endpoint but not as a contract

### 2.3 Missing Test Layers (by type)

| Layer | Current | Gap |
|---|---|---|
| Unit | Java/JUnit5/Mockito | Thin on service query methods and entity validation |
| Integration (Spring) | Java/JUnit5/@SpringBootTest | Cascade deletes, `goals` list, cross-goal isolation |
| E2E | **None** | Full stack from HTTP to DB — nothing exists |
| Contract | **None** | No verification that schema matches frontend expectations |
| Performance | **None** | No load tests; no query complexity limits tested |
| Security | **None** | GraphQL injection, deeply nested queries (DoS) |
| Migration | **None** | Flyway scripts not run against a real PostgreSQL |

---

## 3. Work Plan

### Phase A — Fix Existing Tests (Priority: Critical/High defects)

**A1. Fix `GoalWorkspaceIntegrationTest.createTarget()` template injection**
Replace `.replace("INPUT_PLACEHOLDER", input)` with typed helper methods that use proper variable binding, one per target type.

**A2. Fix `Thread.sleep()` in timestamp tests**
Extract a `waitForClockAdvance()` utility method. Consider injecting `Clock` into services so tests can advance time deterministically instead of sleeping.

**A3. Fix `@BeforeEach` cleanup in `GoalConfidenceIntegrationTest`**
Move cleanup to `@AfterEach`.

**A4. Replace magic IDs with `Long.MAX_VALUE.toString()`**
Do this across all 7 integration test files.

**A5. Add `@DisplayName` to all test methods in `RealityServiceTest` and `EntityTimestampTest`**

### Phase B — Expand Unit Tests (Java)

**B1. GoalService unit tests — query methods**
- `findAll()` → happy path returns list
- `findById()` → found, NOT_FOUND throws
- `delete()` → calls repository, throws NOT_FOUND when missing
- `removeOption()` → calls optionRepository.delete
- `updateOption()` → happy path updates text and/or selected flag

**B2. RealityService unit tests — mutation methods**
- `addItem()` → normalizes kind, saves item, returns updated payload
- `updateItem()` → validates ownership, updates text, returns updated payload
- `removeItem()` → validates ownership, deletes item
- `findByGoal()` → delegates to `buildRealityByGoalIds`

**B3. Entity validation unit tests**
- `Option`: `@NotBlank text`, `@Size(max=500) text`
- `ChecklistItem`: `@NotBlank text`, `@Size(max=500) text`
- `RealityItem`: `@Size(max=5000) text` — verify boundary
- `Resource`: `@Size(max=50000) body`, `@Size(max=50000) dataUrl`
- `Target`: all annotated fields

**B4. TargetService unit tests — update paths**
- Numeric range validation on update
- Binary toggle validation
- Checklist items merge (add, edit, delete existing items)

### Phase C — Expand Integration Tests (Java)

**C1. `goals` list query**
- Empty DB returns empty list
- Multiple goals returned in consistent order
- BatchMapping for reality, options, resources, targets, progress, confidenceHistory resolves for each goal

**C2. Cascade delete tests**
- `deleteGoal` → verify no orphan targets, resources, options, reality_items, confidence_history rows
- `deleteTarget` → verify no orphan checklist_items

**C3. Cross-goal isolation tests**
- Options from goal A not visible in goal B's `optionsByGoal`
- Resources from goal A not visible in goal B's `resourcesByGoal`
- Reality items from goal A not visible in goal B's `realityByGoal`

**C4. Missing error paths**
- `updateGoal` on non-existent id → NOT_FOUND
- `createResource` for non-existent goal → NOT_FOUND
- `realityByGoal` for non-existent goal → NOT_FOUND / error shape

**C5. Goal progress recalculates after target deletion**
- Create goal with 2 targets (50% + 100%)
- Delete the 100% target
- Verify goal progress is now 50%

**C6. Split `GoalWorkspaceIntegrationTest`**
- Move progress tests to `GoalProgressIntegrationTest`
- Move resource rejection to `ResourceIntegrationTest`

### Phase D — Python E2E Test Suite

See section 4 below.

### Phase E — Contract and Schema Tests

**E1. GraphQL schema validation test**
Write a test that fetches the introspection schema and verifies required types, fields and non-nullability match the documented contract.

**E2. Error envelope contract test**
Every GraphQL error must have `extensions.classification`. Write a test that exercises each known error type and asserts on the envelope structure, not just the message string.

---

## 4. Python Test Suite Plan

### Why Python for E2E?

The backend is Java/Spring. Java integration tests cover the Spring context thoroughly. Python adds a separate out-of-process layer that:
- Tests the actual HTTP GraphQL endpoint (not the Spring test transport)
- Can run against any environment (local, staging, prod)
- Verifies CORS headers, content-type negotiation, HTTP status codes
- Can be used for load/smoke testing in CI without requiring Java toolchain

### Proposed Python Stack

| Library | Purpose |
|---|---|
| `pytest` | Test runner, fixtures, parametrize |
| `gql` (graphql-core3 transport) | GraphQL client — typed queries |
| `httpx` | HTTP assertions (headers, status, CORS) |
| `pytest-asyncio` | Async test support for concurrent scenarios |
| `locust` | Load testing (separate from pytest) |
| `pydantic` | Response schema validation |

### Proposed Directory Structure

```
tests-e2e/
  conftest.py              # base URL fixture, client fixture, goal factory
  graphql/
    queries.py             # reusable query/mutation strings
  test_health.py           # health endpoint smoke test
  test_goals_e2e.py        # goal CRUD E2E
  test_reality_e2e.py      # reality E2E
  test_options_e2e.py      # options E2E
  test_targets_e2e.py      # targets E2E
  test_resources_e2e.py    # resources E2E
  test_progress_e2e.py     # progress calculation E2E
  test_cors.py             # CORS header assertions
  test_error_envelope.py   # GraphQL error shape contract
  test_schema_contract.py  # introspection-based schema contract
  perf/
    locustfile.py          # load test scenarios
```

### Python E2E Test Coverage List

**Smoke (run on every deploy):**
- `GET /health` returns 200
- `POST /graphql` with minimal query returns 200

**Goal E2E:**
- Create goal → read back → update → delete lifecycle
- Error on create with missing required field

**Reality E2E:**
- Add action, add obstacle, read back, update, remove

**Options E2E:**
- Add options, select one, verify others deselected, reorder, remove

**Targets E2E:**
- Binary, numeric, checklist create → update → delete lifecycle
- Progress changes as targets are updated

**Resources E2E:**
- Note, link, file, email create → update → delete lifecycle

**CORS E2E:**
- `OPTIONS /graphql` returns expected CORS headers
- `POST /graphql` from allowed origin returns Access-Control-Allow-Origin

**Error shape E2E:**
- NOT_FOUND errors have correct classification
- ValidationError errors have correct classification

**Progress E2E:**
- Create 3 targets, update to specific progress values, verify goal-level progress is correct average

### Can Integration Tests Be Written in Python?

**Short answer: yes, but only as out-of-process HTTP tests.**

Python cannot access the Spring application context, so it cannot inject mocks or use `@SpringBootTest`. Any Python test is an HTTP-level test (E2E by definition). However this is not a limitation — it means:
- Python integration tests = "smoke integration" tests against a local running instance
- They complement Java `@SpringBootTest` tests, not replace them
- The missing unit-level coverage (GoalService query methods, entity validation) **must** stay in Java

**Recommendation**: Use Python exclusively for E2E and contract tests. Keep Java for unit and Spring integration tests.

---

## 5. Migration Test Plan

Currently excluded (H2 vs PostgreSQL). Recommend adding as a separate CI job:

- Spin up PostgreSQL via TestContainers in a dedicated Maven profile (`-P integration`)
- Run Flyway migrations against real PostgreSQL
- Re-run all integration tests
- This catches PostgreSQL-specific SQL issues (e.g., case sensitivity, sequence behaviour)

Not blocking for current phase but should be tracked as Phase F.

---

## 6. Execution Order

```
Phase A  (fix)          → 1 week, Java, no new tests
Phase B  (unit gaps)    → 1–2 weeks, Java
Phase C  (integration)  → 1–2 weeks, Java
Phase D  (Python E2E)   → 2 weeks, Python, requires running local server
Phase E  (contract)     → 1 week, Java + Python
Phase F  (PG migration) → separate CI job, Java/TestContainers
```

---

## 7. Success Criteria for Full Plan

- All existing tests green, `Thread.sleep()` eliminated from test suite
- Unit test coverage reaches all public service methods
- Entity validation tests cover all `@Size`, `@NotBlank`, `@NotNull` annotations
- Integration tests cover cascade deletes and cross-goal isolation
- Python E2E suite runs against local instance in CI with `pytest tests-e2e/`
- Python smoke suite runs against staging in CD pipeline
- No test uses `.replace()` for GraphQL document construction
- No test uses `"999999"` as a magic non-existent id
