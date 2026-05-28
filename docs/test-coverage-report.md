# Test Coverage Report — Spira Mindscape

**Date**: 2026-05-28
**Status**: Major expansion — E2E layer added, backend unit/integration coverage significantly extended

---

## Summary

| Layer | Files | Tests | Technology |
|---|---|---|---|
| Frontend unit | 4 | 30 | TypeScript / Vitest |
| Backend unit + integration | 15 | 568 | Java / JUnit 5 + Spring GraphQL |
| E2E | 8 | 110 | Python / pytest + httpx |
| **Total** | **27** | **708** | |

---

## What changed since the previous report

Previous report (2026-05-21): 386 backend tests, 30 frontend tests, no E2E layer.

### New: Python E2E layer (110 tests)

A full E2E test suite was built from scratch against a running backend. Tests hit the GraphQL API over HTTP and cover every domain area end-to-end: goals, reality, options, targets, resources, progress, and error envelope shape.

### Backend grew 386 → 568 tests (+182)

Systematic gap analysis was performed for each domain. For every area, a matrix of scenarios × test levels was built; gaps were closed at the appropriate level.

### Production bugs found and fixed during analysis

| Bug | Location | Symptom |
|---|---|---|
| Option text had no max-length service validation | `GoalService.addOption` / `updateOption` | DB-level `DataIntegrityViolationException` instead of `ValidationError` |
| `achievedAt` field not covered by date-format error handler | `GraphQlExceptionHandler.isInvalidDateField` | `INTERNAL_ERROR` instead of `ValidationError` for bad date strings |
| `GoalCascadeDeleteIntegrationTest` used plural table names | Test SQL queries | `BadSqlGrammar` on all cascade-delete assertions |

---

## Backend test inventory

### Unit-style files

| File | Tests | What it covers |
|---|---|---|
| `goal/GoalServiceTest.java` | 51 | Goal CRUD, option CRUD + validation, text normalisation, confidence history |
| `goal/GoalValidationTest.java` | 16 | Goal field validation rules in isolation |
| `goal/RealityServiceTest.java` | 26 | Reality item CRUD, text validation, max-length, goal isolation |
| `goal/EntityTimestampTest.java` | 6 | `createdAt` / `updatedAt` lifecycle |
| `resource/ResourceServiceTest.java` | 45 | Resource CRUD, all 4 types, field validation, goal isolation |
| `target/TargetServiceTest.java` | 55 | Target CRUD, all 3 types, progress calculation, all validation paths |

### Integration files

| File | Tests | What it covers |
|---|---|---|
| `graphql/GoalCreationIntegrationTest.java` | 47 | Goal lifecycle via GraphQL: create, update, delete, all field types |
| `graphql/GoalConfidenceIntegrationTest.java` | 13 | Confidence history recording, ordering, delete cascade |
| `graphql/GoalCascadeDeleteIntegrationTest.java` | 7 | DB-level cascade: options, reality, targets, checklist items, resources, confidence history |
| `graphql/GoalListIntegrationTest.java` | 9 | `goals` query ordering, empty state, multi-goal listing |
| `graphql/GoalIsolationIntegrationTest.java` | 7 | Data isolation: goals don't bleed into each other |
| `graphql/RealityIntegrationTest.java` | 39 | Reality items via GraphQL: add, update, remove, validation, isolation |
| `graphql/OptionIntegrationTest.java` | 37 | Options via GraphQL: add, update, select, reorder, remove, validation |
| `graphql/TargetIntegrationTest.java` | 84 | Targets via GraphQL: all 3 types, all CRUD, progress, deadline, validation |
| `graphql/ResourceIntegrationTest.java` | 126 | Resources via GraphQL: all 4 types, all CRUD, field validation |

---

## E2E test inventory

All files in `tests-e2e/`:

| File | Tests | What it covers |
|---|---|---|
| `test_health.py` | 2 | Health endpoint, GraphQL endpoint reachability |
| `test_error_envelope.py` | 5 | Error shape (classification, message) for NOT_FOUND and ValidationError |
| `test_goals_e2e.py` | 18 | Goal CRUD, deadlines, achievedAt, all field types, confidence |
| `test_reality_e2e.py` | 15 | Reality item add/update/remove, text validation, max-length, whitespace |
| `test_options_e2e.py` | 22 | Option add/select/update/remove/reorder, text validation, goal isolation |
| `test_targets_e2e.py` | 27 | All 3 target types, progress, deadlines, numeric start/total updates |
| `test_resources_e2e.py` | 9 | Resource CRUD, all 4 types, field validation |
| `test_progress_e2e.py` | 12 | Cross-target progress: mixed types, partial, 100%, reversals |

---

## Coverage by domain

### Goals

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Create with required fields | ✅ | ✅ | ✅ |
| Create with all optional fields (description, deadline) | ✅ | ✅ | ✅ |
| Blank / missing title | ✅ | ✅ | ✅ |
| Title over 200 chars | ✅ | ✅ | — |
| Description over 5000 chars | ✅ | ✅ | — |
| Missing confidence | ✅ | ✅ | — |
| Confidence out of range (0, 11) | ✅ | ✅ | — |
| Update title, description, confidence | ✅ | ✅ | ✅ |
| Update with explicit null (clear deadline, description) | ✅ | ✅ | — |
| achievedAt set / cleared | ✅ | ✅ | ✅ |
| achievedAt invalid format → ValidationError | ✅ | ✅ | — |
| Deadline set / changed / cleared | ✅ | ✅ | ✅ |
| Deadline invalid format | ✅ | ✅ | — |
| Delete goal | ✅ | ✅ | ✅ |
| Not-found errors (get, update, delete) | ✅ | ✅ | ✅ |
| Ordering: goals in `createdAt` asc order | — | ✅ | ✅ |
| Data isolation between goals | — | ✅ | — |
| Cascade delete (options, reality, targets, resources, history) | — | ✅ (DB-level) | — |

### Confidence history

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Entry recorded on create | — | ✅ | — |
| Entry recorded on confidence change | — | ✅ | — |
| No entry when confidence unchanged | — | ✅ | — |
| History returned in descending `at` order | — | ✅ | — |
| History deleted with goal | — | ✅ (DB-level) | — |

### Reality (actions & obstacles)

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Add action / obstacle | ✅ | ✅ | ✅ |
| Update text | ✅ | ✅ | ✅ |
| Remove | ✅ | ✅ | ✅ |
| Blank text rejected | ✅ | ✅ | ✅ |
| Text over 500 chars rejected | ✅ | ✅ | ✅ |
| Whitespace trimmed | ✅ | ✅ | ✅ |
| Goal not found | ✅ | ✅ | ✅ |
| Item not found | ✅ | ✅ | — |
| Item from another goal rejected | ✅ | ✅ | — |

### Options

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Add option | ✅ | ✅ | ✅ |
| Positions assigned consecutively | ✅ | ✅ | ✅ |
| Select option (deselects others) | ✅ | ✅ | ✅ |
| Update text | ✅ | ✅ | ✅ |
| Remove option | ✅ | ✅ | ✅ |
| Reorder options | ✅ | ✅ | ✅ |
| Blank text rejected on add / update | ✅ | ✅ | ✅ |
| Text over 500 chars rejected | ✅ | ✅ | ✅ |
| Whitespace trimmed | ✅ | ✅ | ✅ |
| Goal not found (add, update, remove, select, reorder) | ✅ | ✅ | ✅ |
| Option from another goal rejected (select, remove) | ✅ | ✅ | ✅ |
| Remove preserves remaining options | ✅ | ✅ | ✅ |
| Reorder wrong count rejected | ✅ | ✅ | — |

### Targets — binary

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Create (`done=false`, `type=binary` saved) | ✅ | ✅ | ✅ |
| Create with optional fields (deadline) | — | ✅ | — |
| `done=true` on create rejected | ✅ | ✅ | ✅ |
| Items on binary type rejected | ✅ | ✅ | — |
| Update `done=true` persisted | ✅ | ✅ | ✅ |
| Update `done=false` persisted | ✅ | ✅ | — |
| Deadline set / changed / cleared | — | ✅ | ✅ |
| Invalid deadline format | — | ✅ | ✅ |
| Blank title rejected | — | ✅ | ✅ |
| Goal not found (create) | — | ✅ | ✅ |
| Target not found (update, delete) | ✅ | ✅ | — |
| Delete binary target | ✅ | ✅ | ✅ |
| Progress = 0 when not done | ✅ | ✅ | ✅ |
| Progress = 1 when done | ✅ | ✅ | ✅ |

### Targets — numeric

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| **Create** | | | |
| Required fields (start, total); current initialised to start | ✅ | ✅ | ✅ |
| Optional fields (unit, deadline) | — | ✅ | ✅ |
| Descending range (start > total) | ✅ | ✅ | — |
| `current` provided on create rejected | ✅ | ✅ | — |
| Missing start / total | ✅ | ✅ | — |
| Explicit `null` for start / total / current | ✅ | ✅ | — |
| Negative start / total | ✅ | ✅ | — |
| Equal start = total | ✅ | ✅ | — |
| **Update** | | | |
| Update current → progress recalculates | — | ✅ | ✅ |
| Current inside ascending range (both directions) | — | ✅ | — |
| Current inside descending range | — | ✅ | — |
| Current outside ascending range | ✅ | ✅ | — |
| Current outside descending range | ✅ | ✅ | — |
| Update start → progress recalculates | ✅ | ✅ | ✅ |
| Update total → progress recalculates | ✅ | ✅ | ✅ |
| Negative current / start / total | ✅ | ✅ | ✅ (start, total) |
| Explicit `null` for current / start / total | ✅ | ✅ | — |
| start = total after update | ✅ | ✅ | ✅ |
| **Progress** | | | |
| Progress = 0 at start | ✅ | ✅ | ✅ |
| Progress = 1 at total | ✅ | ✅ | ✅ |
| Progress = (current−start)/(total−start) ascending | ✅ | ✅ | ✅ |
| Progress = (start−current)/(start−total) descending | ✅ | ✅ | — |

### Targets — checklist

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| **Create** | | | |
| Items saved with correct text and `done` state | ✅ | ✅ | ✅ |
| Optional fields (deadlines per item, target deadline) | — | ✅ | ✅ |
| Null / empty items list rejected | ✅ | ✅ | ✅ |
| Blank item text rejected | ✅ | ✅ | — |
| Items on binary / numeric type rejected | ✅ | ✅ | — |
| **Update** | | | |
| Add new items (no id) | — | ✅ | — |
| Edit existing items (by id) | — | ✅ | ✅ |
| Delete items (omit from list) | — | ✅ | — |
| Empty items list rejected; original preserved | ✅ | ✅ | ✅ |
| Blank item text rejected; original preserved | ✅ | ✅ | — |
| Duplicate item ids rejected | ✅ | ✅ | — |
| Unknown item id rejected (NOT_FOUND) | ✅ | ✅ | — |
| Item id from another target rejected | — | ✅ | — |
| Items on non-checklist target rejected | ✅ | ✅ | — |
| Item deadline set / changed / cleared | — | ✅ | ✅ |
| Invalid item deadline format | — | ✅ | ✅ |
| **Progress** | | | |
| Progress = 0 when no items done | ✅ | ✅ | ✅ |
| Progress = 1 when all items done | ✅ | ✅ | ✅ |
| Progress = done/total (partial) | ✅ | ✅ | ✅ |
| Progress read from repository (not in-memory) | ✅ | — | — |

### Resources

| Scenario | Unit | Integration | E2E |
|---|---|---|---|
| Create note / link / file / email | ✅ | ✅ | ✅ |
| Read by id / by goal | ✅ | ✅ | ✅ |
| Update fields | ✅ | ✅ | ✅ |
| Delete | ✅ | ✅ | ✅ |
| Field validation (title, url, body) | ✅ | ✅ | ✅ |
| Goal not found | ✅ | ✅ | — |
| Resource not found | ✅ | ✅ | — |

---

## Remaining gaps

| Gap | Level | Notes |
|---|---|---|
| No frontend integration/E2E suite | Frontend | UI wiring through React components not covered end-to-end |
| No coverage metric tooling in CI | Infra | Report is inventory-based, not line-coverage percentage |
| Confidence history not exposed in frontend | Frontend | No frontend test surface; backend is fully covered |
| E2E suite not running in CI yet | Infra | Needs a running backend; currently run manually |

---

## Validation run

### Backend

```
cd backend && .\mvnw.cmd test
```

| Class | Tests |
|---|---|
| EntityTimestampTest | 6 |
| GoalServiceTest | 51 |
| GoalValidationTest | 16 |
| RealityServiceTest | 26 |
| ResourceServiceTest | 45 |
| TargetServiceTest | 55 |
| GoalCascadeDeleteIntegrationTest | 7 |
| GoalConfidenceIntegrationTest | 13 |
| GoalCreationIntegrationTest | 47 |
| GoalIsolationIntegrationTest | 7 |
| GoalListIntegrationTest | 9 |
| OptionIntegrationTest | 37 |
| RealityIntegrationTest | 39 |
| ResourceIntegrationTest | 126 |
| TargetIntegrationTest | 84 |
| **Total** | **568** |

Result: **568 tests, all passing**

### E2E

```
cd tests-e2e && pytest
```

| File | Tests |
|---|---|
| test_health.py | 2 |
| test_error_envelope.py | 5 |
| test_goals_e2e.py | 18 |
| test_reality_e2e.py | 15 |
| test_options_e2e.py | 22 |
| test_targets_e2e.py | 27 |
| test_resources_e2e.py | 9 |
| test_progress_e2e.py | 12 |
| **Total** | **110** |

Result: **110 tests, all passing** (against local running backend)

### Frontend

```
npm test
```

Result: **30 tests, all passing**
