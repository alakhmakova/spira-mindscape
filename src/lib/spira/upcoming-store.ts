import { create } from "zustand";
import { toast } from "sonner";
import { spiraApi } from "./api";
import { useSpira } from "./store";
import { deviceTimeZone, type Reminder } from "./reminders";
import type { Goal } from "./types";
import {
  linkableItems,
  linkedId,
  sameDay,
  type LinkableItem,
  type ResolvedUpcomingEvent,
  type UpcomingEventRecord,
  type UpcomingStatus,
} from "./upcoming";

/** When an event happens and how it reminds: an optional `HH:mm` (absent = all day) and its reminders. */
export type UpcomingSchedule = { time?: string; reminders: Reminder[] };

/** What the Add event form saves: an important date, or a link with the date it should carry. */
export type NewUpcomingEvent = (
  | { kind: "important"; title: string; date: string }
  | { kind: LinkableItem["kind"]; item: LinkableItem; date: string }
) &
  UpcomingSchedule;

/** What the form saves when it edits an event. */
export type UpcomingEdit = UpcomingSchedule & {
  /** Important dates only. */
  title?: string;
  date: string;
  /** A link's item, so a changed date can move its deadline. */
  item?: LinkableItem;
};

type UpcomingState = {
  events: UpcomingEventRecord[];
  status: "idle" | "loading" | "ready" | "error";
  load: () => Promise<void>;
  /** Saves a new event. Resolves `true` once the server has it, `false` (with a toast) if not. */
  add: (input: NewUpcomingEvent) => Promise<boolean>;
  /** Saves an edit. Resolves `true` once the server has it, `false` (with a toast) if not. */
  update: (id: string, input: UpcomingEdit) => Promise<boolean>;
  /** Removes an event — and, for a link, the deadline it stood for. */
  remove: (event: ResolvedUpcomingEvent) => Promise<void>;
  /**
   * Settles an event — Done or Won't do (owner, 2026-10-19). Optimistic: it leaves the column at
   * once and comes back, with a toast, if the server refused. Resolves whether it was saved.
   */
  resolve: (
    id: string,
    status: Exclude<UpcomingStatus, "pending">,
  ) => Promise<boolean>;
};

function messageOf(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : "Something went wrong. Please try again.";
}

/**
 * Moves a goal's, target's or task's deadline through the goal store, so the change is optimistic
 * on every screen and persisted by the store's own write. `undefined` clears it.
 */
function setItemDeadline(
  item: Pick<LinkableItem, "kind" | "id" | "goalId" | "targetId">,
  deadline: string | undefined,
  goals: Goal[],
) {
  const { updateGoal, updateTarget } = useSpira.getState();
  if (item.kind === "goal") {
    updateGoal(item.goalId, { deadline });
    return;
  }
  if (!item.targetId) return;
  if (item.kind === "target") {
    updateTarget(item.goalId, item.targetId, { deadline });
    return;
  }
  const target = goals
    .find((g) => g.id === item.goalId)
    ?.targets.find((t) => t.id === item.targetId);
  if (target?.type !== "checklist") return;
  updateTarget(item.goalId, item.targetId, {
    items: target.items.map((task) =>
      task.id === item.id ? { ...task, deadline } : task,
    ),
  });
}

let timeZoneSent: Promise<void> | null = null;

/**
 * Tells the server the device's zone, once per page load — reminders are computed in it, and
 * "on the day at 09:00" means nothing without one. A failure is retried on the next call rather
 * than remembered.
 */
export function syncTimeZone(): Promise<void> {
  const zone = deviceTimeZone();
  if (!zone) return Promise.resolve();
  timeZoneSent ??= spiraApi.setTimeZone(zone).catch(() => {
    timeZoneSent = null;
  });
  return timeZoneSent;
}

export const useUpcoming = create<UpcomingState>((set, get) => ({
  events: [],
  status: "idle",

  load: async () => {
    if (get().status === "loading") return;
    set({ status: "loading" });
    try {
      set({ events: await spiraApi.fetchUpcomingEvents(), status: "ready" });
    } catch {
      // The column says so itself (it renders the error state), so no toast as well.
      set({ status: "error" });
    }
  },

  add: async (input) => {
    try {
      const schedule = { time: input.time, reminders: input.reminders };
      if (input.reminders.length > 0) await syncTimeZone();
      let record: Omit<UpcomingEventRecord, "id">;
      if (input.kind === "important") {
        record = {
          kind: "important",
          title: input.title,
          date: input.date,
          ...schedule,
        };
      } else {
        const { item } = input;
        // The form shows the item's deadline as the event's date; a date the user changed there
        // is the item's new deadline (owner, 2026-10-09). The event itself stores no date.
        if (!sameDay(item.deadline, input.date)) {
          setItemDeadline(item, input.date, useSpira.getState().goals);
        }
        record = {
          kind: item.kind,
          goalId: item.kind === "goal" ? item.id : undefined,
          targetId: item.kind === "target" ? item.id : undefined,
          taskId: item.kind === "task" ? item.id : undefined,
          ...schedule,
        };
      }
      const saved = await spiraApi.createUpcomingEvent(record);
      set((s) => ({ events: [...s.events, saved] }));
      return true;
    } catch (error) {
      toast.error(messageOf(error));
      return false;
    }
  },

  update: async (id, input) => {
    try {
      if (input.reminders.length > 0) await syncTimeZone();
      const { item } = input;
      if (item && !sameDay(item.deadline, input.date)) {
        setItemDeadline(item, input.date, useSpira.getState().goals);
      }
      const saved = await spiraApi.updateUpcomingEvent(id, {
        title: item ? undefined : input.title,
        date: item ? undefined : input.date,
        time: input.time,
        reminders: input.reminders,
      });
      set((s) => ({ events: s.events.map((e) => (e.id === id ? saved : e)) }));
      return true;
    } catch (error) {
      toast.error(messageOf(error));
      return false;
    }
  },

  resolve: async (id, status) => {
    const previous = get().events;
    set({
      events: previous.map((e) =>
        e.id === id
          ? { ...e, status, resolvedAt: new Date().toISOString() }
          : e,
      ),
    });
    try {
      const saved = await spiraApi.resolveUpcomingEvent(id, status);
      set((s) => ({ events: s.events.map((e) => (e.id === id ? saved : e)) }));
      return true;
    } catch (error) {
      set({ events: previous });
      toast.error(messageOf(error));
      return false;
    }
  },

  remove: async (event) => {
    const previous = get().events;
    set({ events: previous.filter((e) => e.id !== event.id) });

    try {
      await spiraApi.deleteUpcomingEvent(event.id);
    } catch (error) {
      set({ events: previous });
      toast.error(messageOf(error));
      return;
    }

    // The server has cleared a link's deadline in the same transaction as the delete; mirror it
    // so the card and the goal page lose the date now rather than on the next refresh. Only after
    // the delete succeeded: the store's own write would otherwise clear a deadline the server kept.
    if (event.kind !== "important") {
      const goals = useSpira.getState().goals;
      const item = linkableItems(goals).find(
        (i) => i.kind === event.kind && i.id === linkedId(event.record),
      );
      if (item) setItemDeadline(item, undefined, goals);
    }
  },
}));
