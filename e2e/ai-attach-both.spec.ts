import { fileURLToPath } from "node:url";
import { test, expect, type Page } from "@playwright/test";
import { createGoal, stubAiProvider } from "./helpers";

const PHOTO = fileURLToPath(new URL("./fixtures/sample.png", import.meta.url));

/**
 * **A saved resource and a photo on the same message** (BUG-027 / BUG-030).
 *
 * The two kinds of attachment travel by completely different routes: a resource is an id the
 * server resolves against the goal, a photo is bytes read in the browser. They meet only on
 * the composer and again in the message handed to the model, so mixing them is exactly where
 * one can quietly drop the other — and it is the ordinary thing a user does.
 *
 * Both orders are covered because they are not symmetric in the code: the file path claims a
 * slot before its read finishes (`claimedRef`), while a resource is added synchronously, so
 * "photo then resource" and "resource then photo" exercise different interleavings of the
 * same cap.
 *
 * What is asserted here is the UI half — both chips present and openable, before and after
 * Send. That the model actually receives both is pinned in
 * `AiChatServiceResourceAttachmentTest`, where a provider can be observed.
 */
async function setUpGoalWithResource(page: Page) {
  // The link chip opens the real URL in a new tab. Stub the destination so the assertion
  // is about OUR behaviour and not about whether the runner has internet — without this
  // the popup lands on chrome-error:// and the test fails for the wrong reason.
  await page.context().route("https://example.com/**", (route) =>
    route.fulfill({
      contentType: "text/html",
      body: "<title>Job board</title>",
    }),
  );

  // No real key is needed to attach something — see `stubAiProvider`.
  await stubAiProvider(page);
  await createGoal(page, `AI both ${Date.now()}`);

  await page.getByRole("button", { name: "Add resource" }).click();
  await page.getByRole("button", { name: "Link", exact: true }).first().click();
  await page.getByPlaceholder("https://").fill("https://example.com/jobs");
  await page
    .locator('div:has(> label:has-text("Title")) input')
    .first()
    .fill("Job board");
  await page.getByRole("button", { name: "Add resource" }).last().click();
  await page.waitForLoadState("networkidle");

  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();
}

async function attachResource(page: Page) {
  await page.getByRole("button", { name: "Attach" }).click();
  await page.getByRole("button", { name: "From resources" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /Job board/ })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

async function attachPhoto(page: Page) {
  await page.locator('input[type="file"]').first().setInputFiles(PHOTO);
  await page.waitForTimeout(800);
}

/** Both chips are on the composer, and each one opens what it is. */
async function expectBothOpenable(page: Page) {
  // The photo opens its picture.
  await page
    .getByRole("button", { name: /Open sample\.png/ })
    .last()
    .click();
  await expect(page.getByRole("img", { name: "sample.png" })).toBeVisible();
  await page.keyboard.press("Escape");

  // The link opens in a new tab rather than a preview (owner, 2026-08-23), so the click is
  // observed as a popup instead of a modal.
  const [popup] = await Promise.all([
    page.waitForEvent("popup"),
    page
      .getByRole("button", { name: /Open Job board/ })
      .last()
      .click(),
  ]);
  expect(popup.url()).toContain("example.com/jobs");
  await popup.close();
}

test("a resource attached first, then a photo — both survive the send", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await setUpGoalWithResource(page);

  await attachResource(page);
  await attachPhoto(page);

  // BEFORE sending: both are there, and both open.
  await expect(page.getByText("Job board").last()).toBeVisible();
  await expect(page.getByText("sample.png").last()).toBeVisible();
  await expectBothOpenable(page);

  await page.getByPlaceholder(/Ask, plan/i).fill("compare these");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2000);

  // AFTER sending: still both, still openable from the sent message.
  await expectBothOpenable(page);
});

test("a photo attached first, then a resource — both survive the send", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await setUpGoalWithResource(page);

  await attachPhoto(page);
  await attachResource(page);

  await expect(page.getByText("sample.png").last()).toBeVisible();
  await expect(page.getByText("Job board").last()).toBeVisible();
  await expectBothOpenable(page);

  await page.getByPlaceholder(/Ask, plan/i).fill("compare these");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(2000);

  await expectBothOpenable(page);
});
