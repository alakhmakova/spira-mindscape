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
  CircleCheck,
  CirclePlus,
  CircleX,
  Info,
  TriangleAlert,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  splitInline,
  isSafeHttpUrl,
  hasResourceTag,
  resourceToken,
  RESOURCE_TOKEN_BUDGET,
} from "@/lib/spira/links";
import {
  ElementActionsMenu,
  REVEAL_ON_ROW_ACTIVITY,
  ResourceLink,
  rowControlPlacement,
  useIsSingleLine,
  appendResourceToken,
  namesToTokens,
  tokensToNames,
  useInlineResources,
} from "@/components/spira/inline-resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { titleFromUrl } from "@/lib/spira/resources";

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
              aria-label="Add"
              className={cn(
                "ml-2 mr-1 shrink-0 rounded-full transition-colors disabled:opacity-40",
                tone === "warning"
                  ? "text-destructive hover:text-destructive/80"
                  : "text-primary hover:text-primary/80",
              )}
            >
              <CirclePlus className="h-5 w-5" />
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
          <InlineListRow
            key={it.id}
            item={it}
            marker={marker}
            tone={tone}
            variant={variant}
            maxLength={maxLength}
            maxLengthLabel={maxLengthLabel}
            onUpdate={onUpdate}
            onRemove={onRemove}
          />
        ))}
      </ul>
    </div>
  );
}

/** One reality item: text on the left, a ⋮ menu floating on the right (see `InlineList`). */
function InlineListRow({
  item,
  marker,
  tone,
  variant,
  maxLength,
  maxLengthLabel,
  onUpdate,
  onRemove,
}: {
  item: Item;
  marker: "dot" | "check" | "warn";
  tone: "default" | "warning";
  variant: Variant;
  maxLength?: number;
  maxLengthLabel?: string;
  onUpdate: (id: string, text: string) => void;
  onRemove: (id: string) => void;
}) {
  const onPrimary = variant === "onPrimary";
  const { ref, singleLine } = useIsSingleLine<HTMLDivElement>();

  return (
    <li
      className={cn(
        "group relative flex items-start gap-3 rounded-md px-2 py-2 transition-colors",
        onPrimary ? "hover:bg-primary-foreground/10" : "hover:bg-white/60",
      )}
    >
      <Marker kind={marker} tone={tone} variant={variant} />
      <div ref={ref} className="min-w-0 flex-1">
        <InlineText
          value={item.text}
          onChange={(next) => next.trim() && onUpdate(item.id, next.trim())}
          maxLength={maxLength}
          maxLengthLabel={maxLengthLabel}
          className={cn(
            "text-left text-[15px] leading-relaxed",
            onPrimary && "text-primary-foreground",
          )}
          ariaLabel="Edit item"
        />
      </div>
      {/* Floats over the row instead of taking a column of its own — centred beside a one-line
          item, up in the corner once the text wraps — and only shows once the row is hovered or
          focused (tabbing in reveals it alongside the editing caret). */}
      <ElementActionsMenu
        ariaLabel="Item actions"
        attachedTo={item.text}
        onDelete={() => onRemove(item.id)}
        onAttach={(resourceId) => {
          const next = appendResourceToken(item.text, resourceId, maxLength);
          if (next) onUpdate(item.id, next);
        }}
        className={cn(
          REVEAL_ON_ROW_ACTIVITY,
          "absolute right-1",
          rowControlPlacement(singleLine),
          onPrimary &&
            "text-primary-foreground/70 hover:text-primary-foreground",
        )}
      />
    </li>
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
 *
 * An attached resource is stored as a `{{res:id}}` token in the same plain text and rendered as a
 * link (see `ResourceLink`). When a value with a URL is over the field's limit, the field
 * offers to turn that URL into a link resource so only the short chip stays — the durable fix for
 * "long URL → over the limit → sync banner"
 * (specs/2026-07-28-inline-resource-attachments/requirements.md).
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
  // Shown when a pure-URL paste would push the field over its maxLength and the URL can't be
  // turned into a resource — the paste is refused (not truncated, not committed) so an
  // over-length URL never reaches the store.
  const [pasteError, setPasteError] = useState<string | null>(null);
  // The over-limit URL awaiting the user's "turn it into a link resource?" answer, together with
  // the full text it came from (the URL is swapped for the resource's token on confirm).
  const [convert, setConvert] = useState<{ url: string; text: string } | null>(
    null,
  );
  // A URL the user already declined to convert — don't re-ask on every blur.
  const declinedUrlRef = useRef<string | null>(null);
  const resourcesCtx = useInlineResources();
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

  // While editing, a resource tag reads `{{res:Job ad}}` — the resource's NAME, not the id it is
  // stored under. `toStored` maps it back on every commit, so what reaches the store is always the
  // id form (and a tag naming something unknown degrades to plain text rather than dangling).
  const resources = resourcesCtx?.resources ?? [];
  const toEditable = (text: string) => tokensToNames(text, resources);
  const toStored = (text: string) => namesToTokens(text, resources);

  const enterEdit = () => {
    if (readOnly) return; // display-only (e.g. reorder mode): never enter edit
    setDraft(toEditable(value));
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

  /**
   * The URL worth turning into a link resource for an over-limit `text`: the longest one whose
   * replacement by a resource token would bring the value back under the limit. Null when there
   * is none (or nowhere to create resources), in which case the plain "too long" message stands.
   */
  const canConvert = (url: string, text: string) =>
    maxLength !== undefined &&
    !!resourcesCtx &&
    url !== declinedUrlRef.current &&
    isSafeHttpUrl(url) &&
    text.length - url.length + RESOURCE_TOKEN_BUDGET <= maxLength;

  const convertibleUrl = (text: string): string | null => {
    const urls = splitInline(text)
      .filter((seg) => seg.type === "url")
      .map((seg) => seg.url)
      .sort((a, b) => b.length - a.length);
    return urls.find((url) => canConvert(url, text)) ?? null;
  };

  /** Commit a value that is ALREADY in the stored (id) form — see `commit` for the draft path. */
  const commitStored = (stored: string) => {
    const trimmed = stored.trim();
    if (!trimmed) {
      // Never save empty — revert to the last good value; flag required fields.
      setEditing(false);
      setDraft(toEditable(value));
      if (required) setError(true);
      return;
    }
    if (maxLength !== undefined && trimmed.length > maxLength) {
      // Too long — do NOT save or truncate. Stay in edit mode so the user can trim
      // (the live "too long" message below explains why); Escape discards.
      // If a URL is what makes it too long, offer to move it out into a link resource.
      const url = convertibleUrl(trimmed);
      if (url) setConvert({ url, text: trimmed });
      return;
    }
    setEditing(false);
    lastGoodValueRef.current = trimmed;
    setError(false);
    if (trimmed !== value) onChange(trimmed);
  };

  // The draft path: map the editable (name) tags back to ids first, so the length check, the
  // store, and the server all see the same string.
  const commit = (raw: string) => commitStored(toStored(raw));

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
      const storedNext = toStored(next);
      // The <textarea maxLength> attribute doesn't cap this programmatic path, so guard it here:
      // refuse the paste rather than truncate the URL or push an over-length value to the server.
      // A link that only fails because of its length can instead become a link resource — ask.
      if (maxLength !== undefined && storedNext.length > maxLength) {
        if (canConvert(pasted, storedNext)) {
          setConvert({ url: pasted, text: next });
          return;
        }
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

  // Turn the pending URL into a `link` resource and keep only its token in the text. The token
  // MUST carry the id the server assigned (handed back by `createLinkResource`) — the optimistic
  // temp id is swapped out moments later and would leave a dangling reference.
  const runConvert = async () => {
    if (!convert || !resourcesCtx) return;
    const pending = convert;
    setConvert(null);
    const resourceId = await resourcesCtx.createLinkResource(pending.url);
    if (!resourceId) return; // the store surfaces the failure; the text is left untouched
    // Map the draft's existing name tags to ids FIRST, then splice in the new token: the resource
    // was created a moment ago, so this render's `resources` doesn't know it yet and mapping the
    // fresh token would drop it to plain text.
    const next = toStored(pending.text).replace(
      pending.url,
      resourceToken(resourceId),
    );
    setPasteError(null);
    setDraft(next);
    commitStored(next);
  };

  const convertDialog = (
    <ConfirmDialog
      open={!!convert}
      onOpenChange={(open) => {
        if (!open) {
          declinedUrlRef.current = convert?.url ?? null;
          setConvert(null);
        }
      }}
      title="That link is too long to save here"
      description={`Keep it as a resource instead? The link is saved under "${
        convert ? titleFromUrl(convert.url) : ""
      }" in this goal's Resources, and the text keeps a short chip you can tap to open it.`}
      confirmLabel="Yes, save as a resource"
      cancelLabel="No, I'll shorten it"
      tone="primary"
      onConfirm={() => void runConvert()}
    />
  );

  // The limit applies to what gets stored, so measure the id form, not the longer name form.
  const storedDraftLength = toStored(draft).trim().length;

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
              setDraft(toEditable(value));
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
          storedDraftLength > maxLength && (
            <span className="mt-1 text-[13px] font-medium text-destructive no-underline not-italic">
              {maxLengthLabel} is too long — max {maxLength} characters (you
              have {storedDraftLength}). Trim it to save.
            </span>
          )
        )}
        {/* Editing is plain text, so an attached resource shows as a raw tag — say so, or a
            `{{res:…}}` in the middle of a sentence looks like a bug. */}
        {hasResourceTag(draft) && (
          <span className="mt-1 flex items-start gap-1.5 text-[13px] text-muted-foreground no-underline not-italic">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              An attached resource shows here as a tag with its name — delete
              the whole tag to detach it.
            </span>
          </span>
        )}
        {convertDialog}
      </span>
    );
  }

  const isEmpty = value.trim() === "";
  const segments = splitInline(value);
  const collapsed = clampLines !== undefined && (forceCollapsed || !expanded);
  // A `max-height` of N line-heights (`lh`) clamps to N lines; `-webkit-line-clamp` would fight
  // the inline links and the absolutely-positioned row menu that float over this text.
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
          {isEmpty ? (
            <span className="text-muted-foreground/75">{placeholder}</span>
          ) : (
            <>
              {segments.map((seg, i) =>
                seg.type === "text" ? (
                  <span key={i}>{seg.value}</span>
                ) : seg.type === "url" ? (
                  <UrlLink key={i} url={seg.url} />
                ) : (
                  <ResourceLink key={i} id={seg.id} />
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
      {convertDialog}
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
