import { describe, expect, it } from "vitest";
import {
  companyLabel,
  newComment,
  newCustomFact,
  DEFAULT_REQUIREMENT_SLOTS,
  duplicateTitle,
  emptyVacancyMap,
  isEmptyVacancyMap,
  parseVacancyMap,
  removeAt,
  requirementLabel,
  setAt,
  DEFAULT_COMPANY_SLOTS,
} from "./vacancy-map";

describe("parseVacancyMap", () => {
  it("reads nothing as an empty map \u2014 with three requirement rows to fill in", () => {
    for (const nothing of [undefined, null, "", "{}"]) {
      const map = parseVacancyMap(nothing);
      // Every branch is empty \u2026
      expect({ ...map, requirements: [] }).toEqual({
        ...emptyVacancyMap(),
        requirements: [],
      });
      // \u2026 except that the page always offers three requirements (owner, 2026-09-22).
      expect(map.requirements).toHaveLength(DEFAULT_REQUIREMENT_SLOTS);
      expect(map.requirements.every((r) => r.text === "")).toBe(true);
      // Three blank rows are still "nothing written yet".
      expect(isEmptyVacancyMap(map)).toBe(true);
    }
  });

  it("makes the FIRST requirement the important one, whatever the document says", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        requirements: [
          { id: "r1", text: "Five years of Java" },
          { id: "r2", text: "Kotlin", important: true },
        ],
      }),
    );

    // Position 0 leads the list and cannot be anything else (owner, 2026-09-22).
    expect(map.requirements[0]).toMatchObject({
      important: true,
      unmet: false,
    });
    // And no other row may claim it, or two would say they are the one that matters most.
    expect(map.requirements[1].important).toBe(false);
    expect(map.requirements).toHaveLength(DEFAULT_REQUIREMENT_SLOTS);
  });

  it("survives a corrupt document instead of throwing", () => {
    const map = parseVacancyMap("{not json");
    // Ids are minted per read, so compare everything else.
    expect({ ...map, requirements: [] }).toEqual({
      ...emptyVacancyMap(),
      requirements: [],
    });
    expect(isEmptyVacancyMap(map)).toBe(true);
  });

  it("reads a half-written document — the normal state while an agent fills it", () => {
    const map = parseVacancyMap(
      JSON.stringify({ facts: { location: "Stockholm" } }),
    );

    expect(map.facts.location).toBe("Stockholm");
    // Everything the agent has not reached yet still reads as empty, not as undefined.
    expect(map.facts.language).toBe("");
    expect(map.skills).toEqual([]);
    expect(map.company.name).toBe("");
  });

  it("ignores fields of the wrong type rather than propagating them", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        facts: { location: 42, deadline: "" },
        skills: "not an array",
        requirements: [{ text: "Java", important: "yes" }],
      }),
    );

    expect(map.facts.location).toBe("");
    expect(map.facts.deadline).toBeNull();
    expect(map.skills).toEqual([]);
    // "yes" is not `true`; the SECOND row shows it, because the first is always important.
    expect(map.requirements[1].important).toBe(false);
  });

  it("gives a requirement its company slots when the document has none", () => {
    const map = parseVacancyMap(
      JSON.stringify({ requirements: [{ id: "r1", text: "Java" }] }),
    );

    expect(map.requirements[0].companies).toHaveLength(DEFAULT_COMPANY_SLOTS);
  });

  it("keeps the company answers a document does have", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        requirements: [
          {
            id: "r1",
            text: "Java",
            companies: [{ id: "c1", text: "Built X" }],
          },
        ],
      }),
    );

    expect(map.requirements[0].companies).toHaveLength(1);
    expect(map.requirements[0].companies[0].text).toBe("Built X");
  });

  it("mints an id for an item stored without one, so React keys stay stable", () => {
    const map = parseVacancyMap(JSON.stringify({ skills: [{ text: "Java" }] }));

    expect(map.skills[0].id).toBeTruthy();
  });
});

describe("reading the same document twice", () => {
  /** What the CV writer leaves behind: rows with text and no id of their own. */
  const written = JSON.stringify({
    requirements: [
      { text: "Bygga interna system" },
      { text: "Arkitektur", companies: [{ text: "På Squidler" }] },
    ],
    skills: [{ text: "Java" }],
    additional: [{ text: "Litet team" }],
    company: { comments: [{ text: "V\u00e4xer snabbt" }] },
  });

  function ids(json: string) {
    const map = parseVacancyMap(json);
    return [
      ...map.requirements.map((item) => item.id),
      ...map.requirements.flatMap((item) => item.companies.map((c) => c.id)),
      ...map.skills.map((item) => item.id),
      ...map.additional.map((item) => item.id),
      ...map.company.comments.map((item) => item.id),
    ];
  }

  it("gives every row the same id both times", () => {
    // **The map is re-read after every write, and a write happens on every keystroke** in an
    // answer box. Minting an id for a row the document does not carry one for therefore re-keyed
    // that row on each letter: React threw the row away and built a new one, which shut the open
    // requirement and took the caret with it (owner, 2026-09-29).
    expect(ids(written)).toEqual(ids(written));
  });

  it("names a row with no id of its own after its position", () => {
    const map = parseVacancyMap(written);

    expect(map.requirements[0].id).toBe("at:requirements/0");
    expect(map.requirements[1].companies[0].id).toBe(
      "at:requirements/1/companies/0",
    );
    // The third row is the padding the page always offers, and it is not in the document either.
    expect(map.requirements[2].id).toBe("at:requirements/2");
    expect(map.skills[0].id).toBe("at:skills/0");
    expect(map.company.comments[0].id).toBe("at:company/comments/0");
  });

  it("keeps the id the document DOES carry", () => {
    const map = parseVacancyMap(
      JSON.stringify({ requirements: [{ id: "r1", text: "Kotlin" }] }),
    );

    expect(map.requirements[0].id).toBe("r1");
  });
});

describe("companyLabel", () => {
  it("numbers an unnamed company by its position", () => {
    expect(companyLabel({ id: "a", label: "", text: "" }, 0)).toBe("Company 1");
    expect(companyLabel({ id: "b", label: "   ", text: "" }, 2)).toBe(
      "Company 3",
    );
  });

  it("uses the real employer once one is typed", () => {
    expect(companyLabel({ id: "a", label: "Advania", text: "" }, 0)).toBe(
      "Advania",
    );
  });
});

describe("requirementLabel", () => {
  it("numbers from the row after the important one, so the list starts at 1", () => {
    // Position 0 wears "Very important!" and no number, so row 1 is Requirement 1 — the list
    // used to start at 2, which read as a missing row (owner, 2026-09-23).
    expect(requirementLabel(1)).toBe("Requirement 1");
    expect(requirementLabel(4)).toBe("Requirement 4");
  });
});

describe("the fields the CV process added later", () => {
  it("reads a requirement's three marks, and defaults both flags to off", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        requirements: [
          { id: "r1", text: "Java", important: true },
          { id: "r2", text: "A driving licence", unmet: true },
          { id: "r3", text: "Kotlin" },
        ],
      }),
    );

    expect(map.requirements[0]).toMatchObject({
      important: true,
      unmet: false,
    });
    expect(map.requirements[1]).toMatchObject({
      important: false,
      unmet: true,
    });
    expect(map.requirements[2]).toMatchObject({
      important: false,
      unmet: false,
    });
  });

  it("ignores an `unmet` that is not a boolean", () => {
    const map = parseVacancyMap(
      JSON.stringify({ requirements: [{ text: "Java", unmet: "yes" }] }),
    );

    expect(map.requirements[0].unmet).toBe(false);
  });

  it("reads the job title, and an old document without one reads as blank", () => {
    expect(
      parseVacancyMap(
        JSON.stringify({ facts: { jobTitle: "Backend developer" } }),
      ).facts.jobTitle,
    ).toBe("Backend developer");
    expect(parseVacancyMap(JSON.stringify({ facts: {} })).facts.jobTitle).toBe(
      "",
    );
  });

  it("keeps the employer a comment was made about", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        skills: [
          {
            id: "s1",
            text: "Java",
            comments: [
              {
                id: "c1",
                text: "Used it daily",
                at: "2026-09-20",
                company: "Advania",
              },
              { id: "c2", text: "No employer named" },
            ],
          },
        ],
      }),
    );

    expect(map.skills[0].comments[0].company).toBe("Advania");
    // A comment written before the field existed reads as blank, never as undefined — the
    // card prints `at <company>:` only when there is one.
    expect(map.skills[0].comments[1].company).toBe("");
  });
});

describe("newComment", () => {
  it("carries the employer when one is given, and blank when it is not", () => {
    expect(newComment("Used it daily", "Advania")).toMatchObject({
      text: "Used it daily",
      company: "Advania",
    });
    expect(newComment("Used it daily")).toMatchObject({ company: "" });
  });

  it("stamps itself, so a thread can be read in order", () => {
    expect(Date.parse(newComment("x").at)).not.toBeNaN();
  });
});

describe("patch ops", () => {
  it("JSON-encodes the value so callers never hand-build JSON", () => {
    expect(setAt("/facts/location", "Stockholm")).toEqual({
      path: "/facts/location",
      value: '"Stockholm"',
    });
    expect(setAt("/requirements/0/important", true)).toEqual({
      path: "/requirements/0/important",
      value: "true",
    });
  });

  it("removes with a null value", () => {
    expect(removeAt("/skills/2")).toEqual({ path: "/skills/2", value: null });
  });
});

describe("isEmptyVacancyMap", () => {
  it("is true for a fresh map", () => {
    expect(isEmptyVacancyMap(emptyVacancyMap())).toBe(true);
  });

  it("is false once any single field is filled", () => {
    const map = emptyVacancyMap();
    map.facts.location = "Stockholm";
    expect(isEmptyVacancyMap(map)).toBe(false);
  });

  it("is false once a list has an item", () => {
    const map = emptyVacancyMap();
    map.skills.push({ id: "a", text: "", checked: false, comments: [] });
    expect(isEmptyVacancyMap(map)).toBe(false);
  });

  it("ignores a field she added and walked away from", () => {
    const map = emptyVacancyMap();
    map.facts.custom.push(newCustomFact());
    expect(isEmptyVacancyMap(map)).toBe(true);
  });

  it("counts a field of her own the moment it has a name", () => {
    const map = emptyVacancyMap();
    map.facts.custom.push({ ...newCustomFact(), label: "Salary" });
    expect(isEmptyVacancyMap(map)).toBe(false);
  });
});

describe("a field the user names herself", () => {
  it("is born blank, name included", () => {
    expect(newCustomFact()).toMatchObject({ label: "", value: "", note: "" });
    expect(newCustomFact().id).not.toBe(newCustomFact().id);
  });

  it("is read back from the document, and rubbish in it reads as blank", () => {
    const map = parseVacancyMap(
      JSON.stringify({
        facts: {
          custom: [
            { id: "c1", label: "Salary", value: "55k", note: "before tax" },
            { label: 7, value: null },
          ],
        },
      }),
    );

    expect(map.facts.custom[0]).toMatchObject({
      id: "c1",
      label: "Salary",
      value: "55k",
      note: "before tax",
    });
    expect(map.facts.custom[1]).toMatchObject({
      label: "",
      value: "",
      note: "",
    });
    // A document written before these existed reads as none, never as undefined.
    expect(parseVacancyMap("{}").facts.custom).toEqual([]);
  });
});

describe("duplicateTitle", () => {
  it("adds 'copy'", () => {
    expect(duplicateTitle("Backend developer", [])).toBe(
      "Backend developer copy",
    );
  });

  it("numbers the second copy rather than repeating the first name", () => {
    expect(
      duplicateTitle("Backend developer", [
        "Backend developer",
        "Backend developer copy",
      ]),
    ).toBe("Backend developer copy 2");
  });

  it("names an untitled map", () => {
    expect(duplicateTitle("   ", [])).toBe("Untitled vacancy copy");
  });
});
