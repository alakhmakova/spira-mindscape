import { Link } from "@tanstack/react-router";
import { Trash2, Trophy } from "lucide-react";
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
    <div className="surface-card p-6 hover:shadow-[var(--shadow-raised)] transition-shadow relative">
      {/* Header: trophy icon + delete button */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="h-12 w-12 rounded-md bg-primary-soft text-primary grid place-items-center mb-4">
            <Trophy className="h-7 w-7" strokeWidth={1.75} />
          </div>
          <Link to="/goals/$goalId" params={{ goalId: goal.id }} className="block">
            <h3 className="font-display text-2xl leading-snug text-balance line-clamp-2 hover:text-primary transition-colors">
              {goal.title}
            </h3>
          </Link>
        </div>
        <button
          onClick={() => setConfirm(true)}
          className="shrink-0 p-2 -m-1 rounded-md text-muted-foreground hover:text-destructive hover:bg-secondary"
          aria-label="Delete goal"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {selected && (
        <p className="text-sm text-muted-foreground mt-3 line-clamp-2 leading-relaxed">
          Strategy: <span className="text-foreground/80">{selected.text}</span>
        </p>
      )}

      <div className="mt-5 space-y-3">
        <div className="flex items-center justify-between text-xs">
          <span className="text-muted-foreground">Progress</span>
          <span className="num font-semibold text-foreground tabular-nums">
            {Math.round(progress * 100)}%
          </span>
        </div>
        <ProgressBar value={progress} />
      </div>

      <div className="mt-5 flex items-center justify-between gap-3 flex-wrap">
        <DeadlinePopover
          iso={goal.deadline}
          onChange={(next) => updateGoal(goal.id, { deadline: next })}
        />
        <ConfidencePill value={goal.confidence} />
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
