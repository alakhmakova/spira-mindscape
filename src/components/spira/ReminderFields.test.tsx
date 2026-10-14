import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { CustomReminderDialog, RemindersField } from "./ReminderFields";
import {
  amountError,
  defaultReminder,
  type Reminder,
} from "@/lib/spira/reminders";

/**
 * The reminders block and the Custom dialog: the cap of five, the number's validation, and the
 * unit radios — a real radio group, with the arrow keys moving the choice.
 */
function Harness({
  initial,
  timed = false,
  onChange,
}: {
  initial: Reminder[];
  timed?: boolean;
  onChange?: (r: Reminder[]) => void;
}) {
  const [reminders, setReminders] = useState(initial);
  return (
    <RemindersField
      reminders={reminders}
      onChange={(next) => {
        setReminders(next);
        onChange?.(next);
      }}
      timed={timed}
      date={new Date(2030, 5, 19).toISOString()}
      time={timed ? "14:00" : undefined}
    />
  );
}

describe("RemindersField", () => {
  it("adds the next free preset, and stops offering at five", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness initial={[defaultReminder(false)]} onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: "Add reminder" }));
    expect(onChange).toHaveBeenLastCalledWith([
      defaultReminder(false),
      { amount: 1, unit: "day", atMinuteOfDay: 540 },
    ]);
    await user.click(screen.getByRole("button", { name: "Add reminder" }));
    await user.click(screen.getByRole("button", { name: "Add reminder" }));
    // All four presets taken: the fifth comes from the Custom dialog.
    await user.click(screen.getByRole("button", { name: "Add reminder" }));
    await user.clear(screen.getByLabelText("Number"));
    await user.type(screen.getByLabelText("Number"), "3");
    await user.click(screen.getByRole("radio", { name: "Weeks before" }));
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(screen.getAllByRole("combobox")).toHaveLength(5);
    expect(screen.queryByRole("button", { name: "Add reminder" })).toBeNull();
  });

  it("removes a reminder, and says when none is left", async () => {
    const user = userEvent.setup();
    render(<Harness initial={[defaultReminder(false)]} />);
    expect(
      screen.getByText("You’ll be reminded on Wednesday, June 19 at 09:00."),
    ).toBeTruthy();

    await user.click(
      screen.getByRole("button", {
        name: "Remove reminder: On the day at 09:00",
      }),
    );
    expect(
      screen.getByText("No reminder — this date will only show in Upcoming."),
    ).toBeTruthy();
  });
});

describe("CustomReminderDialog", () => {
  it("validates the number inline, and Done waits for a valid one", async () => {
    const user = userEvent.setup();
    const onDone = vi.fn();
    render(
      <CustomReminderDialog open timed onCancel={() => {}} onDone={onDone} />,
    );
    const number = screen.getByLabelText("Number");
    await user.clear(number);
    await user.tab();
    expect(screen.getByRole("alert").textContent).toBe(
      "Enter a number from 0 to 999",
    );
    expect(number.getAttribute("aria-invalid")).toBe("true");
    expect(
      (screen.getByRole("button", { name: "Done" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);

    await user.type(number, "45");
    await user.click(screen.getByRole("button", { name: "Done" }));
    expect(onDone).toHaveBeenCalledWith({
      amount: 45,
      unit: "minute",
      atMinuteOfDay: undefined,
    });
  });

  it("offers minutes and hours only on a timed date, and a time of day only on an all-day one", () => {
    const { unmount } = render(
      <CustomReminderDialog
        open
        timed={false}
        onCancel={() => {}}
        onDone={() => {}}
      />,
    );
    expect(screen.getAllByRole("radio").map((r) => r.textContent)).toEqual([
      "Days before",
      "Weeks before",
    ]);
    expect(screen.getByLabelText("At")).toBeTruthy();
    unmount();

    render(
      <CustomReminderDialog open timed onCancel={() => {}} onDone={() => {}} />,
    );
    expect(screen.getAllByRole("radio")).toHaveLength(4);
    expect(screen.queryByLabelText("At")).toBeNull();
  });

  it("moves the unit with the arrow keys, as a radio group does", async () => {
    const user = userEvent.setup();
    render(
      <CustomReminderDialog open timed onCancel={() => {}} onDone={() => {}} />,
    );
    const minutes = screen.getByRole("radio", { name: "Minutes before" });
    expect(minutes.getAttribute("aria-checked")).toBe("true");
    minutes.focus();
    await user.keyboard("{ArrowDown}");
    const hours = screen.getByRole("radio", { name: "Hours before" });
    expect(hours.getAttribute("aria-checked")).toBe("true");
    expect(document.activeElement).toBe(hours);
    await user.keyboard("{ArrowUp}{ArrowUp}");
    expect(
      screen
        .getByRole("radio", { name: "Weeks before" })
        .getAttribute("aria-checked"),
    ).toBe("true");
  });
});

it("amountError accepts 0 to 999 — 0 is the event's own time", () => {
  expect(amountError("0")).toBeUndefined();
  expect(amountError("1")).toBeUndefined();
  expect(amountError("999")).toBeUndefined();
  expect(amountError("")).toBeDefined();
  expect(amountError("1000")).toBeDefined();
});
