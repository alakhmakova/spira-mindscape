import { fileURLToPath } from "node:url";
import { test, expect } from "@playwright/test";
import { createGoal, stubAiProvider } from "./helpers";

const PHOTO = fileURLToPath(new URL("./fixtures/sample.png", import.meta.url));

/**
 * **A photo attached to an AI message, and looked at either side of Send** (BUG-027 / BUG-030).
 *
 * The web's attach button is one `<input type="file" accept="image/*,…">`; on a phone that is
 * what opens the camera, so "take a photo" and "choose a picture" are the same code path here
 * and this spec covers both. Android's camera is a separate intent and is covered by its own
 * tests.
 *
 * Why the preview matters enough to test: an attachment the user cannot open is an attachment
 * they cannot check. Until 2026-08-23 the composer opened images and nothing else, and a sent
 * message's chips followed the same rule — which is how resource chips ended up inert on both
 * sides of Send. The fix made one rule (`attachmentOpener`) serve both chip sites, so this
 * spec deliberately asserts the same behaviour twice: before sending and after.
 *
 * That the model actually *receives* the image is a backend concern and is pinned by
 * `AiChatServiceImageAttachmentTest` — including that a blind model is told so in words rather
 * than being handed silence to invent from.
 */
test("attach a photo, open it before sending, and open it again after", async ({
  page,
}) => {
  test.setTimeout(120_000);
  // No real key is needed to attach something — see `stubAiProvider`.
  await stubAiProvider(page);
  await createGoal(page, `AI photo ${Date.now()}`);

  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();

  // The attach control is a file input — the same one a phone points at its camera.
  await page.locator('input[type="file"]').first().setInputFiles(PHOTO);
  await page.waitForTimeout(800);

  // BEFORE sending: the chip is there and it opens.
  const chip = page.getByRole("button", { name: /Open sample\.png/ });
  await expect(chip).toBeVisible();
  await chip.click();
  // The preview renders the picture itself, named after the file (`ContentModal`).
  await expect(page.getByRole("img", { name: "sample.png" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("img", { name: "sample.png" })).toHaveCount(0);

  // Send it.
  await page.getByPlaceholder(/Ask, plan/i).fill("what is in this picture?");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2000);

  // AFTER sending: still there, still openable — the user can check what the assistant got.
  const sentChip = page
    .getByRole("button", { name: /Open sample\.png/ })
    .last();
  await expect(sentChip).toBeVisible();
  await sentChip.click();
  await expect(page.getByRole("img", { name: "sample.png" })).toBeVisible();
});
