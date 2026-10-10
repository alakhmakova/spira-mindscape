import { test, expect } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * **A confirm dialog's X stands on the title's line, and the sentence under it runs the card's full
 * width** (owner, 2026-10-09 — CLAUDE.md 3d-bis). It used to be pinned into the corner with the
 * whole header padded clear of it: the X floated above the title, and every line of the question
 * stopped 32px short of the card. Every confirm in the app is `ConfirmDialog`, so the goal card's
 * delete stands for all of them. Measured, because a class name says nothing about where it lands.
 */
for (const viewport of [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
]) {
  test(`the X is on the title's line, and the text runs under it — ${viewport.width}px`, async ({
    page,
  }) => {
    const title = `Confirm layout ${Date.now()} with a long enough name to wrap the question`;
    await createGoal(page, title);
    await page.setViewportSize(viewport);
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: `Delete "${title}"` }).click();
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toBeVisible();

    const heading = await dialog.getByRole("heading").boundingBox();
    const close = await dialog
      .getByRole("button", { name: "Close" })
      .boundingBox();
    const body = await dialog
      .getByText("Are you sure you want to permanently delete")
      .boundingBox();
    const lineHeight = await dialog
      .getByRole("heading")
      .evaluate((el) => parseFloat(getComputedStyle(el).lineHeight));
    expect(heading && close && body).toBeTruthy();

    // The X's centre falls inside the title's FIRST line.
    const xCentre = close!.y + close!.height / 2;
    expect(xCentre).toBeGreaterThanOrEqual(heading!.y);
    expect(xCentre).toBeLessThanOrEqual(heading!.y + lineHeight);
    // The question is wider than the title's column: it runs on under the X.
    expect(body!.x + body!.width).toBeGreaterThan(close!.x);
  });
}
