import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { ARTWORK_CREDITS } from "./licences";

/**
 * A CC BY drawing that ships without its line in Settings → Licences is a licence breach, and the
 * favicon is the one drawing that is not a React component — so nothing else would notice it
 * changing hands. The favicon names its source in its own comment; that source must be credited.
 */
describe("ARTWORK_CREDITS", () => {
  const favicon = readFileSync(
    path.resolve(__dirname, "../../../public/favicon.svg"),
    "utf8",
  );

  it("credits the drawing the favicon is made from", () => {
    const source = /"(\w+) by ([^"]+?) from Noun Project"/.exec(favicon);
    expect(source, "favicon.svg must name its source").not.toBeNull();
    const [, work, author] = source!;
    expect(ARTWORK_CREDITS).toContainEqual(
      expect.objectContaining({ work, author, licence: "CC BY 3.0" }),
    );
  });

  it("gives every credit the licensor's https link", () => {
    for (const credit of ARTWORK_CREDITS)
      expect(credit.href).toMatch(/^https:\/\/thenounproject\.com\//);
  });
});
