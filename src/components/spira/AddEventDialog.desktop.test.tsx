import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/**
 * The Add event form on a laptop — a centred modal. The calendar is replaced by a plain field:
 * `DeadlinePopover` has its own tests, and what matters here is what the form does with a date.
 */
vi.mock("./DeadlinePopover", () => ({
  DeadlinePopover: ({
    iso,
    onChange,
  }: {
    iso?: string;
    onChange: (v: string | undefined) => void;
  }) => (
    <input
      aria-label="Event date"
      value={iso ?? ""}
      onChange={(e) => onChange(e.target.value || undefined)}
    />
  ),
}));

import { AddEventDialog } from "./AddEventDialog";
import { useSpira } from "@/lib/spira/store";
import { useUpcoming } from "@/lib/spira/upcoming-store";
import { FIELD_LIMITS } from "@/lib/spira/limits";
import type { Goal } from "@/lib/spira/types";

const D1 = "2026-11-01T00:00:00.000Z";
const D2 = "2026-11-20T00:00:00.000Z";

function goal(id: string, title: string, deadline?: string): Goal {
  return {
    id,
    title,
    description: "",
    confidence: 5,
    deadline,
    createdAt: "2026-01-01T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [],
  };
}

const add = vi.fn();
const onOpenChange = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  add.mockResolvedValue(true);
  useUpcoming.setState({ events: [], status: "ready", add });
  useSpira.setState({
    goals: [
      goal("1", "Run a marathon", D1),
      goal("2", "Learn Spanish"), // no deadline
      goal("3", "Read a book", D1), // already in Upcoming, below
    ],
  });
  useUpcoming.setState({ events: [{ id: "e", kind: "goal", goalId: "3" }] });
});

function open() {
  render(<AddEventDialog open onOpenChange={onOpenChange} />);
  return screen.getByRole("dialog", { name: "Add event" });
}

describe("AddEventDialog (laptop)", () => {
  it("opens on Important date, with Save waiting for a name and a date", async () => {
    const user = userEvent.setup();
    const dialog = open();

    expect(
      within(dialog).getByRole("radio", { name: "Important date" }),
    ).toHaveAttribute("aria-checked", "true");
    const save = within(dialog).getByRole("button", { name: "Save" });
    expect(save).toBeDisabled();

    await user.type(within(dialog).getByLabelText("Event name"), "Trip");
    expect(save).toBeDisabled();
    await user.type(within(dialog).getByLabelText("Event date"), D1);
    expect(save).toBeEnabled();
  });

  it("says what Upcoming is for, and that nothing is added to it automatically", () => {
    const dialog = open();
    expect(dialog).toHaveAccessibleDescription(/dates you simply can’t miss/);
    expect(dialog).toHaveAccessibleDescription(
      /Nothing gets added here automatically/,
    );
  });

  it("saves an important date trimmed, and closes", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.type(within(dialog).getByLabelText("Event name"), "  Trip  ");
    await user.type(within(dialog).getByLabelText("Event date"), D1);
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(add).toHaveBeenCalledWith({
      kind: "important",
      title: "Trip",
      date: D1,
      time: undefined,
      // Every new event carries the default reminder (plan §2).
      reminders: [{ amount: 0, unit: "day", atMinuteOfDay: 540 }],
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("refuses a blank name", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.type(within(dialog).getByLabelText("Event name"), "   ");
    await user.type(within(dialog).getByLabelText("Event date"), D1);
    expect(within(dialog).getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("says when the name is over the limit, and does not cut it short", async () => {
    const user = userEvent.setup();
    const dialog = open();
    const name = within(dialog).getByLabelText("Event name");
    const tooLong = "x".repeat(FIELD_LIMITS.upcomingTitle + 1);

    await user.click(name);
    await user.paste(tooLong);
    await user.type(within(dialog).getByLabelText("Event date"), D1);

    expect(name).toHaveValue(tooLong);
    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Event name must be 200 characters or fewer (you have 201).",
    );
    expect(within(dialog).getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("keeps the form open when the save is refused", async () => {
    const user = userEvent.setup();
    add.mockResolvedValue(false);
    const dialog = open();

    await user.type(within(dialog).getByLabelText("Event name"), "Trip");
    await user.type(within(dialog).getByLabelText("Event date"), D1);
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(add).toHaveBeenCalled();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("offers only goals with a deadline that are not in Upcoming yet", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.click(within(dialog).getByRole("radio", { name: "Goal" }));
    await user.click(
      within(dialog).getByRole("combobox", { name: "Choose goal" }),
    );

    const options = screen.getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Run a marathon"]);
  });

  it("fills the date from the goal's deadline, and saves a changed one with it", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.click(within(dialog).getByRole("radio", { name: "Goal" }));
    await user.click(
      within(dialog).getByRole("combobox", { name: "Choose goal" }),
    );
    await user.click(screen.getByRole("option", { name: "Run a marathon" }));

    const date = within(dialog).getByLabelText("Event date");
    expect(date).toHaveValue(D1);
    expect(dialog).toHaveTextContent(
      "Changing it moves the goal's deadline too.",
    );

    await user.clear(date);
    await user.type(date, D2);
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(add).toHaveBeenCalledWith({
      kind: "goal",
      item: expect.objectContaining({ id: "1", kind: "goal" }),
      date: D2,
      time: undefined,
      // Every new event carries the default reminder (plan §2).
      reminders: [{ amount: 0, unit: "day", atMinuteOfDay: 540 }],
    });
  });

  it("says so when there is nothing of that kind to add", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.click(within(dialog).getByRole("radio", { name: "Target" }));
    expect(
      within(dialog).getByRole("combobox", { name: "Choose target" }),
    ).toBeDisabled();
    expect(dialog).toHaveTextContent(
      "Only a target that has a deadline can be added, and each only once.",
    );
    expect(within(dialog).getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("moves the type with the arrow keys, as a radio group does", async () => {
    const user = userEvent.setup();
    const dialog = open();

    within(dialog).getByRole("radio", { name: "Important date" }).focus();
    await user.keyboard("{ArrowRight}");
    expect(within(dialog).getByRole("radio", { name: "Goal" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(within(dialog).getByRole("radio", { name: "Goal" })).toHaveFocus();
    await user.keyboard("{ArrowLeft}{ArrowLeft}");
    expect(within(dialog).getByRole("radio", { name: "Task" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("closes from Cancel and from its X without saving", async () => {
    const user = userEvent.setup();
    const dialog = open();

    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await user.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(onOpenChange).toHaveBeenCalledTimes(2);
    expect(add).not.toHaveBeenCalled();
  });
});
