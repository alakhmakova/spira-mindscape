import { createFileRoute, Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { Calendar, LayoutGrid, List, Plus } from "lucide-react";
import { GoalCard } from "@/components/spira/GoalCard";
import { GoalsTable } from "@/components/spira/GoalsTable";
import { NewGoalSheet } from "@/components/spira/NewGoalSheet";
import { useShellFilters } from "@/components/shell/shell-store";
import { goalProgress } from "@/lib/spira/progress";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "All goals - Spira" },
      {
        name: "description",
        content:
          "Your goals workspace. See progress, deadlines, and confidence at a glance, then dive into structured execution.",
      },
    ],
  }),
  component: GoalsOverview,
});

function GoalsOverview() {
  const goals = useSpira((s) => s.goals);
  const isLoading = useSpira((s) => s.isLoading);
  const hasLoaded = useSpira((s) => s.hasLoaded);
  const {
    query,
    sort,
    sortDirection,
    deadlineFrom,
    deadlineTo,
    confidence,
    status,
    viewMode,
    setViewMode,
  } = useShellFilters();
  const [open, setOpen] = useState(false);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    let arr = goals.filter((goal) => goal.title.toLowerCase().includes(q));

    if (deadlineFrom || deadlineTo) {
      arr = arr.filter((goal) => {
        if (!goal.deadline) return false;
        return (
          (!deadlineFrom || goal.deadline >= deadlineFrom) &&
          (!deadlineTo || goal.deadline <= deadlineTo)
        );
      });
    }

    if (confidence) {
      arr = arr.filter((goal) => goal.confidence === Number(confidence));
    }

    if (status !== "all") {
      arr = arr.filter((goal) =>
        status === "achieved"
          ? goalProgress(goal) >= 1
          : goalProgress(goal) < 1,
      );
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
  }, [
    goals,
    query,
    sort,
    sortDirection,
    deadlineFrom,
    deadlineTo,
    confidence,
    status,
  ]);

  const filteredGoalIds = useMemo(
    () => new Set(filtered.map((goal) => goal.id)),
    [filtered],
  );

  return (
    <div className="relative min-h-screen bg-[#f4f5f5]/80">
      <div className="spira-overview-inner mx-auto max-w-6xl space-y-8 px-4 py-8 sm:px-6 sm:py-12">
        <header className="spira-overview-header flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            {viewMode === "cards" ? (
              <>
                <h1 className="font-heading text-3xl leading-tight text-foreground sm:text-4xl">
                  All goals
                </h1>
                <p className="mt-1.5 text-sm text-muted-foreground sm:text-[15px]">
                  {goals.length} {goals.length === 1 ? "goal" : "goals"} in
                  motion. Pick one to dive into, or shape a new one.
                </p>
              </>
            ) : (
              <>
                <h2 className="font-heading text-3xl leading-tight text-foreground sm:text-4xl">
                  Timeline
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Goals, targets and tasks with deadlines, ordered by the
                  nearest date.
                </p>
              </>
            )}
          </div>
          <div className="flex w-full shrink-0 items-center gap-2 sm:w-auto">
            <div className="inline-flex w-full rounded-md border bg-secondary p-0.5 hairline sm:w-auto">
              <ViewBtn
                active={viewMode === "cards"}
                onClick={() => setViewMode("cards")}
                icon={LayoutGrid}
              >
                Cards
              </ViewBtn>
              <ViewBtn
                active={viewMode === "table"}
                onClick={() => setViewMode("table")}
                icon={List}
              >
                Timeline
              </ViewBtn>
              <Link
                to="/calendar"
                className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md px-3 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground sm:flex-none"
              >
                <Calendar className="h-3.5 w-3.5" />
                Calendar
              </Link>
            </div>
          </div>
        </header>

        {isLoading && !hasLoaded ? (
          <div className="surface-card p-12 text-center" role="status">
            <div className="font-display text-3xl">Loading goals</div>
            <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
              Connecting to the Spira backend.
            </p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="surface-card p-12 text-center">
            <div className="font-display text-3xl">No goals match</div>
            <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
              {goals.length === 0
                ? "Start with one. You can always shape it as you think."
                : "Try clearing your search or filters above."}
            </p>
            {goals.length === 0 && (
              <button
                type="button"
                onClick={() => setOpen(true)}
                className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              >
                <Plus className="h-4 w-4" />
                Create your first goal
              </button>
            )}
          </div>
        ) : viewMode === "cards" ? (
          <div className="spira-goals-grid grid gap-4 sm:gap-5">
            {filtered.map((goal) => (
              <GoalCard key={goal.id} goal={goal} />
            ))}
          </div>
        ) : (
          <GoalsTable goals={goals} filteredGoalIds={filteredGoalIds} />
        )}

        <button
          type="button"
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
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md px-3 text-xs font-medium transition-colors sm:flex-none",
        active
          ? "border bg-surface text-foreground shadow-sm hairline"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {children}
    </button>
  );
}
