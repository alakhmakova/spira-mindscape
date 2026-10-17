import { test, expect, type Page } from "@playwright/test";
import { axeScan } from "./a11y-helpers";

/**
 * Reminders in the Add event form, end to end against the real backend
 * (specs/2026-10-10-upcoming-notifications/plan.md §2): a time of day, the default reminder that
 * follows it, a Custom reminder, the sentence that says exactly when they will arrive, the bell on
 * the row, and editing the event to take them away again.
 *
 * The page's clock is fixed, so "Today" is a known day and the sentence a known string. The server
 * keeps real time; it only stores what the form sends.
 */

const LAPTOP = { width: 1440, height: 900 };
test.use({ viewport: LAPTOP });

const upcoming = (page: Page) => page.getByRole("region", { name: "Upcoming" });

test("a timed event with a custom reminder, edited back to none", async ({
  page,
}) => {
  test.setTimeout(90_000);
  await page.clock.setFixedTime(new Date(2030, 5, 19, 8, 0));
  const name = `Dentist ${Date.now()}`;
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Add event" }).click();
  const dialog = page.getByRole("dialog", { name: "Add event" });
  await dialog.getByLabel("Event name").fill(name);
  await dialog.getByRole("button", { name: "Pick a date" }).click();
  await page.getByRole("button", { name: "Today", exact: true }).click();

  // All day by default, with the default all-day reminder, and the moment spelled out.
  await expect(dialog.getByRole("switch", { name: "All day" })).toBeChecked();
  await expect(
    dialog.getByRole("combobox", { name: /Reminder 1: On the day at 09:00/ }),
  ).toBeVisible();
  await expect(dialog).toContainText(
    "You’ll be reminded on Wednesday, June 19 at 09:00.",
  );

  // A time: the untouched default follows it.
  await dialog.getByRole("switch", { name: "All day" }).click();
  await dialog.getByLabel("Time").fill("21:30");
  await expect(
    dialog.getByRole("combobox", { name: /Reminder 1: 1 hour before/ }),
  ).toBeVisible();

  // Custom: 3 hours before.
  await dialog.getByRole("combobox", { name: /Reminder 1/ }).click();
  await page.getByRole("option", { name: "Custom…" }).click();
  const custom = page.getByRole("dialog", { name: "Custom reminder" });
  await axeScan(
    page,
    "Custom reminder",
    {},
    '[role="dialog"]:has(#custom-reminder-amount)',
  );
  await custom.getByLabel("Number").fill("3");
  await custom.getByRole("radio", { name: "Hours before" }).click();
  await custom.getByRole("button", { name: "Done" }).click();
  await expect(custom).toBeHidden();

  await dialog.getByRole("button", { name: "Add reminder" }).click();
  await expect(dialog).toContainText(
    "You’ll be reminded on June 19 at 18:30 and June 19 at 21:30.",
  );
  await axeScan(page, "Add event — reminders", {}, '[role="dialog"]');

  await dialog.getByRole("button", { name: "Save" }).click();
  await expect(dialog).toBeHidden();

  const row = upcoming(page).getByRole("listitem").filter({ hasText: name });
  // Two lines under the name: the calendar with the event's own time, and the bell with the
  // nearest reminder still to come — 3 hours before 21:30, ahead of the one at the time itself.
  await expect(row).toContainText("Date: Today at 21:30");
  await expect(row).toContainText("Next reminder: Today at 18:30");

  // What the server stored.
  const stored = await page.request.post("/graphql", {
    data: {
      query:
        "{ upcomingEvents { title time reminders { amount unit atMinuteOfDay } } }",
    },
  });
  const events = (await stored.json()).data.upcomingEvents as {
    title: string;
    time: string;
    reminders: { amount: number; unit: string; atMinuteOfDay: number | null }[];
  }[];
  const saved = events.find((e) => e.title === name);
  expect(saved?.time).toBe("21:30");
  expect(saved?.reminders).toEqual([
    { amount: 3, unit: "hour", atMinuteOfDay: null },
    { amount: 0, unit: "minute", atMinuteOfDay: null },
  ]);

  // Edit: the same form, filled in; take both reminders away.
  await row.getByRole("button", { name: `Edit "${name}"` }).click();
  const edit = page.getByRole("dialog", { name: "Edit event" });
  await expect(edit.getByLabel("Event name")).toHaveValue(name);
  await edit
    .getByRole("button", { name: /Remove reminder: 3 hours before/ })
    .click();
  await edit
    .getByRole("button", { name: /Remove reminder: At the time of the event/ })
    .click();
  await expect(edit).toContainText(
    "No reminder — this date will only show in Upcoming.",
  );
  await edit.getByRole("button", { name: "Save" }).click();
  await expect(edit).toBeHidden();
  await expect(row).toContainText("Date: Today at 21:30");
  await expect(row).not.toContainText("Next reminder");

  await row
    .getByRole("button", { name: `Remove "${name}" from Upcoming` })
    .click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Yes, remove" })
    .click();
  await expect(row).toHaveCount(0);
});
