import { useEffect } from "react";
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

export type SortKey =
  | "recent"
  | "deadline"
  | "progress"
  | "confidence"
  | "title";
export type SortDirection = "asc" | "desc";
export type GoalStatusFilter = "all" | "achieved" | "not-achieved";
/**
 * The state question for a goal's targets ("done" is the target-side word for achieved).
 *
 * `started` / `not-started` split what "not done" covers: a target with some progress on it is a
 * very different thing from one nobody has touched, and only one of the two is worth a nudge.
 */
export type TargetStatusFilter =
  | "all"
  | "done"
  | "not-done"
  | "started"
  | "not-started";
/** The deadline question. "Overdue" follows the card's rule: past, and not yet achieved. */
export type TargetDeadlineFilter = "all" | "overdue" | "not-overdue" | "none";
/** The padlock question — whether progress is pinned (see `isProgressLocked`). */
export type TargetLockFilter = "all" | "locked" | "unlocked";

type State = {
  query: string;
  sort: SortKey;
  sortDirection: SortDirection;
  deadlineFrom: string;
  deadlineTo: string;
  confidence: string;
  status: GoalStatusFilter;
  targetStatus: TargetStatusFilter;
  targetDeadline: TargetDeadlineFilter;
  targetLock: TargetLockFilter;
  viewMode: "cards" | "table";
  setQuery: (q: string) => void;
  setSort: (s: SortKey) => void;
  setSortDirection: (d: SortDirection) => void;
  resetSort: () => void;
  setDeadlineFrom: (value: string) => void;
  setDeadlineTo: (value: string) => void;
  setConfidence: (value: string) => void;
  setStatus: (value: GoalStatusFilter) => void;
  setTargetStatus: (value: TargetStatusFilter) => void;
  setTargetDeadline: (value: TargetDeadlineFilter) => void;
  setTargetLock: (value: TargetLockFilter) => void;
  resetFilters: () => void;
  setViewMode: (v: "cards" | "table") => void;
};

/**
 * View state for the goals overview and a goal's targets.
 *
 * The **status filters and the view mode are preferences**: they are persisted and only ever
 * change when the user changes them — navigating, reloading and "Reset filters" all leave them
 * alone. The search query and the date ranges are deliberately NOT persisted; they start empty
 * every session and are what "Reset filters" clears.
 */
export const useShellFilters = create<State>()(
  persist(
    (set) => ({
      query: "",
      sort: "recent",
      sortDirection: "desc",
      deadlineFrom: "",
      deadlineTo: "",
      confidence: "",
      // First-run defaults: lead with what is still in motion. Once the user picks something
      // else it sticks (see the persist config below).
      status: "not-achieved",
      targetStatus: "not-done",
      targetDeadline: "all",
      targetLock: "all",
      viewMode: "cards",
      setQuery: (query) => set({ query }),
      setSort: (sort) => set({ sort }),
      setSortDirection: (sortDirection) => set({ sortDirection }),
      resetSort: () => set({ sort: "recent", sortDirection: "desc" }),
      setDeadlineFrom: (deadlineFrom) => set({ deadlineFrom }),
      setDeadlineTo: (deadlineTo) => set({ deadlineTo }),
      setConfidence: (confidence) => set({ confidence }),
      setStatus: (status) => set({ status }),
      setTargetStatus: (targetStatus) => set({ targetStatus }),
      setTargetDeadline: (targetDeadline) => set({ targetDeadline }),
      setTargetLock: (targetLock) => set({ targetLock }),
      resetFilters: () =>
        set({
          deadlineFrom: "",
          deadlineTo: "",
          confidence: "",
          // `status` is intentionally absent: it is the user's standing choice, not a filter
          // this button is allowed to undo.
        }),
      setViewMode: (viewMode) => set({ viewMode }),
    }),
    {
      name: "spira:view-prefs",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        status: state.status,
        targetStatus: state.targetStatus,
        targetDeadline: state.targetDeadline,
        targetLock: state.targetLock,
        viewMode: state.viewMode,
      }),
    },
  ),
);

/**
 * Empties the search box whenever the route changes.
 *
 * One `query` field backs two different searches — the dashboard's goal filter and the goal
 * workspace header's goal switcher — so without this, filtering "All goals" and then opening one
 * of the results carried that text straight into the opened goal's header (and its results
 * dropdown). A search belongs to the screen it was typed on, on every surface: desktop, mobile
 * web, and the Android app, whose workspace search is likewise screen-local.
 */
export function useResetQueryOnNavigate(pathname: string) {
  const setQuery = useShellFilters((s) => s.setQuery);
  useEffect(() => {
    setQuery("");
  }, [pathname, setQuery]);
}
