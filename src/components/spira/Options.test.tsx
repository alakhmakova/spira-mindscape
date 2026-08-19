import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

    await user.click(screen.getByRole("button", { name: "Select option" }));

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

    await user.click(screen.getByRole("button", { name: "Deselect option" }));

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

    await user.click(screen.getByRole("button", { name: "Rate option" }));

    expect(store.setOptionStatus).toHaveBeenCalledWith("goal-1", "o1", to);
  });
});

// ── Add option ─────────────────────────────────────────────────────────────

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

    const input = screen.getByPlaceholderText("Add an option…");
    await user.type(input, "New option{Enter}");

    expect(store.addOption).toHaveBeenCalledWith("goal-1", "New option");
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
      screen.getByPlaceholderText("Add an option…"),
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
    const input = screen.getByPlaceholderText("Add an option…");
    const tooLong = "x".repeat(FIELD_LIMITS.optionText + 1);
    fireEvent.change(input, { target: { value: tooLong } });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(store.addOption).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/too long/i);
  });
});

// ── Remove strategy ──────────────────────────────────────────────────────────

describe("OptionsList — remove strategy", () => {
  it("removes the card via Delete in its ⋯ menu", async () => {
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
      within(beta).getByRole("button", { name: "Option actions" }),
    );
    // The menu is portalled out of the card, so query it from the document.
    await user.click(screen.getByRole("menuitem", { name: "Delete option" }));

    expect(store.removeOption).toHaveBeenCalledWith("goal-1", "o2");
  });
});

// ── Reorder mode ─────────────────────────────────────────────────────────────

describe("OptionsList — reorder mode", () => {
  it("hides the add field and per-card menus and shows the drag hint", () => {
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Alpha"), option("o2", "Beta")])}
        reordering
        onReorderingChange={vi.fn()}
      />,
    );

    expect(
      screen.queryByPlaceholderText("Add an option…"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Option actions" }),
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

// ── Show more / Show less ────────────────────────────────────────────────────

/**
 * The toggle is a worded link on its OWN line under the strategy, not a chevron floated over the
 * last line of text (which is what it used to be, and which covered the words it was hiding).
 *
 * jsdom has no layout, so nothing ever "overflows" on its own: `scrollHeight` is 0 and
 * `lineHeight` is "normal". Both are stubbed so the clamp measurement in `InlineText` decides the
 * text is clipped, which is the only condition that makes the toggle exist.
 */
describe("OptionsList — Show more toggle", () => {
  const LINE_HEIGHT = 24;
  // Captured before any spy exists, so re-stubbing can never recurse into itself.
  const realGetComputedStyle = window.getComputedStyle;

  afterEach(() => vi.restoreAllMocks());

  function pretendTextOverflows(lines: number) {
    // A Proxy rather than a copy: userEvent reads `pointer-events` off the same object, so every
    // property except lineHeight has to keep coming from the real declaration.
    vi.spyOn(window, "getComputedStyle").mockImplementation((el, pseudo) => {
      const real = realGetComputedStyle.call(window, el as Element, pseudo);
      return new Proxy(real, {
        get: (target, prop) =>
          prop === "lineHeight"
            ? `${LINE_HEIGHT}px`
            : Reflect.get(target, prop, target),
      });
    });
    vi.spyOn(HTMLElement.prototype, "scrollHeight", "get").mockReturnValue(
      LINE_HEIGHT * lines,
    );
  }

  it("renders the toggle as a worded button on its own line, after the text", async () => {
    pretendTextOverflows(6);
    render(
      <OptionsList
        goal={goalFixture([option("o1", "A very long strategy")])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    const toggle = await screen.findByRole("button", { name: "Show more" });
    // Its own line: the toggle is a SIBLING that follows the text block, never a child of the
    // clamped text (where it would sit inline on the last line).
    const text = screen.getByLabelText("Edit option");
    expect(text.contains(toggle)).toBe(false);
    expect(
      text.compareDocumentPosition(toggle) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    // And it is laid out in flow, not floated over the text like the old chevron.
    expect(toggle.className).not.toContain("absolute");

    await userEvent.click(toggle);
    expect(
      screen.getByRole("button", { name: "Show less" }),
    ).toBeInTheDocument();
  });

  it("has no toggle when the strategy fits inside the clamp", () => {
    pretendTextOverflows(2);
    render(
      <OptionsList
        goal={goalFixture([option("o1", "Short")])}
        reordering={false}
        onReorderingChange={vi.fn()}
      />,
    );

    expect(
      screen.queryByRole("button", { name: /Show (more|less)/ }),
    ).not.toBeInTheDocument();
  });
});
