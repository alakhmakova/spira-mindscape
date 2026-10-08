import { test, expect } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * The goal page's section nav says where you are with a SHAPE, not only a hue.
 *
 * **What it answers** (owner, 2026-09-30): the row marked the current section with Guava type
 * and a weight step, and nothing else — WCAG 1.4.1 (Use of Color, level A). The weight was not
 * even a second signal: the brand face has no file above Medium, so 500/600/700 render
 * identically. The shape is an **underline under the word** on the current item (owner,
 * 2026-10-08, after a dark band, a filled chip and a full-width tab border were each tried and
 * dropped); `#1E7676` on a white row, with every other label `#56514E` and no decoration.
 *
 * **And it has to keep fitting.** Five labels plus padding once pushed the row off a 390px
 * screen — "Will do" wrapped onto a second line with its tail cut. A row that scrolls inside
 * itself is survivable; a page that scrolls sideways is WCAG 1.4.10, so both are measured here.
 */
const ROW = "div.sticky.top-16";

test("the current section is underlined, and the row fits a phone", async ({
  page,
}) => {
  test.setTimeout(240_000);
  await createGoal(page, `Section nav ${Date.now()}`);

  const row = page.locator(ROW).first();
  await expect(row).toBeVisible();

  const read = () =>
    page.evaluate((sel) => {
      const bar = document.querySelector(sel)!;
      const strip = bar.querySelector("div > div.flex")!;
      const rowBox = strip.getBoundingClientRect();
      // The colours come back as `oklch(...)`; painting one and reading the pixel back is the
      // only way to measure what is actually on the screen.
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d")!;
      const paint = (color: string) => {
        ctx.clearRect(0, 0, 1, 1);
        ctx.fillStyle = color;
        ctx.fillRect(0, 0, 1, 1);
        const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data;
        return [r / 255, g / 255, b / 255] as [number, number, number];
      };
      const luminance = ([r, g, b]: [number, number, number]) => {
        const lin = (v: number) =>
          v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
      };
      const page_ = paint(getComputedStyle(document.body).backgroundColor);
      const ratio = (
        a: [number, number, number],
        b: [number, number, number],
      ) => {
        const la = luminance(a);
        const lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
      };
      // Everything in this row sits on the page's own white — there is no fill any more.
      const contrast = (color: string) => ratio(paint(color), page_);
      return {
        rowOverflow: strip.scrollWidth - strip.clientWidth,
        pageOverflow:
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
        rowHeight: Math.round(rowBox.height),
        items: Array.from(strip.querySelectorAll("button")).map((b) => {
          const cs = getComputedStyle(b);
          const box = b.getBoundingClientRect();
          return {
            label: (b.textContent ?? "").trim(),
            // The mark is a text decoration, so it is the WORD's line, not the item's edge.
            underlined: cs.textDecorationLine.includes("underline"),
            // ...and the browser's own, which is what makes it read as a link rather than as a
            // tab strip's rule. See the assertion below for the measurement behind it.
            thickness: cs.textDecorationThickness,
            offset: cs.textUnderlineOffset,
            ruleContrast: contrast(cs.color),
            textContrast: contrast(cs.color),
            // Nothing in this row may carry a border: a bordered item spans its padding too,
            // which is a tab strip rather than an underlined label.
            borderWidth: cs.borderBottomWidth,
            // Nothing in the row may carry a fill: a tinted item would be the chip coming back.
            fill: cs.backgroundColor,
            height: Math.round(box.height),
          };
        }),
      };
    }, ROW);

  const wide = await read();

  // Nothing is marked with a box, so marking one cannot nudge the others sideways or down.
  for (const item of wide.items) {
    expect
      .soft(
        item.borderWidth,
        `${item.label}: the mark is a border, not the word's own rule`,
      )
      .toBe("0px");
    expect
      .soft(
        item.fill,
        `${item.label}: the row is white — no item carries a fill`,
      )
      .toBe("rgba(0, 0, 0, 0)");
    expect
      .soft(item.height, `${item.label}: the item is not the row's height`)
      .toBe(wide.rowHeight);
  }

  // Exactly one is marked, and the mark is a rule — something a reader who cannot tell the hue
  // apart can still see.
  const marked = wide.items.filter((i) => i.underlined);
  expect(
    marked.map((i) => i.label),
    "exactly one section is marked as current",
  ).toHaveLength(1);

  // The rule is drawn in the word's own ink and identifies a state, so it owes 3:1 (WCAG
  // 1.4.11) as well as the 4.5:1 the word owes; `#1E7676` is 5.37:1 on white and clears both.
  expect(
    marked[0].ruleContrast,
    "the current section's rule is under 3:1 against the page",
  ).toBeGreaterThanOrEqual(3);

  // **The decoration is the browser's own.** Measured against the owner's reference link
  // (2026-10-08), both at a cap height of 11px: her line is 1px thick and sits one clear pixel
  // under the baseline, and `auto`/`auto` draw exactly that — `decoration-2` with
  // `underline-offset-[7px]`, which this carried for a day, put a 2px rule seven pixels down.
  // Held as a source-level fact because the difference is a couple of pixels on one glyph row:
  // visible side by side with the reference, invisible to any assertion about the rendered box.
  expect(
    [marked[0].thickness, marked[0].offset],
    "the underline states its own thickness or offset instead of the browser's",
  ).toEqual(["auto", "auto"]);

  // And every word owes 4.5:1 (1.4.3) — `#1E7676` 5.37:1, `#56514E` 7.83:1. Measured on all of
  // them, because the muted label is the one a palette change is most likely to lighten.
  for (const item of wide.items) {
    expect
      .soft(item.textContrast, `${item.label}: the word is under 4.5:1`)
      .toBeGreaterThanOrEqual(4.5);
  }

  // ── and it still fits ─────────────────────────────────────────────────────
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await page.waitForTimeout(400);
    const narrow = await read();
    // No label wraps: every item is one line, the height of the row.
    for (const item of narrow.items) {
      expect
        .soft(item.height, `${width}px — ${item.label} wrapped onto two lines`)
        .toBe(narrow.rowHeight);
    }
    // The page never scrolls sideways. The strip itself may, at the narrowest width.
    expect
      .soft(narrow.pageOverflow, `${width}px — the PAGE scrolls sideways`)
      .toBeLessThanOrEqual(0);
    if (width >= 390) {
      expect
        .soft(narrow.rowOverflow, `${width}px — the nav row does not fit`)
        .toBeLessThanOrEqual(0);
    }
  }
});
