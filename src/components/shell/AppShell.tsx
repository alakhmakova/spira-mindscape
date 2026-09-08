import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  Search,
  Filter,
  SortAscending,
  SortDescending,
  X,
  RefreshCw,
} from "@/components/spira/icons";
import { useAi } from "@/components/ai/ai-store";
import { AiPanel } from "@/components/ai/AiPanel";
import { SideNav } from "./SideNav";
import { useSpira } from "@/lib/spira/store";
import { useAuth } from "@/lib/spira/auth";
import { useApplyAppFont } from "@/lib/spira/app-font";
import { useActivityGate } from "@/lib/useActivityGate";
import {
  GOALS_SCOPE,
  useListActive,
  useListLocked,
  useResetQueryOnNavigate,
  useShellFilters,
  useViewField,
  type GoalDeadlineFilter,
  type GoalStatusFilter,
  type SortDirection,
  type SortKey,
} from "./shell-store";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import {
  ClearSearchWord,
  SheetChoiceCards,
  SheetConfidence,
  SheetDateRange,
  SheetGroup,
  SheetPills,
  SheetSegmented,
  ToolbarSheet,
} from "@/components/spira/ListToolbar";
import { NoticeCard } from "@/components/spira/Notice";
import { cn } from "@/lib/utils";

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** The goal sort keys and their words — shared by the desktop menu and the mobile drawer. */
const SORT_CHOICES = [
  { value: "recent", label: "Created" },
  { value: "deadline", label: "Deadline" },
  { value: "progress", label: "Progress" },
  { value: "confidence", label: "Confidence" },
  { value: "title", label: "Title A-Z" },
] as const;

/** The direction words, with the glyph each carries — shared by the menu and the drawer. */
const SORT_DIRECTION_CHOICES = [
  {
    value: "asc",
    label: "Ascending",
    icon: <SortAscending className="h-4 w-4 shrink-0" />,
  },
  {
    value: "desc",
    label: "Descending",
    icon: <SortDescending className="h-4 w-4 shrink-0" />,
  },
] as const;

/**
 * The goal status question, as **three short words on one line** (owner, 2026-08-17). It used to
 * read "All goals / Only achieved / Only not achieved", which cannot fit across a phone — the
 * qualifier is carried by the heading above the pills, so the pills only need the state.
 */
const GOAL_STATUS_CHOICES = [
  { value: "all", label: "All" },
  { value: "achieved", label: "Achieved" },
  { value: "not-achieved", label: "Not achieved" },
] as const satisfies readonly { value: GoalStatusFilter; label: string }[];

/**
 * Whether a goal has a deadline at all — the twin of Android's `DEADLINE_PILLS`
 * (`GoalsDashboardScreen.kt`). The words are shortened for one line: the heading above already
 * says "Deadline", so the pills need only the answer.
 */
const GOAL_DEADLINE_CHOICES = [
  { value: "all", label: "Any" },
  { value: "has", label: "Deadline" },
  { value: "none", label: "No deadline" },
] as const satisfies readonly { value: GoalDeadlineFilter; label: string }[];

/** Two-letter initials for the account avatar's fallback when there is no Google picture. */
function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2)
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  return (name.slice(0, 2) || "?").toUpperCase();
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const path = useRouterState({
    select: (s) => (s.resolvedLocation ?? s.location).pathname,
  });
  const openAi = useAi((s) => s.open);
  const isAiOpen = useAi((s) => s.isOpen);
  const isAiWide = useAi((s) => s.isWide);
  const loadGoals = useSpira((s) => s.loadGoals);
  const refreshGoals = useSpira((s) => s.refreshGoals);
  const refreshGoalsIfIdle = useSpira((s) => s.refreshGoalsIfIdle);
  const isLoadingGoals = useSpira((s) => s.isLoading);
  const syncError = useSpira((s) => s.syncError);
  const syncErrorKind = useSpira((s) => s.syncErrorKind);
  const authUser = useAuth((s) => s.user);
  const navigate = useNavigate();
  const query = useShellFilters((s) => s.query);
  const setQuery = useShellFilters((s) => s.setQuery);
  const setView = useShellFilters((s) => s.setView);
  const resetList = useShellFilters((s) => s.resetList);
  const setLocked = useShellFilters((s) => s.setLocked);
  const viewMode = useShellFilters((s) => s.viewMode);
  // The All-goals list is the app's own, not a goal's — hence `GOALS_SCOPE` everywhere below.
  // The three lists inside a goal each answer for themselves; see `shell-store.ts` → Scope.
  const sort = useViewField("sort", GOALS_SCOPE);
  const sortDirection = useViewField("sortDirection", GOALS_SCOPE);
  const deadlineFrom = useViewField("deadlineFrom", GOALS_SCOPE);
  const deadlineTo = useViewField("deadlineTo", GOALS_SCOPE);
  const confidence = useViewField("confidence", GOALS_SCOPE);
  const status = useViewField("status", GOALS_SCOPE);
  const goalDeadline = useViewField("goalDeadline", GOALS_SCOPE);
  const goalsLocked = useListLocked("goals", GOALS_SCOPE);
  const goalsActive = useListActive("goals", GOALS_SCOPE);

  const setSort = (next: SortKey) => setView({ sort: next }, GOALS_SCOPE);
  const setSortDirection = (next: SortDirection) =>
    setView({ sortDirection: next }, GOALS_SCOPE);
  const setConfidence = (next: string) =>
    setView({ confidence: next }, GOALS_SCOPE);
  const setStatus = (next: GoalStatusFilter) =>
    setView({ status: next }, GOALS_SCOPE);
  // **"No deadline" and a date range can never both be on.** A range asks which deadlines to keep
  // and "No deadline" asks for the goals that haven't got one, so together they match nothing and
  // neither control says why. Picking either takes the other off.
  const setGoalDeadline = (next: GoalDeadlineFilter) =>
    setView(
      next === "none"
        ? { goalDeadline: next, deadlineFrom: "", deadlineTo: "" }
        : { goalDeadline: next },
      GOALS_SCOPE,
    );
  const setDeadlineFrom = (value: string) =>
    setView(
      {
        deadlineFrom: value,
        ...(value && goalDeadline === "none" ? { goalDeadline: "all" } : {}),
      },
      GOALS_SCOPE,
    );
  const setDeadlineTo = (value: string) =>
    setView(
      {
        deadlineTo: value,
        ...(value && goalDeadline === "none" ? { goalDeadline: "all" } : {}),
      },
      GOALS_SCOPE,
    );

  // A search is scoped to the screen it was typed on — see useResetQueryOnNavigate.
  useResetQueryOnNavigate(path);

  // The body face the owner picked in Settings → Fonts, applied to the whole app. Mounted here
  // because the shell wraps every route, so the choice survives navigation without a flash.
  useApplyAppFont();

  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  // The phone's search is a glyph in the teal header until it is opened; opening swaps the whole
  // header row for a field, the way Android's `SearchTopBar` does (owner, 2026-08-17).
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
  // The field closes with the screen it was opened on, for the same reason its text is cleared:
  // a search belongs to one screen. Without this, arriving at a goal page from the dashboard's
  // open search left an empty field sitting where the header should be.
  useEffect(() => setMobileSearchOpen(false), [path]);

  const isDashboard = path === "/";
  const isCalendar = path.startsWith("/calendar");
  const isWorkspace = path.startsWith("/goals/");
  // The open goal, so the side navigation can offer its sections. Read from the path
  // rather than from a route hook: the shell wraps every route, including the ones that
  // have no goalId at all.
  const openGoalId = isWorkspace ? path.split("/")[2] : undefined;
  // Which screens carry the phone's collapsed search glyph. The dashboard filters its list with
  // it; the goal page switches goals with it. Settings and Calendar have nothing to search.
  const showMobileSearch = isDashboard || isWorkspace;
  // **The filter glyph belongs exactly where the panel is mounted**, which is the dashboard —
  // Settings and Calendar have no list to narrow.
  const showFilterControls = isDashboard;

  const goals = useSpira((s) => s.goals);
  const openGoalTitle = openGoalId
    ? goals.find((g) => g.id === openGoalId)?.title
    : undefined;
  const searchResults =
    query.trim() === ""
      ? []
      : goals
          .filter((g) =>
            g.title.toLowerCase().includes(query.toLowerCase().trim()),
          )
          .slice(0, 5);

  useEffect(() => {
    void loadGoals();
  }, [loadGoals]);

  // ── Keep data fresh across devices ───────────────────────────────────────
  // The store loads goals once and never re-fetches on its own, so a change made
  // on another device (or the phone) wouldn't appear here without a reload. Pull
  // a fresh copy whenever this surface regains attention, plus a light poll while
  // it's visible so two open screens stay roughly in sync. refreshGoalsIfIdle is
  // silent and skips while local edits are in flight, so this never flashes UI
  // or clobbers unsaved work.
  //
  // Cost guard: the poll PAUSES after a few minutes without user interaction (via
  // useActivityGate — pointer/key/wheel/touch all count, so reading-by-scroll keeps it
  // alive), so a forgotten open tab stops hitting the backend and lets the (Neon)
  // database scale to zero. Returning to the tab, or interacting after an idle stretch,
  // refreshes at once so the first interaction still shows fresh cross-device data.
  const isActive = useActivityGate(3 * 60_000, () => {
    if (document.visibilityState === "visible") void refreshGoalsIfIdle();
  });
  useEffect(() => {
    const refreshOnReturn = () => {
      if (document.visibilityState === "visible") void refreshGoalsIfIdle();
    };
    window.addEventListener("focus", refreshOnReturn);
    window.addEventListener("pageshow", refreshOnReturn);
    document.addEventListener("visibilitychange", refreshOnReturn);

    const poll = window.setInterval(() => {
      if (document.visibilityState === "visible" && isActive()) {
        void refreshGoalsIfIdle();
      }
    }, 45_000);

    return () => {
      window.removeEventListener("focus", refreshOnReturn);
      window.removeEventListener("pageshow", refreshOnReturn);
      document.removeEventListener("visibilitychange", refreshOnReturn);
      window.clearInterval(poll);
    };
  }, [refreshGoalsIfIdle, isActive]);

  // ── Offline / online detection ──────────────────────────────────────────
  const [isOffline, setIsOffline] = useState(
    typeof navigator !== "undefined" ? !navigator.onLine : false,
  );

  useEffect(() => {
    const handleOffline = () => setIsOffline(true);
    const handleOnline = () => {
      setIsOffline(false);
      // Auto-retry when connection is restored
      void refreshGoals();
    };
    window.addEventListener("offline", handleOffline);
    window.addEventListener("online", handleOnline);
    return () => {
      window.removeEventListener("offline", handleOffline);
      window.removeEventListener("online", handleOnline);
    };
  }, [refreshGoals]);

  return (
    // nav | AI | content. The coach is back on the LEFT (owner, 2026-09-03 — "ai chat слева, а
    // не справа на десктопе"), sitting between the standing navigation and the page. It had
    // moved right on 2026-08-23 because the nav had nowhere else to go while the coach held the
    // left column; `SideNav` now solves that itself by collapsing to an icon-only rail whenever
    // the coach is open, so the two no longer compete for the same edge.
    <div className="flex min-h-screen bg-background">
      <SideNav path={path} goalId={openGoalId} goalTitle={openGoalTitle} />
      <AiPanel />
      <div className="flex min-w-0 flex-1 flex-col">
        {/* Top bar — teal on every page (the goal-page colours), so the header reads the same
            across the app. */}
        <header className="sticky top-0 z-30 bg-primary transition-colors duration-200">
          {/*
            **The open search takes the whole teal row**, exactly as Android's `SearchTopBar` does:
            the bar keeps its teal, the wordmark steps aside for a white field, and a white disc
            with a teal X closes it. Anything narrower would have put a field between the wordmark
            and the avatar with no room to type.

            **The goal page uses it too** (owner, 2026-08-20). There the field was permanently open
            and centred, so on a phone it took the whole row and sat ON the account circle — the
            avatar was behind the search box. It collapses to a glyph like the dashboard's now, and
            opening it still lists the goals it matches so the search keeps doing its one job:
            switching goals.
          */}
          {mobileSearchOpen && showMobileSearch && (
            <div className="relative flex h-16 items-center gap-2.5 px-4 sm:hidden">
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Escape") {
                      setQuery("");
                      setMobileSearchOpen(false);
                    }
                  }}
                  placeholder={
                    isWorkspace ? "Search goals" : "Search for goals"
                  }
                  aria-label="Search goals"
                  className="h-10 w-full rounded-md bg-white pl-9 pr-14 text-sm text-foreground shadow-sm outline-none placeholder:text-muted-foreground focus:ring-2 focus:ring-white/60"
                />
                {/* The word empties the query; the white disc beside the field closes the search.
                    Two crosses said neither. */}
                {query && <ClearSearchWord onClear={() => setQuery("")} />}
                {isWorkspace && query.trim() !== "" && (
                  <div className="absolute left-0 right-0 top-full z-50 mt-1 overflow-hidden rounded-md border hairline bg-surface shadow-lg">
                    {searchResults.length > 0 ? (
                      searchResults.map((r) => (
                        <Link
                          key={r.id}
                          to="/goals/$goalId"
                          params={{ goalId: r.id }}
                          onClick={() => {
                            setQuery("");
                            setMobileSearchOpen(false);
                          }}
                          className="block truncate px-3 py-2 text-sm text-foreground hover:bg-secondary"
                        >
                          {r.title || "Untitled goal"}
                        </Link>
                      ))
                    ) : (
                      <div className="px-3 py-2 text-sm italic text-muted-foreground">
                        No goals found
                      </div>
                    )}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={() => {
                  setQuery("");
                  setMobileSearchOpen(false);
                }}
                aria-label="Close search"
                className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white text-primary transition-colors hover:bg-white/90"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          )}
          <div
            className={cn(
              "spira-shell-header-row w-full px-4 sm:px-6 h-16 items-center",
              isWorkspace
                ? cn(
                    // **A phone lays this row out as flex, not as the three-column grid.** The
                    // middle column is the search, which is a glyph down here — held as a grid
                    // the row still reserved up to 600px for it and squeezed the wordmark and the
                    // avatar into the columns either side.
                    "flex gap-3 sm:grid sm:gap-3",
                    isAiOpen
                      ? "sm:grid-cols-[1fr_minmax(0,320px)_1fr]"
                      : "sm:grid-cols-[1fr_minmax(0,600px)_1fr]",
                  )
                : "flex gap-3 sm:gap-5",
              // While the phone's search is open it IS the header row; the normal row steps out.
              // **Last in the list on purpose**: `cn` resolves conflicting display utilities in
              // favour of the later one, so put before the `flex` above this was simply ignored
              // and the field stacked on top of the row instead of replacing it.
              mobileSearchOpen && showMobileSearch && "hidden sm:flex",
            )}
          >
            {/* Brand */}
            <div
              className={cn(
                "flex items-center",
                isWorkspace ? "justify-start gap-4" : "gap-2",
              )}
            >
              {isWorkspace ? (
                <>
                  {!isAiOpen && (
                    <Link
                      to="/"
                      className="text-[32px] font-extrabold tracking-[-0.01em] text-white hover:text-white/90 transition-colors leading-none"
                    >
                      spira
                    </Link>
                  )}
                  {!isAiOpen && (
                    <button
                      onClick={() => openAi()}
                      className="whitespace-nowrap text-white hover:text-white/90 text-[20px] font-normal transition-colors leading-none pt-1"
                    >
                      ai coach
                    </button>
                  )}
                </>
              ) : (
                <>
                  {!isAiOpen && (
                    <Link
                      to="/"
                      className="text-[32px] font-extrabold tracking-[-0.01em] transition-colors leading-none text-white hover:text-white/90"
                    >
                      spira
                    </Link>
                  )}
                  {!isAiOpen && (
                    <button
                      onClick={() => openAi()}
                      className="whitespace-nowrap text-[20px] font-normal transition-colors leading-none pt-1 text-white hover:text-white/90"
                    >
                      ai coach
                    </button>
                  )}
                </>
              )}
            </div>

            {/* Spacer (only for non-workspace) */}
            {!isWorkspace && <div className="flex-1" />}

            {/* Search (Centered for workspace, inline for non-workspace) */}
            <div
              className={cn(
                isWorkspace
                  ? "hidden w-full min-w-0 sm:flex"
                  : "hidden sm:flex w-32 sm:w-64 shrink-0",
              )}
            >
              <div
                className={cn("relative w-full", !isWorkspace && "max-w-2xl")}
              >
                <Search
                  className={cn(
                    "pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4",
                    isWorkspace
                      ? "text-muted-foreground"
                      : "text-muted-foreground",
                  )}
                />
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search goals"
                  aria-label="Search goals"
                  className="w-full h-10 pl-9 pr-14 rounded-md text-sm outline-none transition-colors bg-white border-transparent text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/50 shadow-sm"
                />
                {query && <ClearSearchWord onClear={() => setQuery("")} />}
                {isWorkspace && query.trim() !== "" && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-surface border hairline rounded-md shadow-lg overflow-hidden z-50">
                    {searchResults.length > 0 ? (
                      searchResults.map((r) => (
                        <Link
                          key={r.id}
                          to="/goals/$goalId"
                          params={{ goalId: r.id }}
                          onClick={() => setQuery("")}
                          className="block px-3 py-2 text-sm text-foreground hover:bg-secondary truncate"
                        >
                          {r.title || "Untitled goal"}
                        </Link>
                      ))
                    ) : (
                      <div className="px-3 py-2 text-sm text-muted-foreground italic">
                        No goals found
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Right side items */}
            <div
              className={cn(
                "flex shrink-0 items-center gap-3 sm:gap-4 justify-end",
                // The goal page's row is flex on a phone (see above), so nothing else pushes the
                // account across to the right edge.
                isWorkspace && "ml-auto sm:ml-0",
              )}
            >
              {/* **The search and the filters live HERE**, in the teal bar, as bare glyphs — the
                  Android header's arrangement (owner, 2026-08-17). They used to sit in a white
                  strip under the header, which is a whole row of chrome for two controls.

                  The search glyph is the phone's only (a desktop has room for the field itself);
                  the **filter glyph is every width's**, because the filters open into one panel
                  now — a drawer on a phone, a side panel on a laptop — and there is no dropdown
                  anywhere for a filter or a sort to hide in (owner, 2026-08-20). */}
              {showMobileSearch && (
                <button
                  type="button"
                  onClick={() => setMobileSearchOpen(true)}
                  aria-label="Search goals"
                  className="relative grid h-9 w-9 place-items-center rounded-md text-white/85 transition-colors hover:bg-white/15 hover:text-white sm:hidden"
                >
                  <span className="relative inline-flex">
                    <Search className="h-[18px] w-[18px]" />
                    {query && (
                      <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
                    )}
                  </span>
                </button>
              )}
              {showFilterControls && (
                <button
                  type="button"
                  onClick={() => setMobileFiltersOpen(true)}
                  aria-label="Filter and sort goals"
                  className="relative grid h-9 w-9 place-items-center rounded-md text-white/85 transition-colors hover:bg-white/15 hover:text-white"
                >
                  <span className="relative inline-flex">
                    <Filter className="h-[18px] w-[18px]" />
                    {goalsActive && (
                      <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
                    )}
                  </span>
                </button>
              )}
              {/* Account — a bare figure, like the Android header. Tapping it opens the account's
                  own page (Settings: My profile + Fonts, with Sign out inside), not a menu. */}
              <button
                type="button"
                onClick={() => void navigate({ to: "/settings" })}
                aria-label="Account settings"
                title="Account settings"
                className="shrink-0 rounded-full outline-none transition-transform hover:scale-105 focus-visible:ring-2 focus-visible:ring-white/70"
              >
                {authUser?.pictureUrl ? (
                  <img
                    src={authUser.pictureUrl}
                    alt={authUser.name ?? "Account"}
                    referrerPolicy="no-referrer"
                    className="h-9 w-9 rounded-full object-cover border border-white/40"
                  />
                ) : (
                  <span className="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semibold text-white">
                    {getInitials(authUser?.name ?? "")}
                  </span>
                )}
              </button>
            </div>
          </div>

          {/*
            **The filter panel.** Its opener is the filter glyph up in the teal header (see "Right
            side items"), and the search glyph beside it — so the white strip that used to carry
            both, on every non-workspace page, is gone entirely (owner, 2026-08-17). It was a whole
            row of chrome for two controls, and on Settings and Calendar it carried a search box
            that filtered nothing.

            **Not phone-only any more** (owner, 2026-08-20): the same questions in the same shapes
            open as a drawer from the bottom on a phone and as a panel from the right on a laptop.
            The two dropdowns that used to hold them on a wide screen are gone — a filter is never
            a dropdown now.
          */}
          {isDashboard && (
            <div>
              <ToolbarSheet
                open={mobileFiltersOpen}
                onOpenChange={setMobileFiltersOpen}
                title="Filter & Sort"
                // **Everything**, including the status questions that used to be exempt.
                onReset={() => resetList("goals", GOALS_SCOPE)}
                resetDisabled={!goalsActive}
                locked={goalsLocked}
                onLockedChange={(next) => setLocked("goals", GOALS_SCOPE, next)}
              >
                <SheetGroup title="Status">
                  {/* One line of success-ramp pills. Stacked as full-width rows, three short
                      words took three lines of the sheet for nothing. */}
                  <SheetPills
                    options={GOAL_STATUS_CHOICES}
                    value={status}
                    onChange={setStatus}
                  />
                </SheetGroup>

                {/* Whether there IS a date, before the range that asks which dates to keep — the
                    question Android has asked since 2026-08-18 and the web could not (owner,
                    2026-08-20). Info-toned, so it does not read as a second status. */}
                <SheetGroup title="Deadline">
                  <SheetPills
                    options={GOAL_DEADLINE_CHOICES}
                    value={goalDeadline}
                    onChange={setGoalDeadline}
                    tone="info"
                  />
                </SheetGroup>

                <SheetGroup title="Deadline range">
                  <SheetDateRange
                    from={deadlineFrom}
                    to={deadlineTo}
                    onFromChange={setDeadlineFrom}
                    onToChange={setDeadlineTo}
                  />
                </SheetGroup>

                <SheetGroup title="Confidence">
                  <SheetConfidence
                    value={confidence}
                    onChange={setConfidence}
                  />
                </SheetGroup>

                {/* Three questions, three shapes (owner, 2026-08-17): filter values are pills, the
                    direction — a modifier, not a value — is a segmented control, and the sort keys
                    are choice cards. **Direction first**: it is one short line, so asking the small
                    question first keeps the sheet from opening on a wall of cards. */}
                {/* **Always asked, in every view mode.** They used to be hidden on the table,
                    which left the trigger's dot lit and "Reset all" enabled over two questions the
                    panel was no longer showing — and Reset then silently changed a sort the user
                    could not see. The table reads the same order, so there was nothing to hide. */}

                <SheetGroup title="Direction">
                  <SheetSegmented
                    options={SORT_DIRECTION_CHOICES}
                    value={sortDirection}
                    onChange={setSortDirection}
                  />
                </SheetGroup>
                <SheetGroup title="Sort by">
                  <SheetChoiceCards
                    options={SORT_CHOICES}
                    value={sort}
                    onChange={setSort}
                  />
                </SheetGroup>
              </ToolbarSheet>
            </div>
          )}
        </header>

        {/*
          **The app's one message card, sitting in the flow** — the web twin of Android's
          `SpiraInlineBanner` (owner, 2026-08-21). These were two bespoke tinted strips: the offline
          one drew itself in `amber-50 / amber-300 / amber-800`, Tailwind defaults that are **not in
          the Spira palette at all**, and the error one was a red-tinted block with red type, which
          is an error shouting twice. Now they are the same card every other message in the app is,
          and only the kind changes.

          The mark is the kind's own, not a bespoke one: `GlobeOff` said more than a triangle, but a
          set of messages that each pick their own glyph stops reading as one family — which is the
          whole point of the card.
        */}
        {isOffline && (
          <div className="px-4 pt-3 sm:px-6">
            <NoticeCard kind="warning" onDismiss={null}>
              You&apos;re offline. Your goals are still visible — changes will
              sync when you reconnect.
            </NoticeCard>
          </div>
        )}

        {syncError && !isOffline && (
          <div className="px-4 pt-3 sm:px-6">
            <NoticeCard kind="error" onDismiss={null} role="alert">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>{syncError}</span>
                <button
                  type="button"
                  onClick={() => void refreshGoals()}
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs font-semibold text-foreground transition-colors hover:bg-black/5"
                >
                  <RefreshCw className="h-3 w-3" />
                  Refresh
                </button>
              </div>
            </NoticeCard>
          </div>
        )}

        {isLoadingGoals && !syncError && !isOffline && (
          <div
            className="border-b hairline bg-primary-soft px-4 py-2 text-sm text-primary sm:px-6"
            role="status"
          >
            Loading your goals…
          </div>
        )}
        <main className="spira-main min-w-0 flex-1 md:min-w-[390px]">
          {children}
        </main>
      </div>
    </div>
  );
}
