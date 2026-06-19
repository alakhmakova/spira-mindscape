import { useState } from "react";
import { Plus, Trash2, ArrowUp, ArrowDown } from "lucide-react";
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

  const last = goal.options.length - 1;

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
            className={cn(
              "group flex items-stretch rounded-md border transition-colors",
              opt.selected
                ? "border-primary"
                : "border-border hover:border-primary/50",
            )}
          >
            {/* Left Section with Radio */}
            <button
              onClick={() => handleOptionClick(opt.id, opt.selected)}
              className={cn(
                "w-12 shrink-0 flex items-center justify-center border-r transition-colors rounded-l-md",
                opt.selected
                  ? "bg-primary-soft border-primary"
                  : "bg-surface border-border hover:bg-secondary/50",
              )}
              aria-label={opt.selected ? "Deselect strategy" : "Select strategy"}
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

            {/* Right Section with Text and Actions */}
            <div className="flex-1 flex items-center bg-surface px-4 py-3 min-h-[48px] rounded-r-md">
              <InlineText
                value={opt.text}
                onChange={(text) => updateOption(goal.id, opt.id, { text })}
                className="flex-1 text-base font-medium leading-relaxed"
                ariaLabel="Edit strategy"
              />

              {/* Up / Down */}
              <div className="ml-2 flex flex-col shrink-0">
                <button
                  onClick={() => reorderOptions(goal.id, idx, idx - 1)}
                  disabled={idx === 0}
                  className="flex h-5 w-7 items-center justify-center rounded text-[#ea580c] transition-opacity disabled:opacity-25 disabled:cursor-default"
                  aria-label="Move up"
                >
                  <ArrowUp className="h-4 w-4" />
                </button>
                <button
                  onClick={() => reorderOptions(goal.id, idx, idx + 1)}
                  disabled={idx === last}
                  className="flex h-5 w-7 items-center justify-center rounded text-[#ea580c] transition-opacity disabled:opacity-25 disabled:cursor-default"
                  aria-label="Move down"
                >
                  <ArrowDown className="h-4 w-4" />
                </button>
              </div>

              <button
                onClick={() => removeOption(goal.id, opt.id)}
                className="ml-1 grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:text-destructive transition-colors"
                aria-label="Remove"
              >
                <Trash2 className="h-4 w-4" />
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
