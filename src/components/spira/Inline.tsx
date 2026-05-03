import { useState, useRef, useEffect, type TextareaHTMLAttributes } from "react";
import { Plus, X, Check, Pencil, BookmarkCheck, BookmarkX } from "lucide-react";
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
    <div className="space-y-4">
      <div
        className={cn(
          "flex items-stretch overflow-hidden rounded-md border transition-colors focus-within:border-primary",
          tone === "warning"
            ? "border-destructive/30 focus-within:border-destructive bg-surface"
            : "border-border bg-surface"
        )}
      >
        <div
          className={cn(
            "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
            tone === "warning" ? "border-destructive/20 bg-destructive/5" : "border-border bg-secondary/30"
          )}
        >
          <Plus
            className={cn(
              "h-4 w-4",
              tone === "warning" ? "text-destructive/70" : "text-primary/70",
            )}
          />
        </div>
        <div className="flex-1 flex items-center px-4 py-1 relative">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && add()}
            placeholder={placeholder}
            className="flex-1 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
          />
          {draft && (
            <button
              onClick={add}
              className={cn(
                "ml-2 text-sm font-semibold rounded-md px-2 py-1",
                tone === "warning"
                  ? "bg-destructive/10 text-destructive hover:bg-destructive/20"
                  : "bg-primary/10 text-primary hover:bg-primary/20"
              )}
            >
              Add
            </button>
          )}
        </div>
      </div>

      {items.length === 0 && (
        <p className="text-[15px] italic text-muted-foreground text-center py-4">
          {emptyHint}
        </p>
      )}

      <ul className="space-y-2">
        {items.map((it) => (
          <li
            key={it.id}
            className={cn(
              "group flex items-start gap-3 rounded-md px-2 py-2 transition-colors",
              onPrimary ? "hover:bg-primary-foreground/10" : "hover:bg-white/60",
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
                  "flex-1 rounded-md border border-input bg-surface px-3.5 py-2 text-base outline-none placeholder:text-muted-foreground/75 focus:border-primary focus:ring-[3px] focus:ring-ring",
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
                  "flex-1 text-left text-[15px] leading-relaxed",
                  onPrimary && "text-primary-foreground",
                )}
              >
                {it.text}
              </button>
            )}
            <div className="flex">
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
                <X className="h-4 w-4" />
              </button>
            </div>
          </li>
        ))}
      </ul>
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
  if (kind === "check") {
    return <BookmarkCheck className={cn("mt-0.5 h-5 w-5 shrink-0", onPrimary ? "text-primary-foreground" : "text-primary")} strokeWidth={2} />;
  }
  if (kind === "warn") {
    return <BookmarkX className={cn("mt-0.5 h-5 w-5 shrink-0", onPrimary ? "text-primary-foreground" : "text-[#ea580c]")} strokeWidth={2} />;
  }
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
  ...props
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
} & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange">) {
  const ref = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    
    const updateHeight = () => {
      el.style.height = "auto";
      el.style.height = el.scrollHeight + "px";
    };
    
    updateHeight();
    
    window.addEventListener("resize", updateHeight);
    return () => window.removeEventListener("resize", updateHeight);
  }, [value]);
  return (
    <textarea
      ref={ref}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      rows={1}
      {...props}
      className={cn(
        "w-full resize-none overflow-hidden bg-transparent outline-none text-base leading-relaxed placeholder:text-muted-foreground/70",
        className,
      )}
    />
  );
}
