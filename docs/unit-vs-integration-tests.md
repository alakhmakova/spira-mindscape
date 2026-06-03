# Choosing Your Test Level: Unit, Integration, and E2E

Read this if you are new and you just want to know: **"I changed some code — which test do I write, and where do I put it?"**

This is not a theory lecture. It is a field guide for *this* project. By the end you should be able to look at a change, decide which of the three test levels it needs, find the right folder, copy a nearby test, and write yours — without anyone telling you each step.

---

## 1. The 30-second mental model

We have exactly **three levels** of tests. Think of them as three different questions about the same code:

| Level | The question it answers | Speed | Touches a database? | Touches the network? |
|---|---|---|---|---|
| **Unit** | "Does this one Java method make the right decision?" | milliseconds | No (repositories are faked) | No |
| **Integration** | "Does the whole backend path work through GraphQL?" | ~1 second each | Yes (in-memory H2) | No (calls GraphQL in-process) |
| **E2E** | "Does the real running app behave correctly over HTTP?" | seconds | Yes (real PostgreSQL) | Yes (real HTTP requests) |

The shape you want is a **pyramid**: many fast unit tests at the bottom, fewer integration tests in the middle, a thin layer of E2E at the top. This project already follows that shape, and you should keep it that way:

```
        /\        E2E  (Python, tests-e2e/)         ~110 tests  ← few, slow, broad
       /  \
      /----\      Integration (GraphQL, *IntegrationTest)  ~370 tests
     /      \
    /--------\    Unit (services, *ServiceTest)            ~200 tests  ← many, fast, precise
```

**Why a pyramid and not a rectangle?** Unit tests are fast and pinpoint the exact broken line. E2E tests are slow and, when they fail, only tell you "something in the whole stack is wrong." So you push *detail* down to unit tests and keep the top layers for *"is it all wired together?"*.

---

## 2. The cheat-sheet (start here every time)

Before writing a test, ask: **"If this broke, where would the bug actually live?"** That tells you the level.

| What you changed / what could break | Level to write | Folder |
|---|---|---|
| A rule inside a service method (`if`, validation, a calculation, trimming, a default value) | **Unit** | `backend/src/test/java/.../<area>/` |
| The shape of the GraphQL API (a new field, a new mutation, how `null` is handled, the error a client receives) | **Integration** | `backend/src/test/java/.../graphql/` |
| "Does it actually work when the real server runs against a real database?" (a smoke test of a full user flow) | **E2E** | `tests-e2e/` |
| Pure logic in the frontend (progress math, data mapping) | Frontend unit | see `docs/frontend-testing-guide.md` |

Most real rules deserve **two** tests: a unit test for the *rule* (fast, precise) and an integration test for the *contract* (the thing the frontend depends on). That is normal and good — section 6 explains how to do this without writing the same test twice.

---

## 3. Level 1 — Unit tests

### What a unit test is here
It creates **one service object**, hands it **fake repositories**, calls **one method**, and checks the result or the exception. Spring does not start. No database opens. It runs in milliseconds.

### Where they live
Next to the area they test:

```text
backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java
backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java
backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java
backend/src/test/java/com/spiramindscape/backend/goal/EntityTimestampTest.java
backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java
backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java
```

### Where to LOOK in the app code to find unit-test candidates
Open the **service** classes — this is where the decisions live:

```text
backend/src/main/java/.../goal/GoalService.java
backend/src/main/java/.../goal/RealityService.java
backend/src/main/java/.../target/TargetService.java
backend/src/main/java/.../resource/ResourceService.java
```

Scan a service top to bottom and circle these — each is a unit test:

- **Every `throw new IllegalArgumentException(...)`.** Each one is a rule a user can hit. Test that the wrong input throws it, *and* that nothing was saved.
- **Branches**: `if`, `switch`, early `return`.
- **Normalization**: `.trim()`, `.toLowerCase()`, singular→plural (`action`→`actions`).
- **Generated defaults**: a link title derived from a URL, an option's next `position`, a numeric target's `current` defaulting to `start`.
- **Calculations**: `calculateTargetProgress`, goal progress averaging.

### A real example from this project, annotated
From `TargetService.create(...)`:

```java
if (input.current() != null) {
    throw new IllegalArgumentException("Numeric target current cannot be set on create");
}
```

The matching unit test in `TargetServiceTest`:

```java
@Test
void rejectsNumericTargetCurrentOnCreate() {
    when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));   // fake the DB

    assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
            "Read pages", "numeric", null, 0d, 1d, 10d, null, null, null
    ), Map.of("current", 1d)))
            .isInstanceOf(IllegalArgumentException.class)                  // right type
            .hasMessageContaining("Numeric target current cannot be set on create"); // right message
}
```

Notice: the repository is **mocked** (`when(...).thenReturn(...)`), so no database is involved. We only test the *decision* the service makes.

### The recipe (copy this structure)
```java
@ExtendWith(MockitoExtension.class)   // turns on Mockito
class MyServiceTest {
    @Mock private SomethingRepository repo;   // fake dependency
    @InjectMocks private MyService service;    // real service, fakes injected

    @Test
    void rejectsBadInput() {
        // 1. ARRANGE: tell the fake what to return
        when(repo.findById(1L)).thenReturn(Optional.of(something()));
        // 2. ACT + 3. ASSERT the failure
        assertThatThrownBy(() -> service.doThing(1L, badInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the exact message");
        // 4. prove nothing was written
        verify(repo, never()).save(any());
    }
}
```
For a happy path, also stub `when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));` and assert the returned object's fields.

### What to test at this level
- Each validation rule (one test per `throw`).
- Boundaries on **both** sides: a 200-char title is accepted, 201 is rejected.
- The happy path for each branch (e.g. numeric vs binary vs checklist creation).

### What NOT to test here
- **Don't test the database.** A mock returns what you told it to; it proves nothing about real SQL. (That's the integration level's job.)
- **Don't test private methods directly.** Call the public method that uses them.
- **Don't start Spring** for something this small — that's just slow.

---

## 4. Level 2 — Integration tests

### What an integration test is here
It **starts the real Spring application** with an **in-memory H2 database**, then sends a **real GraphQL query/mutation** and checks the response. This exercises everything the frontend's request would hit: schema binding → controller → service → repository → database → error handler. The only "fake" part is that the database is H2 instead of PostgreSQL.

### Where they live
```text
backend/src/test/java/com/spiramindscape/backend/graphql/
  GoalCreationIntegrationTest.java     GoalListIntegrationTest.java
  GoalConfidenceIntegrationTest.java   GoalIsolationIntegrationTest.java
  GoalCascadeDeleteIntegrationTest.java
  OptionIntegrationTest.java           RealityIntegrationTest.java
  TargetIntegrationTest.java           ResourceIntegrationTest.java
```

### Where to LOOK in the app code to find integration-test candidates
Open the **API surface**:

```text
backend/src/main/resources/graphql/schema.graphqls            ← the contract
backend/src/main/java/.../graphql/SpiraGraphqlController.java  ← the entry points
backend/src/main/java/.../graphql/GraphQlExceptionHandler.java ← how errors reach the client
```

Write an integration test when the risk is in the **wiring**, not in one method:

- A new field or mutation in `schema.graphqls`.
- The difference between **omitting** a field and sending it as **explicit `null`** (e.g. clearing a deadline). This is handled by `rawInput` maps in the controller and is a classic place for bugs that a unit test cannot see.
- The **error a client actually receives**: is a bad value classified as `ValidationError` or `NOT_FOUND`?
- Anything spanning multiple rows or services: cascade delete, the `@BatchMapping` resolvers (`Goal.progress`, `Goal.options`, …).

### A real example from this project, annotated
From `GoalCascadeDeleteIntegrationTest` — proving that deleting a goal really removes its options from the database:

```java
@SpringBootTest                  // start the whole app
@AutoConfigureGraphQlTester      // give us a GraphQL client
@ActiveProfiles("test")          // use the H2 test config
class GoalCascadeDeleteIntegrationTest {

    @Test
    void deleteGoalRemovesOptions() {
        String goalId = createGoal("Goal with options", 5);  // via real GraphQL
        addOption(goalId, "Option 1");

        // talk to the DB directly to verify the side effect really happened
        assertThat(countRows("option", "goal_id", Long.valueOf(goalId))).isEqualTo(1);

        deleteGoal(goalId);

        assertThat(countRows("option", "goal_id", Long.valueOf(goalId))).isZero();
    }
}
```

This could not be a unit test: it depends on the JPA cascade configuration and the real schema, not on a single method.

### The recipe
```java
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class MyFeatureIntegrationTest {
    @Autowired private GraphQlTester graphQlTester;
    @Autowired private SomeRepository repo;   // optional, for DB-level checks

    @AfterEach
    void clean() { repo.deleteAll(); }        // each test starts clean

    @Test
    void mutationReturnsExpectedShape() {
        graphQlTester.document("""
            mutation { createGoal(input: { title: "X", confidence: 5 }) { id title } }
            """)
            .execute()
            .path("createGoal.title").entity(String.class).isEqualTo("X");
    }
}
```
For error cases, use `.execute().errors().satisfy(...)` and assert the `classification` extension.

### What to test at this level
- The request shapes the frontend really sends (look at `tests-e2e/graphql/queries.py` or the frontend `api.ts` for the real shapes).
- Required vs optional vs explicit-null fields.
- Error message **and** classification.
- Persistence side effects you can't see from a single method (cascades, ordering, batch fields).

### What NOT to test here
- **Don't re-test every tiny validation boundary** that a unit test already nails. One representative error per rule through GraphQL is enough — the exhaustive boundary cases belong in the unit test (faster, clearer). See section 6.
- **Don't write an integration test for pure local logic** like `action` → `actions`. Starting Spring for that is wasted time.

---

## 5. Level 3 — E2E tests

### What an E2E test is here
Python tests in `tests-e2e/` fire **real HTTP requests** at a **running backend** connected to a **real PostgreSQL** (in CI, a `postgres` service container; the migrations run via Flyway). Nothing is faked. This is the closest thing to "a user actually used the app."

### Where they live
```text
tests-e2e/
  conftest.py            ← shared helpers + fixtures (gql(), created_goal, goal_factory)
  graphql/queries.py     ← the GraphQL documents, reused across tests
  test_health.py  test_error_envelope.py
  test_goals_e2e.py  test_reality_e2e.py  test_options_e2e.py
  test_targets_e2e.py  test_resources_e2e.py  test_progress_e2e.py
```

### Where to LOOK to find E2E candidates
Think in terms of **user journeys**, not methods. A good E2E test is a short story: "create a goal → add two targets → mark one done → the goal is 50% complete." If you can describe a feature as something a user does end to end, it may deserve one E2E test.

### A real example from this project, annotated
From `test_progress_e2e.py`:

```python
def test_goal_progress_is_average_of_all_target_progress(client, created_goal):
    # created_goal is a fixture: it makes a goal and deletes it after the test
    numeric_id = _create_numeric_target(client, created_goal, "Read pages", 0.0, 10.0)
    gql(client, queries.UPDATE_TARGET, {"id": numeric_id, "current": 5.0})  # 50%
    _create_binary_target(client, created_goal, "Not done")                 # 0%

    progress = _goal_progress(client, created_goal)
    assert progress == pytest.approx(0.5 / 2)   # average of 0.5 and 0.0
```

Two things to copy:
- **`pytest.approx(...)`** for any float — never compare floats with `==`.
- **The `created_goal` fixture** — it cleans up after itself, so tests don't pollute the shared database. When you need to create extra goals, use the **`goal_factory`** fixture, which deletes everything it created even if the test fails midway.

### The recipe
```python
def test_my_flow(client, created_goal):
    # 1. do something through the real API
    result = gql(client, queries.SOME_MUTATION, {"goalId": created_goal, ...})
    data = require_data(result, "someMutation")   # require_data asserts no errors
    # 2. assert the user-visible outcome
    assert data["someField"] == "expected"
```
For an error case, assert on the envelope instead:
```python
assert "errors" in result
assert any("Goal not found" in e["message"] for e in result["errors"])
```

### What to test at this level
- One happy path per feature ("the golden path").
- A couple of important failure paths (not found, validation rejected).
- Things only the *full stack* can prove: that the real server boots, that GraphQL is reachable, that progress is computed correctly across target types end to end.

### What NOT to test here
- **Don't re-test every validation rule.** You already covered those at the unit and integration levels. E2E is expensive — keep it to "is the whole thing wired up and alive?"
- **Don't depend on global state.** The backend is shared across the whole test run and is *not* reset between tests. Assert on *your own* freshly-created goal, and use membership checks (`created_goal in ids`) rather than exact global counts.

---

## 6. The golden rule: test a rule deeply ONCE, smoke it higher up

New testers often write the *same* assertion at all three levels. Don't. It's slow and you maintain three copies of one idea. Instead:

> **Test the detail at the lowest level that can see it. Test the wiring once at each higher level.**

Worked example — the "note body max 50 000 chars" rule:

| Level | What it asserts | How many cases |
|---|---|---|
| Unit (`ResourceServiceTest`) | 50 000 accepted, 50 001 rejected, exact message | the exhaustive boundary cases |
| Integration (`ResourceIntegrationTest`) | a too-long body over GraphQL returns a `ValidationError` | **one** representative case |
| E2E (`test_resources_e2e.py`) | usually nothing for this — it's already proven | zero, unless it's part of a journey |

So: boundaries and messages → **unit**. "The API surfaces this error correctly" → **one integration** test. The full journey → **E2E only if it's a real user flow**.

---

## 7. A full worked example: "I'm adding a `priority` field to goals"

Suppose the task is: goals get an integer `priority` from 1–5, required on create.

Here's the whole job, level by level, so you can see the workflow end to end:

1. **App code first.** Add `priority` to `Goal`, `CreateGoalInput`, `schema.graphqls`, and validation in `GoalService` (`throw` if null or outside 1–5).

2. **Unit tests** (`GoalServiceTest` / `GoalValidationTest`) — the rules:
   - priority = 1 accepted, priority = 5 accepted (boundaries).
   - priority = 0 rejected, priority = 6 rejected, priority = null rejected — each with the right message and `verify(repo, never()).save(...)`.

3. **Integration test** (`GoalCreationIntegrationTest`) — the contract:
   - one `createGoal` mutation that sends `priority` and asserts it comes back in the response.
   - one mutation with `priority: 6` that asserts the error is classified `ValidationError`.
   *(You do NOT repeat all four boundary cases here — the unit test owns those.)*

4. **E2E test** (`test_goals_e2e.py`) — only if it matters to a journey:
   - probably just extend an existing "create goal with all fields" test to include `priority`. You likely don't need a brand-new E2E test for one field.

5. **Frontend** — see `docs/frontend-testing-guide.md` (the form and the data mapping).

If you can do the above without anyone telling you which file to open, this guide did its job.

---

## 8. Common mistakes (read once, avoid forever)

- **Mocking the repository and thinking you tested the database.** A mock only returns what you told it. Real SQL / cascade behaviour is only proven at the integration (H2) and E2E (PostgreSQL) levels.
- **Only testing the happy path.** Most bugs hide in blank strings, nulls, ids from another parent, unknown type names, and boundary values (`LIMIT` vs `LIMIT + 1`). Test the unhappy paths.
- **Hardcoding a limit number in a boundary test.** Never write `"A".repeat(201)` or `"… must be 200 characters or fewer"`. Derive both the boundary value and the message from the production constant (`GoalService.MAX_GOAL_TITLE_LENGTH`, `ResourceService.MAX_RESOURCE_LABEL_LENGTH`, …) so the test tracks the real limit instead of becoming a second source of truth that drifts. Full rule + example: `docs/testing-guide.md` → *Convention: bind boundary tests to the production constant*.
- **Starting Spring for tiny logic.** If it's `action` → `actions`, a unit test is enough.
- **Only unit-testing a GraphQL contract.** GraphQL binding behaves differently for omitted vs explicit-`null` fields. The frontend depends on that — cover it with an integration test.
- **Testing private methods.** Test through the public method that calls them.
- **Repeating the same idea at three levels.** See section 6.
- **E2E tests that assume an empty database.** The E2E backend is shared and stateful. Use your own created goal and membership checks.

---

## 9. Commands

Run everything (backend unit + integration):
```powershell
cd backend
.\mvnw.cmd test
```

Run only the unit-style service tests (fast):
```powershell
cd backend
.\mvnw.cmd test "-Dtest=GoalValidationTest,GoalServiceTest,RealityServiceTest,ResourceServiceTest,TargetServiceTest,EntityTimestampTest"
```

Run one integration test class:
```powershell
cd backend
.\mvnw.cmd test "-Dtest=ResourceIntegrationTest"
```

Run one single test method:
```powershell
cd backend
.\mvnw.cmd test "-Dtest=TargetServiceTest#rejectsNumericTargetCurrentOnCreate"
```

Run the E2E suite (needs a running backend — see `docs/testing-guide.md`):
```powershell
cd tests-e2e
pip install -r requirements.txt
pytest
```

For the full per-file inventory, see `docs/testing-guide.md`. For *why* each test exists and where coverage stands, see `docs/test-coverage-report.md`.
