# Validation: Backend Test Coverage — QA Audit & Expansion

**Author**: QA Lead (senior automation)
**Last Updated**: 2026-05-26
**Status**: Active — tracks both inherited state and new work

---

## Legend

- [x] Verified and passing
- [ ] Not yet done — pending
- [!] Exists but has a defect — see note

---

## Section 1: Automated Build Checks (Inherited)

- [x] `npm.cmd test` — frontend unit tests pass
- [x] `npm.cmd run build` — frontend production build succeeds
- [x] `cd backend && .\mvnw.cmd test` — all Java tests pass
- [x] `npm test` (Linux CI)
- [x] `npm run build` (Linux CI)
- [x] `cd backend && sh ./mvnw test` (Linux CI)

---

## Section 2: CI / Reporting (Inherited)

- [x] `.github/workflows/ci.yml` exists
- [x] CI triggers on push, pull_request, workflow_dispatch, schedule
- [x] CI runs frontend tests and build
- [x] CI runs backend Maven tests
- [x] Backend tests emit Allure results to `backend/target/allure-results`
- [x] Workflow uses `simple-elf/allure-report-action@v1`
- [x] Workflow uploads backend surefire artifacts
- [x] Workflow uploads Allure HTML artifact
- [x] CI documented in `docs/github-actions-ci.md`
- [ ] CI runs Python E2E suite as a separate job

---

## Section 3: Existing Test Quality Defects

These defects exist in the current test suite and must be fixed before the suite can be trusted as a stable baseline.

### Critical

- [!] `Thread.sleep()` in `GoalCreationIntegrationTest` (line ~692), `GoalConfidenceIntegrationTest` (line ~156, ~159), `EntityTimestampTest` (lines ~22, ~34, ~49, ~60, ~72)
  - **Defect**: Timing-dependent tests are non-deterministic under load
  - **Fix**: Inject `Clock` into services or assert ordering only, not millisecond equality
  - **Status**: NOT FIXED

- [!] String injection in `GoalWorkspaceIntegrationTest.createTarget()` (line ~162)
  - **Defect**: `.replace("INPUT_PLACEHOLDER", input)` bypasses GraphQL variable binding
  - **Fix**: Replace with properly bound helper methods per target type
  - **Status**: NOT FIXED

- [!] `@BeforeEach` used for cleanup in `GoalConfidenceIntegrationTest` (line ~39)
  - **Defect**: Dirty state on test failure poisons the next test's count assertions
  - **Fix**: Move to `@AfterEach`
  - **Status**: NOT FIXED

### High

- [!] Magic ID `"999999"` in integration tests
  - **Files**: `GoalCreationIntegrationTest`, `GoalWorkspaceIntegrationTest`, `RealityIntegrationTest`, `OptionIntegrationTest`, `TargetIntegrationTest`, `ResourceIntegrationTest`
  - **Fix**: Replace with `String.valueOf(Long.MAX_VALUE)`
  - **Status**: NOT FIXED

- [!] Missing `@DisplayName` in `RealityServiceTest` and `EntityTimestampTest`
  - **Status**: NOT FIXED

- [!] `assertNoGoalsCreated()` uses `goalRepository.count() == 0` instead of title-scoped check
  - **Status**: NOT FIXED

---

## Section 4: Existing Test Coverage Checks (Inherited — Verified)

### Frontend

- [x] Frontend progress unit tests cover numeric targets
- [x] Frontend progress unit tests cover Done / Not Done targets
- [x] Frontend progress unit tests cover checklist targets
- [x] Frontend progress unit tests cover goal progress averaging

### Backend Unit Tests

- [x] `GoalServiceTest` — create with trimmed title/description
- [x] `GoalServiceTest` — max-length description accepted
- [x] `GoalServiceTest` — title > 200 chars rejected
- [x] `GoalServiceTest` — description > 5000 chars rejected
- [x] `GoalServiceTest` — explicit null description clears it
- [x] `GoalServiceTest` — explicit null title ignored (keeps existing)
- [x] `GoalServiceTest` — blank title after trim rejected on update
- [x] `GoalServiceTest` — option position, selection, ownership, reorder rules
- [x] `GoalServiceTest` — confidence history created on goal creation
- [x] `GoalServiceTest` — confidence history created on confidence change
- [x] `GoalServiceTest` — confidence history NOT created when confidence unchanged
- [x] `GoalServiceTest` — achievedAt set and cleared
- [x] `RealityServiceTest` — singular/plural kind normalization
- [x] `RealityServiceTest` — unknown kind rejected
- [x] `RealityServiceTest` — batched grouping with empty lists for goals without items
- [x] `TargetServiceTest` — goal progress as average of target progress
- [x] `TargetServiceTest` — checklist progress from repository items, not lazy collection
- [x] `TargetServiceTest` — numeric: inferred start, reverse direction, clamping
- [x] `TargetServiceTest` — create numeric with current set to start
- [x] `TargetServiceTest` — current rejected on create for numeric
- [x] `TargetServiceTest` — binary rejected as already done on create
- [x] `TargetServiceTest` — checklist blank item text rejected on create
- [x] `TargetServiceTest` — duplicate checklist item IDs rejected on update
- [x] `ResourceServiceTest` — note: required only, required + optional, max body, oversized body, blank title variants
- [x] `ResourceServiceTest` — link: required only, domain-derived title, required + optional, no URL, invalid URL scheme, max URL, oversized URL
- [x] `ResourceServiceTest` — file: image and PDF, all validation paths
- [x] `ResourceServiceTest` — email: type normalisation, generated name, all validation paths, trimming
- [x] `ResourceServiceTest` — contact alias rejected
- [x] `ResourceServiceTest` — delete by id
- [x] `GoalValidationTest` — title and description boundary tests
- [x] `GoalValidationTest` — confidence range 1–10
- [x] `EntityTimestampTest` — onCreate/onUpdate for all entities

### Backend Integration Tests

- [x] Goal creation with required fields only — empty nested collections
- [x] Goal creation with all fields
- [x] Goal title/description trimming on create
- [x] Goal title blank/empty/newline rejected
- [x] Goal title at max length
- [x] Goal title > 200 chars rejected
- [x] Goal description at max length
- [x] Goal description > 5000 chars rejected
- [x] Goal empty input rejected
- [x] Goal missing title rejected
- [x] Goal missing confidence rejected
- [x] Goal invalid deadline format rejected
- [x] Goal update: title, confidence, description, achievedAt, deadline
- [x] Goal update: null description clears it; null title ignored
- [x] Goal update: blank title rejected
- [x] Goal update: field at max length; oversized field rejected
- [x] Goal update: timestamps (createdAt stable, updatedAt advances)
- [x] Goal confidence history recorded on create and update
- [x] Goal delete by id returns true
- [x] Goal delete non-existent id returns NOT_FOUND
- [x] Goal query by id for non-existent id returns error
- [x] Confidence: invalid values rejected (0, 11, negative)
- [x] Confidence: valid values accepted (1, 5, 10)
- [x] Confidence: history returned newest-first after multiple updates
- [x] Confidence: history rows deleted when goal is deleted
- [x] Reality: add action, add obstacle
- [x] Reality: multiple actions/obstacles accumulate
- [x] Reality: actions and obstacles are independent
- [x] Reality: query by goal returns both lists
- [x] Reality: kind mismatch rejected on update
- [x] Reality: cross-goal item rejected on update
- [x] Reality: unknown kind rejected
- [x] Reality: whitespace trimmed on create
- [x] Reality: blank text rejected
- [x] Reality: oversized text rejected; max-length accepted
- [x] Reality: update action/obstacle text
- [x] Reality: update non-existent item returns NOT_FOUND
- [x] Reality: remove action/obstacle
- [x] Reality: remove non-existent item returns NOT_FOUND
- [x] Reality: item has timestamps
- [x] Options: add option with defaults (selected=false, position=0)
- [x] Options: multiple options get consecutive positions
- [x] Options: NOT_FOUND when adding to non-existent goal
- [x] Options: empty list when no options
- [x] Options: selectOption marks chosen, deselects others
- [x] Options: selectOption is idempotent
- [x] Options: selectOption NOT_FOUND for non-existent option
- [x] Options: selectOption ValidationError for cross-goal option
- [x] Options: updateOption deselects active, updates text (active and inactive)
- [x] Options: updateOption NOT_FOUND for non-existent, ValidationError for cross-goal
- [x] Options: removeOption (active and inactive)
- [x] Options: removeOption NOT_FOUND for non-existent
- [x] Options: reorderOptions persists new order in mutation and subsequent query
- [x] Options: reorderOptions empty list for goal with no options
- [x] Options: reorderOptions rejects wrong count
- [x] Options: reorderOptions rejects cross-goal option id
- [x] Options: option has timestamps
- [x] Target: binary create, required + optional fields; done=true rejected
- [x] Target: binary update to done and back; progress recalculates
- [x] Target: numeric create, required + optional, descending range
- [x] Target: numeric update current; ascending and descending range
- [x] Target: numeric invalid deadline rejected
- [x] Target: numeric start/total/current validation on update (negative, null, range)
- [x] Target: checklist create, required + optional
- [x] Target: checklist update items (mark done, all done); CRUD on tasks
- [x] Target: checklist: empty items, blank text, duplicate ids, non-existent id, cross-target id rejected
- [x] Target: checklist items only accepted on checklist type
- [x] Target: deadline set/change/clear for all types and checklist task deadline
- [x] Target: targetsByGoal returns list; empty list; NOT_FOUND for non-existent goal
- [x] Target: targetById returns target; NOT_FOUND for non-existent
- [x] Target: delete all three types; NOT_FOUND for non-existent
- [x] Target: unknown type rejected
- [x] Target: NOT_FOUND for non-existent goal on create
- [x] Target: goal progress increases when binary toggled to done
- [x] Target: goal progress is average of all targets
- [x] Resources: note, link, file, email CRUD (integration)
- [x] Resources: unknown type "contact" rejected
- [x] Goal progress: all three target types combined

### @Size Annotation Coverage

- [x] `Goal.description` — `@Size(max=5000)`
- [x] `RealityItem.text` — `@Size(max=5000)` (verified existing)
- [x] `Option.text` — `@Size(max=500)`
- [x] `ChecklistItem.text` — `@Size(max=500)`
- [x] `Resource.body` — `@Size(max=50000)`
- [x] `Resource.dataUrl` — `@Size(max=50000)`

---

## Section 5: New Work — Defect Fixes

- [ ] `Thread.sleep()` eliminated from `EntityTimestampTest`
- [ ] `Thread.sleep()` eliminated from `GoalCreationIntegrationTest`
- [ ] `Thread.sleep()` eliminated from `GoalConfidenceIntegrationTest`
- [ ] `GoalWorkspaceIntegrationTest.createTarget()` rewritten without string injection
- [ ] `GoalConfidenceIntegrationTest` cleanup moved to `@AfterEach`
- [ ] Magic ID `"999999"` replaced across all integration test files
- [ ] `@DisplayName` added to all methods in `RealityServiceTest`
- [ ] `@DisplayName` added to all methods in `EntityTimestampTest`
- [ ] `assertNoGoalsCreated()` scoped to specific title, not total count

---

## Section 6: New Unit Tests

### GoalService — query/delete/option methods

- [ ] `findAll()` returns all goals (empty and non-empty)
- [ ] `findById()` returns goal when found
- [ ] `findById()` throws when not found
- [ ] `delete()` calls repository
- [ ] `delete()` throws NOT_FOUND when missing
- [ ] `removeOption()` removes option
- [ ] `removeOption()` throws NOT_FOUND when missing
- [ ] `updateOption()` updates text only
- [ ] `updateOption()` updates selected only
- [ ] `updateOption()` rejects cross-goal option
- [ ] `addOption()` with no existing options assigns position 0
- [ ] `findOptionsByGoalIds()` groups correctly for multiple goals
- [ ] `findConfidenceHistoryByGoalIds()` returns history sorted newest-first

### RealityService — mutation methods

- [ ] `addItem()` saves and returns updated payload
- [ ] `addItem()` normalizes kind before saving
- [ ] `addItem()` rejects blank text
- [ ] `updateItem()` updates text and returns updated payload
- [ ] `updateItem()` rejects cross-goal item
- [ ] `updateItem()` rejects kind mismatch
- [ ] `removeItem()` deletes and returns updated payload
- [ ] `removeItem()` rejects cross-goal item
- [ ] `findByGoal()` returns payload for single goal

### TargetService — update and query paths

- [ ] `findByGoal()` verifies goal exists and returns targets
- [ ] `findById()` returns target; throws on missing
- [ ] `delete()` calls repository; throws on missing
- [ ] `update()` numeric range validation ascending
- [ ] `update()` numeric range validation descending
- [ ] `update()` binary toggle
- [ ] `update()` checklist merge: add, edit, delete

### Entity Validation Tests

- [ ] `Option.text` not blank
- [ ] `Option.text` max 500 chars; 501 rejected
- [ ] `ChecklistItem.text` not blank
- [ ] `ChecklistItem.text` max 500 chars; 501 rejected
- [ ] `RealityItem.text` max 5000 chars; 5001 rejected
- [ ] `Resource.body` max 50000 chars; 50001 rejected
- [ ] `Resource.dataUrl` max 50000 chars; 50001 rejected

---

## Section 7: New Integration Tests

### Goals list query

- [ ] `goals` returns empty list
- [ ] `goals` returns multiple goals with correct fields
- [ ] `goals` BatchMapping resolves nested fields for each goal

### Cascade deletes

- [ ] `deleteGoal` removes targets
- [ ] `deleteGoal` removes resources
- [ ] `deleteGoal` removes options
- [ ] `deleteGoal` removes reality items
- [ ] `deleteGoal` removes checklist items
- [ ] `deleteGoal` removes confidence history (covered by existing test — verify)
- [ ] `deleteTarget` (checklist) removes checklist items

### Cross-goal isolation

- [ ] Options of goal A not visible via goal B
- [ ] Resources of goal A not visible via goal B
- [ ] Reality of goal A not visible via goal B
- [ ] Targets of goal A not visible via goal B

### Missing NOT_FOUND paths

- [ ] `updateGoal` on non-existent id → NOT_FOUND
- [ ] `createResource` for non-existent goal → NOT_FOUND
- [ ] `realityByGoal` for non-existent goal → error with classification
- [ ] `resourcesByGoal` for non-existent goal → NOT_FOUND

### Progress recalculation

- [ ] Goal progress decreases when target is deleted
- [ ] Goal progress is 0 when last target is deleted

### Test refactoring

- [ ] `GoalWorkspaceIntegrationTest` split into `GoalProgressIntegrationTest`
- [ ] Resource rejection moved to `ResourceIntegrationTest`

---

## Section 8: Python E2E Suite

### Setup

- [ ] `tests-e2e/` directory exists in repository root
- [ ] `tests-e2e/requirements.txt` specifies `pytest`, `gql[httpx]`, `httpx`, `pytest-asyncio`
- [ ] `tests-e2e/conftest.py` provides `base_url`, `graphql_client`, `created_goal` fixtures
- [ ] `tests-e2e/README.md` documents prerequisites, how to run, how to target staging

### Test files

- [ ] `test_health.py` — health endpoint returns 200
- [ ] `test_goals_e2e.py` — goal CRUD lifecycle; blank title rejection; NOT_FOUND
- [ ] `test_reality_e2e.py` — reality item CRUD; unknown kind rejection
- [ ] `test_options_e2e.py` — option CRUD; select deselects others; reorder
- [ ] `test_targets_e2e.py` — binary/numeric/checklist CRUD; progress after update
- [ ] `test_resources_e2e.py` — note/link/file/email CRUD; unknown type rejection
- [ ] `test_progress_e2e.py` — goal progress = average of target progress values
- [ ] `test_cors.py` — CORS headers present on POST /graphql
- [ ] `test_error_envelope.py` — all error types return `extensions.classification`

### CI

- [ ] Python E2E job added to `.github/workflows/ci.yml`
- [ ] Job starts backend with test profile before running pytest
- [ ] `pytest tests-e2e/ -v` passes in CI

---

## Section 9: Documentation

- [ ] `docs/testing-guide.md` updated with Python E2E setup and run instructions
- [ ] `docs/unit-vs-integration-tests.md` updated to describe E2E layer and when to use it

---

## Known Lint Status (Unchanged)

- [ ] `npm.cmd run lint`

Lint continues to fail on pre-existing Prettier/CRLF issues in `src/components/spira/NewGoalSheet.tsx` and pre-existing warnings in several component files. These failures are not related to test coverage work.

---

## Manual Review Checklist

- [ ] Review this spec for accuracy before using it to assign work
- [ ] Review `docs/testing-guide.md` once Python E2E docs are added
- [ ] Review Python E2E `conftest.py` for correct cleanup (no test data leaks)
- [ ] Confirm `tests-e2e/` tests skip gracefully when backend is not running

---

## Definition Of Done

- [x] Unit tests exist for frontend progress logic
- [x] Backend unit tests exist for core domain services (partial — gaps documented in Section 6)
- [x] Backend Spring integration tests exist for GraphQL persistence flows
- [x] Test documentation exists under `docs/`
- [x] Obsolete placeholder test files removed
- [x] Task spec exists under `specs/`
- [ ] All defects in Section 3 fixed
- [ ] All new unit tests in Section 6 written and passing
- [ ] All new integration tests in Section 7 written and passing
- [ ] Python E2E suite in Section 8 created and passing in CI
- [ ] Documentation in Section 9 updated
