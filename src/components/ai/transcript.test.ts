import { describe, expect, it } from "vitest";
import { parseTranscript } from "./transcript";

/**
 * The transcript is the contract between the browser and the phone (`/api/ai/chat/transcript`),
 * and it is also what a reload reads back. Both surfaces parse it the same way.
 */
describe("parseTranscript", () => {
  it("drops a stranded session note", () => {
    // BUG-077. "Session ended…" is posted when a GROW session closes and removed a few seconds
    // later by `postSessionNote`. Close the tab inside those seconds and the note is already on
    // the server, where nothing would ever take it out again — the owner's plain chat carried one
    // for days, and it made the panel offer "New chat" over a conversation never started. The
    // timer cannot make a line ephemeral; this filter can, and it heals what is already stranded.
    const stored = JSON.stringify([
      { id: "m1", role: "user", content: "hello" },
      {
        id: "n1",
        role: "system",
        content: "Session ended. Nothing was saved.",
      },
      { id: "m2", role: "assistant", content: "hi" },
    ]);
    expect(parseTranscript(stored)?.map((m) => m.id)).toEqual(["m1", "m2"]);
  });

  it("leaves the caller holding what it had when the content is unusable", () => {
    expect(parseTranscript(null)).toBeNull();
    expect(parseTranscript("")).toBeNull();
    expect(parseTranscript("{not an array}")).toBeNull();
  });
});
