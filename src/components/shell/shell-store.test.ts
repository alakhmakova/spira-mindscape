import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import { useResetQueryOnNavigate, useShellFilters } from "./shell-store";

/** Back to a fresh install: nothing pinned, every question at its default. */
function resetStore() {
  window.localStorage.clear();
  useShellFilters.setState({
    query: "",
    sort: "recent",
    sortDirection: "desc",
    deadlineFrom: "",
    deadlineTo: "",
    confidence: "",
    status: "not-achieved",
    goalDeadline: "all",
    targetSort: "deadline",
    targetSortDesc: false,
    targetStatus: "not-done",
    targetDeadline: "all",
    targetLock: "all",
    targetType: "all",
    targetDeadlineFrom: "",
    targetDeadlineTo: "",
    optionLean: "all",
    resourceSort: "recent",
    resourceType: "all",
    locked: {
      goals: false,
      targets: false,
      options: false,
      resources: false,
    },
  });
}

function stored() {
  return JSON.parse(window.localStorage.getItem("spira:view-prefs") ?? "{}")
    .state;
}

/**
 * **A closed padlock pins a list's filters and sort; an open one lets them go** (owner,
 * 2026-08-21). Nothing is written while a list is unlocked, which is what makes an unpinned list
 * open on its defaults — and what makes opening the padlock enough to forget.
 */
describe("useShellFilters — the padlock", () => {
  beforeEach(resetStore);

  it("writes nothing while the padlock is open", () => {
    useShellFilters.getState().setView({ sort: "deadline", confidence: "7" });

    expect(stored().sort).toBeUndefined();
    expect(stored().confidence).toBeUndefined();
  });

  it("writes the list's whole arrangement once the padlock closes", () => {
    useShellFilters.getState().setView({ sort: "deadline", confidence: "7" });

    useShellFilters.getState().setLocked("goals", true);

    expect(stored().sort).toBe("deadline");
    expect(stored().confidence).toBe("7");
    expect(stored().locked.goals).toBe(true);
  });

  it("keeps writing every later change, so it holds what is on screen", () => {
    useShellFilters.getState().setLocked("goals", true);

    useShellFilters.getState().setView({ status: "achieved" });

    expect(stored().status).toBe("achieved");
  });

  it("drops what it was holding when the padlock opens again", () => {
    useShellFilters.getState().setView({ sort: "deadline" });
    useShellFilters.getState().setLocked("goals", true);
    expect(stored().sort).toBe("deadline");

    useShellFilters.getState().setLocked("goals", false);

    expect(stored().sort).toBeUndefined();
    // Unlocking is not a reset: what is on screen stays exactly as it was.
    expect(useShellFilters.getState().sort).toBe("deadline");
  });

  it("pins one list without pinning another", () => {
    useShellFilters
      .getState()
      .setView({ sort: "deadline", targetType: "checklist" });

    useShellFilters.getState().setLocked("goals", true);

    expect(stored().sort).toBe("deadline");
    expect(stored().targetType).toBeUndefined();
  });

  it("each of the four lists has its own padlock", () => {
    useShellFilters.getState().setView({ optionLean: "good_idea" });
    useShellFilters.getState().setView({ resourceType: "note" });

    useShellFilters.getState().setLocked("options", true);

    expect(stored().optionLean).toBe("good_idea");
    expect(stored().resourceType).toBeUndefined();
  });
});

/**
 * What a fresh session sees. Only a closed padlock's fields come back; everything else opens at
 * its default rather than at whatever an older build left in storage.
 */
describe("useShellFilters — waking up", () => {
  beforeEach(resetStore);

  function rehydrate(state: Record<string, unknown>) {
    window.localStorage.setItem(
      "spira:view-prefs",
      JSON.stringify({ state, version: 1 }),
    );
    useShellFilters.persist.rehydrate();
    return useShellFilters.getState();
  }

  it("restores a pinned list", () => {
    const s = rehydrate({
      sort: "deadline",
      confidence: "7",
      status: "achieved",
      locked: { goals: true, targets: false, options: false, resources: false },
    });

    expect(s.sort).toBe("deadline");
    expect(s.confidence).toBe("7");
    expect(s.status).toBe("achieved");
  });

  it("ignores stored fields whose padlock is open", () => {
    const s = rehydrate({
      sort: "deadline",
      targetType: "checklist",
      locked: { goals: true, targets: false, options: false, resources: false },
    });

    expect(s.sort).toBe("deadline");
    expect(s.targetType).toBe("all");
  });

  it("opens on the defaults when nothing is pinned", () => {
    const s = rehydrate({ sort: "deadline", status: "achieved" });

    expect(s.sort).toBe("recent");
    expect(s.status).toBe("not-achieved");
  });
});

/**
 * **Reset all resets all** (owner, 2026-08-21). The status questions used to be exempt, so a
 * button promising everything quietly kept four answers.
 */
describe("useShellFilters — Reset all", () => {
  beforeEach(resetStore);

  it("clears every question the goals panel asks, the statuses included", () => {
    useShellFilters.getState().setView({
      sort: "title",
      sortDirection: "asc",
      deadlineFrom: "2026-08-01",
      confidence: "7",
      status: "achieved",
      goalDeadline: "none",
    });

    useShellFilters.getState().resetList("goals");

    const s = useShellFilters.getState();
    expect(s.sort).toBe("recent");
    expect(s.sortDirection).toBe("desc");
    expect(s.deadlineFrom).toBe("");
    expect(s.confidence).toBe("");
    expect(s.status).toBe("not-achieved");
    expect(s.goalDeadline).toBe("all");
  });

  it("clears every question the targets panel asks", () => {
    useShellFilters.getState().setView({
      targetSort: "created",
      targetSortDesc: true,
      targetStatus: "done",
      targetDeadline: "overdue",
      targetLock: "locked",
      targetType: "checklist",
      targetDeadlineFrom: "2026-08-01",
    });

    useShellFilters.getState().resetList("targets");

    const s = useShellFilters.getState();
    expect(s.targetSort).toBe("deadline");
    expect(s.targetSortDesc).toBe(false);
    expect(s.targetStatus).toBe("not-done");
    expect(s.targetDeadline).toBe("all");
    expect(s.targetLock).toBe("all");
    expect(s.targetType).toBe("all");
    expect(s.targetDeadlineFrom).toBe("");
  });

  it("leaves the other lists alone", () => {
    useShellFilters
      .getState()
      .setView({ status: "achieved", optionLean: "good_idea" });

    useShellFilters.getState().resetList("goals");

    expect(useShellFilters.getState().optionLean).toBe("good_idea");
  });

  it("a reset while pinned is written through, so it survives the reload too", () => {
    useShellFilters.getState().setView({ status: "achieved" });
    useShellFilters.getState().setLocked("goals", true);

    useShellFilters.getState().resetList("goals");

    expect(stored().status).toBe("not-achieved");
  });
});

/**
 * The dashboard filter and the goal-workspace header switcher share one `query` field, so a
 * search typed on one screen used to follow the user onto the next.
 */
describe("useResetQueryOnNavigate", () => {
  beforeEach(resetStore);

  it("empties the search box when the route changes", () => {
    const { rerender } = renderHook(
      ({ path }: { path: string }) => useResetQueryOnNavigate(path),
      { initialProps: { path: "/" } },
    );

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

  it("is never pinned, whatever the padlocks say", () => {
    useShellFilters.getState().setLocked("goals", true);
    useShellFilters.getState().setQuery("interview");

    expect(stored().query).toBeUndefined();
  });
});
