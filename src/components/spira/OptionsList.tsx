import { useEffect, useState } from "react";
import { Plus, Smile, Frown, X, Info } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import type { Goal, Option } from "@/lib/spira/types";
import { FIELD_LIMITS } from "@/lib/spira/limits";
import { cn } from "@/lib/utils";
import { InlineText } from "./Inline";

// Vertical gap between cards (Tailwind space-y-3 = 0.75rem = 12px). Added to each card's
// measured height to get the drag "step" — the distance the pointer travels to shuffle one slot.
const LIST_GAP_PX = 12;

// While dragging, a card is forced to its collapsed (≤3-line) view (see OptionRow's
// `forceCollapsed`) so a very long strategy doesn't need a full card-height of finger travel to
// move one slot — impossible on mobile when the card was taller than the screen. The reorder step
// is capped at roughly the collapsed card height + gap so small finger moves reorder one slot.
const DRAG_STEP_MAX_PX = 116;

export function moveInArray<T>(arr: T[], from: number, to: number): T[] {
  const next = [...arr];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/**
 * The slot a dragged card should occupy given how far the pointer has travelled.
 * `totalDeltaPx` is the pointer's Y displacement from where the drag started (any
 * page scroll during the drag is folded in by the caller); `stepPx` is the travel
 * that shuffles one slot. Rounds to the nearest slot and clamps to the list bounds
 * so a downward drag moves the card down and an upward drag moves it up. Extracted
 * (and exported) so this math — the source of the "down-drag froze" regression —
 * is unit-testable without real layout.
 */
export function reorderTargetIndex(
  fromIndex: number,
  totalDeltaPx: number,
  stepPx: number,
  length: number,
): number {
  const slots = Math.round(totalDeltaPx / stepPx);
  return Math.max(0, Math.min(length - 1, fromIndex + slots));
}

export function OptionsList({
  goal,
  reordering,
  onReorderingChange,
}: {
  goal: Goal;
  /** Reorder mode is controlled by the parent so the Reorder/Save toggle can live in the
   *  Options section header (next to the title). */
  reordering: boolean;
  onReorderingChange: (v: boolean) => void;
}) {
  const {
    addOption,
    updateOption,
    selectOption,
    setOptionStatus,
    removeOption,
    reorderOptions,
  } = useSpira();
  const [draft, setDraft] = useState("");

  // Local reorder state, mirroring the Android long-press-drag reorder
  // (docs/drag-and-drop-options.md): `order` holds the option ids in their current
  // (possibly mid-drag) sequence; the reorder is committed to the server only on release.
  const [order, setOrder] = useState<string[]>(() =>
    goal.options.map((o) => o.id),
  );
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState(0);

  const sourceIds = goal.options.map((o) => o.id).join(",");
  useEffect(() => {
    if (!draggingId) setOrder(goal.options.map((o) => o.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceIds, draggingId]);

  // Only ever in reorder mode with 2+ options.
  useEffect(() => {
    if (goal.options.length < 2 && reordering) onReorderingChange(false);
  }, [goal.options.length, reordering, onReorderingChange]);

  const draftOverBy =
    draft.trim().length > FIELD_LIMITS.optionText ? draft.trim().length : 0;

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    if (t.length > FIELD_LIMITS.optionText) return; // too long — blocked, message shown
    addOption(goal.id, t); // a URL in the text is auto-linked on display
    setDraft("");
  };

  const handleOptionClick = (optId: string, isSelected: boolean) => {
    if (isSelected) {
      updateOption(goal.id, optId, { selected: false });
    } else {
      selectOption(goal.id, optId);
    }
  };

  // Whole-card pointer-drag reorder (reorder mode only). Move/up listeners live on `window` (not the
  // card) so a mid-drag DOM reorder can't drop pointer capture — the old "drag down freezes" bug.
  // Auto-scrolls the page near the viewport edges so a long list / many cards can be traversed; the
  // accumulated scroll is folded into the drag math so reordering continues as the page scrolls.
  const startDrag = (e: React.PointerEvent, id: string) => {
    const li = e.currentTarget as HTMLElement; // the card itself
    e.preventDefault();
    // Cap the step so a tall card (collapsed during drag) reorders with small finger moves.
    const step = Math.min(
      li.getBoundingClientRect().height + LIST_GAP_PX,
      DRAG_STEP_MAX_PX,
    );
    const fromIndex = order.indexOf(id);
    if (fromIndex === -1) return;

    const state = {
      startY: e.clientY,
      step,
      fromIndex,
      baseOrder: [...order],
      toIndex: fromIndex,
      lastClientY: e.clientY,
      scrolledBy: 0,
    };
    setDraggingId(id);
    setDragOffset(0);

    const applyPosition = () => {
      const total = state.lastClientY - state.startY + state.scrolledBy;
      const toIndex = reorderTargetIndex(
        state.fromIndex,
        total,
        state.step,
        state.baseOrder.length,
      );
      if (toIndex !== state.toIndex) {
        state.toIndex = toIndex;
        setOrder(moveInArray(state.baseOrder, state.fromIndex, toIndex));
      }
      setDragOffset(total - (toIndex - state.fromIndex) * state.step);
    };

    // Auto-scroll loop: while the pointer sits within EDGE px of the top/bottom, scroll the window
    // toward it (speed ramps with proximity) and re-run the slot computation with the new scroll.
    const EDGE = 64;
    const MAX_SPEED = 16;
    let rafId = 0;
    const tick = () => {
      const y = state.lastClientY;
      const h = window.innerHeight;
      let dy = 0;
      if (y < EDGE) dy = -Math.ceil(((EDGE - y) / EDGE) * MAX_SPEED);
      else if (y > h - EDGE)
        dy = Math.ceil(((y - (h - EDGE)) / EDGE) * MAX_SPEED);
      if (dy !== 0) {
        const before = window.scrollY;
        window.scrollBy(0, dy);
        const applied = window.scrollY - before;
        if (applied !== 0) {
          state.scrolledBy += applied;
          applyPosition();
        }
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);

    const onMove = (ev: PointerEvent) => {
      state.lastClientY = ev.clientY;
      applyPosition();
    };
    const onUp = () => {
      cancelAnimationFrame(rafId);
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onUp);
      setDraggingId(null);
      setDragOffset(0);
      if (state.toIndex !== state.fromIndex) {
        reorderOptions(goal.id, state.fromIndex, state.toIndex);
      }
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  };

  const optionsById = new Map(goal.options.map((o) => [o.id, o]));
  const ordered = order
    .map((id) => optionsById.get(id))
    .filter((o): o is Option => Boolean(o));

  return (
    <div className="space-y-3">
      {goal.options.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          What strategies could move you forward? Add a few, then choose one.
        </p>
      )}
      {/* The Reorder/Save toggle lives in the Options section header (see goals.$goalId.tsx). */}
      {reordering && (
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Info className="h-3.5 w-3.5 shrink-0" />
          Drag cards to reorder.
        </p>
      )}

      <ul className="space-y-3">
        {ordered.map((opt) => (
          <OptionRow
            key={opt.id}
            opt={opt}
            reordering={reordering}
            isDragging={opt.id === draggingId}
            dragOffset={dragOffset}
            onRemove={() => removeOption(goal.id, opt.id)}
            onToggleSelect={() => handleOptionClick(opt.id, opt.selected)}
            onEditText={(text) => updateOption(goal.id, opt.id, { text })}
            onCycleStatus={() =>
              setOptionStatus(
                goal.id,
                opt.id,
                opt.status === "good_idea"
                  ? "didnt_work"
                  : opt.status === "didnt_work"
                    ? "none"
                    : "good_idea",
              )
            }
            onStartDrag={(e) => startDrag(e, opt.id)}
          />
        ))}
      </ul>

      {/* Creation Field — hidden while reordering. */}
      {!reordering && (
        <div className="mt-4">
          <div className="flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary">
            <div className="w-12 shrink-0 flex items-center justify-center border-r border-border bg-secondary/30">
              <Plus className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="flex-1 min-w-0 flex items-center px-4 py-1 relative">
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && add()}
                placeholder="Add a strategy…"
                className="flex-1 min-w-0 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
              />
              {draft && (
                <button
                  onClick={add}
                  disabled={draftOverBy > 0}
                  className="ml-2 mr-1 shrink-0 rounded-md bg-primary/10 px-2 py-1 text-sm font-medium text-primary hover:bg-primary/20 disabled:opacity-40"
                >
                  Add
                </button>
              )}
            </div>
          </div>
          {draftOverBy > 0 && (
            <p
              className="mt-1 text-[13px] font-medium text-destructive"
              role="alert"
            >
              Strategy is too long — max {FIELD_LIMITS.optionText} characters
              (you have {draftOverBy}). Trim it to add.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * A single strategy row. The rating smiley is floated top-right so line 1 sits beside it and
 * lines 2+ wrap underneath (no reserved empty column). A strategy longer than 3 lines collapses
 * to 3 lines with a "Show more"/"Show less" toggle (InlineText `clampLines`). There is no drag
 * handle: reordering happens only in **reorder mode**, where the whole card is the drag target
 * (grab cursor) and every per-card action (edit, rating, delete, select, Show more) is disabled.
 */
function OptionRow({
  opt,
  reordering,
  isDragging,
  dragOffset,
  onRemove,
  onToggleSelect,
  onEditText,
  onCycleStatus,
  onStartDrag,
}: {
  opt: Option;
  reordering: boolean;
  isDragging: boolean;
  dragOffset: number;
  onRemove: () => void;
  onToggleSelect: () => void;
  onEditText: (text: string) => void;
  onCycleStatus: () => void;
  onStartDrag: (e: React.PointerEvent) => void;
}) {
  // Rating — one button that cycles on tap: none (grey smile) → good_idea (Guava smile) →
  // didnt_work (Kale frown) → none. Floated top-right inside the text (see InlineText). In
  // reorder mode it's inert (pointer-events-none) so a tap on it starts the card drag instead.
  const smiley = (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onCycleStatus();
      }}
      aria-label="Rate strategy"
      aria-pressed={opt.status === "good_idea" || opt.status === "didnt_work"}
      className={cn(
        "grid h-8 w-8 place-items-center rounded-md transition-colors",
        reordering && "pointer-events-none",
        opt.status === "good_idea"
          ? "text-[#F45D48]" // Guava (like)
          : opt.status === "didnt_work"
            ? "text-primary" // Kale (dislike)
            : "text-border-strong hover:text-muted-foreground",
      )}
    >
      {opt.status === "didnt_work" ? (
        <Frown className="h-[18px] w-[18px]" />
      ) : (
        <Smile className="h-[18px] w-[18px]" />
      )}
    </button>
  );

  return (
    <li
      onPointerDown={reordering ? onStartDrag : undefined}
      style={{
        ...(isDragging
          ? {
              transform: `translateY(${dragOffset}px)`,
              zIndex: 10,
              position: "relative",
            }
          : {}),
        ...(reordering ? { touchAction: "none" } : {}),
      }}
      className={cn(
        "group relative flex items-stretch rounded-md border transition-colors",
        reordering && "select-none",
        reordering && (isDragging ? "cursor-grabbing" : "cursor-grab"),
        isDragging
          ? "border-primary shadow-lg"
          : opt.selected
            ? "border-primary"
            : "border-border hover:border-primary/50",
      )}
    >
      {/* Delete — a circle-✕ badge on the top-right corner; hidden while reordering. */}
      {!reordering && (
        <button
          onClick={onRemove}
          aria-label="Remove strategy"
          className="absolute -right-2 -top-2 z-10 grid h-6 w-6 place-items-center rounded-full border border-border bg-surface text-muted-foreground shadow-sm transition-colors hover:text-destructive"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}

      {/* Left slot — the active radio (single-select); inert while reordering. */}
      <button
        onClick={reordering ? undefined : onToggleSelect}
        className={cn(
          "w-12 shrink-0 flex items-center justify-center border-r transition-colors rounded-l-md",
          reordering && "pointer-events-none",
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

      {/* Right section — the strategy text with the rating smiley floated top-right, so line 1
          sits beside it and lines 2+ wrap underneath (no reserved empty column). */}
      <div className="min-h-[48px] min-w-0 flex-1 rounded-r-md bg-surface py-3 pr-3 pl-4">
        <InlineText
          value={opt.text}
          onChange={onEditText}
          maxLength={FIELD_LIMITS.optionText}
          maxLengthLabel="Strategy"
          clampLines={3}
          forceCollapsed={isDragging || reordering}
          readOnly={reordering}
          floatTopRight={smiley}
          className="text-base font-medium leading-relaxed"
          ariaLabel="Edit strategy"
        />
      </div>
    </li>
  );
}
