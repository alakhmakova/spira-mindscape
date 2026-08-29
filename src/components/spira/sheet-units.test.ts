import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "node:fs";
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
 * Heights come from the `.sheet-*` utilities in `styles.css`, which are built on `--app-vh` —
 * one percent of the **keyboard-free** viewport, published by `lib/spira/sheet-height.ts`:
 *
 * | Class | For |
 * |---|---|
 * | `sheet-h-92` / `sheet-h-88` | a sheet with no natural content height (the chat: a two-message conversation would be two messages tall) |
 * | `sheet-max-92` / `sheet-max-85` | everything else — sized by its content, up to that cap |
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
    expect(styles).toContain("--app-vh");
  });

  it("has no viewport-unit height outside the accepted list", () => {
    const stray = allHeights.filter((u) => !(u.key in ACCEPTED));

    expect(
      stray.map((u) => `${u.file}:${u.line} ${u.text}`),
      "A viewport unit is a percentage of the LAYOUT viewport, which the on-screen keyboard " +
        "shrinks (index.html sets `interactive-widget=resizes-content`). Use `sheet-h-92`, " +
        "`sheet-h-88`, `sheet-max-92` or `sheet-max-85` — see CLAUDE.md → Sheets → the height.",
    ).toEqual([]);
  });

  it("defines every sheet utility the components use", () => {
    const used = new Set<string>();
    for (const path of allFiles) {
      for (const m of readFileSync(path, "utf8").matchAll(
        /\bsheet-(?:max|h)-\d+\b/g,
      )) {
        used.add(m[0]);
      }
    }
    // A class Tailwind has never heard of is not an error anywhere — it simply does nothing,
    // and the sheet silently falls back to its content height.
    expect([...used].filter((c) => !styles.includes(`.${c}`))).toEqual([]);
    expect(used.size).toBeGreaterThan(2);
  });

  it("publishes --app-vh from the entry point", () => {
    // Without this call every sheet falls back to the `1vh` in the utilities' `var()`, which is
    // the exact behaviour the change exists to remove — and nothing would look broken until a
    // keyboard opened on a phone.
    expect(mainEntry).toContain("trackViewportHeight()");
  });

  it("never republishes --app-vh on a blur", () => {
    // A `focusout` listener here looks obviously right — "the field lost focus, so the keyboard
    // is gone" — and was added on exactly that reasoning. It is wrong: a blur is the keyboard
    // *starting* to go, and for a couple of hundred milliseconds `innerHeight` is still
    // keyboard-sized, so publishing then republishes the keyboard's height as the screen's.
    // Every sheet shrinks mid-tap, and because that lands between `mousedown` and `mouseup`
    // Chrome dispatches no `click` at all — the first tap on any control in a sheet, after
    // typing, did nothing but close the keyboard (BUG-064). Read the module's own note before
    // adding it back.
    const mod = readFileSync(
      join(SRC, "lib", "spira", "sheet-height.ts"),
      "utf8",
    ).replace(/\/\*[\s\S]*?\*\//g, "");
    expect(
      mod,
      "sheet-height.ts publishes --app-vh on a blur again — see the `focusout` note in that file",
    ).not.toContain("focusout");
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

  it("keeps the AI chat drawer and the key sheet at the heights the owner chose", () => {
    // Named explicitly because these two were argued over for a whole session (2026-08-28):
    // the chat drawer at 92, the key sheet a little shorter at 88 so the coach shows above it.
    // They are also the only two sheets with a FIXED height — a chat has no content height of
    // its own to be sized by.
    const ai = readFileSync(join(COMPONENT_ROOT, "ai", "AiPanel.tsx"), "utf8");
    expect(ai).toContain("sheet-h-92");
    expect(ai).toContain("sheet-h-88");
  });
});
