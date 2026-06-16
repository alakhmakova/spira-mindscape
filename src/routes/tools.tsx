import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  Wrench,
  Trash2,
  ArrowLeft,
  Target as TargetIcon,
  Globe,
} from "lucide-react";
import { toast } from "sonner";
import { parseSchema, type Tool } from "@/lib/spira/tools-api";
import { useTools, useToolWindows } from "@/components/tools/tools-store";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";

export const Route = createFileRoute("/tools")({
  head: () => ({
    meta: [
      { title: "Tools — Spira" },
      {
        name: "description",
        content:
          "Your personal trackers and tools, created with the AI assistant.",
      },
    ],
  }),
  component: ToolsPage,
});

function ToolsPage() {
  const tools = useTools((s) => s.tools);
  const loaded = useTools((s) => s.loaded);
  const reload = useTools((s) => s.reload);
  const removeTool = useTools((s) => s.removeTool);
  const openWindow = useToolWindows((s) => s.open);
  const [confirm, setConfirm] = useState<Tool | null>(null);
  const loading = !loaded;

  useEffect(() => {
    void reload();
  }, [reload]);

  const remove = async (tool: Tool) => {
    try {
      await removeTool(tool.id);
    } catch {
      toast.error("Couldn't delete the tool.");
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-4 sm:px-6 py-8 sm:py-12">
      <Link
        to="/"
        className="link-action mb-4 inline-flex items-center gap-1.5 text-sm font-semibold"
      >
        <ArrowLeft className="h-4 w-4" /> All goals
      </Link>
      <header className="mb-6 flex items-center gap-2">
        <Wrench className="h-5 w-5 text-primary" />
        <h1 className="font-sans text-2xl font-bold">Tools</h1>
      </header>

      {loading && <p className="text-sm text-muted-foreground">Loading…</p>}

      {!loading && tools.length === 0 && (
        <div className="surface-card p-6 text-center">
          <p className="text-sm text-muted-foreground">
            No tools yet. Ask the AI assistant for a tracker — e.g.{" "}
            <span className="text-foreground">
              “make me a job application tracker”
            </span>{" "}
            or <span className="text-foreground">“a weight log”</span> — and
            approve it. It will appear here and on its goal.
          </p>
        </div>
      )}

      {/* Card grid in the All-Goals style — each card opens the tool in a modal. */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tools.map((tool) => {
          const schema = parseSchema(tool.schemaJson);
          const fields = schema?.columns.length ?? 0;
          return (
            <div
              key={tool.id}
              className="surface-card group relative flex flex-col gap-3 p-5 text-left transition-shadow hover:shadow-md"
            >
              <button
                onClick={() => setConfirm(tool)}
                aria-label="Delete tool"
                title="Delete tool"
                className="absolute right-3 top-3 grid h-7 w-7 place-items-center rounded-md text-muted-foreground opacity-0 transition-opacity hover:bg-secondary hover:text-destructive group-hover:opacity-100"
              >
                <Trash2 className="h-4 w-4" />
              </button>
              <button
                onClick={() => openWindow(tool.id)}
                className="flex flex-1 flex-col gap-2 text-left outline-none"
              >
                <div className="grid h-9 w-9 place-items-center rounded-lg bg-primary-soft text-primary">
                  <Wrench className="h-4 w-4" />
                </div>
                <h2 className="font-semibold text-foreground">{tool.name}</h2>
                <div className="mt-auto flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="inline-flex items-center gap-1">
                    {tool.goalId == null ? (
                      <Globe className="h-3 w-3" />
                    ) : (
                      <TargetIcon className="h-3 w-3" />
                    )}
                    {tool.goalId == null ? "Global" : "Goal"}
                  </span>
                  <span>·</span>
                  <span>
                    {fields} field{fields === 1 ? "" : "s"}
                  </span>
                </div>
              </button>
            </div>
          );
        })}
      </div>

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Delete this tool?"
        description={`Delete "${confirm?.name ?? "this tool"}" and all its entries? This can't be undone.`}
        confirmLabel="Yes, delete"
        cancelLabel="Cancel"
        onConfirm={() => {
          if (confirm) remove(confirm);
          setConfirm(null);
        }}
      />
    </div>
  );
}
