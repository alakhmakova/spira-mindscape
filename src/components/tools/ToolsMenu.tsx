import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import { Wrench, ChevronDown, ArrowRight, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import type { Tool } from "@/lib/spira/tools-api";
import { useTools, useToolWindows } from "./tools-store";

/**
 * The Tools entry point: a dropdown listing this goal's tools, the user's global
 * tools, and a link to the full Tools page. Selecting a tool opens it in a
 * floating window (see {@link ToolWindows}); each row has a delete affordance so
 * tools can be removed straight from the goal page. Reads the shared tools store
 * so a tool the AI just created appears here instantly. Used in the goal sub-nav
 * (`variant="navlink"`) and in the user menu fallback (`variant="menuitem"`).
 */
export function ToolsMenu({
  goalId,
  variant = "navlink",
}: {
  goalId?: string;
  variant?: "navlink" | "menuitem";
}) {
  const tools = useTools((s) => s.tools);
  const ensureLoaded = useTools((s) => s.ensureLoaded);
  const removeTool = useTools((s) => s.removeTool);
  const openWindow = useToolWindows((s) => s.open);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState<Tool | null>(null);

  useEffect(() => {
    ensureLoaded();
  }, [ensureLoaded]);

  const goalTools = goalId
    ? tools.filter((t) => String(t.goalId) === goalId)
    : [];
  const globalTools = tools.filter((t) => t.goalId == null);

  const openTool = (t: Tool) => {
    openWindow(t.id);
    setMenuOpen(false);
  };

  const remove = async (t: Tool) => {
    try {
      await removeTool(t.id);
      toast.success(`“${t.name}” deleted`);
    } catch {
      toast.error("Couldn't delete the tool.");
    }
  };

  const trigger =
    variant === "navlink" ? (
      <button className="inline-flex items-center gap-1 text-[13px] font-medium text-muted-foreground transition-colors hover:text-foreground">
        Tools <ChevronDown className="h-3.5 w-3.5" />
      </button>
    ) : (
      <button className="flex w-full items-center gap-2 px-2 py-1.5 text-sm outline-none">
        <Wrench className="h-4 w-4" /> Tools
      </button>
    );

  return (
    <>
      <DropdownMenu open={menuOpen} onOpenChange={setMenuOpen}>
        <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64">
          {goalTools.length > 0 && (
            <>
              <DropdownMenuLabel>This goal</DropdownMenuLabel>
              {goalTools.map((t) => (
                <ToolRow
                  key={t.id}
                  tool={t}
                  iconClass="text-primary"
                  onOpen={() => openTool(t)}
                  onDelete={() => setConfirm(t)}
                />
              ))}
              <DropdownMenuSeparator />
            </>
          )}

          {globalTools.length > 0 && (
            <>
              <DropdownMenuLabel>Global</DropdownMenuLabel>
              {globalTools.map((t) => (
                <ToolRow
                  key={t.id}
                  tool={t}
                  iconClass="text-muted-foreground"
                  onOpen={() => openTool(t)}
                  onDelete={() => setConfirm(t)}
                />
              ))}
              <DropdownMenuSeparator />
            </>
          )}

          {tools.length === 0 && (
            <div className="px-2 py-2 text-xs text-muted-foreground">
              No tools yet. Ask the AI for a tracker.
            </div>
          )}

          <DropdownMenuItem asChild className="gap-2 font-medium">
            <Link to="/tools">
              <ArrowRight className="h-3.5 w-3.5" /> All tools
            </Link>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

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

/** One tool line: a name button that opens it, plus a delete affordance. */
function ToolRow({
  tool,
  iconClass,
  onOpen,
  onDelete,
}: {
  tool: Tool;
  iconClass: string;
  onOpen: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="flex items-center gap-1 rounded-sm px-1 hover:bg-secondary/60">
      <button
        onClick={onOpen}
        className="flex flex-1 items-center gap-2 px-1 py-1.5 text-left text-sm outline-none"
      >
        <Wrench className={`h-3.5 w-3.5 ${iconClass}`} /> {tool.name}
      </button>
      <button
        onClick={onDelete}
        aria-label={`Delete ${tool.name}`}
        title="Delete tool"
        className="grid h-7 w-7 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-destructive"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
