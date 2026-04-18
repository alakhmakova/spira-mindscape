import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill } from "./Confidence";
import { ProgressBar } from "./ProgressBar";
import { DeadlineLabel } from "./DeadlineLabel";
import { useSpira } from "@/lib/spira/store";
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

export function GoalsTable({ goals }: { goals: Goal[] }) {
  const nav = useNavigate();
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const target = goals.find((g) => g.id === confirmId);

  return (
    <div className="space-y-2">
      <div className="hidden md:grid grid-cols-[1fr_220px_180px_120px_44px] gap-4 px-4 py-2 text-xs uppercase tracking-wider text-muted-foreground">
        <div>Title</div>
        <div>Progress</div>
        <div>Deadline</div>
        <div>Confidence</div>
        <div></div>
      </div>
      {goals.map((g) => {
        const p = goalProgress(g);
        return (
          <div
            key={g.id}
            onClick={() => nav({ to: "/goals/$goalId", params: { goalId: g.id } })}
            className="surface-card p-4 sm:p-5 cursor-pointer hover:border-border-strong transition-colors grid md:grid-cols-[1fr_220px_180px_120px_44px] gap-4 md:gap-4 items-center"
          >
            <div className="font-display text-lg leading-tight text-balance line-clamp-2">
              {g.title}
            </div>
            <div className="flex items-center gap-3">
              <ProgressBar value={p} className="flex-1" />
              <span className="num text-xs text-muted-foreground w-9 text-right">
                {Math.round(p * 100)}%
              </span>
            </div>
            <DeadlineLabel iso={g.deadline} />
            <ConfidencePill value={g.confidence} />
            <button
              onClick={(e) => {
                e.stopPropagation();
                setConfirmId(g.id);
              }}
              className="opacity-60 hover:opacity-100 p-2 -m-2 rounded-md hover:bg-accent justify-self-end"
              aria-label="Delete"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        );
      })}

      <AlertDialog open={!!confirmId} onOpenChange={(o) => !o && setConfirmId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this goal?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete "{target?.title}". This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (confirmId) deleteGoal(confirmId);
                setConfirmId(null);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
