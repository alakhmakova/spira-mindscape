import { describe, expect, it } from "vitest";
import {
  noteEditDetail,
  parseNoteDiff,
  proposalContext,
  proposalFromToolArgs,
} from "./proposal-logic";

// An AI note edit used to replace the whole body with the model's copy of the note, so a hand
// edit made in the meantime was lost on Accept (2026-09-15). The card now says whether it ADDS or
// REWRITES, shows the server's diff, and applies through a merge on the server.
describe("edit_note proposals", () => {
  const payload = {
    kind: "edit_note",
    id: "12",
    title: "Profile",
    value: "<p>Kotlin on Android</p>",
    mode: "append_to_section",
    section: "Experience",
    baseUpdatedAt: "2026-09-15T10:02:11.123456Z",
    diff: { added: ["kotlin on android"], removed: [] },
  };

  it("carries the server's mode, section, version and diff", () => {
    const p = proposalFromToolArgs(JSON.stringify(payload))!;
    expect(p.kind).toBe("edit_note");
    expect(p.itemId).toBe("12");
    expect(p.body).toBe("<p>Kotlin on Android</p>");
    expect(p.noteMode).toBe("append_to_section");
    expect(p.noteSection).toBe("Experience");
    expect(p.baseUpdatedAt).toBe("2026-09-15T10:02:11.123456Z");
    expect(p.noteDiff).toEqual({ added: ["kotlin on android"], removed: [] });
    expect(p.detail).toBe("Adds to «Experience»");
  });

  it("an old payload without a mode is an append, never a rewrite", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({ kind: "edit_note", id: "12", value: "<p>x</p>" }),
    )!;
    expect(p.noteMode).toBe("append");
    expect(p.baseUpdatedAt).toBeUndefined();
    expect(p.detail).toBe("Adds to the end of the note");
  });

  it("says plainly when it rewrites", () => {
    expect(noteEditDetail("replace_all")).toBe("Rewrites the whole note");
    expect(noteEditDetail("replace_section", "Contact")).toBe(
      "Rewrites «Contact»",
    );
    expect(noteEditDetail("merge_sections")).toBe("Adds to several sections");
  });

  it("an empty or malformed diff shows nothing", () => {
    expect(parseNoteDiff({ added: [], removed: [] })).toBeUndefined();
    expect(parseNoteDiff("nope")).toBeUndefined();
    expect(parseNoteDiff({ added: ["a", 3, " "], removed: "x" })).toEqual({
      added: ["a"],
      removed: [],
    });
  });

  it("a revise tells the model the mode and section it is revising", () => {
    const ctx = proposalContext(proposalFromToolArgs(JSON.stringify(payload))!);
    expect(ctx).toContain("mode: append_to_section");
    expect(ctx).toContain("section: Experience");
  });
});
