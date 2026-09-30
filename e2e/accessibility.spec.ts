import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { createGoal } from "./helpers";

/**
 * The mechanical half of WCAG 2.1 AA, on the screens people actually use (BUG-023).
 *
 * **What this is not.** Automated rules cover roughly a third of the criteria: axe can see that a
 * button has no accessible name, and cannot see that the name is wrong, that the focus order is
 * nonsense, or that a menu which appears on hover is unreachable from a keyboard. Those are the
 * manual passes BUG-023 lists, and a green run here is not a claim of conformance — it is a
 * guarantee that the machine-checkable part has not got worse.
 *
 * **Why the baseline.** The app has never had an accessibility pass, so the first run found real
 * violations. Failing on all of them would make the check red forever, and a permanently red check
 * is one people learn to ignore. Each rule below is therefore accepted **per screen, with the
 * count it had when it was recorded** — a new instance of an already-known rule still fails, and
 * so does any new rule. The list is meant to shrink; every line is a defect, not a decision.
 */
const WCAG_21_AA = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

/**
 * Violations the screen had when this check was written, by rule id.
 *
 * Recorded 2026-09-29 against the running app. Fix one, then lower its number here — the check
 * fails if a screen grows *more* of a rule than it is admitted to have, which is what keeps this
 * from becoming a licence.
 */
const ACCEPTED: Record<string, Record<string, number>> = {
  // Three brand pairs, measured 2026-09-29. Each is a design decision, not a bug in one screen,
  // which is why they are listed here rather than patched in place:
  //   white on the header's teal          #FFFFFF on #268B8A at 14px  4.08:1  (needs 4.5)
  //   `text-muted-foreground/60`          #A3A3A3 on #FFFFFF at 16px  2.52:1
  //   Guava as text on white               the goal page's section nav, 13px
  // The nav is now reserved-800 #D34533 — 4.4979:1, which misses 4.5 by two thousandths, so axe
  // still counts it. In that ramp only 900 #C23928 (5.3712:1) passes at this size, and the owner
  // reads 900 as red rather than coral. Recorded, not decided (BUG-023).
  "All goals": { "color-contrast": 1 },
  "Goal workspace": { "color-contrast": 6 },
  "New goal sheet": { "color-contrast": 1 },
  "Vacancy map panel": { "color-contrast": 6 },
};

/** Run axe over the page and hold it to what that screen is allowed to have. */
async function scan(page: Page, screen: keyof typeof ACCEPTED | string) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(WCAG_21_AA)
    .analyze();

  const counted: Record<string, number> = {};
  for (const violation of violations) {
    counted[violation.id] = violation.nodes.length;
  }

  // The report is what makes a failure actionable: the rule, how many nodes, and the first
  // selector — without it a red run says only "something regressed".
  const detail = violations
    .map(
      (v) =>
        `  ${v.id} (${v.impact}) ×${v.nodes.length}\n` +
        `    ${v.help}\n` +
        `    first: ${v.nodes[0]?.target.join(" ")}`,
    )
    .join("\n");

  const accepted = ACCEPTED[screen] ?? {};
  const regressions = Object.entries(counted).filter(
    ([id, count]) => count > (accepted[id] ?? 0),
  );

  expect(
    regressions,
    `${screen}: accessibility violations beyond what is recorded in ACCEPTED.\n${detail}`,
  ).toEqual([]);
}

test("the dashboard and a goal meet the machine-checkable part of WCAG 2.1 AA", async ({
  page,
}) => {
  test.setTimeout(120_000);
  // A dashboard with nothing on it exercises the empty state, not the cards.
  await createGoal(page, `A11y ${Date.now()}`);

  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "All goals" })).toBeVisible();
  await scan(page, "All goals");

  await page.getByRole("link", { name: /A11y / }).first().click();
  await expect(
    page.getByRole("heading", { name: "Options", exact: true }),
  ).toBeVisible();
  await scan(page, "Goal workspace");
});

test("the sheets and panels that open over a page meet it too", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const title = `A11y map ${Date.now()}`;
  await createGoal(page, title);

  // A sheet is its own accessibility surface: it takes focus, it traps it, and its fields are
  // labelled separately from the page behind it.
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "New goal" }).first().click();
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeVisible();
  await scan(page, "New goal sheet");
  await page.keyboard.press("Escape");

  // The vacancy map is the newest and densest surface in the app: inline fields that are spans
  // until they are tapped, an accordion, pills, and a head full of icon-only buttons.
  await page
    .getByRole("link", { name: new RegExp(title) })
    .first()
    .click();
  await page.getByRole("button", { name: "Add resource" }).first().click();
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
  await scan(page, "Vacancy map panel");
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
  await page.goto("/");
  await page.waitForLoadState("networkidle");

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
