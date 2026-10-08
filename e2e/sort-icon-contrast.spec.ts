import { test, expect } from "@playwright/test";
import { createGoal } from "./helpers";

/**
 * Every sortable column in the targets table carries a visible sort control.
 *
 * Two things, both asked for by the owner on 2026-10-06 after looking at the header.
 *
 * **One mark for all three, and only ONE HALF of it is lit.** The sorted column used to draw a
 * single chevron while its neighbours drew the double caret, so one header read as having been
 * built by a different hand ("для date в таблице ... должно быть вверх/вниз как у target name and
 * progress"). Every column now draws the same double caret; on the sorted one the **half that
 * points the direction** takes Kale and the other stays the header's own ink ("не оба треугольника
 * должны быть зелеными, а только 1 в зависимости от порядка сортировки"). So across the whole row
 * exactly one of the six triangles is a different colour.
 *
 * **And the mark is legible.** The inactive caret was drawn with `opacity-30`, which put it at
 * **1.48:1** against the header band while its own label sat at 4.82:1. One header looked like it
 * had a control and the next looked like it had none, so the glyph could not say "you can sort by
 * this", and it failed WCAG 1.4.11, which asks 3:1 of a graphic that carries meaning. The hover
 * half of that rule, `group-hover:opacity-60`, had never fired at all: nothing up the tree carries
 * `group`.
 *
 * So this measures the thing that was wrong rather than the class that was changed: the icon's
 * real painted colour, read back through a canvas because the computed value stays in `oklch()`.
 */

const SORTABLE = ["Target Name", "Date", "Progress"];

test("every sortable header's caret is as legible as its own label", async ({
  page,
}) => {
  test.setTimeout(180_000);
  await createGoal(page, `Sort icons ${Date.now()}`);

  await page.getByRole("button", { name: "Add target" }).first().click();
  const form = page.getByRole("dialog");
  await form.getByRole("button", { name: /^Binary/ }).click();
  await form.getByPlaceholder("e.g. Outbound applications").fill("Sort icon");
  await form.getByRole("button", { name: "Add target", exact: true }).click();
  await page.waitForLoadState("networkidle");
  await expect(page.locator("thead").first()).toBeVisible();

  const headers = await page.evaluate(() => {
    // The computed colours come back as `oklch(...)`; painting each one and reading the pixel
    // back is the only way to get the sRGB the user actually sees.
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
    const band = paint(
      getComputedStyle(document.querySelector("thead")!).backgroundColor,
    );
    // Composited over the band by the icon's own opacity, so this measures what is painted —
    // a pale ink and a faded one are the same defect to the eye, and were the same defect here.
    const against = (color: string, opacity: number) => {
      const ink = paint(color);
      const seen = band.map((c, i) => opacity * ink[i] + (1 - opacity) * c) as [
        number,
        number,
        number,
      ];
      const a = luminance(seen);
      const b = luminance(band);
      return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    };

    return Array.from(document.querySelectorAll("thead th")).flatMap((th) => {
      const svg = th.querySelector("svg");
      if (!svg) return [];
      const opacity = getComputedStyle(svg).opacity;
      // The box holding the label and the caret — watched only so a frame cannot reappear on it.
      const frame = getComputedStyle(svg.parentElement!);
      // Each caret is its own <path> so the halves can be coloured apart — that is the whole
      // point of the control, so each half is measured on its own.
      const halves = Array.from(svg.querySelectorAll("path")).map((path) => {
        const ink = getComputedStyle(path).fill;
        return {
          ink,
          d: path.getAttribute("d") ?? "",
          contrast: against(ink, Number(opacity)),
        };
      });
      return [
        {
          label: (th.textContent ?? "").trim(),
          opacity,
          labelInk: getComputedStyle(th).color,
          frameWidth: frame.borderTopWidth,
          halves,
        },
      ];
    });
  });

  expect(headers.map((h) => h.label)).toEqual(SORTABLE);

  for (const h of headers) {
    // Nothing is faded. This is the regression itself: `opacity-30` on the inactive caret.
    expect.soft(h.opacity, `${h.label}: the caret is faded`).toBe("1");
    expect
      .soft(h.halves, `${h.label}: the caret is not two halves`)
      .toHaveLength(2);
    for (const half of h.halves) {
      // Every triangle is legible against the band it sits on — WCAG 1.4.11 asks 3:1 of a
      // graphic that carries meaning, and here both the shape and the colour carry some.
      expect
        .soft(
          half.contrast,
          `${h.label}: a caret half is under 3:1 on the header band`,
        )
        .toBeGreaterThanOrEqual(3);
    }
  }

  // One mark for every sortable column — this is the first thing the owner photographed.
  const shapes = headers.map((h) => h.halves.map((x) => x.d).join("|"));
  expect(
    new Set(shapes).size,
    "the sortable columns do not all draw the same glyph",
  ).toBe(1);

  // **Exactly ONE triangle in the whole row is lit, and it is the darkest thing in the band.**
  //
  // "Lit" is defined by the ink itself, not by "differs from the label", which is what this used
  // to say and what broke on 2026-10-07: the sorted column's OTHER half was then stepped back to
  // `#858585`, so two of its halves differed from the label and the count read 2. The ink is the
  // honest test — there is one near-black mark on the row and everything else is grey.
  const byDarkness = [...headers.flatMap((h) => h.halves)].sort(
    (a, b) => a.contrast - b.contrast,
  );
  const darkest = byDarkness[byDarkness.length - 1].ink;
  const lit = headers.flatMap((h) =>
    h.halves
      .filter((half) => half.ink === darkest)
      .map((half) => ({ label: h.label, d: half.d })),
  );
  expect(
    lit,
    "exactly one caret half is lit, across the whole header row",
  ).toHaveLength(1);

  // It is the sorted column's, and it is the half that points the way the sort runs. The table
  // opens on deadline ascending, so that is the upward caret — the first of the two.
  expect(lit[0].label).toBe("Date");
  const sortedColumn = headers.find((h) => h.label === "Date")!;
  expect(
    lit[0].d,
    "the lit half is not the one that points the sort direction",
  ).toBe(sortedColumn.halves[0].d);

  // **And the half that does NOT point steps back, visibly** (owner, 2026-10-07: the two were
  // "почти одного цвета"). At the header's own ink the pair differed by only 2.90:1, which is not
  // a direction anyone reads at 16px; `neutral-1000 #858585` is the lightest step that still
  // clears the 3:1 a meaningful graphic owes, so they are as far apart as the floor allows.
  const [litHalf, dimHalf] = sortedColumn.halves;
  expect(
    dimHalf.contrast,
    "the unsorted half of the sorted column is not lighter than the lit one",
  ).toBeLessThan(litHalf.contrast);
  expect(
    litHalf.contrast / dimHalf.contrast,
    "the two halves of the sorted caret are too close to tell apart",
  ).toBeGreaterThan(2);

  // Every other column keeps both halves in the header's own ink — it has no direction to show.
  for (const h of headers.filter((x) => x.label !== "Date")) {
    for (const half of h.halves) {
      expect
        .soft(half.ink, `${h.label}: an unsorted column lit a caret half`)
        .toBe(h.labelInk);
    }
  }

  // **There is no frame any more** (owner, 2026-10-06). It was a 2px box round the sorted
  // column's label; the owner brought a reference table that marks the sorted column by ink alone,
  // and a teal box beside it was the loudest thing on the page. What the frame answered — 1.4.1,
  // not by hue alone — the caret still answers: near-black against the band's own muted grey is a
  // difference in weight, not only in colour. Asserted so it does not quietly come back.
  for (const h of headers) {
    expect
      .soft(h.frameWidth, `${h.label}: the sort header grew a frame again`)
      .toBe("0px");
  }
});
