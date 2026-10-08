import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * **The body face declares a descent deep enough for its own descenders** — a convention test, in
 * the spirit of `sheet-units.test.ts`, because this is a rule no rendering test in this repo can
 * catch.
 *
 * ## What went wrong
 *
 * The owner saw the "g" of the header's `Search goals` placeholder cut off flat (2026-10-08). It
 * was not the placeholder, and it was not that field: **every `<input>` in the app** was shaving
 * its descenders.
 *
 * Chrome clips an input's text to the font's own content box — ascent + descent — and nothing in
 * CSS moves that box. Measured on the running app at `deviceScaleFactor: 8`, each against the
 * unchanged field: `line-height: 1`, `line-height: 40px`, `padding-bottom`, `height: auto` and
 * `overflow: visible` produced **byte-identical** screenshots. Raising the font size to 14.5px was
 * the only thing that changed anything, which is what pointed at the metrics rather than the box.
 *
 * Tilda Sans declares `descent = 249/1000` of the em, and draws (units below the baseline):
 *
 * | | Regular | Medium |
 * |---|---|---|
 * | `j` | 246 | **251** |
 * | `g` | 244 | 245 |
 * | `р` `у` `ф` | 240 | 240 |
 *
 * So the tail of a `g` lands on the clip line and a Medium `j` lands past it, and the rasteriser
 * chops what is left. `descent-override: 26%` is the smallest step that clears the whole family
 * (measured: the `g`'s tail tapers 33 → 11 device px instead of stopping dead at 28; 27% and 28%
 * render identically, so they buy nothing). The ascent override restates the font's own 95.1%, so
 * the line box grows by 1.1% of the em — 0.15px at 14px — which matters nowhere, because Tailwind
 * states a `line-height` on essentially every text class.
 *
 * ## Why a source check
 *
 * A headless browser renders this correctly either way at ordinary zoom: the difference is a
 * fraction of a CSS pixel and only separates at a high `deviceScaleFactor`, on a glyph the page
 * may not even be showing. This file cannot see the defect; it can refuse to let the fix be
 * deleted, which is all it does. Verified red by removing the two overrides.
 */
describe("Tilda Sans descent", () => {
  const css = readFileSync(join(process.cwd(), "src/styles.css"), "utf8");

  const faces = [...css.matchAll(/@font-face\s*\{([^}]*)\}/g)]
    .map((m) => m[1])
    .filter((body) => /font-family:\s*"Tilda Sans"/.test(body));

  it("declares both Tilda Sans faces", () => {
    expect(faces).toHaveLength(2);
  });

  for (const [weight, label] of [
    ["400", "Regular"],
    ["500 900", "Medium"],
  ]) {
    it(`gives the ${label} face room for a Medium "j" (25.1% of the em)`, () => {
      const face = faces.find((f) => f.includes(`font-weight: ${weight};`));
      expect(face, `no Tilda Sans face at font-weight: ${weight}`).toBeTruthy();

      const declared = /descent-override:\s*([\d.]+)%/.exec(face!);
      expect(declared, "the face must carry a descent-override").toBeTruthy();
      expect(Number(declared![1])).toBeGreaterThanOrEqual(26);
    });
  }
});
