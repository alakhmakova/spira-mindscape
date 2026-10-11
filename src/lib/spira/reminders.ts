/**
 * Reminders on an Upcoming date (specs/2026-10-10-upcoming-notifications/plan.md, §1–2).
 *
 * A reminder is "{amount} {unit}s before", and on an **all-day** date also "… at {time of day}".
 * The server computes when it fires in the user's zone (`ReminderSchedule.java`); `fireAt` here
 * is the same rule in the device's zone, which is the zone the server is told about, so the
 * sentence under the form says the moment the server will use.
 *
 * - **All day**: days and weeks before, at a time of day (09:00 unless chosen). `0` days is
 *   "on the day".
 * - **Timed**: minutes and hours come off the moment itself, days and weeks off the calendar (the
 *   event's wall-clock time kept across a daylight-saving change). `0` is "at the time".
 */
import { format } from "date-fns";

export type ReminderUnit = "minute" | "hour" | "day" | "week";

export type Reminder = {
  amount: number;
  unit: ReminderUnit;
  /** All-day dates only: minutes after midnight. */
  atMinuteOfDay?: number;
};

/** At most this many per event — the server refuses a sixth. */
export const MAX_REMINDERS = 5;
export const MAX_AMOUNT = 999;
/** 09:00 — what an all-day reminder means when no time of day is chosen. */
export const DEFAULT_MINUTE = 9 * 60;

export const ALL_DAY_PRESETS: Reminder[] = [
  { amount: 0, unit: "day", atMinuteOfDay: DEFAULT_MINUTE },
  { amount: 1, unit: "day", atMinuteOfDay: DEFAULT_MINUTE },
  { amount: 2, unit: "day", atMinuteOfDay: DEFAULT_MINUTE },
  { amount: 1, unit: "week", atMinuteOfDay: DEFAULT_MINUTE },
];

export const TIMED_PRESETS: Reminder[] = [
  { amount: 5, unit: "minute" },
  { amount: 10, unit: "minute" },
  { amount: 1, unit: "hour" },
  { amount: 2, unit: "hour" },
  { amount: 1, unit: "day" },
  { amount: 1, unit: "week" },
];

export function presetsFor(timed: boolean): Reminder[] {
  return timed ? TIMED_PRESETS : ALL_DAY_PRESETS;
}

/** Every new event gets one (owner, 2026-10-10): on the day at 09:00, or 1 hour before. */
export function defaultReminder(timed: boolean): Reminder {
  return timed ? TIMED_PRESETS[2] : ALL_DAY_PRESETS[0];
}

/** A stable identity — the Select's value, and how duplicates are recognised. */
export function reminderKey(r: Reminder): string {
  return `${r.amount}:${r.unit}:${r.atMinuteOfDay ?? ""}`;
}

export function sameReminder(a: Reminder, b: Reminder): boolean {
  return reminderKey(a) === reminderKey(b);
}

export function minuteToTime(minute: number): string {
  const h = Math.floor(minute / 60);
  const m = minute % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/** `HH:mm` → minutes after midnight, or `undefined` when it is not a time. */
export function timeToMinute(time: string): number | undefined {
  const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time.trim());
  return match ? Number(match[1]) * 60 + Number(match[2]) : undefined;
}

const UNIT_WORDS: Record<ReminderUnit, [string, string]> = {
  minute: ["minute", "minutes"],
  hour: ["hour", "hours"],
  day: ["day", "days"],
  week: ["week", "weeks"],
};

/** "1 day before at 09:00", "On the day at 09:00", "10 minutes before", "At the time of the event". */
export function reminderLabel(r: Reminder, timed: boolean): string {
  const at = timed
    ? ""
    : ` at ${minuteToTime(r.atMinuteOfDay ?? DEFAULT_MINUTE)}`;
  if (r.amount === 0) {
    return timed ? "At the time of the event" : `On the day${at}`;
  }
  const [one, many] = UNIT_WORDS[r.unit];
  return `${r.amount} ${r.amount === 1 ? one : many} before${at}`;
}

/** The local calendar day an event's ISO date stands for (stored as local midnight). */
function dayOf(iso: string): { y: number; m: number; d: number } {
  const bare = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (bare)
    return { y: Number(bare[1]), m: Number(bare[2]) - 1, d: Number(bare[3]) };
  const t = new Date(iso);
  return { y: t.getFullYear(), m: t.getMonth(), d: t.getDate() };
}

/** When a reminder fires, in the device's zone — the client half of `ReminderSchedule.fireAt`. */
export function fireAt(
  dateIso: string,
  time: string | undefined,
  r: Reminder,
): Date {
  const { y, m, d } = dayOf(dateIso);
  const eventMinute = time ? timeToMinute(time) : undefined;
  if (eventMinute !== undefined) {
    const h = Math.floor(eventMinute / 60);
    const min = eventMinute % 60;
    if (r.unit === "minute" || r.unit === "hour") {
      const at = new Date(y, m, d, h, min).getTime();
      return new Date(at - r.amount * (r.unit === "hour" ? 3_600_000 : 60_000));
    }
    const days = r.unit === "week" ? r.amount * 7 : r.amount;
    return new Date(y, m, d - days, h, min);
  }
  const minute = r.atMinuteOfDay ?? DEFAULT_MINUTE;
  const days =
    r.unit === "week" ? r.amount * 7 : r.unit === "day" ? r.amount : 0;
  const base = new Date(y, m, d - days, Math.floor(minute / 60), minute % 60);
  if (r.unit === "hour") return new Date(base.getTime() - r.amount * 3_600_000);
  if (r.unit === "minute") return new Date(base.getTime() - r.amount * 60_000);
  return base;
}

function joinAnd(parts: string[]): string {
  if (parts.length <= 1) return parts.join("");
  return `${parts.slice(0, -1).join(", ")} and ${parts[parts.length - 1]}`;
}

/**
 * The sentence under the reminders — **when, exactly, the user will be reminded** (owner,
 * 2026-10-10: the default must never be invisible). Moments already past are left out, because
 * the server will not fire them either.
 */
export function reminderSentence(
  dateIso: string | undefined,
  time: string | undefined,
  reminders: Reminder[],
  now: Date = new Date(),
): string {
  if (reminders.length === 0) {
    return "No reminder — this date will only show in Upcoming.";
  }
  if (!dateIso) return "Pick a date to see when you’ll be reminded.";
  const moments = reminders
    .map((r) => fireAt(dateIso, time, r))
    .filter((t) => t.getTime() >= now.getTime())
    .sort((a, b) => a.getTime() - b.getTime())
    .filter((t, i, all) => i === 0 || t.getTime() !== all[i - 1].getTime());
  if (moments.length === 0) {
    return reminders.length === 1
      ? "This reminder’s moment has already passed, so it won’t go off."
      : "These reminders’ moments have already passed, so they won’t go off.";
  }
  if (moments.length === 1) {
    return `You’ll be reminded on ${format(moments[0], "EEEE, MMMM d")} at ${format(moments[0], "HH:mm")}.`;
  }
  return `You’ll be reminded on ${joinAnd(
    moments.map((t) => `${format(t, "MMMM d")} at ${format(t, "HH:mm")}`),
  )}.`;
}

/**
 * The reminders an event keeps when it switches between all day and a time. The untouched
 * default follows the switch (on the day at 09:00 ↔ 1 hour before); days and weeks keep their
 * number; minutes and hours, which an all-day date cannot have, fall back to the default.
 */
export function convertReminders(
  reminders: Reminder[],
  toTimed: boolean,
): Reminder[] {
  if (
    reminders.length === 1 &&
    sameReminder(reminders[0], defaultReminder(!toTimed))
  ) {
    return [defaultReminder(toTimed)];
  }
  const out: Reminder[] = [];
  for (const r of reminders) {
    let next: Reminder;
    if (toTimed) {
      next =
        r.amount === 0
          ? defaultReminder(true)
          : { amount: r.amount, unit: r.unit };
    } else if (r.unit === "minute" || r.unit === "hour") {
      next = defaultReminder(false);
    } else {
      next = {
        amount: r.amount,
        unit: r.unit,
        atMinuteOfDay: r.atMinuteOfDay ?? DEFAULT_MINUTE,
      };
    }
    if (!out.some((o) => sameReminder(o, next))) out.push(next);
  }
  return out;
}

/** The device's IANA zone — what the server computes reminders in. */
export function deviceTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
}

/** "Enter a number from 1 to 999", or nothing when the field is fine. */
export function amountError(text: string): string | undefined {
  const value = Number(text);
  if (!/^\d{1,3}$/.test(text.trim()) || value < 1 || value > MAX_AMOUNT) {
    return `Enter a number from 1 to ${MAX_AMOUNT}`;
  }
  return undefined;
}
