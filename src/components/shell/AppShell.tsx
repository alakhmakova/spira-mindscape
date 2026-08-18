import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  Search,
  ChevronDownSolid,
  Filter,
  SortAscending,
  SortDescending,
  X,
  GlobeOff,
  Cable,
  RefreshCw,
} from "@/components/spira/icons";
import { useAi } from "@/components/ai/ai-store";
import { AiPanel } from "@/components/ai/AiPanel";
import { useSpira } from "@/lib/spira/store";
import { useAuth } from "@/lib/spira/auth";
import { useApplyAppFont } from "@/lib/spira/app-font";
import { useActivityGate } from "@/lib/useActivityGate";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import {
  useResetQueryOnNavigate,
  useShellFilters,
  type GoalStatusFilter,
  type SortDirection,
  type SortKey,
} from "./shell-store";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from "@/components/ui/dropdown-menu";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import {
  MenuChoice,
  MenuGroup,
  SheetChoiceCards,
  SheetGroup,
  SheetPills,
  SheetSegmented,
  ToolbarMenu,
  ToolbarSheet,
  ToolbarTrigger,
} from "@/components/spira/ListToolbar";
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
  const {
    query,
    setQuery,
    sort,
    setSort,
    sortDirection,
    setSortDirection,
    resetSort,
    deadlineFrom,
    setDeadlineFrom,
    deadlineTo,
    setDeadlineTo,
    confidence,
    setConfidence,
    status,
    setStatus,
    resetFilters,
    viewMode,
  } = useShellFilters();

  // A search is scoped to the screen it was typed on — see useResetQueryOnNavigate.
  useResetQueryOnNavigate(path);

  // The body face the owner picked in Settings → Fonts, applied to the whole app. Mounted here
  // because the shell wraps every route, so the choice survives navigation without a flash.
  useApplyAppFont();

  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  // The phone's search is a glyph in the teal header until it is opened; opening swaps the whole
  // header row for a field, the way Android's `SearchTopBar` does (owner, 2026-08-17).
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);

  const isDashboard = path === "/";
  const isCalendar = path.startsWith("/calendar");
  const isWorkspace = path.startsWith("/goals/");
  // "not-achieved" is the default view, so it isn't counted as an active filter —
  // only deviating from it (All / Only achieved) or setting a date/confidence lights
  // the reset button and adds to the trigger's bracketed count.
  const filtersActive = Boolean(
    deadlineFrom || deadlineTo || confidence || status !== "not-achieved",
  );
  const activeFilterCount =
    (deadlineFrom || deadlineTo ? 1 : 0) +
    (confidence ? 1 : 0) +
    (status !== "not-achieved" ? 1 : 0);
  const sortActive = sort !== "recent" || sortDirection !== "desc";
  // Show filters everywhere except workspace/calendar; show sort only on cards view (timeline has its own ordering)
  const showFilterControls = !isWorkspace && !isCalendar;
  const showSortControls = !isWorkspace && !isCalendar && viewMode === "cards";

  const goals = useSpira((s) => s.goals);
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
    <div className="flex min-h-screen bg-background">
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
          */}
          {mobileSearchOpen && isDashboard && (
            <div className="flex h-16 items-center gap-2.5 px-4 sm:hidden">
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
                  placeholder="Search for goals"
                  aria-label="Search goals"
                  className="h-10 w-full rounded-md bg-white pl-9 pr-3 text-sm text-foreground shadow-sm outline-none placeholder:text-muted-foreground focus:ring-2 focus:ring-white/60"
                />
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
                    "grid gap-3",
                    isAiOpen
                      ? "grid-cols-[1fr_minmax(0,320px)_1fr]"
                      : "grid-cols-[1fr_minmax(0,600px)_1fr]",
                  )
                : "flex gap-3 sm:gap-5",
              // While the phone's search is open it IS the header row; the normal row steps out.
              // **Last in the list on purpose**: `cn` resolves conflicting display utilities in
              // favour of the later one, so put before the `flex` above this was simply ignored
              // and the field stacked on top of the row instead of replacing it.
              mobileSearchOpen && isDashboard && "hidden sm:flex",
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
                  ? "flex w-full min-w-0"
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
                  className="w-full h-10 pl-9 pr-8 rounded-md text-sm outline-none transition-colors bg-white border-transparent text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/50 shadow-sm"
                />
                {query && (
                  <button
                    onClick={() => setQuery("")}
                    className={cn(
                      "absolute right-2 top-1/2 -translate-y-1/2 h-5 w-5 grid place-items-center rounded-full transition-colors",
                      "text-muted-foreground hover:text-foreground hover:bg-secondary",
                    )}
                    aria-label="Clear search"
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
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
            <div className="flex shrink-0 items-center gap-3 sm:gap-4 justify-end">
              {/* **On a phone the search and the filters live HERE**, in the teal bar, as two bare
                  glyphs — the Android header's arrangement (owner, 2026-08-17). They used to sit in
                  a white strip under the header, which is a whole row of chrome for two controls. */}
              {isDashboard && (
                <div className="flex items-center gap-1 sm:hidden">
                  <button
                    type="button"
                    onClick={() => setMobileSearchOpen(true)}
                    aria-label="Search goals"
                    className="relative grid h-9 w-9 place-items-center rounded-md text-white/85 transition-colors hover:bg-white/15 hover:text-white"
                  >
                    <span className="relative inline-flex">
                      <Search className="h-[18px] w-[18px]" />
                      {query && (
                        <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
                      )}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setMobileFiltersOpen(true)}
                    aria-label="Filter and sort goals"
                    className="relative grid h-9 w-9 place-items-center rounded-md text-white/85 transition-colors hover:bg-white/15 hover:text-white"
                  >
                    <span className="relative inline-flex">
                      <Filter className="h-[18px] w-[18px]" />
                      {(filtersActive || (showSortControls && sortActive)) && (
                        <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
                      )}
                    </span>
                  </button>
                </div>
              )}
              {/* Filter */}
              {showFilterControls && (
                <div className="hidden lg:flex items-center gap-1">
                  <DropdownMenu>
                    <ToolbarTrigger
                      label="Filter"
                      count={activeFilterCount}
                      ariaLabel="Filter goals"
                      className="text-white hover:text-white/80"
                      leadingIcon={<Filter className="h-4 w-4 shrink-0" />}
                    />
                    <DropdownMenuContent
                      align="end"
                      className="w-72 space-y-2 p-2"
                    >
                      <DropdownMenuLabel>Deadline range</DropdownMenuLabel>
                      <DeadlineRangeControls
                        deadlineFrom={deadlineFrom}
                        deadlineTo={deadlineTo}
                        setDeadlineFrom={setDeadlineFrom}
                        setDeadlineTo={setDeadlineTo}
                      />
                      <DropdownMenuSeparator />
                      <DropdownMenuLabel>Confidence</DropdownMenuLabel>
                      <div className="grid grid-cols-5 gap-1 px-2">
                        {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
                          <button
                            key={n}
                            onClick={() =>
                              setConfidence(
                                confidence === String(n) ? "" : String(n),
                              )
                            }
                            className={cn(
                              "h-8 rounded-md border hairline text-xs font-semibold",
                              confidence === String(n)
                                ? "bg-primary text-primary-foreground"
                                : "bg-surface text-foreground",
                            )}
                          >
                            {n}
                          </button>
                        ))}
                      </div>
                      <DropdownMenuSeparator />
                      <DropdownMenuRadioGroup
                        value={status}
                        onValueChange={(v) => setStatus(v as GoalStatusFilter)}
                      >
                        <DropdownMenuRadioItem value="all">
                          All goals
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="achieved">
                          Only achieved
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="not-achieved">
                          Only not achieved
                        </DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuContent>
                  </DropdownMenu>
                  {filtersActive && (
                    <button
                      onPointerDown={resetFilters}
                      onClick={resetFilters}
                      className="grid h-8 w-8 place-items-center rounded-md border border-white/40 text-white hover:bg-white/15"
                      aria-label="Reset filters"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              )}

              {/* Sort */}
              {showSortControls && (
                <div className="hidden lg:flex items-center gap-1">
                  {/* The two questions side by side: what to order by, then which way. */}
                  <ToolbarMenu
                    label={
                      SORT_CHOICES.find((c) => c.value === sort)?.label ??
                      "Sort"
                    }
                    ariaLabel="Sort goals"
                    triggerClassName="text-white hover:text-white/80"
                    leadingIcon={
                      sortDirection === "asc" ? (
                        <SortAscending className="h-4 w-4 shrink-0" />
                      ) : (
                        <SortDescending className="h-4 w-4 shrink-0" />
                      )
                    }
                  >
                    <MenuGroup title="Sort by">
                      {SORT_CHOICES.map((c) => (
                        <MenuChoice
                          key={c.value}
                          label={c.label}
                          selected={sort === c.value}
                          onSelect={() => setSort(c.value)}
                        />
                      ))}
                    </MenuGroup>
                    <MenuGroup title="Direction">
                      {SORT_DIRECTION_CHOICES.map((c) => (
                        <MenuChoice
                          key={c.value}
                          label={c.label}
                          variant="aux"
                          icon={c.icon}
                          selected={sortDirection === c.value}
                          onSelect={() => setSortDirection(c.value)}
                        />
                      ))}
                    </MenuGroup>
                  </ToolbarMenu>
                  {sortActive && (
                    <button
                      onPointerDown={resetSort}
                      onClick={resetSort}
                      className="grid h-8 w-8 place-items-center rounded-md border border-white/40 text-white hover:bg-white/15"
                      aria-label="Reset sort"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
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
            **The phone's filter sheet.** Its opener is the filter glyph up in the teal header now
            (see "Right side items"), and the search glyph beside it — so the white strip that used
            to carry both, on every non-workspace page, is gone entirely (owner, 2026-08-17). It was
            a whole row of chrome for two controls, and on Settings and Calendar it carried a search
            box that filtered nothing.
          */}
          {isDashboard && (
            <div className="sm:hidden">
              <ToolbarSheet
                open={mobileFiltersOpen}
                onOpenChange={setMobileFiltersOpen}
                title="Filter & Sort"
                onReset={() => {
                  resetFilters();
                  resetSort();
                }}
                resetDisabled={
                  !filtersActive && !(showSortControls && sortActive)
                }
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

                <SheetGroup title="Deadline range">
                  <DeadlineRangeControls
                    deadlineFrom={deadlineFrom}
                    deadlineTo={deadlineTo}
                    setDeadlineFrom={setDeadlineFrom}
                    setDeadlineTo={setDeadlineTo}
                  />
                </SheetGroup>

                <SheetGroup title="Confidence">
                  <div className="grid grid-cols-5 gap-1">
                    {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
                      <button
                        key={n}
                        onClick={() =>
                          setConfidence(
                            confidence === String(n) ? "" : String(n),
                          )
                        }
                        className={cn(
                          "h-9 rounded-md border hairline text-xs font-semibold",
                          confidence === String(n)
                            ? "bg-primary text-primary-foreground"
                            : "bg-surface text-foreground hover:bg-secondary",
                        )}
                      >
                        {n}
                      </button>
                    ))}
                  </div>
                </SheetGroup>

                {/* Three questions, three shapes (owner, 2026-08-17): filter values are pills, the
                    direction — a modifier, not a value — is a segmented control, and the sort keys
                    are choice cards. **Direction first**: it is one short line, so asking the small
                    question first keeps the sheet from opening on a wall of cards. */}
                {showSortControls && (
                  <>
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
                  </>
                )}
              </ToolbarSheet>
            </div>
          )}
        </header>

        {/* Offline banner — fires instantly from browser events */}
        {isOffline && (
          <div
            className="border-b border-amber-300/40 bg-amber-50 px-4 py-2 text-sm text-amber-800 dark:bg-amber-950/30 dark:text-amber-300 sm:px-6"
            role="status"
            aria-live="polite"
          >
            <div className="flex items-center gap-2">
              <GlobeOff className="h-4 w-4 shrink-0" />
              <span>
                You&apos;re offline. Your goals are still visible — changes will
                sync when you reconnect.
              </span>
            </div>
          </div>
        )}

        {/* API error banner — only when not already covered by offline banner */}
        {syncError && !isOffline && (
          <div
            className="border-b border-destructive/25 bg-destructive/10 px-4 py-2 text-sm text-destructive sm:px-6"
            role="alert"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                {syncErrorKind === "network" ? (
                  <GlobeOff className="h-4 w-4 shrink-0" />
                ) : (
                  <Cable className="h-4 w-4 shrink-0" />
                )}
                <span>{syncError}</span>
              </div>
              <button
                type="button"
                onClick={() => void refreshGoals()}
                className="inline-flex items-center gap-1.5 rounded-md border border-destructive/30 px-2.5 py-1 text-xs font-semibold hover:bg-destructive/10"
              >
                <RefreshCw className="h-3 w-3" />
                Refresh
              </button>
            </div>
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

function DeadlineRangeControls({
  deadlineFrom,
  deadlineTo,
  setDeadlineFrom,
  setDeadlineTo,
}: {
  deadlineFrom: string;
  deadlineTo: string;
  setDeadlineFrom: (value: string) => void;
  setDeadlineTo: (value: string) => void;
}) {
  return (
    <div
      className="grid grid-cols-2 gap-2 px-2"
      onClick={(e) => e.stopPropagation()}
    >
      <DeadlinePopover
        iso={deadlineFrom || undefined}
        onChange={(next) => setDeadlineFrom(next ?? "")}
        variant="button"
        placeholder="From"
        hideDaysLeft
        disableScroll
        className="h-9 justify-start px-2 text-xs"
      />
      <DeadlinePopover
        iso={deadlineTo || undefined}
        onChange={(next) => setDeadlineTo(next ?? "")}
        variant="button"
        placeholder="To"
        hideDaysLeft
        disableScroll
        className="h-9 justify-start px-2 text-xs"
      />
    </div>
  );
}
