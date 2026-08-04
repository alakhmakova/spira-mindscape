import { describe, expect, it } from "vitest";

import {
  type Proposal,
  proposalFromToolArgs,
  dedupCreates,
  isOptionActivate,
  createAspects,
  createSummary,
  applyExcludedAspects,
  buildHistory,
  editDisplay,
  fmtDeadline,
  proposalContext,
} from "./proposal-logic";

// These cover the pure logic behind the AI proposal cards — the parts where bugs cost us
// real data (several creates collapsing into one, fields silently dropped, duplicate text).
// Everything here is a plain function over data, so the tests are fast and deterministic.
// Behaviour reference: specs/2026-06-07-ai-assistant-cards-and-drawers/.

// Small helper: a minimal valid Proposal we can spread over for each case.
function makeProposal(over: Partial<Proposal>): Proposal {
  return { id: "p1", kind: "new_goal", title: "X", status: "pending", ...over };
}

describe("proposalFromToolArgs — maps a tool call to a Proposal", () => {
  it("parses a new goal with confidence, deadline and description", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({
        kind: "new_goal",
        title: "Learn Spanish",
        confidence: "9",
        deadline_value: "2026-08-03",
        value: "Reach B1 by summer",
      }),
    )!;
    expect(p.kind).toBe("new_goal");
    expect(p.title).toBe("Learn Spanish");
    expect(p.confidence).toBe(9);
    expect(p.deadline).toBe("2026-08-03");
    expect(p.body).toBe("Reach B1 by summer");
  });

  it("clamps an out-of-range confidence to 1..10", () => {
    expect(
      proposalFromToolArgs(
        JSON.stringify({ kind: "new_goal", title: "G", confidence: "42" }),
      )!.confidence,
    ).toBe(10);
  });

  it("parses a numeric target (type + total/current/unit)", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({
        kind: "target",
        title: "Send applications",
        total: "20",
        current: "2",
        unit: "apps",
      }),
    )!;
    expect(p.targetType).toBe("numeric");
    expect(p.total).toBe("20");
    expect(p.current).toBe("2");
    expect(p.unit).toBe("apps");
  });

  it("parses a checklist target's items (and drops blank ones)", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({
        kind: "target",
        title: "Visit my sister",
        items: [
          { text: "Buy tickets" },
          { text: "Shop presents", done: true },
          { text: "   " },
        ],
      }),
    )!;
    expect(p.targetType).toBe("checklist");
    expect(p.items).toEqual([
      { text: "Buy tickets", done: false, deadline: undefined },
      { text: "Shop presents", done: true, deadline: undefined },
    ]);
  });

  it("marks an option that should also be made active (done flag)", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({ kind: "option", value: "Move to Berlin", done: "true" }),
    )!;
    expect(p.kind).toBe("option");
    expect(p.done).toBe(true);
    expect(isOptionActivate(p)).toBe(true);
  });

  it("carries the goal id for goal-level ops, and the subject for open_goal", () => {
    const edit = proposalFromToolArgs(
      JSON.stringify({
        kind: "edit_goal",
        id: "42",
        field: "deadline",
        value: "2026-09-05",
      }),
    )!;
    expect(edit.goalId).toBe("42");

    const open = proposalFromToolArgs(
      JSON.stringify({ kind: "open_goal", id: "7", value: "the description" }),
    )!;
    expect(open.goalId).toBe("7");
    expect(open.openSubject).toBe("the description");
  });

  it("parses a delete-by-id for a smaller item", () => {
    const p = proposalFromToolArgs(
      JSON.stringify({ kind: "delete_option", id: "opt-9" }),
    )!;
    expect(p.kind).toBe("delete_option");
    expect(p.itemId).toBe("opt-9");
  });

  it("returns undefined for invalid JSON instead of throwing", () => {
    expect(proposalFromToolArgs("not json")).toBeUndefined();
  });
});

describe("dedupCreates — keep every distinct create, drop only exact duplicates", () => {
  it("keeps three different goals (the 'create 3 goals' case)", () => {
    const creates = [
      makeProposal({ id: "a", title: "Goal 1" }),
      makeProposal({ id: "b", title: "Goal 2" }),
      makeProposal({ id: "c", title: "Goal 3" }),
    ];
    expect(dedupCreates(creates).map((p) => p.title)).toEqual([
      "Goal 1",
      "Goal 2",
      "Goal 3",
    ]);
  });

  it("drops a duplicate of the same kind + title (case/space-insensitive)", () => {
    const creates = [
      makeProposal({ id: "a", title: "Update CV" }),
      makeProposal({ id: "b", title: "  update cv " }),
    ];
    expect(dedupCreates(creates)).toHaveLength(1);
  });

  it("keeps same title across different kinds", () => {
    const creates = [
      makeProposal({ id: "a", kind: "target", title: "Apply" }),
      makeProposal({ id: "b", kind: "task", title: "Apply" }),
    ];
    expect(dedupCreates(creates)).toHaveLength(2);
  });
});

describe("createAspects — the optional fields shown as checkboxes", () => {
  it("lists confidence, deadline and description for a goal that has them", () => {
    const p = makeProposal({
      kind: "new_goal",
      confidence: 7,
      deadline: "2026-08-05",
      body: "desc",
    });
    expect(createAspects(p).map((a) => a.id)).toEqual([
      "confidence",
      "deadline",
      "description",
    ]);
  });

  it("returns [] for a bare goal (name only) → one-tap confirm card", () => {
    expect(createAspects(makeProposal({ kind: "new_goal" }))).toEqual([]);
  });

  it("the description aspect carries its body so it can be viewed at the checkbox", () => {
    const p = makeProposal({ kind: "new_goal", body: "long description" });
    expect(createAspects(p).find((a) => a.id === "description")?.body).toBe(
      "long description",
    );
  });

  it("lists deadline and 'already done' for a target", () => {
    const p = makeProposal({
      kind: "target",
      deadline: "2026-08-05",
      done: true,
    });
    expect(createAspects(p).map((a) => a.id)).toEqual(["deadline", "done"]);
  });
});

describe("applyExcludedAspects — unticked fields are dropped from what gets created", () => {
  it("strips only the excluded fields, leaving the rest", () => {
    const p = makeProposal({
      kind: "new_goal",
      confidence: 7,
      deadline: "2026-08-05",
      body: "desc",
    });
    const out = applyExcludedAspects(p, new Set(["deadline"]));
    expect(out.deadline).toBeUndefined();
    expect(out.confidence).toBe(7);
    expect(out.body).toBe("desc");
  });

  it("clears the 'done' flag when 'done' is unticked", () => {
    const out = applyExcludedAspects(
      makeProposal({ kind: "target", done: true }),
      new Set(["done"]),
    );
    expect(out.done).toBe(false);
  });

  it("returns the same object when nothing is excluded", () => {
    const p = makeProposal({ kind: "new_goal", confidence: 7 });
    expect(applyExcludedAspects(p, new Set())).toBe(p);
  });
});

describe("createSummary — the structural one-liner under a create", () => {
  it("shows the numeric measure", () => {
    expect(
      createSummary(
        makeProposal({
          kind: "target",
          targetType: "numeric",
          current: "2",
          total: "20",
          unit: "apps",
        }),
      ),
    ).toBe("2 / 20 apps");
  });

  it("shows the checklist count", () => {
    const p = makeProposal({
      kind: "target",
      targetType: "checklist",
      items: [{ text: "a", done: true }, { text: "b" }],
    });
    expect(createSummary(p)).toBe("Checklist · 1/2 done");
  });

  it("is undefined for a goal (no structural summary)", () => {
    expect(createSummary(makeProposal({ kind: "new_goal" }))).toBeUndefined();
  });
});

describe("proposalContext — what a revise sends the model", () => {
  it("keeps a description edit in full, not the card's clipped preview", () => {
    const long = "Build a weekly rhythm. ".repeat(30);
    const ctx = proposalContext(
      makeProposal({ kind: "edit", field: "description", title: long }),
    );
    // The card shows ~120 chars; the model must see all of it, or the next revise
    // re-proposes from a truncated text and the user's earlier change disappears.
    expect(ctx).toContain(long.trim());
    expect(ctx).toContain("kind: edit (description)");
  });

  it("keeps every checklist item of a target create", () => {
    const ctx = proposalContext(
      makeProposal({
        kind: "target",
        title: "Run the weekly rhythm",
        targetType: "checklist",
        items: [
          { text: "Week 1 — plan" },
          { text: "Week 2 — review", done: true },
          { text: "Week 3 — adjust", deadline: "2026-08-20" },
        ],
      }),
    );
    expect(ctx).toContain("Week 1 — plan");
    expect(ctx).toContain("Week 2 — review (done)");
    expect(ctx).toContain("Week 3 — adjust (due 2026-08-20)");
  });

  it("keeps the numeric measure, the deadline and a note body", () => {
    const numeric = proposalContext(
      makeProposal({
        kind: "target",
        title: "Run 100 km",
        targetType: "numeric",
        current: "12",
        total: "100",
        unit: "km",
        deadline: "2026-09-01",
      }),
    );
    expect(numeric).toContain("measure: 12 / 100 km");
    expect(numeric).toContain("deadline: 2026-09-01");

    const note = proposalContext(
      makeProposal({
        kind: "note",
        title: "Retro",
        body: "<p>What worked</p>",
      }),
    );
    expect(note).toContain("<p>What worked</p>");
  });

  it("omits empty fields and stays bounded", () => {
    const bare = proposalContext(
      makeProposal({ kind: "option", title: "Batch the planning" }),
    );
    expect(bare).toBe("kind: option\ntitle: Batch the planning");

    const huge = proposalContext(
      makeProposal({ kind: "note", title: "Big", body: "x".repeat(20_000) }),
    );
    expect(huge.length).toBeLessThanOrEqual(4000);
  });
});

describe("buildHistory — the transcript as the model sees it", () => {
  const msg = (role: string, content: string, extra = {}) => ({
    role,
    content,
    ...extra,
  });

  it("keeps real turns and drops notices, errors, empties and in-flight text", () => {
    expect(
      buildHistory([
        msg("user", "hi"),
        msg("assistant", "hello"),
        msg("system", "session ended"),
        msg("assistant", "boom", { error: true }),
        msg("user", "   "),
        msg("assistant", "half a repl", { streaming: true }),
      ]),
    ).toEqual([
      { role: "user", content: "hi" },
      { role: "assistant", content: "hello" },
    ]);
  });

  it("merges consecutive same-role turns", () => {
    // A cancelled or failed card revise leaves the user's request unanswered — two user
    // turns in a row, which the providers are replayed verbatim.
    expect(
      buildHistory([
        msg("user", "extend the description"),
        msg("assistant", "here it is"),
        msg("user", "make it shorter"),
        msg("user", "and in English"),
      ]),
    ).toEqual([
      { role: "user", content: "extend the description" },
      { role: "assistant", content: "here it is" },
      { role: "user", content: "make it shorter\n\nand in English" },
    ]);
  });
});

describe("editDisplay — a goal edit never stretches its card", () => {
  const long = "Build a weekly rhythm. ".repeat(30);

  it("shows a description as a one-line preview with the full text behind it", () => {
    const d = editDisplay(
      makeProposal({ kind: "edit", field: "description", title: long }),
    );
    expect(d.headline).toBe("New description");
    // Short enough to stay on a line or two — the card must not grow past the panel,
    // which is what left Accept/Dismiss unreachable.
    expect(d.detail!.length).toBeLessThanOrEqual(120);
    // …and nothing is lost: the whole text is what "Read full content" opens, and what
    // gets saved.
    expect(d.body).toBe(long);
  });

  it("collapses newlines in the preview so it stays one line", () => {
    const d = editDisplay(
      makeProposal({
        kind: "edit",
        field: "description",
        title: "First line\n\nSecond line",
      }),
    );
    expect(d.detail).toBe("First line Second line");
  });

  it("keeps a short title edit as the headline", () => {
    const d = editDisplay(
      makeProposal({
        kind: "edit",
        field: "title",
        title: "Launch Spira to 50 users",
        detail: "New title",
      }),
    );
    expect(d.headline).toBe("Launch Spira to 50 users");
    expect(d.detail).toBe("New title");
    expect(d.body).toBeUndefined();
  });

  it("clips an over-long title edit", () => {
    const d = editDisplay(
      makeProposal({ kind: "edit", field: "title", title: long }),
    );
    expect(d.headline.length).toBe(120);
    expect(d.headline.endsWith("…")).toBe(true);
  });
});

describe("isOptionActivate", () => {
  it("is true only for an option with the done flag", () => {
    expect(isOptionActivate(makeProposal({ kind: "option", done: true }))).toBe(
      true,
    );
    expect(isOptionActivate(makeProposal({ kind: "option" }))).toBe(false);
    expect(isOptionActivate(makeProposal({ kind: "target", done: true }))).toBe(
      false,
    );
  });
});

describe("fmtDeadline", () => {
  it("formats an ISO date and falls back to the raw string when unparseable", () => {
    expect(fmtDeadline("2026-08-05")).toMatch(/2026/);
    expect(fmtDeadline("")).toBe("");
    expect(fmtDeadline("not-a-date")).toBe("not-a-date");
  });
});
