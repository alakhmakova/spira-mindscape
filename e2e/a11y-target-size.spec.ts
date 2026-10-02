import { test, expect, type Page } from "@playwright/test";
import { createGoal, addOptions } from "./helpers";
import { probeInteractive, type Probe } from "./a11y-helpers";

/**
 * How big the things you tap are, measured on a phone (BUG-023).
 *
 * **Read the levels before reading the numbers.** This is the one check here that is not WCAG 2.1
 * AA, and pretending otherwise would make a green run mean something it does not:
 *
 * | Criterion | Level | Size | What this spec does |
 * |---|---|---|---|
 * | 2.5.5 Target Size | **2.1 AAA** | 44×44 | **reports** — attached to the run, never fails it |
 * | 2.5.8 Target Size (Minimum) | **2.2 AA** | 24×24 | **fails**, against the baseline below |
 *
 * So the suite holds the app to the 2.2 AA floor and tells you about the 2.1 AAA target without
 * turning an aspiration into a red build. If the project ever commits to AAA, move the 44 list
 * into an expectation and the shape of this file does not change.
 *
 * **The exemptions are the standard's own**, not a convenience: a control laid out inline inside a
 * sentence (`display: inline*`) is exempt, because making the words taller is not a thing a layout
 * can do, and the resource references in running text are exactly that case.
 *
 * `npx playwright test e2e/a11y-target-size.spec.ts`
 */

const PHONE = { width: 390, height: 844 };

const AA_2_2 = 24;
const AAA_2_1 = 44;

/**
 * Targets under 24×24 that are known and not yet fixed, by `tag[role] "name"` → the surface.
 *
 * Recorded 2026-09-30. Every line is a defect; this list is meant to shrink.
 */
const SMALLER_THAN_AA: Record<string, string> = {
  // Measured 2026-09-30 on a 390px phone: **44 wide and 14 tall**. The line of text is the whole
  // target — there is no padding around it — so eight goal cards give eight instances of one
  // component. A defect, not a decision: the card has room, the link simply has no height of its
  // own (BUG-023).
  'a "Start"': "All goals — the goal card's link into the workspace",
  // The goal page's own chrome, measured the same day: every one of these is a line of text at
  // **20px tall** with no padding of its own. One component each, not twenty mistakes.
  'button "Goal"': "Goal workspace — the section navigation",
  'button "Reality"': "Goal workspace — the section navigation",
  'button "Resources"': "Goal workspace — the section navigation",
  'button "Options"': "Goal workspace — the section navigation",
  'button "Will do"': "Goal workspace — the section navigation",
  'a "Back to All goals"': "Goal workspace — the way back, above the title",
  'button "Coach"': "Goal workspace — the section's own Coach link",
};

function key(probe: Probe): string {
  return `${probe.tag}${probe.role ? `[${probe.role}]` : ""} "${probe.name}"`;
}

function tooSmall(probes: Probe[], limit: number): Probe[] {
  return probes.filter(
    (p) => !p.inline && (p.width < limit || p.height < limit),
  );
}

/** Measure one screen: fail on the 2.2 AA floor, collect the 2.1 AAA list for the report. */
async function measure(page: Page, surface: string, aaa: string[]) {
  const probes = await probeInteractive(page);
  expect(
    probes.length,
    `${surface}: nothing interactive was found at all`,
  ).toBeGreaterThan(3);

  for (const p of tooSmall(probes, AAA_2_1)) {
    aaa.push(`${surface}: ${key(p)} — ${p.width}×${p.height}`);
  }

  const under = tooSmall(probes, AA_2_2).filter(
    (p) => SMALLER_THAN_AA[key(p)] === undefined,
  );
  // Soft, so one run reports every screen rather than stopping at the first.
  expect
    .soft(
      [...new Set(under.map((p) => `${key(p)} — ${p.width}×${p.height}`))],
      `${surface}: tap targets below ${AA_2_2}×${AA_2_2} (WCAG 2.2 AA, 2.5.8).`,
    )
    .toEqual([]);
}

test("tap targets on a phone", async ({ page }) => {
  test.setTimeout(240_000);
  const title = `A11y target ${Date.now()}`;
  await createGoal(page, title);
  await addOptions(page, ["A11y target option"]);

  await page.setViewportSize(PHONE);
  const aaa: string[] = [];

  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "All goals" })).toBeVisible();
  await measure(page, "All goals", aaa);

  await page.getByRole("button", { name: "New goal" }).first().click();
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeVisible();
  await measure(page, "New goal drawer", aaa);
  await page.keyboard.press("Escape");

  await page.getByRole("link", { name: title }).first().click();
  await expect(
    page.getByRole("heading", { name: "Options", exact: true }),
  ).toBeVisible();
  await measure(page, "Goal workspace", aaa);

  // The AAA list is attached rather than asserted — see the note at the top of this file.
  await test.info().attach("targets-under-44px.txt", {
    body:
      `WCAG 2.1 AAA (2.5.5) wants 44x44. ${aaa.length} targets are smaller:\n\n` +
      aaa.join("\n") +
      "\n",
    contentType: "text/plain",
  });
});
