import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorBoundary } from "./ErrorBoundary";
import { logger } from "../lib/logger";

vi.mock("../lib/logger", () => ({
  logger: {
    reportError: vi.fn(),
    warn: vi.fn(),
    debug: vi.fn(),
    info: vi.fn(),
  },
}));

function Boom(): never {
  throw new Error("component exploded");
}

describe("ErrorBoundary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // React prints every caught error to console.error. Without this the test output
    // is a wall of red that looks like a failure when everything actually passed.
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("renders its children when nothing throws", () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText("All good")).toBeInTheDocument();
    expect(logger.reportError).not.toHaveBeenCalled();
  });

  it("shows the error screen instead of a blank page when a child throws", () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /reload page/i }),
    ).toBeInTheDocument();
  });

  it("reports the error exactly once, as a render error", () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(logger.reportError).toHaveBeenCalledTimes(1);
    expect(logger.reportError).toHaveBeenCalledWith(
      expect.objectContaining({ message: "component exploded" }),
      expect.objectContaining({ kind: "render" }),
    );
  });

  it("includes the component stack, which is what identifies the faulting component", () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    const context = vi.mocked(logger.reportError).mock.calls[0][1];
    expect(context.componentStack).toContain("Boom");
  });
});
