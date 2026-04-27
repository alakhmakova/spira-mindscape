import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Check, CheckCircle2, Flag, ListChecks, Target as TargetIcon } from "lucide-react";
import { differenceInCalendarDays, format, isPast } from "date-fns";
import type { Goal, Target } from "@/lib/spira/types";
import { goalProgress, targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

type TimelineKind = "goal" | "target" | "task";

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
  const { updateGoal, updateTarget } = useSpira();
  const [visibleKinds, setVisibleKinds] = useState<Record<TimelineKind, boolean>>({ goal: true, target: true, task: true });
  const items = buildTimelineItems(goals, visibleKinds);

  const toggleKind = (kind: TimelineKind) => {
    setVisibleKinds((current) => ({ ...current, [kind]: !current[kind] }));
  };

  const updateItemDeadline = (item: TimelineItem, next?: string) => {
    if (item.kind === "goal") updateGoal(item.goal.id, { deadline: next });
    if (item.kind === "target") updateTarget(item.goal.id, item.target.id, { deadline: next });
    if (item.kind === "task") {
      updateTarget(item.goal.id, item.target.id, {
        items: item.target.items.map((task) => (task.id === item.id.replace("task-", "") ? { ...task, deadline: next } : task)),
      });
    }
  };

  return (
    <div className="surface-card px-4 py-5 sm:px-7 sm:py-7">
      <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-foreground">Timeline</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Goals, targets and tasks with deadlines, ordered by the nearest date.
          </p>
        </div>
        <div className="flex w-fit flex-wrap items-center gap-3 rounded-md border hairline bg-secondary px-3 py-2">
          <KindCheckbox checked={visibleKinds.goal} onChange={() => toggleKind("goal")} label="Goals" />
          <KindCheckbox checked={visibleKinds.target} onChange={() => toggleKind("target")} label="Targets" />
          <KindCheckbox checked={visibleKinds.task} onChange={() => toggleKind("task")} label="Tasks" />
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
              onDeadlineChange={(next?: string) => updateItemDeadline(item, next)}
              onOpen={() =>
                nav({
                  to: "/goals/$goalId",
                  params: { goalId: item.goal.id },
                  hash: item.kind === "goal" ? "goal-top" : item.kind === "target" ? `target-${item.target.id}` : `task-${item.id.replace("task-", "")}`,
                })
              }
            />
          ))}
        </ol>
      )}
    </div>
  );
}

function buildTimelineItems(goals: Goal[], visibleKinds: Record<TimelineKind, boolean>): TimelineItem[] {
  const items: TimelineItem[] = [];

  for (const goal of goals) {
    if (visibleKinds.goal && goal.deadline) {
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
      if (visibleKinds.target && target.deadline) {
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

      if (target.type === "checklist" && visibleKinds.task) {
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
  onDeadlineChange,
  onOpen,
}: {
  item: TimelineItem;
  isLast: boolean;
  onDeadlineChange: (next?: string) => void;
  onOpen: () => void;
}) {
  const meta = getItemMeta(item);
  const days = differenceInCalendarDays(new Date(item.deadline), new Date());
  const overdue = isPast(new Date(item.deadline)) && days < 0;
  const achieved = isAchieved(item);

  return (
    <li className="relative grid grid-cols-[28px_1fr] gap-3 sm:grid-cols-[34px_1fr] sm:gap-4">
      {!isLast && (
        <span
          className={cn(
            "absolute left-[13px] top-8 h-full border-l sm:left-4",
            achieved ? "border-success" : item.kind === "goal" ? "border-primary" : "border-dashed border-border-strong",
          )}
        />
      )}
      <div className="relative z-10 pt-1">
        <span className={cn("grid h-7 w-7 place-items-center rounded-full border-2 bg-surface sm:h-8 sm:w-8", achieved ? "border-success bg-success text-primary-foreground" : meta.dot)}>
          {achieved ? <Check className="h-3.5 w-3.5" /> : <meta.icon className="h-3.5 w-3.5" />}
        </span>
      </div>
      <div className={cn("mb-7 w-full py-1 text-left", achieved && "text-success")}>
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
            <div onClick={(e) => e.stopPropagation()} className="mt-1 inline-flex">
              <DeadlinePopover
                iso={item.deadline}
                onChange={onDeadlineChange}
                variant="text"
                hideDaysLeft
                className="text-sm text-muted-foreground"
              />
            </div>
          </div>
          {item.kind === "target" && (
            <span className="shrink-0 text-sm font-semibold text-brand-orange tabular-nums">{Math.round(item.progress * 100)}%</span>
          )}
        </div>
        {item.kind === "goal" ? (
          <div className="mt-4 flex items-center gap-3">
            <ProgressBar value={item.progress} className="h-1.5 flex-1" tone="primary" />
            <span className="num w-10 text-right text-xs font-semibold tabular-nums">{Math.round(item.progress * 100)}%</span>
          </div>
        ) : item.kind === "target" ? (
          <div className="mt-3 text-sm font-semibold text-brand-orange tabular-nums">{Math.round(item.progress * 100)}% complete</div>
        ) : (
          null
        )}
        <button
          onClick={onOpen}
          className={cn(
            "mt-3 inline-flex h-9 items-center rounded-md border px-4 text-sm font-semibold transition-colors",
            achieved
              ? "border-success bg-success text-primary-foreground hover:bg-success/90"
              : "hairline-strong text-foreground hover:border-primary hover:text-primary",
          )}
        >
          {achieved ? "Achieved" : "Let&apos;s do it"}
        </button>
      </div>
    </li>
  );
}

function KindCheckbox({ checked, onChange, label }: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-xs font-semibold text-foreground">
      <input type="checkbox" checked={checked} onChange={onChange} className="h-4 w-4 accent-primary" />
      {label}
    </label>
  );
}

function isAchieved(item: TimelineItem): boolean {
  if (item.kind === "task") return item.done;
  return item.progress >= 1;
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
