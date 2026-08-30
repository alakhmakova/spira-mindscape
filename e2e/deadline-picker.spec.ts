import { test, expect, Page } from "@playwright/test";

/**
 * **The deadline picker is a modal on a phone and a popover on a laptop** (owner, 2026-08-29).
 *
 * On a phone it was the popover once — a floating card over an open form, its own teal strip
 * directly under the sheet's teal band, two heads stacked for one question — and then a bottom
 * sheet, which fixed that and introduced another: a second sheet stacked on the first reads as
 * leaving the form you are filling in. A centred modal is what Android has always done
 * (`DeadlinePickerDialog`), and the two surfaces draw the same card now.
 *
 * The behaviours differ on purpose and both are pinned here: the popover commits the moment a day
 * is tapped, the modal treats a day as a **draft** and commits from its foot. A 48px date grid
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

  test("opens as a modal with the app's own head, and a day is only a draft", async ({
    page,
  }) => {
    await openNewGoal(page);
    await page.getByText("Pick a deadline").click();
    await page.waitForTimeout(600);

    // A centred modal over the form's sheet — not a second sheet, and not a popover. The form's
    // drawer is still the only drawer on the page.
    expect(await page.locator("[data-vaul-drawer]").count()).toBe(1);
    await expect(page.getByRole("dialog")).toBeVisible();

    // **The head states the draft** (owner, 2026-08-29). Nothing chosen yet, so it is still the
    // prompt; once a day is picked it becomes that date, and the line that used to repeat it
    // above the grid is gone.
    const head = page.getByRole("dialog").getByRole("heading");
    await expect(head).toHaveText("Set deadline");
    await expect(
      page.getByRole("button", { name: "Set deadline", exact: true }),
    ).toBeVisible();
    // Nothing chosen yet, so there is nothing to confirm.
    await expect(
      page.getByRole("button", { name: "Set deadline", exact: true }),
    ).toBeDisabled();

    // A day is a draft — it does not close the modal and does not reach the form.
    await dayCell(page, DAY).click();
    await expect(head).toHaveText(/^\w+ \d{1,2}, \d{4}$/);
    // Short on purpose: the weekday and the "13d overdue" the old line carried would truncate
    // the head on a narrow phone, and a head that is sometimes cut is worse than a short one.
    expect(
      await head.evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
      "the head truncated — its title is too long to fit the band",
    ).toBe(true);
    // The modal is still open — a day commits nothing — and the foot can now be pressed.
    await expect(page.getByRole("dialog")).toBeVisible();
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

test.describe("the picker's foot fits its words", () => {
  // Android wrapped "Set deadline" onto two lines (owner, 2026-08-29) because its dialog took
  // the platform's default width and left each button ~139dp. The web sizes the card itself, so
  // it never did — but "never did" is worth pinning at the narrowest phone rather than assumed,
  // and 320 is where a label runs out of room first.
  test.use({ hasTouch: true, isMobile: true });

  for (const width of [320, 360, 412]) {
    test(`neither button wraps at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 780 });
      await openNewGoal(page);
      await page.getByText("Pick a deadline").click();
      await page.waitForTimeout(500);
      await dayCell(page, DAY).click();

      const dialog = page.getByRole("dialog");
      for (const name of ["Cancel", "Set deadline"]) {
        const fits = await dialog
          .getByRole("button", { name, exact: true })
          .evaluate((el) => el.scrollWidth <= el.clientWidth + 1);
        expect(fits, `"${name}" does not fit its button at ${width}px`).toBe(
          true,
        );
      }
    });
  }
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
