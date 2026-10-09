import { describe, expect, it } from "vitest";
import { greeting } from "./greeting";

const at = (hour: number) => new Date(2026, 9, 9, hour, 0);

describe("greeting", () => {
  it("follows the time of day", () => {
    expect(greeting("Anastasiya", at(5))).toBe("Good morning, Anastasiya");
    expect(greeting("Anastasiya", at(11))).toBe("Good morning, Anastasiya");
    expect(greeting("Anastasiya", at(12))).toBe("Good afternoon, Anastasiya");
    expect(greeting("Anastasiya", at(17))).toBe("Good afternoon, Anastasiya");
    expect(greeting("Anastasiya", at(18))).toBe("Good evening, Anastasiya");
    expect(greeting("Anastasiya", at(2))).toBe("Good evening, Anastasiya");
  });

  it("uses the first name only", () => {
    expect(greeting("Anastasiya Lakhmakova", at(9))).toBe(
      "Good morning, Anastasiya",
    );
  });

  it("is the greeting alone without a name", () => {
    expect(greeting(undefined, at(9))).toBe("Good morning");
    expect(greeting("  ", at(9))).toBe("Good morning");
  });
});
