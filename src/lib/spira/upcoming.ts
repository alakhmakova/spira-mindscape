/**
 * The All-goals page's **Upcoming** column: what is coming up, grouped by kind.
 *
 * The events are hardcoded for now (owner, 2026-10-09: "пока захардкодь по 1-2 события в каждую
 * категорию, потом реализуем реальную возможность добавления событий"). They are stored as an
 * offset in days from today rather than as dates, so the column always shows the full range of
 * labels — Today, Tomorrow, a countdown and a plain date — whatever day it is opened on.
 */

export type UpcomingCategory = "important" | "recurring" | "targets" | "tasks";

export type UpcomingEvent = {
  id: string;
  title: string;
  /** Local calendar day, `YYYY-MM-DD`. */
  date: string;
};

export type UpcomingGroup = {
  category: UpcomingCategory;
  label: string;
  events: UpcomingEvent[];
};

/** The order the categories stand in, and the words they are shown under. */
export const UPCOMING_CATEGORIES: {
  category: UpcomingCategory;
  label: string;
}[] = [
  { category: "important", label: "Important dates" },
  { category: "recurring", label: "Recurring" },
  { category: "targets", label: "Targets" },
  { category: "tasks", label: "Tasks" },
];

/** The hardcoded events, as days from today. Replace with stored events once they can be added. */
const SAMPLE: { category: UpcomingCategory; title: string; inDays: number }[] =
  [
    { category: "important", title: "Mom's birthday", inDays: 0 },
    { category: "important", title: "Passport renewal deadline", inDays: 12 },
    { category: "recurring", title: "Weekly review", inDays: 1 },
    { category: "recurring", title: "Monthly budget check", inDays: 5 },
    { category: "targets", title: "Run 10 km without stopping", inDays: 3 },
    {
      category: "targets",
      title: "Finish chapter 4 of User Story Mapping",
      inDays: 9,
    },
    { category: "tasks", title: "Book a dentist appointment", inDays: 0 },
    { category: "tasks", title: "Send portfolio to the recruiter", inDays: 2 },
  ];

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function toIsoDay(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

function parseIsoDay(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** Whole calendar days from `today` to `iso`; negative when it has passed. */
export function daysUntil(iso: string, today: Date = new Date()): number {
  // `Math.round` rather than a floor: a daylight-saving change makes one day 23 or 25 hours long.
  return Math.round(
    (parseIsoDay(iso).getTime() - startOfDay(today).getTime()) / 86_400_000,
  );
}

/**
 * The line under an event's title.
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
  const d = parseIsoDay(iso);
  const weekday = d.toLocaleDateString("en-US", { weekday: "short" });
  const monthDay = d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
  return `${weekday} ${monthDay}`;
}

/** The hardcoded events, dated from `today`, grouped and ordered by date within each group. */
export function sampleUpcoming(today: Date = new Date()): UpcomingGroup[] {
  const base = startOfDay(today);
  return UPCOMING_CATEGORIES.map(({ category, label }) => ({
    category,
    label,
    events: SAMPLE.filter((e) => e.category === category)
      .map((e, i) => {
        const d = new Date(base);
        d.setDate(d.getDate() + e.inDays);
        return { id: `${category}-${i}`, title: e.title, date: toIsoDay(d) };
      })
      .sort((a, b) => a.date.localeCompare(b.date)),
  }));
}
