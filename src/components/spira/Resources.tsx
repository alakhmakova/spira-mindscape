import { useEffect, useRef, useState } from "react";
import {
  FileText,
  LinkIcon,
  Paperclip,
  User as UserIcon,
  Trash2,
  ExternalLink,
  Download,
  X,
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
  contact: { icon: UserIcon, label: "Contact" },
} as const;

export function ResourcesList({ goal }: { goal: Goal }) {
  const removeResource = useSpira((s) => s.removeResource);
  const [previewId, setPreviewId] = useState<string | null>(null);

  if (goal.resources.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        Capture notes, links, files, and contacts that support this goal.
      </p>
    );
  }

  return (
    <>
      <ul className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {goal.resources.map((r) => {
          const Icon = typeMeta[r.type].icon;
          return (
            <li
              key={r.id}
              className="surface-card p-4 flex items-start gap-3 group hover:border-primary/40 hover:shadow-[var(--shadow-soft)] transition-all cursor-pointer"
              onClick={() => {
                if (r.type === "link") window.open(r.url, "_blank");
                else setPreviewId(r.id);
              }}
            >
              <div className="h-9 w-9 rounded-md bg-primary-soft border border-primary/20 grid place-items-center shrink-0 text-primary">
                <Icon className="h-4 w-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
                  {typeMeta[r.type].label}
                </div>
                <div className="text-sm font-semibold truncate text-foreground">
                  {r.type === "contact" ? r.name : r.title}
                </div>
                {r.type === "link" && (
                  <div className="text-xs text-muted-foreground truncate">{r.url}</div>
                )}
                {r.type === "contact" && (r.email || r.phone) && (
                  <div className="text-xs text-muted-foreground truncate">
                    {r.email ?? r.phone}
                  </div>
                )}
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  removeResource(goal.id, r.id);
                }}
                className="text-muted-foreground hover:text-destructive p-1 rounded hover:bg-secondary"
                aria-label="Remove resource"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </li>
          );
        })}
      </ul>

      <ResourcePreview goalId={goal.id} resourceId={previewId} onClose={() => setPreviewId(null)} />
    </>
  );
}

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
    resource?.type === "contact" ? resource.name : (resource as any)?.title;

  const Body = resource && (
    <>
      <div className="px-7 py-5 border-b hairline flex items-center justify-between sticky top-0 bg-surface z-10">
        {resource.type === "note" ? (
          <AutoTextarea
            value={resource.title}
            onChange={(v) => updateResource(goalId, resource.id, { title: v })}
            className="font-display text-2xl w-full pr-4"
            placeholder="Note title"
          />
        ) : (
          <h2 className="font-display text-2xl truncate">{title}</h2>
        )}
        {!(isMobile && resource.type === "note") && (
          <button
            onClick={onClose}
            className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
      <div className="px-7 py-6 overflow-y-auto flex-1 space-y-3">
        {resource.type === "note" && (
          <RichTextEditor
            value={resource.body || ""}
            onChange={(html) => updateResource(goalId, resource.id, { body: html })}
            placeholder="Write your note here..."
          />
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
          <div className="space-y-3">
            {resource.mime.startsWith("image/") && (
              <img
                src={resource.dataUrl}
                alt={resource.title}
                className="w-full rounded-md border hairline"
              />
            )}
            {resource.mime === "application/pdf" && (
              <iframe
                src={resource.dataUrl}
                className="w-full h-[60vh] rounded-md border hairline bg-secondary"
                title={resource.title}
              />
            )}
            <a
              href={resource.dataUrl}
              download={resource.title}
              className="inline-flex items-center gap-2 link-action text-sm font-semibold"
            >
              <Download className="h-4 w-4" /> Download
            </a>
          </div>
        )}
        {resource.type === "contact" && (
          <div className="surface-card p-5 space-y-1.5">
            <div className="font-display text-2xl">{resource.name}</div>
            {resource.role && <div className="text-sm text-muted-foreground">{resource.role}</div>}
            {resource.email && <div className="text-sm">{resource.email}</div>}
            {resource.phone && <div className="text-sm num">{resource.phone}</div>}
          </div>
        )}
      </div>
    </>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return (
    <ResizableSheet open={open} onClose={onClose}>
      {Body}
    </ResizableSheet>
  );
}

const MIN_PANEL_WIDTH = 420;
const RESIZE_KEY = "spira:resource-panel-width";

function ResizableSheet({
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
        className="p-0 flex flex-col bg-surface border-l hairline !max-w-none"
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
  const Body = <Form goalId={goalId} onDone={() => onOpenChange(false)} />;
  if (isMobile)
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">
          {Body}
        </DrawerContent>
      </Drawer>
    );
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
        {Body}
      </SheetContent>
    </Sheet>
  );
}

function Form({ goalId, onDone }: { goalId: string; onDone: () => void }) {
  const addResource = useSpira((s) => s.addResource);
  const [type, setType] = useState<Resource["type"]>("note");

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [url, setUrl] = useState("");
  const [fileData, setFileData] = useState<{ name: string; mime: string; dataUrl: string } | null>(
    null,
  );
  const [name, setName] = useState("");
  const [role, setRole] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");

  const onFile = (f: File) => {
    const reader = new FileReader();
    reader.onload = () =>
      setFileData({ name: f.name, mime: f.type, dataUrl: String(reader.result) });
    reader.readAsDataURL(f);
  };

  const submit = () => {
    if (type === "note") {
      if (!title.trim()) return;
      addResource(goalId, { type: "note", title: title.trim(), body } as Omit<Resource, "id">);
    } else if (type === "link") {
      if (!url.trim()) return;
      addResource(goalId, {
        type: "link",
        title: title.trim() || url,
        url: url.trim(),
      } as Omit<Resource, "id">);
    } else if (type === "file") {
      if (!fileData) return;
      addResource(goalId, {
        type: "file",
        title: title.trim() || fileData.name,
        mime: fileData.mime,
        dataUrl: fileData.dataUrl,
      } as Omit<Resource, "id">);
    } else {
      if (!name.trim()) return;
      addResource(goalId, {
        type: "contact",
        name: name.trim(),
        role,
        email,
        phone,
      } as Omit<Resource, "id">);
    }
    onDone();
  };

  return (
    <>
      <div className="px-7 py-5 border-b hairline flex items-center justify-between sticky top-0 bg-surface z-10">
        <h2 className="font-sans font-bold text-lg">Add a resource</h2>
        <button
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="px-7 py-6 space-y-6 overflow-y-auto flex-1">
        <div>
          <label className="text-sm font-semibold block mb-2">
            Type <span className="text-destructive">*</span>
          </label>
          <div className="grid grid-cols-2 gap-2">
            {(["note", "link", "file", "contact"] as const).map((t) => {
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

        {type !== "contact" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">
              Title {type !== "file" && <span className="text-destructive">*</span>}
            </label>
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
              autoFocus
            />
          </div>
        )}

        {type === "note" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">Note</label>
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className="bg-surface border-2 border-border focus-visible:border-primary min-h-32"
            />
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
              className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
            />
          </div>
        )}
        {type === "file" && (
          <div>
            <label className="text-sm font-semibold block mb-1.5">File</label>
            <input
              type="file"
              accept="image/*,application/pdf"
              onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
              className="block w-full text-sm file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-primary-soft file:text-primary hover:file:bg-primary-soft/80"
            />
            {fileData && (
              <p className="text-xs text-muted-foreground mt-2 truncate">
                {fileData.name} · {fileData.mime || "unknown"}
              </p>
            )}
          </div>
        )}
        {type === "contact" && (
          <div className="space-y-4">
            <div>
              <label className="text-sm font-semibold block mb-1.5">
                Name <span className="text-destructive">*</span>
              </label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Role</label>
              <Input
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-sm font-semibold block mb-1.5">Email</label>
                <Input
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
                />
              </div>
              <div>
                <label className="text-sm font-semibold block mb-1.5">Phone</label>
                <Input
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
                />
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="px-7 py-4 border-t hairline flex items-center justify-end gap-3 bg-surface">
        <button onClick={onDone} className="link-action h-11 px-4 text-sm font-semibold">
          Cancel
        </button>
        <button
          onClick={submit}
          className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90"
        >
          Add resource
        </button>
      </div>
    </>
  );
}
