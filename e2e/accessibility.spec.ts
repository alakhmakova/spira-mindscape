import { test, expect, type Page } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { axeScan } from "./a11y-helpers";
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
 * Violations each surface had when it was recorded, by rule id.
 *
 * Recorded 2026-09-30 against the running app. Fix one, then lower its number here — the check
 * fails if a surface grows *more* of a rule than it is admitted to have, which is what keeps this
 * from becoming a licence.
 *
 * `color-contrast` is most of it, and it is a handful of brand pairs rather than a hundred
 * mistakes, measured 2026-09-29:
 *   white on the header's teal   #FFFFFF on #268B8A at 14px  4.08:1  (needs 4.5)
 *   `text-muted-foreground/60`   #A3A3A3 on #FFFFFF at 16px  2.52:1
 *   Guava as text on white       13px, the goal page's section nav
 * The nav is `reserved-800 #D34533` — 4.4979:1, which misses 4.5 by two thousandths, so axe still
 * counts it. In that ramp only `900 #C23928` (5.3712:1) passes at this size, and the owner reads
 * 900 as red rather than coral. Recorded, not decided (BUG-023). The header pair is why almost
 * every surface carries at least one: the **account avatar** — white initials on `bg-white/15`
 * over the teal bar — is on every screen the app has, and it is the first node axe names on
 * nearly all of them.
 *
 * **Some numbers here are ceilings rather than measurements, and each says why.** Two things make
 * a count move without anything changing in the app:
 *
 * - **the data on screen** — a Timeline row per goal with a deadline, a day cell per day of
 *   whatever month is being shown. The Calendar page is the clearest case: it reads 2 in September
 *   and 4 in October, because a month with more days from its neighbours has more muted cells.
 * - **the chrome behind an overlay.** A modal marks the page behind it `aria-hidden`, and axe
 *   then skips it — but a scan that lands before the library has done the hiding counts the app
 *   header as well, avatar and all. That is a property of when the scan runs, not of the overlay,
 *   so every surface that opens over the page allows the one node the header contributes.
 *
 * The ceilings carry headroom for those two and nothing else: every other rule on those surfaces
 * is still held at zero, which is where a genuinely new defect would show up.
 */
const ACCEPTED: Record<string, Record<string, number>> = {
  // Measured 2026-10-04, every surface, with each overlay scanned on its own.
  "All goals": { "color-contrast": 1 },
  // A ceiling: one row per goal with a deadline. Measured 5 against three goals.
  Timeline: { "color-contrast": 12 },
  "Filter panel": {},
  "New goal sheet": {},
  // A ceiling: the month grid's muted cells, which differ from month to month — measured 2 in
  // September and 4 in October, on a page nobody touched in between.
  Calendar: { "color-contrast": 8 },
  "Settings — profile": { "color-contrast": 2 },
  "Settings — fonts": { "color-contrast": 1 },
  "Settings — about": { "color-contrast": 1 },
  "Sign in": {},
  // A ceiling: the page grows with the goal's own targets, options and resources.
  "Goal workspace": { "color-contrast": 8 },
  // **The one node here is the menu's own destructive item** — "Delete option", red on white.
  // Scanning the menu alone is what made it visible: against the whole page it was lost among the
  // chrome behind, and the `aria-hidden-focus` this entry used to carry was never the menu's at
  // all (it was the page's left `<aside>`). Both are recorded in BUG-023.
  "Element menu": { "color-contrast": 1 },
  // A ceiling, for the same reason as Calendar: the day grid is what is being counted. The first
  // node is the card's own "Set deadline" — white on Kale, the known 4.08:1 pair.
  "Deadline popover": { "color-contrast": 20 },
  "New target sheet": {},
  "Add a resource sheet": {},
  // Its own heading and buttons, not the page behind.
  "Delete confirm": { "color-contrast": 3 },
  "Note open": { "color-contrast": 6 },
  "Picture full screen": { "color-contrast": 6 },
  "AI coach": { "color-contrast": 8 },
  "All goals (phone)": { "color-contrast": 1 },
  "Header search (phone)": {},
  "Filter drawer (phone)": {},
  "New goal drawer (phone)": {},
  "Goal workspace (phone)": { "color-contrast": 8 },
};

/** Run axe over this surface, holding it to what `ACCEPTED` says it may have. */
async function scan(page: Page, surface: string, within?: string) {
  await axeScan(page, surface, ACCEPTED, within);
}

/** The dashboard, loaded and settled. */
async function dashboard(page: Page) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "All goals" })).toBeVisible();
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
  const stops: string[] = [];
  for (let i = 0; i < 40; i++) {
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
