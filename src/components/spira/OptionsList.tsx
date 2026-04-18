import { useState } from "react";
import { Check, Plus, X, GripVertical } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import type { Goal } from "@/lib/spira/types";
import { cn } from "@/lib/utils";

export function OptionsList({ goal }: { goal: Goal }) {
  const { addOption, updateOption, selectOption, removeOption, reorderOptions } = useSpira();
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState("");
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    addOption(goal.id, t);
    setDraft("");
  };

  return (
    <div className="space-y-3">
      {goal.options.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          What strategies could move you forward? Add a few, then choose one.
        </p>
      )}
      <ul className="space-y-2">
        {goal.options.map((opt, idx) => (
          <li
            key={opt.id}
            draggable
            onDragStart={() => setDragIdx(idx)}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => {
              if (dragIdx !== null && dragIdx !== idx) reorderOptions(goal.id, dragIdx, idx);
              setDragIdx(null);
            }}
            className={cn(
              "group flex items-center gap-3 rounded-md border-2 px-3 py-3 transition-colors",
              opt.selected
                ? "bg-primary-soft border-primary/50"
                : "bg-surface border-border hover:border-border-strong",
            )}
          >
            <GripVertical className="h-4 w-4 text-muted-foreground/50 cursor-grab shrink-0" />
            {/* Radio */}
            <button
              onClick={() => selectOption(goal.id, opt.id)}
              className={cn(
                "h-5 w-5 rounded-full border-2 grid place-items-center shrink-0 transition-colors",
                opt.selected
                  ? "border-primary"
                  : "border-border-strong hover:border-primary",
              )}
              aria-label="Select strategy"
            >
              {opt.selected && (
                <span className="h-2.5 w-2.5 rounded-full bg-primary" />
              )}
            </button>
            {editingId === opt.id ? (
              <input
                autoFocus
                value={editText}
                onChange={(e) => setEditText(e.target.value)}
                onBlur={() => {
                  if (editText.trim()) updateOption(goal.id, opt.id, { text: editText.trim() });
                  setEditingId(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    if (editText.trim()) updateOption(goal.id, opt.id, { text: editText.trim() });
                    setEditingId(null);
                  }
                  if (e.key === "Escape") setEditingId(null);
                }}
                className="flex-1 bg-transparent outline-none text-sm font-medium"
              />
            ) : (
              <button
                onClick={() => {
                  setEditingId(opt.id);
                  setEditText(opt.text);
                }}
                className="flex-1 text-left text-sm font-medium"
              >
                {opt.text}
              </button>
            )}
            <button
              onClick={() => removeOption(goal.id, opt.id)}
              className="opacity-0 group-hover:opacity-100 p-1 text-muted-foreground hover:text-destructive transition-opacity"
              aria-label="Remove"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </li>
        ))}
      </ul>
      <div className="flex items-center gap-2 px-3 py-2 rounded-md border-2 border-dashed border-border focus-within:border-primary/40">
        <Plus className="h-4 w-4 text-muted-foreground" />
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
          placeholder="Add a strategy…"
          className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground py-1"
        />
        {draft && (
          <button
            onClick={add}
            className="text-xs link-action font-semibold"
          >
            Add
          </button>
        )}
      </div>
    </div>
  );
}
