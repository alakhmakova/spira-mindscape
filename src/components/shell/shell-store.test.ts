import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import {
  GOALS_SCOPE,
  useResetQueryOnNavigate,
  useShellFilters,
  viewField,
} from "./shell-store";

/** Two goals, because the point of the scoping is that they cannot see each other's answers. */
const GOAL_A = "g1";
const GOAL_B = "g2";

/** Back to a fresh install: nothing pinned, every question at its default. */
function resetStore() {
  window.localStorage.clear();
  useShellFilters.setState({ query: "", views: {}, locked: {} });
}

function stored() {
  return JSON.parse(window.localStorage.getItem("spira:view-prefs") ?? "{}")
    .state;
}

/** What is written down for one scope — `undefined` when nothing is. */
function storedView(key: string): Record<string, unknown> | undefined {
  return stored()?.views?.[key];
}

/**
 * **A goal's lists remember their own arrangement** (owner, 2026-08-31).
 *
 * The report, verbatim: filters set on goal 1's targets with the padlock shut were already on when
 * goal 2 opened — with goal 2's padlock open — and changing them there rewrote goal 1's pinned
 * arrangement. One flat set of fields backed all four lists, so every goal shared one answer.
 */
describe("useShellFilters — one goal's lists are its own", () => {
  beforeEach(resetStore);

  it("does not carry one goal's filter into another goal", () => {
    useShellFilters.getState().setView({ targetStatus: "done" }, GOAL_A);

    expect(viewField("targetStatus", GOAL_A)).toBe("done");
    expect(viewField("targetStatus", GOAL_B)).toBe("not-done");
  });

  it("does not let another goal write through a closed padlock", () => {
    useShellFilters.getState().setView({ targetStatus: "done" }, GOAL_A);
    useShellFilters.getState().setLocked("targets", GOAL_A, true);

    useShellFilters.getState().setView({ targetStatus: "started" }, GOAL_B);

    expect(viewField("targetStatus", GOAL_A)).toBe("done");
    expect(storedView("targets:g1")?.targetStatus).toBe("done");
  });

  it("gives each goal its own padlock", () => {
    useShellFilters.getState().setView({ targetType: "checklist" }, GOAL_A);
    useShellFilters.getState().setView({ targetType: "numeric" }, GOAL_B);

    useShellFilters.getState().setLocked("targets", GOAL_A, true);

    expect(storedView("targets:g1")?.targetType).toBe("checklist");
    expect(storedView("targets:g2")).toBeUndefined();
  });

  it("scopes options and resources per goal too", () => {
    useShellFilters.getState().setView({ optionLean: "good_idea" }, GOAL_A);
    useShellFilters.getState().setView({ resourceType: "note" }, GOAL_A);

    expect(viewField("optionLean", GOAL_B)).toBe("all");
    expect(viewField("resourceType", GOAL_B)).toBe("all");
  });

  it("resets one goal's list without touching another's", () => {
    useShellFilters.getState().setView({ targetType: "checklist" }, GOAL_A);
    useShellFilters.getState().setView({ targetType: "numeric" }, GOAL_B);

    useShellFilters.getState().resetList("targets", GOAL_A);

    expect(viewField("targetType", GOAL_A)).toBe("all");
    expect(viewField("targetType", GOAL_B)).toBe("numeric");
  });

  /** The All-goals list belongs to the app, so it answers the same whichever goal is open. */
  it("keeps the dashboard's own list app-wide", () => {
    useShellFilters.getState().setView({ sort: "deadline" }, GOALS_SCOPE);

    expect(viewField("sort", GOALS_SCOPE)).toBe("deadline");
  });
});

/**
 * **A closed padlock pins a list's filters and sort; an open one lets them go** (owner,
 * 2026-08-21). Nothing is written while a list is unlocked, which is what makes an unpinned list
 * open on its defaults — and what makes opening the padlock enough to forget.
 */
describe("useShellFilters — the padlock", () => {
  beforeEach(resetStore);

  it("writes nothing while the padlock is open", () => {
    useShellFilters
      .getState()
      .setView({ sort: "deadline", confidence: "7" }, GOALS_SCOPE);

    expect(storedView("goals")).toBeUndefined();
  });

  it("writes the list's whole arrangement once the padlock closes", () => {
    useShellFilters
      .getState()
      .setView({ sort: "deadline", confidence: "7" }, GOALS_SCOPE);

    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);

    expect(storedView("goals")?.sort).toBe("deadline");
    expect(storedView("goals")?.confidence).toBe("7");
    expect(stored().locked.goals).toBe(true);
  });

  it("keeps writing every later change, so it holds what is on screen", () => {
    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);

    useShellFilters.getState().setView({ status: "achieved" }, GOALS_SCOPE);

    expect(storedView("goals")?.status).toBe("achieved");
  });

  it("drops what it was holding when the padlock opens again", () => {
    useShellFilters.getState().setView({ sort: "deadline" }, GOALS_SCOPE);
    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);
    expect(storedView("goals")?.sort).toBe("deadline");

    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, false);

    expect(storedView("goals")).toBeUndefined();
    // Unlocking is not a reset: what is on screen stays exactly as it was.
    expect(viewField("sort", GOALS_SCOPE)).toBe("deadline");
  });

  /**
   * **The date range is pinned like every other question** (owner, 2026-08-22). It was exempt for
   * a day, and the owner found the exemption the way users find this class of bug: she set a
   * range, shut the padlock, left the app and came back to an empty field with the padlock still
   * closed. Nothing on screen had said the range was not covered.
   */
  it("pins the deadline range, so the padlock keeps the whole arrangement", () => {
    useShellFilters
      .getState()
      .setView({ sort: "deadline", deadlineFrom: "2026-08-15" }, GOALS_SCOPE);

    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);

    expect(storedView("goals")?.deadlineFrom).toBe("2026-08-15");
  });

  it("pins the targets range too", () => {
    useShellFilters
      .getState()
      .setView({ targetDeadlineTo: "2026-09-30" }, GOAL_A);

    useShellFilters.getState().setLocked("targets", GOAL_A, true);

    expect(storedView("targets:g1")?.targetDeadlineTo).toBe("2026-09-30");
  });

  it("pins one list without pinning another", () => {
    useShellFilters.getState().setView({ sort: "deadline" }, GOALS_SCOPE);
    useShellFilters.getState().setView({ targetType: "checklist" }, GOAL_A);

    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);

    expect(storedView("goals")?.sort).toBe("deadline");
    expect(storedView("targets:g1")).toBeUndefined();
  });

  it("each of the four lists has its own padlock", () => {
    useShellFilters.getState().setView({ optionLean: "good_idea" }, GOAL_A);
    useShellFilters.getState().setView({ resourceType: "note" }, GOAL_A);

    useShellFilters.getState().setLocked("options", GOAL_A, true);

    expect(storedView("options:g1")?.optionLean).toBe("good_idea");
    expect(storedView("resources:g1")).toBeUndefined();
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
      JSON.stringify({ state, version: 2 }),
    );
    useShellFilters.persist.rehydrate();
  }

  it("restores a pinned list", () => {
    rehydrate({
      views: {
        goals: { sort: "deadline", confidence: "7", status: "achieved" },
      },
      locked: { goals: true },
    });

    expect(viewField("sort", GOALS_SCOPE)).toBe("deadline");
    expect(viewField("confidence", GOALS_SCOPE)).toBe("7");
    expect(viewField("status", GOALS_SCOPE)).toBe("achieved");
  });

  it("restores each goal's own arrangement", () => {
    rehydrate({
      views: {
        "targets:g1": { targetType: "checklist" },
        "targets:g2": { targetType: "numeric" },
      },
      locked: { "targets:g1": true, "targets:g2": true },
    });

    expect(viewField("targetType", GOAL_A)).toBe("checklist");
    expect(viewField("targetType", GOAL_B)).toBe("numeric");
  });

  it("ignores stored fields whose padlock is open", () => {
    rehydrate({
      views: {
        goals: { sort: "deadline" },
        "targets:g1": { targetType: "checklist" },
      },
      locked: { goals: true },
    });

    expect(viewField("sort", GOALS_SCOPE)).toBe("deadline");
    expect(viewField("targetType", GOAL_A)).toBe("all");
  });

  it("restores a pinned range", () => {
    rehydrate({
      views: {
        goals: { deadlineFrom: "2026-08-15", deadlineTo: "2026-08-31" },
      },
      locked: { goals: true },
    });

    expect(viewField("deadlineFrom", GOALS_SCOPE)).toBe("2026-08-15");
    expect(viewField("deadlineTo", GOALS_SCOPE)).toBe("2026-08-31");
  });

  it("opens on the defaults when nothing is pinned", () => {
    rehydrate({ views: { goals: { sort: "deadline", status: "achieved" } } });

    expect(viewField("sort", GOALS_SCOPE)).toBe("recent");
    expect(viewField("status", GOALS_SCOPE)).toBe("not-achieved");
  });

  /**
   * v1 held one flat arrangement shared by every goal — the bug this scoping fixes. A single
   * global answer cannot honestly be attributed to any one goal, so nothing carries forward but
   * the view mode, which is not a filter and has no padlock over it.
   */
  function rehydrateV1(state: Record<string, unknown>) {
    window.localStorage.setItem(
      "spira:view-prefs",
      JSON.stringify({ state, version: 1 }),
    );
    useShellFilters.persist.rehydrate();
  }

  it("drops a v1 goal-owned list, and keeps the view mode", () => {
    rehydrateV1({
      targetStatus: "done",
      viewMode: "table",
      locked: { goals: false, targets: true },
    });

    expect(viewField("targetStatus", GOAL_A)).toBe("not-done");
    expect(useShellFilters.getState().viewMode).toBe("table");
  });

  /**
   * **The All-goals list comes across intact.** It was never part of the defect — it is app-wide,
   * there is only one of it, and its v1 fields mean exactly what they mean now. Losing a pinned
   * dashboard arrangement would be the failure the padlock spec names, inflicted by the fix for a
   * different list.
   */
  it("carries a pinned v1 All-goals arrangement across, padlock included", () => {
    rehydrateV1({
      sort: "deadline",
      status: "achieved",
      deadlineFrom: "2026-08-15",
      targetStatus: "done",
      locked: { goals: true, targets: true },
    });

    expect(viewField("sort", GOALS_SCOPE)).toBe("deadline");
    expect(viewField("status", GOALS_SCOPE)).toBe("achieved");
    expect(viewField("deadlineFrom", GOALS_SCOPE)).toBe("2026-08-15");
    expect(useShellFilters.getState().locked.goals).toBe(true);
    // …and the goal-owned list beside it is still dropped.
    expect(viewField("targetStatus", GOAL_A)).toBe("not-done");
  });

  it("carries nothing across when the v1 padlock was open", () => {
    rehydrateV1({ sort: "deadline", locked: { goals: false } });

    expect(viewField("sort", GOALS_SCOPE)).toBe("recent");
  });
});

/**
 * A goal-owned list must never fall back to one shared scope — that bare `targets:` bucket is the
 * defect itself. A caller with no goal id (a screen still loading, or `GOALS_SCOPE` pasted into a
 * goal-scoped call) gets an inert scope of its own.
 */
describe("useShellFilters — a missing goal id", () => {
  beforeEach(resetStore);

  it("does not put a goal-less caller in the same scope as a real goal", () => {
    useShellFilters.getState().setView({ targetStatus: "done" }, null);

    expect(viewField("targetStatus", GOAL_A)).toBe("not-done");
    expect(viewField("targetStatus", "")).toBe("done");
    expect(Object.keys(useShellFilters.getState().views)).toEqual([
      "targets:none",
    ]);
  });
});

/**
 * **Reset all resets all** (owner, 2026-08-21). The status questions used to be exempt, so a
 * button promising everything quietly kept four answers.
 */
describe("useShellFilters — Reset all", () => {
  beforeEach(resetStore);

  it("clears every question the goals panel asks, the statuses included", () => {
    useShellFilters.getState().setView(
      {
        sort: "title",
        sortDirection: "asc",
        deadlineFrom: "2026-08-01",
        confidence: "7",
        status: "achieved",
        goalDeadline: "none",
      },
      GOALS_SCOPE,
    );

    useShellFilters.getState().resetList("goals", GOALS_SCOPE);

    expect(viewField("sort", GOALS_SCOPE)).toBe("recent");
    expect(viewField("sortDirection", GOALS_SCOPE)).toBe("desc");
    expect(viewField("deadlineFrom", GOALS_SCOPE)).toBe("");
    expect(viewField("confidence", GOALS_SCOPE)).toBe("");
    expect(viewField("status", GOALS_SCOPE)).toBe("not-achieved");
    expect(viewField("goalDeadline", GOALS_SCOPE)).toBe("all");
  });

  it("clears every question the targets panel asks", () => {
    useShellFilters.getState().setView(
      {
        targetSort: "created",
        targetSortDesc: true,
        targetStatus: "done",
        targetDeadline: "overdue",
        targetLock: "locked",
        targetType: "checklist",
        targetDeadlineFrom: "2026-08-01",
      },
      GOAL_A,
    );

    useShellFilters.getState().resetList("targets", GOAL_A);

    expect(viewField("targetSort", GOAL_A)).toBe("deadline");
    expect(viewField("targetSortDesc", GOAL_A)).toBe(false);
    expect(viewField("targetStatus", GOAL_A)).toBe("not-done");
    expect(viewField("targetDeadline", GOAL_A)).toBe("all");
    expect(viewField("targetLock", GOAL_A)).toBe("all");
    expect(viewField("targetType", GOAL_A)).toBe("all");
    expect(viewField("targetDeadlineFrom", GOAL_A)).toBe("");
  });

  it("leaves the other lists alone", () => {
    useShellFilters.getState().setView({ status: "achieved" }, GOALS_SCOPE);
    useShellFilters.getState().setView({ optionLean: "good_idea" }, GOAL_A);

    useShellFilters.getState().resetList("goals", GOALS_SCOPE);

    expect(viewField("optionLean", GOAL_A)).toBe("good_idea");
  });

  it("a reset while pinned is written through, so it survives the reload too", () => {
    useShellFilters.getState().setView({ status: "achieved" }, GOALS_SCOPE);
    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);

    useShellFilters.getState().resetList("goals", GOALS_SCOPE);

    expect(storedView("goals")?.status).toBeUndefined();
  });

  // The range is pinned now, so clearing it has to reach storage as well — otherwise "Reset all"
  // would empty the field on screen and the old range would come back on the next visit.
  it("clears a pinned range in storage as well as on screen", () => {
    useShellFilters
      .getState()
      .setView({ deadlineFrom: "2026-08-15" }, GOALS_SCOPE);
    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);
    expect(storedView("goals")?.deadlineFrom).toBe("2026-08-15");

    useShellFilters.getState().resetList("goals", GOALS_SCOPE);
    useShellFilters.persist.rehydrate();

    expect(viewField("deadlineFrom", GOALS_SCOPE)).toBe("");
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
    useShellFilters.getState().setLocked("goals", GOALS_SCOPE, true);
    useShellFilters.getState().setQuery("interview");

    expect(stored().query).toBeUndefined();
  });
});
