import { useState } from "react";
import {
  FileText,
  LinkIcon,
  Paperclip,
  User as UserIcon,
  Trash2,
  ExternalLink,
  Download,
} from "lucide-react";
import type { Goal, Resource } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/components/ui/drawer";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

const typeMeta = {
  note: { icon: FileText, label: "Note" },
  link: { icon: LinkIcon, label: "Link" },
  file: { icon: Paperclip, label: "File" },
  contact: { icon: UserIcon, label: "Contact" },
} as const;

export function ResourcesList({ goal }: { goal: Goal }) {
  const removeResource = useSpira((s) => s.removeResource);
  const [preview, setPreview] = useState<Resource | null>(null);

  if (goal.resources.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        Capture notes, links, files, and contacts that support this goal.
      </p>
    );
  }

  return (
    <>
      <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {goal.resources.map((r) => {
          const Icon = typeMeta[r.type].icon;
          return (
            <li
              key={r.id}
              className="surface-sunken p-3 flex items-start gap-3 group hover:border-border-strong transition-colors cursor-pointer"
              onClick={() => {
                if (r.type === "link") window.open(r.url, "_blank");
                else setPreview(r);
              }}
            >
              <div className="h-8 w-8 rounded-md bg-accent grid place-items-center shrink-0">
                <Icon className="h-4 w-4 text-muted-foreground" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                  {typeMeta[r.type].label}
                </div>
                <div className="text-sm font-medium truncate">
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
                className="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive p-1"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </li>
          );
        })}
      </ul>

      <ResourcePreview resource={preview} onClose={() => setPreview(null)} />
    </>
  );
}

function ResourcePreview({
  resource,
  onClose,
}: {
  resource: Resource | null;
  onClose: () => void;
}) {
  const isMobile = useIsMobile();
  const open = !!resource;

  const Body = resource && (
    <div className="space-y-3">
      {resource.type === "note" && (
        <div className="prose prose-invert max-w-none whitespace-pre-wrap text-sm leading-relaxed">
          {resource.body || <em className="text-muted-foreground">Empty note</em>}
        </div>
      )}
      {resource.type === "link" && (
        <a
          href={resource.url}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-2 text-primary hover:underline text-sm"
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
              className="w-full h-[60vh] rounded-md border hairline bg-surface-sunken"
              title={resource.title}
            />
          )}
          <a
            href={resource.dataUrl}
            download={resource.title}
            className="inline-flex items-center gap-2 text-primary hover:underline text-sm"
          >
            <Download className="h-4 w-4" /> Download
          </a>
        </div>
      )}
      {resource.type === "contact" && (
        <div className="surface-sunken p-4 space-y-1.5">
          <div className="font-display text-xl">{resource.name}</div>
          {resource.role && <div className="text-sm text-muted-foreground">{resource.role}</div>}
          {resource.email && <div className="text-sm">{resource.email}</div>}
          {resource.phone && <div className="text-sm num">{resource.phone}</div>}
        </div>
      )}
    </div>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
        <DrawerContent className="px-4 pb-6">
          <DrawerHeader className="px-0">
            <DrawerTitle className="font-display text-xl truncate">
              {resource?.type === "contact" ? resource.name : (resource as any)?.title}
            </DrawerTitle>
          </DrawerHeader>
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="w-full sm:max-w-2xl overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="font-display text-2xl">
            {resource?.type === "contact" ? resource.name : (resource as any)?.title}
          </SheetTitle>
        </SheetHeader>
        <div className="mt-4">{Body}</div>
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
        <DrawerContent className="px-4 pb-6">
          <DrawerHeader className="px-0">
            <DrawerTitle className="font-display text-2xl">Add resource</DrawerTitle>
          </DrawerHeader>
          {Body}
        </DrawerContent>
      </Drawer>
    );
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="font-display text-2xl">Add resource</SheetTitle>
        </SheetHeader>
        <div className="mt-4">{Body}</div>
      </SheetContent>
    </Sheet>
  );
}

function Form({ goalId, onDone }: { goalId: string; onDone: () => void }) {
  const addResource = useSpira((s) => s.addResource);
  const [type, setType] = useState<Resource["type"]>("note");

  // shared
  const [title, setTitle] = useState("");
  // note
  const [body, setBody] = useState("");
  // link
  const [url, setUrl] = useState("");
  // file
  const [fileData, setFileData] = useState<{ name: string; mime: string; dataUrl: string } | null>(
    null,
  );
  // contact
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
    <div className="space-y-5">
      <div className="grid grid-cols-4 gap-1.5">
        {(["note", "link", "file", "contact"] as const).map((t) => {
          const Icon = typeMeta[t].icon;
          return (
            <button
              key={t}
              onClick={() => setType(t)}
              className={cn(
                "flex flex-col items-center gap-1.5 py-3 rounded-md border text-xs capitalize transition-colors",
                type === t
                  ? "bg-primary/15 border-primary/40 text-primary"
                  : "bg-surface border-border hover:border-border-strong",
              )}
            >
              <Icon className="h-4 w-4" />
              {typeMeta[t].label}
            </button>
          );
        })}
      </div>

      {type !== "contact" && (
        <div>
          <label className="text-xs uppercase tracking-wider text-muted-foreground">Title</label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="mt-1.5 h-11 bg-surface"
            autoFocus
          />
        </div>
      )}

      {type === "note" && (
        <div>
          <label className="text-xs uppercase tracking-wider text-muted-foreground">Note</label>
          <Textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            className="mt-1.5 min-h-32 bg-surface"
          />
        </div>
      )}
      {type === "link" && (
        <div>
          <label className="text-xs uppercase tracking-wider text-muted-foreground">URL</label>
          <Input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://"
            className="mt-1.5 h-11 bg-surface"
          />
        </div>
      )}
      {type === "file" && (
        <div>
          <label className="text-xs uppercase tracking-wider text-muted-foreground">File</label>
          <input
            type="file"
            accept="image/*,application/pdf"
            onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
            className="mt-1.5 block w-full text-sm file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:text-sm file:bg-accent file:text-foreground hover:file:bg-accent/80"
          />
          {fileData && (
            <p className="text-xs text-muted-foreground mt-2 truncate">
              {fileData.name} · {fileData.mime || "unknown"}
            </p>
          )}
        </div>
      )}
      {type === "contact" && (
        <div className="space-y-3">
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} className="mt-1.5 h-11 bg-surface" />
          </div>
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Role</label>
            <Input value={role} onChange={(e) => setRole(e.target.value)} className="mt-1.5 h-11 bg-surface" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="email"
              className="h-11 bg-surface"
            />
            <Input
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="phone"
              className="h-11 bg-surface"
            />
          </div>
        </div>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit}>Add</Button>
      </div>
    </div>
  );
}
