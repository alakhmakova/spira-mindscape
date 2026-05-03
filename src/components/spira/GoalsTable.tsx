import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Check, Flag, ListChecks, Target as TargetIcon } from "lucide-react";
import { differenceInCalendarDays, isPast, format } from "date-fns";
import type { Goal, Target } from "@/lib/spira/types";
import { goalProgress, targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

type TimelineKind = "goal" | "target" | "task";

type TimelineItem =
  | { id: string; kind: "goal"; goal: Goal; title: string; deadline: string; progress: number; achievedAt?: string }
  | { id: string; kind: "target"; goal: Goal; target: Target; title: string; deadline: string; progress: number; achievedAt?: string }
  | {
      id: string;
      kind: "task";
      goal: Goal;
      target: Extract<Target, { type: "checklist" }>;
      title: string;
      deadline: string;
      done: boolean;
      achievedAt?: string;
    };

function GoalIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M10 14.66v1.626a2 2 0 0 1-.976 1.696A5 5 0 0 0 7 21.978"/><path d="M14 14.66v1.626a2 2 0 0 0 .976 1.696A5 5 0 0 1 17 21.978"/><path d="M18 9h1.5a1 1 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z"/><path d="M6 9H4.5a1 1 0 0 1 0-5H6"/>
    </svg>
  );
}

function TargetIconSvg(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M17 3h4v4"/><path d="M18.575 11.082a13 13 0 0 1 1.048 9.027 1.17 1.17 0 0 1-1.914.597L14 17"/><path d="M7 10 3.29 6.29a1.17 1.17 0 0 1 .6-1.91 13 13 0 0 1 9.03 1.05"/><path d="M7 14a1.7 1.7 0 0 0-1.207.5l-2.646 2.646A.5.5 0 0 0 3.5 18H5a1 1 0 0 1 1 1v1.5a.5.5 0 0 0 .854.354L9.5 18.207A1.7 1.7 0 0 0 10 17v-2a1 1 0 0 0-1-1z"/><path d="M9.707 14.293 21 3"/>
    </svg>
  );
}

function TaskIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M21.801 10A10 10 0 1 1 17 3.335"/><path d="m9 11 3 3L22 4"/>
    </svg>
  );
}

function PartyPopperIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      <path d="M5.8 11.3 2 22l10.7-3.79" />
      <path d="M4 3h.01" />
      <path d="M22 8h.01" />
      <path d="M15 2h.01" />
      <path d="M22 20h.01" />
      <path d="m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10" />
      <path d="m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17" />
      <path d="m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7" />
      <path d="M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z" />
    </svg>
  );
}

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
    <div className="relative space-y-0">
      <div className="absolute right-0 top-0 z-20">
        <div className="flex w-fit flex-wrap items-center gap-4 rounded-md border hairline bg-secondary/20 px-3 py-1.5 backdrop-blur-sm">
          <KindCheckbox checked={visibleKinds.goal} onChange={() => toggleKind("goal")} label="Goals" />
          <KindCheckbox checked={visibleKinds.target} onChange={() => toggleKind("target")} label="Targets" />
          <KindCheckbox checked={visibleKinds.task} onChange={() => toggleKind("task")} label="Tasks" />
        </div>
      </div>

      {items.length === 0 ? (
        <div className="rounded-xl border hairline bg-secondary/10 px-5 py-12 text-center text-sm text-muted-foreground">
          No deadlines match this filter.
        </div>
      ) : (
        <div className="relative pt-1 space-y-0">
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
        </div>
      )}
    </div>
  );
}

function buildTimelineItems(goals: Goal[], visibleKinds: Record<TimelineKind, boolean>): TimelineItem[] {
  const items: TimelineItem[] = [];

  for (const goal of goals) {
    let goalAchievedAt = goal.achievedAt;
    const gProgress = goalProgress(goal);
    
    // Auto-compute goal achieved date from targets if missing
    if (gProgress >= 1 && !goalAchievedAt) {
      const dates = goal.targets.map(t => t.achievedAt).filter(Boolean) as string[];
      if (dates.length > 0) {
        dates.sort((a, b) => new Date(b).getTime() - new Date(a).getTime());
        goalAchievedAt = dates[0];
      }
    }

    if (visibleKinds.goal && goal.deadline) {
      items.push({
        id: `goal-${goal.id}`,
        kind: "goal",
        goal,
        title: goal.title,
        deadline: goal.deadline,
        progress: gProgress,
        achievedAt: goalAchievedAt,
      });
    }

    for (const target of goal.targets) {
      let targetAchievedAt = target.achievedAt;
      const tProgress = targetProgress(target);

      // Auto-compute checklist target achieved date from tasks if missing
      if (tProgress >= 1 && !targetAchievedAt && target.type === "checklist") {
        const dates = target.items.map(i => i.achievedAt).filter(Boolean) as string[];
        if (dates.length > 0) {
          dates.sort((a, b) => new Date(b).getTime() - new Date(a).getTime());
          targetAchievedAt = dates[0];
        }
      }

      if (visibleKinds.target && target.deadline) {
        items.push({
          id: `target-${target.id}`,
          kind: "target",
          goal,
          target,
          title: target.title,
          deadline: target.deadline,
          progress: tProgress,
          achievedAt: targetAchievedAt,
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
            achievedAt: task.achievedAt,
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
  const achieved = isAchieved(item);
  const deadline = new Date(item.deadline);
  const days = differenceInCalendarDays(deadline, new Date());
  const overdue = isPast(deadline) && !achieved && days < 0;

  const achievedButtonColor = item.kind === "goal" ? "bg-primary border-primary" : "bg-[#ea580c] border-[#ea580c]";

  return (
    <div className="group relative flex gap-5">
      <div className="relative flex w-5 shrink-0 flex-col items-center">
        {!isLast && (
          <div
            className={cn(
              "absolute bottom-0 left-[9px] top-5 w-[2px]",
              achieved ? "bg-primary" : "bg-border border-l-2 border-dashed border-border-strong bg-transparent",
            )}
          />
        )}
        <div className="relative z-10 pt-1">
          <span
            className={cn(
              "grid h-5 w-5 place-items-center rounded-full border-2 transition-colors",
              achieved
                ? "bg-primary border-primary text-primary-foreground"
                : "border-primary bg-surface text-muted-foreground",
            )}
          >
            {achieved && <Check className="h-3 w-3" strokeWidth={5} />}
          </span>
        </div>
      </div>

      <div className="flex-1 pb-8">
        <div className="flex flex-wrap items-center gap-2.5">
          <button onClick={onOpen} className="text-left transition-opacity hover:opacity-80 focus-visible:opacity-80 focus-visible:outline-none">
            <h3 className="text-base font-bold leading-tight text-foreground">{item.title}</h3>
          </button>
          <meta.icon className="h-3.5 w-3.5 text-muted-foreground/70" />
        </div>

        <div className="mt-0.5 flex flex-wrap items-center gap-2 text-[13px]">
          {achieved ? (
            <span className="font-medium text-muted-foreground/60 italic">
              Completed {item.achievedAt ? format(new Date(item.achievedAt), "MMM d") : ""}
            </span>
          ) : (
            <>
              <DeadlinePopover
                iso={item.deadline}
                onChange={onDeadlineChange}
                variant="text"
                hideDaysLeft
                hideChevron
                className="font-medium text-muted-foreground/80 transition-colors hover:text-primary"
              />
              <span className="text-muted-foreground/20">·</span>
              <span
                className={cn(
                  "font-semibold",
                  overdue ? "text-destructive" : "text-muted-foreground/70",
                )}
              >
                {overdue
                  ? `${Math.abs(days)}d overdue`
                  : days === 0
                    ? "today"
                    : `${days}d left`}
              </span>
            </>
          )}
        </div>

        {item.kind === "goal" && (
          <div className="mt-3 flex max-w-xs items-center gap-3">
            <ProgressBar value={item.progress} className="h-1 flex-1" tone="primary" />
            <span className="num text-[11px] font-bold tabular-nums text-muted-foreground/60">
              {Math.round(item.progress * 100)}%
            </span>
          </div>
        )}

        {item.kind !== "task" && (
          <div className="mt-4">
            <button
              onClick={onOpen}
              className={cn(
                "inline-flex h-8 items-center gap-2 rounded-md border px-4 text-[13px] font-bold transition-all",
                achieved
                  ? `${achievedButtonColor} text-primary-foreground hover:opacity-90`
                  : "border-border-strong bg-transparent text-foreground hover:bg-primary-soft hover:border-primary/30",
              )}
            >
              {achieved ? (
                <>
                  {item.kind === "goal" && <PartyPopperIcon className="h-3.5 w-3.5" />}
                  {item.kind === "goal" ? "Achieved" : "Complete"}
                </>
              ) : (
                "Let's do it"
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function KindCheckbox({ checked, onChange, label }: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2.5 text-xs font-bold text-foreground hover:opacity-80 transition-opacity">
      <input type="checkbox" checked={checked} onChange={onChange} className="h-4 w-4 rounded accent-primary border-border-strong" />
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
      icon: GoalIcon,
      label: "Goal",
      badge: "border-primary/60 bg-primary/5 text-foreground",
    };
  }
  if (item.kind === "target") {
    return {
      icon: TargetIconSvg,
      label: "Target",
      badge: "border-[#ea580c]/60 bg-[#ea580c]/5 text-foreground",
    };
  }
  return {
    icon: TaskIcon,
    label: "Task",
    badge: "border-[#3b82f6]/60 bg-[#3b82f6]/5 text-foreground",
  };
}
