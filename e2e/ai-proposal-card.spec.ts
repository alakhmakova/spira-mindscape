import { test, expect, Page, Locator } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * **Answering a proposal card: Accept, Dismiss, Edit** (owner, 2026-08-24).
 *
 * Three complaints from the phone, none of which had a test on either surface:
 *
 * 1. Accept has to leave a **link to what was created**, and on the web that link was
 *    `text-white` on a chat area that fades to `#F2FFFF` — invisible exactly where a
 *    just-answered card lands. The colour is measured here, not eyeballed: a screenshot at
 *    half size has lied about this twice already.
 * 2. A **dismissed** card must stay dismissed — through more conversation, and through asking
 *    for another one of the same kind.
 * 3. **Edit** must leave **exactly one card**. (This is where Android was broken: the revision
 *    came back as a second card. The web has always revised in place; these assertions are what
 *    keep it that way, and they are the twin of `ProposalCardLifecycleTest` on Android.)
 *
 * The model is stubbed — a live provider would need the owner's key and would answer differently
 * every run — but everything downstream of the stream is real: the card, the approve/reject
 * writes, and the resource the accepted note actually creates (which is what puts the `Open`
 * link on the result line at all).
 */

/** One scripted assistant turn: some prose, and the proposals it comes with. */
type Turn = { text?: string; proposals?: string[] };

const noteArgs = (title: string, body: string, proposalId?: number) =>
  JSON.stringify({
    kind: "note",
    title,
    value: `<p>${body}</p>`,
    ...(proposalId != null ? { proposalId } : {}),
  });

function sseBody(turn: Turn): string {
  const parts: string[] = [];
  if (turn.text)
    parts.push(`event: token\ndata: ${JSON.stringify(turn.text)}\n\n`);
  for (const p of turn.proposals ?? [])
    parts.push(`event: proposal\ndata: ${p}\n\n`);
  parts.push("event: done\ndata: \n\n");
  return parts.join("");
}

/**
 * Answer the AI endpoints from a script. `/api/ai/chat` serves one turn per call, so a test
 * reads as the conversation it is: turn 1, then turn 2, then turn 3.
 */
async function stubAi(page: Page, script: Turn[]) {
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
  // The proposal rows are the model's, so they don't exist server-side for a stubbed turn.
  await page.route("**/api/ai/proposals/**", (route) =>
    route.fulfill({ status: 200, body: "" }),
  );

  let call = 0;
  await page.route("**/api/ai/chat", (route) => {
    const turn = script[call++] ?? {};
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: sseBody(turn),
    });
  });
}

function panelOf(page: Page): Locator {
  return page.getByRole("complementary", { name: "spira ai coach" });
}

async function openCoach(page: Page) {
  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();
  await expect(panelOf(page)).toBeVisible();
}

async function ask(page: Page, text: string) {
  const panel = panelOf(page);
  await panel.getByPlaceholder("Ask, plan, or request an action…").fill(text);
  await panel.getByTitle("Send").click();
}

/** Cards on screen. Every answerable card carries exactly one Accept. */
function cards(page: Page): Locator {
  return panelOf(page).getByRole("button", { name: "Accept" });
}

// ── Contrast, measured ──────────────────────────────────────────────────────
// The chat area is a gradient from teal down to #F2FFFF. Light text passes at the top and
// disappears at the bottom, so the honest test is against the LIGHTEST stop.
const LIGHTEST_STOP = "#F2FFFF";

function srgbToLinear(c: number) {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

function luminance(rgb: [number, number, number]) {
  const [r, g, b] = rgb.map(srgbToLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Accepts "rgb(a, b, c)", "rgba(a, b, c, x)" or "#rrggbb". Alpha is composited on white. */
function parseColor(value: string): [number, number, number] {
  if (value.startsWith("#")) {
    const n = parseInt(value.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  const nums = value.match(/[\d.]+/g)?.map(Number) ?? [];
  const [r, g, b, a = 1] = nums;
  // Compositing on white is the kindest reading of a translucent ink — if it still fails
  // there, it fails everywhere.
  return [
    Math.round(r * a + 255 * (1 - a)),
    Math.round(g * a + 255 * (1 - a)),
    Math.round(b * a + 255 * (1 - a)),
  ];
}

function contrastRatio(a: string, b: string) {
  const la = luminance(parseColor(a));
  const lb = luminance(parseColor(b));
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

// ── Accept ──────────────────────────────────────────────────────────────────

test("Accept leaves one result line, and its link is readable on the light end of the chat", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await stubAi(page, [
    { proposals: [noteArgs("Interview prep", "Questions to prepare", 11)] },
  ]);
  await createGoal(page, `AI card accept ${Date.now()}`);
  await openCoach(page);

  await ask(page, "Create a note titled Interview prep");
  const panel = panelOf(page);
  await expect(cards(page)).toHaveCount(1);

  await cards(page).click();

  // The card is gone; the compact result line takes its place.
  await expect(cards(page)).toHaveCount(0);
  const open = panel.getByRole("button", { name: "Open" });
  await expect(open).toBeVisible();

  // The result line names what was created.
  await expect(
    panel.getByText("Interview prep", { exact: true }),
  ).toBeVisible();

  // Both the line and its link have to be legible where they land. Measured, not eyeballed:
  // the old `text-white/80` scores 1.02:1 here. The pill itself is what carries the ink, so
  // it is read from the element, not from the text node that inherits it.
  const row = open.locator("xpath=..");
  for (const [what, locator] of [
    ["the result line", row],
    ["its Open link", open],
  ] as const) {
    const colour = await locator.evaluate((el) => getComputedStyle(el).color);
    const ratio = contrastRatio(colour, LIGHTEST_STOP);
    expect(
      ratio,
      `${what} is ${colour}, which is ${ratio.toFixed(2)}:1 against the chat's lightest stop ${LIGHTEST_STOP} — under 4.5:1 it disappears at the bottom of the conversation`,
    ).toBeGreaterThanOrEqual(4.5);
  }
});

// ── Dismiss ─────────────────────────────────────────────────────────────────

test("a dismissed card stays dismissed — talking on, and asking again, never revives it", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await stubAi(page, [
    { proposals: [noteArgs("Salary research", "Ranges", 21)] },
    { text: "Here are three things you could do next." },
    { proposals: [noteArgs("Company research", "Who they are", 22)] },
  ]);
  await createGoal(page, `AI card dismiss ${Date.now()}`);
  await openCoach(page);
  const panel = panelOf(page);

  await ask(page, "Create a note titled Salary research");
  await expect(cards(page)).toHaveCount(1);
  await panel.getByRole("button", { name: "Dismiss" }).click();

  await expect(cards(page)).toHaveCount(0);
  await expect(panel.getByText("Dismissed")).toBeVisible();

  // Carrying on talking must not bring it back.
  await ask(page, "Thanks. What else could help me here?");
  await expect(
    panel.getByText("Here are three things you could do next."),
  ).toBeVisible();
  await expect(cards(page)).toHaveCount(0);

  // Nor must asking for another note of the same kind.
  await ask(page, "Create a note titled Company research");
  await expect(cards(page)).toHaveCount(1);
  await expect(
    panel.getByText("Company research", { exact: true }),
  ).toBeVisible();
  // Not even as a settled result line: a dismissal keeps no headline.
  await expect(panel.getByText("Salary research", { exact: true })).toHaveCount(
    0,
  );
});

// ── Edit ────────────────────────────────────────────────────────────────────

test("Edit replaces the card in place — never a second one", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await stubAi(page, [
    { proposals: [noteArgs("Interview prep", "Questions", 31)] },
    { proposals: [noteArgs("SQL interview prep", "Joins, indexes", 32)] },
  ]);
  await createGoal(page, `AI card edit ${Date.now()}`);
  await openCoach(page);
  const panel = panelOf(page);

  await ask(page, "Create a note titled Interview prep");
  await expect(cards(page)).toHaveCount(1);

  await panel.getByRole("button", { name: "Edit" }).click();
  await panel
    .getByPlaceholder("e.g. “in English”, “make it shorter”, “due next Friday”")
    .fill("make it about SQL");
  await panel.getByRole("button", { name: "Send to AI" }).click();

  // One card, and it is the revised one — the original neither survives beside it nor
  // reappears as a settled "Dismissed" line.
  await expect(cards(page)).toHaveCount(1);
  await expect(
    panel.getByText("SQL interview prep", { exact: true }),
  ).toBeVisible();
  await expect(panel.getByText("Dismissed")).toHaveCount(0);

  // The transcript carries the user's own words, captioned with the card being changed —
  // not the prompt built around them.
  await expect(
    panel.getByText("make it about SQL", { exact: true }),
  ).toBeVisible();
  await expect(panel.getByText("Current proposal:")).toHaveCount(0);
});
