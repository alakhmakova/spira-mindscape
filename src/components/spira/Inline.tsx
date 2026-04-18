import { useState, useRef, useEffect } from "react";
import { Plus, X, Check, Pencil } from "lucide-react";
import { cn } from "@/lib/utils";

type Item = { id: string; text: string };

export function InlineList({
  items,
  emptyHint,
  placeholder,
  onAdd,
  onUpdate,
  onRemove,
  marker = "dot",
  tone = "default",
}: {
  items: Item[];
  emptyHint: string;
  placeholder: string;
  onAdd: (text: string) => void;
  onUpdate: (id: string, text: string) => void;
  onRemove: (id: string) => void;
  marker?: "dot" | "check" | "warn";
  tone?: "default" | "warning";
}) {
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState("");

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    onAdd(t);
    setDraft("");
  };

  return (
    <div className="space-y-2">
      {items.length === 0 && (
        <p className="text-sm text-muted-foreground italic">{emptyHint}</p>
      )}
      <ul className="space-y-1">
        {items.map((it) => (
          <li
            key={it.id}
            className="group flex items-start gap-3 rounded-md px-2 py-2 hover:bg-secondary/70 transition-colors"
          >
            <Marker kind={marker} tone={tone} />
            {editingId === it.id ? (
              <input
                autoFocus
                value={editText}
                onChange={(e) => setEditText(e.target.value)}
                onBlur={() => {
                  if (editText.trim()) onUpdate(it.id, editText.trim());
                  setEditingId(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    if (editText.trim()) onUpdate(it.id, editText.trim());
                    setEditingId(null);
                  }
                  if (e.key === "Escape") setEditingId(null);
                }}
                className="flex-1 bg-transparent outline-none text-sm"
              />
            ) : (
              <button
                onClick={() => {
                  setEditingId(it.id);
                  setEditText(it.text);
                }}
                className="flex-1 text-left text-sm leading-relaxed"
              >
                {it.text}
              </button>
            )}
            <div className="flex opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                onClick={() => {
                  setEditingId(it.id);
                  setEditText(it.text);
                }}
                className="p-1 text-muted-foreground hover:text-foreground"
                aria-label="Edit"
              >
                <Pencil className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => onRemove(it.id)}
                className="p-1 text-muted-foreground hover:text-destructive"
                aria-label="Remove"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          </li>
        ))}
      </ul>
      <div className="flex items-center gap-2 mt-1 px-2 py-1.5 rounded-md border-2 border-dashed border-border focus-within:border-primary/40">
        <Plus className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
          placeholder={placeholder}
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

function Marker({ kind, tone }: { kind: "dot" | "check" | "warn"; tone: string }) {
  if (kind === "check")
    return (
      <span className="mt-0.5 h-4 w-4 shrink-0 rounded-sm bg-primary-soft border border-primary/40 grid place-items-center text-primary">
        <Check className="h-3 w-3" strokeWidth={3} />
      </span>
    );
  return (
    <span
      className={cn(
        "mt-2 h-1.5 w-1.5 shrink-0 rounded-full",
        tone === "warning" ? "bg-warning" : "bg-primary",
      )}
    />
  );
}

export function AutoTextarea({
  value,
  onChange,
  placeholder,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = el.scrollHeight + "px";
  }, [value]);
  return (
    <textarea
      ref={ref}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      rows={1}
      className={cn(
        "w-full resize-none bg-transparent outline-none text-base leading-relaxed placeholder:text-muted-foreground/70",
        className,
      )}
    />
  );
}
