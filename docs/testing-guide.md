# Testing Guide

This document maps the current automated tests for the Spira frontend and backend contracts.

**Last Updated**: 2026-06-02

## Text Field Validation

All text fields have explicit size constraints:

- **Labels** (max 200 chars): title, name, email
- **Medium text** (max 500 chars): option text, checklist item text
- **Long text** (max 5000 chars): goal description, reality item text
- **Very long text** (max 50000 chars): note body, data URL

These constraints are enforced at both the JPA `@Size` annotation level and service-layer validation.

### Convention: bind boundary tests to the production constant (no magic numbers)

When a test exercises a length/size limit, it must **never hardcode the number**.
Reference the production constant that defines the limit, and build the boundary
values and the expected error message from it. The limit lives in exactly one place
(e.g. `ResourceService.MAX_RESOURCE_LABEL_LENGTH`, `GoalService.MAX_GOAL_TITLE_LENGTH`,
`RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH`); the entity `@Size` annotation and the
service validation both read it, so the tests should too.

Why: if the limit changes, the test follows automatically — there is nothing to edit,
and the test can never silently disagree with production. A hardcoded `201` or
`"… must be 200 characters or fewer"` is a second, competing source of truth that
drifts the moment someone changes the real limit.

```java
// ❌ Don't — magic numbers; breaks (or worse, lies) when the limit changes
String title = "A".repeat(201);
assertValidationError(response, "Goal title must be 200 characters or fewer");

// ✅ Do — single source of truth; the test tracks the constant
String title = "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH + 1);
assertValidationError(response,
        "Goal title must be " + GoalService.MAX_GOAL_TITLE_LENGTH + " characters or fewer");
```

Practical rules:

- **Accepted boundary** = `"A".repeat(LIMIT)`; **rejected boundary** = `"A".repeat(LIMIT + 1)`.
- Build the expected message by concatenating the constant, not by typing the number.
- In parameterised `@MethodSource` providers, format the constant into the input with
  `"…title: \"%s\"…".formatted(labelOverLimit())` rather than embedding a literal string.
- `@DisplayName` is a compile-time annotation and cannot interpolate a constant, so
  phrase it generically ("over the maximum length") instead of naming the number.
  When the case name is built at runtime (e.g. the first argument of a `@MethodSource`
  `Arguments.of(...)`), you *can* and should fold the constant in.
- The same rule applies to byte limits (`MAX_FILE_BYTES`) and any other validated bound.

The same principle holds on the frontend: import/derive the shared limit rather than
re-typing it in assertions (see `docs/frontend-testing-guide.md`).

The main product rules under test are: goals are structured workspaces, backend persistence is the source of truth, options/reality/targets are edited through GraphQL, and progress is calculated only from targets.

New to testing this project? Start with these beginner guides:

```text
docs/unit-vs-integration-tests.md   # how to choose unit vs integration vs E2E (backend)
docs/frontend-testing-guide.md      # frontend testing from zero
```

## Test Commands

### Frontend

From the repository root:

```powershell
npm.cmd test
npm.cmd run build
```

On Windows PowerShell, use `npm.cmd` if `npm` is blocked by the local execution policy.

### Backend

From the backend directory:

```powershell
cd backend
.\mvnw.cmd test
```

### E2E

The E2E suite runs against a live backend. Start the backend first, then:

```powershell
cd tests-e2e
pytest
```

By default tests target `http://localhost:8080`. Override with the environment variable:

```powershell
$env:SPIRA_BASE_URL = "http://myhost:8080"
pytest
```

Run a single E2E file:

```powershell
pytest test_goals_e2e.py -v
```

Dependencies: Python 3.11+. Install them from the pinned list:

```powershell
cd tests-e2e
pip install -r requirements.txt
```

In CI the E2E job runs against a **real PostgreSQL** service container (so the Flyway
migrations are exercised end-to-end), not H2. Locally you can point the suite at any
running backend via `SPIRA_BASE_URL`.

### Coverage (JaCoCo)

The backend build measures line/branch coverage with JaCoCo. Running the tests
generates an HTML + CSV report automatically:

```powershell
cd backend
.\mvnw.cmd test
# report is written to:
#   backend/target/site/jacoco/index.html   (open in a browser)
#   backend/target/site/jacoco/jacoco.csv   (machine-readable)
```

CI prints a one-line "Backend line coverage: NN.N%" summary and uploads the full
HTML report as the `backend-jacoco-report` artifact on every run. Treat the number
as a *guide to find untested branches*, not a target to chase — 100% coverage of
trivial getters is worth less than one good test of a real rule.

---

## Frontend Unit Tests

Files:

```text
src/lib/spira/api.contract.test.ts
src/lib/spira/api.test.ts
src/lib/spira/progress.test.ts
src/lib/spira/store.test.ts
```

What they cover:

- GraphQL `INTERNAL_ERROR` details are kept out of the public API error message while remaining available in `details`.
- Network failures use a safe backend-unavailable message.
- Goal payload mapping for title, description, confidence, deadline, created date, achieved date, reality, options, resources, targets, and checklist tasks.
- API input serialization for all resource types and all target types, including removal of local checklist task ids before create.
- Numeric target progress, including inferred start values, reverse progress, and clamping.
- Done / Not Done target progress.
- Checklist progress, including empty checklists.
- Goal progress as an equal average of all target progress values.
- Goal progress returning `0` when a goal has no targets.
- Store-level optimistic rollback for resource validation errors.
- Store-level achieved date propagation and clearing for goals, targets, and checklist tasks.

Why these tests exist:

The frontend progress algorithm is part of the MVP domain contract. Backend progress calculations should stay aligned with this behavior.

User-facing sync errors must also stay safe. Technical backend diagnostics such as GraphQL internal error IDs are useful for developers, but they should not appear as the main application message.

---

## Backend Unit Tests

### Goal Validation

File:

```text
backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java
```

What it covers:

- Goal title is required, cannot be blank, and has a 200 character max boundary.
- Description is optional and has a 5000 character max boundary.
- Confidence is required and must stay within `1..10`.
- Deadline is optional.

Why this test exists:

These are bean-validation boundaries for the goal model. They are faster and clearer as unit tests than as GraphQL integration tests.

### Entity Timestamps

File:

```text
backend/src/test/java/com/spiramindscape/backend/goal/EntityTimestampTest.java
```

What it covers:

- `createdAt` is set on first save and never changes on subsequent saves.
- `updatedAt` is updated on every save.
- Both timestamps are non-null after persist.

Why this test exists:

Timestamp behavior is controlled by JPA lifecycle annotations. These tests guard against accidental removal of `@CreationTimestamp` / `@UpdateTimestamp` or misconfiguration of the Hibernate dialect.

### Target Service

File:

```text
backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java
```

What it covers:

**Binary targets**
- Created with `done=false` and type `binary`.
- `done=true` on create is rejected.
- `done` can be updated to `true` or back to `false`.

**Numeric targets**
- Creation initialises `current` to `start`; explicit `current` on create is rejected.
- Missing `start` or `total`, or explicit `null` for either, is rejected.
- Negative `start`, `current`, or `total` is rejected on create and update.
- Equal `start` and `total` is rejected.
- Descending range (`start > total`) is accepted; `current` outside the descending range is rejected.
- Progress for ascending range: `(current − start) / (total − start)`.
- Progress for descending range: `(start − current) / (start − total)`.
- Updating `start` or `total` recalculates progress.
- `current` outside the ascending range is rejected on update.

**Checklist targets**
- Items are saved with correct text and `done` state.
- Null or empty items list on create is rejected.
- Items on non-checklist type are rejected on create and update.
- Empty items list on update is rejected; original is preserved.
- Blank item text on update is rejected.
- Non-existent item id on update is rejected.
- Duplicate item ids on update are rejected.
- Progress: `0` when no items done, `1` when all done, `done/total` partial.
- Progress is calculated from repository-loaded checklist items, not from a potentially lazy JPA collection.

Why this test exists:

It protects the backend progress calculation and service-level rules before GraphQL, persistence, and error classification are involved.

### Resource Service

File:

```text
backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java
```

What it covers:

- Note resources require a title and accept an optional body.
- Link resources require a URL, accept an optional title, and derive a title from the URL domain when the title is omitted.
- File resources require a title, MIME type, and data URL.
- Email resources require an email address, accept an optional name, and derive the name from the full email address when the name is omitted.
- Resource `title` and `name` fields are capped at 200 characters.
- Note bodies are capped at 50,000 characters.
- File resources are limited to image/PDF MIME types, matching data URL MIME prefixes, valid base64 data, and 5 MB payloads.
- Resource updates preserve manual labels but refresh autogenerated link/email labels when their source URL/email changes.
- The old `contact` resource alias is rejected.

Why this test exists:

The current Spira model uses explicit resource types (`note`, `link`, `file`, and `email`) with type-specific validation. These tests protect the service-level rules before GraphQL, persistence, and error classification are involved.

### Goal Service

File:

```text
backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java
```

What it covers:

- Goal create, update, and delete lifecycle.
- Title normalisation (whitespace trimming) and max-length enforcement.
- Confidence history: entry created on goal creation, new entry on confidence change, no duplicate entry when confidence is unchanged.
- Options: add at next position, start unselected, select deselects others, update and remove, text validation and max-length, whitespace trimmed, option from another goal rejected, reorder with wrong id rejected.
- Data isolation: options and reality items from one goal are not visible in another.

Why this test exists:

Options have small but important service rules that are easy to break while changing the UI or GraphQL layer. These tests keep those rules fast and focused.

### Reality Service

File:

```text
backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java
```

What it covers:

- Add, update, and remove reality items.
- Text validation: blank text rejected, text over 500 chars rejected, whitespace trimmed.
- Singular and plural reality kinds normalize to `actions` and `obstacles`.
- Unknown reality kinds are rejected.
- Item from another goal is rejected.
- Batched reality payloads keep actions and obstacles separated and return empty lists for goals without items.

Why this test exists:

Reality is simple enough that the important rules should be checked directly at the service layer instead of relying only on slower GraphQL integration tests.

---

## Backend GraphQL Contract Tests

All GraphQL integration tests use:

```text
backend/src/test/resources/application-test.properties
```

The test profile uses H2 with Hibernate `create-drop` and disables Flyway because production Flyway migrations contain PostgreSQL-specific SQL.

The detailed contract tests live in the files below.

### Goals — Lifecycle

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java
```

What it covers:

- Creating goals with required fields only and with optional fields (description, deadline).
- Title validation: blank, whitespace-only, over 200 characters.
- Description validation: over 5000 characters.
- Confidence validation: missing, out-of-range (0, 11), non-numeric.
- Querying a goal by id and querying an unknown id.
- Updating title, description, deadline, confidence, and `achievedAt`.
- Clearing optional fields with explicit `null` (deadline, description).
- Invalid date format for `deadline` and `achievedAt` returns `ValidationError` (not `INTERNAL_ERROR`).
- Deleting a goal and verifying it is gone.
- `NOT_FOUND` errors for get, update, and delete of missing goals.

### Goals — Confidence History

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/GoalConfidenceIntegrationTest.java
```

What it covers:

- Confidence history is recorded on goal creation.
- A new history entry is added when confidence changes.
- No duplicate entry when confidence is updated to the same value.
- Invalid confidence values (0, 11, negative) are rejected on create and update; original value is preserved.
- Updating confidence to `null` is rejected.
- Non-numeric confidence value is rejected.
- History is returned in descending `at` order after multiple updates.
- Valid boundary values (1, 5, 10) are accepted.

### Goals — Cascade Delete

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/GoalCascadeDeleteIntegrationTest.java
```

What it covers:

- Deleting a goal removes all its options from the database.
- Deleting a goal removes all its reality items from the database.
- Deleting a goal removes all its resources from the database.
- Deleting a goal removes all its targets and their checklist items from the database.
- Deleting a goal removes all its confidence history entries from the database.
- Deleting a target removes its checklist items from the database.
- Deleting one goal does not affect any other goal or its data.

Why this test exists:

Cascade behavior is declared at the JPA level. These tests verify the declarations are correct against the actual H2 schema — a schema mismatch (e.g., wrong table name or missing `cascade = ALL`) would surface here and not in higher-level tests.

### Goals — Data Isolation

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/GoalIsolationIntegrationTest.java
```

What it covers:

- Options added to goal A are not visible when querying goal B.
- Reality items (actions and obstacles) added to goal A are not visible in goal B.
- Targets added to goal A are not visible in goal B.
- Resources added to goal A are not visible in goal B.
- Confidence history of goal A does not appear in goal B.
- Progress from targets in goal A does not affect the progress of goal B.
- Selecting an option in goal A does not affect the selected state of options in goal B.

### Goals — List Query

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/GoalListIntegrationTest.java
```

What it covers:

- `goals` query returns an empty list when no goals exist.
- `goals` query returns all goals ordered by `createdAt` ascending.
- `goals` query resolves the `reality` `BatchMapping` (empty actions and obstacles for a new goal).
- `goals` query resolves the `options` `BatchMapping` (empty list for a new goal).
- `goals` query resolves the `targets` `BatchMapping` (empty list for a new goal).
- `goals` query resolves the `resources` `BatchMapping` (empty list for a new goal).
- `goals` query resolves the `confidenceHistory` `BatchMapping` (one entry on creation).
- `goals` query resolves the `progress` `BatchMapping` (0 for a goal with no targets).
- All `BatchMapping` fields resolve correctly for multiple goals in a single query.

Why these `BatchMapping` tests exist:

The `goals` query uses Spring GraphQL `@BatchMapping` to load child collections efficiently. A broken `BatchMapping` returns empty lists silently — no exception, no GraphQL error — so bugs are invisible unless explicitly asserted.

### Reality

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/RealityIntegrationTest.java
```

What it covers:

- Adding actions and obstacles.
- Multiple items per kind.
- Actions and obstacles remain independent.
- Querying reality by goal id.
- Updating action and obstacle text.
- Removing action and obstacle items.
- Validation for unknown kind and kind mismatch.
- Text validation: blank text rejected, over 500 characters rejected, whitespace trimmed.
- `NOT_FOUND` errors for missing reality items and items that belong to another goal.
- Goal isolation: reality items from one goal do not appear in another.

### Options

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/OptionIntegrationTest.java
```

What it covers:

- Adding options with default `selected=false`.
- Consecutive option positions.
- Querying empty option lists.
- Selecting one option and deselecting all others.
- Idempotent selection of an already-selected option.
- Updating active and inactive option text.
- Deselecting active options through update.
- Removing active and inactive options.
- Reordering options and persisting positions.
- Validation for reorder count mismatch.
- Text validation: blank text rejected, over 500 characters rejected, whitespace trimmed.
- `NOT_FOUND` behavior for missing options and options that do not belong to the goal.
- Goal isolation: option from another goal is rejected on select and remove.

### Targets

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java
```

What it covers:

- Creating binary, numeric, and checklist targets with required and optional fields.
- Required-field validation for each target type.
- Unknown target type validation.
- Invalid target and checklist item deadline formats.
- `Goal not found` when creating or querying targets for a missing goal.
- Querying targets by goal id and target id.
- `Target not found` for missing target query/update/delete.
- Binary target updates and progress changes.
- Numeric target creation: explicit `null` for `start`, `total`, or `current` rejected; negative values rejected; equal `start` and `total` rejected.
- Numeric target updates: `current` outside ascending/descending range rejected; negative `start` or `total` on update rejected; equal `start` and `total` after update rejected; updating `start` or `total` recalculates progress.
- Checklist target progress updates.
- Adding, editing, and deleting checklist tasks while preserving at least one task.
- Checklist validation for empty task lists, blank task text, duplicate task ids, non-existent task ids, and task ids from another target.
- Validation that `items` cannot be sent for non-checklist targets.
- Deleting targets by type.
- Goal progress as the average of target progress values.

### Resources

File:

```text
backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java
```

What it covers:

- Creating note, link, file, and email resources.
- Type-specific required fields: note title, link URL, file title/MIME/data URL, and email address.
- Optional-field combinations for notes, links, files, and emails.
- Resource label rules: title/name trimming, accepted boundary at the label limit (200 characters), and rejection one character past it on create and update.
- Link URL validation for valid HTTP/HTTPS URLs, blank values, trimming, and generated titles from domains such as `chatgpt.com` and `www.chatgpt.com`.
- Link update behavior: autogenerated titles refresh when the URL changes, while manual titles are preserved.
- File validation for required title/MIME/data URL, blank values, allowed image/PDF MIME types, MIME/data URL consistency, malformed base64, and the 5 MB size boundary.
- Email validation for required/blank/invalid addresses, generated names from full email addresses, manual names, and update behavior for generated versus manual names.
- Note body validation: max 50,000 characters on create and update.
- Rejection of fields that do not belong to the resource type on create and update, using a matrix across note, link, file, and email resources.
- `Goal not found` when creating or querying resources for a missing goal.
- `Resource not found` for missing resource query/update/delete.

Why these GraphQL tests exist:

These classes verify the API the frontend actually uses: GraphQL schema binding, controller arguments, service rules, repositories, persistence, error messages, and error classifications together.

They intentionally keep important user-facing contract checks at the integration layer. Small pure calculations and bean-validation boundaries stay in unit tests where they are faster and easier to diagnose.

---

## E2E Tests

The E2E suite in `tests-e2e/` fires real HTTP requests at a running backend using Python + `httpx` + `pytest`. Unlike integration tests that boot Spring in-process, these tests validate the full stack end-to-end: Docker networking, Spring Boot startup, GraphQL parsing, service logic, JPA persistence, and HTTP response encoding.

**Prerequisite**: the backend must be running and reachable at `SPIRA_BASE_URL` (default: `http://localhost:8080`).

Each test that mutates data uses the `created_goal` fixture (defined in `conftest.py`), which creates a goal before the test and deletes it after. Tests that need multiple goals create and clean up their own data.

### Health

File: `tests-e2e/test_health.py`

- The GraphQL endpoint responds without errors to a `goals` query.
- The GraphQL endpoint returns an `errors` array for a query with an unknown field.

### Error Envelope

File: `tests-e2e/test_error_envelope.py`

- `NOT_FOUND` error shape: `classification` extension is `"NOT_FOUND"`.
- `ValidationError` error shape: `classification` extension is `"ValidationError"`.
- Error messages are non-empty strings.

### Goals

File: `tests-e2e/test_goals_e2e.py`

- Create goal with title and confidence only.
- Create goal with all optional fields (description, deadline).
- Read goal by id; all fields returned correctly.
- Update title, description, confidence.
- Set and clear `achievedAt`.
- Set, change, and clear deadline.
- Delete goal; subsequent query returns `NOT_FOUND`.
- `goals` list returns goals in `createdAt` ascending order.
- Confidence history grows with each distinct confidence change.

### Reality

File: `tests-e2e/test_reality_e2e.py`

- Add action and obstacle items.
- Update item text.
- Remove items.
- Blank text is rejected with `ValidationError`.
- Text over 500 characters is rejected.
- Whitespace is trimmed.
- Goal not found returns `NOT_FOUND`.

### Options

File: `tests-e2e/test_options_e2e.py`

- Add options; positions assigned consecutively.
- Select option; others are deselected.
- Update option text.
- Remove option; remaining options are preserved.
- Reorder options; persisted positions verified.
- Blank text rejected on add and update.
- Text over 500 characters rejected.
- Goal isolation: option from another goal is rejected on select and remove.
- Goal not found returns `NOT_FOUND`.

### Targets

File: `tests-e2e/test_targets_e2e.py`

- Create binary target; `done=false`; mark done.
- Create numeric target with `start`, `total`; progress calculated correctly.
- Update numeric target `current`; progress recalculates.
- Update numeric target `start`; progress recalculates.
- Update numeric target `total`; progress recalculates.
- Negative `start` or `total` rejected on numeric update.
- Equal `start` and `total` rejected on numeric update.
- Create checklist target with items; progress from done count.
- Mark checklist items done; progress reaches 1.0.
- Set and clear target deadlines; invalid format returns `ValidationError`.
- Goal not found on create returns `NOT_FOUND`.
- Delete binary, numeric, and checklist targets.

### Resources

File: `tests-e2e/test_resources_e2e.py`

- Create note, link, file, and email resources.
- Read resource by id.
- Update resource fields.
- Delete resource.
- Field validation: blank title, blank URL, invalid MIME type.

### Progress

File: `tests-e2e/test_progress_e2e.py`

- Goal progress is `0` when no targets exist.
- Goal progress is the average of all target progress values.
- Mixed binary + numeric + checklist targets all contribute to the average.
- Partial progress (some done, some not).
- Full progress: all targets at `1.0` → goal progress `1.0`.
- Progress reversal: marking a done target undone decreases progress.

---

## Current Validation

### Full suite

```powershell
# Frontend
npm.cmd test

# Backend
cd backend
.\mvnw.cmd test

# E2E (backend must be running)
cd tests-e2e
pytest
```

### Focused backend commands

```powershell
cd backend

# Goal lifecycle and field validation
.\mvnw.cmd -Dtest=GoalCreationIntegrationTest test

# Confidence history
.\mvnw.cmd -Dtest=GoalConfidenceIntegrationTest test

# Cascade delete (DB-level)
.\mvnw.cmd -Dtest=GoalCascadeDeleteIntegrationTest test

# Data isolation between goals
.\mvnw.cmd -Dtest=GoalIsolationIntegrationTest test

# goals list query and BatchMapping resolvers
.\mvnw.cmd -Dtest=GoalListIntegrationTest test

# Domain tests
.\mvnw.cmd -Dtest=RealityIntegrationTest test
.\mvnw.cmd -Dtest=OptionIntegrationTest test
.\mvnw.cmd -Dtest=TargetIntegrationTest test
.\mvnw.cmd -Dtest=ResourceIntegrationTest test

# Unit tests
.\mvnw.cmd -Dtest=TargetServiceTest test
.\mvnw.cmd -Dtest=GoalServiceTest test
.\mvnw.cmd -Dtest=RealityServiceTest test
.\mvnw.cmd -Dtest=ResourceServiceTest test
.\mvnw.cmd -Dtest=GoalValidationTest test
.\mvnw.cmd -Dtest=EntityTimestampTest test
```

### Run one backend test method

```powershell
cd backend
.\mvnw.cmd -Dtest=TargetIntegrationTest#returnsErrorWhenUpdatingChecklistTargetWithNonExistentTaskId test
```

### Focused E2E commands

```powershell
cd tests-e2e
pytest test_goals_e2e.py -v
pytest test_targets_e2e.py -v
pytest test_progress_e2e.py -v
```

### Run one frontend test file

```powershell
npm.cmd test -- --run src/lib/spira/progress.test.ts
npm.cmd test -- --run src/lib/spira/api.test.ts
```

### Lint

```powershell
npm.cmd run lint
```

The lint command has previously failed on existing formatting / Fast Refresh / hook warnings. Treat lint status separately from the target, reality, option, and goal contract test status.
