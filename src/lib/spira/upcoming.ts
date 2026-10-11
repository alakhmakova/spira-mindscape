/**
 * The All-goals page's **Upcoming** column: the dates the user has chosen to keep an eye on.
 *
 * An event is one of two shapes (owner, 2026-10-09):
 *
 *  - an **important date** of the user's own — a name and a date;
 *  - a **link** to a goal, a target or a task (a checklist item) that has a deadline. A link has
 *    no date of its own: it shows the item's deadline, read live from the goal graph, so moving
 *    the deadline anywhere moves the event with it, and the item's own name is the event's name.
 *
 * The column shows **at most ten** events across all its categories together, the nearest first,
 * from today on, and a category with nothing in it is not shown at all.
 */
import type { Reminder } from "./reminders";
import type { Goal } from "./types";

export type UpcomingKind = "important" | "goal" | "target" | "task";

/** An event as the server stores it. A link carries ids only — see the file comment. */
export type UpcomingEventRecord = {
  id: string;
  kind: UpcomingKind;
  title?: string;
  date?: string;
  goalId?: string;
  targetId?: string;
  taskId?: string;
  /** `HH:mm`, or absent for an all-day date — the entry's own, whatever its kind. */
  time?: string;
  reminders?: Reminder[];
};

/** An event ready to draw: its name and its date, whichever shape it came from. */
export type ResolvedUpcomingEvent = {
  id: string;
  kind: UpcomingKind;
  title: string;
  date: string;
  record: UpcomingEventRecord;
};

export type UpcomingGroup = {
  kind: UpcomingKind;
  label: string;
  events: ResolvedUpcomingEvent[];
};

/** How many events the column shows, across every category together. */
export const UPCOMING_LIMIT = 10;

/**
 * The categories, in the order they stand, with the word each is shown under in the column and
 * the word used for one of them in the Add event form. "Recurring" was here once and is gone
 * (owner, 2026-10-09) — a goal took its place.
 */
export const UPCOMING_CATEGORIES: {
  kind: UpcomingKind;
  label: string;
  singular: string;
}[] = [
  { kind: "important", label: "Important dates", singular: "Important date" },
  { kind: "goal", label: "Goals", singular: "Goal" },
  { kind: "target", label: "Targets", singular: "Target" },
  { kind: "task", label: "Tasks", singular: "Task" },
];

const DAY_ONLY = /^\d{4}-\d{2}-\d{2}$/;

/**
 * The local calendar day an ISO string stands for. A bare `YYYY-MM-DD` is that day as written —
 * `new Date("2026-10-11")` would read it as UTC midnight, the previous evening west of Greenwich —
 * and a full instant is the day it falls on here.
 */
function localDay(iso: string): Date {
  if (DAY_ONLY.test(iso)) {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d);
  }
  const t = new Date(iso);
  return new Date(t.getFullYear(), t.getMonth(), t.getDate());
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** Whole calendar days from `today` to `iso`; negative when it has passed. */
export function daysUntil(iso: string, today: Date = new Date()): number {
  // `Math.round` rather than a floor: a daylight-saving change makes one day 23 or 25 hours long.
  return Math.round(
    (localDay(iso).getTime() - startOfDay(today).getTime()) / 86_400_000,
  );
}

/**
 * The line under an event's name.
 *
 * - the day itself → **Today**, the next → **Tomorrow**;
 * - within the last week → a countdown, **In 5 days**;
 * - further out → the date, **Mon Oct 19** — the reference's own format, weekday first.
 */
export function upcomingWhen(iso: string, today: Date = new Date()): string {
  const days = daysUntil(iso, today);
  if (days === 0) return "Today";
  if (days === 1) return "Tomorrow";
  if (days > 1 && days <= 7) return `In ${days} days`;
  const d = localDay(iso);
  const weekday = d.toLocaleDateString("en-US", { weekday: "short" });
  const monthDay = d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
  return `${weekday} ${monthDay}`;
}

/** A goal, target or task the Add event form can link to: it has a deadline and a server id. */
export type LinkableItem = {
  /** The id the event will store: the goal's, the target's or the checklist item's. */
  id: string;
  kind: Exclude<UpcomingKind, "important">;
  title: string;
  deadline: string;
  goalId: string;
  targetId?: string;
};

/** Whether an id is the server's. An item created a moment ago still has a `local-` one. */
function isServerId(id: string) {
  return !id.startsWith("local-");
}

/** Every goal, target and task that has a deadline, by kind — what a link can point at. */
export function linkableItems(goals: Goal[]): LinkableItem[] {
  const items: LinkableItem[] = [];
  for (const goal of goals) {
    if (!isServerId(goal.id)) continue;
    if (goal.deadline) {
      items.push({
        id: goal.id,
        kind: "goal",
        title: goal.title || "Untitled goal",
        deadline: goal.deadline,
        goalId: goal.id,
      });
    }
    for (const target of goal.targets) {
      if (!isServerId(target.id)) continue;
      if (target.deadline) {
        items.push({
          id: target.id,
          kind: "target",
          title: target.title || "Untitled target",
          deadline: target.deadline,
          goalId: goal.id,
          targetId: target.id,
        });
      }
      if (target.type !== "checklist") continue;
      for (const task of target.items) {
        if (!isServerId(task.id) || !task.deadline) continue;
        items.push({
          id: task.id,
          kind: "task",
          title: task.text || "Untitled task",
          deadline: task.deadline,
          goalId: goal.id,
          targetId: target.id,
        });
      }
    }
  }
  return items;
}

/** The id a link points at, whatever its kind. */
export function linkedId(record: UpcomingEventRecord): string | undefined {
  if (record.kind === "goal") return record.goalId;
  if (record.kind === "target") return record.targetId;
  if (record.kind === "task") return record.taskId;
  return undefined;
}

/**
 * The events the column draws: each link resolved against the goal graph, anything that has
 * passed or lost its deadline dropped, the nearest {@link UPCOMING_LIMIT} kept, and grouped by
 * category in the column's order with the empty categories left out.
 *
 * A link whose item is gone, or whose deadline was cleared somewhere else, simply does not show:
 * the server removes it with a deleted item, and a deadline set again brings it back.
 */
export function resolveUpcoming(
  records: UpcomingEventRecord[],
  goals: Goal[],
  today: Date = new Date(),
  limit: number = UPCOMING_LIMIT,
): UpcomingGroup[] {
  const byKindAndId = new Map(
    linkableItems(goals).map((item) => [`${item.kind}:${item.id}`, item]),
  );

  const resolved: ResolvedUpcomingEvent[] = [];
  for (const record of records) {
    let title: string | undefined;
    let date: string | undefined;
    if (record.kind === "important") {
      title = record.title;
      date = record.date;
    } else {
      const item = byKindAndId.get(`${record.kind}:${linkedId(record)}`);
      title = item?.title;
      date = item?.deadline;
    }
    if (!title || !date || daysUntil(date, today) < 0) continue;
    resolved.push({ id: record.id, kind: record.kind, title, date, record });
  }

  const nearest = resolved
    .sort(
      (a, b) =>
        localDay(a.date).getTime() - localDay(b.date).getTime() ||
        a.title.localeCompare(b.title),
    )
    .slice(0, limit);

  return UPCOMING_CATEGORIES.map(({ kind, label }) => ({
    kind,
    label,
    events: nearest.filter((e) => e.kind === kind),
  })).filter((group) => group.events.length > 0);
}

/** Whether two ISO strings fall on the same local calendar day. */
export function sameDay(a: string, b: string): boolean {
  return localDay(a).getTime() === localDay(b).getTime();
}
