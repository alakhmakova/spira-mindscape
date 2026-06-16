import { useEffect, useState } from "react";
import { Minus, X, Trash2, GripVertical, Square, Pin } from "lucide-react";
import { useIsMobile } from "@/hooks/use-mobile";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { toast } from "sonner";
import { ToolRenderer } from "./ToolRenderer";
import { useTools, useToolWindows, type ToolWindowState } from "./tools-store";
import { cn } from "@/lib/utils";

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

  // Pinned windows are restored from storage before tools are loaded — make sure
  // the tools list is fetched so those windows can resolve and render.
  useEffect(() => {
    if (windows.length > 0) ensureLoaded();
  }, [windows.length, ensureLoaded]);

  if (windows.length === 0) return null;

  return (
    <>
      {windows.map((win) => {
        const tool = tools.find((t) => t.id === win.id);
        if (!tool) return null;
        return <ToolWindow key={win.id} win={win} toolName={tool.name} />;
      })}
    </>
  );
}

function ToolWindow({
  win,
  toolName,
}: {
  win: ToolWindowState;
  toolName: string;
}) {
  const isMobile = useIsMobile();
  const { close, focus, toggleMinimize, togglePin, setRect } = useToolWindows();
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
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  };

  // Resize from the bottom-right corner (desktop only).
  const onResizePointerDown = (e: React.PointerEvent) => {
    e.stopPropagation();
    focus(win.id);
    const startX = e.clientX;
    const startY = e.clientY;
    const ow = win.w;
    const oh = win.h;
    const move = (ev: PointerEvent) => {
      setRect(win.id, {
        w: ow + (ev.clientX - startX),
        h: oh + (ev.clientY - startY),
      });
    };
    const up = () => {
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
        aria-label={win.pinned ? "Unpin tool" : "Pin tool to the page"}
        title={
          win.pinned
            ? "Unpin (won't reopen after reload)"
            : "Pin — keep this tool here, even after reload"
        }
        aria-pressed={win.pinned}
        onClick={() => togglePin(win.id)}
        className={cn(
          "grid h-7 w-7 place-items-center rounded-md hover:bg-secondary",
          win.pinned
            ? "text-primary"
            : "text-muted-foreground hover:text-foreground",
        )}
      >
        <Pin className={cn("h-3.5 w-3.5", win.pinned && "fill-current")} />
      </button>
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
          style={{ zIndex: 40 + win.z }}
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
          zIndex: 40 + win.z,
        }}
        className="fixed flex flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-[0_12px_40px_-12px_rgba(0,0,0,0.4)]"
      >
        {header}
        {body}
        {!win.minimized && (
          <div
            onPointerDown={onResizePointerDown}
            aria-hidden
            className="absolute bottom-0 right-0 h-4 w-4 cursor-se-resize"
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
