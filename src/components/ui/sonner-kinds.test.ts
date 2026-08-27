import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

import { NOTICE_KINDS } from "@/components/spira/Notice";

/**
 * A toast must be the same card as every other message in the app (CLAUDE.md, Design 3d):
 * the kind's tint behind the kind's ink, near-black text.
 *
 * The colours live in two places by necessity — `NOTICE_KINDS` for the in-page card, and
 * plain CSS for the toast, because sonner reads its own custom properties and a Tailwind
 * class cannot reach them reliably. This test is what keeps the two from drifting.
 *
 * It also stands for a failure that shipped twice. The wrapper used to build the classes by
 * interpolation (`` `[--normal-bg:${fill}]` ``), and Tailwind's scanner only sees literal
 * strings, so it generated no rule at all: the class was on the element, nothing was behind
 * it, and every toast silently fell back to sonner's white card with a grey hairline. Nothing
 * failed, and a screenshot could not settle it either — the correct tint is `#F9FDFC`, which
 * is white to the eye. It took `getComputedStyle` on a live toast to see it (2026-08-23).
 */
describe("toast colours match the notice table", () => {
  const css = readFileSync(
    resolve(__dirname, "../../styles.css"),
    "utf8",
  ).toLowerCase();

  const kinds = ["success", "error", "warning", "info"] as const;

  it.each(kinds)("%s uses its own tint and ink", (kind) => {
    const { ink, fill } = NOTICE_KINDS[kind];
    const block = css.match(
      new RegExp(
        `\\[data-sonner-toast\\]\\[data-type="${kind}"\\]\\s*\\{([^}]*)\\}`,
      ),
    );
    expect(block, `no CSS block for the ${kind} toast`).not.toBeNull();

    const body = block![1];
    expect(body).toContain(`--normal-bg: ${fill.toLowerCase()}`);
    expect(body).toContain(`--normal-border: ${ink.toLowerCase()}`);
    // Near-black in every kind: the border and the mark carry the meaning, not the type.
    expect(body).toContain("--normal-text: #222525");
  });

  it("never leaves a kind styled by sonner's own defaults", () => {
    // sonner's fallback is a white card with a grey hairline. If a kind's block goes
    // missing, that is what the user gets — and it looks deliberate.
    for (const kind of kinds) {
      expect(css).toContain(`[data-sonner-toast][data-type="${kind}"]`);
    }
  });
});
