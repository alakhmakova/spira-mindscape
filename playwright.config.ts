import { defineConfig, devices } from "@playwright/test";

// E2E config for the Personal Tools sandbox-isolation tests. These run in a real
// Chromium (the security guarantees — opaque origin, CSP, cross-origin blocks —
// only exist in a real browser, not jsdom). A tiny static server gives the
// parent page a true HTTP origin so the cookie-exfil test is meaningful.
const PORT = Number(process.env.PORT) || 4321;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  reporter: "list",
  use: {
    baseURL: `http://localhost:${PORT}`,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "node e2e/static-server.mjs",
    port: PORT,
    reuseExistingServer: !process.env.CI,
  },
});
