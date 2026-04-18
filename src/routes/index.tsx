import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { LayoutGrid, List, Plus, Search, Sparkles } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { GoalCard } from "@/components/spira/GoalCard";
import { GoalsTable } from "@/components/spira/GoalsTable";
import { NewGoalSheet } from "@/components/spira/NewGoalSheet";
import { useAi } from "@/components/ai/ai-store";
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
  const openAi = useAi((s) => s.open);
  const [view, setView] = useState<"cards" | "table">("cards");
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(false);

  const filtered = useMemo(
    () =>
      goals.filter((g) => g.title.toLowerCase().includes(q.toLowerCase().trim())),
    [goals, q],
  );

  return (
    <div className="mx-auto max-w-6xl px-4 sm:px-6 py-6 sm:py-10 space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-display text-3xl sm:text-5xl tracking-tight">
            What are you working toward?
          </h1>
          <p className="text-muted-foreground mt-2 text-sm sm:text-base">
            {goals.length} {goals.length === 1 ? "goal" : "goals"} in motion. Pick one to dive
            into, or shape a new one.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => openAi()}
            className="hidden sm:inline-flex items-center gap-2 px-3 h-10 rounded-md border border-primary/30 bg-primary/10 text-primary text-sm hover:bg-primary/15"
          >
            <Sparkles className="h-4 w-4" />
            Plan with AI
          </button>
          <button
            onClick={() => setOpen(true)}
            className="inline-flex items-center gap-2 px-4 h-10 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90"
          >
            <Plus className="h-4 w-4" />
            New goal
          </button>
        </div>
      </header>

      <div className="flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
        <div className="relative flex-1 sm:max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search goals…"
            className="w-full h-10 pl-9 pr-3 rounded-md bg-surface border hairline text-sm outline-none focus:border-border-strong"
          />
        </div>
        <div className="inline-flex p-0.5 surface-sunken rounded-md self-start sm:self-auto">
          <ViewBtn active={view === "cards"} onClick={() => setView("cards")} icon={LayoutGrid}>
            Cards
          </ViewBtn>
          <ViewBtn active={view === "table"} onClick={() => setView("table")} icon={List}>
            Table
          </ViewBtn>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="surface-card p-10 text-center">
          <div className="font-display text-2xl">No goals yet</div>
          <p className="text-muted-foreground text-sm mt-1">
            Start with one. You can always shape it as you think.
          </p>
          <button
            onClick={() => setOpen(true)}
            className="mt-4 inline-flex items-center gap-2 px-4 h-10 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90"
          >
            <Plus className="h-4 w-4" />
            Create your first goal
          </button>
        </div>
      ) : view === "cards" ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
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
        "px-3 h-9 rounded-[6px] text-xs flex items-center gap-1.5 transition-colors",
        active ? "bg-surface text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {children}
    </button>
  );
}
