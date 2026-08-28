import { test, expect, Page, Locator } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * **The chat drawer must keep its shape when the viewport shrinks** (BUG-060).
 *
 * The owner hit a state on a phone where the drawer's head had scrolled out of sight and a
 * proposal card was cut across its Accept / Edit buttons, so the card could not be answered at
 * all. Three screenshots half an hour apart showed the drawer at three different heights.
 *
 * The viewport shrinking is not a contrived case — it is what Chrome for Android does every time
 * its toolbar reappears on scroll, and what the on-screen keyboard does when the composer is
 * focused. `setViewportSize` on an open drawer reproduces exactly that, which is why this spec
 * can catch on a laptop something that only showed up on a phone.
 *
 * The model is stubbed, as in `ai-proposal-card.spec.ts`: a live provider would need a key and
 * would answer differently every run. Everything downstream of the stream — the card, the
 * layout, the scrolling — is real, and the layout is the thing under test.
 */

const PHONE = { width: 412, height: 780 };
/** What is left of a phone once Chrome's toolbar is back and the keyboard is up. */
const PHONE_SQUEEZED = { width: 412, height: 300 };

const noteArgs = (title: string) =>
  JSON.stringify({
    kind: "note",
    title,
    value: "<p>Something worth keeping</p>",
    proposalId: 1,
  });

async function stubAi(page: Page) {
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
  await page.route("**/api/ai/proposals/**", (route) =>
    route.fulfill({ status: 200, body: "" }),
  );
  // The transcript is per-user and persisted, so without this the drawer opens on whatever
  // conversation happens to be in the developer's database — which is how the first run of this
  // spec came up against the owner's own chat, with a card already pending and the composer
  // therefore hidden (a pending card IS the input, by design). Stub it and the test is the same
  // every run, on every machine.
  await page.route("**/api/ai/chat/transcript**", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ goalId: null, content: null, updatedAt: null }),
    }),
  );
  await page.route("**/api/ai/chat", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body:
        `event: token\ndata: ${JSON.stringify("Here is one for you.")}\n\n` +
        `event: proposal\ndata: ${noteArgs("Interview prep")}\n\n` +
        "event: done\ndata: \n\n",
    }),
  );
}

function drawer(page: Page): Locator {
  return page.locator("[data-vaul-drawer]").first();
}

async function openCoachWithACard(page: Page) {
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();
  const composer = page.getByPlaceholder("Ask, plan, or request an action…");
  await expect(composer).toBeVisible();
  await composer.fill("make me a note");
  await page.getByTitle("Send").click();
  await expect(page.getByRole("button", { name: "Accept" })).toBeVisible();
}

test.describe("the AI drawer on a phone", () => {
  // The goal is created at desktop width because `createGoal` drives the dashboard's search
  // field, which on a phone lives behind the magnifier. The size that matters is the one the
  // drawer is opened at, and that is set below.
  test("keeps its head and its Accept button reachable when the viewport shrinks", async ({
    page,
  }) => {
    await stubAi(page);
    await createGoal(page, `Drawer height ${Date.now()}`);
    await page.setViewportSize(PHONE);
    await openCoachWithACard(page);

    // The drawer's own top edge, before anything moves.
    const before = await drawer(page).boundingBox();
    expect(before).not.toBeNull();

    // Chrome's toolbar comes back / the keyboard opens.
    await page.setViewportSize(PHONE_SQUEEZED);
    await page.waitForTimeout(400);

    const after = await drawer(page).boundingBox();
    expect(after).not.toBeNull();

    // 1. The drawer still fits inside the viewport rather than hanging below it. A drawer
    //    taller than the screen is how Accept ends up somewhere the finger cannot go.
    expect(after!.y + after!.height).toBeLessThanOrEqual(
      PHONE_SQUEEZED.height + 1,
    );

    // 2. The head is still there. Its leaving the top of the drawer is the tell that the whole
    //    card is scrolling as one block instead of the transcript scrolling inside it.
    const head = page.getByRole("button", { name: /Bring your own key/i });
    await expect(head).toBeInViewport();

    // 3. And the card can still be answered, which is the whole point of the complaint.
    await expect(page.getByRole("button", { name: "Accept" })).toBeInViewport();
  });

  test("does not grow past the viewport when the keyboard closes again", async ({
    page,
  }) => {
    await stubAi(page);
    await createGoal(page, `Drawer height ${Date.now()}`);
    await page.setViewportSize(PHONE);
    await openCoachWithACard(page);

    await page.setViewportSize(PHONE_SQUEEZED);
    await page.waitForTimeout(300);
    await page.setViewportSize(PHONE);
    await page.waitForTimeout(300);

    const box = await drawer(page).boundingBox();
    expect(box).not.toBeNull();
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.y + box!.height).toBeLessThanOrEqual(PHONE.height + 1);
    await expect(page.getByRole("button", { name: "Accept" })).toBeInViewport();
  });
});
