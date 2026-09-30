import { test, expect, type Page } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * The vacancy map end to end (specs/2026-09-17-vacancy-map/).
 *
 * The unit tests already pin what the panel renders and which patch each control sends. What only
 * a running stack can show is the other half: that those patches **reach the server and come back**
 * — the map is left out of every list read and fetched on open, so a write that never lands looks
 * exactly like a write that did until the page is reloaded.
 */

// The picture on a requirement is a two-state control now, not a three-state one: the FIRST
// requirement is permanently the important one and its picture is a picture (owner, 2026-09-23),
// so every other row walks plain <-> cannot-meet.
const PLAIN = "An ordinary requirement. Tap to mark it as one you cannot meet";
const UNMET = "You cannot meet this. Tap to clear the mark";

/** Create the goal, add a vacancy map to it, and leave the panel open. */
async function openMap(page: Page, title: string) {
  await createGoal(page, `Vacancy ${Date.now()}`);

  await page.getByRole("button", { name: "Add resource" }).click();
  await page
    .getByRole("button", { name: "Vacancy map", exact: true })
    .first()
    .click();
  await page
    .locator('div:has(> label:has-text("Title")) input')
    .first()
    .fill(title);
  await page.getByRole("button", { name: "Add resource" }).last().click();
  await page.waitForLoadState("networkidle");

  await page
    .getByRole("button", { name: new RegExp(title) })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Requirements map" }),
  ).toBeVisible();
}

/** Re-open the map after a full reload — the document is fetched again, not remembered. */
async function reopen(page: Page, title: string) {
  await page.reload();
  await page.waitForLoadState("networkidle");
  await page
    .getByRole("button", { name: new RegExp(title) })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Requirements map" }),
  ).toBeVisible();
}

test("a map's marks and its lists survive a reload", async ({ page }) => {
  test.setTimeout(150_000);
  const title = `Backend Developer ${Date.now()}`;
  await openMap(page, title);

  // ── The head is the shared one, and it has exactly one way out ────────────
  // One way out, and it is the X at the end of the action group. There used to be a second:
  // Radix's own corner cross, invisible under the teal band and named the same, so a click
  // meant for the head landed on it instead.
  await expect(
    page.getByRole("button", { name: "Close", exact: true }),
  ).toHaveCount(1);
  // The chevron belongs to the full-screen state, and this panel is not it. `exact`, because
  // this goal's own resource is called "Backend Developer …" and a loose name matches it.
  expect(
    await page.getByRole("button", { name: "Back", exact: true }).count(),
  ).toBe(0);
  await expect(page.getByRole("button", { name: "Duplicate" })).toBeVisible();
  // The name the specs used to press before the heads were merged (CLAUDE.md -> 3i).
  expect(
    await page.getByRole("button", { name: "Close preview" }).count(),
  ).toBe(0);

  // ── A round + under the list adds one, and the text is written in place ───
  await page.getByRole("button", { name: "Add a skill" }).click();
  // An inline field is a display span until it is tapped, so it is typed into, never filled.
  await page.getByRole("textbox", { name: "Item" }).first().click();
  await page.keyboard.type("Java 17");
  // Inline fields commit on blur, never on a keystroke.
  await page.getByRole("heading", { name: "Skills" }).click();
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Add a requirement" }).click();
  // The list always offers three rows, so the new one is the fourth and there are several
  // plain marks on screen.
  await expect(page.getByRole("button", { name: PLAIN }).first()).toBeVisible();

  // ── The picture is the mark, and one tap walks its two states ─────────────
  // The important one is the first row and says so in words; it is not something a tap reaches.
  await expect(page.getByText("Very important!")).toBeVisible();
  await page.getByRole("button", { name: PLAIN }).first().click();
  await expect(page.getByRole("button", { name: UNMET })).toBeVisible();
  await page.waitForLoadState("networkidle");

  // ── Everything above came back from the server, not from the open tab ─────
  await reopen(page, title);
  await expect(page.getByText("Java 17")).toBeVisible();
  await expect(page.getByRole("button", { name: UNMET })).toBeVisible();

  // And back to plain — from the reloaded document.
  await page.getByRole("button", { name: UNMET }).click();
  await expect(page.getByRole("button", { name: UNMET })).toHaveCount(0);
  await page.waitForLoadState("networkidle");

  await reopen(page, title);
  expect(await page.getByRole("button", { name: UNMET }).count()).toBe(0);
});

test("a heading explains its block, and the chevron leaves the map", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const title = `Advania ${Date.now()}`;
  await openMap(page, title);

  // The "Get answers now" card: the block's name, the explanation, and a worded "Got it".
  await page.getByRole("button", { name: 'What "Skills" is for' }).click();
  // The panel itself is a dialog too, so the explainer is the one holding "Got it".
  const card = page
    .getByRole("dialog")
    .filter({ has: page.getByRole("button", { name: "Got it" }) });
  await expect(card.getByText("Skills", { exact: true })).toBeVisible();
  await card.getByRole("button", { name: "Got it" }).click();
  await expect(card).toBeHidden();

  // That one button is the way out — the panel closes and the goal is underneath it.
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Requirements map" }),
  ).toBeHidden();
  await expect(
    page.getByRole("heading", { name: "Options", exact: true }),
  ).toBeVisible();
});
