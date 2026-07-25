import { useEffect, useState } from "react";
import { Plus, Trash2, GripVertical } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import type { Goal, Option } from "@/lib/spira/types";
import { cn } from "@/lib/utils";
import { InlineText } from "./Inline";

// Vertical gap between cards (Tailwind space-y-3 = 0.75rem = 12px). Added to each card's
// measured height to get the drag "step" — the distance the pointer travels to shuffle one slot.
const LIST_GAP_PX = 12;

function moveInArray<T>(arr: T[], from: number, to: number): T[] {
  const next = [...arr];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

export function OptionsList({ goal }: { goal: Goal }) {
  const {
    addOption,
    updateOption,
    selectOption,
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

  const add = () => {
    const t = draft.trim();
    if (!t) return;
    addOption(goal.id, t);
    setDraft("");
  };

  const handleOptionClick = (optId: string, isSelected: boolean) => {
    if (isSelected) {
      updateOption(goal.id, optId, { selected: false });
    } else {
      selectOption(goal.id, optId);
    }
  };

  // Pointer-drag reorder. Move/up listeners live on `window` (not the handle) so a mid-drag
  // DOM reorder can't drop pointer capture — that was the "drag down freezes" bug.
  const startDrag = (e: React.PointerEvent, id: string) => {
    const li = (e.currentTarget as HTMLElement).closest("li");
    if (!li) return;
    e.preventDefault();
    const step = li.getBoundingClientRect().height + LIST_GAP_PX;
    const fromIndex = order.indexOf(id);
    if (fromIndex === -1) return;

    const state = {
      startY: e.clientY,
      step,
      fromIndex,
      baseOrder: [...order],
      toIndex: fromIndex,
    };
    setDraggingId(id);
    setDragOffset(0);

    const onMove = (ev: PointerEvent) => {
      const total = ev.clientY - state.startY;
      const slots = Math.round(total / state.step);
      const toIndex = Math.max(
        0,
        Math.min(state.baseOrder.length - 1, state.fromIndex + slots),
      );
      if (toIndex !== state.toIndex) {
        state.toIndex = toIndex;
        setOrder(moveInArray(state.baseOrder, state.fromIndex, toIndex));
      }
      setDragOffset(total - (toIndex - state.fromIndex) * state.step);
    };
    const onUp = () => {
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

  // Keyboard fallback for reorder (drag is pointer-only): focus a handle and use ↑/↓.
  const nudge = (id: string, delta: -1 | 1) => {
    const from = goal.options.findIndex((o) => o.id === id);
    const to = from + delta;
    if (from === -1 || to < 0 || to >= goal.options.length) return;
    reorderOptions(goal.id, from, to);
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
      <ul className="space-y-3">
        {ordered.map((opt) => {
          const isDragging = opt.id === draggingId;
          return (
            <li
              key={opt.id}
              style={
                isDragging
                  ? {
                      transform: `translateY(${dragOffset}px)`,
                      zIndex: 10,
                      position: "relative",
                    }
                  : undefined
              }
              className={cn(
                "group flex items-stretch rounded-md border transition-colors",
                isDragging
                  ? "border-primary shadow-lg select-none"
                  : opt.selected
                    ? "border-primary"
                    : "border-border hover:border-primary/50",
              )}
            >
              {/* Left slot — the active radio (single-select across the goal). */}
              <button
                onClick={() => handleOptionClick(opt.id, opt.selected)}
                className={cn(
                  "w-12 shrink-0 flex items-center justify-center border-r transition-colors rounded-l-md",
                  opt.selected
                    ? "bg-primary-soft border-primary"
                    : "bg-surface border-border hover:bg-secondary/50",
                )}
                aria-label={
                  opt.selected ? "Deselect strategy" : "Select strategy"
                }
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

              {/* Right section — text, delete, then the drag handle at the edge. */}
              <div className="flex-1 flex items-center bg-surface px-4 py-3 min-h-[48px] rounded-r-md">
                <InlineText
                  value={opt.text}
                  onChange={(text) => updateOption(goal.id, opt.id, { text })}
                  className="flex-1 min-w-0 text-base font-medium leading-relaxed"
                  ariaLabel="Edit strategy"
                />

                {/* Delete — grey (same as an unselected radio), red on hover. */}
                <button
                  onClick={() => removeOption(goal.id, opt.id)}
                  className="ml-2 grid h-8 w-8 shrink-0 place-items-center rounded-md text-border-strong hover:text-destructive transition-colors"
                  aria-label="Remove"
                >
                  <Trash2 className="h-4 w-4" />
                </button>

                {/* Drag handle at the edge — press and drag to reorder (↑/↓ for keyboard) */}
                <button
                  onPointerDown={(e) => startDrag(e, opt.id)}
                  onKeyDown={(e) => {
                    if (e.key === "ArrowUp") {
                      e.preventDefault();
                      nudge(opt.id, -1);
                    } else if (e.key === "ArrowDown") {
                      e.preventDefault();
                      nudge(opt.id, 1);
                    }
                  }}
                  style={{ touchAction: "none" }}
                  className={cn(
                    "-mr-1 ml-1 flex h-8 w-7 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-secondary/50 hover:text-foreground",
                    isDragging ? "cursor-grabbing" : "cursor-grab",
                  )}
                  aria-label="Drag to reorder strategy"
                >
                  <GripVertical className="h-4 w-4" />
                </button>
              </div>
            </li>
          );
        })}
      </ul>

      {/* Creation Field */}
      <div className="flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary mt-4">
        <div className="w-12 shrink-0 flex items-center justify-center border-r border-border bg-secondary/30">
          <Plus className="h-4 w-4 text-muted-foreground" />
        </div>
        <div className="flex-1 flex items-center px-4 py-1 relative">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && add()}
            placeholder="Add a strategy…"
            className="flex-1 bg-transparent text-base outline-none min-h-[40px] placeholder:text-muted-foreground/75"
          />
          {draft && (
            <button
              onClick={add}
              className="ml-2 rounded-md bg-primary/10 px-2 py-1 text-sm font-semibold text-primary hover:bg-primary/20"
            >
              Add
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
