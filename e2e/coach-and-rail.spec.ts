import { test, expect } from "@playwright/test";

/**
 * **The rail and the coach take turns, and the coach is parked rather than closed.**
 *
 * They share the left edge of the page, so only one of them is on screen (owner, 2026-10-07:
 * "либо чат, либо меню"). The part that needs a test is the second half of the rule: opening
 * the rail over a live chat must not *close* it — "информация из чата не должна быть потеряна".
 * The panel stays mounted and hidden, so the scroll position, a pending proposal card and a
 * half-typed message all survive.
 *
 * That is exactly the kind of thing a rewrite breaks silently: unmounting the panel and
 * remounting it looks identical the moment you reopen an empty chat, and only shows up as a
 * lost draft. So the draft is what this measures.
 *
 * Desktop only — the rail does not exist below `lg`, so there is nothing for the coach to take
 * turns with on a phone.
 */
const DESKTOP = { width: 1600, height: 900 };
const DRAFT = "half-typed, must survive";

test.use({ viewport: DESKTOP });

test("opening the rail parks the chat and keeps what is in it", async ({
  page,
}) => {
  await page.goto("/");

  const rail = page.getByRole("complementary", { name: "Main" });
  const panel = page.getByRole("complementary", { name: "AI coach" });
  const menu = page.getByRole("button", { name: /navigation/i });

  await expect(rail).toBeVisible();
  await expect(panel).toBeHidden();

  // Opening the coach takes the rail away — the two never share the edge.
  await page.getByRole("button", { name: "ai coach", exact: true }).click();
  await expect(panel).toBeVisible();
  await expect(rail).toBeHidden();

  const composer = page.getByPlaceholder("Ask, plan, or request an action…");
  await composer.fill(DRAFT);

  // Opening the rail parks the coach. Hidden, not gone.
  await menu.click();
  await expect(rail).toBeVisible();
  await expect(panel).toBeHidden();

  // The rail's coach mark says where the conversation went, and leads back to it.
  const back = page.getByRole("button", { name: "Back to the chat" });
  await expect(back).toBeVisible();
  await back.click();

  await expect(panel).toBeVisible();
  await expect(rail).toBeHidden();
  // The whole point: it was parked, not closed.
  await expect(composer).toHaveValue(DRAFT);
});

test("hiding the rail hands the edge back to a parked chat", async ({
  page,
}) => {
  await page.goto("/");

  const rail = page.getByRole("complementary", { name: "Main" });
  const panel = page.getByRole("complementary", { name: "AI coach" });
  const menu = page.getByRole("button", { name: /navigation/i });

  await page.getByRole("button", { name: "ai coach", exact: true }).click();
  await expect(panel).toBeVisible();

  await menu.click();
  await expect(rail).toBeVisible();

  // Putting the rail away again brings the coach back, because the rail is what took its
  // place. A chat the user had closed outright is not parked and must not reappear.
  await menu.click();
  await expect(rail).toBeHidden();
  await expect(panel).toBeVisible();

  await page.getByRole("button", { name: "Close" }).first().click();
  await expect(panel).toBeHidden();
  await menu.click();
  await expect(rail).toBeVisible();
  await menu.click();
  await expect(rail).toBeHidden();
  await expect(panel).toBeHidden();
});

/**
 * The head is one line and says one thing: which provider is answering, or the offer of a key.
 * It used to say both, under a "spira ai coach" wordmark that named the panel a second time.
 */
test("the coach head is one line: the provider, a new chat and a close", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "ai coach", exact: true }).click();

  const panel = page.getByRole("complementary", { name: "AI coach" });
  await expect(panel).toBeVisible();

  const provider = panel.getByRole("button", { name: /^Provider: /i });
  await expect(provider).toBeVisible();
  await expect(panel.getByRole("button", { name: "Close" })).toBeVisible();

  // The wordmark is gone — the rail's mark and the header's opener already name the panel.
  await expect(panel.getByText("ai coach")).toHaveCount(0);

  // **One line**: the provider control and the head's marks share a row, and the row is the
  // band's own height. Measured on the CENTRES, not the tops — the controls are different
  // heights (a 14px label against a 32px button) and `items-center` lines up their middles,
  // which is what "on one line" means. Comparing tops only ever passed by coincidence.
  const head = await panel.evaluate((el) => {
    const band = el.querySelector("button[aria-label^='Provider: ']")!
      .parentElement!.parentElement!;
    const mid = (r: DOMRect) => Math.round(r.top + r.height / 2);
    return {
      height: Math.round(band.getBoundingClientRect().height),
      centres: [...band.children].map((c) => mid(c.getBoundingClientRect())),
    };
  });
  expect(
    new Set(head.centres).size,
    "the head's controls are not on one line",
  ).toBe(1);
  // 54px is the goal page's section-nav band, which this head sits level with.
  expect(head.height, "the head is no longer one band tall").toBe(54);
});
