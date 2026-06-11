import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DesktopTargetsTable } from "./Targets";
import { useSpira } from "@/lib/spira/store";
import type { Goal, Target } from "@/lib/spira/types";

function goalFixture(targets: Target[]): Goal {
  return {
    id: "goal-1",
    title: "Goal",
    description: "",
    confidence: 5,
    createdAt: "2026-05-15T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets,
  };
}

const numericTarget: Target = {
  id: "target-numeric",
  type: "numeric",
  title: "Outbound applications",
  current: 5,
  total: 10,
};

const checklistTarget: Target = {
  id: "target-checklist",
  type: "checklist",
  title: "Launch checklist",
  items: [{ id: "item-1", text: "Write spec", done: false }],
};

beforeEach(() => {
  useSpira.setState({
    updateTarget: vi.fn(),
    removeTarget: vi.fn(),
  });
});

describe("DesktopTargetsTable — side panel only opens via Status column", () => {
  it("does not open the Update Progress panel when clicking the title", async () => {
    const user = userEvent.setup();
    const goal = goalFixture([numericTarget]);
    render(<DesktopTargetsTable goal={goal} />);

    expect(
      screen.queryByRole("heading", { name: "Update Progress" }),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("textbox", { name: "Edit target title" }),
    );

    expect(
      screen.queryByRole("heading", { name: "Update Progress" }),
    ).not.toBeInTheDocument();
  });

  it("does not open the Tasks panel when clicking the title", async () => {
    const user = userEvent.setup();
    const goal = goalFixture([checklistTarget]);
    render(<DesktopTargetsTable goal={goal} />);

    expect(screen.queryByPlaceholderText("Add task…")).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("textbox", { name: "Edit target title" }),
    );

    expect(screen.queryByPlaceholderText("Add task…")).not.toBeInTheDocument();
  });

  it("does not open a panel when clicking the progress cell", async () => {
    const user = userEvent.setup();
    const goal = goalFixture([numericTarget]);
    render(<DesktopTargetsTable goal={goal} />);

    await user.click(screen.getByText("50%"));

    expect(
      screen.queryByRole("heading", { name: "Update Progress" }),
    ).not.toBeInTheDocument();
  });

  it("opens the Update Progress panel when clicking the Status button", async () => {
    const user = userEvent.setup();
    const goal = goalFixture([numericTarget]);
    render(<DesktopTargetsTable goal={goal} />);

    await user.click(screen.getByRole("button", { name: "Update" }));

    expect(
      screen.getByRole("heading", { name: "Update Progress" }),
    ).toBeInTheDocument();
  });

  it("opens the Tasks panel when clicking the Status button", async () => {
    const user = userEvent.setup();
    const goal = goalFixture([checklistTarget]);
    render(<DesktopTargetsTable goal={goal} />);

    await user.click(screen.getByRole("button", { name: "Tasks" }));

    expect(screen.getByPlaceholderText("Add task…")).toBeInTheDocument();
  });
});
