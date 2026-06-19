import { useRef, useState } from "react";
import { Check, Plus, X, GripVertical } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import type { Goal } from "@/lib/spira/types";
import { cn } from "@/lib/utils";
import { InlineText } from "./Inline";

export function OptionsList({ goal }: { goal: Goal }) {
  const {
    addOption,
    updateOption,
    selectOption,
    removeOption,
    reorderOptions,
  } = useSpira();
  const [draft, setDraft] = useState("");
  // Use a ref so onDrop always reads the latest source index without stale closures.
  const dragSourceRef = useRef<number | null>(null);
  const touchSourceRef = useRef<number | null>(null);
  const [dragOverIdx, setDragOverIdx] = useState<number | null>(null);

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    addOption(goal.id, t);
    setDraft("");
  };

  const handleOptionClick = (optId: string, isSelected: boolean) => {
    if (isSelected) {
      updateOption(goal.id, optId, { selected: false });
    } else {
      selectOption(goal.id, optId);
    }
  };

  return (
    <div className="space-y-3">
      {goal.options.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          What strategies could move you forward? Add a few, then choose one.
        </p>
      )}
      <ul className="space-y-3">
        {goal.options.map((opt, idx) => (
          <li
            key={opt.id}
            data-option-index={idx}
            onDragOver={(e) => {
              e.preventDefault();
              e.dataTransfer.dropEffect = "move";
              setDragOverIdx(idx);
            }}
            onDragLeave={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node))
                setDragOverIdx(null);
            }}
            onDrop={(e) => {
              e.preventDefault();
              if (dragSourceRef.current !== null && dragSourceRef.current !== idx)
                reorderOptions(goal.id, dragSourceRef.current, idx);
              dragSourceRef.current = null;
              setDragOverIdx(null);
            }}
            onTouchMove={(e) => {
              if (touchSourceRef.current === null) return;
              const touch = e.touches[0];
              const target = document
                .elementFromPoint(touch.clientX, touch.clientY)
                ?.closest("[data-option-index]");
              const nextIdx = target
                ? Number((target as HTMLElement).dataset.optionIndex)
                : NaN;
              if (!Number.isNaN(nextIdx) && nextIdx !== touchSourceRef.current) {
                reorderOptions(goal.id, touchSourceRef.current, nextIdx);
                touchSourceRef.current = nextIdx;
              }
            }}
            onTouchEnd={() => { touchSourceRef.current = null; }}
            className={cn(
              "group flex items-stretch overflow-hidden rounded-md border transition-colors",
              opt.selected
                ? "border-primary"
                : "border-border hover:border-primary/50",
              dragOverIdx === idx && dragSourceRef.current !== idx && "ring-2 ring-primary/40",
            )}
          >
            {/* Left Section with Radio */}
            <button
              onClick={() => handleOptionClick(opt.id, opt.selected)}
              className={cn(
                "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
                opt.selected
                  ? "bg-primary-soft border-primary"
                  : "bg-surface border-border hover:bg-secondary/50",
              )}
              aria-label={
                opt.selected ? "Deselect strategy" : "Select strategy"
              }
            >
              <div
                className={cn(
                  "h-5 w-5 rounded-full border-2 grid place-items-center transition-colors",
                  opt.selected ? "border-primary" : "border-border-strong",
                )}
              >
                {opt.selected && (
                  <span className="h-2.5 w-2.5 rounded-full bg-primary" />
                )}
              </div>
            </button>

            {/* Right Section with Text */}
            <div className="flex-1 flex items-center bg-surface px-4 py-3 min-h-[48px]">
              <InlineText
                value={opt.text}
                onChange={(text) => updateOption(goal.id, opt.id, { text })}
                className="flex-1 text-base font-medium leading-relaxed"
                ariaLabel="Edit strategy"
              />

              <button
                draggable
                onDragStart={(e) => {
                  dragSourceRef.current = idx;
                  e.dataTransfer.effectAllowed = "move";
                }}
                onDragEnd={() => {
                  dragSourceRef.current = null;
                  setDragOverIdx(null);
                }}
                onTouchStart={() => { touchSourceRef.current = idx; }}
                className="ml-2 grid h-8 w-6 shrink-0 place-items-center touch-none text-muted-foreground hover:text-foreground cursor-grab active:cursor-grabbing transition-colors"
                aria-label="Drag to reorder"
              >
                <GripVertical className="h-4 w-4" />
              </button>

              <button
                onClick={() => removeOption(goal.id, opt.id)}
                className="ml-1 grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-destructive transition-colors"
                aria-label="Remove"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </li>
        ))}
      </ul>

      {/* Creation Field */}
      <div className="flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary mt-4">
        <div className="w-12 shrink-0 flex items-center justify-center border-r border-border bg-secondary/30">
          <Plus className="h-4 w-4 text-muted-foreground" />
        </div>
        <div className="flex-1 flex items-center px-4 py-1 relative">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && add()}
            placeholder="Add a strategy…"
            className="flex-1 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
          />
          {draft && (
            <button
              onClick={add}
              className="ml-2 rounded-md bg-primary/10 px-2 py-1 text-sm font-semibold text-primary hover:bg-primary/20"
            >
              Add
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
