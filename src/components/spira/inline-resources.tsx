import { useRef, useState } from "react";
import {
  ArrowUpRight,
  CirclePlus,
  Info,
  Paperclip,
  TrashCan,
  X,
} from "@/components/spira/icons";
import { Kebab, KebabVertical } from "@/components/spira/icons";
import type { Resource } from "@/lib/spira/types";
import { resourceDisplayName } from "@/lib/spira/resources";
import { referencesResource } from "@/lib/spira/links";
import { useInlineResources } from "./inline-resources-shared";
import { resourceTypeMeta } from "@/components/spira/resource-meta";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { SheetHead } from "./SheetHead";
import { cn } from "@/lib/utils";

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
      {/* A link needs no leading glyph — the trailing open arrow after the name already says it
          opens out (owner, 2026-08-17). Other kinds keep their type icon. */}
      {resource.type !== "link" && (
        <Icon className="mr-1 inline h-[1em] w-[1em] align-[-0.15em]" />
      )}
      <span className="underline decoration-1 underline-offset-2 [overflow-wrap:anywhere]">
        {resourceDisplayName(resource)}
      </span>
      <ArrowUpRight className="ml-0.5 inline h-[0.9em] w-[0.9em] align-[-0.05em]" />
    </button>
  );
}

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
  const MenuIcon = orientation === "vertical" ? KebabVertical : Kebab;
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
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- event plumbing, not an interaction: keeps a click inside the card from reaching the backdrop
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
              <TrashCan className="h-4 w-4" />
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
 * "Attach resource" as a standalone control, for places that show their actions as buttons rather
 * than hiding them behind a ⋯ menu (the mobile target card, the create form). Renders nothing
 * outside a goal workspace, where there is no resource list to pick from.
 *
 * `variant="icon"` is the same button reduced to its paperclip — for a row that is already a
 * compact strip with its own remove control, where a worded link would be wider than the row.
 */
export function AttachResourceButton({
  attachedTo,
  onAttach,
  className,
  variant = "link",
  ariaLabel = "Attach resource",
}: {
  /** The element's current text — resources it already references are filtered out. */
  attachedTo?: string;
  onAttach: (resourceId: string) => void;
  className?: string;
  variant?: "link" | "icon";
  /** Names the button when it is the icon alone and has no visible word. */
  ariaLabel?: string;
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
        aria-label={variant === "icon" ? ariaLabel : undefined}
        title={variant === "icon" ? ariaLabel : undefined}
        // Same shape as "Add task": a circle-plus and a plain teal label, no underline.
        className={cn(
          variant === "icon"
            ? "shrink-0 rounded p-1 text-muted-foreground transition-colors hover:text-primary"
            : "flex items-center gap-2 py-1 text-left text-sm font-semibold text-primary transition-colors hover:text-primary/80",
          className,
        )}
      >
        {variant === "icon" ? (
          <Paperclip className="h-3.5 w-3.5" />
        ) : (
          <>
            <CirclePlus className="h-[18px] w-[18px] shrink-0" />
            Attach resource
          </>
        )}
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
    /*
     * **A drawer, not a centred dialog** (owner, 2026-08-17) — the same bottom sheet Android opens
     * (`ResourcePickerSheet` in `ui/components/InlineResources.kt`). Picking a resource is a list
     * you reach for while editing text, and a modal box in the middle of the screen took the eye
     * away from the sentence it was about to change.
     *
     * The head is **Kale**, on both surfaces: white type on teal, the same band the app header
     * carries, so a sheet reads as part of the app rather than a white box floating over it.
     */
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent className="sheet-max mt-0 flex flex-col px-0">
        <SheetHead
          title="Attach a resource"
          onClose={() => onOpenChange(false)}
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-7 pt-4">
          <p className="mb-4 flex items-start gap-2 text-[14px] text-muted-foreground">
            <Info className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              Its name is added at the end of the text as a link — tap it to
              open the resource.
            </span>
          </p>

          {resources.length === 0 ? (
            // Nothing to pick: say so inside a bordered notice rather than as loose italic text.
            <p className="rounded-md border border-border/80 bg-surface-sunken/40 px-4 py-5 text-center text-sm text-foreground/80">
              {allAlreadyAttached
                ? "Every resource on this goal is already attached here."
                : "No resources on this goal yet — add one in the Resources section first."}
            </p>
          ) : (
            <ul className="space-y-2">
              {resources.map((resource) => {
                const Icon = resourceTypeMeta[resource.type].icon;
                return (
                  <li key={resource.id}>
                    <button
                      type="button"
                      onClick={() => onPick(resource.id)}
                      className="flex w-full items-center gap-3 rounded-[10px] border border-border bg-surface px-3.5 py-3 text-left transition-colors hover:border-primary/50 hover:bg-primary-soft/40"
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
        </div>
      </DrawerContent>
    </Drawer>
  );
}
