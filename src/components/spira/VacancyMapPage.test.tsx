import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { VacancyMapPanel } from "./VacancyMapPage";
import { useSpira } from "@/lib/spira/store";
import type { Goal } from "@/lib/spira/types";
import {
  emptyVacancyMap,
  parseVacancyMap,
  serializeVacancyMap,
  type MapPatchOp,
} from "@/lib/spira/vacancy-map";

// ── Fixtures ─────────────────────────────────────────────────────────────────

function mapDocument() {
  const map = emptyVacancyMap();
  map.facts.jobTitle = "Backend developer";
  map.skills.push({ id: "s1", text: "Java 17", checked: true, comments: [] });
  // Two: position 0 is always the important one and cannot be changed, so the marks are
  // exercised on the second (owner, 2026-09-22).
  // REPLACE, never push: an empty map already carries three blank rows, and appending to them
  // would leave the fixture's own requirements at index 3 and 4.
  map.requirements = [
    {
      id: "r1",
      text: "Five years of Java",
      important: true,
      unmet: false,
      companies: [],
    },
    { id: "r2", text: "Kotlin", important: false, unmet: false, companies: [] },
  ];
  map.additional.push({
    id: "a1",
    text: "They mention testing",
    detail: "",
    tag: "none",
    checked: false,
  });
  // A company note is a plain line — it carries no timestamp and no employer, unlike the
  // comment threads hanging off a skill or a requirement.
  map.company.comments.push({ id: "n1", text: "The recruiter is Anna" });
  return serializeVacancyMap(map);
}

/** The state a map is in before `loadResourceMap` answers: the field is simply absent. */
function goalAwaitingMap(): Goal {
  const goal = goalWithMap();
  goal.resources = [
    { id: "res-1", type: "vacancy", title: "Backend Developer" },
  ];
  return goal;
}

function goalWithMap(mapData: string = mapDocument()): Goal {
  return {
    id: "goal-1",
    title: "Land a backend role",
    description: "",
    confidence: 5,
    createdAt: "2026-09-01T00:00:00.000Z",
    reality: { actions: [], obstacles: [] },
    options: [],
    targets: [],
    resources: [
      { id: "res-1", type: "vacancy", title: "Backend Developer", mapData },
    ],
  };
}

const store = {
  loadResourceMap: vi.fn(),
  patchVacancyMap: vi.fn(),
  updateResource: vi.fn(),
  removeResource: vi.fn(),
  duplicateResource: vi.fn(),
};

function panel(goal: Goal = goalWithMap()) {
  const onClose = vi.fn();
  useSpira.setState({ goals: [goal], ...store });
  // The real `patchVacancyMap` applies the document it is handed to the store at once — the
  // optimistic write. Without that the panel would render the same mark after every tap, and
  // a cycle could not be walked at all.
  store.patchVacancyMap.mockImplementation(
    (goalId: string, resourceId: string, _ops: MapPatchOp[], next: string) =>
      useSpira.setState((state) => ({
        goals: state.goals.map((g) =>
          g.id === goalId
            ? {
                ...g,
                resources: g.resources.map((r) =>
                  r.id === resourceId && r.type === "vacancy"
                    ? { ...r, mapData: next }
                    : r,
                ),
              }
            : g,
        ),
      })),
  );
  render(
    <VacancyMapPanel goalId="goal-1" resourceId="res-1" onClose={onClose} />,
  );
  return { onClose };
}

/** The ops of the nth `patchVacancyMap` call, as `{ path: value }`. */
function patchedOn(call = 0): Record<string, unknown> {
  const ops = store.patchVacancyMap.mock.calls[call]?.[2] as MapPatchOp[];
  return Object.fromEntries(
    ops.map((op) => [op.path, op.value === null ? null : JSON.parse(op.value)]),
  );
}

/** The document the store was handed alongside those ops. */
function documentOn(call = 0) {
  return parseVacancyMap(store.patchVacancyMap.mock.calls[call]?.[3] as string);
}

beforeEach(() => {
  Object.values(store).forEach((fn) => fn.mockReset());
});

// ── The panel's own chrome ───────────────────────────────────────────────────

describe("the vacancy map's head", () => {
  it("carries the map's own actions, and the way out is with them", () => {
    panel();

    expect(
      screen.getByRole("button", { name: "Download as HTML" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Duplicate" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeInTheDocument();
    // The X at the end of the group closes the panel; the chevron on the left belongs to the
    // full-screen state, which a panel over the page is not (CLAUDE.md → 3i, 2026-09-24).
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Back$/ })).toBeNull();
  });

  it("names the panel above the first card, the same words on every vacancy", () => {
    panel();

    // The vacancy's own name lives in the head; this says what the panel IS.
    expect(
      screen.getByRole("heading", { name: "Requirements map" }),
    ).toBeInTheDocument();
  });

  it("fetches the document on open, because no list read carries it", () => {
    panel(goalAwaitingMap());

    expect(store.loadResourceMap).toHaveBeenCalledWith("goal-1", "res-1");
    expect(screen.getByText("Loading the map…")).toBeInTheDocument();
  });
});

// ── A requirement's picture, and the position that owns "very important" ─────

describe("the requirement mark", () => {
  const plain =
    "An ordinary requirement. Tap to mark it as one you cannot meet";
  const unmet = "You cannot meet this. Tap to clear the mark";

  it("goes plain → cannot meet → plain, writing both flags", async () => {
    panel();

    await userEvent.click(screen.getAllByRole("button", { name: plain })[0]);
    expect(patchedOn(0)).toEqual({
      "/requirements/1/important": false,
      "/requirements/1/unmet": true,
    });
    expect(documentOn(0).requirements[1]).toMatchObject({
      important: false,
      unmet: true,
    });

    // The picture itself is the control, so the next tap is on the new one.
    await userEvent.click(screen.getByRole("button", { name: unmet }));
    expect(patchedOn(1)).toEqual({
      "/requirements/1/important": false,
      "/requirements/1/unmet": false,
    });
  });

  it("says 'Very important!' on the first row and a number on every other", () => {
    panel();

    // Exactly one row claims to be the one the application turns on.
    expect(screen.getAllByText("Very important!")).toHaveLength(1);
    expect(screen.getByText("Requirement 1")).toBeInTheDocument();
    expect(screen.getByText("Requirement 2")).toBeInTheDocument();
  });

  it("fixes the FIRST requirement as the important one, with nothing to tap", () => {
    panel();

    // It leads the list and says so — and its picture is a picture, not a control.
    expect(screen.getByText("Very important!")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /most important/i }),
    ).toBeNull();
  });

  it("always offers three rows, so an empty map is not a dead end", () => {
    panel(goalWithMap(serializeVacancyMap(emptyVacancyMap())));

    // Three pictures, and the first of them is the fixed one.
    expect(screen.getByText("Very important!")).toBeInTheDocument();
    expect(screen.getByText("Requirement 1")).toBeInTheDocument();
    expect(screen.getByText("Requirement 2")).toBeInTheDocument();
  });

  it("never leaves both flags on at once", async () => {
    panel();

    await userEvent.click(screen.getAllByRole("button", { name: plain })[0]);
    const marks = documentOn(0).requirements[1];

    expect(marks.important && marks.unmet).toBe(false);
  });
});

// ── Every list grows from a round + under its last item ──────────────────────

describe("adding to a list", () => {
  it.each([
    ["Add a skill", "/skills/-"],
    ["Add a requirement", "/requirements/-"],
    ["Add information", "/additional/-"],
    ["Add a note", "/company/comments/-"],
  ])("%s appends one item", async (label, path) => {
    panel();

    await userEvent.click(screen.getByRole("button", { name: label }));

    expect(Object.keys(patchedOn(0))).toEqual([path]);
  });

  it("keeps Requirements' button beside its heading — the one exception", () => {
    panel();

    const heading = screen.getByRole("heading", { name: "Requirements" });
    const add = screen.getByRole("button", { name: "Add a requirement" });

    // Its rows are tall and they open, so the foot of the list is a long way from the words
    // (owner, 2026-09-22). The button is in the heading's own row, not under the list.
    expect(heading.parentElement?.parentElement?.contains(add)).toBe(true);
  });

  it("puts the control UNDER the last item, not above the list", () => {
    panel();

    const skills = screen
      .getByRole("heading", { name: "Skills" })
      .closest("section")!;
    const add = within(skills).getByRole("button", { name: "Add a skill" });
    const list = within(skills).getByRole("list");

    // The list comes first in document order; the + is where the eye already is once the last
    // item has been read (owner, 2026-09-20).
    expect(
      list.compareDocumentPosition(add) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});

// ── Every heading explains its block ─────────────────────────────────────────

describe("the block explainers", () => {
  it.each([
    "Skills",
    "Personal qualities",
    "Requirements",
    "Additional information",
    "Company information",
  ])("%s has one, and it closes on Got it", async (block) => {
    panel();

    await userEvent.click(
      screen.getByRole("button", { name: `What "${block}" is for` }),
    );

    const card = await screen.findByRole("dialog");
    expect(within(card).getByText(block)).toBeInTheDocument();

    await userEvent.click(within(card).getByRole("button", { name: "Got it" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

// ── Wording ──────────────────────────────────────────────────────────────────

describe("wording", () => {
  it("heads the company's notes 'Worth knowing'", () => {
    panel();

    expect(screen.getByText("Worth knowing")).toBeInTheDocument();
    expect(screen.queryByText("Your comments")).toBeNull();
  });

  it("asks for the closing date in words until there is a date to show", () => {
    panel();

    expect(
      screen.getByRole("button", { name: /Apply by/ }),
    ).toBeInTheDocument();
  });
});

// ── The facts card ───────────────────────────────────────────

/** The card states the role until "View more"; everything else is behind it. */
async function theRest(goal = goalWithMap()) {
  panel(goal);
  await userEvent.click(screen.getByRole("button", { name: "View more" }));
}

describe("a fact's aside", () => {
  /** A map whose facts are answered — the state in which an aside makes sense at all. */
  function answered() {
    const map = emptyVacancyMap();
    map.facts.education = "BSc in Computer Science";
    map.facts.experience = "3+ years with Java";
    return goalWithMap(serializeVacancyMap(map));
  }

  it("waits for something to comment ON", async () => {
    await theRest();

    // An empty line offers one mark, and it is the one that invites the answer itself
    // (owner, 2026-09-24).
    expect(
      screen.queryByRole("button", { name: "Comment on education" }),
    ).toBeNull();
  });

  it("is no line at all until the comment glyph asks for one", async () => {
    await theRest(answered());

    // It used to be a second, permanently empty row under every answer.
    expect(
      screen.queryByRole("textbox", { name: "Comment on education" }),
    ).toBeNull();

    await userEvent.click(
      screen.getByRole("button", { name: "Comment on education" }),
    );

    expect(
      screen.getByRole("textbox", { name: "Comment on education" }),
    ).toBeInTheDocument();
  });

  it("is the words themselves once there are any, and the glyph stands down", async () => {
    const map = emptyVacancyMap();
    map.facts.educationNote = "A degree is not essential";
    await theRest(goalWithMap(serializeVacancyMap(map)));

    expect(screen.getByText("A degree is not essential")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Comment on education" }),
    ).toBeNull();
  });

  it("is offered only on the facts that carry one", async () => {
    await theRest(answered());

    // The role, the place and the link are the advert's own words; there is nothing to add
    // alongside them, so nothing asks.
    expect(
      screen.queryByRole("button", { name: "Comment on job title" }),
    ).toBeNull();
    expect(
      screen.getByRole("button", { name: "Comment on years of experience" }),
    ).toBeInTheDocument();
  });
});

describe("a field the user names herself", () => {
  function withSalary() {
    const map = emptyVacancyMap();
    map.facts.custom.push({
      id: "c1",
      label: "Salary",
      value: "45k",
      note: "",
    });
    return goalWithMap(serializeVacancyMap(map));
  }

  it("is added blank, so its name is the first thing she writes", async () => {
    await theRest();

    await userEvent.click(screen.getByRole("button", { name: "Add a field" }));

    expect(Object.keys(patchedOn(0))).toEqual(["/facts/custom/-"]);
    expect(documentOn(0).facts.custom).toHaveLength(1);
    expect(
      screen.getByRole("textbox", { name: "Field name" }),
    ).toBeInTheDocument();
  });

  it("writes its answer on its own path, like every other fact", async () => {
    await theRest(withSalary());

    await userEvent.click(screen.getByRole("textbox", { name: "Salary" }));
    await userEvent.keyboard("{Control>}a{/Control}55k{Enter}");

    expect(patchedOn(0)).toEqual({ "/facts/custom/0/value": "55k" });
    expect(documentOn(0).facts.custom[0].value).toBe("55k");
  });

  it("carries an aside of its own", async () => {
    await theRest(withSalary());

    await userEvent.click(
      screen.getByRole("button", { name: "Comment on salary" }),
    );

    expect(
      screen.getByRole("textbox", { name: "Comment on salary" }),
    ).toBeInTheDocument();
  });

  it("is removed whole", async () => {
    await theRest(withSalary());

    await userEvent.click(
      screen.getByRole("button", { name: "Delete this field" }),
    );

    expect(patchedOn(0)).toEqual({ "/facts/custom/0": null });
    expect(documentOn(0).facts.custom).toHaveLength(0);
  });
});

// ── The requirements list ────────────────────────────────────

describe("opening a requirement", () => {
  it("closes the one that was open", async () => {
    panel();
    const important = screen.getByRole("button", { name: /Very important!/ });
    const second = screen.getByRole("button", { name: /Requirement 1/ });

    await userEvent.click(important);
    expect(important).toHaveAttribute("aria-expanded", "true");

    // Each row opens into an answer box per employer; several at once turned the block into a
    // page of its own (owner, 2026-09-24).
    await userEvent.click(second);
    expect(second).toHaveAttribute("aria-expanded", "true");
    expect(important).toHaveAttribute("aria-expanded", "false");
  });

  it("offers to EMPTY the important one, never to delete it", async () => {
    panel();

    await userEvent.click(
      screen.getByRole("button", { name: /Very important!/ }),
    );

    expect(
      screen.queryByRole("button", { name: "Delete this requirement" }),
    ).toBeNull();
    await userEvent.click(
      screen.getByRole("button", { name: "Clear this requirement" }),
    );

    expect(patchedOn(0)).toEqual({ "/requirements/0/text": "" });
    // The list keeps its shape: the row stays, with nothing in it.
    expect(documentOn(0).requirements).toHaveLength(3);
    expect(documentOn(0).requirements[0].text).toBe("");
  });

  it("stays open while she types into it", async () => {
    // What the CV writer leaves behind: rows with text and no id of their own.
    const map = emptyVacancyMap();
    map.requirements = [
      { text: "Bygga interna system" },
      { text: "Arkitektur" },
    ] as unknown as typeof map.requirements;
    panel(goalWithMap(serializeVacancyMap(map)));

    // **The server's copy is what the page ends up holding.** `patchVacancyMap` writes the
    // optimistic document, then adopts the document the server returns — and the server has
    // only ever been sent the patches, so rows the CV writer appended still carry no id there.
    // Without this the test writes its own ids straight back and the defect cannot appear.
    store.patchVacancyMap.mockImplementation(
      (
        goalId: string,
        resourceId: string,
        _ops: MapPatchOp[],
        next: string,
      ) => {
        const server = JSON.parse(next);
        for (const item of server.requirements ?? []) {
          delete item.id;
          for (const company of item.companies ?? []) delete company.id;
        }
        useSpira.setState((state) => ({
          goals: state.goals.map((g) =>
            g.id === goalId
              ? {
                  ...g,
                  resources: g.resources.map((r) =>
                    r.id === resourceId && r.type === "vacancy"
                      ? { ...r, mapData: JSON.stringify(server) }
                      : r,
                  ),
                }
              : g,
          ),
        }));
      },
    );

    const row = screen.getByRole("button", { name: /Very important!/ });
    await userEvent.click(row);
    expect(row).toHaveAttribute("aria-expanded", "true");

    // The answer box writes on every keystroke, and each write re-reads the document. With a
    // freshly minted id per read the row was re-keyed on every letter — it shut, and the caret
    // went with it (owner, 2026-09-29).
    const answer = screen.getByRole("textbox", {
      name: "Your experience at Company 1",
    });
    await userEvent.type(answer, "Byggde");

    expect(
      screen.getByRole("button", { name: /Very important!/ }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("adds an answer box, and keeps it", async () => {
    // A map as the CV writer leaves it: one requirement, one answer already written.
    const map = emptyVacancyMap();
    map.requirements[0] = {
      ...map.requirements[0],
      text: "Bygga interna system",
      companies: [{ id: "c1", label: "ZoCom", text: "Byggde API:et" }],
    };
    panel(goalWithMap(serializeVacancyMap(map)));

    await userEvent.click(
      screen.getByRole("button", { name: /Very important!/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Add a company" }),
    );

    expect(Object.keys(patchedOn(0))).toEqual(["/requirements/0/companies/-"]);
    expect(documentOn(0).requirements[0].companies).toHaveLength(2);
    // **And it is still there afterwards.** The panel tidies legacy padding away when it opens a
    // document, and run on every change that also deleted the box just added — an empty box is
    // indistinguishable from the old padding, so the button appeared to do nothing (owner,
    // 2026-09-29).
    expect(
      screen.getByRole("textbox", { name: "Your experience at Company 2" }),
    ).toBeInTheDocument();
  });

  it("clears the padding a document was opened with, once", async () => {
    // Three boxes is what every map written before 2026-09-24 carries.
    const map = emptyVacancyMap();
    map.requirements[0] = {
      ...map.requirements[0],
      text: "Bygga interna system",
      companies: [
        { id: "c1", label: "", text: "" },
        { id: "c2", label: "", text: "" },
        { id: "c3", label: "", text: "" },
      ],
    };
    panel(goalWithMap(serializeVacancyMap(map)));

    // Highest index first, so the earlier ones do not shift under the removals.
    expect(Object.keys(patchedOn(0))).toEqual([
      "/requirements/0/companies/2",
      "/requirements/0/companies/1",
    ]);
    expect(documentOn(0).requirements[0].companies).toHaveLength(1);
  });

  it("leaves a box she added beside a written one alone when the map is re-opened", () => {
    // This is what the document looks like the moment after "Add a company": one answer written,
    // one box waiting. Re-opening the map used to delete the empty one — it survived the press
    // and vanished on the next visit, so the button looked broken (owner, 2026-09-29).
    const map = emptyVacancyMap();
    map.requirements[0] = {
      ...map.requirements[0],
      text: "Bygga interna system",
      companies: [
        { id: "c1", label: "ZoCom", text: "Byggde API:et" },
        { id: "c2", label: "", text: "" },
      ],
    };
    panel(goalWithMap(serializeVacancyMap(map)));

    expect(store.patchVacancyMap).not.toHaveBeenCalled();
  });

  it("deletes any other row outright", async () => {
    panel();

    await userEvent.click(
      screen.getByRole("button", { name: /Requirement 1/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Delete this requirement" }),
    );

    expect(patchedOn(0)).toEqual({ "/requirements/1": null });
  });
});

// ── The map as a file ────────────────────────────────────────

describe("downloading the map", () => {
  it("hands over one HTML file, named after the vacancy", async () => {
    // jsdom has no object URLs and no downloads: stub the two calls the browser would make,
    // and read what the anchor was given.
    const created: Blob[] = [];
    const url = "blob:vacancy-map";
    const createObjectURL = vi.fn((blob: Blob) => {
      created.push(blob);
      return url;
    });
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    let downloaded: { name: string; href: string } | null = null;
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(function (this: HTMLAnchorElement) {
        downloaded = { name: this.download, href: this.href };
      });

    panel();
    await userEvent.click(
      screen.getByRole("button", { name: "Download as HTML" }),
    );

    expect(downloaded).toEqual({
      name: "Backend Developer.html",
      href: url,
    });
    expect(created[0].type).toBe("text/html;charset=utf-8");
    expect(await created[0].text()).toContain("Backend developer");
    // The anchor is a means, not a leak: it must not be left in the page.
    expect(document.querySelectorAll("a[download]")).toHaveLength(0);
    click.mockRestore();
  });

  it("offers nothing to download until the document is here", () => {
    // The map is left out of every list read, so a panel opened on a goal that has not fetched
    // it yet would otherwise write an empty file and read as the map having been empty.
    panel(goalAwaitingMap());

    expect(
      screen.queryByRole("button", { name: "Download as HTML" }),
    ).toBeNull();
  });
});
