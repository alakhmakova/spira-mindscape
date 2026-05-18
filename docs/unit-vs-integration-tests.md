# Unit Tests vs Integration Tests

This guide is for a new developer who is trying to decide what kind of test to write in this codebase.

The short version:

- A unit test checks one class or one small function directly.
- An integration test checks that several real pieces work together through the same path the app uses.

That sounds theoretical, so here is the practical version for Spira.

## Unit Tests

A unit test should answer:

> Does this one service or helper make the right decision when I give it controlled inputs?

In backend code, unit tests usually live next to the domain area they test:

```text
backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java
backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java
backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java
backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java
backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java
```

They usually use Mockito mocks for repositories. That means the test does not start Spring, does not open a database connection, and does not execute GraphQL.

Look at service code when choosing unit tests:

```java
if (input.current() != null) {
    throw new IllegalArgumentException("Numeric target current cannot be set on create");
}
```

That is a good unit-test candidate because the important behavior is a local rule in `TargetService`. You can call the service method directly and assert that it throws the right error.

Also look for:

- branches: `if`, `switch`, early returns;
- normalization: lowercasing, trimming, singular/plural aliases;
- generated defaults: link title from URL, email name from email, option position;
- calculations: target progress, goal progress;
- validation boundaries: 20 characters, 50,000 characters, 5 MB;
- ownership checks: option belongs to goal, reality item belongs to goal/kind;
- rollback-sensitive rules: reject invalid input before saving.

Good unit tests in this repo include:

- `TargetServiceTest` for target progress and target validation rules.
- `ResourceServiceTest` for resource type rules, labels, URL/email/file validation, and rejected old `contact` aliases.
- `GoalServiceTest` for option position, selection, ownership, and reorder rules.
- `RealityServiceTest` for action/obstacle kind normalization and grouping.

## Integration Tests

An integration test should answer:

> Does the real app path work when GraphQL, Spring binding, services, repositories, persistence, and error handling are connected?

Backend integration tests live here:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/
```

They use:

```java
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
```

That means the test starts the Spring application context and sends GraphQL documents through `GraphQlTester`. The test profile uses H2 and Hibernate `create-drop`, so the test checks real persistence without needing local PostgreSQL.

Look at controller/schema/API code when choosing integration tests:

```graphql
mutation {
  createResource(goalId: "1", input: {
    type: "link"
    url: "https://chatgpt.com"
  }) {
    id
    title
  }
}
```

That is a good integration-test candidate because the important behavior is the API contract, not just one Java method. You want to know whether GraphQL accepts the input shape, binds it to the Java record, calls the service, saves the row, returns fields, and classifies validation errors correctly.

Use integration tests for:

- GraphQL schema binding: required fields, optional fields, nulls, raw input maps;
- persistence: create, update, delete, query by id, query by parent;
- end-to-end validation errors: message text and GraphQL error classification;
- ownership and not-found behavior through public API calls;
- contract examples the frontend relies on;
- behavior involving multiple services or batch resolvers.

Good integration tests in this repo include:

- `GoalCreationIntegrationTest` for goal mutations and validation.
- `GoalWorkspaceIntegrationTest` for workspace query shape and old resource type rejection.
- `RealityIntegrationTest` for action/obstacle GraphQL flows.
- `OptionIntegrationTest` for option create/update/select/reorder/delete flows.
- `ResourceIntegrationTest` for note/link/file/email GraphQL contracts.
- `TargetIntegrationTest` for binary/numeric/checklist GraphQL contracts.

## How To Decide

Start by asking what could break.

If the bug would be caused by one method making the wrong decision, write a unit test.

Examples:

- `ResourceService` should reject a link with `file://...`.
- `TargetService` should reject duplicate checklist item ids.
- `RealityService` should normalize `action` to `actions`.
- `GoalService` should put a new option at the next position.

If the bug would be caused by wiring between layers, write an integration test.

Examples:

- GraphQL does not pass explicit `null` values into the service the way you expect.
- A mutation returns the wrong field after saving.
- A validation error is not classified as `ValidationError`.
- A delete mutation returns true but the row still exists.
- A batch field such as `Goal.resources` or `Goal.progress` returns the wrong data.

Many important rules deserve both:

- Unit test the service rule because it is fast and precise.
- Integration test the GraphQL contract because that is what the frontend uses.

Resource labels are a good example. `ResourceServiceTest` checks the service rule directly. `ResourceIntegrationTest` checks the GraphQL mutation, returned fields, and error classification.

## What To Look At In Code

When you open a class, scan in this order:

1. Public methods.
   These are the behaviors other code can call. Tests usually start here.

2. Constructor dependencies.
   If a class depends on repositories, unit tests can mock them. Integration tests should use real repositories.

3. Validation branches.
   Every `throw new IllegalArgumentException(...)` usually deserves a test somewhere.

4. Defaulting logic.
   Look for `== null ? ... : ...`, generated names, generated positions, generated current values, and empty-list fallbacks.

5. Type-specific logic.
   Switches and type checks are easy to break when a new type is added.

6. Persistence side effects.
   If the code saves, deletes, reorders, or changes several rows, consider at least one integration test.

7. Public API shape.
   If the frontend sends it through GraphQL, make sure there is an integration test for the expected input and output.

## Common Mistakes

Mistake: testing repository behavior with mocks.

If you mock a repository, you are not testing the database query. You are testing how your service reacts to the value you told the mock to return. That is useful for service rules, but not for persistence. Use an integration test for actual repository behavior.

Mistake: only testing the happy path.

Most bugs hide in invalid input: blank strings, nulls, ids from another parent, invalid type names, and boundary values such as 20 vs 21 characters.

Mistake: writing a slow integration test for tiny local logic.

If the behavior is just `action` becomes `actions`, a unit test is enough. Starting Spring for that is noise.

Mistake: writing only a unit test for a GraphQL contract.

If the frontend depends on a mutation accepting a certain shape, the service unit test is not enough. GraphQL binding can behave differently, especially with omitted fields versus explicit `null`.

Mistake: testing private methods directly.

Test through public behavior. Private methods are implementation details. If a private method has important behavior, call the public method that uses it.

Mistake: duplicated tests with different names but identical meaning.

It is okay to have both unit and integration coverage for a critical rule. It is not useful to repeat the same integration mutation three times with no new behavior.

## Current Backend Unit Coverage

Current service-level unit tests cover:

- `GoalValidationTest`: entity validation boundaries for goal title, description, confidence, and deadline.
- `GoalServiceTest`: option creation position, selection behavior, ownership checks, and reorder validation.
- `RealityServiceTest`: action/obstacle kind normalization and payload grouping.
- `ResourceServiceTest`: note/link/file/email rules, derived labels, resource label length, file validation, and old `contact` rejection.
- `TargetServiceTest`: progress calculation, numeric create/update validation, binary create validation, checklist item validation, and duplicate checklist id rejection.

## Current Backend Integration Coverage

Current GraphQL integration tests cover:

- goals;
- reality items;
- options;
- resources;
- targets;
- workspace-level nested data and progress;
- not-found errors;
- validation error messages and classifications.

## Commands

Run all backend tests:

```text
cd backend
.\mvnw.cmd test
```

Run only unit-style service tests:

```text
cd backend
.\mvnw.cmd test "-Dtest=GoalValidationTest,GoalServiceTest,RealityServiceTest,ResourceServiceTest,TargetServiceTest"
```

Run only resource GraphQL contract tests:

```text
cd backend
.\mvnw.cmd test "-Dtest=ResourceIntegrationTest"
```
