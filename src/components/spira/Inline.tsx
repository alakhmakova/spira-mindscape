import {
  useState,
  useRef,
  useEffect,
  type TextareaHTMLAttributes,
} from "react";
import { Plus, X, CircleCheck, CircleX, TriangleAlert } from "lucide-react";
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
            : "border-border bg-surface",
        )}
      >
        <div
          className={cn(
            "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
            tone === "warning"
              ? "border-destructive/20 bg-destructive/5"
              : "border-border bg-secondary/30",
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
                  : "bg-primary/10 text-primary hover:bg-primary/20",
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
              onPrimary
                ? "hover:bg-primary-foreground/10"
                : "hover:bg-white/60",
            )}
          >
            <Marker kind={marker} tone={tone} variant={variant} />
            <InlineText
              value={it.text}
              onChange={(next) => next.trim() && onUpdate(it.id, next.trim())}
              className={cn(
                "flex-1 text-left text-[15px] leading-relaxed",
                onPrimary && "text-primary-foreground",
              )}
              ariaLabel="Edit item"
            />
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

export function InlineText({
  value,
  onChange,
  placeholder,
  ariaLabel,
  className,
  required = true,
  requiredMessage = "This field is required",
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
  className?: string;
  required?: boolean;
  requiredMessage?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const [error, setError] = useState(false);
  // Tracks the last non-empty text typed — restoring from `value` prop alone
  // would revert to a stale value if the store debounces saves.
  const lastGoodValueRef = useRef(value);

  useEffect(() => {
    if (ref.current && document.activeElement !== ref.current) {
      ref.current.textContent = value;
      if (value.trim()) lastGoodValueRef.current = value;
    }
    setError(false);
  }, [value]);

  const commit = (el: HTMLSpanElement) => {
    const next = (el.textContent || "").trim();
    if (!next) {
      el.textContent = lastGoodValueRef.current;
      if (required) setError(true);
      return;
    }
    lastGoodValueRef.current = next;
    setError(false);
    if (next !== value) onChange(next);
  };

  return (
    <span className={cn("flex flex-col", className)}>
      <span
        ref={ref}
        contentEditable
        suppressContentEditableWarning
        role="textbox"
        tabIndex={0}
        aria-label={ariaLabel}
        data-placeholder={placeholder}
        onFocus={(e) => {
          setError(false);
          const range = document.createRange();
          range.selectNodeContents(e.currentTarget);
          range.collapse(false);
          const selection = window.getSelection();
          selection?.removeAllRanges();
          selection?.addRange(range);
        }}
        onBlur={(e) => commit(e.currentTarget)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            e.currentTarget.blur();
          }
          if (e.key === "Escape") {
            e.currentTarget.textContent = value;
            setError(false);
            e.currentTarget.blur();
          }
        }}
        className="block min-w-[1ch] cursor-text whitespace-pre-wrap break-words outline-none empty:before:content-[attr(data-placeholder)] empty:before:text-muted-foreground/75"
      />
      {error && (
        <span
          role="alert"
          className="flex items-center gap-1.5 mt-1 text-[13px] font-medium text-destructive no-underline not-italic"
        >
          <TriangleAlert className="h-3.5 w-3.5 shrink-0" />
          {requiredMessage}
        </span>
      )}
    </span>
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
    return (
      <CircleCheck
        className={cn(
          "mt-0.5 h-5 w-5 shrink-0",
          onPrimary ? "text-primary-foreground" : "text-primary",
        )}
        strokeWidth={2}
      />
    );
  }
  if (kind === "warn") {
    return (
      <CircleX
        className={cn(
          "mt-0.5 h-5 w-5 shrink-0",
          onPrimary ? "text-primary-foreground" : "text-[#ea580c]",
        )}
        strokeWidth={2}
      />
    );
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
  required = false,
  requiredMessage = "This can't be empty — it was kept.",
  ...props
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
  required?: boolean;
  requiredMessage?: string;
} & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange">) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [error, setError] = useState(false);
  // Local buffer for required fields so the user can clear-and-retype without the empty
  // value being pushed to the store (which would blank a required field / trigger a sync
  // error). Non-required fields pass through unchanged.
  const [draft, setDraft] = useState(value);
  const shown = required ? draft : value;
  // Tracks the last non-empty text the user typed — used as the fallback on blur.
  // `value` from props may lag behind if the store debounces saves, which would
  // wrongly restore an older title instead of the one the user just typed.
  const lastGoodDraftRef = useRef(value);

  useEffect(() => {
    if (required && document.activeElement !== ref.current) {
      setDraft(value);
      if (value.trim()) lastGoodDraftRef.current = value;
    }
  }, [value, required]);

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
  }, [shown]);

  const handleChange = (v: string) => {
    if (!required) { onChange(v); return; }
    setDraft(v);
    setError(false);
    if (v.trim()) {
      lastGoodDraftRef.current = v;
      onChange(v);
    }
  };

  const handleBlur = () => {
    if (required && !draft.trim()) { setDraft(lastGoodDraftRef.current); setError(true); }
  };

  const textarea = (
    <textarea
      ref={ref}
      value={shown}
      onChange={(e) => handleChange(e.target.value)}
      onBlur={handleBlur}
      onFocus={() => setError(false)}
      placeholder={placeholder}
      rows={1}
      {...props}
      className={cn(
        "w-full resize-none overflow-hidden bg-transparent outline-none text-base leading-relaxed placeholder:text-muted-foreground/70",
        className,
      )}
    />
  );

  if (!required) return textarea;
  return (
    <div className="w-full">
      {textarea}
      {error && (
        <p
          role="alert"
          className="flex items-center gap-1.5 mt-1 text-[13px] font-medium text-destructive"
        >
          <TriangleAlert className="h-3.5 w-3.5 shrink-0" />
          {requiredMessage}
        </p>
      )}
    </div>
  );
}
