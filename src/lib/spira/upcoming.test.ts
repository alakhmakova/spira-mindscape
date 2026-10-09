import { describe, expect, it } from "vitest";
import {
  UPCOMING_CATEGORIES,
  daysUntil,
  sampleUpcoming,
  upcomingWhen,
} from "./upcoming";

// A Friday, late in the evening, so "today" is a whole day and not the instant.
const TODAY = new Date(2026, 9, 9, 22, 30);

describe("upcomingWhen", () => {
  it("says Today and Tomorrow for the next two days", () => {
    expect(upcomingWhen("2026-10-09", TODAY)).toBe("Today");
    expect(upcomingWhen("2026-10-10", TODAY)).toBe("Tomorrow");
  });

  it("counts down through the last week", () => {
    expect(upcomingWhen("2026-10-11", TODAY)).toBe("In 2 days");
    expect(upcomingWhen("2026-10-16", TODAY)).toBe("In 7 days");
  });

  it("gives the date, weekday first, beyond a week", () => {
    expect(upcomingWhen("2026-10-17", TODAY)).toBe("Sat Oct 17");
    expect(upcomingWhen("2026-12-01", TODAY)).toBe("Tue Dec 1");
  });

  it("counts calendar days, not 24-hour spans", () => {
    expect(daysUntil("2026-10-10", new Date(2026, 9, 9, 23, 59))).toBe(1);
    expect(daysUntil("2026-10-09", new Date(2026, 9, 9, 0, 0))).toBe(0);
  });
});

describe("sampleUpcoming", () => {
  it("has every category, in order, each with one or two events", () => {
    const groups = sampleUpcoming(TODAY);
    expect(groups.map((g) => g.label)).toEqual(
      UPCOMING_CATEGORIES.map((c) => c.label),
    );
    for (const g of groups) {
      expect(g.events.length).toBeGreaterThanOrEqual(1);
      expect(g.events.length).toBeLessThanOrEqual(2);
    }
  });

  it("orders each group by date", () => {
    for (const g of sampleUpcoming(TODAY)) {
      const dates = g.events.map((e) => e.date);
      expect(dates).toEqual([...dates].sort());
    }
  });
});
