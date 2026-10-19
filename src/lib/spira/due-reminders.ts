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
/**
 * What a reminder can be answered with (owner, 2026-10-19). Ahead of the event's day a reminder
 * only informs: **dismiss** ("I know") lets it go and the next one still comes. On the day it asks
 * for a decision: **open** brings up the event's own card (Done / Won't do / Postpone). **snooze**
 * is offered either way. None of these settles the event itself — the card does.
 */
export type ReminderAnswer = "dismiss" | "open" | "snooze";

/**
 * A Snooze is a number and a unit, both the user's own (owner, 2026-10-19: "пусть пользователь
 * реально сам выберет и цифру и в чем исчисляется"). The server takes the same pair
 * (`ReminderService.snoozeUntil`) and counts days, weeks and months on the user's calendar.
 */
export type SnoozeUnit = "minute" | "hour" | "day" | "week" | "month";
export type Snooze = { amount: number; unit: SnoozeUnit };

export const MAX_SNOOZE_AMOUNT = 999;
/** What the picker shows first — the old fixed snooze. */
export const DEFAULT_SNOOZE: Snooze = { amount: 10, unit: "minute" };

export const SNOOZE_UNITS: { unit: SnoozeUnit; one: string; many: string }[] = [
  { unit: "minute", one: "minute", many: "minutes" },
  { unit: "hour", one: "hour", many: "hours" },
  { unit: "day", one: "day", many: "days" },
  { unit: "week", one: "week", many: "weeks" },
  { unit: "month", one: "month", many: "months" },
];

/** "10 minutes", "1 day", "3 months". */
export function snoozeLabel({ amount, unit }: Snooze): string {
  const words = SNOOZE_UNITS.find((u) => u.unit === unit) ?? SNOOZE_UNITS[0];
  return `${amount} ${amount === 1 ? words.one : words.many}`;
}

/** Why the typed number cannot be a snooze, or `undefined` when it can. */
export function snoozeAmountError(text: string): string | undefined {
  const value = Number(text);
  if (
    !/^\d{1,3}$/.test(text.trim()) ||
    value < 1 ||
    value > MAX_SNOOZE_AMOUNT
  ) {
    return `Enter a number from 1 to ${MAX_SNOOZE_AMOUNT}`;
  }
  return undefined;
}

type DueState = {
  due: DueReminder[];
  loaded: boolean;
  refresh: () => Promise<void>;
  /** Answers one event's reminders. Resolves `false` (with a toast) if the server refused. */
  answer: (
    eventId: string,
    action: ReminderAnswer,
    snooze?: Snooze,
  ) => Promise<boolean>;
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

  answer: async (eventId, action, snooze) => {
    const previous = get().due;
    set({ due: previous.filter((d) => d.eventId !== eventId) });
    try {
      await spiraApi.answerReminder(eventId, action, snooze);
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
    const data = event.data as {
      type?: string;
      eventId?: string;
      then?: string;
    } | null;
    if (data?.type === "reminders-changed") refresh();
    // A tapped notification, for a tab the worker could not navigate itself (one it does not
    // control yet): the page goes to the "decide now" page on its own.
    if (data?.type === "open-reminder" && data.eventId) {
      window.location.assign(
        `/remind/${encodeURIComponent(data.eventId)}${data.then === "snooze" ? "?snooze=1" : data.then === "open" ? "?open=1" : ""}`,
      );
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
