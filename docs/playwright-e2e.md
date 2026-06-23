# End-to-End Tests with Playwright (beginner guide)

This project uses **Playwright** for end-to-end (e2e) tests that need a *real
browser*. Right now there is exactly one suite: it proves the Personal Tools
**sandbox** (AI-written render code) is truly isolated. This guide explains what
Playwright is, how it's set up here, what the tests check and why, and how to
reproduce the setup in another project.

> New to the sandbox itself? Read [`ai-tools-sandbox-plan.md`](./ai-tools-sandbox-plan.md) first.

---

## 1. What Playwright is (and why, not just Vitest)

- **Vitest** (our unit/component tests, under `src/**`) runs in **jsdom** — a
  *fake* DOM in Node. It's fast, but it does **not** enforce real browser
  security: iframe sandboxing, cross-origin blocks, CSP, and cookies are not
  real there. So a jsdom test could "pass" while the real browser leaks.
- **Playwright** launches a **real Chromium** and drives a real page. The
  sandbox's guarantees (opaque origin, CSP, same-origin policy) only exist in a
  real browser — so the isolation tests *must* run here, not in Vitest.
- These are **plain scripted tests**: ordinary code executed by the Playwright
  runner. No AI is involved at runtime. (In the test we *write* hostile "AI"
  render code by hand to attack our own sandbox.)

---

## 2. How it's set up in this repo

Files:

| File | What it is |
|---|---|
| `playwright.config.ts` | Playwright config: test dir `e2e/`, a Chromium project, and a `webServer` that boots the static server below before tests. |
| `e2e/static-server.mjs` | A ~10-line Node HTTP server serving a blank page on `http://localhost:4321`. Gives the parent page a **real HTTP origin** so the cookie-theft test is meaningful (you can't set real cookies on `about:blank`). |
| `e2e/sandbox-isolation.spec.ts` | The actual tests. |
| `package.json` → `"test:e2e"` | Runs `playwright test`. |

Config choices worth knowing:
- `testDir: "./e2e"` keeps e2e separate from unit tests. We also told **Vitest to
  only look in `src/**`** (`vite.config.ts` → `test.include`) so it never tries
  to run these Playwright files (the two runners are incompatible).
- ESLint **ignores `e2e/**`** (`eslint.config.js`) — e2e has its own runner/types.
- `webServer.reuseExistingServer` is on locally so re-runs are fast.

### One-time install
Playwright is a dev dependency (`@playwright/test`). The browser binary is
downloaded once:
```bash
npm install            # installs @playwright/test (already in package.json)
npx playwright install chromium   # downloads the matching Chromium build
```
> Heads-up: each Playwright version expects a specific Chromium build number. If
> you upgrade `@playwright/test`, re-run `npx playwright install chromium`, or
> you'll see "Executable doesn't exist … run npx playwright install".

### Running
```bash
npm run test:e2e            # all e2e tests (headless)
npx playwright test --headed          # watch it in a real window
npx playwright test --ui              # interactive UI mode
npx playwright show-report            # open the HTML report after a run
```

---

## 3. The tests (what + why)

File: `e2e/sandbox-isolation.spec.ts`.

**How they work.** Each test, in a real page on `http://localhost:4321`:
1. puts a secret cookie on the parent (`secret=TOPSECRET`),
2. mounts the **real** sandbox iframe using `buildSrcdoc()` from the app code,
3. sends it `init` with a **hostile** render module (the "probe"),
4. the probe tries to escape, then reports what it managed to grab — through the
   **only** channel it has, `ctx.addRow(...)` — which the test reads back.

This mirrors production exactly: if the probe (pretending to be malicious AI
code) can't escape, neither can real AI code.

| Test | What it asserts | Why it matters |
|---|---|---|
| **cannot read cookies, network, storage, or the parent window** | `document.cookie` never contains the secret; `fetch(...)` throws; `localStorage` throws; `window.parent.location/document` and `window.top` throw (cross-origin) | This is the whole threat model: no data exfiltration, no calling our API as the user, no reaching the rest of the app. |
| **the only data path is the validated bridge** | calling `ctx.addRow({...})` produces a `mutate` message to the parent | Proves the frame can only *request* data changes; the parent (which validates + persists) stays in control. |
| **the iframe is sandboxed WITHOUT `allow-same-origin`** | the mounted iframe's `sandbox` has `allow-scripts` but not `allow-same-origin` | Guards against the single most dangerous misconfiguration — the test fails loudly if anyone ever adds it. |

If any of these ever fails, the sandbox is leaking and the feature must not ship.

---

## 4. The probe trick (how a sandbox reports on itself)

The render module can't `return` to the test and has no network — its *only*
output is `ctx.addRow(data)`. So the probe collects its findings into an object
and "saves" it as a row; the test harness listens for that `mutate` message and
reads the object. Example shape the probe sends back:

```js
{ cookie: "THREW:SecurityError", fetch: "THREW:TypeError",
  parentHref: "THREW:SecurityError", storage: "THREW:SecurityError" }
```
`"THREW:…"` means the escape attempt was blocked (an exception). That's the
*passing* state.

---

## 5. Reproduce in another project (checklist)

1. `npm i -D @playwright/test && npx playwright install chromium`
2. Add a `playwright.config.ts` with a `testDir` and a `webServer` that serves a
   real origin (copy `e2e/static-server.mjs`).
3. If you also use Vitest, scope it to your unit folder (`test.include`) so it
   ignores the Playwright specs, and add the e2e folder to your ESLint ignores.
4. Write specs that mount the thing under test and assert behaviour in the real
   browser. For a sandbox, copy `e2e/sandbox-isolation.spec.ts` and adapt the
   probe.
5. Add `test-results/` and `playwright-report/` to `.gitignore`.
6. In CI, run `npx playwright install --with-deps chromium` then `npm run test:e2e`.

---

## 6. Links to the code

- Tests: `e2e/sandbox-isolation.spec.ts`
- Config: `playwright.config.ts`, `e2e/static-server.mjs`
- What's being tested: `src/components/tools/sandbox/` (`runtime.ts`,
  `protocol.ts`, `ToolSandbox.tsx`, `default-render.ts`)
- Design + rationale: [`ai-tools-sandbox-plan.md`](./ai-tools-sandbox-plan.md)
