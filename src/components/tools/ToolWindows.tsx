import { useEffect, useState } from "react";
import { Minus, X, Trash2, GripVertical, Square } from "lucide-react";
import { useIsMobile } from "@/hooks/use-mobile";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { toast } from "sonner";
import { ToolRenderer } from "./ToolRenderer";
import {
  useTools,
  useToolWindows,
  TOOL_WINDOW_LIMITS,
  type ToolWindowState,
} from "./tools-store";
import { cn } from "@/lib/utils";

// Resize grips: left/right/bottom edges + bottom corners, each with a cursor
// and an absolute position. `dir` letters (s/e/w) say which edges this grip
// moves. There are deliberately NO top grips: the top is the draggable title
// bar, and a top grip would overlap the header buttons (causing a stray resize
// "twitch" when you click them). `w`/`e` (the side grips) are listed first so
// they can also be shown alone when the window is collapsed (width-only).
const SIDE_GRIPS: { dir: string; className: string }[] = [
  { dir: "w", className: "left-0 inset-y-2 w-1.5 cursor-w-resize" },
  { dir: "e", className: "right-0 inset-y-2 w-1.5 cursor-e-resize" },
];
const RESIZE_GRIPS: { dir: string; className: string }[] = [
  ...SIDE_GRIPS,
  { dir: "s", className: "bottom-0 inset-x-2 h-1.5 cursor-s-resize" },
  { dir: "sw", className: "bottom-0 left-0 h-3 w-3 cursor-sw-resize" },
  { dir: "se", className: "bottom-0 right-0 h-3 w-3 cursor-se-resize" },
];

/**
 * Renders every open Personal Tool as a floating, draggable, resizable,
 * minimizable window. Mounted once at the app shell so windows coexist with the
 * AI chat (no backdrop — never modal): the user can edit a tracker and watch the
 * conversation side by side. On mobile, windows dock to the bottom as sheets.
 */
export function ToolWindows() {
  const windows = useToolWindows((s) => s.windows);
  const tools = useTools((s) => s.tools);
  const ensureLoaded = useTools((s) => s.ensureLoaded);

  // Load the user's tools on mount so an open window can resolve its tool name.
  useEffect(() => {
    ensureLoaded();
  }, [ensureLoaded]);

  if (windows.length === 0) return null;

  // Stacking order by rank, NOT the raw (ever-growing) z counter: this keeps a
  // window's z-index in a fixed band ABOVE the chat (z-40) but BELOW popovers/
  // dropdowns/dialogs (z-50). Otherwise the date-picker popover (portaled at
  // z-50) ends up hidden behind a window whose z had climbed past 50.
  const order = [...windows].sort((a, b) => a.z - b.z);
  const rankById = new Map(order.map((w, i) => [w.id, i]));

  return (
    <>
      {windows.map((win) => {
        const tool = tools.find((t) => t.id === win.id);
        if (!tool) return null;
        const zIndex = 41 + Math.min(rankById.get(win.id) ?? 0, 8); // 41..49
        return (
          <ToolWindow
            key={win.id}
            win={win}
            toolName={tool.name}
            zIndex={zIndex}
          />
        );
      })}
    </>
  );
}

function ToolWindow({
  win,
  toolName,
  zIndex,
}: {
  win: ToolWindowState;
  toolName: string;
  zIndex: number;
}) {
  const isMobile = useIsMobile();
  const { close, focus, toggleMinimize, setRect } = useToolWindows();
  const tools = useTools((s) => s.tools);
  const removeTool = useTools((s) => s.removeTool);
  const recordsVersion = useTools((s) => s.recordsVersion[win.id] ?? 0);
  const tool = tools.find((t) => t.id === win.id);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (!tool) return null;

  // Drag the whole window by its title bar (desktop only).
  const onHeaderPointerDown = (e: React.PointerEvent) => {
    if (isMobile) return;
    if ((e.target as HTMLElement).closest("[data-no-drag]")) return;
    e.preventDefault(); // don't start a text selection while dragging
    document.body.style.userSelect = "none";
    focus(win.id);
    const startX = e.clientX;
    const startY = e.clientY;
    const ox = win.x;
    const oy = win.y;
    const move = (ev: PointerEvent) => {
      setRect(win.id, {
        x: ox + (ev.clientX - startX),
        y: oy + (ev.clientY - startY),
      });
    };
    const up = () => {
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  };

  // Resize from any edge or corner (desktop only). `dir` says which edges move;
  // edges that move the top/left also shift x/y. The opposite edge stays put
  // when the window hits its minimum size, so it never "jumps".
  const onResizeStart = (e: React.PointerEvent, dir: string) => {
    e.stopPropagation();
    e.preventDefault(); // don't start a text selection while resizing
    document.body.style.userSelect = "none";
    focus(win.id);
    const startX = e.clientX;
    const startY = e.clientY;
    const { x: ox, y: oy, w: ow, h: oh } = win;
    const { MIN_W, MIN_H } = TOOL_WINDOW_LIMITS;
    const move = (ev: PointerEvent) => {
      const dx = ev.clientX - startX;
      const dy = ev.clientY - startY;
      let x = ox;
      let y = oy;
      let w = ow;
      let h = oh;
      if (dir.includes("e")) w = ow + dx;
      if (dir.includes("s")) h = oh + dy;
      if (dir.includes("w")) {
        w = ow - dx;
        x = ox + dx;
      }
      if (dir.includes("n")) {
        h = oh - dy;
        y = oy + dy;
      }
      if (w < MIN_W) {
        if (dir.includes("w")) x = ox + ow - MIN_W;
        w = MIN_W;
      }
      if (h < MIN_H) {
        if (dir.includes("n")) y = oy + oh - MIN_H;
        h = MIN_H;
      }
      setRect(win.id, { x, y, w, h });
    };
    const up = () => {
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  };

  const onDelete = async () => {
    try {
      await removeTool(win.id); // also closes this window
      toast.success(`“${toolName}” deleted`);
    } catch {
      toast.error("Couldn't delete the tool.");
    }
  };

  const header = (
    <div
      onPointerDown={onHeaderPointerDown}
      onMouseDown={() => focus(win.id)}
      className={cn(
        "flex items-center gap-1.5 border-b border-border bg-surface-sunken/60 px-2.5 py-2",
        !isMobile && "cursor-grab active:cursor-grabbing",
      )}
    >
      {!isMobile && (
        <GripVertical className="h-3.5 w-3.5 shrink-0 text-muted-foreground/60" />
      )}
      <span className="min-w-0 flex-1 truncate text-sm font-semibold">
        {toolName}
      </span>
      <button
        data-no-drag
        type="button"
        aria-label="Delete tool"
        title="Delete tool"
        onClick={() => setConfirmDelete(true)}
        className="grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-destructive"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
      <button
        data-no-drag
        type="button"
        aria-label={win.minimized ? "Restore" : "Minimize"}
        title={win.minimized ? "Restore" : "Minimize"}
        onClick={() => toggleMinimize(win.id)}
        className="grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
      >
        {win.minimized ? (
          <Square className="h-3 w-3" />
        ) : (
          <Minus className="h-4 w-4" />
        )}
      </button>
      <button
        data-no-drag
        type="button"
        aria-label="Close"
        title="Close"
        onClick={() => close(win.id)}
        className="grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );

  const body = !win.minimized && (
    <div className="min-h-0 flex-1 overflow-y-auto p-3">
      {/* Remount on recordsVersion bump so AI-driven row changes show at once. */}
      <ToolRenderer key={recordsVersion} tool={tool} />
    </div>
  );

  // ── Mobile: dock to the bottom as a sheet (no free drag/resize) ───────────
  if (isMobile) {
    return (
      <>
        <div
          onPointerDown={() => focus(win.id)}
          style={{ zIndex }}
          className={cn(
            "fixed inset-x-0 bottom-0 flex flex-col rounded-t-xl border border-border bg-surface shadow-[0_-8px_30px_-12px_rgba(0,0,0,0.35)]",
            win.minimized ? "h-auto" : "h-[72vh]",
          )}
        >
          {header}
          {body}
        </div>
        <DeleteToolConfirm
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          name={toolName}
          onConfirm={onDelete}
        />
      </>
    );
  }

  // ── Desktop: a free-floating window ───────────────────────────────────────
  return (
    <>
      <div
        onPointerDown={() => focus(win.id)}
        style={{
          left: win.x,
          top: win.y,
          width: win.w,
          height: win.minimized ? undefined : win.h,
          zIndex,
        }}
        className="fixed flex flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-[0_12px_40px_-12px_rgba(0,0,0,0.4)]"
      >
        {header}
        {body}
        {/* Collapsed: only the side grips (width-only); expanded: all of them. */}
        {(win.minimized ? SIDE_GRIPS : RESIZE_GRIPS).map((grip) => (
          <div
            key={grip.dir}
            onPointerDown={(e) => onResizeStart(e, grip.dir)}
            aria-hidden
            className={cn("absolute z-10", grip.className)}
          />
        ))}
        {/* A subtle visual cue on the bottom-right corner. */}
        {!win.minimized && (
          <div
            aria-hidden
            className="pointer-events-none absolute bottom-0 right-0 h-3.5 w-3.5"
            style={{
              background:
                "linear-gradient(135deg, transparent 50%, var(--color-border) 50%)",
            }}
          />
        )}
      </div>
      <DeleteToolConfirm
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        name={toolName}
        onConfirm={onDelete}
      />
    </>
  );
}

function DeleteToolConfirm({
  open,
  onOpenChange,
  name,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  name: string;
  onConfirm: () => void;
}) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Delete this tool?"
      description={`Delete "${name}" and all its entries? This can't be undone.`}
      confirmLabel="Yes, delete"
      cancelLabel="Cancel"
      onConfirm={onConfirm}
    />
  );
}
