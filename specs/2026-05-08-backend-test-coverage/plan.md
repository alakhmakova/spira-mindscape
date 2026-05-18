# Plan: Backend And Progress Test Coverage

**Last Updated**: 2026-05-18

## Group 1: Frontend Progress Unit Tests

1. Add Vitest as the frontend test runner.
2. Add an `npm test` script that runs `vitest run`.
3. Add tests for `src/lib/spira/progress.ts`.
4. Cover numeric, binary, checklist, and goal-level progress.

## Group 2: Backend Unit Tests

5. Add `TargetServiceTest`.
6. Verify target progress calculation rules.
7. Verify checklist progress uses repository-loaded checklist items.
8. Add `ResourceServiceTest`.
9. Verify Email resource handling.
10. Verify the old `contact` alias is rejected.
11. Add `GoalServiceTest`.
12. Verify option position, selection, ownership, and reorder rules.
13. Add `RealityServiceTest`.
14. Verify action/obstacle kind normalization and batched grouping.
15. Expand `TargetServiceTest` to cover numeric, binary, and checklist validation rules.

## Group 3: Backend Spring Integration Tests

16. Add a `test` Spring profile using H2.
17. Disable Flyway in the H2 integration profile because production migrations use PostgreSQL-specific SQL.
18. Add focused GraphQL integration test classes with `@SpringBootTest` and `@AutoConfigureGraphQlTester`.
19. Test goal creation and default nested fields.
20. Test reality, options, resources, and targets through GraphQL mutations.
21. Test progress through GraphQL for all target types.
22. Test GraphQL error handling for missing goals and invalid resource types.
23. Split broad GraphQL coverage into focused classes and remove obsolete placeholder test files.

## Group 4: Documentation

24. Create `docs/testing-guide.md`.
25. Create `docs/unit-vs-integration-tests.md`.
26. Document every test file, its purpose, and how to run the test suite.
27. Explain, for new developers, how to decide between unit and integration tests.
28. Record the known current lint state separately from the added test coverage.

## Group 5: Validation

29. Run frontend unit tests.
30. Run frontend production build.
31. Run backend Maven tests.
32. Run lint and document any pre-existing failures if they are outside this task.

## Group 6: @Size Annotation Fixes (2026-05-18)

33. Add @Size(max=5000) for Goal.description
34. Add @Size(max=500) for Option.text
35. Add @Size(max=500) for ChecklistItem.text
36. Add @Size(max=50000) for Resource.body and Resource.dataUrl
37. Update all import statements for jakarta.validation.constraints.Size

## Group 7: Documentation Updates (2026-05-18)

38. Update docs/test-coverage-report.md with @Size fixes
39. Update docs/testing-guide.md with text field validation info
40. Update specs/2026-05-08-backend-test-coverage/validation.md
