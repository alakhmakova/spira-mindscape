import { useEffect, useMemo, useState } from "react";
import { Wrench, X, Trash2, Search, Pin } from "lucide-react";
import { toast } from "sonner";
import { type Tool } from "@/lib/spira/tools-api";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { useTools, useToolWindows, useToolPins } from "./tools-store";
import { cn } from "@/lib/utils";

/** Above this many tools, show the search box (keeps the list tidy for few tools). */
const SEARCH_THRESHOLD = 6;
/** Pinned tools up to this rank show their number; beyond it, a plain pin mark. */
const NUMBERED_PINS = 10;

/**
 * The single, app-wide entry point to Personal Tools: a floating button in the
 * bottom-right corner (on every page). Clicking it opens a searchable list of
 * the user's tools; clicking a tool opens it in a floating window (see
 * {@link ToolWindows}). Tools are global — not tied to any goal — and any AI
 * chat can create, change, or fill them. This list is the only tools index.
 *
 * Pinning reorders a tool to the top; the first {@link NUMBERED_PINS} pinned
 * tools show their pin number so the order is explicit.
 */
export function ToolsFab() {
  const tools = useTools((s) => s.tools);
  const ensureLoaded = useTools((s) => s.ensureLoaded);
  const removeTool = useTools((s) => s.removeTool);
  const openWindow = useToolWindows((s) => s.open);
  const pinnedIds = useToolPins((s) => s.pinnedIds);
  const togglePin = useToolPins((s) => s.toggle);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [confirm, setConfirm] = useState<Tool | null>(null);

  useEffect(() => {
    ensureLoaded();
  }, [ensureLoaded]);

  // Pinned first (in pin order), then the rest in their existing order; a stable
  // sort keeps ties put. Search filters by name.
  const ordered = useMemo(() => {
    const rank = new Map(pinnedIds.map((id, i) => [id, i]));
    const q = query.trim().toLowerCase();
    const matched = q
      ? tools.filter((t) => t.name.toLowerCase().includes(q))
      : tools;
    return [...matched].sort((a, b) => {
      const pa = rank.get(a.id) ?? Infinity;
      const pb = rank.get(b.id) ?? Infinity;
      return pa === pb ? 0 : pa - pb; // guards Infinity - Infinity = NaN
    });
  }, [tools, pinnedIds, query]);

  const openTool = (t: Tool) => {
    openWindow(t.id);
    setOpen(false);
  };

  const remove = async (t: Tool) => {
    try {
      await removeTool(t.id);
      toast.success(`“${t.name}” deleted`);
    } catch {
      toast.error("Couldn't delete the tool.");
    }
  };

  const showSearch = tools.length >= SEARCH_THRESHOLD;

  return (
    <>
      {/* Click-catcher: closes the list when clicking anywhere else. */}
      {open && (
        <div
          className="fixed inset-0 z-40"
          aria-hidden
          onClick={() => setOpen(false)}
        />
      )}

      <div className="fixed bottom-4 right-4 z-50 flex flex-col items-end gap-2 sm:bottom-5 sm:right-5">
        {open && (
          <div
            role="dialog"
            aria-label="Your tools"
            className="flex max-h-[70vh] w-[min(90vw,21rem)] flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-[0_18px_50px_-12px_rgba(0,0,0,0.45)]"
          >
            <div className="flex items-center justify-between px-4 pb-2 pt-3">
              <h2 className="text-[15px] font-semibold text-foreground">
                Tools
                {tools.length > 0 && (
                  <span className="ml-2 font-normal text-muted-foreground">
                    {tools.length}
                  </span>
                )}
              </h2>
              <button
                type="button"
                aria-label="Close"
                onClick={() => setOpen(false)}
                className="grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {showSearch && (
              <div className="px-3 pb-2">
                <div className="flex items-center gap-2 rounded-lg bg-secondary/60 px-2.5 py-1.5">
                  <Search className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search tools…"
                    className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                  />
                  {query && (
                    <button
                      type="button"
                      aria-label="Clear search"
                      onClick={() => setQuery("")}
                      className="text-muted-foreground hover:text-foreground"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              </div>
            )}

            {tools.length === 0 ? (
              <div className="px-4 pb-5 pt-1">
                <p className="text-sm leading-[1.55] text-muted-foreground">
                  No tools yet. Ask your AI coach to create one for you — for
                  example a job-application tracker, a countdown to a wedding or
                  a trip, a reminder for an important date, or a tracker for
                  your cycle, pregnancy, or medications. Just describe what
                  you’d like to keep track of.
                </p>
              </div>
            ) : ordered.length === 0 ? (
              <p className="px-4 pb-4 pt-1 text-sm text-muted-foreground">
                No tools match “{query}”.
              </p>
            ) : (
              <ul className="hide-scrollbar min-h-0 flex-1 overflow-y-auto p-1.5">
                {ordered.map((t) => {
                  const pinRank = pinnedIds.indexOf(t.id);
                  const pinned = pinRank !== -1;
                  return (
                    <li
                      key={t.id}
                      className="group flex items-center gap-1.5 rounded-lg px-1 hover:bg-secondary/60"
                    >
                      <button
                        type="button"
                        onClick={() => togglePin(t.id)}
                        aria-label={pinned ? "Unpin tool" : "Pin tool"}
                        aria-pressed={pinned}
                        title={pinned ? "Unpin" : "Pin to top"}
                        className={cn(
                          "grid h-6 w-6 shrink-0 place-items-center rounded-md text-xs font-bold",
                          pinned
                            ? "bg-primary text-primary-foreground"
                            : "text-muted-foreground hover:text-primary",
                        )}
                      >
                        {pinned && pinRank < NUMBERED_PINS ? (
                          pinRank + 1
                        ) : (
                          <Pin
                            className={cn(
                              "h-3.5 w-3.5",
                              pinned && "fill-current",
                            )}
                          />
                        )}
                      </button>
                      <button
                        onClick={() => openTool(t)}
                        className="min-w-0 flex-1 truncate py-2 pr-1 text-left text-sm font-medium text-foreground outline-none"
                      >
                        {t.name}
                      </button>
                      <button
                        onClick={() => setConfirm(t)}
                        aria-label={`Delete ${t.name}`}
                        title="Delete tool"
                        className="grid h-7 w-7 shrink-0 place-items-center rounded-md text-muted-foreground hover:text-destructive"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}

        <button
          type="button"
          aria-label="Tools"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className="inline-flex items-center gap-2 rounded-full bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground shadow-[0_10px_30px_-8px_rgba(0,0,0,0.5)] transition-colors hover:bg-primary/90"
        >
          <Wrench className="h-4 w-4" />
          Tools
        </button>
      </div>

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Delete this tool?"
        description={`Delete "${confirm?.name ?? "this tool"}" and all its entries? This can't be undone.`}
        confirmLabel="Yes, delete"
        cancelLabel="Cancel"
        onConfirm={() => {
          if (confirm) void remove(confirm);
          setConfirm(null);
        }}
      />
    </>
  );
}
