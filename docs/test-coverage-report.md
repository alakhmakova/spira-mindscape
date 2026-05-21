# Test Coverage Report - Spira Mindscape

**Date**: 2026-05-21  
**Status**: Updated after expanding unit/integration coverage and splitting broad tests into focused cases

## Summary of this update

- Fixed the backend confidence integration suite so it now runs with `@AutoConfigureGraphQlTester`.
- Stabilized flaky timestamp assertions that were comparing `Instant.now()` values too strictly.
- Added focused frontend API contract tests for:
  - goal name, description, confidence, deadline, created date, achieved date
  - actions and obstacles
  - options
  - all resource types (`note`, `link`, `file`, `email`)
  - all target types (`numeric`, `binary`, `checklist`)
  - checklist task deadlines and achieved dates
  - resource/target input serialization, including local checklist task ids
- Split bundled tests in frontend store coverage and backend goal integration coverage into smaller single-behavior cases.
- Added backend confidence-history integration coverage for descending ordering and delete cascade behavior.

## Current automated test inventory

### Frontend (TypeScript / Vitest)

- **Files**: 4
- **Tests**: 30
- **Scope**: unit tests

Files:

- `src/lib/spira/api.test.ts`
- `src/lib/spira/api.contract.test.ts`
- `src/lib/spira/progress.test.ts`
- `src/lib/spira/store.test.ts`

### Backend (Java / JUnit + Spring GraphQL)

- **Files**: 13
- **Tests**: 386
- **Scope**: unit tests + GraphQL integration tests

Unit-style files:

- `backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/EntityTimestampTest.java`
- `backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java`

Integration files:

- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalConfidenceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalWorkspaceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/RealityIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/OptionIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java`

## Coverage by application element

| Area | Current coverage | Notes |
|---|---|---|
| Goal name | Strong | Backend create/update/validation coverage plus frontend goal mapping coverage. |
| Goal description | Strong | Backend create/update/null-clearing coverage plus frontend mapping coverage. |
| Goal deadline | Strong | Backend create/update/clear/invalid-format coverage plus frontend null-serialization coverage. |
| Goal created date | Good | Backend lifecycle/integration coverage now uses stable timestamp assertions; frontend mapping covers returned value. |
| Goal achieved date | Good | Backend set/clear coverage plus frontend mapping and store achievement propagation coverage. |
| Goal progress | Good | Backend target/workspace progress coverage plus frontend pure progress tests. |
| Goal confidence | Strong | Dedicated backend confidence integration coverage plus frontend goal mapping coverage. |
| Goal confidence history | Good | Backend now covers create/update recording, descending order, and delete cascade. |
| Actions | Strong | Backend service + integration coverage; frontend mapping covers returned action collections. |
| Obstacles | Strong | Backend service + integration coverage; frontend mapping covers returned obstacle collections. |
| Note resources | Strong | Backend unit/integration coverage plus frontend mapping/input serialization coverage. |
| Link resources | Strong | Backend unit/integration coverage plus frontend mapping/input serialization coverage. |
| File resources | Strong | Backend unit/integration coverage plus frontend mapping/input serialization coverage. |
| Email resources | Strong | Backend unit/integration coverage plus frontend mapping/input serialization coverage. |
| Numeric targets | Strong | Backend unit/integration coverage plus frontend progress and API serialization coverage. |
| Binary targets | Strong | Backend unit/integration coverage plus frontend mapping and store achieved-date coverage. |
| Checklist targets | Strong | Backend unit/integration coverage plus frontend mapping, serialization, and store achieved-date coverage. |
| Checklist tasks | Strong | Backend integration coverage plus frontend task date mapping and undo/achievement coverage. |

## Important changes from the previous report

### 1. The previously reported backend failures are no longer current

The old report mentioned:

- `GoalConfidenceIntegrationTest` failing because of `@AutoConfigureHttpGraphQlTester`
- timestamp tests failing because of strict equality

Those issues are now fixed. The full backend suite passes.

### 2. Broad tests were reduced in the most error-prone areas

Examples of bundled behavior that were split:

- goal update integration coverage for title, confidence, achieved date, and deadline clearing
- frontend store coverage for binary target undo and checklist task undo flows

This makes failures more local and easier to diagnose.

### 3. Frontend API coverage is materially better

Before this update, frontend tests mostly covered error handling and progress math.  
Now the frontend tests also verify that the app correctly:

- maps nested GraphQL goal payloads into local models
- preserves goal metadata fields
- handles all resource and target variants
- serializes resource and target inputs the way the backend contract expects

## Remaining gaps

The codebase is in a much better state, but there are still a few honest gaps:

1. **No frontend integration/E2E suite yet**
   - Frontend coverage is still unit-focused.
   - UI wiring through React components is not covered end-to-end.

2. **No explicit coverage metric tooling in the repo**
   - This report is based on test inventory and branch/contract analysis, not a generated coverage percentage report.

3. **Confidence history is backend-only today**
   - Backend behavior is covered well.
   - The frontend domain model does not currently expose confidence history, so there is no frontend test surface for it yet.

## Validation run for this update

### Frontend

```text
npm test
```

Result:

- **4 files**
- **30 tests**
- **all passing**

### Backend

```text
cd backend
sh ./mvnw test
```

Result:

- **13 files**
- **386 tests**
- **all passing**

### Build

```text
npm run build
```

Result:

- **passing**

### Lint

```text
npm run lint
```

Current status:

- still fails on **pre-existing** formatting issues in:
  - `src/components/spira/DeadlinePopover.tsx`
  - `src/components/spira/GoalCard.tsx`

These lint failures were already outside the scope of the test-coverage work.

## Overall assessment

### Backend

- **Coverage depth**: high
- **Contract coverage**: high
- **Validation/boundary coverage**: high
- **Confidence-history coverage**: now solid

### Frontend

- **Coverage depth**: improved from limited to good for domain/API logic
- **Best-covered areas**: API contract mapping, error handling, progress math, achieved-date propagation
- **Main remaining gap**: component-level integration

## Conclusion

Test coverage is now **substantially more trustworthy** than the previous report suggested:

- the backend suite is green end-to-end
- the fragile timestamp/confidence test issues are fixed
- frontend unit coverage now exercises the real domain contract for goals, reality, resources, targets, and tasks
- several bundled tests were split into focused assertions for better maintainability
