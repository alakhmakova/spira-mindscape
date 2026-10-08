import { test, expect, type Page } from "@playwright/test";
import { axeScan, type AcceptedViolations } from "./a11y-helpers";
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
 * The coach panel itself. Its own surfaces are not Radix dialogs, so a page-wide scan of them
 * swept in the goal page behind and **counted a different number every run** — 6, then 17, then 6
 * on unchanged code, because what the goal page had finished rendering decided the total. Scoping
 * to the panel measures what this test is named after: what the coach opens on top of *itself*.
 */
const AI_PANEL = 'aside[aria-label="AI coach"]';

/**
 * What each surface is allowed to have — **by element, not by count** (re-recorded 2026-10-06).
 *
 * Each line is a defect that has been seen, named by the element carrying it, with how many nodes
 * share that signature. A node whose signature is not listed, or one that has multiplied past its
 * number, fails the run. Fewer than recorded never fails: fixing something must not break the
 * suite.
 *
 * **This replaces a per-rule total, and the reason is a defect those totals hid for weeks.** The
 * owner asked why no test had ever flagged the "Add target" button, which puts white on `#F45D48`
 * at 3.23:1. axe had been reporting it on every single run — this surface's line read
 * `{ "color-contrast": 8 }`, axe found 5, `5 <= 8`, green. Worse, under a ceiling of 8 one defect
 * could be swapped for another and nothing would move. A number says how many; only a signature
 * says which, and only a signature can be taken to a developer as "go and fix this one".
 *
 * Re-record with `A11Y_RECORD=1` — and read `RECORD`'s own warning before believing that run.
 */
const ACCEPTED: AcceptedViolations = {
  "Option menu": {
    "color-contrast": {
      // <div role="menuitem" class="relative flex cursor..." tabindex="-1" data-orientation="vertical" data-radix-coll
      "div.cursor....flex.relative": 1,
    },
  },
  "Reality item menu": {
    "color-contrast": {
      // <div role="menuitem" class="relative flex cursor..." tabindex="-1" data-orientation="vertical" data-radix-coll
      "div.cursor....flex.relative": 1,
    },
  },
  "Options filter panel": {},
  "Resources filter panel": {},
  "Delete resource confirm": {},
  "AI provider sheet": {
    "color-contrast": {
      // <span class="ml-2 text-[12px] text-[#003737]/50">200 000 tokens</span>
      "span.ml-2.text-[#003737]/50.text-[12px]": 5,
      // <span class="text-[12px] text-[#003737]/40">Not connected</span>
      "span.text-[#003737]/40.text-[12px]": 4,
      // <p class="text-[13.5px] text-[#003737]/60 mb-4 leading-[1.5]">Keys are stored encrypted on your account. Keep
      "p.leading-[1.5].mb-4.text-[#003737]/60.text-[13.5px]": 1,
      // <span class="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#003737]/50">
      "span.font-mono.gap-1.5.inline-flex.items-center.text-[#003737]/50.text-[12px]": 1,
    },
  },
  "AI attach menu": {
    "color-contrast": {
      // <span class="text-[16px] font-normal leading-none text-white/74 pt-0.5">ai coach</span>
      "span.font-normal.leading-none.pt-0.5.text-[16px].text-white/74": 1,
      // <button class="inline-flex items-center gap-[6px] text-[12.5px] font-medium text-white/74 hover:text-white hov
      "button.-mx-2.font-medium.gap-[6px].hover:bg-white/10.hover:text-white.inline-flex.items-center.px-2.py-1.rounded-lg.text-[12.5px].text-white/74.transition-colors": 1,
    },
  },
  "Header search results": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
  "Targets filter panel": {},
  "Target menu": {
    "color-contrast": {
      // <div role="menuitem" class="relative flex cursor..." tabindex="-1" data-orientation="vertical" data-radix-coll
      "div.cursor....flex.relative": 1,
    },
  },
  "Delete target confirm": {},
  "Target progress — numeric": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button aria-expanded="true" class="flex w-full items-center justify-center bg-[#E0F2F5] px-4 py-3 text-[15px]
      "button.bg-[#E0F2F5].flex.font-semibold.hover:bg-[#8DD3D4]/40.items-center.justify-center.px-4.py-3.text-[15px].text-primary.transition-colors.w-full": 1,
      // <span>(from&nbsp;</span>
      span: 1,
      // <button type="button" class="mt-2 flex items-center gap-2 py-1 text-left text-sm font-semibold text-destructiv
      "button.flex.font-semibold.gap-2.hover:text-destructive/80.items-center.mt-2.py-1.text-destructive.text-left.text-sm.transition-colors": 1,
    },
  },
  "Target progress — checklist": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <button type="button" class="mt-2 flex items-center gap-2 py-1 text-left text-sm font-semibold text-destructiv
      "button.flex.font-semibold.gap-2.hover:text-destructive/80.items-center.mt-2.py-1.text-destructive.text-left.text-sm.transition-colors": 2,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <span>(from&nbsp;</span>
      span: 1,
      // <button aria-expanded="true" class="flex w-full items-center justify-center bg-[#E0F2F5] px-4 py-3 text-[15px]
      "button.bg-[#E0F2F5].flex.font-semibold.hover:bg-[#8DD3D4]/40.items-center.justify-center.px-4.py-3.text-[15px].text-primary.transition-colors.w-full": 1,
    },
  },
  "Subtask menu": {
    "color-contrast": {
      // <div role="menuitem" class="relative flex cursor..." tabindex="-1" data-orientation="vertical" data-radix-coll
      "div.cursor....flex.relative": 1,
    },
  },
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

  // The key sheet. It is the panel's own markup rather than a Radix dialog, so it is scanned
  // scoped to the panel (`AI_PANEL`) rather than to a dialog role.
  //
  // **Wait for the provider rows, not just for the sheet.** The sheet's frame arrives before its
  // list does, and a scan that lands in between measures an empty card: the 2026-10-06 recording
  // caught exactly that and wrote `{}`, after which every normal run reported eleven nodes the
  // baseline had never heard of. The per-element format is what made that legible — it named the
  // muted token rows rather than saying "11 where 0 was expected".
  await page.getByRole("button", { name: /^Provider: /i }).click();
  await expect(page.getByText("Google Gemini")).toBeVisible();
  await scan(page, "AI provider sheet", AI_PANEL);
  await page.keyboard.press("Escape");

  // The composer's attach menu, likewise the panel's own.
  const attach = page.getByRole("button", { name: "Attach" }).first();
  if (await attach.isVisible().catch(() => false)) {
    await attach.click();
    await scan(page, "AI attach menu", AI_PANEL);
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
