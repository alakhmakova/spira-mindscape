import { test, expect, type Page } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * **Upcoming** — the All-goals page's right-hand column and its Add event form (owner,
 * 2026-10-09). What is pinned here is the loop the feature is for, end to end against the real
 * backend:
 *
 *  - an important date is added with a name and a date, shows under "Important dates" as
 *    "Today", and goes again when removed;
 *  - a goal with a deadline can be added as an event, and **the date set in the form becomes the
 *    goal's deadline** — the card shows it — because a linked event has no date of its own;
 *  - removing that event **removes the goal's deadline**, and the confirm says so first.
 */

const LAPTOP = { width: 1440, height: 900 };

async function openAddEvent(page: Page) {
  await page.getByRole("button", { name: "Add event" }).click();
  const dialog = page.getByRole("dialog", { name: "Add event" });
  await expect(dialog).toBeVisible();
  return dialog;
}

const upcoming = (page: Page) => page.getByRole("region", { name: "Upcoming" });

test.use({ viewport: LAPTOP });

test("an important date is added, shown as Today, and removed", async ({
  page,
}) => {
  const name = `Birthday ${Date.now()}`;
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  const dialog = await openAddEvent(page);
  // Important date is the type the form opens on.
  await expect(
    dialog.getByRole("radio", { name: "Important date" }),
  ).toHaveAttribute("aria-checked", "true");
  const save = dialog.getByRole("button", { name: "Save" });
  await expect(save).toBeDisabled();

  await dialog.getByLabel("Event name").fill(name);
  await dialog.getByRole("button", { name: "Pick a date" }).click();
  await page.getByRole("button", { name: "Today", exact: true }).click();
  await save.click();
  await expect(dialog).toBeHidden();

  const row = upcoming(page).getByRole("listitem").filter({ hasText: name });
  await expect(row).toContainText("Today");

  await row
    .getByRole("button", { name: `Remove "${name}" from Upcoming` })
    .click();
  const confirm = page.getByRole("alertdialog");
  await expect(confirm).toContainText(name);
  // An important date has no deadline behind it, so the confirm promises none.
  await expect(confirm).not.toContainText("deadline");
  await confirm.getByRole("button", { name: "Yes, remove" }).click();
  await expect(row).toHaveCount(0);
});

test("a goal's event carries its deadline both ways", async ({ page }) => {
  test.setTimeout(120_000);
  const title = `Upcoming goal ${Date.now()}`;
  await createGoal(page, title);
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  // Give the goal a deadline from its card: today, through the laptop popover's own link.
  const card = page
    .locator("div.group")
    .filter({ has: page.getByRole("link", { name: title }) })
    .first();
  await card.getByText("Set deadline").click();
  await page.getByRole("button", { name: "Today", exact: true }).click();
  await expect(card).toContainText("Due date");

  const dialog = await openAddEvent(page);
  await dialog.getByRole("radio", { name: "Goal" }).click();
  await dialog.getByRole("combobox").click();
  await page.getByRole("option", { name: title }).click();
  await expect(dialog).toContainText(
    "Changing it moves the goal's deadline too.",
  );

  // Move the date to the 15th of next month, in the form.
  await dialog
    .getByRole("button", { name: /\d{1,2}(st|nd|rd|th), \d{4}/ })
    .click();
  await page.getByRole("button", { name: /next month/i }).click();
  await page
    .locator("button[data-day]")
    .filter({ hasText: /^15$/ })
    .first()
    .click();
  await dialog.getByRole("button", { name: "Save" }).click();
  await expect(dialog).toBeHidden();

  // The goal's own deadline moved with it.
  const next = new Date();
  next.setDate(1);
  next.setMonth(next.getMonth() + 1);
  const month = next.toLocaleDateString("en-US", { month: "short" });
  await expect(card).toContainText(`Due date ${month} 15`);
  const row = upcoming(page).getByRole("listitem").filter({ hasText: title });
  await expect(row).toBeVisible();

  // Removing it says the deadline goes too — and then it does.
  await row
    .getByRole("button", { name: `Remove "${title}" from Upcoming` })
    .click();
  const confirm = page.getByRole("alertdialog");
  await expect(confirm).toContainText("This also removes the goal's deadline");
  await confirm.getByRole("button", { name: "Yes, remove" }).click();
  await expect(row).toHaveCount(0);
  await expect(card).toContainText("Set deadline");

  // And it stays removed after a reload — the server cleared it, not only the page.
  await page.reload();
  await page.waitForLoadState("networkidle");
  await expect(
    page
      .locator("div.group")
      .filter({ has: page.getByRole("link", { name: title }) })
      .first(),
  ).toContainText("Set deadline");
});

test.describe("on a phone", () => {
  test.use({
    viewport: { width: 390, height: 844 },
    hasTouch: true,
    isMobile: true,
  });

  test("the form is a sheet with the Kale head, and an important date goes all the way", async ({
    page,
  }) => {
    const name = `Phone date ${Date.now()}`;
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    await upcoming(page).getByRole("button", { name: "Add event" }).tap();
    const title = page.getByRole("heading", { name: "Add event" });
    await expect(title).toBeVisible();
    // The sheet's own Kale head (CLAUDE.md 3e), not the laptop's white modal head.
    await expect(title.locator("..")).toHaveClass(/bg-primary/);

    await page.getByLabel("Event name").fill(name);
    // On a phone the date is a modal and a day is a draft; its foot commits it.
    await page.getByRole("button", { name: "Pick a date" }).tap();
    await page.getByRole("button", { name: "Today", exact: true }).tap();
    await page.getByRole("button", { name: "Set deadline" }).tap();
    await page.getByRole("button", { name: "Save" }).tap();
    await expect(title).toBeHidden();

    const row = upcoming(page).getByRole("listitem").filter({ hasText: name });
    await expect(row).toContainText("Today");
    await row
      .getByRole("button", { name: `Remove "${name}" from Upcoming` })
      .tap();
    await page.getByRole("button", { name: "Yes, remove" }).tap();
    await expect(row).toHaveCount(0);
  });
});
