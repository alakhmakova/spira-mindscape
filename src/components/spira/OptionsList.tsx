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
    <div className="space-y-2">
      {goal.options.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          What strategies could move you forward? Add a few, then choose one.
        </p>
      )}
      <ul className="space-y-1.5">
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
              "group flex items-center gap-3 rounded-md border hairline px-3 py-2.5 transition-colors",
              opt.selected
                ? "bg-primary/8 border-primary/40"
                : "bg-surface hover:border-border-strong",
            )}
          >
            <GripVertical className="h-4 w-4 text-muted-foreground cursor-grab shrink-0" />
            <button
              onClick={() => selectOption(goal.id, opt.id)}
              className={cn(
                "h-5 w-5 rounded-full border-2 grid place-items-center shrink-0 transition-colors",
                opt.selected
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border-strong hover:border-primary",
              )}
              aria-label="Select"
            >
              {opt.selected && <Check className="h-3 w-3" />}
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
                className="flex-1 bg-transparent outline-none text-sm"
              />
            ) : (
              <button
                onClick={() => {
                  setEditingId(opt.id);
                  setEditText(opt.text);
                }}
                className="flex-1 text-left text-sm"
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
      <div className="flex items-center gap-2 px-3">
        <Plus className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
          placeholder="Add a strategy…"
          className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground/60 py-2"
        />
        {draft && (
          <button onClick={add} className="text-xs text-primary hover:underline">
            Add
          </button>
        )}
      </div>
    </div>
  );
}
