import { useMemo, useRef, useState } from "react";
import { Drawer, DrawerContent, DrawerTitle } from "@/components/ui/drawer";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { X } from "@/components/spira/icons";
import { useIsMobile } from "@/hooks/use-mobile";
import { FIELD_LIMITS, lengthError } from "@/lib/spira/limits";
import { useSpira } from "@/lib/spira/store";
import {
  UPCOMING_CATEGORIES,
  linkableItems,
  linkedId,
  type UpcomingKind,
} from "@/lib/spira/upcoming";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import { cn } from "@/lib/utils";
import { DeadlinePopover } from "./DeadlinePopover";
import { SheetHead } from "./SheetHead";

/**
 * **Add event** — what the Upcoming column's link opens (owner, 2026-10-09).
 *
 * On a laptop it is a centred modal drawn after the reference's "Time tracking" dialog, measured:
 * a ~706px white card with 4px corners and 20px of padding, the title at 20px semibold with the X
 * on its line, a sentence under it, and the question "Type" answered by a row of joined segments
 * — 48px tall, a 1px grey edge with a Kale word when not chosen, a 2px Kale edge on a pale Kale
 * fill with a near-black word when chosen. Its fields are the reference's too: 40px, a 1px grey
 * `#9F9F9F` frame, 4px corners, a 16px semibold label 12px above. Cancel and Save sit bottom right.
 *
 * On a phone it is a SHEET, by the sheet rules (CLAUDE.md 3e): a bottom drawer with the Kale head,
 * its own scrolling body at the 20px gutter, and Cancel / Save pinned at the foot. The segments
 * stand two by two there, since four do not fit across a phone.
 *
 * - **Important date** asks for a name and a date.
 * - **Goal / Target / Task** asks which one, from the items that have a deadline and are not in
 *   Upcoming yet. The item's deadline becomes the event's date, and a date changed here becomes
 *   the item's new deadline — the event has no date of its own (see `upcoming.ts`).
 */
export function AddEventDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const isMobile = useIsMobile();
  const close = () => onOpenChange(false);

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="sheet-max mt-0 flex flex-col px-0">
          {open ? <EventForm mobile onDone={close} /> : null}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        closeButton={false}
        className="modal-max w-[calc(100%-2rem)] max-w-[706px] gap-0 overflow-y-auto rounded-[4px] border-0 bg-white p-5 shadow-2xl sm:max-w-[706px] sm:rounded-[4px]"
      >
        {open ? <EventForm onDone={close} /> : null}
      </DialogContent>
    </Dialog>
  );
}

const DESCRIPTION =
  "Keep an important date in view, or follow a goal, target or task by its deadline.";

/** The reference's field frame — 40px, a 1px `#9F9F9F` edge, 4px corners. */
const FIELD =
  "h-10 rounded-[4px] border-[#9F9F9F] px-3 text-base focus-visible:border-primary";

function EventForm({
  mobile = false,
  onDone,
}: {
  mobile?: boolean;
  onDone: () => void;
}) {
  const goals = useSpira((s) => s.goals);
  const events = useUpcoming((s) => s.events);
  const add = useUpcoming((s) => s.add);

  const [kind, setKind] = useState<UpcomingKind>("important");
  const [title, setTitle] = useState("");
  const [date, setDate] = useState<string | undefined>();
  const [itemId, setItemId] = useState<string>("");
  const [saving, setSaving] = useState(false);

  // Only what has a deadline, and not what is in Upcoming already.
  const choices = useMemo(() => {
    const taken = new Set(
      events.filter((e) => e.kind === kind).map((e) => linkedId(e)),
    );
    return linkableItems(goals).filter(
      (item) => item.kind === kind && !taken.has(item.id),
    );
  }, [goals, events, kind]);
  const item = choices.find((c) => c.id === itemId);

  const pickKind = (next: UpcomingKind) => {
    setKind(next);
    setItemId("");
    setDate(undefined);
  };
  const pickItem = (id: string) => {
    setItemId(id);
    setDate(choices.find((c) => c.id === id)?.deadline);
  };

  const word =
    UPCOMING_CATEGORIES.find((c) => c.kind === kind)?.singular.toLowerCase() ??
    "";
  const titleMessage = lengthError(
    title.trim(),
    FIELD_LIMITS.upcomingTitle,
    "Event name",
  );
  const canSave =
    !saving &&
    !!date &&
    (kind === "important" ? title.trim().length > 0 && !titleMessage : !!item);

  const save = async () => {
    if (!canSave || !date) return;
    setSaving(true);
    const ok =
      kind === "important"
        ? await add({ kind: "important", title: title.trim(), date })
        : item
          ? await add({ kind: item.kind, item, date })
          : false;
    setSaving(false);
    if (ok) onDone();
  };

  const fields = (
    <div className="space-y-5">
      <TypeChoice value={kind} onChange={pickKind} mobile={mobile} />

      {kind === "important" ? (
        <Field label="Event name" htmlFor="event-name">
          {/* No `maxLength`: a paste would be cut short without a word. Over the limit the
              field says so and Save waits, as the goal form does (`lengthError`). */}
          <Input
            id="event-name"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") void save();
            }}
            aria-invalid={titleMessage ? true : undefined}
            aria-describedby={titleMessage ? "event-name-error" : undefined}
            className={FIELD}
          />
          {titleMessage ? (
            <p
              id="event-name-error"
              role="alert"
              className="mt-1.5 text-xs font-medium text-destructive"
            >
              {titleMessage}
            </p>
          ) : null}
        </Field>
      ) : (
        <Field label={`Choose ${word}`} htmlFor="event-item">
          <Select
            value={itemId}
            onValueChange={pickItem}
            disabled={choices.length === 0}
          >
            <SelectTrigger
              id="event-item"
              className={cn(FIELD, "shadow-none [&>span]:truncate")}
            >
              <SelectValue
                placeholder={
                  choices.length === 0
                    ? `No ${word}s with a deadline to add`
                    : `Choose ${word}`
                }
              />
            </SelectTrigger>
            <SelectContent className="max-h-72 bg-white">
              {choices.map((c) => (
                <SelectItem
                  key={c.id}
                  value={c.id}
                  className="py-2 text-[15px]"
                >
                  {c.title}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {choices.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Only a {word} that has a deadline can be added, and each only
              once.
            </p>
          ) : null}
        </Field>
      )}

      {kind === "important" || item ? (
        <Field
          label="Event date"
          hint={
            item ? `Changing it moves the ${word}'s deadline too.` : undefined
          }
        >
          <DeadlinePopover
            iso={date}
            onChange={setDate}
            variant="input"
            placeholder="Pick a date"
            inputClassName={FIELD}
          />
        </Field>
      ) : null}
    </div>
  );

  if (mobile) {
    return (
      <>
        <SheetHead
          title="Add event"
          onClose={onDone}
          titleComponent={DrawerTitle}
        />
        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 pb-8 pt-4">
          <p className="text-[15px] leading-relaxed text-foreground/80">
            {DESCRIPTION}
          </p>
          {fields}
        </div>
        <div
          className="flex shrink-0 gap-3 bg-surface px-5 pt-3"
          style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
        >
          <button
            type="button"
            onClick={onDone}
            className="h-12 flex-1 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void save()}
            disabled={!canSave}
            className="h-12 flex-1 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
          >
            {saving ? "Saving…" : "Save"}
          </button>
        </div>
      </>
    );
  }

  return (
    <>
      {/* The title's row carries the X, as in every confirm dialog (`ConfirmDialog`). */}
      <div className="flex items-start gap-4">
        <DialogTitle className="min-w-0 flex-1 font-sans text-[20px] font-semibold leading-7 tracking-tight text-foreground">
          Add event
        </DialogTitle>
        <button
          type="button"
          onClick={onDone}
          className="-mr-1.5 grid h-7 w-7 shrink-0 place-items-center rounded-md text-muted-foreground/70 transition-colors hover:bg-secondary hover:text-foreground"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <DialogDescription className="mt-3 text-[15px] leading-relaxed text-foreground/80">
        {DESCRIPTION}
      </DialogDescription>
      <div className="mt-5">{fields}</div>
      <div className="mt-7 flex justify-end gap-2">
        <button
          type="button"
          onClick={onDone}
          className="h-12 rounded-[4px] border border-[#9F9F9F] bg-white px-6 text-base font-semibold text-foreground transition-colors hover:bg-secondary"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => void save()}
          disabled={!canSave}
          className="h-12 rounded-[4px] bg-primary px-6 text-base font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
        >
          {saving ? "Saving…" : "Save"}
        </button>
      </div>
    </>
  );
}

function Field({
  label,
  htmlFor,
  hint,
  children,
}: {
  label: string;
  htmlFor?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label
        htmlFor={htmlFor}
        className="mb-3 block text-base font-semibold leading-tight text-foreground"
      >
        {label}
      </label>
      {children}
      {hint ? (
        <p className="mt-2 text-sm text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

/**
 * The "Type" question — the reference's "Status" segments, with four answers instead of two. A
 * radio group to assistive tech, with the arrow keys moving the choice as a radio group's do.
 */
function TypeChoice({
  value,
  onChange,
  mobile,
}: {
  value: UpcomingKind;
  onChange: (next: UpcomingKind) => void;
  mobile: boolean;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const index = UPCOMING_CATEGORIES.findIndex((c) => c.kind === value);

  const move = (delta: number) => {
    const next =
      (index + delta + UPCOMING_CATEGORIES.length) % UPCOMING_CATEGORIES.length;
    onChange(UPCOMING_CATEGORIES[next].kind);
    refs.current[next]?.focus();
  };

  return (
    <div>
      <span
        id="event-type-label"
        className="mb-3 block text-base font-semibold leading-tight text-foreground"
      >
        Type
      </span>
      <div
        role="radiogroup"
        aria-labelledby="event-type-label"
        className={cn(mobile ? "grid grid-cols-2 gap-2" : "flex")}
      >
        {UPCOMING_CATEGORIES.map((c, i) => {
          const selected = c.kind === value;
          return (
            <button
              key={c.kind}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role="radio"
              aria-checked={selected}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(c.kind)}
              onKeyDown={(e) => {
                if (e.key === "ArrowRight" || e.key === "ArrowDown") {
                  e.preventDefault();
                  move(1);
                } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
                  e.preventDefault();
                  move(-1);
                }
              }}
              className={cn(
                "relative h-12 min-w-0 truncate px-3 text-[15px] font-medium transition-colors focus-visible:z-20 focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring",
                // Joined on a laptop: each segment overlaps the last by its 1px edge, and the
                // chosen one rises above its neighbours so its 2px Kale edge is whole.
                !mobile && "flex-1 [&:not(:first-child)]:-ml-px",
                !mobile && i === 0 && "rounded-l-[4px]",
                !mobile &&
                  i === UPCOMING_CATEGORIES.length - 1 &&
                  "rounded-r-[4px]",
                mobile && "rounded-[4px]",
                selected
                  ? "z-10 border-2 border-primary bg-[#F3FAFB] text-foreground"
                  : "border border-[#BABABA] bg-white text-primary hover:bg-[#F3FAFB]",
              )}
            >
              {c.singular}
            </button>
          );
        })}
      </div>
    </div>
  );
}
