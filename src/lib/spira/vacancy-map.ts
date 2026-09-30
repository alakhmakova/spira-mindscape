/**
 * The vacancy map's document — what a "vacancy" resource holds.
 *
 * One map is one job advert, turned into the shape a CV is actually written from: the advert's
 * own facts, the skills it asks for, the personal qualities, each fuller requirement answered
 * per employer the user has worked for, anything extra worth saying in a letter, and the company
 * itself. It replaces the requirement-map NOTE, which was prose and therefore could only ever be
 * rewritten whole.
 *
 * **Two writers share it** — the user typing into the page, and the CV writer filling in what it
 * read from the advert — so nothing here is ever written as a whole document. Every edit names
 * one field (`MapPatchOp`, a JSON Pointer path) and the server merges it; see
 * `VacancyMapPatch.java`. The parse below is deliberately forgiving for the same reason: a map
 * may be half-written by an agent at any moment, and a missing branch must read as "empty", never
 * as a crash.
 */

/** One field-level edit, as the server's `MapPatchInput` expects it. */
export type MapPatchOp = {
  /** JSON Pointer: `/facts/location`, `/skills/-`, `/requirements/2/companies/0/text`. */
  path: string;
  /** The new value as JSON. `null` removes the field — how an item is deleted. */
  value: string | null;
};

/** Sets one field. The value is JSON-encoded here so callers never hand-build JSON. */
export function setAt(path: string, value: unknown): MapPatchOp {
  return { path, value: JSON.stringify(value) };
}

/** Removes one field, or splices one array element out. */
export function removeAt(path: string): MapPatchOp {
  return { path, value: null };
}

/** A comment left against a checklist item or a quality pill. */
export type MapComment = {
  id: string;
  text: string;
  /** ISO timestamp — shown in the comment modal so a thread reads in order. */
  at: string;
  /**
   * Optional: the employer this comment's experience comes from (owner, 2026-09-18), so it is
   * clear — to her and to the CV writer — which job the example belongs to. Blank is normal.
   */
  company: string;
};

/** A skill or a personal quality: tickable, and carrying its own comment thread. */
export type MapCheckItem = {
  id: string;
  text: string;
  checked: boolean;
  comments: MapComment[];
};

/** One employer's answer to one requirement. */
export type MapRequirementCompany = {
  id: string;
  /**
   * The employer's real name once the user types one. Blank is the normal state and is NOT a
   * defect: it renders as "Company 1", "Company 2" … from the position, so deleting the middle
   * one renumbers the rest without touching any stored text.
   */
  label: string;
  text: string;
};

/** One thing the employer asks for, answered once per company the user has worked for. */
export type MapRequirement = {
  id: string;
  text: string;
  /** The user's own mark that this is what the application turns on. */
  important: boolean;
  /**
   * Her mark that she cannot meet this at all (owner, 2026-09-20). It is the opposite end of the
   * same one-tap control as `important`, so the two are never both true.
   */
  unmet: boolean;
  companies: MapRequirementCompany[];
};

/** Where a piece of extra information is meant to be used. */
export type MapAdditionalTag = "none" | "cover_letter" | "profile";

/**
 * Something worth saying that is not a requirement: `text` is the fact, `detail` is the user's
 * own account of it, which is what a cover letter or a CV profile is later written from.
 */
export type MapAdditionalItem = {
  id: string;
  text: string;
  detail: string;
  tag: MapAdditionalTag;
  checked: boolean;
};

/** A plain list line — the company block's comments. */
export type MapNote = { id: string; text: string };

/** A fact the user adds and names herself — the advert's own conditions are the fixed ones. */
export type MapCustomFact = {
  id: string;
  /** What she calls it: "Salary", "Notice period", "Interview date". */
  label: string;
  value: string;
  /** Her own aside about it, the same second line every other fact has. */
  note: string;
};

/** The advert's own facts. Every one of these may be blank; none is required. */
export type MapFacts = {
  /** The role the advert is for — the first thing the card states (owner, 2026-09-20). */
  jobTitle: string;
  location: string;
  /** The advert's URL. Rendered as a real link when it is one. */
  link: string;
  education: string;
  educationNote: string;
  experience: string;
  experienceNote: string;
  language: string;
  languageNote: string;
  /** ISO `yyyy-mm-dd`, or null — the application deadline. */
  deadline: string | null;
  /** Anything else she wants the card to state, named by her (owner, 2026-09-24). */
  custom: MapCustomFact[];
};

export type MapCompany = {
  /** Free text about the company, above the table. */
  about: string;
  name: string;
  link: string;
  comments: MapNote[];
};

export type VacancyMap = {
  /** Document version. Bump only for a change a reader cannot absorb by ignoring a new field. */
  v: 1;
  facts: MapFacts;
  skills: MapCheckItem[];
  qualities: MapCheckItem[];
  requirements: MapRequirement[];
  additional: MapAdditionalItem[];
  company: MapCompany;
};

/**
 * How many employer columns a requirement starts with (owner, 2026-09-24: one).
 *
 * <p>Three made every requirement three empty boxes deep before a word was written in any of
 * them. She adds the second employer when there is a second employer.
 */
export const DEFAULT_COMPANY_SLOTS = 1;

/**
 * How many requirement rows the page always shows (owner, 2026-09-22): a map with none at all
 * still offers three to fill in, the same way a requirement always offers three employer columns.
 * An advert that states more gets more; one that states fewer is padded out here, not in storage.
 */
export const DEFAULT_REQUIREMENT_SLOTS = 3;

/** A fresh id for an item inside the document. Not a server id — the map is one JSON value. */
export function mapItemId(): string {
  const random = globalThis.crypto?.randomUUID?.();
  if (random) return random.slice(0, 8);
  return Math.random().toString(36).slice(2, 10);
}

export function emptyFacts(): MapFacts {
  return {
    jobTitle: "",
    location: "",
    link: "",
    education: "",
    educationNote: "",
    experience: "",
    experienceNote: "",
    language: "",
    languageNote: "",
    deadline: null,
    custom: [],
  };
}

export function emptyVacancyMap(): VacancyMap {
  return {
    v: 1,
    facts: emptyFacts(),
    skills: [],
    qualities: [],
    // The page always offers three rows to fill in, so an empty map has them too — otherwise a
    // map read from nothing and a map read from `{}` would differ (see `padRequirements`).
    requirements: padRequirements([]),
    additional: [],
    company: { about: "", name: "", link: "", comments: [] },
  };
}

export function newCheckItem(text = ""): MapCheckItem {
  return { id: mapItemId(), text, checked: false, comments: [] };
}

export function newComment(text: string, company = ""): MapComment {
  return { id: mapItemId(), text, at: new Date().toISOString(), company };
}

/** A fact she names herself, added blank so the label is the first thing she types. */
export function newCustomFact(): MapCustomFact {
  return { id: mapItemId(), label: "", value: "", note: "" };
}

export function newCompanySlot(): MapRequirementCompany {
  return { id: mapItemId(), label: "", text: "" };
}

export function newRequirement(text = ""): MapRequirement {
  return {
    id: mapItemId(),
    text,
    important: false,
    unmet: false,
    companies: Array.from({ length: DEFAULT_COMPANY_SLOTS }, newCompanySlot),
  };
}

export function newAdditionalItem(text = ""): MapAdditionalItem {
  return { id: mapItemId(), text, detail: "", tag: "none", checked: false };
}

export function newNote(text = ""): MapNote {
  return { id: mapItemId(), text };
}

/**
 * The name a company column shows. Blank is the normal stored state — the label is only kept once
 * the user types a real employer, so the numbering always follows the current positions.
 */
export function companyLabel(
  company: MapRequirementCompany,
  index: number,
): string {
  return company.label.trim() || `Company ${index + 1}`;
}

/** The heading a requirement card shows: "Requirement 1", by position. */
export function requirementLabel(index: number): string {
  // **Position 0 is "Very important!", so the numbers start at the row after it** (owner,
  // 2026-09-23): the list read 1, 2, 3 with the first number missing, which looked like a row
  // had been deleted.
  return `Requirement ${index}`;
}

// ── Parsing ────────────────────────────────────────────────────────────────
// Forgiving on purpose. The document is written a field at a time by two writers, so at any
// moment a branch may be missing, half-built, or (if an agent wrote it) the wrong JSON type.
// Every reader below answers "what is there?" and falls back to empty — it never throws.

function str(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function bool(value: unknown): boolean {
  return value === true;
}

function arr(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function obj(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

/** An id is only trusted when it is a non-empty string; otherwise one is minted. */
/**
 * The id an item is rendered under.
 *
 * <p><strong>A missing id must not be invented afresh on every read</strong> (2026-09-29). The
 * document is re-parsed after every write, and a write happens on **every keystroke** in a
 * company answer box — so a minted id gave each row a different React key each time, which
 * tore the row down and rebuilt it: the open requirement snapped shut and the caret went with
 * it. Rows the CV writer appends have no id at all (it patches in `{text}` objects), so this is
 * the normal state of a freshly analysed map, not an edge case.
 *
 * <p>Where the document supplies no id, the item's identity is its POSITION, and that is what it
 * is named after — stable between two reads of the same document, which is the whole
 * requirement. `mapItemId()` stays for items the PAGE creates, which are stored with their id.
 *
 * @param at the path to the item, e.g. {@code "requirements/2/companies/0"}
 */
function id(value: unknown, at: string): string {
  const text = str(value).trim();
  return text || `at:${at}`;
}

function toComment(raw: unknown, index: number, at = "comments"): MapComment {
  const source = obj(raw);
  return {
    id: id(source.id, `${at}/${index}`),
    text: str(source.text),
    at: str(source.at) || new Date(0).toISOString(),
    company: str(source.company),
  };
}

function toCheckItem(raw: unknown, index: number, at = "items"): MapCheckItem {
  const source = obj(raw);
  const here = `${at}/${index}`;
  return {
    id: id(source.id, here),
    text: str(source.text),
    checked: bool(source.checked),
    comments: arr(source.comments).map((comment, i) =>
      toComment(comment, i, `${here}/comments`),
    ),
  };
}

function toCompany(
  raw: unknown,
  index: number,
  at = "companies",
): MapRequirementCompany {
  const source = obj(raw);
  return {
    id: id(source.id, `${at}/${index}`),
    label: str(source.label),
    text: str(source.text),
  };
}

function toRequirement(raw: unknown, index: number): MapRequirement {
  const source = obj(raw);
  const here = `requirements/${index}`;
  const companies = arr(source.companies).map((company, i) =>
    toCompany(company, i, `${here}/companies`),
  );
  return {
    id: id(source.id, here),
    text: str(source.text),
    important: bool(source.important),
    unmet: bool(source.unmet),
    // A requirement with no companies at all would render as a dead card with nothing to answer
    // into — an agent that wrote only the text still gets the slots the user needs. Their ids
    // come from their position too: they are not in the document either.
    companies: companies.length
      ? companies
      : Array.from({ length: DEFAULT_COMPANY_SLOTS }, (_, i) => ({
          ...newCompanySlot(),
          id: `at:${here}/companies/${i}`,
        })),
  };
}

/**
 * Three rows, always, and **the first is always the important one**.
 *
 * <p>The owner's rule (2026-09-22): the requirement the application turns on leads the list and
 * that cannot be changed, so position 0 IS "very important" whatever the document says. Marking
 * it is therefore not a state the user toggles — the other rows carry the three marks.
 */
function padRequirements(items: MapRequirement[]): MapRequirement[] {
  const out = [...items];
  // The padded rows are not in the document, so their ids follow their position as well —
  // see `id`. Minted, they changed on every read and closed the row being typed into.
  while (out.length < DEFAULT_REQUIREMENT_SLOTS) {
    const at = `requirements/${out.length}`;
    out.push({
      ...newRequirement(""),
      id: `at:${at}`,
      // Its answer boxes are not in the document either, so they are named after their place
      // as well — one minted id in the row is enough to re-key it on every read.
      companies: Array.from({ length: DEFAULT_COMPANY_SLOTS }, (_, i) => ({
        ...newCompanySlot(),
        id: `at:${at}/companies/${i}`,
      })),
    });
  }
  // **"Very important" is a POSITION, not a toggle.** Position 0 always has it; no other row may,
  // or two rows would claim to be the one the application turns on. A later row can still be
  // marked as one she cannot meet.
  return out.map((item, index) =>
    index === 0
      ? { ...item, important: true, unmet: false }
      : item.important
        ? { ...item, important: false }
        : item,
  );
}

function toAdditional(raw: unknown, index: number): MapAdditionalItem {
  const source = obj(raw);
  const tag = str(source.tag);
  return {
    id: id(source.id, `additional/${index}`),
    text: str(source.text),
    detail: str(source.detail),
    tag:
      tag === "cover_letter" || tag === "profile"
        ? (tag as MapAdditionalTag)
        : "none",
    checked: bool(source.checked),
  };
}

function toNote(raw: unknown, index: number): MapNote {
  const source = obj(raw);
  return {
    id: id(source.id, `company/comments/${index}`),
    text: str(source.text),
  };
}

function toCustomFact(raw: unknown, index: number): MapCustomFact {
  const source = obj(raw);
  return {
    id: id(source.id, `facts/custom/${index}`),
    label: str(source.label),
    value: str(source.value),
    note: str(source.note),
  };
}

function toFacts(raw: unknown): MapFacts {
  const source = obj(raw);
  const deadline = str(source.deadline).trim();
  return {
    jobTitle: str(source.jobTitle),
    location: str(source.location),
    link: str(source.link),
    education: str(source.education),
    educationNote: str(source.educationNote),
    experience: str(source.experience),
    experienceNote: str(source.experienceNote),
    language: str(source.language),
    languageNote: str(source.languageNote),
    deadline: deadline || null,
    custom: arr(source.custom).map((item, index) => toCustomFact(item, index)),
  };
}

/**
 * Reads a stored document. Anything missing or malformed reads as empty, so a map an agent is
 * halfway through writing still renders — and a corrupt field costs that field, not the page.
 */
export function parseVacancyMap(json: string | null | undefined): VacancyMap {
  if (!json || !json.trim()) return emptyVacancyMap();
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    // The column holds only what this app wrote, so this is a corrupt row rather than a format
    // to support. An empty map is the safe read; the stored text is left untouched until the
    // user actually edits something.
    return emptyVacancyMap();
  }
  const source = obj(raw);
  const company = obj(source.company);
  return {
    v: 1,
    facts: toFacts(source.facts),
    skills: arr(source.skills).map((item, index) =>
      toCheckItem(item, index, "skills"),
    ),
    qualities: arr(source.qualities).map((item, index) =>
      toCheckItem(item, index, "qualities"),
    ),
    requirements: padRequirements(
      arr(source.requirements).map((item, index) => toRequirement(item, index)),
    ),
    additional: arr(source.additional).map((item, index) =>
      toAdditional(item, index),
    ),
    company: {
      about: str(company.about),
      name: str(company.name),
      link: str(company.link),
      comments: arr(company.comments).map((note, index) => toNote(note, index)),
    },
  };
}

export function serializeVacancyMap(map: VacancyMap): string {
  return JSON.stringify(map);
}

/** True when nothing has been filled in yet — drives the page's "empty map" invitation. */
export function isEmptyVacancyMap(map: VacancyMap): boolean {
  const facts = map.facts;
  const anyFact =
    facts.location ||
    facts.link ||
    facts.education ||
    facts.educationNote ||
    facts.experience ||
    facts.experienceNote ||
    facts.language ||
    facts.languageNote ||
    facts.deadline ||
    // A field she named herself counts the moment it has a name OR an answer — an empty one she
    // added and walked away from does not.
    facts.custom.some(
      (item) => item.label.trim() || item.value.trim() || item.note.trim(),
    );
  return (
    !anyFact &&
    map.skills.length === 0 &&
    map.qualities.length === 0 &&
    // The page always offers three requirement rows, so "no requirements" means three blank
    // ones, not an empty list (see `padRequirements`).
    map.requirements.every(
      (item) =>
        !item.text.trim() &&
        item.companies.every((c) => !c.text.trim() && !c.label.trim()),
    ) &&
    map.additional.length === 0 &&
    !map.company.about &&
    !map.company.name &&
    !map.company.link &&
    map.company.comments.length === 0
  );
}

/**
 * The title a duplicate takes. "copy", then "copy 2" — so duplicating twice does not produce two
 * resources with the same name (owner: a duplicate must say it is one).
 */
export function duplicateTitle(title: string, existing: string[]): string {
  const base = title.trim() || "Untitled vacancy";
  const taken = new Set(existing.map((name) => name.trim()));
  const first = `${base} copy`;
  if (!taken.has(first)) return first;
  for (let n = 2; n < 100; n++) {
    const next = `${first} ${n}`;
    if (!taken.has(next)) return next;
  }
  return first;
}
