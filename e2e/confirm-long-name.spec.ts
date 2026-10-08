import { test, expect } from "@playwright/test";
import { createGoal, openElementMenu } from "./helpers";

/**
 * A confirm dialog keeps its buttons inside its own card, whatever it is asked to quote.
 *
 * **What went wrong** (owner, 2026-10-05, on production): a target named after a job advert carries
 * the whole advert URL, and the delete confirm quotes the name back. The card is a `grid`, a grid
 * column sizes to `max-content` unless told otherwise, and a 130-character URL has no break
 * opportunity in it — so the column, and with it the footer, was laid out **735px wide inside a
 * card clamped to 600px**. `overflow` is visible, so the two buttons were simply drawn outside the
 * white card, on the dimmed page.
 *
 * Measured before the fix: `scrollWidth` 735 against `clientWidth` 600, description 678px, footer
 * 710px. After: 600 / 600 / 520 / 552.
 *
 * Two things were needed and neither alone is enough — `grid-cols-[minmax(0,1fr)]` on the card, so
 * the column may shrink below its content at all, and `break-words` + `overflow-wrap: anywhere` on
 * the quoted text, so it then has somewhere to break.
 */
const LONG_URL_NAME =
  "Ericsson https://jobs.ericsson.com/careers?domain=ericsson.com&jobPipeline=LinkedIn&utm_source=linkedin&start=0&pid=563121775899764&sort_by=hot";

test("a long url in a target's name does not push the confirm's buttons out of the card", async ({
  page,
}) => {
  test.setTimeout(240_000);
  await createGoal(page, `Confirm width ${Date.now()}`);

  await page.getByRole("button", { name: "Add target" }).first().click();
  const form = page.getByRole("dialog");
  await form.getByRole("button", { name: /^Binary/ }).click();
  await form.getByPlaceholder("e.g. Outbound applications").fill(LONG_URL_NAME);
  await form.getByRole("button", { name: "Add target", exact: true }).click();
  await page.waitForLoadState("networkidle");

  const row = page.getByRole("row", { name: /Ericsson/ }).first();
  await openElementMenu(page, row, "Target actions");
  await page.getByRole("menuitem", { name: "Delete target" }).first().click();
  await expect(page.getByRole("alertdialog")).toBeVisible();

  const fit = await page.evaluate(() => {
    const card = document.querySelector('[role="alertdialog"]') as HTMLElement;
    const footer = card.lastElementChild as HTMLElement;
    const cardBox = card.getBoundingClientRect();
    const footerBox = footer.getBoundingClientRect();
    return {
      overflow: card.scrollWidth - card.clientWidth,
      footerPastRight: Math.round(footerBox.right - cardBox.right),
      footerPastBottom: Math.round(footerBox.bottom - cardBox.bottom),
    };
  });

  // The card does not scroll sideways: its content fits the width it is clamped to.
  expect(
    fit.overflow,
    "the dialog's content is wider than the dialog",
  ).toBeLessThanOrEqual(1);
  // And the buttons are inside it, not drawn on the page beside or below the card.
  expect(
    fit.footerPastRight,
    "the footer sticks out to the right of the card",
  ).toBeLessThanOrEqual(0);
  expect(
    fit.footerPastBottom,
    "the footer hangs below the card",
  ).toBeLessThanOrEqual(0);

  // ── And the name of the thing being deleted is BOLD ───────────────────────
  // (owner, 2026-10-06). Android has drawn it this way all along; the web set the whole sentence
  // in one flat weight, so the target's name — here a URL longer than the sentence around it —
  // had to be found rather than read. The quotes are inside the bold, as on Android.
  //
  // This also measures the **wording**, because the fix turned a template string into JSX and JSX
  // collapses the newline between an element and the punctuation that follows it. A stray space
  // before the `?`, or a missing one after "delete", is exactly what that rewrite risks and
  // nothing else in the suite would notice.
  const quoted = page.getByRole("alertdialog").locator("strong");
  await expect(quoted).toHaveText(`"${LONG_URL_NAME}"`);
  await expect(quoted).toHaveCSS("font-weight", "600");

  const sentence = await page
    .getByRole("alertdialog")
    .locator("p, [id$='-description']")
    .first()
    .innerText();
  expect(sentence.replace(/\s+/g, " ").trim()).toBe(
    `Are you sure you want to permanently delete "${LONG_URL_NAME}"? ` +
      "Progress and checklist tasks inside it will be removed. You can't undo this.",
  );
  // The collapse above would hide a doubled space, so check the raw text for one too.
  expect(sentence, "the sentence has a doubled space in it").not.toMatch(
    / {2}/,
  );
  expect(sentence, "there is a space before the question mark").not.toMatch(
    / \?/,
  );

  await page.keyboard.press("Escape");
});
