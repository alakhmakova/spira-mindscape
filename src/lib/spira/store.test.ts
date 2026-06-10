import { beforeEach, describe, expect, it, vi } from "vitest";

import { SpiraApiError, spiraApi } from "./api";
import { useSpira } from "./store";
import type { Goal, Resource } from "./types";

const BODY_LIMIT_MESSAGE =
  "Note resource body must be 50000 characters or fewer";

function goalFixture(): Goal {
  return {
    id: "goal-1",
    title: "Goal",
    description: "",
    confidence: 5,
    createdAt: "2026-05-15T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [
      {
        id: "resource-1",
        type: "note",
        title: "Research notes",
        body: "<p>Draft note.</p>",
      },
    ],
    targets: [],
  };
}

describe("useSpira resource sync errors", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    useSpira.setState({
      goals: [goalFixture()],
      chat: [],
      isLoading: false,
      hasLoaded: true,
      syncError: undefined,
      syncErrorKind: undefined,
    });
  });

  it("rolls back an oversized note create and shows the validation message", async () => {
    vi.spyOn(spiraApi, "createResource").mockRejectedValue(
      new SpiraApiError(BODY_LIMIT_MESSAGE),
    );

    useSpira.getState().addResource("goal-1", {
      type: "note",
      title: "Oversized note",
      body: "A".repeat(50_001),
    });

    await vi.waitFor(() => {
      expect(
        useSpira
          .getState()
          .goals[0].resources.some(
            (resource) =>
              resource.type === "note" && resource.title === "Oversized note",
          ),
      ).toBe(false);
    });

    expect(useSpira.getState().syncError).toBe(BODY_LIMIT_MESSAGE);
    expect(useSpira.getState().syncErrorKind).toBe("service");
  });

  it("rolls back an oversized note update and shows the validation message", async () => {
    vi.useFakeTimers();
    vi.spyOn(spiraApi, "updateResource").mockRejectedValue(
      new SpiraApiError(BODY_LIMIT_MESSAGE),
    );

    useSpira.getState().updateResource("goal-1", "resource-1", {
      body: "A".repeat(50_001),
    });

    expect(
      (
        useSpira.getState().goals[0].resources[0] as Extract<
          Goal["resources"][number],
          { type: "note" }
        >
      ).body,
    ).toHaveLength(50_001);

    await vi.advanceTimersByTimeAsync(500);

    await vi.waitFor(() => {
      expect(
        (
          useSpira.getState().goals[0].resources[0] as Extract<
            Goal["resources"][number],
            { type: "note" }
          >
        ).body,
      ).toBe("<p>Draft note.</p>");
    });

    expect(useSpira.getState().syncError).toBe(BODY_LIMIT_MESSAGE);
    expect(useSpira.getState().syncErrorKind).toBe("service");
  });

  it("keeps every reality item when several are added at once (concurrent reconcile)", async () => {
    // Regression: the AI can create several actions/obstacles in one go (a stepper
    // "Save all"). Each add fires its own server call; previously the response REPLACED
    // the whole reality list, so 3 concurrent responses clobbered each other and only the
    // last item survived. addReality must now reconcile only ITS OWN item.
    //
    // We simulate the worst case: each call's response contains ONLY its own item (a stale
    // full-list snapshot that omits the siblings). The fix must still end with all three.
    vi.spyOn(spiraApi, "addRealityItem").mockImplementation(
      async (_goalId: string, kind: "actions" | "obstacles", text: string) => ({
        actions: kind === "actions" ? [{ id: "srv-" + text, text }] : [],
        obstacles: kind === "obstacles" ? [{ id: "srv-" + text, text }] : [],
      }),
    );

    const addReality = useSpira.getState().addReality;
    addReality("goal-1", "actions", "update cv");
    addReality("goal-1", "actions", "got my diploma");
    addReality("goal-1", "actions", "finished my personal project");

    await vi.waitFor(() => {
      expect(useSpira.getState().goals[0].reality.actions).toHaveLength(3);
    });

    const actions = useSpira.getState().goals[0].reality.actions;
    expect(actions.map((a) => a.text).sort()).toEqual(
      ["finished my personal project", "got my diploma", "update cv"].sort(),
    );
    // Every item reconciled to its real server id (no temp "local-" ids left behind).
    expect(actions.every((a) => a.id.startsWith("srv-"))).toBe(true);
  });

  it("keeps every resource when several are created at once", async () => {
    // Same family of bug as reality items: creating several resources (notes/links/emails)
    // in one go fires concurrent server calls. addResource must reconcile each by its own
    // temp id (it does) so none is lost.
    vi.spyOn(spiraApi, "createResource").mockImplementation(
      async (_goalId: string, input) =>
        ({ ...input, id: "srv-" + (input as { title?: string }).title }) as Resource,
    );

    const addResource = useSpira.getState().addResource;
    addResource("goal-1", { type: "note", title: "CV", body: "<p>a</p>" });
    addResource("goal-1", { type: "note", title: "Diploma", body: "<p>b</p>" });
    addResource("goal-1", { type: "note", title: "Project", body: "<p>c</p>" });

    await vi.waitFor(() => {
      const synced = useSpira
        .getState()
        .goals[0].resources.filter((r) => r.id.startsWith("srv-"));
      expect(synced).toHaveLength(3);
    });

    const titles = useSpira
      .getState()
      .goals[0].resources.map((r) => (r.type === "note" ? r.title : ""))
      .filter(Boolean);
    expect(titles).toEqual(expect.arrayContaining(["CV", "Diploma", "Project"]));
  });

  it("rolls back only the failed resource, keeping siblings created at the same time", async () => {
    // Regression: a failed add used to restore a whole-list snapshot, wiping siblings added
    // alongside it. Now only the failed item is removed.
    vi.spyOn(spiraApi, "createResource").mockImplementation(async (_goalId: string, input) => {
      const title = (input as { title?: string }).title;
      if (title === "Bad") throw new SpiraApiError("boom");
      return { ...input, id: "srv-" + title } as Resource;
    });

    const addResource = useSpira.getState().addResource;
    addResource("goal-1", { type: "note", title: "Good1", body: "" });
    addResource("goal-1", { type: "note", title: "Bad", body: "" });
    addResource("goal-1", { type: "note", title: "Good2", body: "" });

    await vi.waitFor(() => {
      const titles = useSpira
        .getState()
        .goals[0].resources.map((r) => (r.type === "note" ? r.title : ""))
        .filter(Boolean);
      expect(titles).toContain("Good1");
      expect(titles).toContain("Good2");
      expect(titles).not.toContain("Bad");
    });
  });

  it("stamps target and goal achieved dates when the last target completes", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T10:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "numeric",
      title: "Lower incident count",
      start: 10,
      current: 0,
      total: 0,
    });
    useSpira.setState({
      goals: [
        {
          ...goalFixture(),
          targets: [
            {
              id: "target-1",
              type: "numeric",
              title: "Lower incident count",
              start: 10,
              current: 5,
              total: 0,
            },
          ],
        },
      ],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", { current: 0 });

    const goal = useSpira.getState().goals[0];
    const target = goal.targets[0];
    expect(goal.achievedAt).toBe("2026-05-16T10:00:00.000Z");
    expect(target.achievedAt).toBe("2026-05-16T10:00:00.000Z");
  });

  it("clears binary target and goal achieved dates when completion is undone", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T12:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "binary",
      title: "Submit application",
      done: false,
      deadline: "2026-05-15T00:00:00.000Z",
      achievedAt: undefined,
    });
    useSpira.setState({
      goals: [binaryGoalFixture()],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", { done: false });
    const goal = useSpira.getState().goals[0];
    const target = goal.targets[0];
    expect(goal.achievedAt).toBeUndefined();
    expect(target.achievedAt).toBeUndefined();
  });

  it("keeps the binary target deadline when completion is undone", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T12:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "binary",
      title: "Submit application",
      done: false,
      deadline: "2026-05-15T00:00:00.000Z",
      achievedAt: undefined,
    });
    useSpira.setState({
      goals: [binaryGoalFixture()],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", { done: false });

    const goal = useSpira.getState().goals[0];
    const target = goal.targets[0];
    expect(target.deadline).toBe("2026-05-15T00:00:00.000Z");
  });

  it("stamps checklist task, target, and goal achieved dates when all tasks are done", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T11:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "checklist",
      title: "Launch checklist",
      items: [
        { id: "task-1", text: "Write notes", done: true },
        { id: "task-2", text: "Send update", done: true },
      ],
    });
    useSpira.setState({
      goals: [
        {
          ...goalFixture(),
          targets: [
            {
              id: "target-1",
              type: "checklist",
              title: "Launch checklist",
              items: [
                { id: "task-1", text: "Write notes", done: true },
                { id: "task-2", text: "Send update", done: false },
              ],
            },
          ],
        },
      ],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", {
      items: [
        { id: "task-1", text: "Write notes", done: true },
        { id: "task-2", text: "Send update", done: true },
      ],
    });

    const goal = useSpira.getState().goals[0];
    const target = goal.targets[0];
    expect(goal.achievedAt).toBe("2026-05-16T11:00:00.000Z");
    expect(target.achievedAt).toBe("2026-05-16T11:00:00.000Z");
    expect(target.type === "checklist" && target.items[1].achievedAt).toBe(
      "2026-05-16T11:00:00.000Z",
    );
  });

  it("clears checklist task, target, and goal achieved dates when a task is undone", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T13:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "checklist",
      title: "Launch checklist",
      items: [
        {
          id: "task-1",
          text: "Write notes",
          done: false,
          deadline: "2026-05-15T00:00:00.000Z",
          achievedAt: undefined,
        },
      ],
    });
    useSpira.setState({
      goals: [completedChecklistGoalFixture()],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", {
      items: [
        {
          id: "task-1",
          text: "Write notes",
          done: false,
          deadline: "2026-05-15T00:00:00.000Z",
        },
      ],
    });

    const goal = useSpira.getState().goals[0];
    const target = goal.targets[0];
    expect(goal.achievedAt).toBeUndefined();
    expect(target.achievedAt).toBeUndefined();
    expect(
      target.type === "checklist" && target.items[0].achievedAt,
    ).toBeUndefined();
  });

  it("keeps checklist task deadlines when a task is undone", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T13:00:00.000Z"));
    vi.spyOn(spiraApi, "updateTarget").mockResolvedValue({
      id: "target-1",
      type: "checklist",
      title: "Launch checklist",
      items: [
        {
          id: "task-1",
          text: "Write notes",
          done: false,
          deadline: "2026-05-15T00:00:00.000Z",
          achievedAt: undefined,
        },
      ],
    });
    useSpira.setState({
      goals: [completedChecklistGoalFixture()],
    });

    useSpira.getState().updateTarget("goal-1", "target-1", {
      items: [
        {
          id: "task-1",
          text: "Write notes",
          done: false,
          deadline: "2026-05-15T00:00:00.000Z",
        },
      ],
    });

    const target = useSpira.getState().goals[0].targets[0];
    expect(target.type === "checklist" && target.items[0].deadline).toBe(
      "2026-05-15T00:00:00.000Z",
    );
  });
});

function binaryGoalFixture(): Goal {
  return {
    ...goalFixture(),
    achievedAt: "2026-05-16T11:00:00.000Z",
    targets: [
      {
        id: "target-1",
        type: "binary",
        title: "Submit application",
        done: true,
        deadline: "2026-05-15T00:00:00.000Z",
        achievedAt: "2026-05-16T11:00:00.000Z",
      },
    ],
  };
}

function completedChecklistGoalFixture(): Goal {
  return {
    ...goalFixture(),
    achievedAt: "2026-05-16T11:00:00.000Z",
    targets: [
      {
        id: "target-1",
        type: "checklist",
        title: "Launch checklist",
        achievedAt: "2026-05-16T11:00:00.000Z",
        items: [
          {
            id: "task-1",
            text: "Write notes",
            done: true,
            deadline: "2026-05-15T00:00:00.000Z",
            achievedAt: "2026-05-16T11:00:00.000Z",
          },
        ],
      },
    ],
  };
}
