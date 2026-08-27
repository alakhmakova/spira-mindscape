import { Page, Locator, expect } from "@playwright/test";

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
    has: page.getByRole("button", { name: "Rate option" }),
  });
}

/**
 * Answer the AI endpoints so a spec never depends on a **real provider key**.
 *
 * Several attachment specs used to send a chat message and quietly rely on a key happening to be
 * saved in the local dev database. Resetting that database (owner, 2026-08-25) took the keys with
 * it, and the specs failed in a way that pointed nowhere near the cause: the send returned
 * `NO_KEY`, the panel opened its **provider sheet**, and every later click was reported as
 * "intercepts pointer events" by an overlay the spec had never heard of.
 *
 * A key is not what any of them are testing. Stubbing it makes them say what they mean and
 * survive a wipe.
 *
 * `reply` is the assistant's answer, streamed as one token. Pass `proposals` (raw
 * `propose_goal_change` argument JSON) to have the turn produce cards.
 */
export async function stubAiProvider(
  page: Page,
  turns: { reply?: string; proposals?: string[] }[] = [{ reply: "Noted." }],
) {
  await page.route("**/api/ai/keys", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([
        { provider: "ANTHROPIC", hint: "…test", model: "claude-test" },
      ]),
    }),
  );
  await page.route("**/api/ai/preferences", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ provider: "ANTHROPIC" }),
    }),
  );
  // Proposal rows belong to a real turn; a stubbed one has none.
  await page.route("**/api/ai/proposals/**", (route) =>
    route.fulfill({ status: 200, body: "" }),
  );

  let call = 0;
  await page.route("**/api/ai/chat", (route) => {
    const turn = turns[Math.min(call++, turns.length - 1)] ?? {};
    const parts: string[] = [];
    if (turn.reply)
      parts.push(`event: token\ndata: ${JSON.stringify(turn.reply)}\n\n`);
    for (const p of turn.proposals ?? [])
      parts.push(`event: proposal\ndata: ${p}\n\n`);
    parts.push("event: done\ndata: \n\n");
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: parts.join(""),
    });
  });
}

/**
 * Wait until the row carrying [text] has stopped being replaced.
 *
 * **Why this exists.** A new option is rendered optimistically under a temporary id and re-keyed
 * to the server's when the mutation lands. React keys the row by that id, so the row is **torn
 * down and rebuilt** — measured on this project at **~200 ms** after it first appears (row added
 * at 495 ms, removed at 689 ms, re-added at 706 ms; then stable for the twelve seconds that
 * followed). Anything holding on to the row across that boundary is holding a component about to
 * be thrown away, and an open ⋯ menu goes with it: the click lands on a detached node and
 * Playwright reports "element was detached from the DOM, retrying" until the test times out.
 *
 * `waitForLoadState("networkidle")` does **not** cover it — called right after the keypress it
 * can return before the mutation has even been sent, which is why the first attempt at this fix
 * still failed one run in four.
 *
 * So this watches the node itself: the same DOM element for two consecutive samples, 400 ms
 * apart, means the swap is behind us. It is the same guard `createGoal` applies to goals, where
 * the id is visible in the card's `href` and can simply be read.
 */
async function waitForRowToSettle(page: Page, text: string) {
  const row = page.locator("li", { hasText: text }).first();
  await expect(row).toBeVisible();
  await expect(async () => {
    const before = await row.elementHandle();
    await page.waitForTimeout(400);
    const after = await row.elementHandle();
    const same =
      before && after
        ? await page.evaluate(([a, b]) => a === b, [before, after])
        : false;
    expect(same, "the row is still being replaced").toBe(true);
  }).toPass({ timeout: 15_000 });
}

/**
 * Add options to the Options section via the inline "Add an option" input, and **wait for each
 * create to reconcile** — see [waitForRowToSettle] for what would otherwise go wrong.
 */
export async function addOptions(page: Page, texts: string[]) {
  const input = page.getByPlaceholder("Add an option…");
  for (const text of texts) {
    await input.fill(text);
    await input.press("Enter");
    await waitForRowToSettle(page, text);
  }
}

/**
 * Open an element's ⋯ menu (an option, a reality item, a checklist task) and wait for its items.
 *
 * Two product behaviours make a bare `hover().click()` unreliable here, and **neither is a bug to
 * design around** — the spec has to tolerate them:
 *
 * - the menu is `pointer-events-none` until its row is hovered, so the hover has to be live at the
 *   moment of the press;
 * - a row that re-renders — an optimistic id settling, the 45s goals poll landing — takes its
 *   Radix menu instance with it, so a press can open a menu that is unmounted a frame later.
 *
 * Retrying the *open* is therefore the honest fix. Measured on this project the menu came back
 * with both items well inside 100 ms when the press survived, and not at all when it did not, so
 * a second attempt costs nothing and turns a coin-flip into a pass.
 */
export async function openElementMenu(
  page: Page,
  row: Locator,
  ariaLabel: string,
) {
  await expect(async () => {
    await row.hover();
    await row.getByRole("button", { name: ariaLabel }).click();
    await expect(page.getByRole("menuitem").first()).toBeVisible({
      timeout: 2000,
    });
  }).toPass({ timeout: 20_000 });
}

/**
 * Open an element's ⋯ menu and choose one of its items.
 *
 * **The whole pair is retried, not just the open.** A row can also be replaced *after* the menu
 * is showing, and then the item is there but its node is detached — Playwright clicks into
 * nothing and reports "element is not stable" followed by "element was detached from the DOM".
 * Re-opening and choosing again is the only thing that recovers from that; asserting harder does
 * not.
 */
export async function chooseElementMenuItem(
  page: Page,
  row: Locator,
  ariaLabel: string,
  itemName: string,
) {
  await expect(async () => {
    await openElementMenu(page, row, ariaLabel);
    await page
      .getByRole("menuitem", { name: itemName })
      .click({ timeout: 3000 });
  }).toPass({ timeout: 30_000 });
}
