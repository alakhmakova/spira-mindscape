import { defineConfig, devices } from "@playwright/test";

/**
 * Web E2E for Spira. These tests drive the real SPA + backend, so the full local stack
 * must be running first (see docs/changing-fonts.md's sibling `.claude/skills/run-spira`,
 * or README): Docker Postgres + Spring Boot on the `local` profile (auto-logs-in dev@local,
 * so no Google sign-in is needed) + Vite. Run with `npm run test:e2e`.
 */
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
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Reuse a Vite dev server if one is already up; otherwise start it. The backend +
  // Postgres are NOT started here (they need Docker) — bring them up separately.
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
