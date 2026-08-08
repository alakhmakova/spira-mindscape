import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { TargetRow } from "./Targets";
import type { Target } from "@/lib/spira/types";

function checklistTargetFixture(): Extract<Target, { type: "checklist" }> {
  return {
    id: "target-1",
    type: "checklist",
    title: "Launch checklist",
    items: [{ id: "item-1", text: "Write spec", done: false }],
  };
}

/**
 * The mobile card keeps its progress controls behind "Update progress" (the reference-card
 * layout), so every test opens that panel first.
 */
async function openProgress(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: /Update progress/ }));
}

/** Adding a task starts as a link that expands into the input. */
async function openAddTask(user: ReturnType<typeof userEvent.setup>) {
  await openProgress(user);
  await user.click(screen.getByRole("button", { name: "Add task" }));
}

/**
 * Every test here drives the card through several `userEvent` round-trips, and each one is a
 * full jsdom render pass. Alone the file finishes in about three seconds; running beside
 * eighteen other files on a loaded machine a single test can pass five seconds and trip
 * vitest's default. These assertions are about behaviour, not speed, so they get room rather
 * than a race.
 */
const SLOW_UI = 20_000;

describe("TargetRow (mobile) — manual checklist task entry", () => {
  it(
    "shows an 'Add task…' input for checklist targets",
    async () => {
      const user = userEvent.setup();
      render(
        <TargetRow
          target={checklistTargetFixture()}
          onUpdate={vi.fn()}
          onRemove={vi.fn()}
        />,
      );
      await openAddTask(user);

      expect(
        screen.getByPlaceholderText("Add task… (Enter to confirm)"),
      ).toBeInTheDocument();
    },
    SLOW_UI,
  );

  it(
    "appends a new task to the checklist when typing and clicking Add",
    async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();
      const target = checklistTargetFixture();

      render(
        <TargetRow target={target} onUpdate={onUpdate} onRemove={vi.fn()} />,
      );
      await openAddTask(user);

      const input = screen.getByPlaceholderText("Add task… (Enter to confirm)");
      await user.type(input, "Buy domain name");
      await user.click(screen.getByRole("button", { name: "Add" }));

      expect(onUpdate).toHaveBeenCalledTimes(1);
      const patch = onUpdate.mock.calls[0][0] as Partial<
        Extract<Target, { type: "checklist" }>
      >;

      expect(patch.items).toHaveLength(2);
      expect(patch.items?.[0]).toEqual(target.items[0]);
      expect(patch.items?.[1]).toMatchObject({
        text: "Buy domain name",
        done: false,
      });
      expect(patch.items?.[1].id).toEqual(expect.any(String));
      expect(patch.items?.[1].id).not.toBe("");
    },
    SLOW_UI,
  );

  it(
    "appends a new task when pressing Enter",
    async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();
      const target = checklistTargetFixture();

      render(
        <TargetRow target={target} onUpdate={onUpdate} onRemove={vi.fn()} />,
      );
      await openAddTask(user);

      const input = screen.getByPlaceholderText("Add task… (Enter to confirm)");
      await user.type(input, "Set up analytics{Enter}");

      expect(onUpdate).toHaveBeenCalledTimes(1);
      const patch = onUpdate.mock.calls[0][0] as Partial<
        Extract<Target, { type: "checklist" }>
      >;
      expect(patch.items?.[1]).toMatchObject({
        text: "Set up analytics",
        done: false,
      });
    },
    SLOW_UI,
  );

  it(
    "clears and refocuses the input after adding a task",
    async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();

      render(
        <TargetRow
          target={checklistTargetFixture()}
          onUpdate={onUpdate}
          onRemove={vi.fn()}
        />,
      );
      await openAddTask(user);

      const input: HTMLInputElement = screen.getByPlaceholderText(
        "Add task… (Enter to confirm)",
      );
      await user.type(input, "Another task{Enter}");

      expect(input.value).toBe("");
      expect(input).toHaveFocus();
    },
    SLOW_UI,
  );

  it(
    "does not call onUpdate for blank input",
    async () => {
      const user = userEvent.setup();
      const onUpdate = vi.fn();

      render(
        <TargetRow
          target={checklistTargetFixture()}
          onUpdate={onUpdate}
          onRemove={vi.fn()}
        />,
      );
      await openAddTask(user);

      const input = screen.getByPlaceholderText("Add task… (Enter to confirm)");
      await user.type(input, "   {Enter}");

      expect(onUpdate).not.toHaveBeenCalled();
      expect(
        screen.queryByRole("button", { name: "Add" }),
      ).not.toBeInTheDocument();
    },
    SLOW_UI,
  );
});
