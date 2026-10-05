import { test, expect, type Page } from "@playwright/test";
import { axeScan } from "./a11y-helpers";
import {
  createGoal,
  addOptions,
  openElementMenu,
  stubAiProvider,
} from "./helpers";

/**
 * The overlays `accessibility.spec.ts` does not open (BUG-023).
 *
 * **Why a second file rather than more cases in the first.** These need a goal that *has* things —
 * an option to open a menu on, a resource to edit, an item in Reality to act on. That is the
 * answer to the question this suite kept running into: **a check that opens whatever happens to be
 * in the database measures the database, not the app.** The screenshot pass showed it plainly — the
 * ⋯ menu photographed empty because the goal it picked had no options. So this spec **builds its
 * own fixture first** and every surface below is reached from something the test created itself.
 *
 * `npx playwright test e2e/a11y-overlays.spec.ts`
 */

const DIALOG = '[role="dialog"]';
const ALERT = '[role="alertdialog"]';
const MENU = '[role="menu"]';

/**
 * Violations each surface had when it was recorded, by rule id. Same contract as the sister
 * spec: a line is a defect that has been seen, never a decision, and anything new fails.
 *
 * Recorded 2026-10-04.
 */
const ACCEPTED: Record<string, Record<string, number>> = {
  // **The one node is the menu's own destructive item** — "Delete option", red on white.
  "Option menu": { "color-contrast": 1 },
  "Reality item menu": {},
  "Options filter panel": {},
  "Resources filter panel": {},
  "Delete resource confirm": { "color-contrast": 4 },
  // The AI panel's own surfaces are not Radix dialogs, so they are scanned with the page behind
  // them and carry its chrome — the teal bar's avatar and the chat's own gradient.
  "AI provider sheet": { "color-contrast": 6 },
  "AI attach menu": { "color-contrast": 8 },
  "Header search results": { "color-contrast": 1 },
  // ── What a target brings with it ───────────────────────────────────
  "Targets filter panel": {},
  // Its own destructive item again, as in every element menu.
  "Target menu": { "color-contrast": 1 },
  "Delete target confirm": { "color-contrast": 3 },
  // Ceilings. The card's panel is part of the page, so these carry the page's chrome with them —
  // and the card's own text includes a date and a progress figure, which is why the numeric one
  // read 6 in one run and 7 in the next with nothing changed.
  "Target progress — numeric": { "color-contrast": 8 },
  "Target progress — checklist": { "color-contrast": 8 },
  "Subtask menu": { "color-contrast": 1 },
};

async function scan(page: Page, surface: string, within?: string) {
  await axeScan(page, surface, ACCEPTED, within);
}

/**
 * Add a target through the form, because **a target is the thing most of this goal's surfaces hang
 * off**: its own ⋯ menu, the confirm behind it, the progress panel its card opens, the subtask menu
 * inside a checklist, and the Will-do filter panel, which is not rendered at all while the list is
 * empty. None of it can be reached without creating one first.
 *
 * The two types are not interchangeable — a numeric target's progress panel is a pair of ± buttons
 * and two number fields, a checklist's is a list of its own rows — so both are made.
 */
async function addTarget(
  page: Page,
  kind: "Numeric" | "Checklist",
  title: string,
) {
  await page.getByRole("button", { name: "Add target" }).first().click();
  const form = page.getByRole("dialog");
  // The type cards carry a description under their title, so the accessible name is the whole
  // card's text — matched on how it starts, not on equality.
  await form.getByRole("button", { name: new RegExp(`^${kind}`) }).click();
  await form.getByPlaceholder("e.g. Outbound applications").fill(title);

  if (kind === "Numeric") {
    // Bound to their labels now (BUG-023), which is also what makes them addressable here.
    await form.getByLabel("Start", { exact: false }).fill("0");
    await form.getByLabel("Target", { exact: false }).fill("10");
  } else {
    // A checklist starts with **no** rows — "Tasks — add at least one" — so one has to be asked
    // for before there is anywhere to type, and the form will not submit until there is.
    await form.getByRole("button", { name: "Add task" }).click();
    const task = form.getByRole("textbox").last();
    await task.fill("A11y subtask");
    // An inline field commits on Enter or on blur — typed and uncommitted, the form stays invalid
    // and its submit stays disabled.
    await task.press("Enter");
  }

  // The submit says the same words as the trigger that opened the form, hence the scope.
  await form.getByRole("button", { name: "Add target", exact: true }).click();
  await page.waitForLoadState("networkidle");
  // `.filter({ visible: true })` because the list is rendered twice — a table for a laptop and
  // cards for a phone, one of them hidden by CSS — and the hidden copy matches the text first.
  await expect(
    page.getByText(title).filter({ visible: true }).first(),
  ).toBeVisible();
}

/** A goal with one of everything these surfaces need. */
async function fixture(page: Page, title: string) {
  await createGoal(page, title);

  // An option — for its ⋯ menu and its delete confirm.
  await addOptions(page, ["A11y overlay option"]);

  // Something in Reality — for the item's own ⋯ menu.
  const action = page.getByPlaceholder("Add an action you've taken…");
  await action.fill("A11y overlay action");
  await action.press("Enter");
  await expect(page.getByText("A11y overlay action")).toBeVisible();

  // A note resource — for the edit sheet and the delete confirm.
  await page.getByRole("button", { name: "Add resource" }).first().click();
  const sheet = page.getByRole("dialog");
  await sheet.getByRole("textbox").first().fill("A11y overlay note");
  await sheet.getByRole("button", { name: "Add resource" }).click();
  await page.waitForLoadState("networkidle");
  await expect(page.getByText("A11y overlay note").first()).toBeVisible();
}

test("every menu, confirm and panel a goal page can open", async ({ page }) => {
  test.setTimeout(300_000);
  await fixture(page, `A11y overlays ${Date.now()}`);

  // ── An option: its menu, and the confirm behind the menu's Delete ──────────
  const option = page.locator("li", { hasText: "A11y overlay option" }).first();
  await openElementMenu(page, option, "Option actions");
  await scan(page, "Option menu", MENU);
  // **No confirm here, deliberately not scanned**: deleting an option happens on the click, with
  // no dialog in between (`OptionsList`). The confirms this app does have are the goal's, the
  // target's and the resource's — the resource one is below, the goal's is in the sister spec.
  await page.keyboard.press("Escape");

  // ── A Reality item's own menu ──────────────────────────────────────────────
  const item = page.locator("li", { hasText: "A11y overlay action" }).first();
  await openElementMenu(page, item, "Item actions");
  await scan(page, "Reality item menu", MENU);
  await page.keyboard.press("Escape");

  // ── The two filter panels a goal has of its own ────────────────────────────
  await page.getByRole("button", { name: "Filter options" }).first().click();
  await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
  await scan(page, "Options filter panel", DIALOG);
  await page.keyboard.press("Escape");

  await page
    .getByRole("button", { name: "Filter and sort resources" })
    .first()
    .click();
  await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
  await scan(page, "Resources filter panel", DIALOG);
  await page.keyboard.press("Escape");

  // ── A resource: the confirm behind its delete ───────────────────────────
  // The card reveals copy / download / delete — there is no edit here, a note is edited by opening
  // it (which the sister spec scans as "Note open").
  await page
    .getByRole("button", { name: /^Actions for / })
    .first()
    .click();
  // Named "Remove", and **only by its `title`** — it carries no `aria-label` of its own, which is
  // the weakest way to name a control that still computes a name at all.
  await page
    .getByRole("button", { name: "Remove", exact: true })
    .first()
    .click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await scan(page, "Delete resource confirm", ALERT);
  await page.keyboard.press("Escape");
});

test("everything a target brings with it", async ({ page }) => {
  test.setTimeout(300_000);
  await createGoal(page, `A11y targets ${Date.now()}`);
  await addTarget(page, "Numeric", "A11y numeric target");
  await addTarget(page, "Checklist", "A11y checklist target");

  // ── The Will-do filter panel, which does not exist until the list does ─────────
  await page
    .getByRole("button", { name: "Filter and sort targets" })
    .first()
    .click();
  await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
  await scan(page, "Targets filter panel", DIALOG);
  await page.keyboard.press("Escape");

  // ── A target's own menu, and the confirm behind its Delete ────────────────
  // **A laptop draws this list as a table** and the menu lives in its Actions column; the phone's
  // card has no ⋯ at all. The two renderings are genuinely different surfaces, which is why the
  // progress panels further down are reached at the other width.
  const row = page.getByRole("row", { name: /A11y numeric target/ }).first();
  await openElementMenu(page, row, "Target actions");
  await scan(page, "Target menu", MENU);

  await page.getByRole("menuitem", { name: "Delete target" }).first().click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await scan(page, "Delete target confirm", ALERT);
  await page.keyboard.press("Escape");

  // ── The progress panel a card opens — a different thing for each type ──────────
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Options", exact: true }),
  ).toBeVisible();

  // Opening one renames its own button to "N% progress", so the next "Update progress" is the
  // other card's.
  await page.getByRole("button", { name: "Update progress" }).first().click();
  await expect(page.getByRole("button", { name: "Increment" })).toBeVisible();
  await scan(page, "Target progress — numeric");

  await page.getByRole("button", { name: "Update progress" }).first().click();
  await expect(page.getByText("A11y subtask").first()).toBeVisible();
  await scan(page, "Target progress — checklist");

  // ── And a subtask's own menu, inside that open panel ───────────────────
  const subtaskMenu = page
    .getByRole("button", { name: "Subtask actions" })
    .first();
  await expect(async () => {
    await subtaskMenu.hover();
    await subtaskMenu.click();
    await expect(page.getByRole("menuitem").first()).toBeVisible({
      timeout: 2000,
    });
  }).toPass({ timeout: 20_000 });
  await scan(page, "Subtask menu", MENU);
  await page.keyboard.press("Escape");
});

test("what the AI panel opens on top of itself", async ({ page }) => {
  test.setTimeout(240_000);
  await stubAiProvider(page, [{ reply: "Noted." }]);
  await createGoal(page, `A11y ai overlays ${Date.now()}`);

  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();
  await expect(
    page.getByPlaceholder("Ask, plan, or request an action…"),
  ).toBeVisible();

  // The key sheet. It is the panel's own markup rather than a Radix dialog, so it is scanned with
  // the page — the chrome behind is part of what that costs.
  await page.getByRole("button", { name: /Bring your own key/i }).click();
  await scan(page, "AI provider sheet");
  await page.keyboard.press("Escape");

  // The composer's attach menu, likewise the panel's own.
  const attach = page.getByRole("button", { name: "Attach" }).first();
  if (await attach.isVisible().catch(() => false)) {
    await attach.click();
    await scan(page, "AI attach menu");
    await page.keyboard.press("Escape");
  }
});

test("the header's search results", async ({ page }) => {
  test.setTimeout(180_000);
  const title = `A11y search ${Date.now()}`;
  await createGoal(page, title);

  await page.goto("/");
  await page.waitForLoadState("networkidle");
  // The results hang under the field as their own card, which no page scan reaches until a query
  // has been typed.
  await page.getByPlaceholder("Search goals").first().fill("A11y");
  await expect(page.getByRole("link", { name: /A11y/ }).first()).toBeVisible();
  await scan(page, "Header search results");
});
