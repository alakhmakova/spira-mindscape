import { test, expect, type Page } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { axeScan, type AcceptedViolations } from "./a11y-helpers";
import {
  createGoal,
  addOptions,
  openElementMenu,
  stubAiProvider,
} from "./helpers";

const SAMPLE_PNG = fileURLToPath(
  new URL("./fixtures/sample.png", import.meta.url),
);

/**
 * The mechanical half of WCAG 2.1 AA, over **every screen and every overlay the app has**
 * (BUG-023).
 *
 * **What this is not.** Automated rules cover roughly a third of the criteria: axe can see that a
 * button has no accessible name, and cannot see that the name is wrong, that the focus order is
 * nonsense, or that a menu which appears on hover is unreachable from a keyboard. Those are the
 * manual passes BUG-023 lists, and a green run here is not a claim of conformance — it is a
 * guarantee that the machine-checkable part has not got worse. The static half is
 * `eslint-plugin-jsx-a11y`, which reads the markup of screens no spec opens; neither replaces the
 * other.
 *
 * **What "every screen" means.** The five routes (`/`, `/calendar`, `/settings` and its three
 * tabs, `/login`, a goal) and the surfaces that only exist once something is opened — the sheets,
 * the date popover, the confirm, an element's menu, a resource preview, the fullscreen picture,
 * the coach. Each is a separate accessibility surface: it takes focus and names its own controls,
 * and a page that passes says nothing about the drawer over it. **The phone is scanned
 * separately**, because it draws different chrome — a drawer where the laptop has a side panel,
 * and a search overlay the laptop has not got at all.
 *
 * **Why the baseline.** The app had never had an accessibility pass, so the first run found real
 * violations. Failing on all of them would make the check red forever, and a permanently red check
 * is one people learn to ignore. Each rule below is therefore accepted **per surface, with the
 * count it had when it was recorded** — a new instance of an already-known rule still fails, and
 * so does any new rule. The list is meant to shrink; every line in it is a defect, not a decision.
 */
const PHONE = { width: 390, height: 844 };

/** An overlay is scanned on its own — see `scan`. */
const DIALOG = '[role="dialog"]';
const ALERT = '[role="alertdialog"]';
const MENU = '[role="menu"]';

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
  "All goals": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
  Timeline: {
    "color-contrast": {
      // <span class="font-medium text-muted-foreground/50 text-[12px]">Deadline</span>
      "span.font-medium.text-[12px].text-muted-foreground/50": 3,
      // <span>Nov 4, 2026</span>
      span: 3,
      // <span class="font-semibold text-muted-foreground/70">28d left</span>
      "span.font-semibold.text-muted-foreground/70": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <span class="num text-[11px] font-bold tabular-nums text-muted-foreground/60">20%</span>
      "span.font-bold.num.tabular-nums.text-[11px].text-muted-foreground/60": 1,
    },
  },
  "Filter panel": {},
  "New goal sheet": {},
  Calendar: {
    "color-contrast": {
      // <div class="text-xs num inline-flex items-center justify-center h-6 min-w-6 px-1.5 rounded-full font-semibold
      "div.font-semibold.h-6.inline-flex.items-center.justify-center.min-w-6.num.px-1.5.rounded-full.text-foreground/70.text-xs": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <span class="truncate">apply C:\Users\buale\OneDrive\Изображения\Снимки экрана\2026-10-06 10 12 11.png</span>
      "span.truncate": 1,
    },
  },
  "Settings — profile": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button type="button" class="inline-flex items-center gap-2 rounded-md border-2 border-border px-4 py-2 text-s
      "button.border-2.border-border.font-semibold.gap-2.hover:border-destructive/60.inline-flex.items-center.px-4.py-2.rounded-md.text-destructive.text-sm.transition-colors": 1,
    },
  },
  "Settings — fonts": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
  "Settings — about": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
  "Sign in": {},
  "Goal workspace": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button class="hidden sm:inline-flex items-center px-3 h-9 rounded-md bg-[#F45D48] text-white text-sm font-med
      "button.bg-[#F45D48].font-medium.h-9.hidden.hover:bg-[#F45D48]/90.items-center.px-3.rounded-md.sm:inline-flex.text-sm.text-white": 1,
    },
  },
  "Element menu": {
    "color-contrast": {
      // <div role="menuitem" class="relative flex cursor..." tabindex="-1" data-orientation="vertical" data-radix-coll
      "div.cursor....flex.relative": 1,
    },
  },
  "Deadline popover": {
    "color-contrast": {
      // <th aria-label="Monday" class="text-muted-foreground/40 flex-1 select-none rounded-md text-[0.8rem] font-norma
      "th.flex-1.font-normal.rdp-weekday.rounded-md.select-none.text-[0.8rem].text-center.text-muted-foreground/40": 7,
      // <span class="text-[11px] font-medium text-muted-foreground/40">40</span>
      "span.font-medium.text-[11px].text-muted-foreground/40": 6,
    },
  },
  "New target sheet": {},
  "Add a resource sheet": {},
  "Delete confirm": {},
  "Note open": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button class="hidden sm:inline-flex items-center px-3 h-9 rounded-md bg-[#F45D48] text-white text-sm font-med
      "button.bg-[#F45D48].font-medium.h-9.hidden.hover:bg-[#F45D48]/90.items-center.px-3.rounded-md.sm:inline-flex.text-sm.text-white": 1,
    },
  },
  "Picture full screen": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button class="hidden sm:inline-flex items-center px-3 h-9 rounded-md bg-[#F45D48] text-white text-sm font-med
      "button.bg-[#F45D48].font-medium.h-9.hidden.hover:bg-[#F45D48]/90.items-center.px-3.rounded-md.sm:inline-flex.text-sm.text-white": 1,
    },
  },
  "AI coach": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="text-[16px] font-normal leading-none text-white/74 pt-0.5">ai coach</span>
      "span.font-normal.leading-none.pt-0.5.text-[16px].text-white/74": 1,
      // <button class="inline-flex items-center gap-[6px] text-[12.5px] font-medium text-white/74 hover:text-white hov
      "button.-mx-2.font-medium.gap-[6px].hover:bg-white/10.hover:text-white.inline-flex.items-center.px-2.py-1.rounded-lg.text-[12.5px].text-white/74.transition-colors": 1,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
      // <button class="hidden sm:inline-flex items-center px-3 h-9 rounded-md bg-[#F45D48] text-white text-sm font-med
      "button.bg-[#F45D48].font-medium.h-9.hidden.hover:bg-[#F45D48]/90.items-center.px-3.rounded-md.sm:inline-flex.text-sm.text-white": 1,
    },
  },
  "All goals (phone)": {
    "color-contrast": {
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
  "Header search (phone)": {},
  "Filter drawer (phone)": {},
  "New goal drawer (phone)": {},
  "Goal workspace (phone)": {
    "color-contrast": {
      // <span class="text-base text-muted-foreground/60 font-medium">% completed</span>
      "span.font-medium.text-base.text-muted-foreground/60": 3,
      // <span class="grid h-9 w-9 place-items-center rounded-full border border-white/40 bg-white/15 text-sm font-semi
      "span.bg-white/15.border.border-white/40.font-semibold.grid.h-9.place-items-center.rounded-full.text-sm.text-white.w-9": 1,
    },
  },
};

/** Run axe over this surface, holding it to what `ACCEPTED` says it may have. */
async function scan(page: Page, surface: string, within?: string) {
  await axeScan(page, surface, ACCEPTED, within);
}

/** The dashboard, loaded and settled. */
async function dashboard(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(
    page.getByRole("heading", { name: "Goals", exact: true }),
  ).toBeVisible();
}

test.describe("WCAG 2.1 AA — the machine-checkable part, every surface", () => {
  test("the dashboard, its views and the panels it opens", async ({ page }) => {
    test.setTimeout(180_000);
    // A dashboard with nothing on it exercises the empty state, not the cards.
    await createGoal(page, `A11y dash ${Date.now()}`);

    await dashboard(page);
    await scan(page, "All goals");

    // Timeline is a different rendering of the same data — a table, with its own header cells.
    // The view switcher is a `role="tablist"`, so these are tabs and not buttons.
    await page.getByRole("tab", { name: "Timeline" }).click();
    await expect(page.getByRole("heading", { name: "Timeline" })).toBeVisible();
    await scan(page, "Timeline");
    await page.getByRole("tab", { name: "Cards", exact: true }).click();

    // Filter & Sort: a side panel here, a drawer on a phone (checked below).
    await page.getByRole("button", { name: "Filter and sort goals" }).click();
    await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
    await scan(page, "Filter panel", DIALOG);
    await page.keyboard.press("Escape");

    // A sheet is its own accessibility surface: it takes focus, it traps it, and its fields are
    // labelled separately from the page behind it.
    await page.getByRole("button", { name: "New goal" }).first().click();
    await expect(
      page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
    ).toBeVisible();
    await scan(page, "New goal sheet", DIALOG);
    await page.keyboard.press("Escape");
  });

  test("the pages that are not the dashboard", async ({ page }) => {
    test.setTimeout(150_000);

    await page.goto("/calendar");
    await page.waitForLoadState("networkidle");
    await scan(page, "Calendar");

    // Settings is three tabs, and About Spira is the app's one long page of prose — its own
    // surface, with headings and links a scanner has something to say about.
    await page.goto("/settings");
    await page.waitForLoadState("networkidle");
    await scan(page, "Settings — profile");
    await page.goto("/settings?tab=fonts");
    await page.waitForLoadState("networkidle");
    await scan(page, "Settings — fonts");
    await page.goto("/settings?tab=about");
    await page.waitForLoadState("networkidle");
    await scan(page, "Settings — about");

    // **Sign-in needs an anonymous session to exist at all**: the route sends an authenticated
    // visitor home, and every local run is authenticated (`dev@local`). Answering the one request
    // its guard makes — `/api/auth/me` — is the whole stub; nothing else here is faked.
    await page.route("**/api/auth/me", (route) =>
      route.fulfill({
        status: 401,
        contentType: "application/json",
        body: "{}",
      }),
    );
    // **And it has to be reached from inside the app.** In dev, Vite proxies `/login` to the
    // backend (for the OAuth redirect), so `goto("/login")` lands on Spring Security's own
    // generated page and never on ours. Loading `/` with an anonymous answer makes the root guard
    // navigate there itself, client-side, which is how a real visitor arrives too.
    await page.goto("/");
    await page.waitForURL(/\/login/);
    await expect(page.getByText(/Continue with Google/i)).toBeVisible();
    await scan(page, "Sign in");
    await page.unroute("**/api/auth/me");
  });

  test("a goal workspace and everything it opens", async ({ page }) => {
    test.setTimeout(240_000);
    await createGoal(page, `A11y goal ${Date.now()}`);
    await scan(page, "Goal workspace");

    // An element's menu — a floating menu whose items are a surface of their own.
    await addOptions(page, ["A11y option"]);
    const card = page.locator("li", { hasText: "A11y option" }).first();
    await openElementMenu(page, card, "Option actions");
    await scan(page, "Element menu", MENU);
    await page.keyboard.press("Escape");

    // The date card: a popover on a laptop, a modal on a phone (CLAUDE.md 3e-ter).
    await page
      .getByRole("button", { name: /Set deadline/ })
      .first()
      .click();
    await expect(page.getByRole("grid")).toBeVisible();
    await scan(page, "Deadline popover", DIALOG);
    await page.keyboard.press("Escape");

    await page.getByRole("button", { name: "Add target" }).first().click();
    await expect(
      page.getByPlaceholder("e.g. Outbound applications"),
    ).toBeVisible();
    await scan(page, "New target sheet", DIALOG);
    await page.keyboard.press("Escape");

    await page.getByRole("button", { name: "Add resource" }).first().click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await scan(page, "Add a resource sheet", DIALOG);
    await page.keyboard.press("Escape");

    // The confirm that deletes the goal — an AlertDialog, and the last thing to open here.
    await page.getByRole("button", { name: "Delete goal" }).first().click();
    await expect(page.getByRole("alertdialog")).toBeVisible();
    await scan(page, "Delete confirm", ALERT);
    await page.keyboard.press("Escape");
  });

  test("a resource open on the page, and a picture full screen", async ({
    page,
  }) => {
    test.setTimeout(240_000);
    await createGoal(page, `A11y res ${Date.now()}`);

    // A note: the rich-text editor is a contenteditable with a toolbar of icon buttons.
    await page.getByRole("button", { name: "Add resource" }).first().click();
    const sheet = page.getByRole("dialog");
    await sheet.getByRole("textbox").first().fill("A11y note");
    await sheet.getByRole("button", { name: "Add resource" }).click();
    await page.waitForLoadState("networkidle");
    await page.getByText("A11y note").first().click();
    await expect(
      page.getByRole("button", { name: "Close preview" }),
    ).toBeVisible();
    await scan(page, "Note open");
    // The preview covers the page, "Add resource" included.
    await page.getByRole("button", { name: "Close preview" }).click();

    // A picture: the preview, and then the fullscreen viewer, which is a different surface with
    // its own zoom toolbar.
    await page.getByRole("button", { name: "Add resource" }).first().click();
    const fileSheet = page.getByRole("dialog");
    await fileSheet.getByRole("button", { name: "File" }).click();
    await fileSheet.locator('input[type="file"]').setInputFiles(SAMPLE_PNG);
    await fileSheet.getByRole("button", { name: "Add resource" }).click();
    await page.waitForLoadState("networkidle");
    await page.getByText("sample.png").first().click();

    const reset = page.getByRole("button", { name: "Reset zoom" });
    await expect(async () => {
      await page
        .getByRole("button", { name: /Open sample/ })
        .first()
        .click();
      await expect(reset).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: 20_000 });
    await scan(page, "Picture full screen");
    await page.keyboard.press("Escape");
  });

  test("the AI coach", async ({ page }) => {
    test.setTimeout(180_000);
    // A stubbed provider, so what is under test is the panel and not somebody's API key.
    await stubAiProvider(page, [{ reply: "Noted." }]);
    await createGoal(page, `A11y coach ${Date.now()}`);

    await page
      .getByRole("button", { name: /ai coach/i })
      .first()
      .click();
    await expect(
      page.getByPlaceholder("Ask, plan, or request an action…"),
    ).toBeVisible();
    await scan(page, "AI coach");
  });

  test("a phone draws different chrome, so it is scanned separately", async ({
    page,
  }) => {
    test.setTimeout(240_000);
    const title = `A11y phone ${Date.now()}`;
    await createGoal(page, title);

    await page.setViewportSize(PHONE);
    await dashboard(page);
    await scan(page, "All goals (phone)");

    // The search is a whole overlay here; on a laptop the field is simply in the header.
    await page.getByRole("button", { name: "Search goals" }).click();
    await expect(page.getByPlaceholder("Search for goals")).toBeVisible();
    await scan(page, "Header search (phone)");
    await page.keyboard.press("Escape");

    // The two panels the laptop opens at its side come up from the bottom edge here.
    await page.getByRole("button", { name: "Filter and sort goals" }).click();
    await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
    await scan(page, "Filter drawer (phone)", DIALOG);
    await page.keyboard.press("Escape");

    await page.getByRole("button", { name: "New goal" }).first().click();
    await expect(
      page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
    ).toBeVisible();
    await scan(page, "New goal drawer (phone)", DIALOG);
    await page.keyboard.press("Escape");

    await page.getByRole("link", { name: title }).first().click();
    await expect(
      page.getByRole("heading", { name: "Options", exact: true }),
    ).toBeVisible();
    await scan(page, "Goal workspace (phone)");
  });
});

/**
 * Focus order, which no automated rule can judge (WCAG 2.4.3).
 *
 * Found with a keyboard, not a scanner (owner, 2026-09-30): on the dashboard the tab order read
 * `... Calendar — "Delete goal" — "Get a job as a Software Developer"`. The destructive action
 * announced itself **before the goal it would destroy**, so a screen-reader user heard
 * "Delete goal, button" with no idea which one, and reaching the sixth goal meant passing through
 * six delete buttons.
 */
test("a goal card names the goal before it offers to delete it", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const title = `A11y order ${Date.now()}`;
  await createGoal(page, title);
  await dashboard(page);

  // Walk the page as a keyboard user does, and write down what each stop calls itself.
  //
  // **The budget has to clear the standing rail, and the rail now lists every goal** (2026-10-07).
  // It was 40, which was ample while the nav held four rows; the rail lists one row per goal now,
  // so on a database with a few dozen in it the walk ran out of presses among the goal names and
  // reported "nothing offered to delete", with the delete button sitting just past the end. That
  // is a measurement artefact, not a focus-order defect — the thing this test is actually about is
  // the ORDER of the two stops, and both have to be reached before the order can be read.
  const stops: string[] = [];
  for (let i = 0; i < 120; i++) {
    await page.keyboard.press("Tab");
    stops.push(
      await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) return "";
        return (el.getAttribute("aria-label") || el.textContent || el.tagName)
          .replace(/\s+/g, " ")
          .trim()
          .slice(0, 60);
      }),
    );
  }

  const goal = stops.findIndex((stop) => stop.includes(title));
  const remove = stops.findIndex((stop) => stop.startsWith(`Delete "${title}`));

  expect(
    goal,
    `the goal never took focus. Stops: ${stops.join(" | ")}`,
  ).toBeGreaterThanOrEqual(0);
  expect(
    remove,
    `nothing offered to delete "${title}". Stops: ${stops.join(" | ")}`,
  ).toBeGreaterThanOrEqual(0);
  // The name first, the destruction after it.
  expect(
    remove,
    `delete came before the goal's own name. Stops: ${stops.join(" | ")}`,
  ).toBeGreaterThan(goal);
});
