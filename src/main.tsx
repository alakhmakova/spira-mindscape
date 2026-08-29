import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "@tanstack/react-router";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { logger } from "./lib/logger";
import { getRouter } from "./router";
import { trackViewportHeight } from "./lib/spira/sheet-height";
import "./styles.css";

// React only sees errors thrown during render. Everything else — an event handler, a
// setTimeout, a promise nobody awaited — escapes every boundary, which is how the app
// could break for a user and leave no trace anywhere. These two listeners are what make
// those reachable; the logger drops the ones that are expected (offline, 401).
window.addEventListener("error", (event) => {
  logger.reportError(event.error ?? new Error(event.message), {
    kind: "window-error",
  });
});
window.addEventListener("unhandledrejection", (event) => {
  logger.reportError(event.reason, { kind: "unhandled-rejection" });
});

// Sheets are sized from `--app-vh` rather than from `vh`, so the on-screen keyboard
// cannot shrink them — see `lib/spira/sheet-height.ts`.
trackViewportHeight();

const router = getRouter();

const rootElement = document.getElementById("root")!;
createRoot(rootElement).render(
  <StrictMode>
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  </StrictMode>,
);
