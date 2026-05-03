import { Link } from "@tanstack/react-router";
import { ChevronRight, X, Calendar, AlertTriangle } from "lucide-react";
import { useState } from "react";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill, getConfidenceColor } from "./Confidence";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { ConfirmDialog } from "./ConfirmDialog";

/** Overdue red — same as "Yes, delete" button in ConfirmDialog */
const OVERDUE_RED = "#d13239";

function formatDeadlineInfo(iso: string | undefined) {
  if (!iso) return null;

  const deadline = new Date(iso);
  const now = new Date();
  const deadlineDay = new Date(deadline.getFullYear(), deadline.getMonth(), deadline.getDate());
  const todayDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffDays = Math.round((deadlineDay.getTime() - todayDay.getTime()) / 86_400_000);
  const isOverdue = diffDays < 0;

  const dateStr = deadline.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const countdown =
    diffDays === 0
      ? "due today"
      : diffDays === 1
        ? "1 day left"
        : diffDays > 1
          ? `${diffDays} days left`
          : diffDays === -1
            ? "1 day overdue"
            : `${Math.abs(diffDays)} days overdue`;

  return { dateStr, countdown, isOverdue };
}

export function GoalCard({ goal }: { goal: Goal }) {
  const progress = goalProgress(goal);
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const updateGoal = useSpira((s) => s.updateGoal);
  const [confirm, setConfirm] = useState(false);

  const accentColor = getConfidenceColor(goal.confidence);
  const deadlineInfo = formatDeadlineInfo(goal.deadline);
  const isOverdue = deadlineInfo?.isOverdue ?? false;

  const stripeColor = isOverdue ? OVERDUE_RED : accentColor;

  return (
    <div className="bg-card text-card-foreground border border-border/60 rounded-xl p-6 hover:shadow-md transition-shadow relative flex flex-col h-full cursor-pointer group">
      {/* Confidence & Actions Header */}
      <div className="flex items-center justify-between gap-3 mb-4">
        <ConfidencePill value={goal.confidence} className="relative z-10" />
        <button
          onClick={() => setConfirm(true)}
          className="relative z-10 shrink-0 p-2 -m-1 rounded-md text-muted-foreground/40 hover:text-muted-foreground hover:bg-secondary/50 transition-colors flex items-center justify-center"
          aria-label="Delete goal"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Title */}
      <div className="flex-1 min-w-0 mb-4">
        <h3 className="font-bold text-xl text-foreground leading-tight line-clamp-2 mb-3">
          <Link to="/goals/$goalId" params={{ goalId: goal.id }} className="after:absolute after:inset-0">
            {goal.title}
          </Link>
        </h3>
      </div>

      {/* Progress */}
      <div className="mt-auto space-y-4 relative z-10">
        <div className="space-y-2.5">
          <div className="flex items-center justify-between text-xs font-bold text-muted-foreground/80">
            <span>Progress</span>
            <span className="tabular-nums">{Math.round(progress * 100)}%</span>
          </div>
          <ProgressBar value={progress} />
        </div>

        {/* Footer bar */}
        <div className="flex items-center justify-between gap-3 rounded-md pr-3 pl-4 h-10 border border-border/80 relative overflow-hidden bg-transparent">
          {/* Colored stripe on the left */}
          <div 
            className="absolute left-0 top-0 bottom-0 w-[3px]"
            style={{ backgroundColor: stripeColor }}
          />

          {/* Deadline clickable trigger */}
          <DeadlinePopover
            iso={goal.deadline}
            onChange={(next) => updateGoal(goal.id, { deadline: next })}
            renderTrigger={() =>
              deadlineInfo ? (
                <span className="text-[13px] font-medium flex items-center gap-2.5 min-w-0 transition-opacity hover:opacity-70 cursor-pointer">
                  <span
                    className="flex items-center gap-1.5"
                    style={{ color: isOverdue ? OVERDUE_RED : "var(--muted-foreground)" }}
                  >
                    {isOverdue ? (
                      <AlertTriangle className="h-3.5 w-3.5 translate-y-[1px]" />
                    ) : (
                      <Calendar className="h-3.5 w-3.5 opacity-70 translate-y-[1px]" />
                    )}
                    Due {deadlineInfo.dateStr}
                  </span>
                  <span className="w-px h-3.5 bg-border shrink-0" />
                  <span className="text-foreground font-semibold truncate">
                    {deadlineInfo.countdown}
                  </span>
                </span>
              ) : (
                <span className="text-[13px] font-medium text-muted-foreground transition-opacity hover:opacity-70 cursor-pointer">
                  Set deadline
                </span>
              )
            }
          />

          {/* Start link */}
          <Link
            to="/goals/$goalId"
            params={{ goalId: goal.id }}
            className="shrink-0 flex items-center gap-0.5 text-[13px] leading-[14px] font-semibold text-muted-foreground hover:text-foreground transition-colors group-start"
          >
            <span className="underline decoration-1 underline-offset-[3px]">Start</span>
            <ChevronRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      </div>

      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="Delete this goal?"
        description={`Are you sure you want to permanently delete "${goal.title}"? All targets, options, and resources inside it will be removed. You can't undo this.`}
        confirmLabel="Yes, delete"
        cancelLabel="No, go back"
        onConfirm={() => deleteGoal(goal.id)}
      />
    </div>
  );
}
