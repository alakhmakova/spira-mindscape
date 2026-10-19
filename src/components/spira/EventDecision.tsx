import { useNavigate } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { X } from "@/components/spira/icons";
import { useEventDecision } from "@/lib/spira/event-decision";
import { useSpira } from "@/lib/spira/store";
import {
  linkableItems,
  linkedId,
  resolveAll,
  UPCOMING_CATEGORIES,
  upcomingWhen,
  type ResolvedUpcomingEvent,
} from "@/lib/spira/upcoming";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import { AddEventDialog } from "./AddEventDialog";

/**
 * **The event's own card** — what Open on an on-the-day reminder and "Let's do it" on the
 * Important events timeline both bring up (owner, 2026-10-19). The reminder picture over the
 * event's name and date, and three answers:
 *
 * - **Done** — the event is settled and ticked on the timeline. A linked task is ticked, and a
 *   binary target marked done, along with it; anything that cannot simply be ticked — a goal, a
 *   numeric or checklist target — opens on its goal page so the user can mark what is right.
 * - **Won't do** — settled with a grey cross; nothing in the goal is touched.
 * - **Postpone** — the event's own form, everything editable as when it was made.
 *
 * Mounted once (`__root.tsx`); `useEventDecision` says which event, if any.
 */
export function EventDecisionHost() {
  const eventId = useEventDecision((s) => s.eventId);
  const close = useEventDecision((s) => s.close);
  const events = useUpcoming((s) => s.events);
  const status = useUpcoming((s) => s.status);
  const load = useUpcoming((s) => s.load);
  const goals = useSpira((s) => s.goals);
  const [postponing, setPostponing] = useState<ResolvedUpcomingEvent | null>(
    null,
  );

  // A notification can open the card on a fresh page, before anything has loaded Upcoming.
  useEffect(() => {
    if (eventId && status === "idle") void load();
  }, [eventId, status, load]);

  const event = useMemo(
    () =>
      eventId
        ? (resolveAll(events, goals).find((e) => e.id === eventId) ?? null)
        : null,
    [eventId, events, goals],
  );

  return (
    <>
      <Dialog
        open={event !== null}
        onOpenChange={(open) => {
          if (!open) close();
        }}
      >
        <DialogContent
          closeButton={false}
          className="modal-max flex w-[calc(100%-32px)] max-w-[440px] flex-col gap-0 overflow-y-auto rounded-xl border-0 bg-white p-0 shadow-2xl sm:rounded-xl"
        >
          {event ? (
            <DecisionCard
              event={event}
              onClose={close}
              onPostpone={() => {
                close();
                setPostponing(event);
              }}
            />
          ) : null}
        </DialogContent>
      </Dialog>
      <AddEventDialog
        open={postponing !== null}
        onOpenChange={(open) => {
          if (!open) setPostponing(null);
        }}
        editing={postponing}
      />
    </>
  );
}

function DecisionCard({
  event,
  onClose,
  onPostpone,
}: {
  event: ResolvedUpcomingEvent;
  onClose: () => void;
  onPostpone: () => void;
}) {
  const resolve = useUpcoming((s) => s.resolve);
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const settled = (event.record.status ?? "pending") !== "pending";

  const kind =
    UPCOMING_CATEGORIES.find((c) => c.kind === event.kind)?.singular ??
    "Important date";
  const when = `${upcomingWhen(event.date)}${event.record.time ? ` at ${event.record.time}` : ""}`;

  const done = async () => {
    setBusy(true);
    const ok = await resolve(event.id, "done");
    setBusy(false);
    if (!ok) return;
    onClose();
    const opened = markLinkedItemDone(event);
    if (opened) {
      void navigate(opened);
      toast.success(`Marked done. Now mark "${event.title}" on its goal.`);
    }
  };

  const wontDo = async () => {
    setBusy(true);
    const ok = await resolve(event.id, "wont_do");
    setBusy(false);
    if (ok) onClose();
  };

  return (
    <div className="flex flex-col p-6">
      <div className="flex items-start gap-4">
        <p className="min-w-0 flex-1 pt-1 text-sm font-semibold uppercase tracking-wide text-primary">
          {kind}
        </p>
        <button
          type="button"
          onClick={onClose}
          className="-mr-1.5 grid h-7 w-7 shrink-0 place-items-center rounded-md text-muted-foreground/70 transition-colors hover:bg-secondary hover:text-foreground"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      {/* Bell and Pencil by Ladang Visual (Noun Project, CC BY 3.0) — Settings → Licences. */}
      <img
        src="/illustrations/reminder.svg"
        alt=""
        className="mx-auto mt-2 h-40 w-auto"
      />
      <DialogTitle className="mt-4 break-words text-center text-2xl font-medium leading-tight tracking-tight text-foreground [overflow-wrap:anywhere]">
        {event.title}
      </DialogTitle>
      <DialogDescription className="mt-2 text-center text-base text-foreground/80">
        {when}
      </DialogDescription>

      {settled ? (
        <p className="mt-8 text-center text-sm text-muted-foreground">
          {event.record.status === "done"
            ? "You marked this done."
            : "You decided not to do this."}
        </p>
      ) : (
        <div className="mt-8 flex flex-col gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={() => void done()}
            className="h-12 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
          >
            Done
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => void wontDo()}
            className="h-12 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
          >
            Won&apos;t do
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onPostpone}
            className="h-12 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
          >
            Postpone
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Ticks what a Done can tick on its own (owner, 2026-10-19): a task, or a binary target. For
 * anything else — a goal, a numeric or a checklist target — "done" is the user's to judge, so the
 * answer is where to take them: its goal page, scrolled to it. `undefined` when nothing is left to
 * do or the event is not a link.
 */
function markLinkedItemDone(event: ResolvedUpcomingEvent) {
  if (event.kind === "important") return undefined;
  const { goals, updateTarget } = useSpira.getState();
  const item = linkableItems(goals).find(
    (i) => i.kind === event.kind && i.id === linkedId(event.record),
  );
  if (!item) return undefined;
  const goal = goals.find((g) => g.id === item.goalId);
  const target = goal?.targets.find((t) => t.id === item.targetId);

  if (item.kind === "task" && target?.type === "checklist") {
    updateTarget(item.goalId, target.id, {
      items: target.items.map((task) =>
        task.id === item.id ? { ...task, done: true } : task,
      ),
    });
    return undefined;
  }
  if (item.kind === "target" && target?.type === "binary") {
    updateTarget(item.goalId, target.id, { done: true });
    return undefined;
  }
  return {
    to: "/goals/$goalId" as const,
    params: { goalId: item.goalId },
    hash: item.kind === "goal" ? "goal-top" : `target-${item.id}`,
  };
}
