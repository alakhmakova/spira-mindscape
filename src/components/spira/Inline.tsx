import { useState, useRef, useEffect } from "react";
import { Plus, X, Check, Pencil } from "lucide-react";
import { cn } from "@/lib/utils";

type Item = { id: string; text: string };
type Variant = "default" | "onPrimary";

export function InlineList({
  items,
  emptyHint,
  placeholder,
  onAdd,
  onUpdate,
  onRemove,
  marker = "dot",
  tone = "default",
  variant = "default",
}: {
  items: Item[];
  emptyHint: string;
  placeholder: string;
  onAdd: (text: string) => void;
  onUpdate: (id: string, text: string) => void;
  onRemove: (id: string) => void;
  marker?: "dot" | "check" | "warn";
  tone?: "default" | "warning";
  variant?: Variant;
}) {
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState("");
  const onPrimary = variant === "onPrimary";

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    onAdd(t);
    setDraft("");
  };

  return (
    <div className="space-y-2">
      {items.length === 0 && (
        <p
          className={cn(
            "text-sm italic",
            onPrimary ? "text-primary-foreground/70" : "text-muted-foreground",
          )}
        >
          {emptyHint}
        </p>
      )}
      <ul className="space-y-1">
        {items.map((it) => (
          <li
            key={it.id}
            className={cn(
              "group flex items-start gap-3 rounded-md px-2 py-2 transition-colors",
              onPrimary ? "hover:bg-primary-foreground/10" : "hover:bg-secondary/70",
            )}
          >
            <Marker kind={marker} tone={tone} variant={variant} />
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
                className={cn(
                  "flex-1 bg-transparent outline-none text-sm",
                  onPrimary && "text-primary-foreground placeholder:text-primary-foreground/50",
                )}
              />
            ) : (
              <button
                onClick={() => {
                  setEditingId(it.id);
                  setEditText(it.text);
                }}
                className={cn(
                  "flex-1 text-left text-sm leading-relaxed",
                  onPrimary && "text-primary-foreground",
                )}
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
                className={cn(
                  "p-1",
                  onPrimary
                    ? "text-primary-foreground/70 hover:text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
                aria-label="Edit"
              >
                <Pencil className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => onRemove(it.id)}
                className={cn(
                  "p-1",
                  onPrimary
                    ? "text-primary-foreground/70 hover:text-destructive-foreground"
                    : "text-muted-foreground hover:text-destructive",
                )}
                aria-label="Remove"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          </li>
        ))}
      </ul>
      <div
        className={cn(
          "flex items-center gap-2 mt-1 px-2 py-1.5 rounded-md border-2 border-dashed",
          onPrimary
            ? "border-primary-foreground/30 focus-within:border-primary-foreground/60"
            : "border-border focus-within:border-primary/40",
        )}
      >
        <Plus
          className={cn(
            "h-3.5 w-3.5",
            onPrimary ? "text-primary-foreground/70" : "text-muted-foreground",
          )}
        />
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
          placeholder={placeholder}
          className={cn(
            "flex-1 bg-transparent text-sm outline-none py-1",
            onPrimary
              ? "text-primary-foreground placeholder:text-primary-foreground/50"
              : "placeholder:text-muted-foreground",
          )}
        />
        {draft && (
          <button
            onClick={add}
            className={cn(
              "text-xs font-semibold",
              onPrimary
                ? "text-primary-foreground underline underline-offset-2"
                : "link-action",
            )}
          >
            Add
          </button>
        )}
      </div>
    </div>
  );
}

function Marker({
  kind,
  tone,
  variant = "default",
}: {
  kind: "dot" | "check" | "warn";
  tone: string;
  variant?: Variant;
}) {
  const onPrimary = variant === "onPrimary";
  if (kind === "check")
    return (
      <span
        className={cn(
          "mt-0.5 h-4 w-4 shrink-0 rounded-sm grid place-items-center",
          onPrimary
            ? "bg-primary-foreground/15 border border-primary-foreground/40 text-primary-foreground"
            : "bg-primary-soft border border-primary/40 text-primary",
        )}
      >
        <Check className="h-3 w-3" strokeWidth={3} />
      </span>
    );
  return (
    <span
      className={cn(
        "mt-2 h-1.5 w-1.5 shrink-0 rounded-full",
        onPrimary
          ? "bg-primary-foreground"
          : tone === "warning"
            ? "bg-warning"
            : "bg-primary",
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
