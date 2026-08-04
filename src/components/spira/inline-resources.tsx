import {
  createContext,
  useContext,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  ArrowUpRight,
  CirclePlus,
  Info,
  MoreHorizontal,
  MoreVertical,
  Paperclip,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import type { Resource } from "@/lib/spira/types";
import { resourceDisplayName } from "@/lib/spira/resources";
import {
  referencesResource,
  resourceToken,
  rewriteResourceTokens,
  stripResourceTokens,
} from "@/lib/spira/links";
import { resourceTypeMeta } from "@/components/spira/resource-meta";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

/**
 * Goal-scoped services every inline field needs to render and manage attached resources.
 * Provided by `InlineResourcesProvider` (Resources.tsx), which owns the preview panel.
 * Absent outside a goal workspace — components must degrade gracefully when it is null.
 */
export type InlineResourcesValue = {
  goalId: string;
  resources: Resource[];
  /** Create a `link` resource for the URL; resolves with its id (null if the create failed). */
  createLinkResource: (url: string) => Promise<string | null>;
  /** "open" follows a link out to the site; "preview" always opens the editable preview panel. */
  openResource: (id: string, mode?: "open" | "preview") => void;
};

const InlineResourcesContext = createContext<InlineResourcesValue | null>(null);

export const InlineResourcesContextProvider = InlineResourcesContext.Provider;

export function useInlineResources(): InlineResourcesValue | null {
  return useContext(InlineResourcesContext);
}

/**
 * Append a `{{res:id}}` reference to a field's text. Returns null (and explains why) when the
 * field has no room left — the token must never push a value past the server's limit.
 */
export function appendResourceToken(
  text: string,
  resourceId: string,
  maxLength?: number,
): string | null {
  const base = text.trim();
  const next = base
    ? `${base} ${resourceToken(resourceId)}`
    : resourceToken(resourceId);
  if (maxLength !== undefined && next.length > maxLength) {
    toast.error(
      `No room to attach a resource here — this field is limited to ${maxLength} characters.`,
    );
    return null;
  }
  return next;
}

/**
 * The text a field shows while being edited: each tag carries the resource's **name** instead of
 * its id, so `Read {{res:Job ad}} first` is readable. A tag whose id can't be resolved is left
 * untouched rather than guessed at.
 */
export function tokensToNames(text: string, resources: Resource[]): string {
  return rewriteResourceTokens(text, (inner) => {
    const resource = resources.find((r) => r.id === inner);
    return resource ? resourceDisplayName(resource) : inner;
  });
}

/**
 * The text a field stores: each tag's name (or id, if the user left one in place) is mapped back
 * to the resource id. A tag naming something that no longer exists degrades to plain text — the
 * words stay and no dangling reference is written.
 */
export function namesToTokens(text: string, resources: Resource[]): string {
  return rewriteResourceTokens(text, (inner) => {
    const byId = resources.find((r) => r.id === inner);
    if (byId) return byId.id;
    const lower = inner.toLowerCase();
    const byName = resources.find(
      (r) => resourceDisplayName(r).toLowerCase() === lower,
    );
    return byName ? byName.id : null;
  });
}

/**
 * A field's text as plain prose: resource tags become the resource's name (or vanish, if it no
 * longer exists). Use wherever a value is quoted rather than rendered — dialogs, toasts — so a raw
 * `{{res:42}}` never reaches the user.
 */
export function useReadableText(text: string): string {
  const ctx = useInlineResources();
  const resources = ctx?.resources ?? [];
  return stripResourceTokens(text, (inner) => {
    const resource = resources.find((r) => r.id === inner);
    return resource ? resourceDisplayName(resource) : "";
  });
}

/**
 * An attached resource rendered inline as a **link** (never the raw URL): the resource's own type
 * icon on the left, its name underlined in dark Kale, and a diagonal arrow on the right marking it
 * as a jump-out. Tapping it opens the resource — a link goes to the site, everything else opens its
 * preview. Long-press (mobile) or right-click (desktop) opens the preview instead, which is where
 * the resource is edited.
 */
export function ResourceLink({ id }: { id: string }) {
  const ctx = useInlineResources();
  const resource = ctx?.resources.find((r) => r.id === id);
  const longPress = useRef<ReturnType<typeof setTimeout> | null>(null);
  const suppressClick = useRef(false);

  if (!resource) {
    // The reference outlived its resource (e.g. deleted on another device). Show a neutral
    // placeholder rather than the raw token, and never a broken link.
    return <span className="italic text-muted-foreground">unavailable</span>;
  }

  const Icon = resourceTypeMeta[resource.type].icon;

  const clearLongPress = () => {
    if (longPress.current) clearTimeout(longPress.current);
    longPress.current = null;
  };

  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation(); // open the resource; don't enter the field's edit mode
        if (suppressClick.current) {
          suppressClick.current = false;
          return;
        }
        ctx?.openResource(id);
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        e.stopPropagation();
        ctx?.openResource(id, "preview");
      }}
      onPointerDown={(e) => {
        e.stopPropagation();
        clearLongPress();
        longPress.current = setTimeout(() => {
          suppressClick.current = true;
          ctx?.openResource(id, "preview");
        }, 500);
      }}
      onPointerUp={clearLongPress}
      onPointerLeave={clearLongPress}
      onPointerCancel={clearLongPress}
      title={`${resourceTypeMeta[resource.type].label} — tap to open, long-press to edit`}
      // `inline-block` (not inline-flex) so the label keeps the surrounding text's baseline — an
      // inline-flex box takes its baseline from the icon and rides visibly high — and so a
      // strikethrough on an ancestor (a done task) never crosses out the resource's name: text
      // decoration doesn't propagate into an atomic inline box. The icons are inline, nudged down
      // with `vertical-align` to sit optically centred on lowercase text.
      className="inline-block max-w-full align-baseline font-medium text-primary transition-colors hover:text-primary/80"
    >
      <Icon className="mr-1 inline h-[1em] w-[1em] align-[-0.15em]" />
      <span className="underline decoration-1 underline-offset-2 [overflow-wrap:anywhere]">
        {resourceDisplayName(resource)}
      </span>
      <ArrowUpRight className="ml-0.5 inline h-[0.9em] w-[0.9em] align-[-0.05em]" />
    </button>
  );
}

/**
 * Tracks whether the element the ref is on currently renders as a single line of text. Row
 * controls sit vertically centred beside one-line content and jump to the top-right corner as soon
 * as the text wraps, so they never float in the middle of a paragraph.
 */
export function useIsSingleLine<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);
  const [singleLine, setSingleLine] = useState(true);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
      // 1.5 line-heights: comfortably above one line, comfortably below two.
      setSingleLine(el.getBoundingClientRect().height <= lineHeight * 1.5);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return { ref, singleLine } as const;
}

/**
 * True once the element runs past `lines` lines of text. Used where a control should stop being
 * vertically centred beside a long title and ride to the top instead.
 */
export function useTallText<T extends HTMLElement>(lines: number) {
  const ref = useRef<T | null>(null);
  const [tall, setTall] = useState(false);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
      setTall(el.getBoundingClientRect().height > lineHeight * (lines + 0.5));
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [lines]);

  return { ref, tall } as const;
}

/** Vertical placement for a row's floating controls, from `useIsSingleLine`. */
export function rowControlPlacement(singleLine: boolean): string {
  return singleLine ? "top-1/2 -translate-y-1/2" : "top-1";
}

/**
 * Classes that keep an element's menu out of the way until the row is actually being worked on:
 * hidden by default, revealed on hover, on focus anywhere inside the row (so tabbing in shows it
 * at the same moment the inline editing caret appears), on its own keyboard focus, and while its
 * menu is open. `pointer-events-none` while hidden means the invisible trigger never swallows a
 * click meant for the text it floats over.
 */
export const REVEAL_ON_ROW_ACTIVITY = [
  "opacity-0 pointer-events-none transition-opacity",
  "group-hover:opacity-100 group-hover:pointer-events-auto",
  "group-focus-within:opacity-100 group-focus-within:pointer-events-auto",
  "focus-visible:opacity-100 focus-visible:pointer-events-auto",
  "data-[state=open]:opacity-100 data-[state=open]:pointer-events-auto",
].join(" ");

/**
 * The same reveal, minus `group-focus-within` — for a row that holds controls of its own (an
 * Options card has a rating badge and a select radio). Focus anywhere in such a row would pop the
 * menu open on a tap that had nothing to do with editing; the caller pairs this with
 * {@link REVEAL_ACTIVE} keyed to the *text's* focus, so the menu appears with the editing caret
 * and not before.
 */
export const REVEAL_ON_ROW_HOVER = [
  "opacity-0 pointer-events-none transition-opacity",
  "group-hover:opacity-100 group-hover:pointer-events-auto",
  "focus-visible:opacity-100 focus-visible:pointer-events-auto",
  "data-[state=open]:opacity-100 data-[state=open]:pointer-events-auto",
].join(" ");

/** Shown outright. Chosen INSTEAD of a reveal class, never alongside one — two `opacity-*`
 *  utilities on the same element resolve by stylesheet order, not by the order written here. */
export const REVEAL_ACTIVE =
  "opacity-100 pointer-events-auto transition-opacity";

/**
 * The per-element ⋯ menu: attach a resource to this element's text, and (where the element can be
 * removed) delete it. Replaces the bare ✕ on options, reality items, checklist tasks, and targets.
 */
export function ElementActionsMenu({
  ariaLabel,
  onAttach,
  onDelete,
  deleteLabel = "Delete",
  className,
  iconClassName,
  align = "end",
  attachedTo,
  orientation = "horizontal",
}: {
  ariaLabel: string;
  /** Called with the picked resource's id; the caller appends the token to its own text. */
  onAttach: (resourceId: string) => void;
  onDelete?: () => void;
  deleteLabel?: string;
  className?: string;
  iconClassName?: string;
  align?: "start" | "end" | "center";
  /** The element's current text. Resources already referenced by it are left out of the picker,
   *  so the same resource can't be attached to the same place twice. */
  attachedTo?: string;
  /** ⋯ or ⋮ — a row that floats its menu over the text reads better horizontal; a row that keeps
   *  a fixed control column (a checklist task) reads better vertical. */
  orientation?: "horizontal" | "vertical";
}) {
  const MenuIcon = orientation === "vertical" ? MoreVertical : MoreHorizontal;
  const ctx = useInlineResources();
  const [pickerOpen, setPickerOpen] = useState(false);
  const all = ctx?.resources ?? [];
  const attachable =
    attachedTo === undefined
      ? all
      : all.filter((r) => !referencesResource(attachedTo, r.id));

  return (
    // Menus and dialogs are portalled in the DOM but stay children in the REACT tree, so their
    // clicks bubble to whatever renders this menu. When that is an InlineText read view (the
    // Options card), an un-stopped click would drop the field into edit mode and unmount the menu
    // mid-interaction. `display: contents` keeps the layout untouched.
    <span className="contents" onClick={(e) => e.stopPropagation()}>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            aria-label={ariaLabel}
            onClick={(e) => e.stopPropagation()}
            className={cn(
              "p-1 text-muted-foreground transition-colors hover:text-foreground",
              className,
            )}
          >
            <MenuIcon className={cn("h-4 w-4", iconClassName)} />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align={align}
          className="min-w-[190px] rounded-xl bg-white p-1.5 shadow-lg"
        >
          {ctx && (
            <DropdownMenuItem
              onClick={() => setPickerOpen(true)}
              className="gap-2.5 rounded-lg px-3 py-2.5 text-sm"
            >
              <Paperclip className="h-4 w-4 text-muted-foreground" />
              Attach resource
            </DropdownMenuItem>
          )}
          {ctx && onDelete && <DropdownMenuSeparator />}
          {onDelete && (
            <DropdownMenuItem
              onClick={onDelete}
              className="gap-2.5 rounded-lg px-3 py-2.5 text-sm text-destructive focus:text-destructive"
            >
              <Trash2 className="h-4 w-4" />
              {deleteLabel}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <ResourcePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        resources={attachable}
        allAlreadyAttached={all.length > 0 && attachable.length === 0}
        onPick={(resourceId) => {
          setPickerOpen(false);
          onAttach(resourceId);
        }}
      />
    </span>
  );
}

/**
 * "Attach resource" as a standalone link, for places that show their actions as buttons rather
 * than hiding them behind a ⋯ menu (the mobile target card). Renders nothing outside a goal
 * workspace, where there is no resource list to pick from.
 */
export function AttachResourceButton({
  attachedTo,
  onAttach,
  className,
}: {
  /** The element's current text — resources it already references are filtered out. */
  attachedTo?: string;
  onAttach: (resourceId: string) => void;
  className?: string;
}) {
  const ctx = useInlineResources();
  const [pickerOpen, setPickerOpen] = useState(false);
  if (!ctx) return null;

  const all = ctx.resources;
  const attachable =
    attachedTo === undefined
      ? all
      : all.filter((r) => !referencesResource(attachedTo, r.id));

  return (
    <>
      <button
        type="button"
        onClick={() => setPickerOpen(true)}
        // Same shape as "Add task": a circle-plus and a plain teal label, no underline.
        className={cn(
          "flex items-center gap-2 py-1 text-left text-sm font-semibold text-primary transition-colors hover:text-primary/80",
          className,
        )}
      >
        <CirclePlus className="h-[18px] w-[18px] shrink-0" />
        Attach resource
      </button>
      <ResourcePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        resources={attachable}
        allAlreadyAttached={all.length > 0 && attachable.length === 0}
        onPick={(resourceId) => {
          setPickerOpen(false);
          onAttach(resourceId);
        }}
      />
    </>
  );
}

/** Picks one of the goal's resources to attach; the link is inserted at the end of the text. */
function ResourcePickerDialog({
  open,
  onOpenChange,
  resources,
  allAlreadyAttached = false,
  onPick,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resources: Resource[];
  /** True when the goal has resources but every one of them is already on this element. */
  allAlreadyAttached?: boolean;
  onPick: (resourceId: string) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Same footprint as ConfirmDialog: never edge-to-edge on a phone, roomier on desktop.
          `onOpenAutoFocus` is suppressed so the close button doesn't open with a focus ring around
          it — no other dialog in the app opens with one. Tabbing to it still shows the ring. */}
      <DialogContent
        onOpenAutoFocus={(e) => e.preventDefault()}
        className="w-[calc(100%-2rem)] max-w-[440px] rounded-lg border-0 bg-surface p-6 shadow-2xl sm:max-w-[600px]"
      >
        <DialogHeader className="text-left">
          <DialogTitle className="font-sans text-[20px] font-semibold tracking-tight">
            Attach a resource
          </DialogTitle>
          <DialogDescription className="flex items-start gap-2 text-[14px] text-foreground/80">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
            <span>
              Its name is added at the end of the text as a link — tap it to
              open the resource.
            </span>
          </DialogDescription>
        </DialogHeader>

        {resources.length === 0 ? (
          // Nothing to pick: say so inside a bordered notice rather than as loose italic text.
          <p className="rounded-md border border-border/80 bg-surface-sunken/40 px-4 py-5 text-center text-sm text-foreground/80">
            {allAlreadyAttached
              ? "Every resource on this goal is already attached here."
              : "No resources on this goal yet — add one in the Resources section first."}
          </p>
        ) : (
          <ul className="-mx-2 max-h-[50vh] space-y-1 overflow-y-auto px-2">
            {resources.map((resource) => {
              const Icon = resourceTypeMeta[resource.type].icon;
              return (
                <li key={resource.id}>
                  <button
                    type="button"
                    onClick={() => onPick(resource.id)}
                    className="flex w-full items-center gap-3 rounded-sm border border-border/70 bg-surface px-3 py-2.5 text-left transition-colors hover:border-primary/50 hover:bg-primary-soft/40"
                  >
                    <Icon className="h-4 w-4 shrink-0 text-primary" />
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">
                      {resourceDisplayName(resource)}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {resourceTypeMeta[resource.type].label}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}
