# Frontend Testing Guide (start from zero)

Read this if you have **never written a frontend test** and you want to start testing the Spira frontend. It assumes you can read TypeScript/React but have never used a test runner. Like the backend guide (`docs/unit-vs-integration-tests.md`), this is a practical field guide, not a lecture.

---

## 1. What "frontend testing" even means

The frontend is the part that runs in the browser: the React components you see, plus the plain TypeScript that calculates things and talks to the backend.

Testing it means writing small programs that **run your frontend code automatically and check it does the right thing** — so you don't have to click through the app by hand every time you change something.

There are three levels, same idea as the backend pyramid:

| Level | The question it answers | Needs a browser? | Status in this project |
|---|---|---|---|
| **Logic unit test** | "Does this pure TypeScript function compute the right value?" | No | ✅ Exists (4 files) |
| **Component test** | "When I render this UI and click it, does it behave correctly?" | No (a simulated DOM) | ❌ Not yet — the main gap |
| **Browser E2E** | "Does the whole real app work in a real browser?" | Yes (Chromium etc.) | ❌ Not yet |

The tool we use is **Vitest** (already installed). It is the test runner — the frontend equivalent of JUnit on the backend. You write tests, Vitest runs them and reports pass/fail.

Run all frontend tests from the repo root:
```powershell
npm.cmd test
```
Run one file while you work on it:
```powershell
npm.cmd test -- --run src/lib/spira/progress.test.ts
```

---

## 2. The vocabulary you need (and nothing more)

Frontend tests use a few words a lot. Here they are once:

| Word | What it means | Backend equivalent you already know |
|---|---|---|
| `describe("name", () => {...})` | Groups related tests | a test class |
| `it("does X", () => {...})` (or `test(...)`) | One test | a `@Test` method |
| `expect(value).toBe(...)` | An assertion | `assertThat(value).isEqualTo(...)` |
| `toBeCloseTo(x)` | Assertion for floats (with tolerance) | `isCloseTo(x, offset(...))` |
| **mock / spy** | A fake version of something (e.g. the network) so the test is controlled | Mockito `@Mock` |
| `vi.fn()`, `vi.spyOn(...)`, `vi.stubGlobal(...)` | Vitest's ways to create those fakes | `mock(...)`, `when(...).thenReturn(...)` |
| **fixture** | A reusable object/setup for tests | a helper factory method |

That's enough to read every test in this project.

---

## 3. What is ALREADY tested (and what those tests actually do)

All current frontend tests live in one folder and are **logic unit tests** — they test pure TypeScript, no UI rendering, no browser:

```text
src/lib/spira/progress.test.ts        ← progress math
src/lib/spira/api.test.ts             ← talking to the backend (error handling, request shape)
src/lib/spira/api.contract.test.ts    ← mapping backend responses into app objects
src/lib/spira/store.test.ts           ← the state store (optimistic updates, rollback)
```

Let's look at what each *kind* proves, with real snippets so you recognize the patterns.

### 3a. Pure calculation (`progress.test.ts`)
This is the simplest kind: give a function an input, check the output. It mirrors the backend's `TargetServiceTest`.

```ts
it("calculates checklist progress from completed items", () => {
  expect(
    targetProgress({
      id: "checklist", type: "checklist", title: "Prepare workspace",
      items: [
        { id: "1", text: "Write requirements", done: true },
        { id: "2", text: "Review validation", done: false },
        { id: "3", text: "Run tests", done: true },
      ],
    }),
  ).toBeCloseTo(2 / 3);   // 2 of 3 done
});
```
No mocks, no setup — just "function in, value out." When you write a new pure helper, this is the pattern.

### 3b. The network layer (`api.test.ts`)
Here the test **fakes the network** so it can control what the backend "returns," then checks how our code reacts. The fake is `vi.stubGlobal("fetch", ...)`:

```ts
it("uses a safe backend-unavailable message for network failures", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("Failed to fetch"); }));

  await expect(spiraApi.fetchGoals()).rejects.toMatchObject({
    message: "We couldn't reach the backend. Check that it is running, then retry.",
  });
});
```
This proves a *user-facing* rule: when the backend is down, the user sees a friendly message, not a raw stack trace. Note `async`/`await` — network code is asynchronous, so tests are too.

### 3c. The state store (`store.test.ts`)
The store holds the app's data and does **optimistic updates**: it changes the screen immediately, then talks to the backend, and **rolls back** if the backend rejects it. The test fakes the API and checks the rollback:

```ts
it("rolls back an oversized note create and shows the validation message", async () => {
  vi.spyOn(spiraApi, "createResource").mockRejectedValue(new SpiraApiError(BODY_LIMIT_MESSAGE));

  useSpira.getState().addResource("goal-1", { type: "note", title: "Oversized note", body: "A".repeat(50_001) });

  await vi.waitFor(() => {
    // the optimistic note was removed again after the backend said no
    expect(useSpira.getState().goals[0].resources.some(r => r.title === "Oversized note")).toBe(false);
  });
  expect(useSpira.getState().syncError).toBe(BODY_LIMIT_MESSAGE);
});
```
This is the most "advanced" pattern here (fake timers, async waiting), but the idea is the same: control the dependency, trigger the behaviour, assert the outcome.

**Takeaway:** the current tests cover the frontend's *brain* — math, networking, and state logic. They do **not** cover what the user sees and clicks. That's the next two levels.

---

## 4. The gap: component tests (this is where to start)

A **component test** renders a real React component into a simulated page (in memory, no real browser), then interacts with it like a user and checks the result.

This is the highest-value thing missing from the frontend. It catches bugs the logic tests can't: a button that does nothing, a form that doesn't show its error, a list that renders blank.

### What you'd need (one-time setup)
Two libraries that are **not yet installed**:
```powershell
npm.cmd install -D @testing-library/react @testing-library/user-event jsdom
```
Then tell Vitest to simulate a browser DOM (in `vite.config.ts`):
```ts
// inside defineConfig({ ... })
test: { environment: "jsdom" }
```
`jsdom` is a fake DOM so React can "render" without a real browser. React Testing Library (RTL) gives you `render`, `screen`, and ways to find and click things the way a user would.

### What a component test looks like (illustrative)
For a goal-creation form, a test would read roughly like:
```ts
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

it("shows an error when the title is blank", async () => {
  render(<GoalForm />);
  await userEvent.click(screen.getByRole("button", { name: /create/i }));
  expect(screen.getByText(/title is required/i)).toBeInTheDocument();
});
```
You render the component, act like a user (click), and assert what's now on screen.

### Where to LOOK to find component-test candidates
Open the React components (the `.tsx` files under `src/`). Good first targets are the small, high-value pieces of UI:
- the **goal creation/edit form** (validation messages, required fields),
- the **target list / target row** (clicking "done" updates the shown progress),
- the **options list** (add, select, reorder).

### What to test at the component level
- A user can't submit invalid input, and sees the error message.
- Clicking something changes what's displayed (a checkbox flips progress).
- A list renders the items it's given, and an empty state when given none.

### What NOT to test at the component level
- **Don't re-test the math.** `targetProgress` is already unit-tested; don't re-prove the formula through the UI — just check the UI *shows* the number.
- **Don't hit the real backend.** Fake the API (like `store.test.ts` does), so the test is fast and deterministic.
- **Don't test the design/CSS pixels.** Test behaviour and visible text/roles, not colours.

---

## 5. The top: browser E2E (later, and few)

A browser E2E test launches the **real app in a real browser** and clicks through it. The tool would be **Playwright** (recommended today) or Cypress — neither is installed yet.

This is the frontend twin of the Python `tests-e2e/` suite: slow, broad, and you want **very few** of them — only the most important journeys, e.g. "open the app → create a goal → add a target → see progress update."

Add these only after component tests exist, and keep them to a handful. They are the slowest and most fragile, so they earn their place only for the critical end-to-end flow.

---

## 6. Your decision cheat-sheet (frontend)

| What you changed | Test to write | Tool |
|---|---|---|
| A pure function (math, formatting, mapping data) | Logic unit test next to it (`*.test.ts`) | Vitest |
| How the app calls the backend or handles its errors | Unit test with a faked `fetch`/API | Vitest + `vi` |
| A React component's behaviour (forms, clicks, what's shown) | **Component test** | Vitest + React Testing Library *(needs setup)* |
| A full user journey through the real UI | Browser E2E (sparingly) | Playwright *(needs setup)* |

Same golden rule as the backend (`docs/unit-vs-integration-tests.md` §6): **test the detail at the lowest level that can see it; smoke the wiring once higher up.** Don't re-prove the progress formula in a component test or an E2E test — prove it once in `progress.test.ts`.

---

## 7. A realistic first task for you

Want to learn by doing? A good first contribution:

1. Install the three dev libraries in §4 and add `environment: "jsdom"` to `vite.config.ts`.
2. Pick the **goal creation form** component.
3. Write two component tests: (a) submitting a blank title shows the required-message; (b) submitting a valid goal calls the create handler.
4. Run `npm.cmd test` and watch them pass.

If those two tests pass, you've covered the single biggest gap in the frontend and you now know the pattern for every other component.

---

## 8. Commands recap

```powershell
# from the repo root
npm.cmd test                                   # run all frontend tests once
npm.cmd test -- --run src/lib/spira/api.test.ts  # run one file
npm.cmd run build                              # type-check + build (CI runs this too)
```

The frontend tests also run automatically in CI (the "Frontend tests and build" job) — see `docs/github-actions-ci.md`.
