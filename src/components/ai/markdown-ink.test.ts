import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * **The chat's Markdown is drawn for a light ground** — a convention test, in the spirit of
 * `sheet-units.test.ts` and the backend's `LoggingConventionTest`, because this is a rule no
 * assertion in this repo can currently catch.
 *
 * ## Why it exists
 *
 * The assistant's prose used to sit directly on the teal gradient, so `Markdown`'s overrides
 * were written in white: `text-white/90` for a link, `bg-white/15` behind inline code,
 * `border-white/40` on a blockquote, `border-white/20` for a rule. Then the prose moved into a
 * **`#E0F2F5` bubble** and those four overrides stayed exactly as they were.
 *
 * The result is a link that is present, underlined, and invisible — white on pale blue. The
 * owner reported it as "в чате ссылки белого цвета - ничего не видно" (2026-09-09), and every
 * existing test stayed green: the markup is correct, the accessible name is correct, the
 * element is in the DOM. Only a human looking at the pixels could see it, and only in a build.
 *
 * ## The rule
 *
 * Nothing inside `Markdown` may paint white ink or a white wash. Colours come from the palette
 * (`CLAUDE.md` → Colour), and anything that CAN inherit its ink does — `code` sets only a
 * background, `blockquote` only its rule — so neither has to know which light ground it is on.
 * If a caller on teal is ever added it takes a `tone` prop, and this test grows a case for it.
 */

const PANEL = join(process.cwd(), "src", "components", "ai", "AiPanel.tsx");

/** `text-white/90`, `bg-white/15`, `border-white/40`, `text-white` — white ink or wash. */
const WHITE =
  /(?<![\w-])(text|bg|border|fill|stroke|ring|shadow)-white(\/\d+)?\b/g;

/**
 * The body of the `Markdown` component, comments stripped.
 *
 * <p>Comments are stripped because this file's own prose names the offending classes, and a
 * doc comment must not fail the build — trap 1 in CLAUDE.md's list of ways a source scan
 * passes while the defect is present.
 */
function markdownBody(source: string): string {
  const start = source.indexOf("function Markdown(");
  expect(start, "the Markdown component was renamed or moved").toBeGreaterThan(
    -1,
  );
  // It ends at the next top-level declaration, which is the section comment after it.
  const end = source.indexOf("\n// ──", start);
  expect(
    end,
    "the Markdown component's end could not be found",
  ).toBeGreaterThan(start);
  return source
    .slice(start, end)
    .split("\n")
    .map((line) =>
      line
        .replace(/\/\/.*$/, "")
        .replace(/^\s*\*.*$/, "")
        .replace(/\/\*.*$/, ""),
    )
    .join("\n");
}

describe("the chat's Markdown paints for a light ground", () => {
  it("uses no white ink or white wash anywhere in the component", () => {
    const body = markdownBody(readFileSync(PANEL, "utf8"));

    const offenders = [...body.matchAll(WHITE)].map((m) => m[0]);

    expect(
      offenders,
      "Markdown renders inside the #E0F2F5 assistant bubble and the white resource " +
        "preview. White ink there is invisible — which is exactly how the chat shipped " +
        "with unreadable links. Take the colour from the palette, or let it inherit.",
    ).toEqual([]);
  });

  it("still paints the things that were invisible: links, code, quotes, rules", () => {
    const body = markdownBody(readFileSync(PANEL, "utf8"));

    // Kale for a link, Salt-300 behind code, Kale-300 on a quote, Salt-500 for a rule.
    expect(body).toContain("#0A8080");
    expect(body).toContain("#F4F4F3");
    expect(body).toContain("#8DD3D4");
    expect(body).toContain("#DCDCDC");
  });

  it("the scan itself bites — proved against the code that shipped broken", () => {
    // Without this the test above could be passing because the matcher is wrong. These are
    // the four overrides as they actually were.
    const asItShipped = `
      code: () => <code className="bg-white/15 rounded px-1" />,
      blockquote: () => <blockquote className="border-l-2 border-white/40 text-white/80" />,
      a: () => <a className="underline text-white/90 hover:text-white" />,
      hr: () => <hr className="border-white/20 my-3" />,
    `;

    expect([...asItShipped.matchAll(WHITE)].map((m) => m[0])).toEqual([
      "bg-white/15",
      "border-white/40",
      "text-white/80",
      "text-white/90",
      "text-white",
      "border-white/20",
    ]);
  });
});
