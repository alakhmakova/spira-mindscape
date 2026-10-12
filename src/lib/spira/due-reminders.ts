import { create } from "zustand";
import { toast } from "sonner";
import { spiraApi, type DueReminder } from "./api";
import { upcomingWhen } from "./upcoming";
import { syncTimeZone } from "./upcoming-store";
import { startWebPush } from "./web-push";

/**
 * Reminders that have gone off and wait for an answer — what the full-screen "decide now" page
 * shows (specs/2026-10-10-upcoming-notifications/plan.md §5a).
 *
 * "Due" is the **server's** fact (`answered_at` on the reminder), so it holds across devices:
 * answered on the laptop, the phone lets go on its next refresh. The app polls while it is open
 * and refreshes whenever it comes back to the front, so a reminder that goes off while Spira is
 * open takes the screen within half a minute even without a push.
 */
export type ReminderAnswer = "done" | "open" | "snooze";

type DueState = {
  due: DueReminder[];
  loaded: boolean;
  refresh: () => Promise<void>;
  /** Answers one event's reminders. Resolves `false` (with a toast) if the server refused. */
  answer: (eventId: string, action: ReminderAnswer) => Promise<boolean>;
};

export const POLL_MS = 30_000;

let inFlight: Promise<void> | null = null;

export const useDueReminders = create<DueState>((set, get) => ({
  due: [],
  loaded: false,

  refresh: () => {
    inFlight ??= spiraApi
      .fetchDueReminders()
      .then((due) => set({ due, loaded: true }))
      // A failed poll changes nothing on screen: the next one, half a minute later, tries again.
      .catch(() => set({ loaded: true }))
      .finally(() => {
        inFlight = null;
      });
    return inFlight;
  },

  answer: async (eventId, action) => {
    const previous = get().due;
    set({ due: previous.filter((d) => d.eventId !== eventId) });
    try {
      await spiraApi.answerReminder(eventId, action);
      return true;
    } catch (error) {
      set({ due: previous });
      toast.error(
        error instanceof Error && error.message
          ? error.message
          : "Couldn’t save your answer. Please try again.",
      );
      return false;
    }
  },
}));

/**
 * Keeps the due list fresh while a signed-in page is open: on start, every {@link POLL_MS}, when
 * the tab comes back to the front, and when the service worker says a reminder has just gone off.
 * Also tells the server the device's time zone once, and registers the service worker.
 */
export function startDueReminderPolling(): () => void {
  const refresh = () => void useDueReminders.getState().refresh();
  void syncTimeZone();
  void startWebPush();
  refresh();
  const timer = window.setInterval(refresh, POLL_MS);
  const onVisible = () => {
    if (document.visibilityState === "visible") refresh();
  };
  const onMessage = (event: MessageEvent) => {
    const data = event.data as { type?: string; eventId?: string } | null;
    if (data?.type === "reminders-changed") refresh();
    // A tapped notification, for a tab the worker could not navigate itself (one it does not
    // control yet): the page goes to the "decide now" page on its own.
    if (data?.type === "open-reminder" && data.eventId) {
      window.location.assign(`/remind/${encodeURIComponent(data.eventId)}`);
    }
  };
  document.addEventListener("visibilitychange", onVisible);
  window.addEventListener("focus", refresh);
  navigator.serviceWorker?.addEventListener("message", onMessage);
  return () => {
    window.clearInterval(timer);
    document.removeEventListener("visibilitychange", onVisible);
    window.removeEventListener("focus", refresh);
    navigator.serviceWorker?.removeEventListener("message", onMessage);
  };
}

/** "Today at 14:00", "Tomorrow", "Mon Oct 19" — the Upcoming column's own words. */
export function reminderWhen(reminder: Pick<DueReminder, "date" | "time">) {
  if (!reminder.date) return "";
  const when = upcomingWhen(reminder.date);
  return reminder.time ? `${when} at ${reminder.time}` : when;
}
