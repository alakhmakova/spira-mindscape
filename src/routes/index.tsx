import { createFileRoute, Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { LayoutGrid, List, Plus, Calendar } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { goalProgress } from "@/lib/spira/progress";
import { GoalCard } from "@/components/spira/GoalCard";
import { GoalsTable } from "@/components/spira/GoalsTable";
import { NewGoalSheet } from "@/components/spira/NewGoalSheet";
import { useShellFilters } from "@/components/shell/shell-store";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "All goals — Spira" },
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
  const { query, sort, sortDirection, deadlineFrom, deadlineTo, confidence, status, viewMode, setViewMode } = useShellFilters();
  const [open, setOpen] = useState(false);

  const greeting = "All goals";

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    let arr = goals.filter((g) => g.title.toLowerCase().includes(q));
    if (deadlineFrom || deadlineTo) {
      arr = arr.filter((g) => {
        if (!g.deadline) return false;
        return (!deadlineFrom || g.deadline >= deadlineFrom) && (!deadlineTo || g.deadline <= deadlineTo);
      });
    }
    if (confidence) {
      arr = arr.filter((g) => g.confidence === Number(confidence));
    }
    if (status !== "all") {
      arr = arr.filter((g) => (status === "achieved" ? goalProgress(g) >= 1 : goalProgress(g) < 1));
    }
    const sorted = [...arr].sort((a, b) => {
      let result = 0;
      switch (sort) {
        case "deadline":
          result = (a.deadline ?? "9999").localeCompare(b.deadline ?? "9999");
          break;
        case "progress":
          result = goalProgress(a) - goalProgress(b);
          break;
        case "confidence":
          result = a.confidence - b.confidence;
          break;
        case "title":
          result = a.title.localeCompare(b.title);
          break;
        default:
          result = a.createdAt.localeCompare(b.createdAt);
      }
      return sortDirection === "asc" ? result : -result;
    });
    return sorted;
  }, [goals, query, sort, sortDirection, deadlineFrom, deadlineTo, confidence, status]);

  return (
    <div className="min-h-screen bg-[#f4f5f5]/80">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8 sm:py-12 space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          {viewMode === "cards" ? (
            <>
              <h1 className="font-heading text-3xl sm:text-4xl text-foreground leading-tight">{greeting}</h1>
              <p className="text-muted-foreground mt-1.5 text-sm sm:text-[15px]">
                {goals.length} {goals.length === 1 ? "goal" : "goals"} in motion. Pick one to dive
                into, or shape a new one.
              </p>
            </>
          ) : (
            <>
              <h2 className="font-heading text-3xl sm:text-4xl text-foreground leading-tight">Timeline</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Goals, targets and tasks with deadlines, ordered by the nearest date.
              </p>
            </>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <div className="inline-flex p-0.5 bg-secondary rounded-md border hairline">
            <ViewBtn active={viewMode === "cards"} onClick={() => setViewMode("cards")} icon={LayoutGrid}>
              Cards
            </ViewBtn>
            <ViewBtn active={viewMode === "table"} onClick={() => setViewMode("table")} icon={List}>
              Timeline
            </ViewBtn>
            <Link
              to="/calendar"
              className="px-3 h-9 rounded-md text-xs flex items-center gap-1.5 transition-colors font-medium text-muted-foreground hover:text-foreground"
            >
              <Calendar className="h-3.5 w-3.5" />
              Calendar
            </Link>
          </div>
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
      ) : viewMode === "cards" ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
          {filtered.map((g) => (
            <GoalCard key={g.id} goal={g} />
          ))}
        </div>
      ) : (
        <GoalsTable goals={filtered} />
      )}

      <button
        onClick={() => setOpen(true)}
        className="fixed bottom-5 right-5 z-40 grid h-14 w-14 place-items-center rounded-full bg-primary text-primary-foreground shadow-raised transition-transform hover:scale-105 hover:bg-primary/90 sm:bottom-7 sm:right-7"
        aria-label="New goal"
      >
        <Plus className="h-7 w-7" />
      </button>

      <NewGoalSheet open={open} onOpenChange={setOpen} />
      </div>
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
