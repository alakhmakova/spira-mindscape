import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/**
 * The Add event form on a phone — a bottom sheet by the sheet rules (CLAUDE.md 3e): the Kale
 * head with the title and its own X, the same questions, and Cancel / Save pinned at the foot.
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

const D1 = "2026-11-01T00:00:00.000Z";
const add = vi.fn();
const onOpenChange = vi.fn();
const desktopWidth = window.innerWidth;

beforeEach(() => {
  vi.clearAllMocks();
  add.mockResolvedValue(true);
  window.innerWidth = 390;
  useUpcoming.setState({ events: [], status: "ready", add });
  useSpira.setState({ goals: [] });
});

afterEach(() => {
  window.innerWidth = desktopWidth;
});

describe("AddEventDialog (phone)", () => {
  it("is a sheet with the app's head, and the form's questions", async () => {
    render(<AddEventDialog open onOpenChange={onOpenChange} />);

    // The title sits in the sheet's Kale band (`SheetHead`, Primary) — the laptop's modal has a
    // white head, so this is what tells the two shapes apart.
    const title = await screen.findByRole("heading", { name: "Add event" });
    expect(title.parentElement).toHaveClass("bg-primary");
    expect(screen.getAllByRole("radio")).toHaveLength(4);
    expect(screen.getByLabelText("Event name")).toBeInTheDocument();
  });

  it("saves from the pinned foot", async () => {
    const user = userEvent.setup();
    render(<AddEventDialog open onOpenChange={onOpenChange} />);

    await user.type(await screen.findByLabelText("Event name"), "Trip");
    await user.type(screen.getByLabelText("Event date"), D1);
    await user.click(screen.getByRole("button", { name: "Save" }));

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

  it("closes from the head's X without saving", async () => {
    const user = userEvent.setup();
    render(<AddEventDialog open onOpenChange={onOpenChange} />);

    const head = (await screen.findByRole("heading", { name: "Add event" }))
      .parentElement as HTMLElement;
    await user.click(within(head).getByRole("button"));
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(add).not.toHaveBeenCalled();
  });
});
