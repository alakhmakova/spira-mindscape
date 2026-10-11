import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Goal } from "./types";
import type { LinkableItem, ResolvedUpcomingEvent } from "./upcoming";

/**
 * `useUpcoming` against a mocked API: what it sends, what it keeps, and the two rules the store
 * owns — a changed date in the form becomes the item's deadline, and a link's deadline is cleared
 * only once the server has accepted the delete.
 */

const api = vi.hoisted(() => ({
  fetchUpcomingEvents: vi.fn(),
  createUpcomingEvent: vi.fn(),
  deleteUpcomingEvent: vi.fn(),
}));
vi.mock("./api", () => ({ spiraApi: api }));

const toastError = vi.hoisted(() => vi.fn());
vi.mock("sonner", () => ({ toast: { error: toastError, info: vi.fn() } }));

import { useSpira } from "./store";
import { useUpcoming } from "./upcoming-store";

const D1 = "2026-11-01T00:00:00.000Z";
const D2 = "2026-11-20T00:00:00.000Z";

function goal(patch: Partial<Goal> = {}): Goal {
  return {
    id: "1",
    title: "Run a marathon",
    description: "",
    confidence: 5,
    deadline: D1,
    createdAt: "2026-01-01T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [
      {
        id: "10",
        type: "checklist",
        title: "Plan",
        deadline: D1,
        items: [{ id: "100", text: "Long run", done: false, deadline: D1 }],
      },
    ],
    ...patch,
  };
}

const goalItem: LinkableItem = {
  id: "1",
  kind: "goal",
  title: "Run a marathon",
  deadline: D1,
  goalId: "1",
};
const taskItem: LinkableItem = {
  id: "100",
  kind: "task",
  title: "Long run",
  deadline: D1,
  goalId: "1",
  targetId: "10",
};

const updateGoal = vi.fn();
const updateTarget = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  useUpcoming.setState({ events: [], status: "idle" });
  useSpira.setState({ goals: [goal()], updateGoal, updateTarget });
});

describe("load", () => {
  it("keeps what the server returns and becomes ready", async () => {
    api.fetchUpcomingEvents.mockResolvedValue([
      { id: "e1", kind: "important", title: "Trip", date: D1 },
    ]);
    await useUpcoming.getState().load();
    expect(useUpcoming.getState()).toMatchObject({
      status: "ready",
      events: [{ id: "e1" }],
    });
  });

  it("becomes 'error' on a failure — the column says so, not a toast", async () => {
    api.fetchUpcomingEvents.mockRejectedValue(new Error("down"));
    await useUpcoming.getState().load();
    expect(useUpcoming.getState().status).toBe("error");
    expect(toastError).not.toHaveBeenCalled();
  });
});

describe("add", () => {
  it("sends an important date as it is, and keeps the saved event", async () => {
    api.createUpcomingEvent.mockResolvedValue({
      id: "e1",
      kind: "important",
      title: "Trip",
      date: D1,
    });
    const ok = await useUpcoming
      .getState()
      .add({ kind: "important", title: "Trip", date: D1 });

    expect(ok).toBe(true);
    expect(api.createUpcomingEvent).toHaveBeenCalledWith({
      kind: "important",
      title: "Trip",
      date: D1,
    });
    expect(useUpcoming.getState().events.map((e) => e.id)).toEqual(["e1"]);
  });

  it("sends a link as the item's id only, and leaves an unchanged deadline alone", async () => {
    api.createUpcomingEvent.mockResolvedValue({
      id: "e2",
      kind: "goal",
      goalId: "1",
    });
    await useUpcoming
      .getState()
      .add({ kind: "goal", item: goalItem, date: D1 });

    expect(api.createUpcomingEvent).toHaveBeenCalledWith({
      kind: "goal",
      goalId: "1",
      targetId: undefined,
      taskId: undefined,
    });
    expect(updateGoal).not.toHaveBeenCalled();
  });

  it("makes a date changed in the form the goal's new deadline", async () => {
    api.createUpcomingEvent.mockResolvedValue({
      id: "e2",
      kind: "goal",
      goalId: "1",
    });
    await useUpcoming
      .getState()
      .add({ kind: "goal", item: goalItem, date: D2 });
    expect(updateGoal).toHaveBeenCalledWith("1", { deadline: D2 });
  });

  it("moves a task's deadline inside its checklist, and only that task's", async () => {
    api.createUpcomingEvent.mockResolvedValue({
      id: "e3",
      kind: "task",
      taskId: "100",
    });
    await useUpcoming
      .getState()
      .add({ kind: "task", item: taskItem, date: D2 });

    expect(updateTarget).toHaveBeenCalledWith("1", "10", {
      items: [{ id: "100", text: "Long run", done: false, deadline: D2 }],
    });
  });

  it("says what went wrong and keeps nothing when the server refuses", async () => {
    api.createUpcomingEvent.mockRejectedValue(
      new Error("This goal is already in Upcoming"),
    );
    const ok = await useUpcoming
      .getState()
      .add({ kind: "goal", item: goalItem, date: D1 });

    expect(ok).toBe(false);
    expect(toastError).toHaveBeenCalledWith("This goal is already in Upcoming");
    expect(useUpcoming.getState().events).toEqual([]);
  });
});

describe("remove", () => {
  const linked: ResolvedUpcomingEvent = {
    id: "e2",
    kind: "goal",
    title: "Run a marathon",
    date: D1,
    record: { id: "e2", kind: "goal", goalId: "1" },
  };

  it("takes the event off at once and clears the goal's deadline after the server agrees", async () => {
    useUpcoming.setState({ events: [linked.record], status: "ready" });
    api.deleteUpcomingEvent.mockResolvedValue(undefined);

    await useUpcoming.getState().remove(linked);

    expect(api.deleteUpcomingEvent).toHaveBeenCalledWith("e2");
    expect(useUpcoming.getState().events).toEqual([]);
    expect(updateGoal).toHaveBeenCalledWith("1", { deadline: undefined });
  });

  it("puts the event back, keeps the deadline and says why when the delete fails", async () => {
    useUpcoming.setState({ events: [linked.record], status: "ready" });
    api.deleteUpcomingEvent.mockRejectedValue(new Error("Network down"));

    await useUpcoming.getState().remove(linked);

    expect(useUpcoming.getState().events).toEqual([linked.record]);
    expect(updateGoal).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith("Network down");
  });

  it("never touches a deadline for an important date", async () => {
    const important: ResolvedUpcomingEvent = {
      id: "e1",
      kind: "important",
      title: "Trip",
      date: D1,
      record: { id: "e1", kind: "important", title: "Trip", date: D1 },
    };
    useUpcoming.setState({ events: [important.record], status: "ready" });
    api.deleteUpcomingEvent.mockResolvedValue(undefined);

    await useUpcoming.getState().remove(important);

    expect(updateGoal).not.toHaveBeenCalled();
    expect(updateTarget).not.toHaveBeenCalled();
  });
});
