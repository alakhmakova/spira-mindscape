import { test, expect } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * **A long message must not swallow the caret or the buttons** (owner, 2026-08-24 / 2026-08-25).
 *
 * Two related faults, both reported from the phone and both present on the web's own terms:
 *
 * 1. Without a height cap the field grows with the text and pushes the row below it — the
 *    paperclip, "Start GROW session", Send — out of reach, so a long message leaves nothing to
 *    press.
 * 2. With a cap but no auto-scroll you type into text you cannot see: the caret runs off the
 *    bottom of the field and you have to drag it back to find your own cursor.
 *
 * The web has capped the textarea at 128px from the start (`Math.min(el.scrollHeight, 128)`), and
 * a browser scrolls a focused textarea to its caret by itself — but only while nothing overrides
 * `overflow`. That is precisely the kind of thing a styling pass changes without noticing, which
 * is why it is asserted rather than assumed.
 */

test("a long message keeps the caret in view and the actions reachable", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await createGoal(page, `Composer ${Date.now()}`);

  await page
    .getByRole("button", { name: /ai coach/i })
    .first()
    .click();

  const panel = page.getByRole("complementary", { name: "spira ai coach" });
  const field = panel.getByPlaceholder("Ask, plan, or request an action…");
  await field.click();

  // Long enough to overflow several times over — roughly what the owner was typing.
  const essay = Array.from(
    { length: 30 },
    (_, i) => `line ${i} of a message that keeps going and going`,
  ).join(" ");
  await field.fill(essay);
  // `fill` sets the value without a caret journey; type the tail so the cursor is genuinely at
  // the end, which is the state the bug was about.
  await field.type(" and the end.");

  const box = await field.evaluate((el: HTMLTextAreaElement) => ({
    height: el.getBoundingClientRect().height,
    scrollHeight: el.scrollHeight,
    scrollTop: el.scrollTop,
    overflowY: getComputedStyle(el).overflowY,
    caret: el.selectionStart,
    length: el.value.length,
  }));

  // 1. Capped — the field never grows past the panel's share of the screen.
  expect(box.height, "the field must stay capped").toBeLessThanOrEqual(140);
  expect(
    box.scrollHeight,
    "the test text must actually overflow, or this proves nothing",
  ).toBeGreaterThan(box.height + 20);

  // 2. Scrolled to the caret — the last thing typed is what you are looking at.
  expect(box.caret, "the caret is at the end").toBe(box.length);
  expect(
    box.scrollTop,
    "the field must have scrolled down to follow the caret",
  ).toBeGreaterThan(0);
  // The bottom of the visible window is at the bottom of the content, give or take the
  // textarea's own padding — that is what "the caret is on screen" means for a caret at the end.
  expect(
    box.scrollTop + box.height,
    "and the caret's line must be inside the visible window",
  ).toBeGreaterThanOrEqual(box.scrollHeight - 16);
  expect(box.overflowY, "a hidden overflow would trap the caret").not.toBe(
    "hidden",
  );

  // 3. Everything below the field is still there to press.
  await expect(panel.getByTitle("Send")).toBeVisible();
  await expect(panel.getByRole("button", { name: "Attach" })).toBeVisible();
});
