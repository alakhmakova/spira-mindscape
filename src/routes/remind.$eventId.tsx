import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { DecideNow } from "@/components/spira/DecideNow";
import { useDueReminders } from "@/lib/spira/due-reminders";
import { useEventDecision } from "@/lib/spira/event-decision";

/**
 * Where a tapped reminder notification lands (plan §5a): the "decide now" page for that event.
 * The service worker focuses an open Spira tab and navigates it here, or opens a new one.
 *
 * `?snooze=1` — the notification's Snooze button — opens the page on the snooze picker, so the
 * user picks how long rather than the notification guessing. `?open=1` — its Open button, on the
 * event's day — answers the reminder and brings up the event's own card straight away.
 *
 * Every answer leaves for All goals; the event's card, when Open asked for it, is app-wide and
 * stays up over that page.
 *
 * If the reminder is no longer due — answered on another device, or from the notification's own
 * buttons — there is nothing to decide, and the app simply opens.
 */
export const Route = createFileRoute("/remind/$eventId")({
  head: () => ({ meta: [{ title: "Reminder — Spira" }] }),
  validateSearch: (
    search: Record<string, unknown>,
  ): { snooze?: 1; open?: 1 } => ({
    ...(search.snooze === 1 || search.snooze === "1" ? { snooze: 1 } : {}),
    ...(search.open === 1 || search.open === "1" ? { open: 1 } : {}),
  }),
  component: RemindPage,
});

function RemindPage() {
  const { eventId } = Route.useParams();
  const { snooze, open } = Route.useSearch();
  const answer = useDueReminders((s) => s.answer);
  const openEvent = useEventDecision((s) => s.open);
  const navigate = useNavigate();
  const due = useDueReminders((s) => s.due);
  const loaded = useDueReminders((s) => s.loaded);
  const refresh = useDueReminders((s) => s.refresh);
  const reminder = due.find((d) => d.eventId === eventId);

  // Fresh on arrival: the list may predate the notification that brought the user here.
  useEffect(() => {
    void refresh();
  }, [refresh, eventId]);

  // The notification's Open: answer the reminder if it is still due, then show the card — it is
  // the card the user asked for, even if the reminder was answered on another device meanwhile.
  const opened = useRef(false);
  useEffect(() => {
    if (!open || !loaded || opened.current) return;
    opened.current = true;
    if (reminder) void answer(eventId, "open");
    openEvent(eventId);
    void navigate({ to: "/", replace: true });
  }, [open, loaded, reminder, answer, eventId, openEvent, navigate]);

  useEffect(() => {
    if (!open && loaded && !reminder) void navigate({ to: "/", replace: true });
  }, [open, loaded, reminder, navigate]);

  if (!reminder || open) return null;
  return (
    <DecideNow
      reminder={reminder}
      remaining={due.length - 1}
      initialSnooze={snooze === 1}
      onAnswered={() => void navigate({ to: "/", replace: true })}
    />
  );
}
