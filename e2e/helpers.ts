import { Page, expect } from "@playwright/test";

/** Create a goal via the UI and land on its workspace page. Returns nothing. */
export async function createGoal(page: Page, title: string) {
  await page.goto("/");
  // Let the initial loadGoals settle first; creating mid-load can drop the optimistic
  // goal when the server list replaces state.
  await page.waitForLoadState("networkidle");

  const emptyStateButton = page.getByRole("button", {
    name: "Create your first goal",
  });
  if (await emptyStateButton.isVisible().catch(() => false)) {
    await emptyStateButton.click();
  } else {
    await page.getByRole("button", { name: "New goal" }).first().click();
  }

  await page
    .getByPlaceholder("e.g. Launch Spira to first 50 users")
    .fill(title);
  await page.getByRole("button", { name: "Create goal", exact: true }).click();

  // Wait for the create mutation to reconcile the optimistic goal to its real server id;
  // navigating while the card still holds a "local-" id 404s the goal route.
  await page.waitForLoadState("networkidle");

  // Creating closes the sheet and adds a card on the dashboard (no auto-nav). The
  // dashboard filters by the search query, so search for the (unique) title to isolate
  // the new card — this avoids off-screen/virtualised cards when many goals exist.
  const search = page.getByPlaceholder("Search goals").first();
  await search.fill(title);
  const card = page.getByRole("link", { name: title }).first();
  // The dashboard's background refresh can briefly drop the optimistic card; retry the
  // search a couple of times until the (real-id) card link is stably present.
  await expect(async () => {
    if (!(await card.isVisible().catch(() => false))) {
      await search.fill("");
      await search.fill(title);
    }
    await expect(card).toBeVisible();
    await expect(card).toHaveAttribute("href", /\/goals\/(?!local-)/);
  }).toPass({ timeout: 20_000 });
  await card.click();

  await page.waitForURL(/\/goals\/(?!local-)/);
  // Clear the search so its results dropdown doesn't linger over the goal page.
  await page.getByPlaceholder("Search goals").first().fill("");
  // The Options section heading (exact) confirms we're on the goal workspace.
  await expect(
    page.getByRole("heading", { name: "Options", exact: true }),
  ).toBeVisible();
}

/**
 * All option cards in the Options section. Identified by the per-card rating
 * button, which is present on every card regardless of selection or reorder
 * mode (reordering is now a whole-card drag toggled from the section header, so
 * there is no per-card drag handle to key off).
 */
export function optionCards(page: Page) {
  return page.locator("li", {
    has: page.getByRole("button", { name: "Rate strategy" }),
  });
}

/** Add strategies to the Options section via the inline "Add a strategy" input. */
export async function addOptions(page: Page, texts: string[]) {
  const input = page.getByPlaceholder("Add a strategy…");
  for (const text of texts) {
    await input.fill(text);
    await input.press("Enter");
    await expect(page.locator("li", { hasText: text }).first()).toBeVisible();
  }
}
