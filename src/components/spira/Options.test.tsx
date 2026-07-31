import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { OptionsList, moveInArray, reorderTargetIndex } from "./OptionsList";
import { useSpira } from "@/lib/spira/store";
import { FIELD_LIMITS } from "@/lib/spira/limits";
import type { Goal, Option, OptionStatus } from "@/lib/spira/types";

// ── Fixtures ─────────────────────────────────────────────────────────────────

function option(id: string, text: string, extra: Partial<Option> = {}): Option {
  return { id, text, selected: false, status: "none", position: 0, ...extra };
}

function goalFixture(options: Option[]): Goal {
  return {
    id: "goal-1",
    title: "Goal",
    description: "",
    confidence: 5,
    createdAt: "2026-05-15T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options,
    resources: [],
    targets: [],
  };
}

const store = {
  addOption: vi.fn(),
  updateOption: vi.fn(),
  selectOption: vi.fn(),
  setOptionStatus: vi.fn(),
  removeOption: vi.fn(),
  reorderOptions: vi.fn(),
};

beforeEach(() => {
  Object.values(store).forEach((fn) => fn.mockReset());
  useSpira.setState({ ...store });
});

// ── Pure reorder math (guards the "down-drag froze" regression) ──────────────

describe("reorderTargetIndex", () => {
  it("moves a card DOWN when the pointer travels down (positive delta)", () => {
    // From slot 0, dragging down ~2 steps lands on slot 2.
    expect(reorderTargetIndex(0, 200, 84, 3)).toBe(2);
  });

  it("moves a card UP when the pointer travels up (negative delta)", () => {
    expect(reorderTargetIndex(2, -200, 84, 3)).toBe(0);
  });

  it("clamps to the last slot and never past the end", () => {
    expect(reorderTargetIndex(0, 10_000, 84, 3)).toBe(2);
  });

  it("clamps to the first slot and never before the start", () => {
    expect(reorderTargetIndex(2, -10_000, 84, 3)).toBe(0);
  });

  it("stays put for a sub-half-step nudge, and rounds at the half-step boundary", () => {
    expect(reorderTargetIndex(0, 41, 84, 3)).toBe(0); // < half a step
    expect(reorderTargetIndex(0, 43, 84, 3)).toBe(1); // > half a step → one slot
  });
});

describe("moveInArray", () => {
  it("moves an item forward (0 → last)", () => {
    expect(moveInArray(["a", "b", "c"], 0, 2)).toEqual(["b", "c", "a"]);
  });

  it("moves an item backward (last → 0)", () => {
    expect(moveInArray(["a", "b", "c"], 2, 0)).toEqual(["c", "a", "b"]);
  });

  it("is a no-op when from === to", () => {
    expect(moveInArray(["a", "b", "c"], 1, 1)).toEqual(["a", "b", "c"]);
  });
});

// ── Selection (single active option) ─────────────────────────────────────────

describe("OptionsList — selection", () => {
  it("selects an unselected option via its radio", async () => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha")])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Select strategy" }));

    expect(store.selectOption).toHaveBeenCalledWith("goal-1", "o1");
    expect(store.updateOption).not.toHaveBeenCalled();
  });

  it("deselects the active option (radio shows the Deselect label)", async () => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha", { selected: true })])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Deselect strategy" }));

    expect(store.updateOption).toHaveBeenCalledWith("goal-1", "o1", {
      selected: false,
    });
    expect(store.selectOption).not.toHaveBeenCalled();
  });
});

// ── Rating cycle (none → good_idea → didnt_work → none) ───────────────────────

describe("OptionsList — rating cycle", () => {
  it.each<[OptionStatus, OptionStatus]>([
    ["none", "good_idea"],
    ["good_idea", "didnt_work"],
    ["didnt_work", "none"],
  ])("cycles %s → %s", async (from, to) => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha", { status: from })])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Rate strategy" }));

    expect(store.setOptionStatus).toHaveBeenCalledWith("goal-1", "o1", to);
  });
});

// ── Add strategy ─────────────────────────────────────────────────────────────

describe("OptionsList — add strategy", () => {
  it("adds a strategy on Enter and clears the input", async () => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    const input = screen.getByPlaceholderText("Add a strategy…");
    await user.type(input, "New strategy{Enter}");

    expect(store.addOption).toHaveBeenCalledWith("goal-1", "New strategy");
    expect(input).toHaveValue("");
  });

  it("ignores an empty (whitespace-only) submission", async () => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    await user.type(
      screen.getByPlaceholderText("Add a strategy…"),
      "   {Enter}",
    );

    expect(store.addOption).not.toHaveBeenCalled();
  });

  it("blocks an over-limit strategy and shows the length message", () => {
    render(
      <OptionsList
        goal={goalFixture([])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    // Set the (long) value in one shot — typing char-by-char would be needlessly slow.
    const input = screen.getByPlaceholderText("Add a strategy…");
    const tooLong = "x".repeat(FIELD_LIMITS.optionText + 1);
    fireEvent.change(input, { target: { value: tooLong } });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(store.addOption).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/too long/i);
  });
});

// ── Remove strategy ──────────────────────────────────────────────────────────

describe("OptionsList — remove strategy", () => {
  it("removes the card via its Remove button", async () => {
    const user = userEvent.setup();
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha"), option("o2", "Beta")])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    const beta = screen.getByText("Beta").closest("li")!;
    await user.click(
      within(beta).getByRole("button", { name: "Remove strategy" }),
    );

    expect(store.removeOption).toHaveBeenCalledWith("goal-1", "o2");
  });
});

// ── Reorder mode ─────────────────────────────────────────────────────────────

describe("OptionsList — reorder mode", () => {
  it("hides the add field and Remove buttons and shows the drag hint", () => {
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha"), option("o2", "Beta")])}
        reordering
        onReorderingChange={vi.fn()}
      />,
    );

    expect(
      screen.queryByPlaceholderText("Add a strategy…"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Remove strategy" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Drag cards to reorder.")).toBeInTheDocument();
  });

  it("exits reorder mode when fewer than 2 options remain", () => {
    const onReorderingChange = vi.fn();
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha")])}
        reordering
        onReorderingChange={onReorderingChange}
      />,
    );

    expect(onReorderingChange).toHaveBeenCalledWith(false);
  });
});
