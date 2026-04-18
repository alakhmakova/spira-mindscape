import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { MoreHorizontal, Pencil, Trash2 } from "lucide-react";
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

export function GoalsTable({ goals }: { goals: Goal[] }) {
  const nav = useNavigate();
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const target = goals.find((g) => g.id === confirmId);

  return (
    <div className="surface-card overflow-hidden">
      {/* Header row */}
      <div className="hidden md:grid grid-cols-[1.6fr_180px_180px_140px_44px] gap-6 px-6 py-3.5 border-b hairline text-[13px] font-semibold text-foreground/70">
        <div>Title</div>
        <div>Deadline</div>
        <div>Status</div>
        <div>Progress</div>
        <div className="text-right">Actions</div>
      </div>

      <ul>
        {goals.map((g, idx) => {
          const p = goalProgress(g);
          const isLast = idx === goals.length - 1;
          return (
            <li
              key={g.id}
              onClick={() => nav({ to: "/goals/$goalId", params: { goalId: g.id } })}
              className={`group cursor-pointer hover:bg-secondary/60 transition-colors ${
                !isLast ? "border-b hairline" : ""
              }`}
            >
              <div className="grid md:grid-cols-[1.6fr_180px_180px_140px_44px] gap-3 md:gap-6 px-6 py-5 items-center">
                <div className="min-w-0">
                  <a className="link-action font-medium text-[15px] block truncate">
                    {g.title}
                  </a>
                  <div className="text-xs text-muted-foreground mt-0.5 truncate">
                    {g.targets.length} {g.targets.length === 1 ? "target" : "targets"} ·{" "}
                    {g.options.length} {g.options.length === 1 ? "option" : "options"}
                  </div>
                </div>
                <div className="text-sm text-foreground/80">
                  <DeadlineLabel iso={g.deadline} />
                </div>
                <StatusDot goal={g} />
                <div className="flex items-center gap-2.5">
                  <ProgressBar value={p} className="flex-1" />
                  <span className="num text-xs font-semibold tabular-nums w-9 text-right">
                    {Math.round(p * 100)}%
                  </span>
                </div>
                <div className="md:flex md:justify-end">
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      onClick={(e) => e.stopPropagation()}
                      className="p-1.5 -m-1 rounded-md hover:bg-background text-muted-foreground hover:text-foreground"
                      aria-label="Row actions"
                    >
                      <MoreHorizontal className="h-4 w-4" />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
                      <DropdownMenuItem
                        onClick={() =>
                          nav({ to: "/goals/$goalId", params: { goalId: g.id } })
                        }
                      >
                        <Pencil className="h-4 w-4 mr-2" /> Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        className="text-destructive focus:text-destructive"
                        onClick={() => setConfirmId(g.id)}
                      >
                        <Trash2 className="h-4 w-4 mr-2" /> Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                {/* Mobile-only confidence row */}
                <div className="md:hidden flex justify-start">
                  <ConfidencePill value={g.confidence} />
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      <ConfirmDialog
        open={!!confirmId}
        onOpenChange={(o) => !o && setConfirmId(null)}
        title="Delete this goal?"
        description={`Are you sure you want to permanently delete "${target?.title}"? You can't undo this.`}
        confirmLabel="Yes, delete"
        cancelLabel="No, go back"
        onConfirm={() => {
          if (confirmId) deleteGoal(confirmId);
          setConfirmId(null);
        }}
      />
    </div>
  );
}

function StatusDot({ goal }: { goal: Goal }) {
  const p = goalProgress(goal);
  const tone =
    goal.confidence <= 3
      ? "destructive"
      : p >= 1
        ? "success"
        : "primary";
  const label =
    p >= 1
      ? "Complete"
      : goal.confidence <= 3
        ? "At risk"
        : "Current";
  return (
    <div className="flex items-center gap-2 text-sm">
      <span
        className={`h-2 w-2 rounded-full ${
          tone === "destructive"
            ? "bg-destructive"
            : tone === "success"
              ? "bg-success"
              : "bg-primary"
        }`}
      />
      {label}
    </div>
  );
}
