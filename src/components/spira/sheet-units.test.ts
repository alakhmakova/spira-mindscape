import { describe, it, expect } from "vitest";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join, sep } from "node:path";

/**
 * **A sheet never states its own height** — a convention test, in the spirit of the backend's
 * `LoggingConventionTest`, because this is a rule no ordinary test can catch.
 *
 * ## Why it exists
 *
 * Sheets were sized with `h-[92vh]`, which reads as "92 % of the screen" and is not. `index.html`
 * declares `interactive-widget=resizes-content`, so the on-screen keyboard shrinks the **layout
 * viewport** — and `vh`, `dvh` and `svh` are all percentages of that viewport. With a keyboard up,
 * `92vh` on the owner's phone is ~395 px of an 888 px screen: the collapsed chat drawer, the
 * collapsed key sheet, and the New-target sheet that shrank the moment tasks were typed into it.
 * The sheets that never misbehaved — filter and sort — are exactly the ones with nothing to type
 * into.
 *
 * Measured on the device, 2026-08-28 (`public/viewport-check.html`): `92vh` = 817 px whether
 * Chrome's toolbar is showing or hidden, `92dvh` = 817/765, `92svh` = 765. **No unit can produce
 * half a screen** — which is what finally ruled the unit out as the cause and pointed at the
 * keyboard.
 *
 * Four rounds of automated checks proved nothing before that, because a headless browser has no
 * keyboard: there every unit measures a correct 92 %. A test that cannot observe the difference
 * can still refuse to let it be written, and that is all this file does.
 *
 * ## The rule
 *
 * Heights come from the `.sheet-*` utilities in `styles.css`, and every one of them states a
 * **constant top edge** rather than a percentage: `calc(100dvh - var(--sheet-top-gap))`. The
 * bottom is the viewport's bottom, so the keyboard shrinks a sheet from below and its top edge
 * never moves — which is the whole point (owner, 2026-08-29: "все должно быть стабильно, без
 * скачков"). `--app-vh` and `lib/spira/sheet-height.ts` were retired with the last percentage.
 *
 * | Class | For |
 * |---|---|
 * | `sheet-h` | a sheet with no natural content height (the chat: a two-message conversation would be two messages tall) |
 * | `sheet-max` | everything else — sized by its content, up to that cap |
 * | `sheet-inset` | a sheet stacked INSIDE another one; a percentage of its parent, so it cannot outgrow it |
 */

const SRC = join(process.cwd(), "src");
/** Windows writes `spira\Targets.tsx`; the keys below are written with forward slashes. */
const COMPONENT_ROOT = join(SRC, "components");
/** Sheets live under `components`, but nothing stops one being written in a route. */
const SCAN_ROOTS = [COMPONENT_ROOT, join(SRC, "routes")];

/** Every `.tsx` under `src/components`, which is where all sheets live. */
function componentFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) return componentFiles(path);
    return path.endsWith(".tsx") ? [path] : [];
  });
}

/** `h-[92dvh]`, `min-h-[40vh]`, `max-h-[100svh]`, … — a Tailwind height in a viewport unit. */
/**
 * A Tailwind height in a viewport unit, in BOTH spellings:
 *
 * - the arbitrary one — `h-[92dvh]`, `max-h-[100svh]`;
 * - the **built-in** one — `h-screen`, `max-h-dvh`. Same trap: they are
 *   percentages of the same layout viewport the keyboard shrinks, and a scan that knew only
 *   the bracketed form would let `<DrawerContent className="h-dvh">` through, green.
 *
 * `min-h-*` is deliberately NOT matched: a floor cannot make a sheet short, and every use of it
 * in the app is a page wrapper that should fill the window.
 */
const HEIGHT =
  /(?<![\w-])((?:max-)?h)-(?:\[(\d+)(?:dvh|svh|lvh|vh)\]|(screen|dvh|svh|lvh|vh))\b/g;

/**
 * The heights that may stay in a viewport unit, each with the reason. Same shape — and same
 * intent — as `LoggingConventionTest`'s `ACCEPTED` map: adding an entry is the last resort and
 * it must carry its reasoning.
 */
/** Both of these are `md:`/`lg:`-only sticky columns — desktop has no on-screen keyboard. */
const DESKTOP_COLUMN =
  "Not a sheet: a sticky full-height column shown only from the `md`/`lg` breakpoint up, where " +
  "there is no on-screen keyboard to shrink the viewport. It SHOULD track the window.";

const ACCEPTED: Record<string, string> = {
  "components/spira/Resources.tsx h-[100dvh]":
    "The note editor is full-screen, and full-screen means 'match what is visible' — including " +
    "while the keyboard is up, when the editor SHOULD occupy only the space above it. `dvh` is " +
    "the unit that does that; `100vh` would push its toolbar under Chrome's chrome.",
  "components/spira/Resources.tsx max-h-[100dvh]": "Same surface, same reason.",
  "components/ai/AiPanel.tsx h-screen": DESKTOP_COLUMN,
  "components/ai/AiPanel.tsx max-h-screen": DESKTOP_COLUMN,
  "components/shell/SideNav.tsx h-screen": DESKTOP_COLUMN,
};

type Use = { file: string; line: number; text: string; key: string };

function heightsIn(path: string): Use[] {
  const uses: Use[] = [];
  readFileSync(path, "utf8")
    .split("\n")
    .forEach((line, i) => {
      // Prose in a comment is not a declaration — this file and its neighbours discuss the
      // units by name, and a doc comment must not fail the build.
      const code = line.replace(/\/\/.*$/, "").replace(/^\s*\*.*$/, "");
      for (const m of code.matchAll(HEIGHT)) {
        const file = path
          .slice(SRC.length + 1)
          .split(sep)
          .join("/");
        uses.push({ file, line: i + 1, text: m[0], key: `${file} ${m[0]}` });
      }
    });
  return uses;
}

const allFiles = SCAN_ROOTS.flatMap(componentFiles);
const allHeights = allFiles.flatMap(heightsIn);
const styles = readFileSync(join(SRC, "styles.css"), "utf8");
const mainEntry = readFileSync(join(SRC, "main.tsx"), "utf8");

describe("sheets are sized from the keyboard-free viewport, not from `vh`", () => {
  it("scans the components at all, so a passing run means something", () => {
    // If a refactor moves the sheets somewhere else, this test would otherwise pass by
    // scanning nothing — the failure mode every source-scanning check has.
    expect(allFiles.length).toBeGreaterThan(20);
    expect(styles).toContain("--sheet-top-gap");
  });

  it("has no viewport-unit height outside the accepted list", () => {
    const stray = allHeights.filter((u) => !(u.key in ACCEPTED));

    expect(
      stray.map((u) => `${u.file}:${u.line} ${u.text}`),
      "A viewport unit is a percentage of the LAYOUT viewport, which the on-screen keyboard " +
        "shrinks (index.html sets `interactive-widget=resizes-content`), so a sheet written in " +
        "one cannot hold its top edge still. Use `sheet-h`, `sheet-max` or `sheet-inset` — see " +
        "CLAUDE.md → Sheets → the height.",
    ).toEqual([]);
  });

  it("defines every sheet utility the components use", () => {
    const used = new Set<string>();
    for (const path of allFiles) {
      for (const m of readFileSync(path, "utf8").matchAll(
        /\bsheet-(?:h|max|inset)\b/g,
      )) {
        used.add(m[0]);
      }
    }
    // A class Tailwind has never heard of is not an error anywhere — it simply does nothing,
    // and the sheet silently falls back to its content height.
    expect([...used].filter((c) => !styles.includes(`.${c}`))).toEqual([]);
    // Three of them: `sheet-h`, `sheet-max`, `sheet-inset`.
    expect(used.size).toBe(3);
  });

  it("states a constant top edge, never a percentage of the viewport", () => {
    // The three failed shapes, in order: `92vh` (92 % of what the keyboard left — a third of the
    // screen), `min(92 * --app-vh, 100dvh)` (with a keyboard the second term won at 100 %, so the
    // sheet's top edge landed on the screen's), and `min(92 * --app-vh, 92dvh)` (8 % of 888 is
    // 71 px, 8 % of 430 is 34, so the top edge jumped 37 px on every focus and began covering the
    // app header). Every one of them was a percentage of a viewport the keyboard resizes.
    //
    // `calc(100dvh - var(--sheet-top-gap))` cannot move: the bottom is the viewport's bottom and
    // the top is a constant below its top, so the keyboard shrinks the sheet from below only.
    for (const cls of ["sheet-h", "sheet-max"]) {
      const rule = styles.slice(styles.indexOf(`.${cls} {`));
      expect(
        rule.slice(0, rule.indexOf("}")),
        `.${cls} must be sized as calc(100dvh - var(--sheet-top-gap))`,
      ).toContain("calc(100dvh - var(--sheet-top-gap))");
    }
  });

  it("has no viewport-height module left to go stale", () => {
    // `lib/spira/sheet-height.ts` published `--app-vh`, one percent of the keyboard-free
    // viewport, so a sheet could be a stable percentage of the SCREEN. Two defects came out of
    // it: it republished on `focusout` — the instant the keyboard starts leaving but is still
    // there — which shrank every sheet mid-tap and cost the first tap on any control in a sheet
    // (BUG-064); and even correct, a percentage still moved the top edge when the keyboard
    // opened. A constant top gap needs no module at all, and the module is gone. Bringing it
    // back means bringing back a percentage, which is the thing that never worked.
    expect(existsSync(join(SRC, "lib", "spira", "sheet-height.ts"))).toBe(
      false,
    );
    expect(mainEntry).not.toContain("trackViewportHeight");
  });

  it("keeps vaul's `repositionInputs` switched off", () => {
    // The height utilities above are only as good as their being the thing that actually sizes
    // the sheet — and for a whole session they were not (2026-08-29). vaul's `repositionInputs`
    // is on by default and writes an **inline** `height` onto the drawer whenever the visual
    // viewport resizes with something typeable focused, which beats every class. Measured on a
    // 412x780 phone: 718 px at rest, 300 px with the keyboard up, and **still 300 px** once the
    // keyboard closed. That is why four rounds of CSS fixes changed nothing.
    //
    // `e2e/ai-drawer-height.spec.ts` is the real guard; this one is here because the E2E does
    // not run in the `Stop` hooks, and a `npm i vaul@latest` that resets the default would
    // otherwise reach a phone before anything said a word.
    const drawer = readFileSync(
      join(COMPONENT_ROOT, "ui", "drawer.tsx"),
      "utf8",
    );
    expect(
      drawer.replace(/\s+/g, " "),
      "vaul must not reposition inputs — with `interactive-widget=resizes-content` the browser " +
        "already sits the sheet above the keyboard, and vaul's inline height overrides " +
        "`sheet-h-*`. See the comment on `Drawer` in drawer.tsx.",
    ).toContain("repositionInputs={false}");
  });

  it("keeps the AI chat drawer and the key sheet on their own utilities", () => {
    // The chat has no content height of its own — a two-message conversation would be a
    // two-message-tall drawer — so it is the one surface that must state a height. The key sheet
    // is `absolute inset-0` INSIDE it, so it is sized against its parent: a viewport-based height
    // there could outgrow the sheet it is stacked on.
    const ai = readFileSync(join(COMPONENT_ROOT, "ai", "AiPanel.tsx"), "utf8");
    expect(ai).toContain("sheet-h");
    expect(ai).toContain("sheet-inset");
  });
});
