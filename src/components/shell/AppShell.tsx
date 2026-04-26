import { Link, useRouterState } from "@tanstack/react-router";
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
      <header className="sticky top-0 z-30 bg-background/85 backdrop-blur border-b hairline">
        <div className="w-full px-4 sm:px-6 h-16 flex items-center gap-3 sm:gap-5">
          {/* Brand */}
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <span className="text-2xl tracking-tight text-[#ff4800]" style={{ fontFamily: "'Nunito', sans-serif", fontWeight: 800 }}>
              Spira
            </span>
          </Link>

          {/* AI quick action */}
          <button
            onClick={() => openAi()}
            className="hidden sm:inline-flex items-center h-8 px-3 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90"
          >
            Spira AI
          </button>

          {/* Spacer */}
          <div className="flex-1" />

          {/* Right side items */}
          <div className="flex items-center gap-3 sm:gap-4 justify-end">
            {/* Search */}
            <div className="relative w-32 sm:w-64">
              <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search goals…"
                className="w-full h-9 pl-9 pr-8 rounded-md bg-surface border hairline text-sm outline-none placeholder:text-muted-foreground/70 focus:border-primary/50 focus:ring-2 focus:ring-primary/15 transition-shadow"
              />
              {query && (
                <button
                  onClick={() => setQuery("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 h-5 w-5 grid place-items-center text-muted-foreground hover:text-foreground rounded-full hover:bg-secondary transition-colors"
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
                    <div className="grid grid-cols-2 gap-2 px-2">
                      <input type="date" value={deadlineFrom} onChange={(e) => setDeadlineFrom(e.target.value)} className="h-9 rounded-md border hairline bg-surface px-2 text-xs outline-none" aria-label="Deadline from" />
                      <input type="date" value={deadlineTo} onChange={(e) => setDeadlineTo(e.target.value)} className="h-9 rounded-md border hairline bg-surface px-2 text-xs outline-none" aria-label="Deadline to" />
                    </div>
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

            {/* Calendar Icon Button (Global) */}
            <Link
              to="/calendar"
              className={cn(
                "inline-flex items-center justify-center h-9 w-9 shrink-0 rounded-md border hairline-strong hover:bg-accent text-foreground/80",
                isCalendar && "bg-primary-soft border-primary/40 text-primary"
              )}
              title="Calendar"
            >
              <Calendar className="h-4 w-4" />
            </Link>

            {/* Mobile AI Icon Button (Global) */}
            <button
              onClick={() => openAi()}
              className="sm:hidden inline-flex items-center justify-center h-9 w-9 shrink-0 rounded-md bg-primary text-primary-foreground hover:bg-primary/90"
              title="Spira AI"
            >
              <Sparkles className="h-4 w-4" />
            </button>

            {/* User */}
            <div className="flex items-center gap-2 pl-2 md:border-l md:hairline md:pl-3">
              <div className="h-8 w-8 rounded-full bg-primary-soft border border-primary/30 grid place-items-center text-primary text-xs font-semibold">
                SU
              </div>
              <div className="hidden md:block leading-tight text-right">
                <div className="text-xs font-semibold text-foreground">Spira User</div>
              </div>
              <ChevronDown className="hidden md:inline h-3.5 w-3.5 text-muted-foreground" />
            </div>
          </div>
        </div>

        {/* Mobile secondary row: filters + sort */}
        {!isWorkspace && (
          <div className="sm:hidden border-t hairline px-4 h-11 flex items-center gap-2 overflow-x-auto">
            <DropdownMenu>
              <DropdownMenuTrigger className="inline-flex items-center gap-1 h-7 px-2.5 rounded-md border hairline-strong text-xs text-foreground/80">
                <SlidersHorizontal className="h-3 w-3" /> Filter
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-56">
                <DropdownMenuLabel>Deadline</DropdownMenuLabel>
                <DropdownMenuRadioGroup
                  value={filterDeadline}
                  onValueChange={(v) => setFilterDeadline(v as any)}
                >
                  <DropdownMenuRadioItem value="all">Any</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="overdue">Overdue</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="week">7 days</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="month">30 days</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
                <DropdownMenuSeparator />
                <DropdownMenuLabel>Confidence</DropdownMenuLabel>
                <DropdownMenuRadioGroup
                  value={filterConfidence}
                  onValueChange={(v) => setFilterConfidence(v as any)}
                >
                  <DropdownMenuRadioItem value="all">Any</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="low">Low</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="med">Medium</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="high">High</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
              </DropdownMenuContent>
            </DropdownMenu>
            <DropdownMenu>
              <DropdownMenuTrigger className="inline-flex items-center gap-1 h-7 px-2.5 rounded-md border hairline-strong text-xs text-foreground/80">
                <ArrowDownUp className="h-3 w-3" /> Sort
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
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </header>

      <main className="flex-1">{children}</main>
    </div>
  );
}
