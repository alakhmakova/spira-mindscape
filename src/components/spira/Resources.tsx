import { useEffect, useMemo, useRef, useState } from "react";
import {
  FileText,
  LinkIcon,
  Paperclip,
  Mail,
  Trash2,
  ExternalLink,
  Download,
  Copy,
  Check,
  ArrowUpRight,
  Pencil,
  ZoomIn,
  X,
  ChevronRight,
} from "lucide-react";
import type { Goal, Resource } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
import { AutoTextarea } from "@/components/spira/Inline";
import { RichTextEditor } from "@/components/spira/RichTextEditor";

const typeMeta = {
  note: { icon: FileText, label: "Note" },
  link: { icon: LinkIcon, label: "Link" },
  file: { icon: Paperclip, label: "File" },
  email: { icon: Mail, label: "Email" },
} as const;

function titleFromUrl(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

function nameFromEmail(email: string) {
  const local = email.split("@")[0]?.trim();
  if (!local) return "Email";
  return local
    .replace(/[._-]+/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function resourceDisplayName(resource: Resource) {
  if (resource.type === "note") return resource.title.trim() || "Untitled note";
  if (resource.type === "link") return resource.title.trim() || titleFromUrl(resource.url);
  if (resource.type === "file") return resource.title.trim() || "Untitled file";
  return resource.name?.trim() || (resource.email ? nameFromEmail(resource.email) : "Email");
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
  setTimeout(() => { document.body.removeChild(a); URL.revokeObjectURL(url); }, 100);
}

async function copyPlainText(text: string) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (err) {
      console.warn("clipboard.writeText failed", err);
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
    console.error("Fallback copy failed", err);
  }
  document.body.removeChild(textArea);
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
    } catch {}
  }
  
  if (navigator.share && navigator.canShare) {
    try {
      const blob = dataUrlToBlob(dataUrl);
      const file = new File([blob], "image.png", { type: blob.type });
      if (navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file] });
        return;
      }
    } catch {}
  }

  // Ultimate fallback if clipboard and share fail (e.g. insecure HTTP)
  alert("Copying or sharing images on this browser requires a secure HTTPS connection.");
}

function toPngBlob(dataUrl: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.width;
      canvas.height = img.height;
      canvas.getContext("2d")!.drawImage(img, 0, 0);
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("toBlob failed"))), "image/png");
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
      console.error("Copy/download action failed:", err);
    }
  };
  return { copied, run } as const;
}

export function ResourcesList({ goal }: { goal: Goal }) {
  const removeResource = useSpira((s) => s.removeResource);
  const [previewId, setPreviewId] = useState<string | null>(null);

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
            onOpen={() => {
              if (r.type === "link") window.open(r.url, "_blank");
              else setPreviewId(r.id);
            }}
            onRemove={() => removeResource(goal.id, r.id)}
          />
        ))}
      </div>

      <ResourcePreview goalId={goal.id} resourceId={previewId} onClose={() => setPreviewId(null)} />
    </>
  );
}

/* ── Card with inline actions ─────────────────────── */

function ResourceCard({
  resource: r,
  onOpen,
  onRemove,
}: {
  resource: Resource;
  onOpen: () => void;
  onRemove: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const Icon = typeMeta[r.type].icon;
  const { copied, run } = useCopied();

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (r.type === "note") {
      run(() => copyPlainText(stripHtml(r.body)));
    } else if (r.type === "link") {
      run(() => copyPlainText(r.url));
    } else if (r.type === "file" && r.mime.startsWith("image/")) {
      run(() => copyImageToClipboard(r.dataUrl));
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
      downloadDataUrl(r.dataUrl, r.title);
    }
  };

  const canCopy = r.type === "note" || r.type === "link" || (r.type === "file" && r.mime.startsWith("image/")) || (r.type === "email" && !!r.email);
  const canDownload = r.type === "note" || r.type === "file";

  const typeColors: Record<string, { bg: string; text: string; border: string; icon: string }> = {
    note: { bg: "bg-[#f0f9ff]", text: "text-[#0c69a3]", border: "border-[#bae2fd]", icon: "text-[#0c69a3]" },
    link: { bg: "bg-[#f0fdf4]", text: "text-[#15803d]", border: "border-[#b7e4c7]", icon: "text-[#15803d]" },
    file: { bg: "bg-[#fef3c7]", text: "text-[#92400e]", border: "border-[#fde68a]", icon: "text-[#92400e]" },
    email: { bg: "bg-[#faf5ff]", text: "text-[#7c3aed]", border: "border-[#e9d5ff]", icon: "text-[#7c3aed]" },
  };

  const colors = typeColors[r.type] || typeColors.note;

  return (
    <div
      className={cn(
        "group inline-flex items-center rounded-lg border bg-white transition-all duration-200 overflow-hidden",
        expanded
          ? "border-border/60 shadow-[0_2px_12px_-2px_rgba(0,0,0,0.06)]"
          : "border-border/40 hover:border-border/60 hover:shadow-[0_1px_6px_-1px_rgba(0,0,0,0.04)]"
      )}
    >
      <button
        onClick={onOpen}
        className="flex items-center gap-1.5 pl-2 pr-1 py-1.5 h-9 transition-colors hover:bg-secondary/20"
      >
        <div className={cn("grid h-5 w-5 place-items-center rounded-md shrink-0", colors.bg)}>
          <Icon className={cn("h-3 w-3", colors.icon)} />
        </div>
        <span className="text-sm font-medium text-foreground whitespace-nowrap max-w-[140px] truncate">
          {resourceDisplayName(r)}
        </span>
      </button>

      {!expanded ? (
        <button
          onClick={(e) => { e.stopPropagation(); setExpanded(true); }}
          className="flex h-full items-center justify-center px-1.5 text-muted-foreground/50 transition-colors hover:text-muted-foreground hover:bg-secondary/30"
        >
          <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
        </button>
      ) : (
        <div className="flex items-center gap-0.5 pr-1 pl-0.5">
          <button
            onClick={(e) => { e.stopPropagation(); setExpanded(false); }}
            className="flex h-6 w-6 items-center justify-center rounded-md text-muted-foreground/60 transition-colors hover:text-muted-foreground hover:bg-secondary/50"
          >
            <ChevronRight className="h-3 w-3 rotate-180" />
          </button>

          <div className="w-px h-3.5 bg-border/60" />

          {r.type === "email" ? (
             <button
               onClick={(e) => { e.stopPropagation(); onOpen(); }}
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
                  {copied ? <Check className="h-3 w-3 text-green-600" /> : <Copy className="h-3 w-3" />}
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
            onClick={(e) => { e.stopPropagation(); onRemove(); }}
            className="grid h-6 w-6 place-items-center rounded-md text-muted-foreground/60 hover:bg-destructive-soft hover:text-destructive transition-colors"
            title="Remove"
          >
            <Trash2 className="h-3 w-3" />
          </button>
        </div>
      )}
    </div>
  );
}

function CopyField({ label, value, actionIcon, onAction, actionTitle }: { label: string; value: string; actionIcon?: React.ReactNode; onAction?: () => void; actionTitle?: string }) {
  const { copied, run } = useCopied();
  return (
    <div className="rounded-md border border-border bg-surface px-4 py-3">
      <label className="block text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{label}</label>
      <div className="mt-1.5 flex items-center gap-2">
        <div className="flex-1 min-w-0 break-words text-sm font-semibold text-foreground">{value}</div>
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
          {copied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
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
  updateResource: (goalId: string, resourceId: string, patch: Partial<Resource>) => void;
  title: string;
  isMobile: boolean;
  onClose: () => void;
}) {
  const { copied, run } = useCopied();
  const [isEditingEmail, setIsEditingEmail] = useState(false);

  if (isEditingEmail) {
    return <Form goalId={goalId} initialResource={resource} onDone={() => setIsEditingEmail(false)} />;
  }

  const isImage = resource.type === "file" && resource.mime.startsWith("image/");
  const canCopy =
    resource.type === "note" ||
    resource.type === "link" ||
    isImage;
  const canDownload = resource.type === "note" || resource.type === "file";
  const copyLabel = isImage && isMobile ? "Share" : resource.type === "note" ? "Copy as plain text" : resource.type === "link" ? "Copy URL" : "Copy image";

  const handleCopy = () => {
    if (resource.type === "note") {
      run(() => copyPlainText(stripHtml(resource.body)));
    } else if (resource.type === "link") {
      run(() => copyPlainText(resource.url));
    } else if (resource.type === "file" && resource.mime.startsWith("image/")) {
      run(() => copyImageToClipboard(resource.dataUrl, resource.title));
    }
  };

  const handleDownload = () => {
    if (resource.type === "note") {
      const text = stripHtml(resource.body);
      const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
      downloadBlob(blob, `${resource.title || "note"}.txt`);
    } else if (resource.type === "file") {
      downloadDataUrl(resource.dataUrl, resource.title);
    }
  };

  return (
    <>
      <div className="px-7 py-5 flex items-center justify-between sticky top-0 z-10 bg-primary text-white">
        <div className="flex-1 min-w-0 pr-2">
          {resource.type === "note" ? (
            <AutoTextarea
              value={resource.title}
              onChange={(v) => updateResource(goalId, resource.id, { title: v })}
              className="font-display text-2xl w-full bg-transparent border-none focus:outline-none resize-none p-0 !text-white placeholder:text-white/50"
              placeholder="Note title"
            />
          ) : (
            <h2 className="font-sans font-bold text-lg truncate pr-4 !text-white" style={{ color: "white" }}>{title}</h2>
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
              {copied ? <Check className="h-4 w-4 text-green-300" /> : <Copy className="h-4 w-4" />}
            </button>
          )}
          {canDownload && (
            <button
              onClick={handleDownload}
              className="h-8 w-8 grid place-items-center rounded-md text-white/90 hover:bg-white/20 hover:text-white transition-colors"
              aria-label="Download"
              title="Download"
            >
              <Download className="h-4 w-4" />
            </button>
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
      <div className={cn(
        "px-7 py-6 overflow-y-auto flex-1 min-h-0",
        resource.type === "file" && resource.mime === "application/pdf"
          ? "flex flex-col gap-3 overflow-hidden"
          : "flex flex-col",
      )}>
        {resource.type === "note" && (
          <div className="flex-1 min-h-0 relative">
            <RichTextEditor
              value={resource.body || ""}
              onChange={(html) => updateResource(goalId, resource.id, { body: html })}
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
            {resource.mime.startsWith("image/") && (
              <ZoomableImage src={resource.dataUrl} alt={resource.title} />
            )}
            {resource.mime === "application/pdf" && (
              <PdfViewer dataUrl={resource.dataUrl} title={resource.title} />
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
            {resource.phone && <CopyField label="Phone" value={resource.phone} />}
          </div>
        )}
      </div>
    </>
  );
}

/* ── PDF viewer (blob URL for iframe) ────────────── */

function PdfViewer({ dataUrl, title }: { dataUrl: string; title: string }) {
  const blobUrl = useMemo(() => {
    try {
      const byteString = atob(dataUrl.split(",")[1]);
      const mimeString = dataUrl.split(",")[0].split(":")[1].split(";")[0];
      const ab = new ArrayBuffer(byteString.length);
      const ia = new Uint8Array(ab);
      for (let i = 0; i < byteString.length; i++) {
        ia[i] = byteString.charCodeAt(i);
      }
      const blob = new Blob([ab], { type: mimeString });
      return URL.createObjectURL(blob);
    } catch {
      return dataUrl;
    }
  }, [dataUrl]);

  useEffect(() => {
    return () => {
      if (blobUrl !== dataUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [blobUrl, dataUrl]);

  return (
    <div className="relative flex-1 min-h-0">
      <iframe
        src={blobUrl}
        className="absolute inset-0 w-full h-full rounded-md border hairline bg-secondary"
        title={title}
      />
    </div>
  );
}

/* ── Zoomable image (tap to fullscreen on mobile) ── */

function ZoomableImage({ src, alt }: { src: string; alt: string }) {
  const [zoomed, setZoomed] = useState(false);

  return (
    <>
      <div className="relative cursor-zoom-in group" onClick={() => setZoomed(true)}>
        <img src={src} alt={alt} className="w-full rounded-md border hairline" />
        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/5 transition-colors rounded-md flex items-center justify-center">
          <div className="opacity-0 group-hover:opacity-100 transition-opacity bg-black/60 text-white rounded-full p-2">
            <ZoomIn className="h-5 w-5" />
          </div>
        </div>
      </div>
      {zoomed && (
        <div
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4 cursor-zoom-out"
          onClick={() => setZoomed(false)}
        >
          <button
            onClick={() => setZoomed(false)}
            className="absolute top-4 right-4 z-10 h-10 w-10 grid place-items-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-colors"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
          <img
            src={src}
            alt={alt}
            className="max-w-full max-h-full object-contain touch-pinch-zoom"
            style={{ touchAction: "pinch-zoom" }}
            onClick={(e) => e.stopPropagation()}
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
      ? s.goals.find((g) => g.id === goalId)?.resources.find((r) => r.id === resourceId) ?? null
      : null,
  );
  const isMobile = useIsMobile();
  const open = !!resource;

  const title =
    resource ? resourceDisplayName(resource) : "";

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
            onTitleChange={(v) => updateResource(goalId, resource.id, { title: v })}
            onBodyChange={(html) => updateResource(goalId, resource.id, { body: html })}
            onClose={onClose}
          />
          </DrawerContent>
        </Drawer>
      );
    }

    return (
      <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return (
    resource?.type === "email" ? (
      <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
        <SheetContent side="right" className="w-full sm:max-w-md p-0 flex flex-col bg-surface border-l hairline">
          {Body}
        </SheetContent>
      </Sheet>
    ) : (
      <ResizableSheet open={open} onClose={onClose}>
        {Body}
      </ResizableSheet>
    )
  );
}

function MobileNoteBody({
  title,
  body,
  onTitleChange,
  onBodyChange,
  onClose,
}: {
  title: string;
  body: string;
  onTitleChange: (value: string) => void;
  onBodyChange: (html: string) => void;
  onClose: () => void;
}) {
  const { copied, run } = useCopied();

  const handleCopy = () => {
    run(() => copyPlainText(stripHtml(body)));
  };

  const handleDownload = () => {
    const text = stripHtml(body);
    const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
    downloadBlob(blob, `${title || "note"}.txt`);
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
              {copied ? <Check className="h-4.5 w-4.5 text-green-600" /> : <Copy className="h-4.5 w-4.5" />}
            </button>
            <button
              type="button"
              onClick={handleDownload}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-primary"
              aria-label="Download as .txt"
              title="Download as .txt"
            >
              <Download className="h-4.5 w-4.5" />
            </button>
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
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent
        side="right"
        className={cn(
          "p-0 flex flex-col bg-surface border-l hairline !max-w-none",
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
        <DrawerContent className="px-0 pb-safe max-h-[92vh] flex flex-col bg-surface">
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

function Form({ goalId, initialResource, onDone }: { goalId: string; initialResource?: Resource; onDone: () => void }) {
  const addResource = useSpira((s) => s.addResource);
  const updateResource = useSpira((s) => s.updateResource);
  const [type, setType] = useState<Resource["type"]>(initialResource?.type || "note");
  const submittedRef = useRef(false);
  const [title, setTitle] = useState(initialResource && initialResource.type !== "email" ? initialResource.title : "");
  const [body, setBody] = useState(initialResource?.type === "note" ? initialResource.body : "");
  const [url, setUrl] = useState(initialResource?.type === "link" ? initialResource.url : "");
  const [fileData, setFileData] = useState<{ name: string; mime: string; dataUrl: string } | null>(
    initialResource?.type === "file" ? { name: initialResource.title, mime: initialResource.mime, dataUrl: initialResource.dataUrl } : null,
  );
  const [name, setName] = useState(initialResource?.type === "email" ? (initialResource.name || "") : "");
  const [role, setRole] = useState(initialResource?.type === "email" ? (initialResource.role || "") : "");
  const [email, setEmail] = useState(initialResource?.type === "email" ? (initialResource.email || "") : "");
  const [phone, setPhone] = useState(initialResource?.type === "email" ? (initialResource.phone || "") : "");
  const fileInputId = `resource-file-${goalId}-${initialResource?.id ?? "new"}`;

  const onFile = (f: File) => {
    const reader = new FileReader();
    reader.onload = () =>
      setFileData({ name: f.name, mime: f.type, dataUrl: String(reader.result) });
    reader.readAsDataURL(f);
  };

  const submit = () => {
    if (submittedRef.current) return;
    let payload: Partial<Resource> | null = null;
    if (type === "note") {
      payload = { type: "note", title: title.trim() || "Untitled note", body };
    } else if (type === "link") {
      if (!url.trim()) return;
      const cleanUrl = url.trim();
      payload = { type: "link", title: title.trim() || titleFromUrl(cleanUrl), url: cleanUrl };
    } else if (type === "file") {
      if (!fileData) return;
      payload = { type: "file", title: title.trim() || fileData.name, mime: fileData.mime, dataUrl: fileData.dataUrl };
    } else {
      if (!email.trim()) return;
      const cleanEmail = email.trim();
      payload = { type: "email", name: name.trim() || nameFromEmail(cleanEmail), role, email: cleanEmail, phone };
    }
    submittedRef.current = true;
    onDone();
    if (initialResource) {
      setTimeout(() => updateResource(goalId, initialResource.id, payload!), 50);
    } else {
      setTimeout(() => addResource(goalId, payload as Omit<Resource, "id">), 50);
    }
  };

  const isMobile = useIsMobile();

  return (
    <>
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 z-10 bg-surface">
        <h2 className="font-sans font-bold text-lg">{initialResource ? "Edit resource" : "Add a resource"}</h2>
        <button onClick={onDone} className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary" aria-label="Close">
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="px-7 pt-2 pb-6 space-y-6 overflow-y-auto flex-1 min-h-0">
        {!initialResource && (
          <div>
            <label className="text-sm font-semibold block mb-2">Type <span className="text-destructive">*</span></label>
            <div className="grid grid-cols-2 gap-2">
              {(["note", "link", "file", "email"] as const).map((t) => {
                const Icon = typeMeta[t].icon;
                return (
                  <button key={t} onClick={() => setType(t)} className={cn("flex items-center gap-2.5 px-3 py-3 rounded-md border-2 text-sm font-semibold capitalize transition-colors text-left", type === t ? "bg-primary-soft border-primary text-primary" : "bg-surface border-border hover:border-border-strong")}>
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
            <label className="text-sm font-semibold block mb-1.5">Title {type !== "file" && <span className="text-destructive">*</span>}</label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} autoFocus />
          </div>
        )}
        {type === "note" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">Note</label>
            <Textarea value={body} onChange={(e) => setBody(e.target.value)} className="min-h-32" />
          </div>
        )}
        {type === "link" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">URL <span className="text-destructive">*</span></label>
            <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://" />
          </div>
        )}
        {type === "file" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">File</label>
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
          </div>
        )}
        {type === "email" && (
          <div className="space-y-4">
            <div>
              <label className="text-sm font-semibold block mb-1.5">Name</label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Optional" />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Email <span className="text-destructive">*</span></label>
              <Input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="name@example.com" autoFocus />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Role</label>
              <Input value={role} onChange={(e) => setRole(e.target.value)} placeholder="Optional" />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Phone</label>
              <Input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Optional" />
            </div>
          </div>
        )}
      </div>
      <div className="px-7 py-4 flex items-center justify-end gap-3 bg-surface">
        <button onClick={onDone} className="h-11 px-5 rounded-md border-2 border-border text-foreground font-semibold text-sm hover:bg-secondary transition-colors">Cancel</button>
        <button onClick={submit} className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90">{initialResource ? "Save changes" : "Add resource"}</button>
      </div>
    </>
  );
}
