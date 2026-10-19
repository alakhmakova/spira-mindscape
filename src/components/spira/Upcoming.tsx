import { useEffect, useMemo, useState } from "react";
import { format } from "date-fns";
import {
  Bell,
  Calendar,
  ChevronDown,
  ChevronUp,
  CirclePlus,
  X,
} from "@/components/spira/icons";
import { useSpira } from "@/lib/spira/store";
import {
  UPCOMING_CATEGORIES,
  resolveUpcoming,
  upcomingWhen,
  type ResolvedUpcomingEvent,
  type UpcomingGroup,
} from "@/lib/spira/upcoming";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import { momentLabel, nextReminderAt } from "@/lib/spira/reminders";
import { pushState, type PushState } from "@/lib/spira/web-push";
import { AddEventDialog } from "./AddEventDialog";
import { NotificationsHint } from "./NotificationsHint";
import { shouldOfferNotificationsHint } from "@/lib/spira/notifications-hint";
import { ConfirmDialog, QuotedName } from "./ConfirmDialog";

/**
 * **Upcoming** — the All-goals page's right-hand column (owner, 2026-10-09), modelled on the
 * reference's own column of that name (`gusto/upcomingsynccalendar.png`).
 *
 * Measured off that picture: the heading is the same 20px medium as the page's "Goals"; each
 * category is a 16px medium word behind a small caret that folds it; each event is a 14px medium
 * name in near-black over a 14px grey line saying when. The category and its events share one
 * left edge, with the caret hanging in the gutter to the left of it.
 *
 * What it shows is `resolveUpcoming`'s: the nearest ten events across all categories together,
 * from today on, with an empty category left out. "Add event" opens `AddEventDialog`; each event
 * carries an X that removes it, behind a confirm that says when a deadline goes with it.
 */
export function Upcoming({ className }: { className?: string }) {
  const goals = useSpira((s) => s.goals);
  const events = useUpcoming((s) => s.events);
  const status = useUpcoming((s) => s.status);
  const load = useUpcoming((s) => s.load);
  const remove = useUpcoming((s) => s.remove);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<ResolvedUpcomingEvent | null>(null);
  const [removing, setRemoving] = useState<ResolvedUpcomingEvent | null>(null);
  // Read ahead of the click: the press must decide at once whether the hint comes first.
  const [push, setPush] = useState<PushState | null>(null);
  const [hinting, setHinting] = useState(false);

  useEffect(() => {
    let live = true;
    void pushState()
      .then((state) => live && setPush(state))
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    if (status === "idle") void load();
  }, [status, load]);

  const groups = useMemo(() => resolveUpcoming(events, goals), [events, goals]);

  return (
    <section aria-labelledby="upcoming-heading" className={className}>
      <div className="flex min-h-11 items-center justify-between gap-3">
        <h2
          id="upcoming-heading"
          className="text-xl font-medium leading-tight text-foreground"
        >
          Upcoming
        </h2>
        <button
          type="button"
          // The first press in a browser without notifications shows the hint first; closing
          // it carries on to the form (`NotificationsHint.tsx`).
          onClick={() =>
            shouldOfferNotificationsHint(push)
              ? setHinting(true)
              : setAdding(true)
          }
          // **An outline circle with a plus, then the word** (owner, 2026-10-09, after the
          // reference's "Add custom earning type" row): Gravity's `circle-plus` at 16px in the
          // link's own Kale — not a filled disc, which read as a separate button — and the word
          // underlined beside it. The glyph stays out of the underline.
          className="inline-flex items-center gap-2 py-1 text-[15px] font-medium text-primary transition-opacity hover:opacity-80"
        >
          <CirclePlus aria-hidden="true" className="h-4 w-4 shrink-0" />
          <span className="underline decoration-1 underline-offset-[3px]">
            Add event
          </span>
        </button>
      </div>

      <div className="mt-5">
        {status === "error" && events.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Couldn&apos;t load your upcoming dates.{" "}
            <button
              type="button"
              onClick={() => void load()}
              className="font-medium text-primary underline underline-offset-[3px]"
            >
              Try again
            </button>
          </p>
        ) : status !== "ready" && events.length === 0 ? (
          <p className="text-sm text-muted-foreground" role="status">
            Loading…
          </p>
        ) : groups.length === 0 ? (
          <p className="text-sm leading-relaxed text-muted-foreground">
            Nothing coming up. Add an important date, or follow a goal, target
            or task by its deadline.
          </p>
        ) : (
          <div className="space-y-6">
            {groups.map((group) => (
              <UpcomingCategory
                key={group.kind}
                group={group}
                onEdit={setEditing}
                onRemove={setRemoving}
              />
            ))}
          </div>
        )}
      </div>

      {hinting ? (
        <NotificationsHint
          onDismiss={() => {
            setHinting(false);
            setAdding(true);
          }}
          onLeave={() => setHinting(false)}
        />
      ) : null}
      <AddEventDialog open={adding} onOpenChange={setAdding} />
      <AddEventDialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
        editing={editing}
      />

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title="Remove this date?"
        description={removing ? <RemoveQuestion event={removing} /> : null}
        confirmLabel="Yes, remove"
        cancelLabel="No, go back"
        onConfirm={() => {
          if (removing) void remove(removing);
          setRemoving(null);
        }}
      />
    </section>
  );
}

/**
 * The confirm's sentence. **A link's deadline goes with it** (owner, 2026-10-09), and the dialog
 * has to say so before it happens — naming which deadline, and that it cannot be undone.
 */
function RemoveQuestion({ event }: { event: ResolvedUpcomingEvent }) {
  if (event.kind === "important") {
    return (
      <>
        Are you sure you want to remove <QuotedName>{event.title}</QuotedName>{" "}
        from Upcoming?
      </>
    );
  }
  const word = UPCOMING_CATEGORIES.find(
    (c) => c.kind === event.kind,
  )?.singular.toLowerCase();
  return (
    <>
      Are you sure you want to remove <QuotedName>{event.title}</QuotedName>{" "}
      from Upcoming? This also removes the {word}&apos;s deadline,{" "}
      {format(new Date(event.date), "MMMM d, yyyy")}. You can&apos;t undo this.
    </>
  );
}

function UpcomingCategory({
  group,
  onEdit,
  onRemove,
}: {
  group: UpcomingGroup;
  onEdit: (event: ResolvedUpcomingEvent) => void;
  onRemove: (event: ResolvedUpcomingEvent) => void;
}) {
  const [open, setOpen] = useState(true);
  const listId = `upcoming-${group.kind}`;
  const Caret = open ? ChevronUp : ChevronDown;

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={listId}
        // The caret hangs in a 24px gutter, so the word lines up with the events under it.
        className="flex items-center gap-1 py-0.5 text-left text-base font-medium leading-tight text-foreground"
      >
        <span className="grid w-5 shrink-0 place-items-center text-muted-foreground">
          <Caret className="h-3.5 w-3.5" />
        </span>
        {group.label}
      </button>
      <ul id={listId} hidden={!open} className="mt-1.5 space-y-2.5 pl-6">
        {group.events.map((event) => (
          <li key={event.id} className="flex items-start gap-2">
            {/* The name and the lines under it open the event for editing — its time and its
                reminders (plan §2). */}
            <button
              type="button"
              onClick={() => onEdit(event)}
              aria-label={`Edit "${event.title}"`}
              // The label names the action; the date and the next reminder are read after it.
              aria-describedby={`upcoming-when-${event.id}`}
              className="flex min-w-0 flex-1 flex-col gap-0.5 rounded-sm text-left focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring"
            >
              <span className="break-words text-sm font-medium leading-snug text-foreground">
                {event.title}
              </span>
              <span id={`upcoming-when-${event.id}`} className="contents">
                <EventWhen event={event} />
              </span>
            </button>
            {/* 24px, the 2.5.8 floor, and always there: a phone has no hover to reveal it on. */}
            <button
              type="button"
              onClick={() => onRemove(event)}
              aria-label={`Remove "${event.title}" from Upcoming`}
              title="Remove from Upcoming"
              className="grid h-6 w-6 shrink-0 place-items-center rounded-md text-muted-foreground/60 transition-colors hover:bg-secondary hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Two lines under an event's name (owner, 2026-10-14): **the calendar** — when the event is, with
 * its time if it has one — and **the bell** — when the nearest reminder still to come goes off.
 * The bell line is left out when there is no reminder, or when every one has already gone off:
 * a time in the past there would read as a promise.
 */
function EventWhen({ event }: { event: ResolvedUpcomingEvent }) {
  const { time, reminders } = event.record;
  const next = nextReminderAt(event.date, time, reminders);
  return (
    <>
      <span className="flex items-center gap-1.5 text-sm leading-snug text-muted-foreground">
        <Calendar aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
        <span className="sr-only">Date: </span>
        {upcomingWhen(event.date)}
        {time ? ` at ${time}` : ""}
      </span>
      {next ? (
        <span className="flex items-center gap-1.5 text-sm leading-snug text-muted-foreground">
          <Bell aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
          <span className="sr-only">Next reminder: </span>
          {momentLabel(next)}
        </span>
      ) : null}
    </>
  );
}
