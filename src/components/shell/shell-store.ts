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
 * Whether a goal has a deadline at all — Android's DEADLINE pills. Deliberately separate from the
 * date range beside it: this asks whether there IS a date, the range asks which dates to keep.
 */
export type GoalDeadlineFilter = "all" | "has" | "none";
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
/** The padlock question — whether a target's progress is pinned (see `isProgressLocked`). */
export type TargetLockFilter = "all" | "locked" | "unlocked";
/** Which kind of target this is — the twin of Android's `TargetTypeFilter`. */
export type TargetTypeFilter = "all" | "binary" | "numeric" | "checklist";
export type TargetSortKey = "title" | "deadline" | "progress" | "created";
/** The thumb lean an option carries, as a filter. */
export type OptionLeanFilter = "all" | "good_idea" | "didnt_work" | "none";
export type ResourceSortKey = "recent" | "name";
export type ResourceTypeFilter = "all" | "note" | "link" | "file" | "email";

/**
 * The four lists that ask a filter-and-sort question, each with its own panel and its own padlock.
 */
export type ListKey = "goals" | "targets" | "options" | "resources";

/* ─────────────────────────────────────────────────────────────────────────────
   The padlock
   ─────────────────────────────────────────────────────────────────────────────

   **A closed padlock pins a list's filters and sort; an open one lets them go** (owner,
   2026-08-21). It sits in each panel's coloured head, on the right beside the X.

   - **Closed** — the arrangement is written down and comes back on the next visit, after a reload,
     and after the app has been closed and reopened. Every change made while it is closed is
     written too, so the lock holds what is on screen rather than a snapshot of when it was shut.
   - **Open** — nothing is written and whatever was written is dropped, so the list opens on its
     defaults. This is the behaviour every list had before a lock existed.

   It replaces the "Remember these filters" button that lived in the panel body for one day: a
   button that saves is a thing you have to remember to press again after every change, and it only
   ever covered the All-goals list.
   ───────────────────────────────────────────────────────────────────────────── */

/** What each list pins when its padlock is closed. */
type GoalsView = {
  sort: SortKey;
  sortDirection: SortDirection;
  deadlineFrom: string;
  deadlineTo: string;
  confidence: string;
  status: GoalStatusFilter;
  goalDeadline: GoalDeadlineFilter;
};

type TargetsView = {
  targetSort: TargetSortKey;
  targetSortDesc: boolean;
  targetStatus: TargetStatusFilter;
  targetDeadline: TargetDeadlineFilter;
  targetLock: TargetLockFilter;
  targetType: TargetTypeFilter;
  targetDeadlineFrom: string;
  targetDeadlineTo: string;
};

type OptionsView = { optionLean: OptionLeanFilter };

type ResourcesView = {
  resourceSort: ResourceSortKey;
  resourceType: ResourceTypeFilter;
};

type Views = GoalsView & TargetsView & OptionsView & ResourcesView;

/**
 * The defaults every list opens on — and exactly what "Reset all" restores.
 *
 * **Reset all resets all** (owner, 2026-08-21). There used to be a class of "standing preference"
 * the button was not allowed to undo — the status questions — so a button promising everything
 * quietly kept four answers. Android's reset never drew that distinction; now neither does this.
 */
const DEFAULTS: Views = {
  // The first-run answers lead with what is still in motion.
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
};

/**
 * Which fields belong to each list — what "Reset all" restores and what lights the trigger's dot.
 *
 * A **superset** of what the padlock pins: see [PINNED].
 */
const FIELDS: Record<ListKey, readonly (keyof Views)[]> = {
  goals: [
    "sort",
    "sortDirection",
    "deadlineFrom",
    "deadlineTo",
    "confidence",
    "status",
    "goalDeadline",
  ],
  targets: [
    "targetSort",
    "targetSortDesc",
    "targetStatus",
    "targetDeadline",
    "targetLock",
    "targetType",
    "targetDeadlineFrom",
    "targetDeadlineTo",
  ],
  options: ["optionLean"],
  resources: ["resourceSort", "resourceType"],
};

type State = Views & {
  /** The search box. Never pinned and never persisted — see `useResetQueryOnNavigate`. */
  query: string;
  /** Cards or table on the dashboard. Not a filter, so no padlock covers it; it simply persists. */
  viewMode: "cards" | "table";
  /** Which lists have their padlock closed. */
  locked: Record<ListKey, boolean>;

  setQuery: (q: string) => void;
  setViewMode: (v: "cards" | "table") => void;
  setLocked: (list: ListKey, next: boolean) => void;
  /** Put one list's questions back to their defaults — every one of them. */
  resetList: (list: ListKey) => void;
  /** Change any pinned field. Persistence follows the list's padlock, via `partialize`. */
  setView: (patch: Partial<Views>) => void;
};

/** The subset of [Views] a list pins, read off the current state. */
function viewOf(state: Views, list: ListKey): Partial<Views> {
  const out: Record<string, unknown> = {};
  for (const key of PINNED[list]) out[key] = state[key];
  return out as Partial<Views>;
}

/** The defaults for one list — what "Reset all" restores. */
function defaultsOf(list: ListKey): Partial<Views> {
  const out: Record<string, unknown> = {};
  for (const key of FIELDS[list]) out[key] = DEFAULTS[key];
  return out as Partial<Views>;
}

/**
 * What a closed padlock writes down: **every question the list asks**, the date ranges included.
 *
 * The two ranges were exempt for a day (owner, 2026-08-21) on the argument that a range is about a
 * moment, so one closed over in August would open the page in October on a list that looks empty
 * for no visible reason. The owner met the other half of that trade on 2026-08-22: she set a
 * range, shut the padlock, left the app and came back to find it gone. **A control that promises
 * to keep the arrangement and silently drops two of the seven answers is the worse failure** —
 * nothing on screen admitted the range was not covered, and the padlock sat there closed.
 *
 * The original worry is largely answered by two things that shipped after it was written: a list
 * emptied by its own filter now says so in a warning notice (`FilteredEmptyNotice`), and the
 * trigger carries a Guava dot whenever anything is narrowing the list. A restored range is no
 * longer invisible.
 *
 * The **search box** is still never pinned — it is not in [FIELDS] at all, so no padlock reaches
 * it. A query belongs to the screen it was typed on; see `useResetQueryOnNavigate`.
 */
const PINNED: Record<ListKey, readonly (keyof Views)[]> = FIELDS;

const NOTHING_LOCKED: Record<ListKey, boolean> = {
  goals: false,
  targets: false,
  options: false,
  resources: false,
};

/**
 * View state for every list in the app: the goals overview, a goal's targets, its options and its
 * resources.
 *
 * Each list's filters and sort live here so its **padlock** has one thing to pin (see above), and
 * so the panel that asks the questions is the only place that has to know them. The search query
 * and the view mode are the two exceptions: a query belongs to the screen it was typed on, and the
 * cards/table choice is not a filter.
 */
export const useShellFilters = create<State>()(
  persist(
    (set) => ({
      ...DEFAULTS,
      query: "",
      viewMode: "cards",
      locked: { ...NOTHING_LOCKED },

      setQuery: (query) => set({ query }),
      setViewMode: (viewMode) => set({ viewMode }),
      // Opening a padlock leaves what is on screen alone — unlocking is not a reset. What it does
      // is stop the arrangement being written, and `partialize` then drops it from storage, so the
      // next visit opens on the defaults with no separate "forget" step to remember.
      setLocked: (list, next) =>
        set((state) => ({ locked: { ...state.locked, [list]: next } })),
      resetList: (list) => set(defaultsOf(list) as Partial<State>),
      setView: (patch) => set(patch as Partial<State>),
    }),
    {
      name: "spira:view-prefs",
      storage: createJSONStorage(() => localStorage),
      // **Only what a closed padlock covers.** An open one writes nothing, which is what makes an
      // unpinned list open on its defaults after a reload — and what makes opening the padlock
      // enough to forget.
      partialize: (state) => {
        const pinned: Record<string, unknown> = {};
        for (const list of Object.keys(state.locked) as ListKey[]) {
          if (state.locked[list]) Object.assign(pinned, viewOf(state, list));
        }
        return { ...pinned, locked: state.locked, viewMode: state.viewMode };
      },
      // Anything a padlock did not cover comes back at its default, not at whatever an older build
      // left in storage.
      merge: (persisted, current) => {
        const stored = (persisted ?? {}) as Partial<State>;
        const locked = { ...NOTHING_LOCKED, ...(stored.locked ?? {}) };
        const restored: Record<string, unknown> = {};
        for (const list of Object.keys(locked) as ListKey[]) {
          if (!locked[list]) continue;
          for (const key of PINNED[list]) {
            if (stored[key] !== undefined) restored[key] = stored[key];
          }
        }
        return {
          ...current,
          ...DEFAULTS,
          ...restored,
          locked,
          viewMode: stored.viewMode ?? current.viewMode,
        };
      },
      // v0 kept the status questions whether or not anyone had asked it to, and briefly a
      // `savedView`. Neither has a padlock behind it, so those installs start at the defaults with
      // every padlock open — which is what an un-pinned list is supposed to look like.
      //
      // **`viewMode` is carried across** — it is not a filter, no padlock covers it, and dropping
      // it would silently put a table-view user back on cards for no reason they could see.
      version: 1,
      migrate: (persisted) => {
        const old = (persisted ?? {}) as Partial<State>;
        return (old.viewMode ? { viewMode: old.viewMode } : {}) as never;
      },
    },
  ),
);

/** Whether a list's padlock is closed. */
export function useListLocked(list: ListKey): boolean {
  return useShellFilters((s) => s.locked[list]);
}

/** Whether any of a list's questions is away from its default — what lights the trigger's dot. */
export function useListActive(list: ListKey): boolean {
  return useShellFilters((s) =>
    FIELDS[list].some((key) => s[key] !== DEFAULTS[key]),
  );
}

/** How many of a list's questions are narrowing it. */
export function useListActiveCount(list: ListKey): number {
  return useShellFilters(
    (s) => FIELDS[list].filter((key) => s[key] !== DEFAULTS[key]).length,
  );
}

/** The default of one pinned field, for a call site that needs to compare against it. */
export function viewDefault<K extends keyof Views>(key: K): Views[K] {
  return DEFAULTS[key];
}

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
