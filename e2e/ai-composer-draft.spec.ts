import { fileURLToPath } from "node:url";
import { test, expect } from "@playwright/test";
import { createGoal } from "./helpers";

const PHOTO = fileURLToPath(new URL("./fixtures/sample.png", import.meta.url));

/**
 * **An unsent message survives a page reload** (BUG-049).
 *
 * Android's composer has survived the process being killed since BUG-050 — draft and every chip,
 * resource or photo. The web's lived entirely in React state, so an accidental F5, a crashed tab
 * or a restored session dropped the sentence someone was in the middle of writing and every file
 * attached to it, silently. The owner asked for the two surfaces to match.
 *
 * This drives the real thing rather than the store underneath it (`composer-draft.test.ts` covers
 * that): what matters is that after `page.reload()` the composer looks the way it was left.
 */

test("the draft, a resource chip and a photo all survive a reload", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await createGoal(page, `AI draft ${Date.now()}`);

  // A resource to attach, so the id-shaped chip is covered alongside the byte-shaped one.
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

  const composer = page.getByPlaceholder("Ask, plan, or request an action…");
  await composer.fill("What does this say about the salary band?");

  await page.getByRole("button", { name: "Attach" }).click();
  await page.getByRole("button", { name: "From resources" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /Job board/ })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);

  await page.locator('input[type="file"]').first().setInputFiles(PHOTO);
  await expect(page.getByText("sample.png")).toBeVisible();

  // The debounce that writes the draft down is 400ms; give it room.
  await page.waitForTimeout(1200);

  await page.reload();
  await page.waitForLoadState("networkidle");
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();

  await expect(composer).toHaveValue(
    "What does this say about the salary band?",
  );
  await expect(page.getByText("Job board").last()).toBeVisible();
  await expect(page.getByText("sample.png")).toBeVisible();
});

test("sending clears the draft, so a reload afterwards starts empty", async ({
  page,
}) => {
  test.setTimeout(120_000);
  // No key is configured in the E2E stack, so the send fails at the provider — which is fine
  // and is in fact the harder case: the message has left the composer either way, and what was
  // written down for a reload must go with it rather than coming back as a duplicate.
  await page.route("**/api/ai/keys", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([
        { provider: "ANTHROPIC", hint: "…test", model: "claude-test" },
      ]),
    }),
  );
  await page.route("**/api/ai/chat", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: 'event: token\ndata: "Noted."\n\nevent: done\ndata: \n\n',
    }),
  );

  await createGoal(page, `AI draft sent ${Date.now()}`);
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();

  const composer = page.getByPlaceholder("Ask, plan, or request an action…");
  await composer.fill("A thought I am about to send");
  await page.waitForTimeout(1200);
  await page.getByTitle("Send").click();
  await expect(composer).toHaveValue("");

  await page.reload();
  await page.waitForLoadState("networkidle");
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();

  await expect(composer).toHaveValue("");
});
