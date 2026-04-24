import { Link, useRouterState } from "@tanstack/react-router";
import {
  Search,
  SlidersHorizontal,
  ArrowDownUp,
  Sparkles,
  ChevronDown,
} from "lucide-react";
import { useAi } from "@/components/ai/ai-store";
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
    filterDeadline,
    setFilterDeadline,
    filterConfidence,
    setFilterConfidence,
  } = useShellFilters();

  const isCalendar = path.startsWith("/calendar");
  const isWorkspace = path.startsWith("/goals/");

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
            <div className="relative w-48 sm:w-64">
              <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search goals…"
                className="w-full h-9 pl-9 pr-3 rounded-md bg-surface border hairline text-sm outline-none placeholder:text-muted-foreground/70 focus:border-primary/50 focus:ring-2 focus:ring-primary/15 transition-shadow"
              />
            </div>

            {/* Filter */}
            <DropdownMenu>
              <DropdownMenuTrigger
                className={cn(
                  "hidden lg:inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80",
                  (filterDeadline !== "all" || filterConfidence !== "all") &&
                    "border-primary/40 text-primary bg-primary-soft",
                )}
              >
                <SlidersHorizontal className="h-3.5 w-3.5" />
                Filters
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>Deadline</DropdownMenuLabel>
                <DropdownMenuRadioGroup
                  value={filterDeadline}
                  onValueChange={(v) => setFilterDeadline(v as any)}
                >
                  <DropdownMenuRadioItem value="all">Any deadline</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="overdue">Overdue</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="week">Due within 7 days</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="month">Due within 30 days</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
                <DropdownMenuSeparator />
                <DropdownMenuLabel>Confidence</DropdownMenuLabel>
                <DropdownMenuRadioGroup
                  value={filterConfidence}
                  onValueChange={(v) => setFilterConfidence(v as any)}
                >
                  <DropdownMenuRadioItem value="all">Any confidence</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="low">Low (1–3)</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="med">Medium (4–6)</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="high">High (7–10)</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
              </DropdownMenuContent>
            </DropdownMenu>

            {/* Sort */}
            <DropdownMenu>
              <DropdownMenuTrigger className="hidden lg:inline-flex items-center gap-1.5 h-9 px-3 rounded-md border hairline-strong text-sm hover:bg-accent text-foreground/80">
                <ArrowDownUp className="h-3.5 w-3.5" />
                Sort
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
              </DropdownMenuContent>
            </DropdownMenu>

            {/* Calendar link as small pill */}
            <Link
              to="/calendar"
              className={cn(
                "hidden md:inline-flex h-9 px-3 items-center rounded-md text-sm border hairline-strong hover:bg-accent",
                isCalendar && "bg-primary-soft border-primary/40 text-primary",
              )}
            >
              Calendar
            </Link>

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

        {/* Mobile secondary row: filters + sort + AI */}
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
          <Link
            to="/calendar"
            className={cn(
              "inline-flex h-7 px-2.5 items-center rounded-md text-xs border hairline-strong",
              isCalendar && "bg-primary-soft border-primary/40 text-primary",
            )}
          >
            Calendar
          </Link>
          <button
            onClick={() => openAi()}
            className="ml-auto inline-flex items-center gap-1 h-7 px-2.5 rounded-md bg-primary text-primary-foreground text-xs font-medium"
          >
            <Sparkles className="h-3 w-3" /> AI
          </button>
        </div>
      </header>

      <main className="flex-1">{children}</main>
    </div>
  );
}
