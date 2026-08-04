import { test, expect } from "@playwright/test";
import { createGoal, addOptions } from "./helpers";

/**
 * Inline resource attachments end to end
 * (specs/2026-07-28-inline-resource-attachments/requirements.md): attaching a resource from an
 * element's ⋯ menu, the over-limit URL → link resource swap, and the delete-while-attached
 * warning that degrades each link back to plain text.
 */
test("attach, auto-create from a long URL, and detach on delete", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await createGoal(page, `Attachments ${Date.now()}`);

  // A link resource to attach.
  await page.getByRole("button", { name: "Add resource" }).click();
  await page.getByRole("button", { name: "Link", exact: true }).first().click();
  await page
    .getByPlaceholder("https://")
    .fill("https://www.linkedin.com/jobs/view/123456789");
  await page
    .locator('div:has(> label:has-text("Title")) input')
    .first()
    .fill("Job ad");
  await page.getByRole("button", { name: "Add resource" }).last().click();
  await page.waitForLoadState("networkidle");

  // Attach it to a strategy from the card's ⋯ menu — the link shows the title, not a URL.
  await addOptions(page, ["Tailor the CV for this role and apply"]);
  const optionCard = page.locator("li", { hasText: "Tailor the CV" }).first();
  // Element menus stay hidden until their row is hovered or focused.
  await optionCard.hover();
  await optionCard.getByRole("button", { name: "Strategy actions" }).click();
  await page.getByRole("menuitem", { name: "Attach resource" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /Job ad/ })
    .click();
  await page.waitForLoadState("networkidle");
  await expect(
    optionCard.getByRole("button", { name: /Job ad/ }),
  ).toBeVisible();

  // Same menu on a reality item.
  const realityInput = page.getByPlaceholder("Add an action you've taken…");
  await realityInput.fill("Sent the application");
  await realityInput.press("Enter");
  const realityItem = page
    .locator("li", { hasText: "Sent the application" })
    .first();
  await realityItem.hover();
  await realityItem.getByRole("button", { name: "Item actions" }).click();
  await page.getByRole("menuitem", { name: "Attach resource" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /Job ad/ })
    .click();
  await page.waitForLoadState("networkidle");
  await expect(
    realityItem.getByRole("button", { name: /Job ad/ }),
  ).toBeVisible();

  // A URL that pushes the field over its limit is offered as a link resource instead of
  // failing to save (the old "sync failed" banner).
  const longUrl = `https://example.com/apply?ref=${"x".repeat(520)}`;
  const editor = page.getByRole("textbox", { name: "Edit strategy" });
  await editor.click();
  await editor.fill(`Apply here ${longUrl}`);
  await editor.blur();
  await expect(page.getByText(/too long to save here/i)).toBeVisible();
  await page.getByRole("button", { name: "Yes, save as a resource" }).click();
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("li", { hasText: "Apply here" }).first(),
  ).toContainText("example");

  // Deleting an attached resource warns, then turns its links into plain text.
  const jobAdCard = page
    .locator("div.group", { hasText: "Job ad" })
    .filter({ has: page.getByRole("button", { name: "Job ad" }) })
    .first();
  await jobAdCard.locator("button").nth(1).click(); // expand the card's actions
  await jobAdCard.locator('button[title="Remove"]').click();
  await expect(page.getByText(/attached in 1 place/i)).toBeVisible();
  await page.getByRole("button", { name: "Yes, delete" }).click();
  await page.waitForLoadState("networkidle");

  await expect(page.getByRole("button", { name: /^Job ad/ })).toHaveCount(0);
  await expect(realityItem).toContainText("Sent the application Job ad");
});
