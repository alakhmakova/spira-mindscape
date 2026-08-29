import { test, expect, Page } from "@playwright/test";

/**
 * **The deadline picker is a sheet on a phone and a popover on a laptop** (owner, 2026-08-29).
 *
 * On a phone it used to be the popover too, and it read wrong: a floating card over an open form,
 * its own teal strip directly under the sheet's teal band — two heads stacked for one question.
 * The app's language for "ask me something without leaving the page" on a phone is a bottom
 * sheet (CLAUDE.md → Sheets).
 *
 * The behaviours differ on purpose and both are pinned here: the popover commits the moment a day
 * is tapped, the sheet treats a day as a **draft** and commits from its foot. A 48px date grid
 * that commits *and closes* on a mis-tap leaves nothing to undo.
 */

const PHONE = { width: 412, height: 780 };
const LAPTOP = { width: 1280, height: 900 };

/** The date the picker starts on — today's month is what opens, so pick from it. */
const DAY = "15";

async function openNewGoal(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "New goal" }).first().click();
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeVisible();
  await page.waitForTimeout(500);
}

const dayCell = (page: Page, d: string) =>
  page
    .locator("button[data-day]")
    .filter({ hasText: new RegExp(`^${d}$`) })
    .first();

const deadlineField = (page: Page) =>
  page.getByRole("button", { name: /Pick a deadline|,\s*\d{4}/ }).first();

test.describe("the deadline picker on a phone", () => {
  test.use({ hasTouch: true, isMobile: true, viewport: PHONE });

  test("opens as a sheet with the app's own head, and a day is only a draft", async ({
    page,
  }) => {
    await openNewGoal(page);
    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(600);

    // The app's sheet, not a popover: a Kale head with the title and an X, and a pinned foot.
    // Two drawers are open at once — the form and the date sheet on top of it.
    expect(await page.locator("[data-vaul-drawer]").count()).toBe(2);
    await expect(
      page.getByRole("heading", { name: "Set deadline" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Set deadline", exact: true }),
    ).toBeVisible();
    // Nothing chosen yet, so there is nothing to confirm.
    await expect(
      page.getByRole("button", { name: "Set deadline", exact: true }),
    ).toBeDisabled();

    // A day is a draft — it does not close the sheet and does not reach the form.
    await dayCell(page, DAY).click();
    await expect(
      page.getByRole("heading", { name: "Set deadline" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Set deadline", exact: true }),
    ).toBeEnabled();

    // …and Cancel throws the draft away.
    await page.getByRole("button", { name: "Cancel" }).click();
    await page.waitForTimeout(400);
    await expect(page.getByText("Pick a deadline")).toBeVisible();
  });

  test("the foot commits, and clearing is a draft too", async ({ page }) => {
    await openNewGoal(page);
    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(600);
    await dayCell(page, DAY).click();
    await page
      .getByRole("button", { name: "Set deadline", exact: true })
      .click();
    await page.waitForTimeout(400);

    // The form's field now carries the date.
    await expect(page.getByText("Pick a deadline")).toHaveCount(0);
    await expect(deadlineField(page)).toContainText(new RegExp(`${DAY}`));

    // Reopen, Clear, and the confirm word says what it would do.
    await deadlineField(page).click();
    await page.waitForTimeout(600);
    await page.getByRole("button", { name: "Clear" }).click();
    const confirm = page.getByRole("button", { name: "Remove deadline" });
    await expect(confirm).toBeEnabled();
    await confirm.click();
    await page.waitForTimeout(400);
    await expect(page.getByText("Pick a deadline")).toBeVisible();
  });
});

test.describe("the deadline picker on a laptop", () => {
  test.use({ viewport: LAPTOP });

  test("still opens as a popover and commits on the day itself", async ({
    page,
  }) => {
    // The phone sheet is a second surface, not a replacement: the popover's one-click commit is
    // what the laptop has always had, and the refactor that split the two must not move it.
    await openNewGoal(page);
    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(500);

    expect(await page.locator("[data-vaul-drawer]").count()).toBe(0);
    await expect(
      page.locator("[data-radix-popper-content-wrapper]"),
    ).toBeVisible();

    await dayCell(page, DAY).click();
    await page.waitForTimeout(400);
    // One click, committed, closed.
    await expect(
      page.locator("[data-radix-popper-content-wrapper]"),
    ).toHaveCount(0);
    await expect(page.getByText("Pick a deadline")).toHaveCount(0);
  });
});
