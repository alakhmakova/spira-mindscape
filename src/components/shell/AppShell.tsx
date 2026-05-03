import { Link, useRouterState, useParams } from "@tanstack/react-router";
import {
  Search,
  SlidersHorizontal,
  ArrowDownUp,
  Sparkles,
  ChevronDown,
  X,
  Calendar,
} from "lucide-react";
import { useAi } from "@/components/ai/ai-store";
import { useSpira } from "@/lib/spira/store";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import { useShellFilters } from "./shell-store";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuCheckboxItem,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export function AppShell({ children }: { children: React.ReactNode }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const openAi = useAi((s) => s.open);
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
  } = useShellFilters();

  const isCalendar = path.startsWith("/calendar");
  const isWorkspace = path.startsWith("/goals/");
  const filtersActive = Boolean(deadlineFrom || deadlineTo || confidence || status !== "all");
  const sortActive = sort !== "recent" || sortDirection !== "desc";

  const goals = useSpira((s) => s.goals);
  const searchResults = query.trim() === "" ? [] : goals.filter(g => g.title.toLowerCase().includes(query.toLowerCase().trim())).slice(0, 5);

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top bar */}
      <header
        className={cn(
          "sticky top-0 z-30 transition-colors duration-200",
          isWorkspace
            ? "bg-primary border-b border-primary/20"
            : "bg-background/85 backdrop-blur border-b hairline"
        )}
      >
        <div className={cn("w-full px-4 sm:px-6 h-16 items-center", isWorkspace ? "grid grid-cols-[1fr_minmax(auto,600px)_1fr]" : "flex gap-3 sm:gap-5")}>
          {/* Brand */}
          <div className={cn("flex items-center", isWorkspace ? "justify-start gap-4" : "gap-2")}>
            {isWorkspace ? (
              <>
                <Link to="/" className="text-[32px] font-extrabold tracking-normal text-white hover:text-white/90 transition-colors leading-none">
                  spira
                </Link>
                <button
                  onClick={() => openAi()}
                  className="text-white hover:text-white/90 text-[20px] font-normal transition-colors leading-none pt-1"
                >
                  ai coach
                </button>
              </>
            ) : (
              <>
                <Link to="/" className="text-[32px] font-extrabold tracking-normal text-[#ea580c] hover:text-[#ea580c]/90 transition-colors leading-none">
                  spira
                </Link>
                <button
                  onClick={() => openAi()}
                  className="text-primary hover:text-primary/90 text-[20px] font-normal transition-colors leading-none pt-1"
                >
                  ai coach
                </button>
              </>
            )}
          </div>

          {/* Spacer (only for non-workspace) */}
          {!isWorkspace && <div className="flex-1" />}

          {/* Search (Centered for workspace, inline for non-workspace) */}
          <div className={cn("flex", isWorkspace ? "justify-center w-full" : "w-32 sm:w-64 shrink-0")}>
            <div className="relative w-full max-w-2xl">
              <Search className={cn("pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4", isWorkspace ? "text-muted-foreground" : "text-muted-foreground")} />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={isWorkspace ? "Search for answers..." : "Search goals…"}
                className={cn(
                  "w-full h-10 pl-9 pr-8 rounded-md text-sm outline-none transition-colors",
                  isWorkspace
                    ? "bg-white border-transparent text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/50 shadow-sm"
                    : "bg-surface border border-input placeholder:text-muted-foreground/75 focus:border-primary focus:ring-[3px] focus:ring-ring"
                )}
              />
              {query && (
                <button
                  onClick={() => setQuery("")}
                  className={cn("absolute right-2 top-1/2 -translate-y-1/2 h-5 w-5 grid place-items-center rounded-full transition-colors", "text-muted-foreground hover:text-foreground hover:bg-secondary")}
                  aria-label="Clear search"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
              {isWorkspace && query.trim() !== "" && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-surface border hairline rounded-md shadow-lg overflow-hidden z-50">
                  {searchResults.length > 0 ? (
                    searchResults.map(r => (
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
                    <div className="px-3 py-2 text-sm text-muted-foreground italic">No goals found</div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Right side items */}
          <div className="flex items-center gap-3 sm:gap-4 justify-end">
            {/* Filter */}
            {!isWorkspace && (
              <div className="hidden lg:flex items-center gap-1">
                <DropdownMenu>
                  <DropdownMenuTrigger
                    className={cn(
                      "inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80",
                      filtersActive && "border-primary/40 text-primary bg-primary-soft",
                    )}
                  >
                    <SlidersHorizontal className="h-3.5 w-3.5" />
                    {filtersActive ? "Filters on" : "Filters"}
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-72 space-y-2 p-2">
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
                        <button key={n} onClick={() => setConfidence(confidence === String(n) ? "" : String(n))} className={cn("h-8 rounded-md border hairline text-xs font-semibold", confidence === String(n) ? "bg-primary text-primary-foreground" : "bg-surface text-foreground")}>{n}</button>
                      ))}
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuRadioGroup value={status} onValueChange={(v) => setStatus(v as any)}>
                      <DropdownMenuRadioItem value="all">All goals</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="achieved">Only achieved</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="not-achieved">Only not achieved</DropdownMenuRadioItem>
                    </DropdownMenuRadioGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
                {filtersActive && <button onClick={resetFilters} className="grid h-8 w-8 place-items-center rounded-md border hairline-strong text-primary hover:bg-primary-soft" aria-label="Reset filters"><X className="h-3.5 w-3.5" /></button>}
              </div>
            )}

            {/* Sort */}
            {!isWorkspace && (
              <div className="hidden lg:flex items-center gap-1">
                <DropdownMenu>
                  <DropdownMenuTrigger className={cn("inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80", sortActive && "border-primary/40 text-primary bg-primary-soft")}>
                    <ArrowDownUp className="h-3.5 w-3.5" />
                    {sortActive ? `Sort ${sortDirection === "asc" ? "↑" : "↓"}` : "Sort"}
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-52">
                    <DropdownMenuRadioGroup
                      value={sort}
                      onValueChange={(v) => setSort(v as any)}
                    >
                      <DropdownMenuRadioItem value="recent">Most recent</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="deadline">Deadline soonest</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="progress">Progress</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="confidence">Confidence</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="title">Title A→Z</DropdownMenuRadioItem>
                    </DropdownMenuRadioGroup>
                    <DropdownMenuSeparator />
                    <DropdownMenuRadioGroup value={sortDirection} onValueChange={(v) => setSortDirection(v as any)}>
                      <DropdownMenuRadioItem value="asc">Ascending</DropdownMenuRadioItem>
                      <DropdownMenuRadioItem value="desc">Descending</DropdownMenuRadioItem>
                    </DropdownMenuRadioGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
                {sortActive && <button onClick={resetSort} className="grid h-8 w-8 place-items-center rounded-md border hairline-strong text-primary hover:bg-primary-soft" aria-label="Reset sort"><X className="h-3.5 w-3.5" /></button>}
              </div>
            )}



            {/* Mobile AI Icon Button (Global) */}
            {!isWorkspace && (
              <button
                onClick={() => openAi()}
                className="sm:hidden inline-flex items-center justify-center h-9 w-9 shrink-0 rounded-md bg-primary text-primary-foreground hover:bg-primary/90"
                title="Spira AI"
              >
                <Sparkles className="h-4 w-4" />
              </button>
            )}

            {/* User */}
            <div className={cn("flex items-center gap-2", isWorkspace ? "" : "pl-2 md:border-l md:pl-3 md:hairline")}>
              <div className={cn("h-8 w-8 rounded-full border grid place-items-center text-xs font-semibold", isWorkspace ? "bg-white/10 border-white/20 text-white" : "bg-primary-soft border-primary/30 text-primary")}>
                SU
              </div>
              <div className="hidden md:block leading-tight text-right">
                <div className={cn("text-xs font-semibold", isWorkspace ? "text-white" : "text-foreground")}>Spira User</div>
              </div>
              <ChevronDown className={cn("hidden md:inline h-3.5 w-3.5", isWorkspace ? "text-white/70" : "text-muted-foreground")} />
            </div>
          </div>
        </div>

        {/* Mobile secondary row: filters + sort */}
        {!isWorkspace && (
          <div className="sm:hidden border-t hairline px-4 h-11 flex items-center gap-2 overflow-x-auto">
            {filtersActive && <button onClick={resetFilters} className="grid h-7 w-7 shrink-0 place-items-center rounded-md border hairline-strong text-primary bg-primary-soft" aria-label="Reset filters"><X className="h-3 w-3" /></button>}
            <DropdownMenu>
              <DropdownMenuTrigger className={cn("inline-flex items-center gap-1 h-7 px-2.5 rounded-md border hairline-strong text-xs text-foreground/80", filtersActive && "border-primary/40 text-primary bg-primary-soft")}>
                <SlidersHorizontal className="h-3 w-3" /> {filtersActive ? "Filters on" : "Filter"}
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-72 space-y-2 p-2">
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
                    <button key={n} onClick={() => setConfidence(confidence === String(n) ? "" : String(n))} className={cn("h-8 rounded-md border hairline text-xs font-semibold", confidence === String(n) ? "bg-primary text-primary-foreground" : "bg-surface text-foreground")}>{n}</button>
                  ))}
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuRadioGroup value={status} onValueChange={(v) => setStatus(v as any)}>
                  <DropdownMenuRadioItem value="all">All goals</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="achieved">Only achieved</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="not-achieved">Only not achieved</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
              </DropdownMenuContent>
            </DropdownMenu>
            {sortActive && <button onClick={resetSort} className="grid h-7 w-7 shrink-0 place-items-center rounded-md border hairline-strong text-primary bg-primary-soft" aria-label="Reset sort"><X className="h-3 w-3" /></button>}
            <DropdownMenu>
              <DropdownMenuTrigger className={cn("inline-flex items-center gap-1 h-7 px-2.5 rounded-md border hairline-strong text-xs text-foreground/80", sortActive && "border-primary/40 text-primary bg-primary-soft")}>
                <ArrowDownUp className="h-3 w-3" /> {sortActive ? `Sort ${sortDirection === "asc" ? "↑" : "↓"}` : "Sort"}
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuRadioGroup
                  value={sort}
                  onValueChange={(v) => setSort(v as any)}
                >
                  <DropdownMenuRadioItem value="recent">Recent</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="deadline">Deadline</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="progress">Progress</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="confidence">Confidence</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="title">Title</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
                <DropdownMenuSeparator />
                <DropdownMenuRadioGroup value={sortDirection} onValueChange={(v) => setSortDirection(v as any)}>
                  <DropdownMenuRadioItem value="asc">Ascending</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="desc">Descending</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </header>

      <main className="flex-1">{children}</main>
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
    <div className="grid grid-cols-2 gap-2 px-2" onClick={(e) => e.stopPropagation()}>
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

function WorkspaceBreadcrumbTitle() {
  const params = useParams({ strict: false }) as { goalId?: string };
  const goalId = params.goalId;
  const goal = useSpira((s) => s.goals.find((g) => g.id === goalId));
  if (!goal) return <span className="text-white/50 truncate max-w-[180px] sm:max-w-xs">&hellip;</span>;
  return (
    <span className="text-white/90 font-medium truncate max-w-[180px] sm:max-w-xs">
      {goal.title || "Untitled goal"}
    </span>
  );
}
