import { describe, it, expect } from "vitest";
import {
  countResourceAttachments,
  planResourceDetach,
  resourceDisplayName,
  titleFromUrl,
} from "./resources";
import { FIELD_LIMITS } from "./limits";
import type { Goal } from "./types";

function goalFixture(overrides: Partial<Goal> = {}): Goal {
  return {
    id: "goal-1",
    title: "Goal",
    description: "",
    confidence: 5,
    createdAt: "2026-07-28T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [],
    ...overrides,
  };
}

describe("titleFromUrl", () => {
  it("uses the site name, without www or the TLD", () => {
    expect(titleFromUrl("https://www.github.com/a/b?c=d")).toBe("github");
  });

  it("falls back to the raw value when it isn't a URL", () => {
    expect(titleFromUrl("not a url")).toBe("not a url");
  });
});

describe("resourceDisplayName", () => {
  it("falls back to the domain for an untitled link", () => {
    expect(
      resourceDisplayName({
        id: "1",
        type: "link",
        title: "",
        url: "https://example.com/x",
      }),
    ).toBe("example");
  });

  it("names an email contact by its address when there is no name", () => {
    expect(
      resourceDisplayName({
        id: "2",
        type: "email",
        name: "",
        email: "a@b.co",
      }),
    ).toBe("a@b.co");
  });
});

describe("planResourceDetach", () => {
  it("finds every element referencing the resource, across all kinds", () => {
    const goal = goalFixture({
      options: [
        {
          id: "o1",
          text: "Call them {{res:7}}",
          selected: false,
          status: "none",
          position: 0,
        },
        {
          id: "o2",
          text: "Nothing attached",
          selected: false,
          status: "none",
          position: 1,
        },
      ],
      reality: {
        actions: [{ id: "a1", text: "Sent {{res:7}} already" }],
        obstacles: [{ id: "b1", text: "No time" }],
      },
      targets: [
        {
          id: "t1",
          type: "checklist",
          title: "Prep {{res:7}}",
          items: [
            { id: "c1", text: "Read {{res:7}}", done: false },
            { id: "c2", text: "Book a room", done: false },
          ],
        },
      ],
    });

    const patches = planResourceDetach(goal, "7", "CV");

    expect(patches).toEqual([
      { kind: "option", optionId: "o1", text: "Call them CV" },
      {
        kind: "reality",
        realityKind: "actions",
        itemId: "a1",
        text: "Sent CV already",
      },
      { kind: "targetTitle", targetId: "t1", title: "Prep CV" },
      {
        kind: "checklist",
        targetId: "t1",
        items: [
          { id: "c1", text: "Read CV", done: false },
          { id: "c2", text: "Book a room", done: false },
        ],
      },
    ]);
    expect(countResourceAttachments(goal, "7")).toBe(4);
  });

  it("ignores elements that only reference a DIFFERENT resource", () => {
    // Regression: counting "has any token" made every attachment inflate every other
    // resource's delete warning ("attached in 2 places" for a single reference).
    const goal = goalFixture({
      options: [
        {
          id: "o1",
          text: "Apply here {{res:15}}",
          selected: false,
          status: "none",
          position: 0,
        },
      ],
      reality: {
        actions: [{ id: "a1", text: "Sent it {{res:14}}" }],
        obstacles: [],
      },
    });

    expect(countResourceAttachments(goal, "14")).toBe(1);
    expect(planResourceDetach(goal, "14", "Job ad")).toEqual([
      {
        kind: "reality",
        realityKind: "actions",
        itemId: "a1",
        text: "Sent it Job ad",
      },
    ]);
  });

  it("leaves other resources' tokens alone", () => {
    const goal = goalFixture({
      options: [
        {
          id: "o1",
          text: "{{res:7}} and {{res:8}}",
          selected: false,
          status: "none",
          position: 0,
        },
      ],
    });

    expect(planResourceDetach(goal, "7", "CV")[0]).toEqual({
      kind: "option",
      optionId: "o1",
      text: "CV and {{res:8}}",
    });
  });

  it("keeps the rewritten text inside the field limit", () => {
    const longTitle = "T".repeat(FIELD_LIMITS.resourceLabel);
    const goal = goalFixture({
      options: [
        {
          id: "o1",
          text: `${"x".repeat(FIELD_LIMITS.optionText - 20)} {{res:7}}`,
          selected: false,
          status: "none",
          position: 0,
        },
      ],
    });

    const [patch] = planResourceDetach(goal, "7", longTitle);

    expect(patch).toMatchObject({ kind: "option", optionId: "o1" });
    expect((patch as { text: string }).text.length).toBeLessThanOrEqual(
      FIELD_LIMITS.optionText,
    );
    expect((patch as { text: string }).text.endsWith("…")).toBe(true);
  });

  it("reports nothing to detach when the resource isn't referenced", () => {
    expect(countResourceAttachments(goalFixture(), "7")).toBe(0);
  });
});
