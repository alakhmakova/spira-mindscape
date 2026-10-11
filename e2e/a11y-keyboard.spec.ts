import { test, expect, type Page } from "@playwright/test";
import { createGoal, addOptions } from "./helpers";
import {
  probeInteractive,
  tabThrough,
  restingStyles,
  type Probe,
  type Stop,
} from "./a11y-helpers";

/**
 * **WCAG 2.1.1 Keyboard**, **2.1.2 No Keyboard Trap**, **2.4.3 Focus Order** and **2.4.7 Focus
 * Visible** — the part of the manual keyboard pass a machine can do on its own (BUG-023).
 *
 * **What it decides, and what it cannot.** It can answer "is every control reachable with Tab
 * alone", "does focus ever get stuck", "does anything happen on screen when focus lands" and
 * "does anything use a positive tabindex" — all four are properties of the whole sequence, which
 * is why no axe rule covers them. It cannot answer whether the order is *sensible*: that
 * "Delete goal" came before the goal's own name was found by a person, and is pinned by a
 * hand-written assertion in `accessibility.spec.ts`, not by a general rule. There is no general
 * rule.
 *
 * `npx playwright test e2e/a11y-keyboard.spec.ts`
 */

/** Enough presses to walk the longest screen here twice over. */
const PRESSES = 160;

/**
 * Controls that Tab is **not** expected to reach, by `tag[role] "name"`.
 *
 * Recorded 2026-09-30. Each line is either a defect waiting to be fixed or a documented reason;
 * anything new fails. Keep the reason with the line — an unexplained entry here is indistinguishable
 * from a control nobody can use.
 */
const UNREACHABLE_BY_DESIGN: Record<string, string> = {};

/** `tag[role] "name"` — stable enough to read in a failure, loose enough to survive a restyle. */
function key(probe: Probe): string {
  return `${probe.tag}${probe.role ? `[${probe.role}]` : ""} "${probe.name}"`;
}

/**
 * Everything the probe found, minus what Tab actually reached.
 *
 * **Native radios are grouped**, because a radio group is meant to have exactly one tab stop: Tab
 * enters the group at the checked button and the arrow keys move within it. Counting the other
 * buttons as unreachable would report correct behaviour as a defect.
 */
async function unreachable(page: Page, probes: Probe[], stops: Stop[]) {
  const reached = new Set(stops.map((s) => s.id).filter(Boolean) as string[]);
  const groups = await page.evaluate(() => {
    const out: Record<string, string> = {};
    for (const el of Array.from(
      document.querySelectorAll('input[type="radio"][name][data-a11y-probe]'),
    )) {
      out[el.getAttribute("data-a11y-probe")!] =
        (el as HTMLInputElement).name || "";
    }
    return out;
  });
  const reachedGroups = new Set(
    Object.entries(groups)
      .filter(([id]) => reached.has(id))
      .map(([, name]) => name),
  );

  // **A control that swaps itself out when focus arrives counts as reached.** The inline fields
  // are a read view (`role="textbox"` span) that becomes a `<textarea>` on focus, so Tab lands on
  // an element the probe never tagged and the span looks unvisited. The name is the same on both,
  // and it is what a screen reader would call either of them.
  const reachedNames = new Set(stops.filter((s) => !s.id).map((s) => s.name));

  return probes.filter((probe) => {
    if (reached.has(probe.id)) return false;
    if (probe.name && reachedNames.has(probe.name)) return false;
    const group = groups[probe.id];
    if (group && reachedGroups.has(group)) return false;
    return !(key(probe) in UNREACHABLE_BY_DESIGN);
  });
}

/** Walk a screen and report what a keyboard cannot get to, and what shows nothing when it does. */
async function walk(page: Page, screen: string) {
  const probes = await probeInteractive(page);
  expect(
    probes.length,
    `${screen}: nothing interactive was found at all`,
  ).toBeGreaterThan(5);

  const stops = await tabThrough(page, PRESSES);
  const missed = await unreachable(page, probes, stops);

  expect(
    missed.map(key),
    `${screen}: ${missed.length} of ${probes.length} controls cannot be reached with Tab.\n` +
      missed.map((p) => `  ${key(p)} at ${p.at}`).join("\n"),
  ).toEqual([]);

  // 2.4.7: something has to change on screen when focus arrives. Comparing against the same
  // element at rest is the only way to tell a focus ring from a card's own shadow.
  const ids = [...new Set(stops.map((s) => s.id).filter(Boolean) as string[])];
  const resting = await restingStyles(page, ids);
  const invisible = stops.filter(
    (s) => s.id && resting[s.id] !== undefined && resting[s.id] === s.style,
  );

  expect(
    [...new Set(invisible.map((s) => `${s.tag} "${s.name}"`))],
    `${screen}: focus landed on these and nothing on screen changed (2.4.7).`,
  ).toEqual([]);

  return { probes, stops };
}

test("every control on the dashboard can be reached, and shows it", async ({
  page,
}) => {
  test.setTimeout(180_000);
  await createGoal(page, `A11y kb ${Date.now()}`);
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(
    page.getByRole("heading", { name: "Goals", exact: true }),
  ).toBeVisible();

  await walk(page, "All goals");
});

test("every control on a goal page can be reached, and shows it", async ({
  page,
}) => {
  test.setTimeout(240_000);
  await createGoal(page, `A11y kb goal ${Date.now()}`);
  // An option gives the page its reveal-on-hover controls — the rating badge and the ⋯ menu,
  // which are exactly the kind of control a mouse finds and a keyboard does not.
  await addOptions(page, ["A11y keyboard option"]);

  await walk(page, "Goal workspace");
});

test("nothing claims a place in the tab order with a positive tabindex", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await createGoal(page, `A11y tabindex ${Date.now()}`);

  // A positive `tabindex` moves an element ahead of everything in document order, on every screen
  // it appears on, which is the one authoring mistake 2.4.3 names outright.
  for (const [screen, url] of [
    ["All goals", "/"],
    ["Settings", "/settings"],
    ["Calendar", "/calendar"],
  ] as const) {
    await page.goto(url);
    await page.waitForLoadState("networkidle");
    const positives = await page.evaluate(() =>
      Array.from(document.querySelectorAll("[tabindex]"))
        .filter((el) => Number(el.getAttribute("tabindex")) > 0)
        .map(
          (el) =>
            `${el.tagName.toLowerCase()} tabindex=${el.getAttribute("tabindex")}`,
        ),
    );
    expect(positives, `${screen}: positive tabindex`).toEqual([]);
  }
});

test("an overlay keeps focus inside it, and Escape hands it back", async ({
  page,
}) => {
  test.setTimeout(180_000);
  await createGoal(page, `A11y trap ${Date.now()}`);
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  const trigger = page.getByRole("button", { name: "New goal" }).first();
  await trigger.click();
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeVisible();

  // 2.1.2 is about not being trapped *in the page*; a modal is the one place where keeping focus
  // in is correct, and letting it wander onto the page behind is the defect.
  const escaped: string[] = [];
  for (let i = 0; i < 30; i++) {
    await page.keyboard.press("Tab");
    const outside = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body) return null;
      if (el.closest('[role="dialog"]')) return null;
      return (
        el.getAttribute("aria-label") ||
        (el.textContent ?? "").replace(/\s+/g, " ").trim() ||
        el.tagName
      ).slice(0, 50);
    });
    if (outside) escaped.push(outside);
  }
  expect(
    [...new Set(escaped)],
    "focus left the open sheet and landed on the page behind it",
  ).toEqual([]);

  // And the way out is the keyboard's: Escape closes it and gives focus back to something real,
  // not to `<body>`, where the next Tab would start again from the top of the page.
  await page.keyboard.press("Escape");
  await expect(
    page.getByPlaceholder("e.g. Launch Spira to first 50 users"),
  ).toBeHidden();
  // Polled, not read once: the sheet unmounts with an exit animation and the library restores
  // focus as part of that, so reading `activeElement` in the same tick measures the gap rather
  // than the result.
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const el = document.activeElement as HTMLElement | null;
          if (!el || el === document.body) return "body";
          return (
            el.getAttribute("aria-label") ||
            (el.textContent ?? "").replace(/\s+/g, " ").trim() ||
            el.tagName
          ).slice(0, 50);
        }),
      {
        message:
          "Escape left focus on <body> — the next Tab would start again from the top of the page",
        timeout: 5000,
      },
    )
    .not.toBe("body");

  // The same promise for the other overlay shape — the confirm, which is an `AlertDialog` rather
  // than a `Dialog`. Both go through the same fix (`src/components/ui/restore-focus.ts`), so both
  // are checked rather than one being taken on trust.
  const remove = page.getByRole("button", { name: /^Delete "/ }).first();
  await remove.click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("alertdialog")).toBeHidden();
  await expect
    .poll(
      () =>
        page.evaluate(
          () =>
            (document.activeElement as HTMLElement | null)?.getAttribute(
              "aria-label",
            ) ?? "body",
        ),
      {
        message: "Escape on the confirm left focus on <body>",
        timeout: 5000,
      },
    )
    .toMatch(/^Delete "/);
});
