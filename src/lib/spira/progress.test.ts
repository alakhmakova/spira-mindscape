import { describe, expect, it } from "vitest";

import { goalProgress, targetProgress } from "./progress";
import type { Goal, Target } from "./types";

describe("targetProgress", () => {
  it("calculates numeric progress with inferred start, reverse direction, and clamping", () => {
    expect(
      targetProgress({
        id: "inferred-start",
        type: "numeric",
        title: "Reduce backlog",
        current: 14,
        total: 10,
      }),
    ).toBe(0);

    expect(
      targetProgress({
        id: "reverse",
        type: "numeric",
        title: "Lower metric",
        start: 100,
        current: 80,
        total: 70,
      }),
    ).toBeCloseTo(2 / 3);

    expect(
      targetProgress({
        id: "clamped",
        type: "numeric",
        title: "Finish pages",
        start: 0,
        current: 12,
        total: 10,
      }),
    ).toBe(1);
  });

  it("calculates done/not done progress", () => {
    expect(targetProgress(binaryTarget(true))).toBe(1);
    expect(targetProgress(binaryTarget(false))).toBe(0);
  });

  it("calculates checklist progress from completed items", () => {
    expect(
      targetProgress({
        id: "empty",
        type: "checklist",
        title: "Empty checklist",
        items: [],
      }),
    ).toBe(0);

    expect(
      targetProgress({
        id: "checklist",
        type: "checklist",
        title: "Prepare workspace",
        items: [
          { id: "1", text: "Write requirements", done: true },
          { id: "2", text: "Review validation", done: false },
          { id: "3", text: "Run tests", done: true },
        ],
      }),
    ).toBeCloseTo(2 / 3);
  });
});

describe("goalProgress", () => {
  it("averages all target progress values equally", () => {
    const goal = goalWithTargets([
      {
        id: "numeric",
        type: "numeric",
        title: "Read pages",
        start: 0,
        current: 5,
        total: 10,
      },
      binaryTarget(true),
      {
        id: "checklist",
        type: "checklist",
        title: "Prepare workspace",
        items: [
          { id: "1", text: "Write requirements", done: true },
          { id: "2", text: "Review validation", done: false },
        ],
      },
    ]);

    expect(goalProgress(goal)).toBeCloseTo(2 / 3);
  });

  it("returns zero when a goal has no targets", () => {
    expect(goalProgress(goalWithTargets([]))).toBe(0);
  });
});

function binaryTarget(done: boolean): Target {
  return {
    id: done ? "done" : "not-done",
    type: "binary",
    title: done ? "Done target" : "Not done target",
    done,
  };
}

function goalWithTargets(targets: Target[]): Goal {
  return {
    id: "goal",
    title: "Goal",
    description: "",
    confidence: 7,
    createdAt: "2026-05-08T00:00:00Z",
    reality: {
      actions: [],
      obstacles: [],
    },
    options: [],
    resources: [],
    targets,
  };
}
