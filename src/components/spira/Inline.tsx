import {
  useState,
  useRef,
  useEffect,
  type TextareaHTMLAttributes,
} from "react";
import { Plus, X, CircleCheck, CircleX, TriangleAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import { splitUrls, isSafeHttpUrl } from "@/lib/spira/links";

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
    onAdd(t); // a URL in the text is auto-linked on display
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
        <div className="flex-1 min-w-0 flex items-center px-4 py-1 relative">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && add()}
            placeholder={placeholder}
            className="flex-1 min-w-0 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
          />
          {draft && (
            <button
              onClick={add}
              className={cn(
                "ml-2 mr-1 shrink-0 text-sm font-medium rounded-md px-2 py-1",
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

/**
 * Inline, text-styled editable field. Looks like the surrounding text (no box/label), commits
 * on blur / Enter, reverts on Escape, and never saves a required field empty.
 *
 * A URL in the text renders as a plain clickable link (no naming, no chip). Editing is plain
 * text, so a URL is edited/deleted like any other text — tap the link opens it, tapping the rest
 * of the field enters edit mode. `min-w-0` + `overflow-wrap:anywhere` keep a long URL wrapping
 * inside its card instead of overflowing it.
 */
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
  const ref = useRef<HTMLTextAreaElement>(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState(false);
  // Set before a programmatic blur (Escape / paste-commit) so the blur handler doesn't
  // double-commit a stale draft.
  const skipCommitRef = useRef(false);
  // Tracks the last non-empty value — restoring from `value` alone would revert to a stale
  // value if the store debounces saves.
  const lastGoodValueRef = useRef(value);
  useEffect(() => {
    if (value.trim()) lastGoodValueRef.current = value;
  }, [value]);

  const autoSize = (el: HTMLTextAreaElement) => {
    el.style.height = "auto";
    el.style.height = el.scrollHeight + "px";
  };

  const enterEdit = () => {
    setDraft(value);
    setEditing(true);
  };

  // On entering edit mode, focus the textarea, place the caret at the end, and size it to content.
  useEffect(() => {
    if (!editing || !ref.current) return;
    const el = ref.current;
    el.focus();
    const len = el.value.length;
    el.setSelectionRange(len, len);
    autoSize(el);
  }, [editing]);

  const commit = (raw: string) => {
    const trimmed = raw.trim();
    setEditing(false);
    if (!trimmed) {
      // Never save empty — revert to the last good value; flag required fields.
      setDraft(value);
      if (required) setError(true);
      return;
    }
    lastGoodValueRef.current = trimmed;
    setError(false);
    if (trimmed !== value) onChange(trimmed);
  };

  // A pure-URL paste commits + exits immediately, so the view re-renders it as a live link with no
  // extra tap. Mixed-text pastes insert normally (native textarea paste).
  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pasted = e.clipboardData.getData("text/plain").trim();
    if (!/\s/.test(pasted) && isSafeHttpUrl(pasted)) {
      e.preventDefault();
      const el = e.currentTarget;
      const start = el.selectionStart ?? draft.length;
      const end = el.selectionEnd ?? draft.length;
      const next = draft.slice(0, start) + pasted + draft.slice(end);
      skipCommitRef.current = true; // the unmount blur must not re-commit a stale draft
      setDraft(next);
      commit(next);
    }
  };

  if (editing) {
    // A real <textarea> (not contentEditable) so the Android IME can't desync and duplicate text.
    return (
      <span className={cn("flex min-w-0 flex-col", className)}>
        <textarea
          ref={ref}
          value={draft}
          rows={1}
          aria-label={ariaLabel}
          placeholder={placeholder}
          onChange={(e) => {
            setDraft(e.target.value);
            autoSize(e.currentTarget);
          }}
          onBlur={() => {
            if (skipCommitRef.current) {
              skipCommitRef.current = false;
              setEditing(false);
              return;
            }
            commit(draft);
          }}
          onPaste={handlePaste}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              e.currentTarget.blur();
            }
            if (e.key === "Escape") {
              skipCommitRef.current = true;
              setDraft(value);
              setError(false);
              e.currentTarget.blur();
            }
          }}
          style={{ font: "inherit" }}
          className="block w-full min-w-0 resize-none overflow-hidden whitespace-pre-wrap [overflow-wrap:anywhere] bg-transparent text-inherit outline-none placeholder:text-muted-foreground/75"
        />
      </span>
    );
  }

  const isEmpty = value.trim() === "";
  const segments = splitUrls(value);

  return (
    <span className={cn("flex min-w-0 flex-col", className)}>
      <span
        role="textbox"
        tabIndex={0}
        aria-label={ariaLabel}
        // Focus bubbles in React — only enter edit mode when the span itself is focused, not when
        // a child link receives focus (which would swallow its click).
        onFocus={(e) => {
          if (e.target === e.currentTarget) enterEdit();
        }}
        onClick={() => enterEdit()}
        className="block min-w-0 cursor-text whitespace-pre-wrap [overflow-wrap:anywhere] outline-none"
      >
        {isEmpty ? (
          <span className="text-muted-foreground/75">{placeholder}</span>
        ) : (
          segments.map((seg, i) =>
            seg.type === "text" ? (
              <span key={i}>{seg.value}</span>
            ) : (
              <UrlLink key={i} url={seg.url} />
            ),
          )
        )}
      </span>
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

/** A bare URL rendered as a plain clickable link. Inline, so it wraps with the text. */
function UrlLink({ url }: { url: string }) {
  const safe = isSafeHttpUrl(url);
  return (
    <a
      href={safe ? url : undefined}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()} // open the URL; don't enter the field's edit mode
      className="text-primary underline decoration-1 underline-offset-2 [overflow-wrap:anywhere] hover:decoration-2"
    >
      {url}
    </a>
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
    if (!required) {
      onChange(v);
      return;
    }
    setDraft(v);
    setError(false);
    if (v.trim()) {
      lastGoodDraftRef.current = v;
      onChange(v);
    }
  };

  const handleBlur = () => {
    if (required && !draft.trim()) {
      setDraft(lastGoodDraftRef.current);
      setError(true);
    }
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
