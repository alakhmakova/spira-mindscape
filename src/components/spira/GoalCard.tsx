import { Link } from "@tanstack/react-router";
import { ArrowRight, MoreHorizontal, Trash2, Pencil, Target as TargetIcon } from "lucide-react";
import { useState } from "react";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill } from "./Confidence";
import { ProgressBar } from "./ProgressBar";
import { DeadlineLabel } from "./DeadlineLabel";
import { useSpira } from "@/lib/spira/store";
import { ConfirmDialog } from "./ConfirmDialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export function GoalCard({ goal }: { goal: Goal }) {
  const progress = goalProgress(goal);
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const [confirm, setConfirm] = useState(false);

  const selected = goal.options.find((o) => o.selected);

  return (
    <div className="group surface-card p-6 hover:shadow-[var(--shadow-raised)] transition-shadow relative">
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-wider text-muted-foreground mb-2">
            <TargetIcon className="h-3 w-3 text-primary" />
            Goal
          </div>
          <Link
            to="/goals/$goalId"
            params={{ goalId: goal.id }}
            className="block"
          >
            <h3 className="font-display text-2xl leading-snug text-balance line-clamp-2 hover:text-primary transition-colors">
              {goal.title}
            </h3>
          </Link>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger className="opacity-0 group-hover:opacity-100 transition-opacity p-1.5 -m-1 rounded-md hover:bg-accent text-muted-foreground">
            <MoreHorizontal className="h-4 w-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem asChild>
              <Link to="/goals/$goalId" params={{ goalId: goal.id }}>
                <Pencil className="h-4 w-4 mr-2" /> Edit
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem
              className="text-destructive focus:text-destructive"
              onClick={() => setConfirm(true)}
            >
              <Trash2 className="h-4 w-4 mr-2" /> Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
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

      <div className="mt-5 pt-4 border-t hairline flex items-center justify-between gap-3">
        <div className="flex items-center gap-3 text-xs">
          <DeadlineLabel iso={goal.deadline} />
        </div>
        <ConfidencePill value={goal.confidence} />
      </div>

      <div className="mt-5">
        <Link
          to="/goals/$goalId"
          params={{ goalId: goal.id }}
          className="inline-flex items-center gap-1.5 h-10 px-4 rounded-md border-2 border-primary text-primary text-sm font-semibold hover:bg-primary-soft transition-colors"
        >
          Open workspace <ArrowRight className="h-3.5 w-3.5" />
        </Link>
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
