import { Link } from "@tanstack/react-router";
import { X, Trophy } from "lucide-react";
import { useState } from "react";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill } from "./Confidence";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { ConfirmDialog } from "./ConfirmDialog";

export function GoalCard({ goal }: { goal: Goal }) {
  const progress = goalProgress(goal);
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const updateGoal = useSpira((s) => s.updateGoal);
  const [confirm, setConfirm] = useState(false);

  const selected = goal.options.find((o) => o.selected);

  return (
    <div className="bg-card text-card-foreground border border-border/60 rounded-xl p-6 hover:shadow-md transition-shadow relative flex flex-col h-full">
      {/* Icon & Actions Header */}
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="h-10 w-10 grid place-items-center text-amber-500">
          <Trophy className="h-8 w-8" strokeWidth={1.5} />
        </div>
        <button
          onClick={() => setConfirm(true)}
          className="relative z-10 shrink-0 p-2 -m-1 rounded-md text-muted-foreground/40 hover:text-muted-foreground hover:bg-secondary/50 transition-colors"
          aria-label="Delete goal"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Title & Description */}
      <div className="flex-1 min-w-0 mb-6">
        <h3 className="font-semibold text-[17px] text-foreground leading-snug line-clamp-2">
          <Link to="/goals/$goalId" params={{ goalId: goal.id }} className="after:absolute after:inset-0">
            {goal.title}
          </Link>
        </h3>
      </div>

      {/* Spira specific data (Progress, Deadline, Confidence) */}
      <div className="mt-auto space-y-6 relative z-10">
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs font-medium">
            <span className="text-muted-foreground">Progress</span>
            <span className="tabular-nums">{Math.round(progress * 100)}%</span>
          </div>
          <ProgressBar value={progress} />
        </div>

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <DeadlinePopover
            iso={goal.deadline}
            onChange={(next) => updateGoal(goal.id, { deadline: next })}
          />
          <ConfidencePill value={goal.confidence} />
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
