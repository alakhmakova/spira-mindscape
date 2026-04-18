import { Link } from "@tanstack/react-router";
import { MoreHorizontal, Trash2, Pencil } from "lucide-react";
import { useState } from "react";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill } from "./Confidence";
import { ProgressBar } from "./ProgressBar";
import { DeadlineLabel } from "./DeadlineLabel";
import { useSpira } from "@/lib/spira/store";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

export function GoalCard({ goal }: { goal: Goal }) {
  const progress = goalProgress(goal);
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const [confirm, setConfirm] = useState(false);

  return (
    <div className="group surface-card p-4 sm:p-5 transition-colors hover:border-border-strong relative">
      <div className="flex items-start justify-between gap-3">
        <Link
          to="/goals/$goalId"
          params={{ goalId: goal.id }}
          className="flex-1 min-w-0 -m-1 p-1"
        >
          <h3 className="font-display text-lg sm:text-xl leading-snug text-balance line-clamp-3">
            {goal.title}
          </h3>
        </Link>
        <DropdownMenu>
          <DropdownMenuTrigger className="opacity-60 hover:opacity-100 p-1 rounded-md hover:bg-accent">
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

      <div className="mt-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <ProgressBar value={progress} />
          <span className="num text-xs text-muted-foreground tabular-nums">
            {Math.round(progress * 100)}%
          </span>
        </div>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <DeadlineLabel iso={goal.deadline} />
          <ConfidencePill value={goal.confidence} />
        </div>
      </div>

      <AlertDialog open={confirm} onOpenChange={setConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this goal?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete "{goal.title}" and all its targets, options, and
              resources. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => deleteGoal(goal.id)}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
