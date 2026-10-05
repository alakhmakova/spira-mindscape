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
/** An ordinary phone with room to spare — where empty space under a card is visible. */
const PHONE_TALL = { width: 412, height: 900 };

const noteArgs = (title: string) =>
  JSON.stringify({
    kind: "note",
    title,
    value: "<p>Something worth keeping</p>",
    proposalId: 1,
  });

async function stubAi(page: Page, opts: { hangOnRevise?: boolean } = {}) {
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
  let turn = 0;
  await page.route("**/api/ai/chat", async (route) => {
    turn += 1;
    // The second turn is the revise. Holding it open is what keeps the panel in the
    // "Revising" state long enough to measure it — and then it is ABORTED rather than left
    // hanging: an unanswered route outlives the test body, so a 30 s sleep here would stall
    // teardown and, with two retries, turn a spec whose assertions finish in seconds into a
    // slow flake. The product's own revise safety net is 90 s, so the state holds either way.
    if (turn > 1 && opts.hangOnRevise) {
      await new Promise((r) => setTimeout(r, 2_000));
      await route.abort();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body:
        `event: token\ndata: ${JSON.stringify("Here is one for you.")}\n\n` +
        `event: proposal\ndata: ${noteArgs("Interview prep")}\n\n` +
        "event: done\ndata: \n\n",
    });
  });
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

  test("leaves no dead space under a short proposal card", async ({ page }) => {
    // The gap this spec did not have, and the owner found it on the merged build: a band of
    // empty gradient under the card, because the card's block had `flex-1` and so claimed a
    // share of the panel whether or not it needed one. Nothing here asserted what sits BELOW
    // the card, only that Accept was reachable — so the regression sailed through.
    await stubAi(page);
    await createGoal(page, `Drawer height ${Date.now()}`);
    await page.setViewportSize(PHONE_TALL);
    await openCoachWithACard(page);

    const card = page.getByRole("button", { name: "Accept" });
    const cardBox = await card.boundingBox();
    const drawerBox = await drawer(page).boundingBox();
    expect(cardBox).not.toBeNull();
    expect(drawerBox).not.toBeNull();

    // Whatever sits under the card — the composer is hidden while one is pending — must not be
    // a void. A hand-width of slack covers the card's own padding and the rounded bottom.
    const gap =
      drawerBox!.y + drawerBox!.height - (cardBox!.y + cardBox!.height);
    expect(gap).toBeLessThan(120);
  });

  test("leaves no dead space under the Revising row either", async ({
    page,
  }) => {
    // A second path with its own bottom row, and the one the owner photographed: while a card is
    // being revised the composer is hidden and this row takes its place. It is a different
    // element from the card block, so the card's fix says nothing about it — which is why it
    // gets its own assertion rather than an assumption.
    await stubAi(page, { hangOnRevise: true });
    await createGoal(page, `Drawer revising ${Date.now()}`);
    await page.setViewportSize(PHONE_TALL);
    await openCoachWithACard(page);

    await page.getByRole("button", { name: "Edit" }).click();
    await page
      .getByPlaceholder(
        "e.g. “in English”, “make it shorter”, “due next Friday”",
      )
      .fill("fix it");
    await page.getByRole("button", { name: "Send to AI" }).click();
    await expect(page.getByText(/Revising/)).toBeVisible();

    const row = page.getByText(/Revising/).locator("xpath=../..");
    const rowBox = await row.boundingBox();
    const drawerBox = await drawer(page).boundingBox();
    expect(rowBox).not.toBeNull();
    expect(drawerBox).not.toBeNull();

    const gap = drawerBox!.y + drawerBox!.height - (rowBox!.y + rowBox!.height);
    expect(gap).toBeLessThan(40);
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

    // …and it comes BACK to full height. This is the owner's actual complaint — a drawer
    // "under half the screen" — and the assertion the old spec was missing: it only checked
    // that the drawer had not overgrown, so a drawer left permanently collapsed passed.
    expect(box!.height).toBeGreaterThan(PHONE.height * 0.85);
  });

  test("comes back to full height after the keyboard closes, with the field still focused", async ({
    page,
  }) => {
    // **The regression the four earlier rounds could not see** (2026-08-29). Every spec above
    // either shrank the viewport with nothing focused, or shrank it and never grew it back —
    // and both of those pass on the broken code, because the defect lives in a handler vaul
    // only runs while something typeable is focused.
    //
    // vaul's `repositionInputs` (on by default) listens for `visualViewport` resizes and writes
    // an INLINE `height` onto the drawer, taken from `initialDrawerHeight` — the height it
    // happened to measure on the first such resize. An inline style beats `sheet-h-92`, so from
    // that moment the sheet is not sized by our CSS at all. Measured here before the fix:
    // 718 px at rest → 300 px with the keyboard up → **still 300 px on a 780 px screen** once
    // the keyboard went away. That is the owner's "меньше половины экрана", and no CSS change
    // could ever have reached it.
    //
    // The order below is the whole test: focus FIRST (a keyboard cannot be up otherwise), then
    // shrink, then grow back **without blurring** — closing the keyboard with the back gesture
    // and leaving the caret in the field is the ordinary way out on Android.
    await stubAi(page);
    await createGoal(page, `Drawer regrow ${Date.now()}`);
    await page.setViewportSize(PHONE);
    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .click();
    const composer = page.getByPlaceholder("Ask, plan, or request an action…");
    await expect(composer).toBeVisible();
    await composer.click();

    await page.setViewportSize(PHONE_SQUEEZED);
    await page.waitForTimeout(300);
    await page.setViewportSize(PHONE);
    await page.waitForTimeout(400);

    const box = await drawer(page).boundingBox();
    expect(box).not.toBeNull();
    expect(box!.height).toBeGreaterThan(PHONE.height * 0.85);

    // And the reason it came back: nothing wrote a pixel height onto the element. Asserting the
    // absence is what pins the cause rather than the symptom — a future vaul upgrade that turns
    // the handler back on fails here with a message that says what happened.
    const inline = await drawer(page).evaluate(
      (el) => (el as HTMLElement).style.height,
    );
    expect(
      inline,
      "vaul wrote an inline height onto the drawer — `repositionInputs` is back on (see drawer.tsx)",
    ).toBe("");
  });

  test("the composer floats over the transcript, and the keyboard scrolls it to the bottom", async ({
    page,
  }) => {
    // The other half of the owner's 2026-08-29 change, and the reason the sheet can afford to
    // stop growing to the top edge: the room comes from scrolling now.
    //
    //   "вместо этого должна проходить автоматическая прокрутка контента в самый низ, когда
    //    открывается клавиатура и при открытой клавиатуре можно полностью прокручивать контент
    //    как бы за поле с сообщением"
    //
    // Two things are asserted, because they are two separate mechanisms: the footer is an
    // absolutely-positioned layer over a full-height transcript (`AiPanel.tsx`), and a viewport
    // that shrinks while a field is focused scrolls that transcript to the end
    // (`useKeyboardStickyBottom`).
    await stubAi(page);
    // Plain prose rather than the shared stub's proposal: a pending card IS the input, so it
    // would hide the composer this test is about. A later route wins in Playwright.
    await page.route("**/api/ai/chat", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body:
          `event: token\ndata: ${JSON.stringify(
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(
              8,
            ),
          )}\n\n` + "event: done\ndata: \n\n",
      }),
    );
    await createGoal(page, `Drawer float ${Date.now()}`);
    await page.setViewportSize(PHONE);
    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .click();

    const composer = page.getByPlaceholder("Ask, plan, or request an action…");
    await expect(composer).toBeVisible();
    for (let i = 0; i < 3; i += 1) {
      await composer.fill(`message ${i}`);
      await page.getByTitle("Send").click();
      await page.waitForTimeout(250);
    }

    const transcript = drawer(page).locator("[data-vaul-no-drag]").first();
    const footer = drawer(page).locator(".pointer-events-auto").first();

    // 1. The footer floats: the transcript runs to the same bottom edge rather than stopping
    //    above it, and is padded by exactly the footer it disappears behind.
    const geo = await page.evaluate(() => {
      const t = document.querySelector(
        "[data-vaul-drawer] [data-vaul-no-drag]",
      ) as HTMLElement;
      const f = document.querySelector(
        "[data-vaul-drawer] .pointer-events-auto",
      ) as HTMLElement;
      return {
        transcriptBottom: Math.round(t.getBoundingClientRect().bottom),
        footerBottom: Math.round(f.getBoundingClientRect().bottom),
        footerHeight: Math.round(f.getBoundingClientRect().height),
        padBottom: Math.round(parseFloat(getComputedStyle(t).paddingBottom)),
      };
    });
    expect(geo.transcriptBottom).toBe(geo.footerBottom);
    expect(geo.padBottom).toBeGreaterThanOrEqual(geo.footerHeight);
    expect(geo.padBottom).toBeLessThan(geo.footerHeight + 24);

    // 2. Scrolled up, focused, then squeezed — the keyboard brings the end of the conversation
    //    back to the composer.
    await transcript.evaluate((el) => {
      (el as HTMLElement).scrollTop = 0;
    });
    await composer.click();
    await page.setViewportSize(PHONE_SQUEEZED);
    await page.waitForTimeout(700);

    const atBottom = await transcript.evaluate((el) => {
      const e = el as HTMLElement;
      return e.scrollHeight - e.clientHeight - e.scrollTop;
    });
    expect(
      atBottom,
      "the keyboard opening did not scroll the transcript to the end — see " +
        "`useKeyboardStickyBottom` in AiPanel.tsx",
    ).toBeLessThan(4);
  });

  test("keeps its top edge exactly where it was when the keyboard opens", async ({
    page,
  }) => {
    // **The rule, and the third attempt at it.** Every earlier version sized a sheet as a
    // percentage of a viewport that the keyboard resizes, so the top edge could not hold still:
    //
    //   `92vh`                          → 92 % of what the keyboard left: a third of the screen
    //   `min(92 * --app-vh, 100dvh)`    → with a keyboard the cap won at 100 %: top edge at 0
    //   `min(92 * --app-vh, 92dvh)`     → 8 % of 888 is 71, 8 % of 430 is 34: a 37 px jump up,
    //                                     onto the app header (owner: "подскакивает немного и
    //                                     начинает перекрывать хедер")
    //
    // `calc(100dvh - var(--sheet-top-gap))` cannot: the bottom is the viewport's bottom, the top
    // is a constant below its top, and the keyboard only ever shrinks it from below.
    await stubAi(page);
    await createGoal(page, `Drawer keyboard ${Date.now()}`);
    await page.setViewportSize(PHONE);
    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .click();

    // Focus FIRST, then shrink: a keyboard cannot be up without a focused editable element.
    const composer = page.getByPlaceholder("Ask, plan, or request an action…");
    await expect(composer).toBeVisible();
    await composer.click();
    const before = (await drawer(page).boundingBox())!;

    await page.setViewportSize(PHONE_SQUEEZED);
    await page.waitForTimeout(300);
    const withKeyboard = (await drawer(page).boundingBox())!;

    await page.setViewportSize(PHONE);
    await page.waitForTimeout(300);
    const after = (await drawer(page).boundingBox())!;

    // The top edge does not move. Not "moves a little" — at all.
    expect(withKeyboard.y).toBeCloseTo(before.y, 0);
    expect(after.y).toBeCloseTo(before.y, 0);

    // And it is clear of the 64px app header, with the page showing above it.
    expect(before.y).toBeGreaterThanOrEqual(64);
    // Still a drawer, not a stub: it fills everything below that edge.
    expect(before.y + before.height).toBeCloseTo(PHONE.height, 0);
    expect(withKeyboard.y + withKeyboard.height).toBeCloseTo(
      PHONE_SQUEEZED.height,
      0,
    );
  });
});
