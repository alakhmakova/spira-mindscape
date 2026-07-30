import {
  useState,
  useRef,
  useEffect,
  useLayoutEffect,
  type CSSProperties,
  type ReactNode,
  type TextareaHTMLAttributes,
} from "react";
import {
  Plus,
  X,
  CircleCheck,
  CircleX,
  TriangleAlert,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
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
  maxLength,
  maxLengthLabel = "Item",
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
  /** Character limit for both the add-field and inline edits (mirrors the server). Over-limit
   *  input is flagged and blocked (add disabled), never silently truncated. */
  maxLength?: number;
  maxLengthLabel?: string;
}) {
  const [draft, setDraft] = useState("");
  const onPrimary = variant === "onPrimary";
  const overBy =
    maxLength !== undefined && draft.trim().length > maxLength
      ? draft.trim().length
      : 0;

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    if (maxLength !== undefined && t.length > maxLength) return; // too long — blocked, message shown
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
              disabled={overBy > 0}
              className={cn(
                "ml-2 mr-1 shrink-0 text-sm font-medium rounded-md px-2 py-1 disabled:opacity-40",
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
      {overBy > 0 && (
        <p className="text-[13px] font-medium text-destructive" role="alert">
          {maxLengthLabel} is too long — max {maxLength} characters (you have{" "}
          {overBy}). Trim it to add.
        </p>
      )}

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
              maxLength={maxLength}
              maxLengthLabel={maxLengthLabel}
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
  maxLength,
  maxLengthLabel = "This field",
  clampLines,
  forceCollapsed = false,
  floatTopRight,
  readOnly = false,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
  className?: string;
  required?: boolean;
  requiredMessage?: string;
  /** Character limit (mirrors the server). Over-limit text is allowed on screen and flagged,
   *  but never committed — so an over-length value never reaches the store / triggers a sync
   *  error, and is never silently truncated. */
  maxLength?: number;
  /** Field name used in the "too long" message, e.g. "Strategy". */
  maxLengthLabel?: string;
  /** When set, the read view clamps to this many lines with a "Show more"/"Show less" toggle. */
  clampLines?: number;
  /** Force the clamped (collapsed) view regardless of the toggle — e.g. while dragging. */
  forceCollapsed?: boolean;
  /** Node floated to the top-right of the read view; inline text wraps under it (line 1 beside
   *  it, lines 2+ below). Hidden while editing (it would overlap the textarea). */
  floatTopRight?: ReactNode;
  /** When true, the field is display-only: clicking/focusing never enters edit mode. */
  readOnly?: boolean;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const displayRef = useRef<HTMLSpanElement>(null);
  const [editing, setEditing] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [overflowing, setOverflowing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState(false);
  // Shown when a pure-URL paste would push the field over its maxLength — the paste is
  // refused (not truncated, not committed) so an over-length URL never reaches the store.
  const [pasteError, setPasteError] = useState<string | null>(null);
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
    if (readOnly) return; // display-only (e.g. reorder mode): never enter edit
    setDraft(value);
    setPasteError(null);
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
    if (!trimmed) {
      // Never save empty — revert to the last good value; flag required fields.
      setEditing(false);
      setDraft(value);
      if (required) setError(true);
      return;
    }
    if (maxLength !== undefined && trimmed.length > maxLength) {
      // Too long — do NOT save or truncate. Stay in edit mode so the user can trim
      // (the live "too long" message below explains why); Escape discards.
      return;
    }
    setEditing(false);
    lastGoodValueRef.current = trimmed;
    setError(false);
    if (trimmed !== value) onChange(trimmed);
  };

  // Measure whether the read view exceeds the clamp, so the "Show more" toggle only appears when
  // the text actually overflows. `scrollHeight` reports the full content height even while the
  // element is line-clamped, so this works in both collapsed and expanded states.
  useLayoutEffect(() => {
    if (clampLines === undefined || editing) return;
    const el = displayRef.current;
    if (!el) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight)) return;
      setOverflowing(el.scrollHeight > lineHeight * clampLines + 2);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [clampLines, value, editing]);

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
      // The <textarea maxLength> attribute doesn't cap this programmatic path, so guard it here:
      // refuse the paste rather than truncate the URL or push an over-length value to the server.
      if (maxLength !== undefined && next.length > maxLength) {
        setPasteError(
          `That link is too long to save here (max ${maxLength} characters).`,
        );
        return;
      }
      setPasteError(null);
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
            if (pasteError) setPasteError(null);
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
        {pasteError ? (
          <span className="mt-1 text-[13px] font-medium text-destructive no-underline not-italic">
            {pasteError}
          </span>
        ) : (
          maxLength !== undefined &&
          draft.trim().length > maxLength && (
            <span className="mt-1 text-[13px] font-medium text-destructive no-underline not-italic">
              {maxLengthLabel} is too long — max {maxLength} characters (you
              have {draft.trim().length}). Trim it to save.
            </span>
          )
        )}
      </span>
    );
  }

  const isEmpty = value.trim() === "";
  const segments = splitUrls(value);
  const collapsed = clampLines !== undefined && (forceCollapsed || !expanded);
  // A `max-height` of N line-heights (`lh`) clamps to N lines while letting inline text wrap
  // around the internal `floatTopRight` element — which `-webkit-line-clamp` would not.
  const clampStyle: CSSProperties | undefined =
    collapsed && overflowing
      ? { maxHeight: `${clampLines}lh`, overflow: "hidden" }
      : undefined;

  const showToggle = clampLines !== undefined && overflowing && !forceCollapsed;

  return (
    <span className={cn("flex min-w-0 flex-col", className)}>
      <span className="relative block min-w-0">
        <span
          ref={displayRef}
          role="textbox"
          tabIndex={readOnly ? -1 : 0}
          aria-label={ariaLabel}
          // Focus bubbles in React — only enter edit mode when the span itself is focused, not when
          // a child link receives focus (which would swallow its click).
          onFocus={(e) => {
            if (e.target === e.currentTarget) enterEdit();
          }}
          onClick={() => enterEdit()}
          style={clampStyle}
          className={cn(
            "block min-w-0 whitespace-pre-wrap [overflow-wrap:anywhere] outline-none",
            readOnly ? "cursor-[inherit]" : "cursor-text",
          )}
        >
          {/* Floated top-right slot (e.g. the rating smiley): text wraps under it. Placed first so
              inline content flows around it; not shown in edit mode (the edit branch omits it). */}
          {floatTopRight && (
            <span className="float-right ml-2 -mt-1 mb-1">{floatTopRight}</span>
          )}
          {isEmpty ? (
            <span className="text-muted-foreground/75">{placeholder}</span>
          ) : (
            <>
              {segments.map((seg, i) =>
                seg.type === "text" ? (
                  <span key={i}>{seg.value}</span>
                ) : (
                  <UrlLink key={i} url={seg.url} />
                ),
              )}
            </>
          )}
        </span>
        {/* Expand/collapse chevron at the END of the text (bottom), aligned UNDER the smiley:
            same width as the floated slot (w-8) and pinned right, so its centre lines up with the
            smiley above rather than sitting further right. `bg-surface` masks any clipped text
            behind it on the last line. */}
        {showToggle && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setExpanded((v) => !v);
            }}
            aria-label={expanded ? "Show less" : "Show more"}
            className="absolute bottom-0 right-0 grid h-6 w-8 place-items-center bg-surface text-primary transition-colors hover:text-primary/80"
          >
            {expanded ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </button>
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
  maxLength,
  maxLengthLabel = "This field",
  ...props
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
  required?: boolean;
  requiredMessage?: string;
  /** Hard cap on characters (mirrors the server limit). */
  maxLength?: number;
  maxLengthLabel?: string;
} & Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  "value" | "onChange" | "maxLength"
>) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [error, setError] = useState(false);
  // Buffer locally when the field is required OR length-capped, so a would-be-invalid value
  // (empty required field, or over the limit) is shown to the user but NOT pushed to the store
  // (which would blank a required field or reject an over-length value with a sync banner).
  const buffered = required || maxLength !== undefined;
  const [draft, setDraft] = useState(value);
  const shown = buffered ? draft : value;
  // Tracks the last non-empty text the user typed — used as the fallback on blur.
  // `value` from props may lag behind if the store debounces saves, which would
  // wrongly restore an older title instead of the one the user just typed.
  const lastGoodDraftRef = useRef(value);

  useEffect(() => {
    if (buffered && document.activeElement !== ref.current) {
      setDraft(value);
      if (value.trim()) lastGoodDraftRef.current = value;
    }
  }, [value, buffered]);

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
    if (!buffered) {
      onChange(v);
      return;
    }
    setDraft(v);
    setError(false);
    const over = maxLength !== undefined && v.length > maxLength;
    const emptyRequired = required && !v.trim();
    // Only push a value the server will accept — not empty (required) and not over the limit.
    if (!over && !emptyRequired) {
      if (v.trim()) lastGoodDraftRef.current = v;
      onChange(v);
    }
  };

  const handleBlur = () => {
    if (required && !draft.trim()) {
      setDraft(lastGoodDraftRef.current);
      setError(true);
    }
  };

  const over = maxLength !== undefined && shown.length > maxLength;

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

  // Keep a stable DOM structure whenever there's a chance of showing a note (required
  // field, or a maxLength is set) — otherwise flipping in/out the wrapper div would
  // remount the textarea and drop focus mid-typing (e.g. when going over the limit).
  if (!buffered) return textarea;
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
      {over && (
        <p
          className="mt-1 text-[13px] font-medium text-destructive"
          role="alert"
        >
          {maxLengthLabel} is too long — max {maxLength} characters (you have{" "}
          {shown.length}). Trim it to save.
        </p>
      )}
    </div>
  );
}
