import { describe, it, expect } from "vitest";
import { FIELD_LIMITS, lengthError } from "./limits";

describe("lengthError", () => {
  it("returns null when the value is within the limit", () => {
    expect(lengthError("hello", 10, "Title")).toBeNull();
  });

  it("returns null when the value is exactly at the limit", () => {
    expect(lengthError("a".repeat(200), 200, "Title")).toBeNull();
  });

  it("returns a clear, field-specific message when over the limit", () => {
    const msg = lengthError("a".repeat(214), 200, "Target title");
    expect(msg).toBe(
      "Target title must be 200 characters or fewer (you have 214).",
    );
  });

  it("counts characters, not bytes", () => {
    // Two-character string, limit of 1 → over by one character.
    expect(lengthError("ab", 1, "Unit")).toContain("you have 2");
  });
});

describe("FIELD_LIMITS", () => {
  it("mirrors the server-side limits the UI must not exceed", () => {
    // These values are contractually tied to the backend (see limits.ts docblock).
    // If a server limit changes, this test should be updated alongside it.
    expect(FIELD_LIMITS.goalTitle).toBe(200);
    expect(FIELD_LIMITS.goalDescription).toBe(5000);
    expect(FIELD_LIMITS.targetTitle).toBe(200);
    expect(FIELD_LIMITS.targetUnit).toBe(50);
    expect(FIELD_LIMITS.optionText).toBe(500);
    expect(FIELD_LIMITS.realityText).toBe(500);
    expect(FIELD_LIMITS.checklistText).toBe(500);
    expect(FIELD_LIMITS.resourceLabel).toBe(200);
    expect(FIELD_LIMITS.resourceUrl).toBe(1000);
    expect(FIELD_LIMITS.resourceNoteBody).toBe(50000);
    expect(FIELD_LIMITS.resourcePhone).toBe(50);
  });
});
