import { beforeEach, describe, expect, it } from "vitest";

import { useShellFilters } from "./shell-store";

/**
 * The status filters are user preferences, not filters: they survive a reload and "Reset filters"
 * must leave them alone. Everything else in the store is per-session.
 */
describe("useShellFilters — status is a stored preference", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useShellFilters.setState({
      status: "not-achieved",
      targetStatus: "not-done",
      deadlineFrom: "",
      deadlineTo: "",
      confidence: "",
    });
  });

  it("writes the chosen goal and target status to localStorage", () => {
    useShellFilters.getState().setStatus("achieved");
    useShellFilters.getState().setTargetStatus("all");

    const stored = JSON.parse(
      window.localStorage.getItem("spira:view-prefs") ?? "{}",
    );
    expect(stored.state.status).toBe("achieved");
    expect(stored.state.targetStatus).toBe("all");
  });

  it("never persists the search query or the date ranges", () => {
    useShellFilters.getState().setQuery("interview");
    useShellFilters.getState().setDeadlineFrom("2026-08-01");

    const stored = JSON.parse(
      window.localStorage.getItem("spira:view-prefs") ?? "{}",
    );
    expect(stored.state.query).toBeUndefined();
    expect(stored.state.deadlineFrom).toBeUndefined();
  });

  it("clears the date filters on reset but keeps the status choice", () => {
    useShellFilters.getState().setStatus("achieved");
    useShellFilters.getState().setDeadlineFrom("2026-08-01");
    useShellFilters.getState().setConfidence("7");

    useShellFilters.getState().resetFilters();

    expect(useShellFilters.getState().deadlineFrom).toBe("");
    expect(useShellFilters.getState().confidence).toBe("");
    expect(useShellFilters.getState().status).toBe("achieved");
  });
});
