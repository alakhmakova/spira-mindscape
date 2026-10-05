import { test, expect, type Page, type Locator } from "@playwright/test";

import { createGoal } from "./helpers";

/**
 * **A goal's target filters, and its padlock, belong to that goal** (owner, 2026-08-31 — BUG-067).
 *
 * The unit tests in `src/components/shell/shell-store.test.ts` pin the store. This file walks the
 * journey the owner actually described, through the real panel, because that is where the bug was
 * visible and the store is not the only thing that has to be right: the panel has to *read* from
 * this goal's scope and *write* to it, and the goal-workspace route has to hand the right id to
 * both. A store scoped correctly behind a component still reading one global field would pass every
 * unit test in the repo.
 *
 * The journey, in the order the owner walked it:
 *
 *  1. pin a filter on goal A;
 *  2. open goal B — it must be untouched, with its own padlock open;
 *  3. pin a *different* filter on B, go back to A — A must be exactly as it was left;
 *  4. change a filter on goal C and leave its padlock **open** — nothing is kept;
 *  5. after all of it, A and B still hold their own, different arrangements **across a reload**,
 *     and C has gone back to its defaults.
 *
 * These run in order, on one page, against three goals created once — the state under test *is*
 * the accumulation, so splitting it into independent tests would mean re-walking the whole journey
 * five times to assert five things about it.
 */
test.describe.configure({ mode: "serial" });

/** The three goals, by workspace URL. */
const goal: Record<"a" | "b" | "c", string> = { a: "", b: "", c: "" };
let page: Page;

/**
 * The one on screen.
 *
 * The targets list is rendered **twice** — a card stack for phones and a table for laptops, one of
 * them hidden by CSS at any width — so a bare `getByText` matches two nodes and `.first()` can
 * easily be the hidden one.
 */
function visible(locator: Locator): Locator {
  return locator.filter({ visible: true }).first();
}

/** The open Filter & Sort panel. */
function panel(): Locator {
  return page.getByRole("dialog");
}

async function openFilters() {
  await page
    .getByRole("button", { name: "Filter and sort targets" })
    .first()
    .click();
  // Apply, not a question heading: "Progress" is both a heading and a Sort-by card.
  await expect(panel().getByRole("button", { name: "Apply" })).toBeVisible();
}

/** Close the panel with its own confirm word. */
async function apply() {
  await panel().getByRole("button", { name: "Apply" }).click();
  await expect(panel()).toBeHidden();
}

/**
 * Choose one answer. Every label used in this file is unique across the panel's six questions —
 * only "All" repeats, which is why no test here ever picks it.
 */
async function choose(label: string) {
  await panel().getByRole("radio", { name: label, exact: true }).click();
}

/** The padlock in the panel's head. Its accessible name states which way it is currently set. */
function padlock(): Locator {
  return panel().getByRole("button", {
    name: /Keep these filters and sort|Filters and sort are kept/,
  });
}

async function closePadlock() {
  await expect(
    panel().getByRole("button", { name: "Keep these filters and sort" }),
  ).toBeVisible();
  await padlock().click();
  await expect(
    panel().getByRole("button", {
      name: "Filters and sort are kept — unlock to let them reset",
    }),
  ).toBeVisible();
}

/** Assert this goal's whole arrangement: which answer is on, and whether it is pinned. */
async function expectArrangement(
  chosen: string | null,
  { pinned }: { pinned: boolean },
) {
  await openFilters();
  for (const label of ["Done", "Checklist", "Overdue"]) {
    await expect(
      panel().getByRole("radio", { name: label, exact: true }),
      `${label} should be ${label === chosen ? "on" : "off"}`,
    ).toHaveAttribute("aria-checked", String(label === chosen));
  }
  await expect(padlock()).toHaveAttribute("aria-pressed", String(pinned));
  await apply();
}

/** Add one target, so a filter has something visible to hide. */
async function addTarget(title: string) {
  await page.getByRole("button", { name: "Add target", exact: true }).click();
  const sheet = page.getByRole("dialog");
  await sheet.getByPlaceholder("e.g. Outbound applications").fill(title);
  await sheet.getByRole("button", { name: "Add target", exact: true }).click();
  await expect(sheet).toBeHidden();
  await expect(visible(page.getByText(title))).toBeVisible();
}

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage();
  const stamp = Date.now();
  for (const key of ["a", "b", "c"] as const) {
    await createGoal(page, `Filters ${key.toUpperCase()} ${stamp}`);
    await addTarget(`${key.toUpperCase()} task`);
    goal[key] = page.url();
  }
});

test.afterAll(async () => {
  await page.close();
});

test("a padlocked filter on one goal leaves every other goal untouched", async () => {
  await page.goto(goal.a);
  await openFilters();
  await choose("Done");
  await closePadlock();
  await apply();

  // It really filters, not merely displays: the one target is not done, so the list empties.
  await expect(
    visible(page.getByText("No targets match that search or filter.")),
  ).toBeVisible();

  // Goal B has never been touched — its own defaults, its own padlock, still open.
  await page.goto(goal.b);
  await expectArrangement(null, { pinned: false });
});

test("another goal cannot write through a closed padlock", async () => {
  await page.goto(goal.b);
  await openFilters();
  await choose("Checklist");
  await closePadlock();
  await apply();

  // This is the half the owner reported second, and the worse one: goal A was pinned, and
  // changing goal B used to rewrite it anyway.
  await page.goto(goal.a);
  await expectArrangement("Done", { pinned: true });
});

test("two padlocked goals hold different arrangements at the same time", async () => {
  await page.goto(goal.a);
  await expectArrangement("Done", { pinned: true });

  await page.goto(goal.b);
  await expectArrangement("Checklist", { pinned: true });
});

test("a goal whose padlock is open keeps nothing", async () => {
  await page.goto(goal.c);
  await openFilters();
  await choose("Overdue");
  await apply();
  // On screen right now — unlocking is not a reset, and neither is *not* locking.
  await expectArrangement("Overdue", { pinned: false });

  await page.reload();
  await expectArrangement(null, { pinned: false });
});

test("and none of that disturbs the two padlocked goals, across a reload", async () => {
  await page.goto(goal.a);
  await page.reload();
  await expectArrangement("Done", { pinned: true });

  await page.goto(goal.b);
  await page.reload();
  await expectArrangement("Checklist", { pinned: true });
});
