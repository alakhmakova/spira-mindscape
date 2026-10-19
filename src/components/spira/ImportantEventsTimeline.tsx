import { useEffect, useMemo } from "react";
import { format } from "date-fns";
import { Check, X } from "@/components/spira/icons";
import { useEventDecision } from "@/lib/spira/event-decision";
import { useSpira } from "@/lib/spira/store";
import {
  resolveAll,
  UPCOMING_CATEGORIES,
  upcomingWhen,
  type ResolvedUpcomingEvent,
} from "@/lib/spira/upcoming";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import { cn } from "@/lib/utils";

/**
 * **Important events timeline** — every Upcoming event in date order, settled ones included
 * (owner, 2026-10-19, after the reference's "Payroll setup timeline", `gusto/timeline.png`).
 *
 * Each event is a 24px mark on a rail, its name, and a grey line under it:
 *
 * | Status | Mark | Line under the name | Rail below it |
 * |---|---|---|---|
 * | next pending | white, 2px Kale ring | when it is, and what | dashed grey |
 * | later pending | white, 2px grey ring | when it is, and what | dashed grey |
 * | done | solid Kale, white tick | "Done on …" | solid Kale |
 * | won't do | solid grey, white cross | "Won't do · decided on …" | solid grey |
 *
 * Only the FIRST pending event — the next one in line — wears the Kale ring; every pending event
 * after it is ringed in grey, as in the reference (owner, 2026-10-19).
 *
 * A pending event carries **Let's do it** — white, Kale once hovered — which opens the event's own
 * card (`EventDecision.tsx`), exactly as Open on its reminder does. A settled one has no button:
 * there is nothing left to decide.
 */
export function ImportantEventsTimeline() {
  const goals = useSpira((s) => s.goals);
  const events = useUpcoming((s) => s.events);
  const status = useUpcoming((s) => s.status);
  const load = useUpcoming((s) => s.load);
  const openEvent = useEventDecision((s) => s.open);

  useEffect(() => {
    if (status === "idle") void load();
  }, [status, load]);

  const items = useMemo(() => resolveAll(events, goals), [events, goals]);
  const nextId = items.find(
    (e) => (e.record.status ?? "pending") === "pending",
  )?.id;

  if (status === "error" && events.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Couldn&apos;t load your events.{" "}
        <button
          type="button"
          onClick={() => void load()}
          className="font-medium text-primary underline underline-offset-[3px]"
        >
          Try again
        </button>
      </p>
    );
  }
  if (status !== "ready" && events.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" role="status">
        Loading…
      </p>
    );
  }
  if (items.length === 0) {
    return (
      <p className="text-sm leading-relaxed text-muted-foreground">
        No important events yet. Add one with Add event under Upcoming, on the
        Cards view.
      </p>
    );
  }

  return (
    <ol className="pt-1" aria-label="Important events">
      {items.map((event, i) => (
        <TimelineEvent
          key={event.id}
          event={event}
          last={i === items.length - 1}
          next={event.id === nextId}
          onOpen={() => openEvent(event.id)}
        />
      ))}
    </ol>
  );
}

function TimelineEvent({
  event,
  last,
  next,
  onOpen,
}: {
  event: ResolvedUpcomingEvent;
  last: boolean;
  /** The first pending event — the one the Kale ring points at. */
  next: boolean;
  onOpen: () => void;
}) {
  const status = event.record.status ?? "pending";
  const kind =
    UPCOMING_CATEGORIES.find((c) => c.kind === event.kind)?.singular ??
    "Important date";
  const decidedOn = event.record.resolvedAt
    ? format(new Date(event.record.resolvedAt), "MMM d")
    : "";
  const line =
    status === "done"
      ? `Done on ${decidedOn}`
      : status === "wont_do"
        ? `Won't do · decided on ${decidedOn}`
        : `${upcomingWhen(event.date)}${event.record.time ? ` at ${event.record.time}` : ""} · ${kind}`;

  return (
    <li className="relative flex gap-4 pb-12 last:pb-0">
      {/* The rail to the next mark, from under this one: solid once this event is settled (Kale
          for done, grey for won't do), dashed while it is still ahead — the reference's own
          reading of "behind you" and "still to come". */}
      {last ? null : (
        <span
          aria-hidden="true"
          className={cn(
            "absolute bottom-0 left-[11px] top-6",
            status === "done"
              ? "border-l-2 border-primary"
              : status === "wont_do"
                ? "border-l-2 border-[#919197]"
                : "border-l border-dashed border-[#919197]",
          )}
        />
      )}
      <StatusMark status={status} next={next} />
      <div className="min-w-0 flex-1">
        <h3 className="break-words text-xl font-medium leading-6 text-foreground [overflow-wrap:anywhere]">
          {event.title}
        </h3>
        <p className="mt-1.5 text-sm text-muted-foreground">
          <span className="sr-only">
            {status === "done"
              ? "Done. "
              : status === "wont_do"
                ? "Won't do. "
                : "Still to do. "}
          </span>
          {line}
        </p>
        {status === "pending" ? (
          <button
            type="button"
            onClick={onOpen}
            aria-label={`Let's do it: ${event.title}`}
            className="mt-4 h-12 rounded-md border border-[#222525] bg-white px-6 text-[15px] font-semibold text-foreground transition-colors hover:border-primary hover:bg-primary hover:text-primary-foreground focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring"
          >
            Let&apos;s do it
          </button>
        ) : null}
      </div>
    </li>
  );
}

/** The 24px mark on the rail — see the table on {@link ImportantEventsTimeline}. */
function StatusMark({ status, next }: { status: string; next: boolean }) {
  if (status === "done") {
    return (
      <span
        aria-hidden="true"
        className="relative z-[1] grid h-6 w-6 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground"
      >
        <Check className="h-3.5 w-3.5" />
      </span>
    );
  }
  if (status === "wont_do") {
    return (
      <span
        aria-hidden="true"
        className="relative z-[1] grid h-6 w-6 shrink-0 place-items-center rounded-full bg-[#6C6C72] text-white"
      >
        <X className="h-3.5 w-3.5" />
      </span>
    );
  }
  return (
    <span
      aria-hidden="true"
      className={cn(
        "relative z-[1] h-6 w-6 shrink-0 rounded-full border-2 bg-white",
        next ? "border-primary" : "border-[#919197]",
      )}
    />
  );
}
