import { defineConfig, devices } from "@playwright/test";

/**
 * Web E2E for Spira. These tests drive the real SPA + backend.
 *
 * **Locally** (`npm run test:e2e`): bring the stack up first — Docker Postgres + Spring Boot on
 * the `local` profile (auto-logs-in `dev@local`, so no Google sign-in) + Vite. See the
 * `run-spira` skill (`.claude/skills/run-spira/SKILL.md`) or README.
 *
 * **In CI** (the `web-e2e` job): the backend runs under the `e2e` profile instead, which has no
 * auto-login — it authenticates a request when it carries an `X-E2E-Auth: <email>` header
 * (`E2eTestAuthFilter`, the same mechanism the Python E2E suite uses). Setting `SPIRA_E2E_AUTH`
 * makes every browser request send that header, so the SPA loads authenticated.
 */
const e2eAuthEmail = process.env.SPIRA_E2E_AUTH;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  // The app runs a background goals-refresh poll that can re-render and detach elements
  // mid-interaction; retry flaky UI steps rather than disabling the product behaviour.
  retries: 2,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    // Only set in CI (see above); locally the `local` profile authenticates by session cookie.
    ...(e2eAuthEmail
      ? { extraHTTPHeaders: { "X-E2E-Auth": e2eAuthEmail } }
      : {}),
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Vite serves the SPA and proxies /graphql + /api to the backend on :8080. Locally we reuse an
  // already-running dev server; in CI we always start a fresh one.
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
