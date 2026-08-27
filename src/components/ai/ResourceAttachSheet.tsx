import { useState } from "react";

import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { SheetHead } from "@/components/spira/SheetHead";
import { ClearSearchWord } from "@/components/spira/ListToolbar";
import { SM_BREAKPOINT, useIsNarrowerThan } from "@/hooks/use-mobile";
import {
  FileText,
  Link as LinkIcon,
  Mail,
  Paperclip,
} from "@/components/spira/icons";
import { cn } from "@/lib/utils";

export type AttachableResource = {
  id: string;
  label: string;
  mime?: string;
  type?: string;
};

/** The kit glyph for a resource type — the same mapping Android's picker uses. */
function typeIcon(type?: string) {
  const cls = "h-[18px] w-[18px] shrink-0";
  switch (type) {
    case "note":
      return <FileText className={cls} />;
    case "link":
      return <LinkIcon className={cls} />;
    case "file":
      return <Paperclip className={cls} />;
    default:
      return <Mail className={cls} />;
  }
}

function typeLabel(type?: string) {
  switch (type) {
    case "note":
      return "Note";
    case "link":
      return "Link";
    case "file":
      return "File";
    default:
      return "Email";
  }
}

/**
 * **Attach a resource** — one of the goal's saved resources put in front of the assistant
 * (BUG-030). No bytes leave the browser: the chip carries the resource id and the server
 * inlines what it already holds.
 *
 * This is a **sheet**, because Android's is (`ResourcePickerSheetContent` in
 * `AiChatScreen.kt`) and CLAUDE.md's sheet spec says the two surfaces draw the same card —
 * "when the two disagree it is a defect, not a platform difference". The web used to show a
 * small inline dropdown built around a **bare `<input>`**, which broke that rule and rule 1
 * as well ("never ship raw, un-customized default elements"). It also inherited the AI
 * panel's `text-white`, so the search field typed white on white and the user could not see
 * what they were typing (owner, 2026-08-23).
 */
export function ResourceAttachSheet({
  open,
  onOpenChange,
  resources,
  attachedIds,
  onPick,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resources: AttachableResource[];
  /** Already on the composer — offered as "Added" rather than a second time. */
  attachedIds: number[];
  onPick: (resource: AttachableResource) => void;
}) {
  const [query, setQuery] = useState("");
  const isMobile = useIsNarrowerThan(SM_BREAKPOINT);

  const filtered = resources.filter((r) =>
    r.label.toLowerCase().includes(query.trim().toLowerCase()),
  );

  const close = () => {
    setQuery("");
    onOpenChange(false);
  };

  // One tree for both widths: the drawer and the side panel are only *where* it sits.
  const body = (
    <div className="flex min-h-0 flex-col bg-white text-foreground">
      <SheetHead title="Attach a resource" onClose={close} />
      <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4 pb-8">
        <div className="relative">
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search resources"
            aria-label="Search resources"
            className="h-11 w-full rounded-md border border-border bg-white pl-3 pr-16 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
          />
          {query.trim() !== "" && (
            <span className="absolute inset-y-0 right-2 flex items-center">
              {/* The word "Clear", never a cross — see CLAUDE.md, Design 3f. */}
              <ClearSearchWord onClear={() => setQuery("")} />
            </span>
          )}
        </div>

        <div className="mt-3">
          {filtered.length === 0 ? (
            <p className="py-4 text-sm text-muted-foreground">
              {resources.length === 0
                ? "This goal has no resources yet."
                : "No matching resources."}
            </p>
          ) : (
            filtered.map((r) => {
              const taken = attachedIds.includes(Number(r.id));
              return (
                <button
                  key={r.id}
                  type="button"
                  disabled={taken}
                  onClick={() => {
                    onPick(r);
                    close();
                  }}
                  className={cn(
                    "flex w-full items-center gap-3 rounded-md px-1 py-3 text-left transition-colors",
                    taken
                      ? "text-muted-foreground"
                      : "text-foreground hover:bg-primary/5",
                  )}
                >
                  <span
                    className={taken ? "text-muted-foreground" : "text-primary"}
                  >
                    {typeIcon(r.type)}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-[15px]">
                    {r.label}
                  </span>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {taken ? "Added" : typeLabel(r.type)}
                  </span>
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="mt-0 flex max-h-[92vh] flex-col bg-white px-0">
          {body}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        // Its own X lives in the teal head; the corner one would sit on top of it.
        closeButton={false}
        className="flex w-full flex-col gap-0 border-l-0 bg-white p-0 sm:max-w-[420px]"
      >
        {body}
      </SheetContent>
    </Sheet>
  );
}
