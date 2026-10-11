import { describe, expect, it } from "vitest";
import {
  ALL_DAY_PRESETS,
  TIMED_PRESETS,
  convertReminders,
  defaultReminder,
  fireAt,
  reminderLabel,
  reminderSentence,
  timeToMinute,
} from "./reminders";

/**
 * The reminder rules the Add event form shows. The dates are local midnight — how the form stores
 * a day — so these hold in whatever zone the test runs in.
 */
const JUNE_19 = new Date(2030, 5, 19).toISOString();
const EARLY = new Date(2030, 0, 1);

describe("presets", () => {
  it("an all-day date offers on the day, 1 day, 2 days and 1 week before, at 09:00", () => {
    expect(ALL_DAY_PRESETS.map((r) => reminderLabel(r, false))).toEqual([
      "On the day at 09:00",
      "1 day before at 09:00",
      "2 days before at 09:00",
      "1 week before at 09:00",
    ]);
  });

  it("a timed date offers minutes and hours as well", () => {
    expect(TIMED_PRESETS.map((r) => reminderLabel(r, true))).toEqual([
      "5 minutes before",
      "10 minutes before",
      "1 hour before",
      "2 hours before",
      "1 day before",
      "1 week before",
    ]);
  });

  it("the default is on the day at 09:00, or 1 hour before a timed date", () => {
    expect(reminderLabel(defaultReminder(false), false)).toBe(
      "On the day at 09:00",
    );
    expect(reminderLabel(defaultReminder(true), true)).toBe("1 hour before");
  });

  it("labels a custom one, and zero on a timed date", () => {
    expect(
      reminderLabel(
        { amount: 3, unit: "week", atMinuteOfDay: 18 * 60 + 30 },
        false,
      ),
    ).toBe("3 weeks before at 18:30");
    expect(reminderLabel({ amount: 0, unit: "minute" }, true)).toBe(
      "At the time of the event",
    );
  });
});

describe("fireAt", () => {
  it("all day: the day, minus days or weeks, at the time of day", () => {
    expect(fireAt(JUNE_19, undefined, ALL_DAY_PRESETS[0])).toEqual(
      new Date(2030, 5, 19, 9, 0),
    );
    expect(fireAt(JUNE_19, undefined, ALL_DAY_PRESETS[3])).toEqual(
      new Date(2030, 5, 12, 9, 0),
    );
  });

  it("timed: minutes and hours off the moment, days off the calendar", () => {
    expect(fireAt(JUNE_19, "14:00", { amount: 5, unit: "minute" })).toEqual(
      new Date(2030, 5, 19, 13, 55),
    );
    expect(fireAt(JUNE_19, "14:00", { amount: 1, unit: "day" })).toEqual(
      new Date(2030, 5, 18, 14, 0),
    );
  });

  it("reads a bare YYYY-MM-DD as that day", () => {
    expect(fireAt("2030-06-19", undefined, ALL_DAY_PRESETS[0])).toEqual(
      new Date(2030, 5, 19, 9, 0),
    );
  });
});

describe("reminderSentence", () => {
  it("says exactly when, with the weekday for one reminder", () => {
    expect(
      reminderSentence(JUNE_19, undefined, [ALL_DAY_PRESETS[0]], EARLY),
    ).toBe("You’ll be reminded on Wednesday, June 19 at 09:00.");
  });

  it("lists several in order", () => {
    expect(
      reminderSentence(
        JUNE_19,
        "14:00",
        [
          { amount: 1, unit: "hour" },
          { amount: 1, unit: "week" },
          { amount: 1, unit: "day" },
        ],
        EARLY,
      ),
    ).toBe(
      "You’ll be reminded on June 12 at 14:00, June 18 at 14:00 and June 19 at 13:00.",
    );
  });

  it("asks for a date first, and says when there is no reminder", () => {
    expect(reminderSentence(undefined, undefined, [ALL_DAY_PRESETS[0]])).toBe(
      "Pick a date to see when you’ll be reminded.",
    );
    expect(reminderSentence(JUNE_19, undefined, [])).toBe(
      "No reminder — this date will only show in Upcoming.",
    );
  });

  it("leaves out a moment already past, as the server will", () => {
    const after = new Date(2030, 5, 19, 10, 0);
    expect(
      reminderSentence(JUNE_19, undefined, [ALL_DAY_PRESETS[0]], after),
    ).toBe("This reminder’s moment has already passed, so it won’t go off.");
  });
});

describe("convertReminders", () => {
  it("the untouched default follows the switch both ways", () => {
    expect(convertReminders([defaultReminder(false)], true)).toEqual([
      defaultReminder(true),
    ]);
    expect(convertReminders([defaultReminder(true)], false)).toEqual([
      defaultReminder(false),
    ]);
  });

  it("days and weeks keep their number; minutes and hours cannot be all day", () => {
    expect(
      convertReminders(
        [
          { amount: 2, unit: "day" },
          { amount: 10, unit: "minute" },
        ],
        false,
      ),
    ).toEqual([
      { amount: 2, unit: "day", atMinuteOfDay: 540 },
      defaultReminder(false),
    ]);
  });
});

it("timeToMinute reads HH:mm only", () => {
  expect(timeToMinute("09:30")).toBe(570);
  expect(timeToMinute("24:00")).toBeUndefined();
  expect(timeToMinute("9:30")).toBeUndefined();
});
