import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import { useResetQueryOnNavigate, useShellFilters } from "./shell-store";

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

/**
 * The dashboard filter and the goal-workspace header switcher share one `query` field, so a
 * search typed on one screen used to follow the user onto the next — filter "All goals", open a
 * result, and the workspace header opened pre-filled with the same text.
 */
describe("useResetQueryOnNavigate", () => {
  beforeEach(() => {
    useShellFilters.setState({ query: "" });
  });

  it("empties the search box when the route changes", () => {
    const { rerender } = renderHook(
      ({ path }: { path: string }) => useResetQueryOnNavigate(path),
      { initialProps: { path: "/" } },
    );

    // The user searches on the dashboard, then opens one of the goals it found.
    act(() => useShellFilters.getState().setQuery("interview"));
    rerender({ path: "/goals/g1" });

    expect(useShellFilters.getState().query).toBe("");
  });

  it("leaves the query alone while the user stays on one screen", () => {
    const { rerender } = renderHook(
      ({ path }: { path: string }) => useResetQueryOnNavigate(path),
      { initialProps: { path: "/" } },
    );

    act(() => useShellFilters.getState().setQuery("interview"));
    rerender({ path: "/" }); // a re-render that is not a navigation

    expect(useShellFilters.getState().query).toBe("interview");
  });
});
