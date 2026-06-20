import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  Search,
  SlidersHorizontal,
  ArrowDownUp,
  ChevronDown,
  LogOut,
  X,
  GlobeOff,
  Cable,
  RefreshCw,
} from "lucide-react";
import { useAi } from "@/components/ai/ai-store";
import { AiPanel } from "@/components/ai/AiPanel";
import { useSpira } from "@/lib/spira/store";
import { useAuth } from "@/lib/spira/auth";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import {
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
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from "@/components/ui/dropdown-menu";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
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
  const isLoadingGoals = useSpira((s) => s.isLoading);
  const syncError = useSpira((s) => s.syncError);
  const syncErrorKind = useSpira((s) => s.syncErrorKind);
  const authUser = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
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

  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  const isDashboard = path === "/";
  const isCalendar = path.startsWith("/calendar");
  const isWorkspace = path.startsWith("/goals/");
  const filtersActive = Boolean(
    deadlineFrom || deadlineTo || confidence || status !== "all",
  );
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
        {/* Top bar */}
        <header
          className={cn(
            "sticky top-0 z-30 transition-colors duration-200",
            isWorkspace
              ? "bg-primary border-b border-primary/20"
              : "bg-background/85 backdrop-blur border-b hairline",
          )}
        >
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
              isDashboard &&
                !isWorkspace &&
                "bg-primary text-white sm:bg-transparent sm:text-foreground",
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
                      className="text-[32px] font-extrabold tracking-normal text-white hover:text-white/90 transition-colors leading-none"
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
                      className={cn(
                        "text-[32px] font-extrabold tracking-normal transition-colors leading-none",
                        isDashboard
                          ? "text-white hover:text-white/90 sm:text-[#ea580c] sm:hover:text-[#ea580c]/90"
                          : "text-[#ea580c] hover:text-[#ea580c]/90",
                      )}
                    >
                      spira
                    </Link>
                  )}
                  {!isAiOpen && (
                    <button
                      onClick={() => openAi()}
                      className={cn(
                        "whitespace-nowrap text-[20px] font-normal transition-colors leading-none pt-1",
                        isDashboard
                          ? "text-white hover:text-white/90 sm:text-primary sm:hover:text-primary/90"
                          : "text-primary hover:text-primary/90",
                      )}
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
                  className={cn(
                    "w-full h-10 pl-9 pr-8 rounded-md text-sm outline-none transition-colors",
                    isWorkspace
                      ? "bg-white border-transparent text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/50 shadow-sm"
                      : "bg-surface border border-input placeholder:text-muted-foreground/75 focus:border-primary focus:ring-[3px] focus:ring-ring",
                  )}
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
              {/* Filter */}
              {showFilterControls && (
                <div className="hidden lg:flex items-center gap-1">
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      className={cn(
                        "inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80",
                        filtersActive &&
                          "border-primary/40 text-primary bg-primary-soft",
                      )}
                    >
                      <SlidersHorizontal className="h-3.5 w-3.5" />
                      {filtersActive ? "Filters on" : "Filters"}
                    </DropdownMenuTrigger>
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
                      <DropdownMenuLabel>Exact confidence</DropdownMenuLabel>
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
                      className="grid h-8 w-8 place-items-center rounded-md border hairline-strong text-primary hover:bg-primary-soft"
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
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      className={cn(
                        "inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80",
                        sortActive &&
                          "border-primary/40 text-primary bg-primary-soft",
                      )}
                    >
                      <ArrowDownUp className="h-3.5 w-3.5" />
                      {sortActive
                        ? `Sort ${sortDirection === "asc" ? "up" : "down"}`
                        : "Sort"}
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-52">
                      <DropdownMenuRadioGroup
                        value={sort}
                        onValueChange={(v) => setSort(v as SortKey)}
                      >
                        <DropdownMenuRadioItem value="recent">
                          Most recent
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="deadline">
                          Deadline soonest
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="progress">
                          Progress
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="confidence">
                          Confidence
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="title">
                          Title A-Z
                        </DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                      <DropdownMenuSeparator />
                      <DropdownMenuRadioGroup
                        value={sortDirection}
                        onValueChange={(v) =>
                          setSortDirection(v as SortDirection)
                        }
                      >
                        <DropdownMenuRadioItem value="asc">
                          Ascending
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="desc">
                          Descending
                        </DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuContent>
                  </DropdownMenu>
                  {sortActive && (
                    <button
                      onPointerDown={resetSort}
                      onClick={resetSort}
                      className="grid h-8 w-8 place-items-center rounded-md border hairline-strong text-primary hover:bg-primary-soft"
                      aria-label="Reset sort"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              )}
              {/* User */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    className="flex items-center gap-2 outline-none rounded-md focus-visible:ring-2 focus-visible:ring-ring"
                    aria-label="User menu"
                  >
                    {authUser?.pictureUrl ? (
                      <img
                        src={authUser.pictureUrl}
                        alt={authUser.name}
                        referrerPolicy="no-referrer"
                        className="h-8 w-8 rounded-full object-cover border border-white/20"
                      />
                    ) : (
                      <div className="h-8 w-8 rounded-full border grid place-items-center text-xs font-semibold bg-primary-soft border-primary/30 text-primary">
                        {authUser ? getInitials(authUser.name) : "?"}
                      </div>
                    )}
                    <div className="hidden md:block leading-tight text-right">
                      <div
                        className={cn(
                          "text-xs font-semibold",
                          isWorkspace ? "text-white" : "text-foreground",
                        )}
                      >
                        {authUser?.name ?? ""}
                      </div>
                    </div>
                    <ChevronDown
                      className={cn(
                        "hidden md:inline h-3.5 w-3.5",
                        isWorkspace ? "text-white/70" : "text-muted-foreground",
                      )}
                    />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-52">
                  <DropdownMenuLabel className="font-normal">
                    <div className="text-sm font-semibold truncate">
                      {authUser?.name}
                    </div>
                    <div className="text-xs text-muted-foreground truncate">
                      {authUser?.email}
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    className="gap-2 text-destructive focus:text-destructive"
                    onSelect={async () => {
                      await logout();
                      void navigate({ to: "/login" });
                    }}
                  >
                    <LogOut className="h-4 w-4" />
                    Sign out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>

          {/* Mobile secondary row: search + filters + sort — only on non-workspace pages */}
          {!isWorkspace && (
            <div className="sm:hidden border-t hairline px-4 py-2 flex min-h-11 items-center gap-2 bg-background">
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search goals..."
                  aria-label="Search goals"
                  className="h-8 w-full rounded-md border border-input bg-surface pl-8 pr-7 text-xs outline-none transition-colors placeholder:text-muted-foreground/75 focus:border-primary focus:ring-[3px] focus:ring-ring"
                />
                {query && (
                  <button
                    onClick={() => setQuery("")}
                    className="absolute right-1.5 top-1/2 grid h-5 w-5 -translate-y-1/2 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                    aria-label="Clear search"
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
              {(filtersActive || (showSortControls && sortActive)) && (
                <button
                  onClick={() => {
                    resetFilters();
                    resetSort();
                  }}
                  className="grid h-7 w-7 shrink-0 place-items-center rounded-md border hairline-strong text-primary bg-primary-soft"
                  aria-label="Reset filters and sort"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
              <button
                onClick={() => setMobileFiltersOpen(true)}
                className={cn(
                  "inline-flex items-center gap-1 h-7 px-2.5 rounded-md border hairline-strong text-xs text-foreground/80 shrink-0",
                  (filtersActive || (showSortControls && sortActive)) &&
                    "border-primary/40 text-primary bg-primary-soft",
                )}
              >
                <SlidersHorizontal className="h-3 w-3" />{" "}
                {filtersActive || (showSortControls && sortActive)
                  ? "Active"
                  : "Filter & Sort"}
              </button>
              <Drawer
                open={mobileFiltersOpen}
                onOpenChange={setMobileFiltersOpen}
              >
                <DrawerContent className="mt-0 px-0 h-[92vh] max-h-[92vh] flex flex-col">
                  <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
                    <h2 className="font-sans font-bold text-lg">
                      Filters & Sort
                    </h2>
                    <button
                      onClick={() => setMobileFiltersOpen(false)}
                      className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
                      aria-label="Close"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>

                  <div className="overflow-y-auto flex-1 min-h-0 px-6 pt-2 pb-6 space-y-5">
                    {/* Deadline range */}
                    <div>
                      <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wide">
                        Deadline range
                      </p>
                      <DeadlineRangeControls
                        deadlineFrom={deadlineFrom}
                        deadlineTo={deadlineTo}
                        setDeadlineFrom={setDeadlineFrom}
                        setDeadlineTo={setDeadlineTo}
                      />
                    </div>

                    {/* Confidence */}
                    <div>
                      <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wide">
                        Exact confidence
                      </p>
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
                    </div>

                    {/* Status */}
                    <div>
                      <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wide">
                        Status
                      </p>
                      <div className="space-y-0.5">
                        {(
                          [
                            { value: "all", label: "All goals" },
                            { value: "achieved", label: "Only achieved" },
                            {
                              value: "not-achieved",
                              label: "Only not achieved",
                            },
                          ] as const
                        ).map((opt) => (
                          <button
                            key={opt.value}
                            onClick={() => setStatus(opt.value)}
                            className={cn(
                              "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                              status === opt.value
                                ? "bg-primary/10 text-primary font-semibold"
                                : "text-foreground hover:bg-secondary",
                            )}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Sort (only on cards view) */}
                    {showSortControls && (
                      <>
                        <div>
                          <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wide">
                            Sort by
                          </p>
                          <div className="space-y-0.5">
                            {(
                              [
                                { value: "recent", label: "Most recent" },
                                {
                                  value: "deadline",
                                  label: "Deadline soonest",
                                },
                                { value: "progress", label: "Progress" },
                                { value: "confidence", label: "Confidence" },
                                { value: "title", label: "Title A-Z" },
                              ] as const
                            ).map((opt) => (
                              <button
                                key={opt.value}
                                onClick={() => setSort(opt.value)}
                                className={cn(
                                  "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                                  sort === opt.value
                                    ? "bg-primary/10 text-primary font-semibold"
                                    : "text-foreground hover:bg-secondary",
                                )}
                              >
                                {opt.label}
                              </button>
                            ))}
                          </div>
                        </div>
                        <div>
                          <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wide">
                            Direction
                          </p>
                          <div className="space-y-0.5">
                            {(
                              [
                                { value: "asc", label: "Ascending" },
                                { value: "desc", label: "Descending" },
                              ] as const
                            ).map((opt) => (
                              <button
                                key={opt.value}
                                onClick={() => setSortDirection(opt.value)}
                                className={cn(
                                  "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                                  sortDirection === opt.value
                                    ? "bg-primary/10 text-primary font-semibold"
                                    : "text-foreground hover:bg-secondary",
                                )}
                              >
                                {opt.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      </>
                    )}
                  </div>

                  <div
                    className="shrink-0 bg-surface px-6 pt-3 flex gap-3"
                    style={{
                      paddingBottom: "max(env(safe-area-inset-bottom), 12px)",
                    }}
                  >
                    {(filtersActive || (showSortControls && sortActive)) && (
                      <button
                        onClick={() => {
                          resetFilters();
                          resetSort();
                        }}
                        className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
                      >
                        Reset all
                      </button>
                    )}
                    <button
                      onClick={() => setMobileFiltersOpen(false)}
                      className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 transition-colors"
                    >
                      Done
                    </button>
                  </div>
                </DrawerContent>
              </Drawer>
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
