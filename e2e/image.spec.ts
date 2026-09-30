import { test, expect } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { createGoal } from "./helpers";

const SAMPLE_PNG = fileURLToPath(
  new URL("./fixtures/sample.png", import.meta.url),
);

test.describe("Resources — image zoom", () => {
  test("an image resource opens fullscreen and zooms in/out", async ({
    page,
  }) => {
    await createGoal(page, `E2E img ${Date.now()}`);

    await page.getByRole("button", { name: "Add resource" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByRole("button", { name: "File" }).click();
    await dialog.locator('input[type="file"]').setInputFiles(SAMPLE_PNG);
    await dialog.getByRole("button", { name: "Add resource" }).click();

    // The card, not its words — the name is drawn twice (see the hover copy in Resources.tsx).
    await page
      .getByRole("button", {
        name: "sample.png",
        // `exact`, or the card's own "Actions for sample.png" button matches too.
        exact: true,
      })
      .click();
    // Over a page the way out is the X at the end of the action group; the chevron and its
    // "Back" belong to the full-screen state alone (CLAUDE.md -> 3i).
    await expect(page.getByRole("button", { name: "Close" })).toBeVisible();

    // Open the fullscreen viewer (force past the hover-hint overlay). Retry the whole open
    // step: a background goals refresh can re-render and detach the inline <img> mid-click.
    const reset = page.getByRole("button", { name: "Reset zoom" });
    await expect(async () => {
      const inline = page.locator('img[alt="sample.png"]').first();
      await inline.scrollIntoViewIfNeeded();
      await inline.click({ force: true });
      await expect(reset).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: 15_000 });
    await expect(reset).toHaveText("100%");

    // Zoom in, then out — the toolbar percentage reflects it.
    await page.getByRole("button", { name: "Zoom in" }).click();
    await expect(reset).toHaveText("150%");
    await page.getByRole("button", { name: "Zoom out" }).click();
    await expect(reset).toHaveText("100%");
  });
});
