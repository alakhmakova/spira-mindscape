import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { format } from "date-fns";
import type { Goal } from "@/lib/spira/types";
import { goalProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { ConfirmDialog } from "./ConfirmDialog";
import { cn } from "@/lib/utils";

export function GoalsTable({ goals }: { goals: Goal[] }) {
  const nav = useNavigate();
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const updateGoal = useSpira((s) => s.updateGoal);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const target = goals.find((g) => g.id === confirmId);

  return (
    <div className="surface-card overflow-hidden">
      {/* Header row */}
      <div className="hidden md:grid grid-cols-[1.6fr_200px_160px_160px_44px] gap-6 px-6 py-3.5 border-b hairline text-[13px] font-semibold text-foreground/70">
        <div>Title</div>
        <div>Deadline</div>
        <div>Confidence</div>
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
              className={cn(
                "cursor-pointer hover:bg-secondary/60 transition-colors",
                !isLast && "border-b hairline",
              )}
            >
              <div className="grid md:grid-cols-[1.6fr_200px_160px_160px_44px] gap-3 md:gap-6 px-6 py-5 items-center">
                <div className="min-w-0">
                  <a className="link-action font-medium text-[15px] block truncate">
                    {g.title}
                  </a>
                  <div className="text-xs text-muted-foreground mt-0.5 truncate">
                    {g.targets.length} {g.targets.length === 1 ? "target" : "targets"} ·{" "}
                    {g.options.length} {g.options.length === 1 ? "option" : "options"}
                  </div>
                </div>
                <div onClick={(e) => e.stopPropagation()} className="text-sm text-foreground/80">
                  {g.deadline ? (
                    <DeadlinePopoverDateOnly
                      iso={g.deadline}
                      onChange={(next) => updateGoal(g.id, { deadline: next })}
                    />
                  ) : (
                    <DeadlinePopover
                      iso={undefined}
                      onChange={(next) => updateGoal(g.id, { deadline: next })}
                    />
                  )}
                </div>
                <ConfidenceCell value={g.confidence} />
                <div className="flex items-center gap-2.5">
                  <ProgressBar value={p} className="flex-1" />
                  <span className="num text-xs font-semibold tabular-nums w-9 text-right">
                    {Math.round(p * 100)}%
                  </span>
                </div>
                <div className="md:flex md:justify-end">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmId(g.id);
                    }}
                    className="p-2 -m-1 rounded-md text-muted-foreground hover:text-destructive hover:bg-background"
                    aria-label="Delete goal"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
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
