# Validation: Backend And Progress Test Coverage

**Last Updated**: 2026-05-21

## Automated Checks

- [x] `npm.cmd test`
- [x] `npm.cmd run build`
- [x] `cd backend && .\mvnw.cmd test`
- [x] `npm test` (Linux shell)
- [x] `npm run build` (Linux shell)
- [x] `cd backend && sh ./mvnw test` (Linux shell)

## CI / Reporting Checks

- [x] `.github/workflows/ci.yml` exists.
- [x] CI is configured for `push`, `pull_request`, `workflow_dispatch`, and `schedule`.
- [x] CI runs frontend tests and frontend build.
- [x] CI runs backend Maven tests.
- [x] Backend tests are configured to write Allure results to `backend/target/allure-results`.
- [x] Workflow uses `simple-elf/allure-report-action@v1`.
- [x] Workflow uploads backend surefire artifacts.
- [x] Workflow uploads generated Allure HTML artifact.
- [x] CI implementation is documented in `docs/github-actions-ci.md`.

## Test Coverage Checks

- [x] Frontend progress unit tests cover numeric targets.
- [x] Frontend progress unit tests cover Done / Not Done targets.
- [x] Frontend progress unit tests cover checklist targets.
- [x] Frontend progress unit tests cover goal progress averaging.
- [x] Backend unit tests cover goal progress averaging.
- [x] Backend unit tests cover checklist progress without relying on lazy target collections.
- [x] Backend unit tests cover numeric inferred start, reverse direction, and clamping.
- [x] Backend unit tests cover target create/update validation rules for numeric, binary, and checklist targets.
- [x] Backend unit tests cover Email resource type handling.
- [x] Backend unit tests reject the old `contact` alias.
- [x] Backend unit tests cover goal option position, selection, ownership, and reorder rules.
- [x] Backend unit tests cover reality kind normalization and grouping.
- [x] Spring integration tests create goals through GraphQL.
- [x] Spring integration tests persist and query reality items, options, resources, and targets.
- [x] Spring integration tests verify GraphQL progress for numeric, binary, and checklist targets.
- [x] Spring integration tests verify missing-goal errors.
- [x] Spring integration tests verify invalid resource type errors.
- [x] Spring integration tests cover resource required/optional field combinations, label boundaries, derived labels, file validation, and disallowed type fields.
- [x] All text fields have explicit @Size(max=N) annotations:
  - Goal.description: 5000 chars
  - RealityItem.text: 5000 chars
  - Option.text: 500 chars
  - ChecklistItem.text: 500 chars
  - Resource.body: 50000 chars
  - Resource.dataUrl: 50000 chars

## Known Current Lint Status

- [ ] `npm.cmd run lint`

The lint command currently fails on existing Prettier/CRLF issues in `src/components/spira/NewGoalSheet.tsx` and reports existing warnings in several component files. These lint failures are not introduced by the new test files.

## Manual Checks

- [ ] Review `docs/testing-guide.md` for accuracy.
- [ ] Review `docs/unit-vs-integration-tests.md` for accuracy.
- [ ] Review this spec before using it as future testing guidance.

## Definition Of Done

- [x] Unit tests exist for frontend progress logic.
- [x] Backend unit tests exist for core domain services.
- [x] Backend Spring integration tests exist for GraphQL persistence flows.
- [x] Test documentation exists under `docs/`.
- [x] Obsolete placeholder test files are removed.
- [x] Task spec exists under `specs/`.

## Recent Updates (2026-05-18)

### @Size Annotation Fixes

The following files were updated to add missing `@Size(max=N)` annotations:

| File | Change |
|------|--------|
| `Goal.java` | Added @Size(max=5000) for description |
| `RealityItem.java` | Already had @Size(max=5000) - verified |
| `Option.java` | Added @Size(max=500) for text + import |
| `ChecklistItem.java` | Added @Size(max=500) for text + import |
| `Resource.java` | Added @Size(max=50000) for body and dataUrl |
