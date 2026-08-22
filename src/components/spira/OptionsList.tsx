import { useEffect, useState } from "react";
import { Plus, CirclePlus } from "@/components/spira/icons";
import { Dots9, FrownFilled, SmileFilled } from "@/components/spira/icons";
import { FilteredEmptyNotice, NoticeCard } from "@/components/spira/Notice";
import { useSpira } from "@/lib/spira/store";
import type { Goal, Option } from "@/lib/spira/types";
import { FIELD_LIMITS } from "@/lib/spira/limits";
import { cn } from "@/lib/utils";
import { InlineText } from "./Inline";
import {
  ElementActionsMenu,
  REVEAL_ACTIVE,
  REVEAL_ON_ROW_HOVER,
  appendResourceToken,
  rowControlPlacement,
  useIsSingleLine,
} from "@/components/spira/inline-resources";

// Vertical gap between cards (Tailwind space-y-3 = 0.75rem = 12px). Added to each card's
// measured height to get the drag "step" — the distance the pointer travels to shuffle one slot.
const LIST_GAP_PX = 12;

// While dragging, a card is forced to its collapsed (≤3-line) view (see OptionRow's
// `forceCollapsed`) so a very long option doesn't need a full card-height of finger travel to
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

/**
 * The thumb lean an option carries — the smiley badge's three states, and the words the filter
 * uses for them. `null` matches everything.
 *
 * An option saved before the badge existed has an empty status rather than "none"; both mean the
 * same thing to the user, so "Didn't try" has to catch either.
 */
/** Re-exported from the store, which owns it now — the padlock has to be able to pin it. */
export type { OptionLeanFilter } from "@/components/shell/shell-store";
type OptionLeanFilter = "all" | "good_idea" | "didnt_work" | "none";

export const OPTION_LEAN_CHOICES = [
  { value: "all", label: "All" },
  {
    value: "good_idea",
    label: "Good idea",
    icon: <SmileFilled className="h-3.5 w-3.5" />,
  },
  {
    value: "didnt_work",
    label: "Bad idea",
    icon: <FrownFilled className="h-3.5 w-3.5" />,
  },
  { value: "none", label: "Didn't try" },
] as const;

export function matchesLean(opt: Option, filter: OptionLeanFilter): boolean {
  if (filter === "all") return true;
  return (opt.status || "none") === filter;
}

export function OptionsList({
  goal,
  reordering,
  onReorderingChange,
  leanFilter = "all",
}: {
  goal: Goal;
  /** Reorder mode is controlled by the parent so the Reorder/Save toggle can live in the
   *  Options section header (next to the title). */
  reordering: boolean;
  onReorderingChange: (v: boolean) => void;
  /** The lean filter, owned by the section header where its trigger sits. */
  leanFilter?: OptionLeanFilter;
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

  // Only ever in reorder mode with 2+ options, and never while the lean filter narrows the list:
  // a drop sends the card's index in the RENDERED list to the server as an absolute position, so
  // on a filtered list a wrong order would be saved with no sign anything went wrong.
  useEffect(() => {
    const narrowed = leanFilter !== "all";
    if ((goal.options.length < 2 || narrowed) && reordering) {
      onReorderingChange(false);
    }
  }, [goal.options.length, reordering, onReorderingChange, leanFilter]);

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
  // The drag maths always runs over the FULL list; the lean filter only narrows what is drawn,
  // and reorder mode is off (see the effect above) whenever the two could differ.
  const ordered = order
    .map((id) => optionsById.get(id))
    .filter((o): o is Option => Boolean(o))
    .filter((o) => matchesLean(o, leanFilter));

  return (
    <div className="space-y-3">
      {goal.options.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          What options could move you forward? Add a few, then choose one.
        </p>
      )}
      {goal.options.length > 0 && ordered.length === 0 && (
        <FilteredEmptyNotice>No options match that filter.</FilteredEmptyNotice>
      )}
      {/* The Reorder/Save toggle lives in the Options section header (see goals.$goalId.tsx). */}
      {reordering && (
        // The app's one message card, not a grey line with a glyph beside it (owner, 2026-08-21).
        // The words are Android's too, and they name the gesture rather than the goal: what people
        // got wrong was trying to drag the card body, so the sentence has to say where to grab.
        <NoticeCard kind="info" onDismiss={null}>
          Drag a card by the grip on its left. Swiping still scrolls the page.
        </NoticeCard>
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
                placeholder="Add an option…"
                className="flex-1 min-w-0 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
              />
              {draft && (
                <button
                  onClick={add}
                  disabled={draftOverBy > 0}
                  aria-label="Add"
                  className="ml-2 mr-1 shrink-0 rounded-full text-primary transition-colors hover:text-primary/80 disabled:opacity-40"
                >
                  <CirclePlus className="h-5 w-5" />
                </button>
              )}
            </div>
          </div>
          {draftOverBy > 0 && (
            <p
              className="mt-1 text-[13px] font-medium text-destructive"
              role="alert"
            >
              Option is too long — max {FIELD_LIMITS.optionText} characters (you
              have {draftOverBy}). Trim it to add.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * A single option row. The rating smiley is floated top-right so line 1 sits beside it and
 * lines 2+ wrap underneath (no reserved empty column). A option longer than 3 lines collapses
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
  const { ref: textRef, singleLine } = useIsSingleLine<HTMLDivElement>();
  // True while the option text itself holds focus — what reveals the ⋯ menu (see below).
  const [editing, setEditing] = useState(false);
  // Rating — one button that cycles on tap: none (grey smile) → good_idea (Guava smile) →
  // didnt_work (Kale frown) → none. It sits on the card's top-right EDGE as a circle badge; the
  // ⋮ actions menu lives inside the card instead. In reorder mode it's inert
  // (pointer-events-none) so a tap on it starts the card drag instead.
  const smiley = (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onCycleStatus();
      }}
      aria-label="Rate option"
      aria-pressed={opt.status === "good_idea" || opt.status === "didnt_work"}
      className={cn(
        "absolute -right-2 -top-2 z-10 grid h-7 w-7 place-items-center rounded-full border border-border bg-surface shadow-sm transition-colors",
        reordering && "pointer-events-none",
        opt.status === "good_idea"
          ? "text-[#F45D48]" // Guava (like)
          : opt.status === "didnt_work"
            ? "text-primary" // Kale (dislike)
            : "text-border-strong hover:text-muted-foreground",
      )}
    >
      {opt.status === "didnt_work" ? (
        <FrownFilled className="h-4 w-4" />
      ) : (
        <SmileFilled className="h-4 w-4" />
      )}
    </button>
  );

  // Actions — a horizontal ⋯ menu floating over the card (no reserved column, so the option gets
  // the full width): centred beside a one-line option, up in the corner once it wraps. It sits
  // as far right as it can (`right-5`) without sliding under the rating badge, whose left edge is
  // exactly there — overlapping the text is fine, overlapping the smiley is not.
  //
  // It appears on hover, and on tapping the STRATEGY TEXT (with the editing caret) — deliberately
  // not on focus anywhere in the row, or tapping the rating badge or the select radio would pop it
  // open too. Gone entirely while reordering.
  const actionsMenu = reordering ? null : (
    <ElementActionsMenu
      ariaLabel="Option actions"
      deleteLabel="Delete option"
      attachedTo={opt.text}
      onDelete={onRemove}
      onAttach={(resourceId) => {
        const next = appendResourceToken(
          opt.text,
          resourceId,
          FIELD_LIMITS.optionText,
        );
        if (next) onEditText(next);
      }}
      className={cn(
        editing ? REVEAL_ACTIVE : REVEAL_ON_ROW_HOVER,
        "absolute right-5 z-10 grid h-7 w-7 place-items-center rounded-md bg-surface/90 p-0",
        rowControlPlacement(singleLine),
      )}
    />
  );

  return (
    <li
      style={{
        ...(isDragging
          ? {
              transform: `translateY(${dragOffset}px)`,
              zIndex: 10,
              position: "relative",
            }
          : {}),
      }}
      className={cn(
        "group relative flex items-stretch rounded-md border transition-colors",
        reordering && "select-none",
        isDragging
          ? "border-primary shadow-lg"
          : opt.selected
            ? "border-primary"
            : "border-border hover:border-primary/50",
      )}
    >
      {/* Rating badge on the card's top-right edge. */}
      {smiley}

      {/*
        Left slot — the active radio, and **the drag grip in its place while reordering** (owner,
        2026-08-21; Android has done this since 2026-08-18).

        The card used to be the drag target itself: `onPointerDown` on the whole `<li>` plus
        `touch-action: none` across it. On a touch screen that made the options list very nearly
        unscrollable in reorder mode — a swipe meant to move the page picked a card up instead,
        because the card claimed the gesture on the first pixel and the page never got a look in.

        A scroll is a swipe and a drag is a press on a grip, so they now have separate targets: the
        grip alone starts a drag and carries `touch-action: none`, and a swipe anywhere else on the
        card scrolls the page exactly as it does outside reorder mode.

        Same cell, same 48px width, so nothing on the card moves when the mode changes — and the
        cell keeps its teal wash for the active option, so which one is active stays readable while
        the list is being rearranged.
      */}
      {reordering ? (
        <div
          onPointerDown={onStartDrag}
          style={{ touchAction: "none" }}
          role="button"
          aria-label="Drag to reorder"
          className={cn(
            "w-12 shrink-0 flex items-center justify-center border-r rounded-l-md transition-colors",
            isDragging ? "cursor-grabbing" : "cursor-grab",
            opt.selected
              ? "bg-[oklch(0.95_0.032_180)] border-primary"
              : "bg-surface border-border hover:bg-secondary/50",
          )}
        >
          <Dots9
            className={cn(
              "h-[18px] w-[18px]",
              opt.selected ? "text-primary" : "text-border-strong",
            )}
          />
        </div>
      ) : (
        <button
          onClick={onToggleSelect}
          className={cn(
            "w-12 shrink-0 flex items-center justify-center border-r transition-colors rounded-l-md",
            opt.selected
              ? // The option slot keeps its original tint (pre-palette-pass) at the owner's request;
                // `--primary-soft` is now Kale-200 and is used by targets/tasks instead.
                "bg-[oklch(0.95_0.032_180)] border-primary"
              : "bg-surface border-border hover:bg-secondary/50",
          )}
          aria-label={opt.selected ? "Deselect option" : "Select option"}
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
      )}

      {/* Right section — the option text gets the full width; the ⋮ menu floats over its
          top-right corner only while the card is hovered or focused. */}
      <div className="relative min-h-[48px] min-w-0 flex-1 rounded-r-md bg-surface py-3 pr-3 pl-4">
        {actionsMenu}
        {/* onFocus/onBlur here are focusin/focusout — they fire for the inline editor inside. */}
        <div
          ref={textRef}
          onFocus={() => setEditing(true)}
          onBlur={() => setEditing(false)}
        >
          <InlineText
            value={opt.text}
            onChange={onEditText}
            maxLength={FIELD_LIMITS.optionText}
            maxLengthLabel="Option"
            clampLines={3}
            forceCollapsed={isDragging || reordering}
            readOnly={reordering}
            className="text-base font-medium leading-relaxed"
            ariaLabel="Edit option"
          />
        </div>
      </div>
    </li>
  );
}
