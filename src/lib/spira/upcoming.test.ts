import { describe, expect, it } from "vitest";
import type { Goal, Target } from "./types";
import {
  UPCOMING_LIMIT,
  daysUntil,
  linkableItems,
  resolveUpcoming,
  sameDay,
  upcomingWhen,
  type UpcomingEventRecord,
} from "./upcoming";

// A Friday, late in the evening, so "today" is a whole day and not the instant.
const TODAY = new Date(2026, 9, 9, 22, 30);

/** Local midnight `n` days from TODAY, as the app stores deadlines (`toISOString`). */
const inDays = (n: number) => new Date(2026, 9, 9 + n).toISOString();

function goal(id: string, patch: Partial<Goal> = {}): Goal {
  return {
    id,
    title: `Goal ${id}`,
    description: "",
    confidence: 5,
    createdAt: inDays(-30),
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [],
    ...patch,
  };
}

const checklist = (
  id: string,
  deadline: string | undefined,
  tasks: { id: string; text: string; deadline?: string }[],
): Target => ({
  id,
  type: "checklist",
  title: `Target ${id}`,
  deadline,
  items: tasks.map((t) => ({ ...t, done: false })),
});

const important = (id: string, n: number): UpcomingEventRecord => ({
  id,
  kind: "important",
  title: `Date ${id}`,
  date: inDays(n),
});

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

  it("reads a stored instant as the local day it falls on", () => {
    expect(upcomingWhen(inDays(1), TODAY)).toBe("Tomorrow");
    expect(sameDay(inDays(3), "2026-10-12")).toBe(true);
  });

  it("counts calendar days, not 24-hour spans", () => {
    expect(daysUntil("2026-10-10", new Date(2026, 9, 9, 23, 59))).toBe(1);
    expect(daysUntil("2026-10-09", new Date(2026, 9, 9, 0, 0))).toBe(0);
  });
});

describe("linkableItems", () => {
  it("offers every goal, target and task with a deadline, and nothing without one", () => {
    const goals = [
      goal("1", {
        deadline: inDays(5),
        targets: [
          checklist("10", inDays(4), [
            { id: "100", text: "Long run", deadline: inDays(2) },
            { id: "101", text: "Rest day" },
          ]),
          checklist("11", undefined, []),
        ],
      }),
      goal("2"),
    ];
    expect(linkableItems(goals).map((i) => `${i.kind}:${i.id}`)).toEqual([
      "goal:1",
      "target:10",
      "task:100",
    ]);
  });

  it("leaves out what the server has not given an id yet", () => {
    expect(linkableItems([goal("local-1", { deadline: inDays(1) })])).toEqual(
      [],
    );
  });
});

describe("resolveUpcoming", () => {
  it("names a link after its item and dates it by the item's deadline", () => {
    const goals = [
      goal("1", {
        title: "Run a marathon",
        deadline: inDays(5),
        targets: [
          checklist("10", undefined, [
            { id: "100", text: "Long run", deadline: inDays(2) },
          ]),
        ],
      }),
    ];
    const groups = resolveUpcoming(
      [
        { id: "a", kind: "goal", goalId: "1" },
        { id: "b", kind: "task", taskId: "100" },
      ],
      goals,
      TODAY,
    );
    expect(groups.map((g) => g.label)).toEqual(["Goals", "Tasks"]);
    expect(groups[0].events[0]).toMatchObject({
      title: "Run a marathon",
      date: inDays(5),
    });
    expect(groups[1].events[0].title).toBe("Long run");
  });

  it("keeps the nearest ten across ALL categories, not ten in each", () => {
    const records = Array.from({ length: 14 }, (_, i) =>
      important(`d${i}`, 13 - i),
    );
    const groups = resolveUpcoming(records, [], TODAY);
    const shown = groups.flatMap((g) => g.events);
    expect(shown).toHaveLength(UPCOMING_LIMIT);
    expect(shown.map((e) => daysUntil(e.date, TODAY))).toEqual([
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
    ]);
  });

  it("drops what has passed, and links whose item or deadline is gone", () => {
    const goals = [goal("1"), goal("2", { deadline: inDays(-1) })];
    const groups = resolveUpcoming(
      [
        important("past", -2),
        { id: "x", kind: "goal", goalId: "1" }, // no deadline
        { id: "y", kind: "goal", goalId: "2" }, // overdue
        { id: "z", kind: "target", targetId: "999" }, // deleted
        important("today", 0),
      ],
      goals,
      TODAY,
    );
    expect(groups).toHaveLength(1);
    expect(groups[0].events.map((e) => e.id)).toEqual(["today"]);
  });

  it("leaves an empty category out, and keeps the column's order", () => {
    const goals = [goal("1", { deadline: inDays(1) })];
    const groups = resolveUpcoming(
      [{ id: "g", kind: "goal", goalId: "1" }, important("i", 3)],
      goals,
      TODAY,
    );
    expect(groups.map((g) => g.kind)).toEqual(["important", "goal"]);
  });
});
