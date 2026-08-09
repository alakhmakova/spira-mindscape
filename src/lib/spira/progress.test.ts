import { describe, expect, it } from "vitest";

import {
  formatPercent,
  goalProgress,
  goalProgressSteps,
  isProgressLocked,
  relockOnCompletion,
  progressSteps,
  targetProgress,
} from "./progress";
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

describe("isProgressLocked", () => {
  it("locks an achieved target and leaves an unfinished one open by default", () => {
    expect(isProgressLocked(binaryTarget(true))).toBe(true);
    expect(isProgressLocked(binaryTarget(false))).toBe(false);
  });

  it("honours the user's explicit choice in both directions", () => {
    expect(
      isProgressLocked({ ...binaryTarget(true), progressLocked: false }),
    ).toBe(false);
    expect(
      isProgressLocked({ ...binaryTarget(false), progressLocked: true }),
    ).toBe(true);
  });

  it("treats a null (never decided) flag as no choice at all", () => {
    expect(
      isProgressLocked({ ...binaryTarget(true), progressLocked: null }),
    ).toBe(true);
  });
});

describe("relockOnCompletion", () => {
  const unlocked = (done: boolean): Target => ({
    ...binaryTarget(done),
    id: "t",
    progressLocked: false,
  });

  it("re-locks a target that was deliberately unlocked earlier", () => {
    // The bug: one unlock used to outlive the completion it was meant for, so the target never
    // locked itself again however often it hit 100%.
    expect(
      relockOnCompletion(unlocked(false), unlocked(true)).progressLocked,
    ).toBe(true);
  });

  it("leaves an unlock alone when the update doesn't complete the target", () => {
    expect(
      relockOnCompletion(unlocked(false), unlocked(false)).progressLocked,
    ).toBe(false);
  });

  it("lets a target that is already complete stay unlocked, so it can be corrected", () => {
    expect(
      relockOnCompletion(unlocked(true), unlocked(true)).progressLocked,
    ).toBe(false);
  });

  it("writes nothing when the flag was never set — 100% already locks itself", () => {
    const before = binaryTarget(false);
    const after = binaryTarget(true);
    expect(relockOnCompletion(before, after)).toBe(after);
    expect(isProgressLocked(after)).toBe(true);
  });
});

describe("formatPercent", () => {
  // A 0 → 1 900 000 SEK savings target: 1 900 000 possible increments.
  const SEK = 1_900_000;

  it("shows the decimals a big target needs — whole percent hides weeks of saving", () => {
    // The reported case: 10 000 and 20 000 both printed "1%".
    expect(formatPercent(10_000 / SEK, SEK)).toBe("0.53");
    expect(formatPercent(20_000 / SEK, SEK)).toBe("1.05");
    expect(formatPercent(4_000 / SEK, SEK)).toBe("0.21");
    expect(formatPercent(240_000 / SEK, SEK)).toBe("12.63");
  });

  it("stays whole where a whole percent is the truth", () => {
    // A four-task checklist moves in 25% steps — decimals would be noise.
    expect(formatPercent(0.5, 4)).toBe("50");
    expect(formatPercent(0.25, 4)).toBe("25");
    // Trailing zeros are trimmed even when the target is fine-grained.
    expect(formatPercent(0.5, SEK)).toBe("50");
    // A 200-step target: one step is half a percent, so one decimal.
    expect(formatPercent(3 / 200, 200)).toBe("1.5");
  });

  it("never claims 0 or 100 unless it is true", () => {
    expect(formatPercent(0, SEK)).toBe("0");
    expect(formatPercent(1, SEK)).toBe("100");
    expect(formatPercent(1 / SEK, SEK)).toBe("<0.01");
    expect(formatPercent((SEK - 1) / SEK, SEK)).toBe(">99.99");
    // Even a coarse target escalates rather than print a false 0.
    expect(formatPercent(0.0004, 4)).toBe("0.04");
  });

  it("clamps out-of-range input", () => {
    expect(formatPercent(-0.5, SEK)).toBe("0");
    expect(formatPercent(4, SEK)).toBe("100");
  });

  it("falls back to whole percent when the resolution is unknown", () => {
    expect(formatPercent(0.126)).toBe("13");
    // Still escalates rather than print a false zero — just one decimal at a time.
    expect(formatPercent(0.0021)).toBe("0.2");
  });
});

describe("progressSteps / goalProgressSteps", () => {
  const numeric = (start: number, total: number): Target => ({
    id: "n",
    type: "numeric",
    title: "Save",
    current: start,
    start,
    total,
  });

  it("counts a target's increments", () => {
    expect(progressSteps(numeric(0, 1_900_000))).toBe(1_900_000);
    // A countdown target measures the same distance.
    expect(progressSteps(numeric(500, 0))).toBe(500);
    expect(progressSteps(binaryTarget(false))).toBe(1);
  });

  it("a goal takes the finest target, divided across the mean", () => {
    const goal = goalWithTargets([numeric(0, 1_000), binaryTarget(false)]);
    // Two targets, the finer one with 1 000 steps → each step moves the mean by 1/2 000.
    expect(goalProgressSteps(goal)).toBe(2_000);
    expect(goalProgressSteps(goalWithTargets([]))).toBe(0);
  });
});
