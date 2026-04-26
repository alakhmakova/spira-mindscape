import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Check, CircleDot, Flag, ListChecks, Target as TargetIcon } from "lucide-react";
import { differenceInCalendarDays, format, isPast } from "date-fns";
import type { Goal, Target } from "@/lib/spira/types";
import { goalProgress, targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { cn } from "@/lib/utils";

type TimelineFilter = "goals" | "goals-tasks" | "all";

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
          <FilterBtn active={filter === "goals-tasks"} onClick={() => setFilter("goals-tasks")}>
            Goals + tasks
          </FilterBtn>
          <FilterBtn active={filter === "all"} onClick={() => setFilter("all")}>
            All
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

/**
 * Compact deadline trigger for the table — opens the calendar popover but
 * shows ONLY the formatted date (no countdown). Reuses DeadlinePopover for
 * actual editing UI.
 */
function DeadlinePopoverDateOnly({
  iso,
  onChange,
}: {
  iso: string;
  onChange: (next: string | undefined) => void;
}) {
  // Render the standard popover but hide its countdown via wrapper styling.
  // Easiest: render a custom trigger styled the same and wire the popover.
  return (
    <div className="[&_span.opacity-60]:hidden inline-block">
      <DeadlinePopover iso={iso} onChange={onChange} />
    </div>
  );
}

function ConfidenceCell({ value }: { value: number }) {
  const tone =
    value <= 3
      ? "bg-destructive"
      : value <= 6
        ? "bg-warning"
        : "bg-success";
  return (
    <div className="flex items-center gap-2 text-sm num tabular-nums font-medium">
      <span className={cn("h-2.5 w-2.5 rounded-full", tone)} />
      <span>
        {value}
        <span className="text-muted-foreground font-normal">/10</span>
      </span>
    </div>
  );
}
