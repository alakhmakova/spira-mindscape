import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Check, CheckCircle2, Flag, ListChecks, Target as TargetIcon } from "lucide-react";
import { differenceInCalendarDays, format, isPast } from "date-fns";
import type { Goal, Target } from "@/lib/spira/types";
import { goalProgress, targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { cn } from "@/lib/utils";

type TimelineFilter = "goals" | "goals-targets" | "all";

type TimelineItem =
  | { id: string; kind: "goal"; goal: Goal; title: string; deadline: string; progress: number }
  | { id: string; kind: "target"; goal: Goal; target: Target; title: string; deadline: string; progress: number }
  | {
      id: string;
      kind: "task";
      goal: Goal;
      target: Extract<Target, { type: "checklist" }>;
      title: string;
      deadline: string;
      done: boolean;
    };

export function GoalsTable({ goals }: { goals: Goal[] }) {
  const nav = useNavigate();
  const [filter, setFilter] = useState<TimelineFilter>("all");
  const items = buildTimelineItems(goals, filter);

  return (
    <div className="surface-card px-4 py-5 sm:px-7 sm:py-7">
      <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-foreground">Timeline</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Goals, targets and tasks with deadlines, ordered by the nearest date.
          </p>
        </div>
        <div className="inline-flex w-fit rounded-md border hairline bg-secondary p-0.5">
          <FilterBtn active={filter === "goals"} onClick={() => setFilter("goals")}>
            Goals
          </FilterBtn>
          <FilterBtn active={filter === "goals-targets"} onClick={() => setFilter("goals-targets")}>
            Goals and Targets
          </FilterBtn>
          <FilterBtn active={filter === "all"} onClick={() => setFilter("all")}>
            Goals, Targets, Tasks
          </FilterBtn>
        </div>
      </div>

      {items.length === 0 ? (
        <div className="rounded-lg border hairline bg-secondary/50 px-5 py-8 text-center text-sm text-muted-foreground">
          No deadlines match this filter.
        </div>
      ) : (
        <ol className="relative ml-3 space-y-0 sm:ml-4">
          {items.map((item, idx) => (
            <TimelineRow
              key={item.id}
              item={item}
              isLast={idx === items.length - 1}
              onOpen={() => nav({ to: "/goals/$goalId", params: { goalId: item.goal.id } })}
            />
          ))}
        </ol>
      )}
    </div>
  );
}

function buildTimelineItems(goals: Goal[], filter: TimelineFilter): TimelineItem[] {
  const items: TimelineItem[] = [];

  for (const goal of goals) {
    if (goal.deadline) {
      items.push({
        id: `goal-${goal.id}`,
        kind: "goal",
        goal,
        title: goal.title,
        deadline: goal.deadline,
        progress: goalProgress(goal),
      });
    }

    for (const target of goal.targets) {
      if (filter !== "goals" && target.deadline) {
        items.push({
          id: `target-${target.id}`,
          kind: "target",
          goal,
          target,
          title: target.title,
          deadline: target.deadline,
          progress: targetProgress(target),
        });
      }

      if (target.type === "checklist" && filter === "all") {
        for (const task of target.items) {
          if (!task.deadline) continue;
          items.push({
            id: `task-${task.id}`,
            kind: "task",
            goal,
            target,
            title: task.text,
            deadline: task.deadline,
            done: task.done,
          });
        }
      }
    }
  }

  return items.sort((a, b) => a.deadline.localeCompare(b.deadline));
}

function TimelineRow({
  item,
  isLast,
  onOpen,
}: {
  item: TimelineItem;
  isLast: boolean;
  onOpen: () => void;
}) {
  const meta = getItemMeta(item);
  const days = differenceInCalendarDays(new Date(item.deadline), new Date());
  const overdue = isPast(new Date(item.deadline)) && days < 0;

  return (
    <li className="relative grid grid-cols-[28px_1fr] gap-3 sm:grid-cols-[34px_1fr] sm:gap-4">
      {!isLast && (
        <span
          className={cn(
            "absolute left-[13px] top-8 h-full border-l sm:left-4",
            item.kind === "goal" ? "border-primary" : "border-dashed border-border-strong",
          )}
        />
      )}
      <div className="relative z-10 pt-1">
        <span className={cn("grid h-7 w-7 place-items-center rounded-full border-2 bg-surface sm:h-8 sm:w-8", meta.dot)}>
          <meta.icon className="h-3.5 w-3.5" />
        </span>
      </div>
      <button
        onClick={onOpen}
        className={cn(
          "mb-7 w-full rounded-lg border p-4 text-left transition-colors hover:bg-secondary/70 sm:p-5",
          meta.card,
        )}
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className={cn("rounded-full px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide", meta.badge)}>
                {meta.label}
              </span>
              <span className={cn("text-xs font-medium", overdue ? "text-destructive" : "text-muted-foreground")}>
                {overdue ? `${Math.abs(days)}d overdue` : days === 0 ? "Today" : `${days}d left`}
              </span>
            </div>
            <h3 className="text-[17px] font-semibold leading-snug text-foreground">{item.title}</h3>
            <p className="mt-1 text-sm text-muted-foreground">{item.kind === "goal" ? `${item.goal.targets.length} targets` : item.goal.title}</p>
          </div>
          <time className="shrink-0 text-sm font-semibold text-foreground">{format(new Date(item.deadline), "MMM d")}</time>
        </div>
        {item.kind !== "task" ? (
          <div className="mt-4 flex items-center gap-3">
            <ProgressBar value={item.progress} className="h-1.5 flex-1" tone={item.kind === "target" ? "muted" : "primary"} />
            <span className="num w-10 text-right text-xs font-semibold tabular-nums">{Math.round(item.progress * 100)}%</span>
          </div>
        ) : (
          <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
            <span className={cn("grid h-5 w-5 place-items-center rounded-full border", item.done && "border-success bg-success text-primary-foreground")}>
              {item.done && <Check className="h-3.5 w-3.5" />}
            </span>
            <span>{item.target.title}</span>
          </div>
        )}
      </button>
    </li>
  );
}

function FilterBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "h-9 whitespace-nowrap rounded px-3 text-xs font-semibold transition-colors",
        active ? "border hairline bg-surface text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

function getItemMeta(item: TimelineItem) {
  if (item.kind === "goal") {
    return {
      icon: Flag,
      label: "Goal",
      dot: "border-primary text-primary",
      badge: "bg-primary text-primary-foreground",
      card: "border-primary/30 bg-primary-soft/60",
    };
  }
  if (item.kind === "target") {
    return {
      icon: TargetIcon,
      label: "Target",
      dot: "border-warning text-warning",
      badge: "bg-warning/20 text-foreground",
      card: "border-warning/40 bg-surface",
    };
  }
  return {
    icon: item.done ? Check : ListChecks,
    label: "Task",
    dot: item.done ? "border-success text-success" : "border-border-strong text-muted-foreground",
    badge: "bg-secondary text-secondary-foreground",
    card: "border-border bg-surface",
  };
}
