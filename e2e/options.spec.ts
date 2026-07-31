import { test, expect } from "@playwright/test";
import { createGoal, addOptions, optionCards } from "./helpers";

test.describe("Options — active radio + drag reorder", () => {
  test("the radio selects a single active option", async ({ page }) => {
    await createGoal(page, `E2E active ${Date.now()}`);
    await addOptions(page, ["Alpha strategy", "Beta strategy"]);

    const alpha = page.locator("li", { hasText: "Alpha strategy" });
    const beta = page.locator("li", { hasText: "Beta strategy" });

    // Select Alpha → its radio flips to the "deselect" state.
    await alpha.getByRole("button", { name: "Select strategy" }).click();
    await expect(
      alpha.getByRole("button", { name: "Deselect strategy" }),
    ).toBeVisible();

    // Selecting Beta deselects Alpha (only one active per goal).
    await beta.getByRole("button", { name: "Select strategy" }).click();
    await expect(
      beta.getByRole("button", { name: "Deselect strategy" }),
    ).toBeVisible();
    await expect(
      alpha.getByRole("button", { name: "Select strategy" }),
    ).toBeVisible();
  });

  test("deleting via the trash button removes the card", async ({ page }) => {
    await createGoal(page, `E2E delete ${Date.now()}`);
    await addOptions(page, ["Keep me", "Remove me"]);

    await page
      .locator("li", { hasText: "Remove me" })
      .getByRole("button", { name: "Remove" })
      .click();

    await expect(page.locator("li", { hasText: "Remove me" })).toHaveCount(0);
    await expect(page.locator("li", { hasText: "Keep me" })).toBeVisible();
  });

  test("dragging a card downward reorders it (regression: down-drag froze)", async ({
    page,
  }) => {
    await createGoal(page, `E2E drag ${Date.now()}`);
    await addOptions(page, ["First option", "Second option", "Third option"]);

    const cards = optionCards(page);
    await expect(cards).toHaveCount(3);
    await expect(cards.first()).toContainText("First option");

    // Enter reorder mode — the whole card becomes the drag target (the Reorder
    // toggle lives in the Options section header and shows for 2+ options).
    await page.getByRole("button", { name: "Reorder" }).click();

    const firstCard = page.locator("li", { hasText: "First option" });
    await firstCard.scrollIntoViewIfNeeded();
    const box = await firstCard.boundingBox();
    if (!box) throw new Error("option card not found");
    const startX = box.x + box.width / 2;
    const startY = box.y + box.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    for (let dy = 20; dy <= 200; dy += 20) {
      await page.mouse.move(startX, startY + dy, { steps: 2 });
    }
    await page.mouse.up();

    await expect(cards.first()).not.toContainText("First option");
    await expect(cards.last()).toContainText("First option");
  });
});
