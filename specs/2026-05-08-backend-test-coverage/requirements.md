# Requirements: Backend Test Coverage — QA Expansion

**Author**: QA Lead (senior automation)
**Last Updated**: 2026-05-26
**Status**: Active requirements — supersedes previous version

---

## Scope

Expand and stabilise the automated test suite for the Spira backend after completing the initial coverage sprint. This document covers:

1. Remediation of defects found in existing tests
2. Missing unit test coverage for service query/delete paths and entity validation
3. Missing integration test scenarios (cascade deletes, list queries, cross-goal isolation)
4. A new Python E2E test layer that validates the API over HTTP

Auth, AI orchestration, and GROW session coverage remain excluded (not yet implemented in the product).

---

## Included

### Fixes to Existing Tests

- Remove all `Thread.sleep()` calls; replace with deterministic time control or ordering-only assertions
- Replace `.replace("INPUT_PLACEHOLDER", input)` string injection with properly bound GraphQL variable helpers
- Move database cleanup to `@AfterEach` in `GoalConfidenceIntegrationTest` (currently in `@BeforeEach`)
- Replace `"999999"` magic IDs with `String.valueOf(Long.MAX_VALUE)` across all integration tests
- Add `@DisplayName` to every test method in `RealityServiceTest` and `EntityTimestampTest`
- Fix `assertNoGoalsCreated()` to assert on a specific title, not on total count

### New Unit Tests (Java)

**GoalService:**
- `findAll()` returns all goals
- `findById()` returns goal when found; throws on missing
- `delete()` calls repository; throws NOT_FOUND when missing
- `removeOption()` deletes option; throws NOT_FOUND when missing
- `updateOption()` updates text only, selected only, both fields, and rejects cross-goal option
- `addOption()` when goal has no options (position must be 0)
- `findOptionsByGoalIds()` groups correctly for multiple goals
- `findConfidenceHistoryByGoalIds()` returns history sorted newest-first per goal

**RealityService:**
- `addItem()` saves item and returns updated payload with correct kind list
- `updateItem()` validates goal ownership, validates kind match, updates text
- `removeItem()` validates ownership, deletes item, returns updated payload
- `findByGoal()` delegates to batch method for single goal
- Blank/empty text rejected at service level for `addItem()` and `updateItem()`

**TargetService:**
- `findByGoal()` verifies goal exists; returns targets
- `findById()` returns target when found; throws on missing
- `delete()` calls repository; throws NOT_FOUND when missing
- `update()` — numeric range validation (ascending and descending)
- `update()` — binary toggle from done to not done
- `update()` — checklist merge: add new, edit existing, delete omitted items
- Progress returns 0 for goal with no targets (covered by existing test but should be explicit)

**Entity Validation (Bean Validation — same pattern as `GoalValidationTest`):**
- `Option.text`: not blank, max 500 chars
- `ChecklistItem.text`: not blank, max 500 chars
- `RealityItem.text`: not blank, max 5000 chars (verify current `@Size`)
- `Resource.body`: max 50000 chars
- `Resource.dataUrl`: max 50000 chars
- `Target.title`: not blank, max 200 chars (if annotated)

### New Integration Tests (Java/Spring)

**Goal list query:**
- `goals` returns empty list when no goals exist
- `goals` returns all goals with correct fields
- `goals` BatchMapping resolves reality, options, resources, targets, confidenceHistory for each goal

**Cascade deletes:**
- `deleteGoal` → no orphan rows in targets, resources, options, reality_items, checklist_items, confidence_history
- `deleteTarget` (checklist type) → checklist_item rows are deleted

**Cross-goal isolation:**
- Options of goal A are not visible in `optionsByGoal(goalB)`
- Resources of goal A are not visible in `resourcesByGoal(goalB)`
- Reality items of goal A are not visible in `realityByGoal(goalB)`
- Targets of goal A are not visible in `targetsByGoal(goalB)`

**Missing NOT_FOUND error paths:**
- `updateGoal` on non-existent id
- `createResource` for non-existent goal
- `realityByGoal` for non-existent goal
- `resourcesByGoal` for non-existent goal

**Goal progress after target deletion:**
- Create 2 targets, verify combined progress, delete one, verify progress updates

**Split `GoalWorkspaceIntegrationTest`:**
- Move progress tests to `GoalProgressIntegrationTest`
- Move resource rejection to `ResourceIntegrationTest`

### New Python E2E Test Suite

Location: `tests-e2e/` in repository root.

**Required stack:**
- `pytest` ≥ 8.0
- `gql[httpx]` for GraphQL client
- `httpx` for raw HTTP assertions
- `pytest-asyncio` for async tests

**Required test files and coverage:**

| File | Must test |
|---|---|
| `test_health.py` | `GET /health` returns 200 with status=UP |
| `test_goals_e2e.py` | Full goal lifecycle; blank title rejection; NOT_FOUND on missing id |
| `test_reality_e2e.py` | Add action, obstacle; update; remove; unknown kind rejection |
| `test_options_e2e.py` | Add, select (deselects others), update text, reorder, remove |
| `test_targets_e2e.py` | Binary/numeric/checklist lifecycle; progress after update |
| `test_resources_e2e.py` | note/link/file/email lifecycle; unknown type rejection |
| `test_progress_e2e.py` | Goal progress = average of all target progress values |
| `test_cors.py` | `POST /graphql` CORS headers from configured origin |
| `test_error_envelope.py` | Every error type returns `extensions.classification` |

**Required fixtures (`conftest.py`):**
- `base_url` — reads from env var `SPIRA_BASE_URL`, defaults to `http://localhost:8080`
- `graphql_client` — creates `gql.Client` for session
- `created_goal` — creates a goal and deletes it after the test (autouse-optional)

**Python CI requirements:**
- `pip install -r tests-e2e/requirements.txt`
- `pytest tests-e2e/ -v` passes against a locally running backend
- Tests skip gracefully when backend is not running (marker `@pytest.mark.e2e` with conditional skip on unreachable host)

### Documentation Updates

- Update `docs/testing-guide.md` to include Python E2E setup instructions
- Update `docs/unit-vs-integration-tests.md` to include Python E2E layer description
- Add `tests-e2e/README.md` with: prerequisites, how to start backend, how to run tests, how to point at staging

---

## Excluded (Remains Out of Scope)

- Authentication and authorization tests — auth not yet implemented
- AI orchestration tests — AI persistence not yet implemented
- GROW session tests — GROW not yet implemented
- Playwright browser E2E — frontend is not covered by this task
- PostgreSQL/TestContainers migration tests — tracked separately as Phase F
- Performance/load tests — tracked separately; `locust` file is scaffolded but not required to pass in CI

---

## Product Contracts To Protect (Unchanged)

All contracts from the previous requirements version remain active:

- Goals are structured workspaces containing reality, options, resources, and targets
- Progress is calculated only from targets; all targets contribute equally
- Numeric progress: inferred starts, reverse direction, clamping to [0, 1]
- Binary progress: 1 when done, 0 when not done
- Checklist progress: completed items / total items
- A goal with no targets has progress 0
- Resource type is `email`, not `contact`
- Resource labels capped at 20 characters; auto-generated for links and emails
- Options keep deterministic positions; one selected option per goal at most
- Reality items accept singular or plural kind; stored as canonical plural
- The backend is a single-user API until auth work begins

### Additional Contracts Now Explicitly Required

- `deleteGoal` removes all child entities — no orphans in any child table
- `goals` list returns results in consistent order (by `created_at ASC` or documented order)
- Every GraphQL error response includes `extensions.classification`
- Cross-goal data isolation: data written to goal A is never readable via goal B queries

---

## Success Criteria

- `cd backend && .\mvnw.cmd test` passes with zero failures and zero `Thread.sleep()` in test code
- All new Java unit tests listed in this document exist and pass
- All new Java integration tests listed in this document exist and pass
- `tests-e2e/` directory exists with the files listed above
- `pytest tests-e2e/ -v` passes against a locally running backend instance
- CI runs Python E2E suite as a separate job after the backend integration job
- `docs/testing-guide.md` documents how to run both Java and Python test suites
- No test uses `.replace()` for GraphQL document string construction
- No test uses `"999999"` as a magic non-existent ID
