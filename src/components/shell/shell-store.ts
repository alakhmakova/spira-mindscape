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
   Scope — whose questions are these?
   ─────────────────────────────────────────────────────────────────────────────

   **A goal's lists remember their own arrangement** (owner, 2026-08-31). `targets`, `options` and
   `resources` are lists *inside a goal*, so each goal answers the panel's questions for itself and
   carries its own padlock. `goals` is the All-goals dashboard's own list and stays app-wide —
   there is only one of it.

   Before this, all four lived in one flat set of fields, so every goal shared one answer. Two
   things went wrong at once, and the second is the worse of them: a filter set and pinned on goal
   1 was already on when goal 2 opened, with goal 2's padlock sitting open — and then changing it
   there rewrote goal 1's arrangement behind goal 1's *closed* padlock. A lock that another screen
   can write through is not a lock.
   ───────────────────────────────────────────────────────────────────────────── */

/** Which lists belong to a goal rather than to the app. */
const GOAL_SCOPED: Record<ListKey, boolean> = {
  goals: false,
  targets: true,
  options: true,
  resources: true,
};

/**
 * The goal id to pass for the All-goals list — it belongs to the app, not to any goal.
 *
 * A named constant rather than a bare `null` so a call site reads as a deliberate "this list is
 * app-wide" instead of "I had no goal id to hand", which is precisely the mistake this scoping
 * exists to prevent.
 */
export const GOALS_SCOPE = null;

/**
 * The scope a goal-owned list falls back to when the caller has no goal id.
 *
 * **This is a legibility guard, not an isolation one** — be precise about it, because the sloppy
 * version of this sentence has already been wrong once. No goal has a blank id, so a goal-less
 * caller was never going to collide with a real goal's answers whatever the fallback; every
 * goal-less caller shares this one scope, and that is fine, because nothing meaningful is written
 * before a goal has loaded. What the name buys is that `targets:none` in storage says plainly
 * "something wrote without a goal", where a bare `targets:` reads as a shared bucket — the very
 * shape this file removes — and invites exactly that misreading from the next person.
 *
 * Two callers can be in this position: a screen rendering before its goal has loaded, and
 * `GOALS_SCOPE` pasted into a goal-scoped call, which type checks because both are `string | null`.
 * Android's `NO_GOAL` is the same guard.
 */
const NO_GOAL = "none";

/**
 * Where one list's answers are filed: the list's own name for the dashboard, and the name plus the
 * goal id for the three lists that live inside a goal.
 */
function scopeKey(list: ListKey, goalId: string | null): string {
  if (!GOAL_SCOPED[list]) return list;
  return `${list}:${goalId || NO_GOAL}`;
}

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

   **One padlock per scope**, not per list: goal 1's targets can be pinned while goal 2's are not.

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
 * There is no separate list of what a **closed padlock pins**, because it pins the scope whole:
 * **every question the list asks, the date ranges included.**
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
 * The **search box** is the one thing no padlock reaches — it is not in here at all. A query
 * belongs to the screen it was typed on; see `useResetQueryOnNavigate`.
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

/**
 * Which list a field belongs to, so a patch does not have to name it.
 *
 * Every key appears under exactly one list in [FIELDS], which is what makes this safe: `setView`
 * can take `{ targetStatus: "done" }` and file it under that goal's targets without the caller
 * repeating "targets".
 */
const LIST_OF_FIELD = Object.fromEntries(
  (Object.keys(FIELDS) as ListKey[]).flatMap((list) =>
    FIELDS[list].map((key) => [key, list] as const),
  ),
) as Record<keyof Views, ListKey>;

/** One scope's answers. A key that is absent is at its default — see [readField]. */
type ScopeView = Partial<Views>;

type State = {
  /** The search box. Never pinned and never persisted — see `useResetQueryOnNavigate`. */
  query: string;
  /** Cards or table on the dashboard. Not a filter, so no padlock covers it; it simply persists. */
  viewMode: "cards" | "table";
  /** Every scope's answers, keyed by [scopeKey]. */
  views: Record<string, ScopeView>;
  /** Which scopes have their padlock closed, keyed the same way. */
  locked: Record<string, boolean>;

  setQuery: (q: string) => void;
  setViewMode: (v: "cards" | "table") => void;
  setLocked: (list: ListKey, goalId: string | null, next: boolean) => void;
  /** Put one list's questions back to their defaults — every one of them. */
  resetList: (list: ListKey, goalId: string | null) => void;
  /**
   * Change any pinned field, for one goal's lists (pass `null` for the dashboard's own).
   *
   * The patch's keys say which list each answer belongs to, so a caller never repeats it.
   * Persistence follows that scope's padlock, via `partialize`.
   */
  setView: (patch: Partial<Views>, goalId: string | null) => void;
};

/** One field's current answer for one scope, falling back to the default it has never left. */
function readField<K extends keyof Views>(
  state: Pick<State, "views">,
  key: K,
  goalId: string | null,
): Views[K] {
  const scope = state.views[scopeKey(LIST_OF_FIELD[key], goalId)];
  const value = scope?.[key];
  return (value === undefined ? DEFAULTS[key] : value) as Views[K];
}

/**
 * View state for every list in the app: the goals overview, and — **per goal** — its targets, its
 * options and its resources.
 *
 * Each list's filters and sort live here so its **padlock** has one thing to pin (see above), and
 * so the panel that asks the questions is the only place that has to know them. The search query
 * and the view mode are the two exceptions: a query belongs to the screen it was typed on, and the
 * cards/table choice is not a filter.
 */
export const useShellFilters = create<State>()(
  persist(
    (set) => ({
      query: "",
      viewMode: "cards",
      views: {},
      locked: {},

      setQuery: (query) => set({ query }),
      setViewMode: (viewMode) => set({ viewMode }),
      // Opening a padlock leaves what is on screen alone — unlocking is not a reset. What it does
      // is stop the arrangement being written, and `partialize` then drops it from storage, so the
      // next visit opens on the defaults with no separate "forget" step to remember.
      setLocked: (list, goalId, next) =>
        set((state) => ({
          locked: { ...state.locked, [scopeKey(list, goalId)]: next },
        })),
      // Deleting the keys rather than writing the defaults over them: an absent answer IS the
      // default (see `readField`), so a reset scope stores nothing at all.
      resetList: (list, goalId) =>
        set((state) => {
          const key = scopeKey(list, goalId);
          const scope = { ...(state.views[key] ?? {}) };
          for (const field of FIELDS[list]) delete scope[field];
          return { views: { ...state.views, [key]: scope } };
        }),
      setView: (patch, goalId) =>
        set((state) => {
          const views = { ...state.views };
          for (const [field, value] of Object.entries(patch)) {
            const key = scopeKey(LIST_OF_FIELD[field as keyof Views], goalId);
            views[key] = { ...(views[key] ?? {}), [field]: value };
          }
          return { views };
        }),
    }),
    {
      name: "spira:view-prefs",
      storage: createJSONStorage(() => localStorage),
      // **Only what a closed padlock covers.** An open one writes nothing, which is what makes an
      // unpinned list open on its defaults after a reload — and what makes opening the padlock
      // enough to forget. An open padlock is not recorded either, so a goal whose lists were
      // merely visited leaves nothing behind at all.
      partialize: (state) => {
        const views: Record<string, ScopeView> = {};
        const locked: Record<string, boolean> = {};
        for (const [key, isLocked] of Object.entries(state.locked)) {
          if (!isLocked) continue;
          locked[key] = true;
          if (state.views[key]) views[key] = state.views[key];
        }
        return { views, locked, viewMode: state.viewMode } as unknown as State;
      },
      // Anything a padlock did not cover comes back at its default, not at whatever an older build
      // left in storage.
      merge: (persisted, current) => {
        const stored = (persisted ?? {}) as Partial<State>;
        const locked: Record<string, boolean> = {};
        const views: Record<string, ScopeView> = {};
        for (const [key, isLocked] of Object.entries(stored.locked ?? {})) {
          if (!isLocked) continue;
          locked[key] = true;
          const scope = stored.views?.[key];
          if (scope) views[key] = scope;
        }
        return {
          ...current,
          views,
          locked,
          viewMode: stored.viewMode ?? current.viewMode,
        };
      },
      // **What survives the move to scopes, and what cannot.**
      //
      // v1 held one flat arrangement shared by every goal. For `targets` / `options` / `resources`
      // that is the bug itself, and one global answer cannot honestly be attributed to any single
      // goal — so it is dropped, and those lists open on their defaults with every padlock open.
      //
      // **The All-goals list is different and comes across intact.** It was never part of the
      // defect: it is app-wide, there is only one of it, and v1's fields for it mean exactly what
      // they mean now. Dropping a dashboard arrangement the user had pinned would be the very
      // failure the padlock spec names — a control that promises to keep the arrangement and
      // silently loses it — inflicted by the fix for a different list.
      //
      // **`viewMode` is carried across too** — it is not a filter, no padlock covers it, and
      // dropping it would silently put a table-view user back on cards for no reason they see.
      version: 2,
      migrate: (persisted) => {
        const old = (persisted ?? {}) as Record<string, unknown>;
        const next: Record<string, unknown> = {};
        if (old.viewMode) next.viewMode = old.viewMode;
        // Only if its padlock was closed — an open one stored nothing to carry.
        const wasLocked = (old.locked as Record<string, boolean> | undefined)
          ?.goals;
        if (wasLocked) {
          const view: Record<string, unknown> = {};
          for (const field of FIELDS.goals) {
            if (old[field] !== undefined) view[field] = old[field];
          }
          next.views = { goals: view };
          next.locked = { goals: true };
        }
        return next as never;
      },
    },
  ),
);

/**
 * One question's current answer, for one goal's lists — pass `null` for the dashboard's own list.
 *
 * The key says which list it belongs to, so the caller never repeats it. The value returned is a
 * primitive, so a component re-renders only when its own answer changes.
 */
export function useViewField<K extends keyof Views>(
  key: K,
  goalId: string | null,
): Views[K] {
  return useShellFilters((s) => readField(s, key, goalId));
}

/** Whether a list's padlock is closed, for this goal. */
export function useListLocked(list: ListKey, goalId: string | null): boolean {
  return useShellFilters((s) => s.locked[scopeKey(list, goalId)] ?? false);
}

/** Whether any of a list's questions is away from its default — what lights the trigger's dot. */
export function useListActive(list: ListKey, goalId: string | null): boolean {
  return useShellFilters((s) =>
    FIELDS[list].some((key) => readField(s, key, goalId) !== DEFAULTS[key]),
  );
}

/** How many of a list's questions are narrowing it. */
export function useListActiveCount(
  list: ListKey,
  goalId: string | null,
): number {
  return useShellFilters(
    (s) =>
      FIELDS[list].filter((key) => readField(s, key, goalId) !== DEFAULTS[key])
        .length,
  );
}

/** The default of one pinned field, for a call site that needs to compare against it. */
export function viewDefault<K extends keyof Views>(key: K): Views[K] {
  return DEFAULTS[key];
}

/** Read one answer outside React — for tests, and for a caller with no hook to hand. */
export function viewField<K extends keyof Views>(
  key: K,
  goalId: string | null,
): Views[K] {
  return readField(useShellFilters.getState(), key, goalId);
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
