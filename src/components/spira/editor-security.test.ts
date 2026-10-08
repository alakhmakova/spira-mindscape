import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * **The editor stack never slides back below the versions that patched its advisories** — a
 * convention test over `package-lock.json`, in the spirit of `sheet-units.test.ts`.
 *
 * ## Why it exists
 *
 * Three advisories landed against the note editor's own dependencies (2026-10-08):
 *
 * | Package | Advisory | Patched in |
 * |---|---|---|
 * | `prosemirror-view` | **XSS in paste handling** (GHSA-c8x8-7fp4-3x9w, CVSS 8.5) — pasted HTML could run script in the page holding the editor | `1.42.3` |
 * | `@tiptap/core` | `mergeAttributes()` turns an own `__proto__` key into inherited executable DOM attributes (GHSA-cp6q-959q-f8rh) | `3.30.4` |
 * | `@tiptap/core` | Quadratic ReDoS in block and inline Markdown attribute parsing (GHSA-j95f-988m-3j2f) | `3.30.5` |
 *
 * All three are **HIGH, not CRITICAL** — and CI's supply-chain gate is deliberately
 * "block on CRITICAL, report HIGH" (`.github/workflows/ci.yml`), because the HIGH list is
 * otherwise build tooling that never ships in the bundle and is only fixable by a breaking
 * upgrade. So nothing in CI fails if these three come back. They would come back silently: both
 * packages are reached **transitively** (`prosemirror-view` through `@tiptap/pm`, which only a
 * `@tiptap/*` bump moves), so a regenerated lockfile or a resolution that pins an older
 * `@tiptap/pm` is all it takes.
 *
 * Unlike the rest of that HIGH list, these two **do** ship: they are the editor, on both surfaces
 * — the web note editor (`RichTextEditor.tsx`) and the WebView bundle the Android app runs
 * (`embeds/note-editor/`, built into `android/app/src/main/assets/note-editor/index.html`). And
 * the paste path is not hypothetical here: the toolbar has a "Paste (keep formatting)" button,
 * pasting rich text into a note is an ordinary thing to do, and a note's text is the user's own
 * writing.
 *
 * ## What it does NOT do
 *
 * It does not reproduce the XSS. The advisory does not disclose the payload, and a guessed one
 * that fails to fire proves nothing — a green "no script ran" against an unpatched version would
 * be worse than no test. A version floor is the honest check: it states exactly what is known
 * (which releases carry the fix) and fails for exactly the reason that matters (the fix left).
 *
 * **And it does not reach the phone.** Every assertion below is over `package-lock.json`, which is
 * not what the Android WebView runs — that is the committed bundle
 * `android/app/src/main/assets/note-editor/index.html`, rebuilt by `npm run build:note-editor`.
 * A patched lockfile with a stale bundle would pass this file and still ship the vulnerable code,
 * so the second half of the guard is the CI step "The committed note-editor bundle matches its
 * source" (`.github/workflows/ci.yml`), which rebuilds it and fails on any difference. Neither
 * check covers the other; keep both.
 */

type Lock = { packages?: Record<string, { version?: string }> };

/** The lowest release of each package that carries its fix. */
const FLOORS: Record<string, string> = {
  "prosemirror-view": "1.42.3",
  "@tiptap/core": "3.30.5",
  "@tiptap/pm": "3.30.5",
};

/** `1.42.6` → `[1, 42, 6]`, so the comparison is numeric and `1.42.10 > 1.42.6`. */
function parts(version: string): number[] {
  return version.split("-")[0].split(".").map(Number);
}

function atLeast(version: string, floor: string): boolean {
  const a = parts(version);
  const b = parts(floor);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] ?? 0;
    const y = b[i] ?? 0;
    if (x !== y) return x > y;
  }
  return true;
}

const lock = JSON.parse(
  readFileSync(join(process.cwd(), "package-lock.json"), "utf-8"),
) as Lock;

describe("the editor's patched dependency floors", () => {
  for (const [name, floor] of Object.entries(FLOORS)) {
    it(`resolves ${name} to >= ${floor}, everywhere in the lockfile`, () => {
      // **Every** entry, not just the top-level one: npm nests a second copy under a dependent's
      // own `node_modules/` whenever ranges conflict, and a nested old copy is still the one some
      // import gets.
      const found = Object.entries(lock.packages ?? {})
        .filter(([path]) => path.endsWith(`node_modules/${name}`))
        .map(([path, entry]) => ({ path, version: entry.version ?? "" }));

      // A floor for a package that is no longer installed is a stale rule, not a pass — if the
      // editor stack is ever swapped out, this file is part of what has to be rewritten.
      expect(
        found.length,
        `${name} is not in package-lock.json`,
      ).toBeGreaterThan(0);

      const stale = found.filter(({ version }) => !atLeast(version, floor));
      expect(
        stale,
        `${name} must be >= ${floor} (the release that patched its advisory); found ` +
          stale.map(({ path, version }) => `${version} at ${path}`).join(", "),
      ).toEqual([]);
    });
  }
});
