import { test, expect, type Locator, type Page } from "@playwright/test";
import { axeScan, type AcceptedViolations } from "./a11y-helpers";
import { createGoal, skipNotificationsHint, stubAiProvider } from "./helpers";

/**
 * The All-goals page and **everything it opens**, measured as accessibility surfaces of their own
 * (owner, 2026-10-10: "тесты на доступность для всей главной страницы, в том числе то что
 * вложено").
 *
 * `accessibility.spec.ts` scans the page itself, its Filter panel and the New goal sheet. What it
 * never opened is everything that arrived with the Upcoming column and the standing rail, and the
 * calendar as it is reached FROM the home page:
 *
 *  - the **Upcoming** column with events in it (an empty one has no rows to judge),
 *  - the **Add event** modal, its Type radio group in each state, the item **listbox**, and the
 *    date card it opens — a popover on a laptop, a modal on a phone,
 *  - the **New goal** sheet's own date card,
 *  - the **remove** confirm,
 *  - the **standing rail** (a column on a laptop, an overlay on a phone),
 *  - the **AI coach** opened from the home page rather than from a goal.
 *
 * Each overlay is scanned on its own (see `axeScan`), and the keyboard half — which axe cannot
 * judge — is checked by hand below: focus goes into a modal, stays there, and comes back to what
 * opened it.
 */

const PHONE = { width: 390, height: 844 };

// Add event shows a one-time hint first in a browser without notifications; not under test here.
test.beforeEach(async ({ page }) => {
  await skipNotificationsHint(page);
});
const LAPTOP = { width: 1440, height: 900 };

const DIALOG = '[role="dialog"]';
const ALERT = '[role="alertdialog"]';
const LISTBOX = '[role="listbox"]';
const RAIL = 'aside[aria-label="Main"]';
const AI_PANEL = 'aside[aria-label="AI coach"]';

/**
 * What each surface is allowed to have — see `accessibility.spec.ts` for why this is by element
 * and not by count. **Empty is the goal**: these surfaces were built against the rules and every
 * line added here would be a defect, not a decision.
 */
const ACCEPTED: AcceptedViolations = {};

async function scan(page: Page, surface: string, within?: string) {
  await axeScan(page, surface, ACCEPTED, within);
}

async function home(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(
    page.getByRole("heading", { name: "Goals", exact: true }),
  ).toBeVisible();
}

const upcoming = (page: Page) => page.getByRole("region", { name: "Upcoming" });

/** An important date, added through the API so the column has a row to be judged. */
async function seedImportantDate(page: Page, title: string) {
  const res = await page.request.post("/graphql", {
    data: {
      query:
        "mutation($input: CreateUpcomingEventInput!) { createUpcomingEvent(input: $input) { id } }",
      variables: {
        input: {
          kind: "important",
          title,
          date: new Date(Date.now() + 2 * 86_400_000).toISOString(),
        },
      },
    },
  });
  expect(res.ok(), await res.text()).toBeTruthy();
  const body = await res.json();
  expect(body.errors, JSON.stringify(body.errors)).toBeUndefined();
}

/** The date trigger inside a form: a button reading the date, or its placeholder. */
const dateTrigger = (scope: Locator) =>
  scope.getByRole("button", {
    name: /Pick a (date|deadline)|Set deadline|\d{1,2}(st|nd|rd|th), \d{4}/,
  });

/**
 * **Focus is inside the overlay, and Tab keeps it there** (WCAG 2.4.3, 2.1.2). Pressing Tab more
 * times than the dialog has controls must never land outside it — a modal whose focus leaks lets
 * a keyboard user type into the page that the scrim says is unavailable.
 */
async function expectFocusTrapped(page: Page, overlay: Locator, presses = 25) {
  await expect
    .poll(() => overlay.evaluate((el) => el.contains(document.activeElement)))
    .toBe(true);
  for (let i = 0; i < presses; i++) {
    await page.keyboard.press("Tab");
    const inside = await overlay.evaluate((el) =>
      el.contains(document.activeElement),
    );
    expect(inside, `focus left the overlay after ${i + 1} Tab(s)`).toBe(true);
  }
}

test.describe("the home page and what it opens — laptop", () => {
  test.use({ viewport: LAPTOP });

  test("the page with Upcoming filled in, and the standing rail", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await createGoal(page, `A11y home ${Date.now()}`);
    await seedImportantDate(page, `A11y date ${Date.now()}`);
    await home(page);
    await expect(upcoming(page).getByRole("listitem").first()).toBeVisible();
    await scan(page, "Home with Upcoming");

    // The rail starts open on a laptop; its toggle says so.
    const toggle = page.getByRole("button", { name: "Hide navigation" });
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await scan(page, "Standing rail", RAIL);

    // Its toggle is a real disclosure: the state flips with it, from the keyboard.
    await toggle.focus();
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("button", { name: "Show navigation" }),
    ).toHaveAttribute("aria-expanded", "false");
    await expect(page.locator(RAIL)).toHaveCount(0);
  });

  test("the Add event modal in each state, its list and its calendar", async ({
    page,
  }) => {
    test.setTimeout(150_000);
    // A goal with a deadline, so "Choose goal" has something in its list.
    const title = `A11y event goal ${Date.now()}`;
    await createGoal(page, title);
    await home(page);
    const card = page
      .locator("div.group")
      .filter({ has: page.getByRole("link", { name: title }) })
      .first();
    await card.getByText("Set deadline").click();
    await page.getByRole("button", { name: "Today", exact: true }).click();
    await expect(card).toContainText("Due date");

    const opener = upcoming(page).getByRole("button", { name: "Add event" });
    await opener.click();
    const dialog = page.getByRole("dialog", { name: "Add event" });
    await expect(dialog).toBeVisible();
    await scan(page, "Add event — important date", DIALOG);
    await expectFocusTrapped(page, dialog);

    // The calendar, opened from inside the modal.
    await dateTrigger(dialog).click();
    await expect(page.getByRole("grid")).toBeVisible();
    await scan(
      page,
      "Add event — date popover",
      '[role="dialog"]:has([role="grid"])',
    );
    await page.keyboard.press("Escape");
    // Escape closes the popover only — the form under it stays.
    await expect(dialog).toBeVisible();

    // A linked type: the radio group, then the item list as a listbox.
    await dialog.getByRole("radio", { name: "Goal" }).click();
    await expect(dialog.getByRole("radio", { name: "Goal" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    await scan(page, "Add event — goal", DIALOG);
    await dialog.getByRole("combobox", { name: "Choose goal" }).click();
    await expect(page.getByRole("option", { name: title })).toBeVisible();
    await scan(page, "Add event — item list", LISTBOX);
    await page.keyboard.press("Escape");

    // A type with nothing to choose: the disabled combobox and its explanation.
    await dialog.getByRole("radio", { name: "Target" }).click();
    await scan(page, "Add event — nothing to add", DIALOG);

    // Escape closes the modal and focus goes back to the link that opened it.
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(opener).toBeFocused();
  });

  test("the New goal sheet's calendar", async ({ page }) => {
    test.setTimeout(90_000);
    await home(page);
    await page.getByRole("button", { name: "New goal" }).first().click();
    const sheet = page.getByRole("dialog");
    await expect(
      sheet.getByPlaceholder("e.g. Launch Spira to first 50 users"),
    ).toBeVisible();
    await expectFocusTrapped(page, sheet);

    await dateTrigger(sheet).click();
    await expect(page.getByRole("grid")).toBeVisible();
    await scan(
      page,
      "New goal — date popover",
      '[role="dialog"]:has([role="grid"])',
    );
    await page.keyboard.press("Escape");
  });

  test("the remove confirm, and where focus goes after it", async ({
    page,
  }) => {
    test.setTimeout(90_000);
    const name = `A11y remove ${Date.now()}`;
    await seedImportantDate(page, name);
    await home(page);

    const remove = upcoming(page).getByRole("button", {
      name: `Remove "${name}" from Upcoming`,
    });
    await remove.click();
    const confirm = page.getByRole("alertdialog");
    await expect(confirm).toBeVisible();
    await scan(page, "Upcoming remove confirm", ALERT);
    await expectFocusTrapped(page, confirm, 8);

    // Going back leaves the row where it was and puts focus on the button that asked.
    await confirm.getByRole("button", { name: "No, go back" }).click();
    await expect(remove).toBeFocused();
    await remove.click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "Yes, remove" })
      .click();
  });

  test("the AI coach, opened from the home page", async ({ page }) => {
    test.setTimeout(90_000);
    await stubAiProvider(page, [{ reply: "Noted." }]);
    await home(page);
    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .click();
    await expect(
      page.getByPlaceholder("Ask, plan, or request an action…"),
    ).toBeVisible();
    await scan(page, "AI coach (home)", AI_PANEL);

    // A turn on the page: the transcript is judged with a message in it, not empty.
    await page
      .getByPlaceholder("Ask, plan, or request an action…")
      .fill("Hello");
    await page.keyboard.press("Enter");
    await expect(
      page.locator(AI_PANEL).getByText("Noted.").last(),
    ).toBeVisible();
    await scan(page, "AI coach with a turn (home)", AI_PANEL);
  });
});

test.describe("the home page and what it opens — phone", () => {
  test.use({ viewport: PHONE, hasTouch: true, isMobile: true });

  test("the rail overlay, the Add event sheet and its date modal", async ({
    page,
  }) => {
    test.setTimeout(150_000);
    await seedImportantDate(page, `A11y phone date ${Date.now()}`);
    await home(page);
    await scan(page, "Home with Upcoming (phone)");

    // The rail is closed on a phone and opens as an overlay over the page.
    const open = page.getByRole("button", { name: "Show navigation" });
    await expect(open).toHaveAttribute("aria-expanded", "false");
    await open.tap();
    await expect(page.locator(RAIL)).toBeVisible();
    await scan(page, "Rail overlay (phone)", RAIL);
    await page.getByRole("button", { name: "Close navigation" }).tap();
    await expect(page.locator(RAIL)).toHaveCount(0);

    await upcoming(page).getByRole("button", { name: "Add event" }).tap();
    const sheet = page.getByRole("dialog", { name: "Add event" });
    await expect(sheet).toBeVisible();
    await scan(page, "Add event sheet (phone)", DIALOG);

    // On a phone the calendar is a modal stacked on the sheet.
    await dateTrigger(sheet).tap();
    const picker = page
      .getByRole("dialog")
      .filter({ has: page.getByRole("grid") });
    await expect(picker).toBeVisible();
    await scan(
      page,
      "Add event — date modal (phone)",
      '[role="dialog"]:has([role="grid"])',
    );
    await picker.getByRole("button", { name: "Cancel" }).tap();
    await expect(sheet).toBeVisible();
  });

  test("the New goal drawer's date modal, and the coach", async ({ page }) => {
    test.setTimeout(120_000);
    await stubAiProvider(page, [{ reply: "Noted." }]);
    await home(page);

    await page.getByRole("button", { name: "New goal" }).first().tap();
    const sheet = page.getByRole("dialog");
    await expect(
      sheet.getByPlaceholder("e.g. Launch Spira to first 50 users"),
    ).toBeVisible();
    await dateTrigger(sheet).tap();
    const picker = page
      .getByRole("dialog")
      .filter({ has: page.getByRole("grid") });
    await expect(picker).toBeVisible();
    await scan(
      page,
      "New goal — date modal (phone)",
      '[role="dialog"]:has([role="grid"])',
    );
    await picker.getByRole("button", { name: "Cancel" }).tap();
    await sheet.getByRole("button", { name: "Cancel" }).tap();
    await expect(sheet).toBeHidden();

    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .tap();
    await expect(
      page.getByPlaceholder("Ask, plan, or request an action…"),
    ).toBeVisible();
    // On a phone the coach is a bottom drawer, so it is a dialog rather than the laptop's column.
    await scan(page, "AI coach (phone)", DIALOG);
  });
});
