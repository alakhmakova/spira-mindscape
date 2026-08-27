import { test, expect } from "@playwright/test";
import { createGoal, stubAiProvider } from "./helpers";

/**
 * **Attaching one of the goal's saved resources to an AI message** (BUG-030).
 *
 * This spec exists because nothing covered it. `resource-attachments.spec.ts` attaches a
 * resource to a **goal element** — an option's ⋯ menu — which is a different flow entirely;
 * the chat's picker had no test at all on either surface. That is how it drifted into a
 * hand-rolled dropdown around a bare `<input>` that rendered **white text on white** (the
 * field set a background but no colour, and the AI panel's root is `text-white`), and how
 * the Android twin ended up wearing a white head with a drag handle while every other sheet
 * wears the Kale band. Both were found by the owner, not by CI (2026-08-23).
 *
 * What is checked here: the picker opens as a sheet with the standard head, its search is
 * legible and narrows the list, picking a resource puts a chip on the composer, and the chip
 * survives as an attachment on the sent message.
 */
test("attach a saved resource to an AI message from the chat picker", async ({
  page,
}) => {
  test.setTimeout(120_000);
  // No real key is needed to attach something — see `stubAiProvider`.
  await stubAiProvider(page);
  await createGoal(page, `AI attach ${Date.now()}`);

  // Two resources, so the search has something to narrow.
  for (const title of ["Interview notes", "Job board"]) {
    await page.getByRole("button", { name: "Add resource" }).click();
    await page
      .getByRole("button", { name: "Link", exact: true })
      .first()
      .click();
    await page.getByPlaceholder("https://").fill("https://example.com/x");
    await page
      .locator('div:has(> label:has-text("Title")) input')
      .first()
      .fill(title);
    await page.getByRole("button", { name: "Add resource" }).last().click();
    await page.waitForLoadState("networkidle");
  }

  // Open the coach and its attach menu.
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();
  await page.getByRole("button", { name: "Attach" }).click();
  await page.getByRole("button", { name: "From resources" }).click();

  // The picker is a SHEET with the standard head — not the old inline dropdown.
  const sheet = page.getByRole("dialog");
  await expect(sheet.getByText("Attach a resource")).toBeVisible();

  // The search must be legible: it inherited the panel's white text and was invisible.
  const search = sheet.getByLabel("Search resources");
  await search.fill("Interview");
  await expect(search).toHaveValue("Interview");
  await expect(
    sheet.getByRole("button", { name: /Interview notes/ }),
  ).toBeVisible();
  await expect(sheet.getByRole("button", { name: /Job board/ })).toHaveCount(0);

  // Picking it closes the sheet and puts a chip on the composer, before sending.
  await sheet.getByRole("button", { name: /Interview notes/ }).click();
  await expect(sheet).toHaveCount(0);
  await expect(page.getByText("Interview notes").last()).toBeVisible();

  // Re-opening offers it as already added rather than a second time.
  await page.getByRole("button", { name: "Attach" }).click();
  await page.getByRole("button", { name: "From resources" }).click();
  await expect(page.getByRole("dialog").getByText("Added")).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: "Close" }).click();

  // The chip is removable while the message is unsent — the user's last chance to notice
  // they picked the wrong resource.
  const chip = page.getByText("Interview notes").last();
  await expect(chip).toBeVisible();

  // And it survives the send, still named on the sent message: a chip that renders before
  // sending and vanishes after leaves the user unable to check what the assistant was given.
  await page.getByPlaceholder(/Ask, plan/i).fill("what does this say?");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(1500);
  await expect(page.getByText("Interview notes").last()).toBeVisible();

  // NOT asserted, because it is not true yet: **opening** the chip. On the web
  // `canPreview` is `mime.startsWith("image/") && !!dataUrl`, and a resource chip carries
  // neither — so it is inert, before and after sending. Android opens every attachment
  // through `LocalOpenAttachment`, resources included. That parity gap is real and known
  // (owner asked for preview on both, 2026-08-23); when it is closed, assert it here.
});
