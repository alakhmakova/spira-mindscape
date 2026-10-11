import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { DecideNow } from "@/components/spira/DecideNow";
import { useDueReminders } from "@/lib/spira/due-reminders";

/**
 * Where a tapped reminder notification lands (plan §5a): the "decide now" page for that event.
 * The service worker focuses an open Spira tab and navigates it here, or opens a new one.
 *
 * If the reminder is no longer due — answered on another device, or from the notification's own
 * buttons — there is nothing to decide, and the app simply opens.
 */
export const Route = createFileRoute("/remind/$eventId")({
  head: () => ({ meta: [{ title: "Reminder — Spira" }] }),
  component: RemindPage,
});

function RemindPage() {
  const { eventId } = Route.useParams();
  const navigate = useNavigate();
  const due = useDueReminders((s) => s.due);
  const loaded = useDueReminders((s) => s.loaded);
  const refresh = useDueReminders((s) => s.refresh);
  const reminder = due.find((d) => d.eventId === eventId);

  // Fresh on arrival: the list may predate the notification that brought the user here.
  useEffect(() => {
    void refresh();
  }, [refresh, eventId]);

  useEffect(() => {
    if (loaded && !reminder) void navigate({ to: "/", replace: true });
  }, [loaded, reminder, navigate]);

  if (!reminder) return null;
  return (
    <DecideNow
      reminder={reminder}
      remaining={due.length - 1}
      onAnswered={(action) => {
        if (action !== "open") void navigate({ to: "/", replace: true });
      }}
    />
  );
}
