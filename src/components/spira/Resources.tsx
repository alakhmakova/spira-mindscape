import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  Trash2,
  ExternalLink,
  Download,
  Copy,
  Check,
  ArrowUpRight,
  Pencil,
  ZoomIn,
  ZoomOut,
  X,
  ChevronRight,
  Loader2,
  Info,
} from "lucide-react";
import type { Goal, Resource, ResourceInput, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import {
  countResourceAttachments,
  planResourceDetach,
  resourceDisplayName,
  titleFromUrl,
} from "@/lib/spira/resources";
import { isSafeHttpUrl } from "@/lib/spira/links";
import { resourceTypeMeta } from "@/components/spira/resource-meta";
import {
  InlineResourcesContextProvider,
  type InlineResourcesValue,
} from "@/components/spira/inline-resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/hooks/use-mobile";
import { logger } from "@/lib/logger";
import { cn } from "@/lib/utils";
import { AutoTextarea } from "@/components/spira/Inline";
import { FIELD_LIMITS, lengthError } from "@/lib/spira/limits";
import { PdfViewer } from "@/components/spira/PdfViewer";
import { RichTextEditor } from "@/components/spira/RichTextEditor";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import {
  downloadNoteTxt,
  downloadNoteDoc,
  printNotePdf,
  openInGoogleDocs,
  syncGoogleDoc,
} from "@/components/spira/note-export";
import { toast } from "sonner";

const typeMeta = resourceTypeMeta;

const MAX_RESOURCE_FILE_BYTES = 5 * 1024 * 1024;
// Single source of truth for the resource label cap (mirrors the server); see limits.ts.
const MAX_RESOURCE_LABEL_LENGTH = FIELD_LIMITS.resourceLabel;

function validResourceFileType(mime: string) {
  return mime.startsWith("image/") || mime === "application/pdf";
}

function validResourceUrl(value: string) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function validResourceEmail(value: string) {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value);
}

/* ── helpers: copy & download ─────────────────────── */

function stripHtml(html: string): string {
  const div = document.createElement("div");
  div.innerHTML = html;
  return div.textContent || div.innerText || "";
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => {
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, 100);
}

async function copyPlainText(text: string) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (err) {
      // Expected on some browsers/permission states — the execCommand path below is
      // the fallback, so this is a dev-console note, not an incident.
      logger.warn("clipboard.writeText failed, falling back", err);
    }
  }
  // Legacy fallback for HTTP / insecure contexts
  const textArea = document.createElement("textarea");
  textArea.value = text;
  textArea.style.position = "fixed";
  textArea.style.left = "-9999px";
  document.body.appendChild(textArea);
  textArea.select();
  try {
    document.execCommand("copy");
  } catch (err) {
    // Both copy paths are now exhausted, so the user's copy silently did nothing.
    logger.reportError(err, { kind: "render" });
  }
  document.body.removeChild(textArea);
}

/**
 * Copies rich text: writes BOTH an HTML and a plain-text flavour to the
 * clipboard, so pasting into a rich editor (Word, Google Docs, email) keeps the
 * note's formatting (headings, bold, lists, links) while plain editors still get
 * clean text. Falls back to plain text where the async ClipboardItem API isn't
 * available (older/insecure browsers).
 */
async function copyRichText(html: string, plainText: string) {
  if (
    navigator.clipboard &&
    navigator.clipboard.write &&
    typeof ClipboardItem !== "undefined"
  ) {
    try {
      await navigator.clipboard.write([
        new ClipboardItem({
          "text/html": new Blob([html], { type: "text/html" }),
          "text/plain": new Blob([plainText], { type: "text/plain" }),
        }),
      ]);
      return;
    } catch (err) {
      // Degrades to plain text, which is a fine outcome — dev-console only.
      logger.warn("clipboard.write (rich text) failed, using plain text", err);
    }
  }
  await copyPlainText(plainText);
}

async function copyImageToClipboard(dataUrl: string, title?: string) {
  if (navigator.clipboard && navigator.clipboard.write) {
    try {
      await navigator.clipboard.write([
        new ClipboardItem({
          "image/png": toPngBlob(dataUrl),
        }),
      ]);
      return;
    } catch {
      // Clipboard may be unavailable on some browsers or insecure origins.
    }
  }

  if (navigator.share && navigator.canShare) {
    try {
      const blob = dataUrlToBlob(dataUrl);
      const file = new File([blob], "image.png", { type: blob.type });
      if (navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file] });
        return;
      }
    } catch {
      // Share may be unavailable on some browsers or insecure origins.
    }
  }

  // Ultimate fallback if clipboard and share fail (e.g. insecure HTTP)
  alert(
    "Copying or sharing images on this browser requires a secure HTTPS connection.",
  );
}

function toPngBlob(dataUrl: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.width;
      canvas.height = img.height;
      canvas.getContext("2d")!.drawImage(img, 0, 0);
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error("toBlob failed"))),
        "image/png",
      );
    };
    img.onerror = reject;
    img.src = dataUrl;
  });
}

function dataUrlToBlob(dataUrl: string): Blob {
  const [header, base64] = dataUrl.split(",");
  const mime = header.match(/:(.*?);/)?.[1] || "application/octet-stream";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

function downloadDataUrl(dataUrl: string, filename: string) {
  const blob = dataUrlToBlob(dataUrl);
  downloadBlob(blob, filename);
}

function useCopied() {
  const [copied, setCopied] = useState(false);
  const run = async (fn: () => Promise<void>) => {
    try {
      await fn();
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch (err) {
      // The user pressed copy/download and nothing happened — no UI feedback either,
      // so without this report the failure is invisible on both sides.
      logger.reportError(err, { kind: "render" });
    }
  };
  return { copied, run } as const;
}

export function ResourcesList({ goal }: { goal: Goal }) {
  const removeResource = useSpira((s) => s.removeResource);
  const loadResourceFile = useSpira((s) => s.loadResourceFile);
  const [previewId, setPreviewId] = useState<string | null>(null);
  // A resource that is attached inside other elements can't just vanish — confirm first, then
  // degrade every `{{res:id}}` chip to plain text so the sentences still read.
  const [pendingDelete, setPendingDelete] = useState<Resource | null>(null);
  const detach = useDetachResource(goal);

  if (goal.resources.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        Capture notes, links, files, and emails that support this goal.
      </p>
    );
  }

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {goal.resources.map((r) => (
          <ResourceCard
            key={r.id}
            resource={r}
            // File contents are not in the goals list (lazy) — fetch them on demand for the
            // card's copy/download actions.
            loadFile={() => loadResourceFile(goal.id, r.id)}
            onOpen={() => {
              if (r.type === "link" && isSafeHttpUrl(r.url))
                window.open(r.url, "_blank", "noopener,noreferrer");
              else setPreviewId(r.id);
            }}
            // Always ask. Deleting an UNATTACHED resource used to happen on the spot, with no
            // confirmation at all — the one case where the loss is silent and irreversible.
            onRemove={() => setPendingDelete(r)}
          />
        ))}
      </div>

      <ResourcePreview
        goalId={goal.id}
        resourceId={previewId}
        onClose={() => setPreviewId(null)}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete this resource?"
        description={
          pendingDelete
            ? (() => {
                const name = resourceDisplayName(pendingDelete);
                const count = countResourceAttachments(goal, pendingDelete.id);
                // The name is set bold in both wordings — the same rule the goal and target
                // dialogs follow, so what is about to go is never buried in a sentence.
                const named = (
                  <strong className="font-semibold">&quot;{name}&quot;</strong>
                );
                return count > 0 ? (
                  <span className="flex items-start gap-2">
                    <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                    <span>
                      {named} is attached in {count}{" "}
                      {count === 1 ? "place" : "places"} on this goal. Deleting
                      it turns each of those links into plain text — the words
                      stay, only the link to the resource is lost.
                    </span>
                  </span>
                ) : (
                  <span>
                    {named} will be permanently deleted. You can&apos;t undo
                    this.
                  </span>
                );
              })()
            : ""
        }
        confirmLabel="Yes, delete"
        onConfirm={() => {
          if (!pendingDelete) return;
          detach(pendingDelete);
          removeResource(goal.id, pendingDelete.id);
          setPendingDelete(null);
        }}
      />
    </>
  );
}

/**
 * Rewrites every element of the goal that references a resource so the token becomes plain text
 * (the resource's title). Used right before the resource itself is deleted.
 */
function useDetachResource(goal: Goal) {
  const updateOption = useSpira((s) => s.updateOption);
  const updateReality = useSpira((s) => s.updateReality);
  const updateTarget = useSpira((s) => s.updateTarget);

  return (resource: Resource) => {
    for (const patch of planResourceDetach(
      goal,
      resource.id,
      resourceDisplayName(resource),
    )) {
      if (patch.kind === "option") {
        updateOption(goal.id, patch.optionId, { text: patch.text });
      } else if (patch.kind === "reality") {
        updateReality(goal.id, patch.realityKind, patch.itemId, patch.text);
      } else if (patch.kind === "targetTitle") {
        updateTarget(goal.id, patch.targetId, { title: patch.title });
      } else {
        updateTarget(goal.id, patch.targetId, {
          items: patch.items,
        } as Partial<Target>);
      }
    }
  };
}

/**
 * Makes a goal's resources available to every inline field inside it: chips resolve their title,
 * the ⋯ menus can attach one, and an over-limit URL can become a link resource. Also owns the
 * preview panel a chip opens (see specs/2026-07-28-inline-resource-attachments/requirements.md).
 */
export function InlineResourcesProvider({
  goal,
  children,
}: {
  goal: Goal;
  children: React.ReactNode;
}) {
  const addResource = useSpira((s) => s.addResource);
  const [previewId, setPreviewId] = useState<string | null>(null);
  const resources = goal.resources;

  const value = useMemo<InlineResourcesValue>(
    () => ({
      goalId: goal.id,
      resources,
      createLinkResource: (url) =>
        new Promise<string | null>((resolve) => {
          // `addResource` returns a temp id synchronously and hands back the persisted resource
          // (with its server id) in the callback — only that id may go into a token.
          const timeout = setTimeout(() => resolve(null), 15000);
          addResource(
            goal.id,
            { type: "link", title: titleFromUrl(url), url },
            (created) => {
              clearTimeout(timeout);
              resolve(created.id);
            },
          );
        }),
      openResource: (id, mode = "open") => {
        const resource = resources.find((r) => r.id === id);
        if (!resource) return;
        // Only http(s) may be navigated to — a stored javascript:/data: URL falls through to the
        // preview panel instead of being opened.
        if (
          mode === "open" &&
          resource.type === "link" &&
          isSafeHttpUrl(resource.url)
        ) {
          window.open(resource.url, "_blank", "noopener,noreferrer");
          return;
        }
        setPreviewId(id);
      },
    }),
    [goal.id, resources, addResource],
  );

  return (
    <InlineResourcesContextProvider value={value}>
      {children}
      <ResourcePreview
        goalId={goal.id}
        resourceId={previewId}
        onClose={() => setPreviewId(null)}
      />
    </InlineResourcesContextProvider>
  );
}

/* ── Card with inline actions ─────────────────────── */

function ResourceCard({
  resource: r,
  onOpen,
  onRemove,
  loadFile,
}: {
  resource: Resource;
  onOpen: () => void;
  onRemove: () => void;
  loadFile: () => Promise<string>;
}) {
  const [expanded, setExpanded] = useState(false);
  const Icon = typeMeta[r.type].icon;
  const { copied, run } = useCopied();

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (r.type === "note") {
      // Keep the note's formatting when pasting into a rich editor; plain-text
      // flavour is included as a fallback.
      run(() => copyRichText(r.body, stripHtml(r.body)));
    } else if (r.type === "link") {
      run(() => copyPlainText(r.url));
    } else if (r.type === "file" && r.mime.startsWith("image/")) {
      run(async () => {
        const dataUrl = r.dataUrl || (await loadFile());
        if (dataUrl) await copyImageToClipboard(dataUrl);
      });
    } else if (r.type === "email" && r.email) {
      run(() => copyPlainText(r.email!));
    }
  };

  const handleDownload = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (r.type === "note") {
      const text = stripHtml(r.body);
      const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
      downloadBlob(blob, `${r.title || "note"}.txt`);
    } else if (r.type === "file") {
      void (async () => {
        const dataUrl = r.dataUrl || (await loadFile());
        if (dataUrl) downloadDataUrl(dataUrl, r.title);
      })();
    }
  };

  const canCopy =
    r.type === "note" ||
    r.type === "link" ||
    (r.type === "file" && r.mime.startsWith("image/")) ||
    (r.type === "email" && !!r.email);
  const canDownload = r.type === "note" || r.type === "file";

  const typeColors: Record<
    string,
    { bg: string; text: string; border: string; icon: string }
  > = {
    note: {
      bg: "bg-[#f0f9ff]",
      text: "text-[#0c69a3]",
      border: "border-[#bae2fd]",
      icon: "text-[#0c69a3]",
    },
    link: {
      bg: "bg-[#f0fdf4]",
      text: "text-[#15803d]",
      border: "border-[#b7e4c7]",
      icon: "text-[#15803d]",
    },
    file: {
      bg: "bg-[#fef3c7]",
      text: "text-[#92400e]",
      border: "border-[#fde68a]",
      icon: "text-[#92400e]",
    },
    email: {
      bg: "bg-[#faf5ff]",
      text: "text-[#7c3aed]",
      border: "border-[#e9d5ff]",
      icon: "text-[#7c3aed]",
    },
  };

  const colors = typeColors[r.type] || typeColors.note;

  return (
    <div
      className={cn(
        "group inline-flex items-center rounded-lg border bg-white transition-all duration-200 overflow-hidden",
        expanded
          ? "border-border/60 shadow-[0_2px_12px_-2px_rgba(0,0,0,0.06)]"
          : "border-border/40 hover:border-border/60 hover:shadow-[0_1px_6px_-1px_rgba(0,0,0,0.04)]",
      )}
    >
      <button
        onClick={onOpen}
        className="flex items-center gap-1.5 pl-2 pr-1 py-1.5 h-9 transition-colors hover:bg-secondary/20"
      >
        <div
          className={cn(
            "grid h-5 w-5 place-items-center rounded-md shrink-0",
            colors.bg,
          )}
        >
          <Icon className={cn("h-3 w-3", colors.icon)} />
        </div>
        <span className="text-sm font-medium text-foreground whitespace-nowrap max-w-[140px] truncate">
          {resourceDisplayName(r)}
        </span>
      </button>

      {!expanded ? (
        <button
          onClick={(e) => {
            e.stopPropagation();
            setExpanded(true);
          }}
          className="flex h-full items-center justify-center px-1.5 text-muted-foreground/50 transition-colors hover:text-muted-foreground hover:bg-secondary/30"
        >
          <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
        </button>
      ) : (
        <div className="flex items-center gap-0.5 pr-1 pl-0.5">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setExpanded(false);
            }}
            className="flex h-6 w-6 items-center justify-center rounded-md text-muted-foreground/60 transition-colors hover:text-muted-foreground hover:bg-secondary/50"
          >
            <ChevronRight className="h-3 w-3 rotate-180" />
          </button>

          <div className="w-px h-3.5 bg-border/60" />

          {r.type === "email" ? (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onOpen();
              }}
              className="grid h-6 w-6 place-items-center rounded-md text-muted-foreground/60 hover:bg-secondary/50 hover:text-primary transition-colors"
              title="Edit email"
            >
              <Pencil className="h-3 w-3" />
            </button>
          ) : (
            <>
              {canCopy && (
                <button
                  onClick={handleCopy}
                  className="grid h-6 w-6 place-items-center rounded-md text-muted-foreground/60 hover:bg-secondary/50 hover:text-primary transition-colors"
                  title={copied ? "Copied!" : "Copy"}
                >
                  {copied ? (
                    <Check className="h-3 w-3 text-green-600" />
                  ) : (
                    <Copy className="h-3 w-3" />
                  )}
                </button>
              )}
              {canDownload && (
                <button
                  onClick={handleDownload}
                  className="grid h-6 w-6 place-items-center rounded-md text-muted-foreground/60 hover:bg-secondary/50 hover:text-primary transition-colors"
                  title="Download"
                >
                  <Download className="h-3 w-3" />
                </button>
              )}
            </>
          )}

          <button
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            className="grid h-6 w-6 place-items-center rounded-md text-muted-foreground/60 hover:text-destructive transition-colors"
            title="Remove"
          >
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
      )}
    </div>
  );
}

function CopyField({
  label,
  value,
  actionIcon,
  onAction,
  actionTitle,
}: {
  label: string;
  value: string;
  actionIcon?: React.ReactNode;
  onAction?: () => void;
  actionTitle?: string;
}) {
  const { copied, run } = useCopied();
  return (
    <div className="rounded-md border border-border bg-surface px-4 py-3">
      <label className="block text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
        {label}
      </label>
      <div className="mt-1.5 flex items-center gap-2">
        <div className="flex-1 min-w-0 break-words text-sm font-semibold text-foreground">
          {value}
        </div>
        {actionIcon && onAction && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onAction();
            }}
            className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-primary transition-colors shrink-0"
            title={actionTitle}
          >
            {actionIcon}
          </button>
        )}
        <button
          onClick={(e) => {
            e.stopPropagation();
            run(() => copyPlainText(value));
          }}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-primary transition-colors shrink-0"
          title={`Copy ${label.toLowerCase()}`}
        >
          {copied ? (
            <Check className="h-4 w-4 text-green-600" />
          ) : (
            <Copy className="h-4 w-4" />
          )}
        </button>
      </div>
    </div>
  );
}

/* ── Preview body (shared by sheet + drawer) ──────── */

function PreviewBody({
  resource,
  goalId,
  updateResource,
  title,
  isMobile,
  onClose,
}: {
  resource: Resource;
  goalId: string;
  updateResource: (
    goalId: string,
    resourceId: string,
    patch: Partial<Resource>,
  ) => void;
  title: string;
  isMobile: boolean;
  onClose: () => void;
}) {
  const { copied, run } = useCopied();
  const [isEditingEmail, setIsEditingEmail] = useState(false);
  const loadResourceFile = useSpira((s) => s.loadResourceFile);

  // File contents are excluded from the goals list (lazy) — pull them when a file is opened.
  const isFile = resource.type === "file";
  const hasFileData = isFile && !!resource.dataUrl;
  useEffect(() => {
    if (isFile && !hasFileData) void loadResourceFile(goalId, resource.id);
  }, [isFile, hasFileData, goalId, resource.id, loadResourceFile]);

  const ensureFile = async () =>
    (resource.type === "file" && resource.dataUrl) ||
    (await loadResourceFile(goalId, resource.id));

  if (isEditingEmail) {
    return (
      <Form
        goalId={goalId}
        initialResource={resource}
        onDone={() => setIsEditingEmail(false)}
      />
    );
  }

  const isImage =
    resource.type === "file" && resource.mime.startsWith("image/");
  const canCopy =
    resource.type === "note" || resource.type === "link" || isImage;
  const canDownload = resource.type === "note" || resource.type === "file";
  const copyLabel =
    isImage && isMobile
      ? "Share"
      : resource.type === "note"
        ? "Copy as plain text"
        : resource.type === "link"
          ? "Copy URL"
          : "Copy image";

  const handleCopy = () => {
    if (resource.type === "note") {
      // Keep the note's formatting when pasting into a rich editor (incl. another note).
      run(() => copyRichText(resource.body, stripHtml(resource.body)));
    } else if (resource.type === "link") {
      run(() => copyPlainText(resource.url));
    } else if (resource.type === "file" && resource.mime.startsWith("image/")) {
      run(async () => {
        const dataUrl = await ensureFile();
        if (dataUrl) await copyImageToClipboard(dataUrl, resource.title);
      });
    }
  };

  const handleDownload = () => {
    if (resource.type === "note") {
      const text = stripHtml(resource.body);
      const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
      downloadBlob(blob, `${resource.title || "note"}.txt`);
    } else if (resource.type === "file") {
      void (async () => {
        const dataUrl = await ensureFile();
        if (dataUrl) downloadDataUrl(dataUrl, resource.title);
      })();
    }
  };

  return (
    <>
      <div className="px-7 py-5 flex items-center justify-between sticky top-0 z-10 bg-primary text-white">
        <div className="flex-1 min-w-0 pr-2">
          {resource.type === "note" ? (
            <AutoTextarea
              required
              requiredMessage="Note title is required"
              maxLength={FIELD_LIMITS.resourceLabel}
              maxLengthLabel="Note title"
              value={resource.title}
              onChange={(v) =>
                updateResource(goalId, resource.id, { title: v })
              }
              className="font-display text-2xl w-full bg-transparent border-none focus:outline-none resize-none p-0 !text-white placeholder:text-white/50"
              placeholder="Note title"
            />
          ) : (
            <h2
              className="font-sans font-bold text-lg truncate pr-4 !text-white"
              style={{ color: "white" }}
            >
              {title}
            </h2>
          )}
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {canCopy && (
            <button
              onClick={handleCopy}
              className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
              aria-label="Copy"
              title={copied ? "Copied!" : copyLabel}
            >
              {copied ? (
                <Check className="h-4 w-4 text-green-300" />
              ) : (
                <Copy className="h-4 w-4" />
              )}
            </button>
          )}
          {resource.type === "note" ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
                  aria-label="Download"
                  title="Download as…"
                >
                  <Download className="h-4 w-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  onClick={() => downloadNoteTxt(resource.title, resource.body)}
                >
                  Plain text (.txt)
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => downloadNoteDoc(resource.title, resource.body)}
                >
                  Word (.doc)
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => printNotePdf(resource.title, resource.body)}
                >
                  PDF (Save as PDF)
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={async () => {
                    try {
                      const link = await openInGoogleDocs(
                        resource.id,
                        resource.body,
                        resource.title,
                      );
                      // Reflect the link locally so the menu immediately offers "Open" +
                      // "Update" (the backend has stored it; this avoids a reload).
                      if (link && link !== resource.driveWebViewLink) {
                        updateResource(goalId, resource.id, {
                          driveWebViewLink: link,
                        });
                      }
                      toast.success(
                        resource.driveWebViewLink
                          ? "Opening in Google Docs"
                          : "Created in Google Docs — opening it now",
                      );
                    } catch (e) {
                      toast.error(
                        e instanceof Error
                          ? e.message
                          : "Couldn't open the Google Doc",
                      );
                    }
                  }}
                >
                  {resource.driveWebViewLink
                    ? "Open in Google Docs"
                    : "Create in Google Docs"}
                </DropdownMenuItem>
                {resource.driveWebViewLink && (
                  <DropdownMenuItem
                    onClick={async () => {
                      try {
                        await syncGoogleDoc(
                          resource.id,
                          resource.body,
                          resource.title,
                        );
                        toast.success("Google Doc updated from this note");
                      } catch (e) {
                        toast.error(
                          e instanceof Error
                            ? e.message
                            : "Couldn't update the Google Doc",
                        );
                      }
                    }}
                  >
                    Update Google Doc from note
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            canDownload && (
              <button
                onClick={handleDownload}
                className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
                aria-label="Download"
                title="Download"
              >
                <Download className="h-4 w-4" />
              </button>
            )
          )}
          {resource.type === "email" && (
            <button
              onClick={() => setIsEditingEmail(true)}
              className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
              aria-label="Edit"
              title="Edit email"
            >
              <Pencil className="h-4 w-4" />
            </button>
          )}
          <div className="w-px h-4 bg-white/30 mx-1" />
          <button
            onClick={onClose}
            className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
            aria-label="Close preview"
            title="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div
        className={cn(
          "px-7 py-6 overflow-y-auto flex-1 min-h-0",
          resource.type === "file" && resource.mime === "application/pdf"
            ? "flex flex-col gap-3 overflow-hidden"
            : "flex flex-col",
        )}
      >
        {resource.type === "note" && (
          <div className="flex-1 min-h-0 relative">
            <RichTextEditor
              value={resource.body || ""}
              onChange={(html) =>
                updateResource(goalId, resource.id, { body: html })
              }
              placeholder="Write your note here..."
            />
          </div>
        )}
        {resource.type === "link" && (
          <a
            href={resource.url}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 link-action text-sm font-semibold"
          >
            <ExternalLink className="h-4 w-4" />
            {resource.url}
          </a>
        )}
        {resource.type === "file" && (
          <>
            {/* Contents load lazily — show a spinner until the bytes arrive. */}
            {!resource.dataUrl ? (
              <div className="grid flex-1 place-items-center py-10 text-muted-foreground">
                <Loader2
                  className="h-6 w-6 animate-spin"
                  aria-label="Loading file"
                />
              </div>
            ) : (
              <>
                {resource.mime.startsWith("image/") && (
                  <ZoomableImage src={resource.dataUrl} alt={resource.title} />
                )}
                {resource.mime === "application/pdf" && (
                  <PdfViewer
                    dataUrl={resource.dataUrl}
                    title={resource.title}
                  />
                )}
              </>
            )}
          </>
        )}
        {resource.type === "email" && (
          <div className="space-y-6">
            <CopyField label="Name" value={resourceDisplayName(resource)} />
            {resource.email && (
              <CopyField
                label="Email"
                value={resource.email}
                actionIcon={<ArrowUpRight className="h-4 w-4" />}
                actionTitle="Send email"
                onAction={() => window.open(`mailto:${resource.email}`)}
              />
            )}
            {resource.role && <CopyField label="Role" value={resource.role} />}
            {resource.phone && (
              <CopyField label="Phone" value={resource.phone} />
            )}
          </div>
        )}
      </div>
    </>
  );
}

/* ── Zoomable image (tap to fullscreen on mobile) ── */

const IMG_ZOOM_MIN = 1;
const IMG_ZOOM_MAX = 5;
const IMG_ZOOM_STEP = 0.5;

function ZoomableImage({ src, alt }: { src: string; alt: string }) {
  const [zoomed, setZoomed] = useState(false);
  const [scale, setScale] = useState(1);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const pan = useRef<{
    sx: number;
    sy: number;
    ox: number;
    oy: number;
  } | null>(null);

  const open = () => {
    setScale(1);
    setPos({ x: 0, y: 0 });
    setZoomed(true);
  };
  const close = () => setZoomed(false);
  const zoomIn = () =>
    setScale((s) => Math.min(IMG_ZOOM_MAX, +(s + IMG_ZOOM_STEP).toFixed(2)));
  const zoomOut = () =>
    setScale((s) => {
      const next = Math.max(IMG_ZOOM_MIN, +(s - IMG_ZOOM_STEP).toFixed(2));
      if (next === IMG_ZOOM_MIN) setPos({ x: 0, y: 0 });
      return next;
    });

  // Drag to pan once zoomed in (works for mouse + touch via pointer events).
  const onPointerDown = (e: React.PointerEvent) => {
    if (scale <= 1) return;
    e.stopPropagation();
    pan.current = { sx: e.clientX, sy: e.clientY, ox: pos.x, oy: pos.y };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!pan.current) return;
    setPos({
      x: pan.current.ox + (e.clientX - pan.current.sx),
      y: pan.current.oy + (e.clientY - pan.current.sy),
    });
  };
  const onPointerUp = () => {
    pan.current = null;
  };

  return (
    <>
      <div className="relative cursor-zoom-in group" onClick={open}>
        <img
          src={src}
          alt={alt}
          className="w-full rounded-md border hairline"
        />
        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/5 transition-colors rounded-md flex items-center justify-center">
          <div className="opacity-0 group-hover:opacity-100 transition-opacity bg-black/60 text-white rounded-full p-2">
            <ZoomIn className="h-5 w-5" />
          </div>
        </div>
      </div>
      {zoomed && (
        <div
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center overflow-hidden"
          onClick={close}
        >
          <button
            onClick={close}
            className="absolute top-4 right-4 z-10 h-10 w-10 grid place-items-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-colors"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>

          {/* Zoom toolbar */}
          <div
            className="absolute bottom-5 left-1/2 z-10 flex -translate-x-1/2 items-center gap-0.5 rounded-full bg-white/10 px-1 py-1 text-white backdrop-blur"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={zoomOut}
              disabled={scale <= IMG_ZOOM_MIN}
              aria-label="Zoom out"
              className="grid h-9 w-9 place-items-center rounded-full transition-colors hover:bg-white/20 disabled:opacity-30"
            >
              <ZoomOut className="h-5 w-5" />
            </button>
            <button
              onClick={() => {
                setScale(1);
                setPos({ x: 0, y: 0 });
              }}
              aria-label="Reset zoom"
              className="h-9 w-14 rounded-full text-xs font-medium tabular-nums transition-colors hover:bg-white/20"
            >
              {Math.round(scale * 100)}%
            </button>
            <button
              onClick={zoomIn}
              disabled={scale >= IMG_ZOOM_MAX}
              aria-label="Zoom in"
              className="grid h-9 w-9 place-items-center rounded-full transition-colors hover:bg-white/20 disabled:opacity-30"
            >
              <ZoomIn className="h-5 w-5" />
            </button>
          </div>

          <img
            src={src}
            alt={alt}
            draggable={false}
            className="max-w-full max-h-full object-contain select-none"
            style={{
              transform: `translate(${pos.x}px, ${pos.y}px) scale(${scale})`,
              transition: pan.current ? "none" : "transform 0.15s ease",
              cursor: scale > 1 ? (pan.current ? "grabbing" : "grab") : "auto",
              touchAction: "none",
            }}
            onClick={(e) => e.stopPropagation()}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
          />
        </div>
      )}
    </>
  );
}

/* ── Preview panel ──────────────────────────────────── */

function ResourcePreview({
  goalId,
  resourceId,
  onClose,
}: {
  goalId: string;
  resourceId: string | null;
  onClose: () => void;
}) {
  const updateResource = useSpira((s) => s.updateResource);
  const resource = useSpira((s) =>
    resourceId
      ? (s.goals
          .find((g) => g.id === goalId)
          ?.resources.find((r) => r.id === resourceId) ?? null)
      : null,
  );
  const isMobile = useIsMobile();
  const open = !!resource;

  const title = resource ? resourceDisplayName(resource) : "";

  const Body = resource && (
    <PreviewBody
      resource={resource}
      goalId={goalId}
      updateResource={updateResource}
      title={title}
      isMobile={isMobile}
      onClose={onClose}
    />
  );

  if (isMobile) {
    if (resource?.type === "note") {
      return (
        <Drawer open={open} onOpenChange={(nextOpen) => !nextOpen && onClose()}>
          <DrawerContent className="mt-0 h-[100svh] max-h-[100svh] rounded-none border-0 px-0 flex flex-col bg-surface">
            <MobileNoteBody
              title={resource.title}
              body={resource.body || ""}
              resourceId={resource.id}
              driveWebViewLink={resource.driveWebViewLink ?? null}
              onTitleChange={(v) =>
                updateResource(goalId, resource.id, { title: v })
              }
              onBodyChange={(html) =>
                updateResource(goalId, resource.id, { body: html })
              }
              onDocLinked={(link) =>
                updateResource(goalId, resource.id, { driveWebViewLink: link })
              }
              onClose={onClose}
            />
          </DrawerContent>
        </Drawer>
      );
    }

    // A PDF needs a DEFINITE drawer height so its flex-1 canvas area can fill (otherwise
    // the height collapses and the document shows as a thin strip). Other file types
    // (images, email) size to their content, capped at 92vh.
    const isPdf =
      resource?.type === "file" && resource.mime === "application/pdf";
    return (
      <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
        <DrawerContent
          className={cn(
            "px-0 pb-6 flex flex-col",
            isPdf ? "h-[92vh]" : "max-h-[92vh]",
          )}
        >
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return resource?.type === "email" ? (
    <>
      {open && <ResourceBackdrop onClose={onClose} />}
      <Sheet open={open} onOpenChange={(o) => !o && onClose()} modal={false}>
        <SheetContent
          side="right"
          overlay={false}
          onInteractOutside={(e) => e.preventDefault()}
          onPointerDownOutside={(e) => e.preventDefault()}
          className="w-full sm:max-w-md p-0 flex flex-col bg-surface border-l hairline shadow-2xl"
        >
          {Body}
        </SheetContent>
      </Sheet>
    </>
  ) : (
    <ResizableSheet open={open} onClose={onClose}>
      {Body}
    </ResizableSheet>
  );
}

function MobileNoteBody({
  title,
  body,
  resourceId,
  driveWebViewLink,
  onTitleChange,
  onBodyChange,
  onDocLinked,
  onClose,
}: {
  title: string;
  body: string;
  resourceId: string;
  driveWebViewLink?: string | null;
  onTitleChange: (value: string) => void;
  onBodyChange: (html: string) => void;
  onDocLinked: (link: string) => void;
  onClose: () => void;
}) {
  const { copied, run } = useCopied();

  const handleCopy = () => {
    // Keep the note's formatting when pasting into a rich editor (incl. another note).
    run(() => copyRichText(body, stripHtml(body)));
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="shrink-0 bg-surface px-5 pt-5 pb-2">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            Note
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-primary"
              aria-label="Copy as plain text"
              title={copied ? "Copied!" : "Copy as plain text"}
            >
              {copied ? (
                <Check className="h-4.5 w-4.5 text-green-600" />
              ) : (
                <Copy className="h-4.5 w-4.5" />
              )}
            </button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-primary"
                  aria-label="Download as…"
                  title="Download as…"
                >
                  <Download className="h-4.5 w-4.5" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => downloadNoteTxt(title, body)}>
                  Plain text (.txt)
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => downloadNoteDoc(title, body)}>
                  Word (.doc)
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => printNotePdf(title, body)}>
                  PDF (Save as PDF)
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={async () => {
                    try {
                      const link = await openInGoogleDocs(
                        resourceId,
                        body,
                        title,
                      );
                      if (link && link !== driveWebViewLink) onDocLinked(link);
                      toast.success(
                        driveWebViewLink
                          ? "Opening in Google Docs"
                          : "Created in Google Docs — opening it now",
                      );
                    } catch (e) {
                      toast.error(
                        e instanceof Error
                          ? e.message
                          : "Couldn't open the Google Doc",
                      );
                    }
                  }}
                >
                  {driveWebViewLink
                    ? "Open in Google Docs"
                    : "Create in Google Docs"}
                </DropdownMenuItem>
                {driveWebViewLink && (
                  <DropdownMenuItem
                    onClick={async () => {
                      try {
                        await syncGoogleDoc(resourceId, body, title);
                        toast.success("Google Doc updated from this note");
                      } catch (e) {
                        toast.error(
                          e instanceof Error
                            ? e.message
                            : "Couldn't update the Google Doc",
                        );
                      }
                    }}
                  >
                    Update Google Doc from note
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
              aria-label="Close note"
              title="Close note"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>
        <AutoTextarea
          value={title}
          onChange={onTitleChange}
          maxLength={FIELD_LIMITS.resourceLabel}
          maxLengthLabel="Note title"
          className="font-display text-2xl w-full"
          placeholder="Note title"
        />
      </div>
      <div className="min-h-0 flex flex-1 flex-col px-5 pt-2">
        <RichTextEditor
          value={body}
          onChange={onBodyChange}
          placeholder="Write your note here..."
        />
      </div>
    </div>
  );
}

const MIN_PANEL_WIDTH = 420;
const RESIZE_KEY = "spira:resource-panel-width";

/**
 * Partial backdrop for the resource panel: blocks the goal page while leaving
 * the AI chat (z-40) and the resource panel (z-50) interactive. Rendered at the
 * document root (portal) so no ancestor transform/blur can trap its stacking.
 */
function ResourceBackdrop({ onClose }: { onClose: () => void }) {
  if (typeof document === "undefined") return null;
  return createPortal(
    <div
      className="fixed inset-0 z-[35] bg-black/40 animate-in fade-in-0"
      onClick={onClose}
      aria-hidden
    />,
    document.body,
  );
}

export function ResizableSheet({
  open,
  onClose,
  children,
}: {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return 720;
    const stored = Number(window.localStorage.getItem(RESIZE_KEY));
    if (stored && stored >= MIN_PANEL_WIDTH) return stored;
    return Math.min(720, window.innerWidth - 80);
  });
  const draggingRef = useRef(false);
  const [isDragging, setIsDragging] = useState(false);
  const handleRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onResize = () => {
      setWidth((w) => Math.min(w, window.innerWidth));
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    setIsDragging(true);
    handleRef.current?.setAttribute("data-dragging", "true");
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";

    const onMove = (ev: PointerEvent) => {
      if (!draggingRef.current) return;
      const next = Math.max(
        MIN_PANEL_WIDTH,
        Math.min(window.innerWidth, window.innerWidth - ev.clientX),
      );
      setWidth(next);
    };
    const onUp = () => {
      draggingRef.current = false;
      setIsDragging(false);
      handleRef.current?.removeAttribute("data-dragging");
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.localStorage.setItem(RESIZE_KEY, String(width));
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  // Persist width whenever it changes (after release)
  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(RESIZE_KEY, String(width));
  }, [width]);

  return (
    <>
      {/* Backdrop blocks the goal page while keeping the AI chat (z-40) and the
          resource panel (z-50) interactive. Click it to close. */}
      {open && <ResourceBackdrop onClose={onClose} />}
      <Sheet open={open} onOpenChange={(o) => !o && onClose()} modal={false}>
        <SheetContent
          side="right"
          overlay={false}
          // We supply our own partial backdrop above. radix must not auto-close
          // on outside interaction (clicking the chat should keep it open).
          onInteractOutside={(e) => e.preventDefault()}
          onPointerDownOutside={(e) => e.preventDefault()}
          className={cn(
            "p-0 flex flex-col bg-surface border-l hairline !max-w-none shadow-2xl",
            isDragging && "[&_iframe]:pointer-events-none",
          )}
          style={{ width: `${width}px` }}
        >
          <div
            ref={handleRef}
            onPointerDown={startDrag}
            className="resize-handle"
            role="separator"
            aria-orientation="vertical"
            aria-label="Resize panel"
          />
          {children}
        </SheetContent>
      </Sheet>
    </>
  );
}

export function NewResourceSheet({
  goalId,
  open,
  onOpenChange,
}: {
  goalId: string;
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const isMobile = useIsMobile();
  const handleDone = () => onOpenChange(false);
  if (isMobile)
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="mt-0 px-0 h-[92vh] max-h-[92vh] flex flex-col bg-surface">
          {open && <Form goalId={goalId} onDone={handleDone} />}
        </DrawerContent>
      </Drawer>
    );
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
        {open && <Form goalId={goalId} onDone={handleDone} />}
      </SheetContent>
    </Sheet>
  );
}

function Form({
  goalId,
  initialResource,
  onDone,
}: {
  goalId: string;
  initialResource?: Resource;
  onDone: () => void;
}) {
  const addResource = useSpira((s) => s.addResource);
  const updateResource = useSpira((s) => s.updateResource);
  const [type, setType] = useState<Resource["type"]>(
    initialResource?.type || "note",
  );
  const submittedRef = useRef(false);
  const [title, setTitle] = useState(
    initialResource && initialResource.type !== "email"
      ? initialResource.title
      : "",
  );
  const [body, setBody] = useState(
    initialResource?.type === "note" ? initialResource.body : "",
  );
  const [url, setUrl] = useState(
    initialResource?.type === "link" ? initialResource.url : "",
  );
  const [fileData, setFileData] = useState<{
    name: string;
    mime: string;
    dataUrl: string;
  } | null>(
    initialResource?.type === "file"
      ? {
          name: initialResource.title,
          mime: initialResource.mime,
          dataUrl: initialResource.dataUrl,
        }
      : null,
  );
  const [name, setName] = useState(
    initialResource?.type === "email" ? initialResource.name || "" : "",
  );
  const [role, setRole] = useState(
    initialResource?.type === "email" ? initialResource.role || "" : "",
  );
  const [email, setEmail] = useState(
    initialResource?.type === "email" ? initialResource.email || "" : "",
  );
  const [phone, setPhone] = useState(
    initialResource?.type === "email" ? initialResource.phone || "" : "",
  );
  const [fileError, setFileError] = useState("");
  const fileInputId = `resource-file-${goalId}-${initialResource?.id ?? "new"}`;

  const onFile = (f: File) => {
    if (!validResourceFileType(f.type)) {
      setFileData(null);
      setFileError("Choose an image or PDF.");
      return;
    }
    if (f.size > MAX_RESOURCE_FILE_BYTES) {
      setFileData(null);
      setFileError("File must be 5 MB or smaller.");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      setFileError("");
      setFileData({
        name: f.name,
        mime: f.type,
        dataUrl: String(reader.result),
      });
    };
    reader.readAsDataURL(f);
  };

  const trimmedUrl = url.trim();
  const trimmedEmail = email.trim();
  const linkError =
    type === "link" && trimmedUrl && !validResourceUrl(trimmedUrl)
      ? "Enter a valid http or https URL."
      : type === "link"
        ? (lengthError(trimmedUrl, FIELD_LIMITS.resourceUrl, "URL") ?? "")
        : "";
  const emailError =
    type === "email" && trimmedEmail && !validResourceEmail(trimmedEmail)
      ? "Enter a valid email address."
      : type === "email"
        ? (lengthError(trimmedEmail, FIELD_LIMITS.resourceLabel, "Email") ?? "")
        : "";
  const bodyError =
    type === "note"
      ? (lengthError(body, FIELD_LIMITS.resourceNoteBody, "Note") ?? "")
      : "";
  const roleError =
    type === "email"
      ? (lengthError(role.trim(), FIELD_LIMITS.resourceLabel, "Role") ?? "")
      : "";
  const phoneError =
    type === "email"
      ? (lengthError(phone.trim(), FIELD_LIMITS.resourcePhone, "Phone") ?? "")
      : "";
  const labelValue =
    type === "email"
      ? name.trim() || trimmedEmail
      : title.trim() ||
        (type === "link" && trimmedUrl
          ? titleFromUrl(trimmedUrl)
          : type === "file"
            ? fileData?.name || ""
            : "");
  const labelError =
    labelValue.trim().length > MAX_RESOURCE_LABEL_LENGTH
      ? `${type === "email" ? "Name" : "Title"} must be ${MAX_RESOURCE_LABEL_LENGTH} characters or fewer.`
      : "";

  const canSubmit = labelError
    ? false
    : type === "note"
      ? !!title.trim() && !bodyError
      : type === "link"
        ? !!trimmedUrl && !linkError
        : type === "file"
          ? !!fileData && !fileError
          : !!trimmedEmail && !emailError && !roleError && !phoneError;

  const submit = () => {
    if (submittedRef.current) return;
    if (!canSubmit) return;
    let payload: Partial<Resource> | null = null;
    if (type === "note") {
      payload = { type: "note", title: title.trim() || "Untitled note", body };
    } else if (type === "link") {
      if (!url.trim()) return;
      const cleanUrl = url.trim();
      payload = {
        type: "link",
        title: title.trim() || titleFromUrl(cleanUrl),
        url: cleanUrl,
      };
    } else if (type === "file") {
      if (!fileData) return;
      payload = {
        type: "file",
        title: title.trim() || fileData.name,
        mime: fileData.mime,
        // File contents load lazily, so an existing resource being edited may have an empty
        // dataUrl here. Only send it when we actually have bytes (i.e. the user picked a new
        // file) — otherwise omit it so the stored file is left untouched rather than blanked.
        ...(fileData.dataUrl ? { dataUrl: fileData.dataUrl } : {}),
      };
    } else {
      if (!email.trim()) return;
      const cleanEmail = email.trim();
      payload = {
        type: "email",
        name: name.trim() || cleanEmail,
        role,
        email: cleanEmail,
        phone,
      };
    }
    submittedRef.current = true;
    onDone();
    if (initialResource) {
      setTimeout(
        () => updateResource(goalId, initialResource.id, payload!),
        50,
      );
    } else {
      setTimeout(() => addResource(goalId, payload as ResourceInput), 50);
    }
  };

  const isMobile = useIsMobile();

  return (
    <>
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 z-10 bg-surface">
        <h2 className="font-sans font-bold text-lg">
          {initialResource ? "Edit resource" : "Add a resource"}
        </h2>
        <button
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="px-6 pt-2 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0">
        {!initialResource && (
          <div>
            <label className="text-sm font-semibold block mb-2">
              Type <span className="text-destructive">*</span>
            </label>
            <div className="grid grid-cols-2 gap-2">
              {(["note", "link", "file", "email"] as const).map((t) => {
                const Icon = typeMeta[t].icon;
                return (
                  <button
                    key={t}
                    onClick={() => setType(t)}
                    className={cn(
                      "flex items-center gap-2.5 px-3 py-3 rounded-md border-2 text-sm font-semibold capitalize transition-colors text-left",
                      type === t
                        ? "bg-primary-soft border-primary text-primary"
                        : "bg-surface border-border hover:border-border-strong",
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    {typeMeta[t].label}
                  </button>
                );
              })}
            </div>
          </div>
        )}
        {type !== "email" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">
              Title{" "}
              {type === "note" && <span className="text-destructive">*</span>}
            </label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} />
            {labelError && (
              <p
                className="mt-2 text-xs font-medium text-destructive"
                role="alert"
              >
                {labelError}
              </p>
            )}
          </div>
        )}
        {type === "note" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">Note</label>
            {/* `embedded` renders it as a bordered field (matching the inputs) on
                mobile, with the formatting toolbar tucked behind "Format". */}
            <RichTextEditor
              value={body}
              onChange={(html) => setBody(html)}
              placeholder="Write your note here..."
              embedded
            />
            {bodyError && (
              <p
                className="mt-2 text-xs font-medium text-destructive"
                role="alert"
              >
                {bodyError}
              </p>
            )}
          </div>
        )}
        {type === "link" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">
              URL <span className="text-destructive">*</span>
            </label>
            <Input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://"
            />
            {linkError && (
              <p
                className="mt-2 text-xs font-medium text-destructive"
                role="alert"
              >
                {linkError}
              </p>
            )}
          </div>
        )}
        {type === "file" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">
              File <span className="text-destructive">*</span>
            </label>
            <input
              id={fileInputId}
              type="file"
              accept="image/*,application/pdf"
              onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
              className="sr-only"
            />
            <label
              htmlFor={fileInputId}
              className="flex min-h-11 cursor-pointer items-center justify-between gap-3 rounded-md border border-input bg-surface px-3.5 py-2 text-base text-foreground transition-colors hover:border-primary hover:bg-secondary/30 focus-within:border-primary"
            >
              <span className="min-w-0 break-words text-sm font-medium text-foreground">
                {fileData?.name || "Choose an image or PDF"}
              </span>
              <span className="shrink-0 rounded-md bg-primary-soft px-3 py-1.5 text-sm font-semibold text-primary">
                Browse
              </span>
            </label>
            {fileError && (
              <p
                className="mt-2 text-xs font-medium text-destructive"
                role="alert"
              >
                {fileError}
              </p>
            )}
          </div>
        )}
        {type === "email" && (
          <div className="space-y-4">
            <div>
              <label className="text-sm font-semibold block mb-1.5">Name</label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Optional"
              />
              {labelError && (
                <p
                  className="mt-2 text-xs font-medium text-destructive"
                  role="alert"
                >
                  {labelError}
                </p>
              )}
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">
                Email <span className="text-destructive">*</span>
              </label>
              <Input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                placeholder="name@example.com"
              />
              {emailError && (
                <p
                  className="mt-2 text-xs font-medium text-destructive"
                  role="alert"
                >
                  {emailError}
                </p>
              )}
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Role</label>
              <Input
                value={role}
                onChange={(e) => setRole(e.target.value)}
                placeholder="Optional"
              />
              {roleError && (
                <p
                  className="mt-2 text-xs font-medium text-destructive"
                  role="alert"
                >
                  {roleError}
                </p>
              )}
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">
                Phone
              </label>
              <Input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="Optional"
              />
              {phoneError && (
                <p
                  className="mt-2 text-xs font-medium text-destructive"
                  role="alert"
                >
                  {phoneError}
                </p>
              )}
            </div>
          </div>
        )}
      </div>
      <div
        className="shrink-0 bg-surface px-6 pt-3 flex gap-3"
        style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
      >
        <button
          onClick={onDone}
          className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
        >
          Cancel
        </button>
        <button
          onClick={submit}
          disabled={!canSubmit}
          className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 disabled:opacity-40 transition-colors"
        >
          {initialResource ? "Save changes" : "Add resource"}
        </button>
      </div>
    </>
  );
}
