import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { LayoutGrid, List, Plus } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { goalProgress } from "@/lib/spira/progress";
import { GoalCard } from "@/components/spira/GoalCard";
import { GoalsTable } from "@/components/spira/GoalsTable";
import { NewGoalSheet } from "@/components/spira/NewGoalSheet";
import { useShellFilters } from "@/components/shell/shell-store";
import { differenceInCalendarDays, isPast } from "date-fns";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Your goals — Spira" },
      {
        name: "description",
        content:
          "Your goals workspace. See progress, deadlines, and confidence at a glance — then dive into structured execution.",
      },
    ],
  }),
  component: GoalsOverview,
});

function GoalsOverview() {
  const goals = useSpira((s) => s.goals);
  const { query, sort, filterDeadline, filterConfidence } = useShellFilters();
  const [view, setView] = useState<"cards" | "table">("cards");
  const [open, setOpen] = useState(false);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    let arr = goals.filter((g) => g.title.toLowerCase().includes(q));
    if (filterDeadline !== "all") {
      arr = arr.filter((g) => {
        if (!g.deadline) return false;
        const d = new Date(g.deadline);
        const days = differenceInCalendarDays(d, new Date());
        if (filterDeadline === "overdue") return isPast(d) && days < 0;
        if (filterDeadline === "week") return days >= 0 && days <= 7;
        if (filterDeadline === "month") return days >= 0 && days <= 30;
        return true;
      });
    }
    if (filterConfidence !== "all") {
      arr = arr.filter((g) => {
        if (filterConfidence === "low") return g.confidence <= 3;
        if (filterConfidence === "med") return g.confidence >= 4 && g.confidence <= 6;
        return g.confidence >= 7;
      });
    }
    const sorted = [...arr].sort((a, b) => {
      switch (sort) {
        case "deadline":
          return (a.deadline ?? "9999").localeCompare(b.deadline ?? "9999");
        case "progress":
          return goalProgress(b) - goalProgress(a);
        case "confidence":
          return b.confidence - a.confidence;
        case "title":
          return a.title.localeCompare(b.title);
        default:
          return b.createdAt.localeCompare(a.createdAt);
      }
    });
    return sorted;
  }, [goals, query, sort, filterDeadline, filterConfidence]);

  return (
    <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8 sm:py-12 space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-display text-4xl sm:text-5xl tracking-tight text-balance">
            What are you working toward?
          </h1>
          <p className="text-muted-foreground mt-2 text-sm sm:text-[15px]">
            {goals.length} {goals.length === 1 ? "goal" : "goals"} in motion. Pick one to dive
            into, or shape a new one.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="inline-flex p-0.5 bg-secondary rounded-md border hairline">
            <ViewBtn active={view === "cards"} onClick={() => setView("cards")} icon={LayoutGrid}>
              Cards
            </ViewBtn>
            <ViewBtn active={view === "table"} onClick={() => setView("table")} icon={List}>
              Table
            </ViewBtn>
          </div>
          <button
            onClick={() => setOpen(true)}
            className="inline-flex items-center gap-2 px-4 h-10 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 shadow-sm"
          >
            <Plus className="h-4 w-4" />
            New goal
          </button>
        </div>
      </header>

      {filtered.length === 0 ? (
        <div className="surface-card p-12 text-center">
          <div className="font-display text-3xl">No goals match</div>
          <p className="text-muted-foreground text-sm mt-2 max-w-md mx-auto">
            {goals.length === 0
              ? "Start with one. You can always shape it as you think."
              : "Try clearing your search or filters above."}
          </p>
          {goals.length === 0 && (
            <button
              onClick={() => setOpen(true)}
              className="mt-5 inline-flex items-center gap-2 px-4 h-10 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90"
            >
              <Plus className="h-4 w-4" />
              Create your first goal
            </button>
          )}
        </div>
      ) : view === "cards" ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
          {filtered.map((g) => (
            <GoalCard key={g.id} goal={g} />
          ))}
        </div>
      ) : (
        <GoalsTable goals={filtered} />
      )}

      <NewGoalSheet open={open} onOpenChange={setOpen} />
    </div>
  );
}

function ViewBtn({
  active,
  onClick,
  icon: Icon,
  children,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ElementType;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "px-3 h-9 rounded-md text-xs flex items-center gap-1.5 transition-colors font-medium",
        active
          ? "bg-surface text-foreground shadow-sm border hairline"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {children}
    </button>
  );
}
