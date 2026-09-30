# The vacancy map: one resource per job advert

**Date:** 2026-09-17 · **Status (2026-09-18):** the resource is implemented and verified on web,
and **all three slices are in** — the analysis builds the map, the conversation follows her lead
over it, and the collection cards are gone. Backend 1361/1361, web 315/315. **Not yet run end to
end against a real advert with a real model** (owner: postponed), and the web panel's new map
button has not been looked at in a browser.
**Supersedes:** the requirement-map **note** described in `specs/2026-09-16-cv-requirement-map/`.
The process that spec describes — five categories, read from the advert, answered by the user — is
unchanged. What changes is where it lives and who may write to it.

## Why the note had to go

The requirement map was rendered as an HTML note (`CvAnalysisNote`), and a note is one blob. That
gave it three faults, and they compound:

| The note | The map |
|---|---|
| Written whole, every time | Written **one field at a time** |
| A hand edit was guarded by a timestamp — "if she changed it since, leave it alone" — so her edit and the agent's update were mutually exclusive | Both write at once; they only collide if they touch the *same field* |
| Its own footer had to say "Edits here are not read back — tell the writer instead" | What she types **is** the map |
| Prose: the agent re-read its own rendering | Structured: the agent reads fields |

The footer is the tell. A document the app asks you not to edit is a document in the wrong shape.

> **The rule this whole feature rests on:** two writers share one map — the user typing into the
> page, and the CV writer filling in what it read from the advert. Nothing is ever written as a
> whole document. That is enforced by the mutation, not by a convention an agent has to remember.

## The document

One `vacancy` resource holds one advert. The shape is `src/lib/spira/vacancy-map.ts`, which is
authoritative; this is the summary.

| Branch | Holds | On the page |
|---|---|---|
| `facts` | location, link, education, years of experience, language (each with a free comment), deadline | The long card at the top |
| `skills[]` | `{ text, checked, comments[] }` | Skills — a checklist, comments behind an icon |
| `qualities[]` | the same shape | Personal qualities — a cloud of tickable pills |
| `requirements[]` | `{ text, important, companies[] }`, each company `{ label, text }` | The right column; expands to the advert's full sentence and one answer per employer |
| `additional[]` | `{ text, detail, tag, checked }`, tag ∈ none / cover_letter / profile | Additional information |
| `company` | about, name, link, comments[] | Company information |

Three details that are decisions, not incidentals:

- **A company's `label` is normally blank**, and renders as "Company 1", "Company 2" … from its
  position. So deleting the middle one renumbers the rest without touching stored text, and typing
  a real employer over it is just filling the field in (owner, 2026-09-17).
- **`important` on a requirement is the core message.** The analysis used to carry a separate
  `core_message` that led the summary and the letter; it is now simply the requirement marked
  important, because that control already exists and means the same thing (owner, 2026-09-17).
  Icons for the important and ordinary states are the owner's to supply; `Flag` is a placeholder.
- **Every field may be blank.** An advert that does not state a language requirement leaves that
  field empty; nothing on this page is required except the resource's title, which is the vacancy.

## Storage

`resource.map_data`, a `TEXT` column of JSON (`V28__vacancy_map_resource.sql`, which also widens
the `type` CHECK to admit `vacancy`).

- **Not selected by any list read.** `ResourceView` returns null for it, exactly as it does for
  `data_url`, so a goals fetch never carries every map; the page loads it on open through
  `resourceById` (`loadResourceMap`, the twin of `loadResourceFile`). On the client `undefined`
  means *not fetched* and `""` means *loaded and empty* — collapsing the two either re-fetches
  forever or never fetches at all.
- **Field whitelist:** a vacancy accepts `title` and `mapData` only (`VACANCY_FIELDS`).
- **200 000 characters**, enforced server-side. Nothing else bounds a column an agent writes to.
- Parsing is **forgiving by design** (`parseVacancyMap`): the document is half-written by an agent
  at any given moment, so a missing or malformed branch reads as empty, never as a crash.

### The patch contract

`patchVacancyMap(id, patches)` — each patch is a JSON Pointer path and a JSON value.

```
/facts/location                        set one fact
/skills/-                              append (the one addition to pointer syntax, from JSON Patch)
/requirements/2/companies/0/text       write one employer's answer to one requirement
/skills/3                  value null  remove — how an item is deleted
```

Missing containers are created from the *next* token: numeric or `-` makes an array, anything else
an object. So the page can write `/company/comments/-` into a document that has no `company` yet,
and a map created empty needs no seeding. `VacancyMapPatch` is pure and covered by
`VacancyMapPatchTest`, whose point is the assertions that an **untouched neighbour survives**.

The one place a whole document is written is **duplicating** a map, where the document is the thing
being copied. `resourceInput` therefore sends `mapData` on create only, never on update — otherwise
renaming a resource would resend the whole map, reintroducing the clobber this exists to prevent,
and would blank the map outright when the document has not been lazily loaded.

## The page

`src/components/spira/VacancyMapPage.tsx` — `VacancyMapPanel`, rendered by `PreviewBody` like
every other resource. The route `/goals/$goalId/map/$resourceId` still exists so a link the CV
writer hands over survives being opened in a new tab.

- **A side panel, not a page** (owner, 2026-09-18). It was a page first, on the argument above
  about the link; as a page it had no way back to the goal, and leaving the goal page clears the
  coach's goal, so opening a map switched an open chat to the All-goals one.
- **It wears the shared resource head** (`ResourceHead`, CLAUDE.md → 3i): the chevron out, the
  vacancy's name scrolling if it is long, Duplicate and Delete as icons on a laptop and inside the
  kebab on a phone. Under it, **"Requirements map"** names the panel — the same words on every
  vacancy, beside the inert "Need help?".
- **Corners are near-square here**: `rounded-[4px]` for cards and controls, `rounded-[2px]` for the
  small chrome inside them. This is the first surface built to the standard the rest of the app is
  moving towards (owner, 2026-09-17); when the app-wide token changes these become the token.
- **Every free-text field is `InlineText`/`AutoTextarea`, inside `InlineResourcesProvider`** — so
  any answer can carry a `{{res:id}}` chip pointing at another resource ("my education is written
  up in that note"). This needed no new mechanism; it falls out of using the app's inline fields.
- A vacancy opened from an inline chip gets an explicit branch in `PreviewBody` that links to the
  page. Without it the preview panel falls through to the note body and renders an empty sheet.

## The advert → map mapping

What `analysis-extract.md` produces, and where it lands. This is the contract slice 1 implements.

| Extraction | Map | Note |
|---|---|---|
| `title` | the resource's title | The map's name **is** the vacancy |
| `location` | `facts.location` | |
| the advert's URL | `facts.link` | |
| `core_message` | a `requirement` with `important: true` | Not a card of its own |
| `competencies[].tag` | `skills[].text` | |
| a competence's `quotes` | a **comment** on that skill | See below |
| `requirements[].demand` | `requirements[].text` | **The advert's exact sentence** |
| `traits[].trait` | `qualities[].text`, its `context` as a comment | |
| `extra` with `use: letter` | `additional[]`, tag `cover_letter` | |
| `extra` with `use: flag` | `additional[]`, raised with the user at once | |
| *(new)* education, years of experience, language | `facts.*` | Extractor gains three fields |

Four decisions behind that table, all the owner's (2026-09-17):

1. **There is no separate quotes field, because a requirement's text *is* the quote.** "Требования
   это и есть прямые цитаты из вакансии." The old model stored one row per quote and showed them
   under "The advert says"; the map stores the employer's sentence as the requirement itself.
2. **A competence's fuller wording becomes a comment on the skill.** This is the one place the
   quote still earns its keep, and the reason is a measured defect: the owner answered "по сути
   нет" to `Bash` where the advert said "Bash **eller annan scripting**" and her own project is
   full of scripting (live run, 2026-09-16). The tag alone loses the correction; a comment carries
   it without a new field.
3. **Skills stay a plain checkbox** — ticked or not — and the **comment carries the nuance** that
   the old yes/no/partly verdict held. Her comment already outranked her tick ("don't write Azure
   DevOps, put GitHub Actions"), so the comment is where "partly" actually lived.
4. **An empty company answer means unevidenced.** No "no evidence" flag: a requirement with no
   answer filled in is one the CV cannot stand behind, and the review lists those.

## How the work is sliced

1. **The analysis fills the map.** Extend `analysis-extract.md` with the three fact fields; make
   `CvAnalysisService` create a `vacancy` resource and write it with patches instead of calling
   `storeAnalysis` + `renderNote`; have the agent link the user to it. The old flow keeps working
   underneath. `cv_application.analysis_note_id` already points at a resource and a map **is** a
   resource, so the pointer needs no migration — only a reinterpretation.
2. **The conversation becomes flexible.** Skills, in place of one monolithic prompt: reading an
   advert into the map, and career-consultant collection. The agent explains what the map is and
   how it maps to a CV, then asks which part she wants to fill rather than marching through a fixed
   queue. After each part: more, or done? Only on her confirmation does it offer to write the CV.
3. **Retire `cv_requirement`.** The table, its repository, the collection cards in `AiPanel`
   (`CvCompetenceChecklist`, `CvEvidenceCard`) and the three recording endpoints go; the map is the
   single store. `CvApplication.location / coreMessage / roleTitle / companyName` become unused —
   they are the map's now — and are dropped in this slice rather than left to drift.

### What slices 2 and 3 actually shipped (2026-09-18)

Where it differs from the plan above, this is the record.

- **Four steps, not six**: Job analysis · The vacancy map · Your CV · Cover letter (`CvStep`).
  `V30__cv_map_process.sql` folds the old INTAKE / COMPETENCIES / CORE / REQUIREMENTS / TRAITS
  phases into `MAP` and renumbers `announced_step`; legacy phase names still parse to `MAP`.
- **Her details are a part of the map step, not a gate.** There is no "Details are correct"
  confirm any more; the panel only offers a candidate note (`CvMapActions`, beside **Open the
  vacancy map**).
- **One writing tool, `map_write`** (`CvMapService.write`), in place of `record_verdicts` /
  `record_answer` / `cv_confirm_intake`. It refuses to change or delete anything she wrote — a
  non-blank text, a tick, an item holding either — unless the call says `replace=true`, which the
  prompt allows only after she agreed. Filling an empty field, ticking and appending are free.
- **Leaving the map for the CV needs her own turn.** `cv_phase_done next=draft` is refused on a
  control turn (`userWroteThisTurn`), so the model cannot decide on her behalf that the map is done.
- **The `cv_requirement` table is NOT dropped.** An application begun under the six-step process
  keeps its answers there; `CvMapService.ensure` converts them into a map once (ticks and
  "partly" comments onto skills, answers under Company 1), and nothing writes the table again.
  The same goes for `location` / `companyName` on `CvApplication` — `ensure` reads them for that
  conversion. Dropping either is a later migration once no pre-map application is left.
- Removed with the cards: `/intake/confirm`, `/map`, `/competences`, `/answers`, `CvAnalysisNote`,
  and the client's `confirmCvIntake` / `fetchCvMap` / `submitCvVerdicts` / `submitCvAnswer`.

## The illustrations (owner's own, 2026-09-20)

`public/illustrations/` holds the drawings the owner made, each cleaned before use — the fixed
`width`/`height` dropped so they scale, a `viewBox` **added** (several arrive with none at all, and
without one the drawing renders at whatever size the browser guesses), an accessible name added,
and anything that only wrapped the art (a clip to its own box, the `<defs>` that served it)
removed. The paths and her colours are untouched.

| File | Where | What it means |
|---|---|---|
| `vacancy.svg` | left of the facts card | the vacancy itself — a clipboard, drawn in her own teal (#2BAAAC, which is Brand-400). It replaced a traced briefcase on 2026-09-22 |
| `requirement.svg` | left of every requirement row | an ordinary requirement |
| `requirement-important.svg` | the same slot | **the picture IS the "very important" mark** — tapping it swaps the two, which replaced the flag that used to sit on the right of the row |
| `requirement-unmet.svg` | the same slot | the third state of that one tap: a requirement she cannot meet at all. Drawn in Guava, against the pencil's Kale |
| `company.svg` | left of Company information | the employer — a house with a tree. It replaced a Gusto shopfront recoloured to her instructions, which she rejected on sight (2026-09-22) |

## How a block of the map is put together (owner, 2026-09-20)

Every block on the map now follows the same three rules, and they are the map's, not the app's:

- **The heading carries an info mark, and it opens the "Get answers now" card** — a plain white
  popover with the block's name in bold, a sentence or two under it, and **"Got it" as a worded
  link**, never a button. `CardTitle`'s `hint` prop and `BlockHint` draw it; a heading without one
  is an unfinished block.
- **A list grows from a round `+` UNDER its last item, with the action in words** (`AddLine`) —
  "Add a skill", "Add a requirement", "Add information", "Add a note". The old outlined button in
  the heading row is gone from every block: it sat furthest from where the new line appears, and
  on a narrow panel it squeezed the heading.
- **Every inline-edited row ends in a small pencil** (`EditableLine`) — a hint, not a button: the
  page is almost entirely bare text, and nothing else said the words could be typed into. It turns
  Kale while that row has the caret.

The company block's notes are headed **"Worth knowing"** (it was "Your comments"), and the facts
card leads with the briefcase, the **job title on the picture's line and "Apply by" under it** —
the deadline used to float, so opening the details left it stranded at the bottom of the card.

## Deliberately not done

- **The CV draft resource.** Generating the CV from the map needs its own mapping, and the owner
  wants a second resource type for the draft. That is the phase after this one.
- **Android** lists a map and says it opens on the web. Without that branch every `when` in
  `ResourcesTab.kt` falls through to the *file* case, so a map showed as "File" and rendered
  through `FileBody`, which reads a mime and bytes it has not got. Saying less is fine; saying
  something untrue is not.
- **"Need help?"** on the page is inert, by instruction.
- **`analysis_note_rendered_at`** is now meaningless: it existed so a re-render would not clobber a
  hand edit, and patches make that structural.

## Where it lives

- `V28__vacancy_map_resource.sql` · `Resource.mapData` · `ResourceView.mapData()` (null on lists)
- `VacancyMapPatch` + `VacancyMapPatchTest` · `ResourceService.patchMap` +
  `ResourceServiceVacancyMapTest` · `MapPatchInput` · `patchVacancyMap` in the schema and controller
- `src/lib/spira/vacancy-map.ts` (+ its test) · `types.ts` · `api.ts` · `store.ts`
  (`loadResourceMap`, `patchVacancyMap`, `duplicateResource`)
- CV process: `CvMapService` (+ `CvMapServiceTest`) · `VacancyMapDocument` · `VacancyMapBuild` ·
  `V29__cv_application_map_resource.sql` · `V30__cv_map_process.sql` · `CvTransitions` ·
  `AiChatService` (`map_write`, the state block) · `CvMapActions` in `AiPanel.tsx`
- `src/components/spira/VacancyMapPage.tsx` · `routes/goals_.$goalId.map.$resourceId.tsx` ·
  `resource-meta.ts` · the `Comment` glyph in `icons.tsx` (gravity: comment)

## What is tested, and at which level (2026-09-21)

Each level is here for something the one below it cannot show.

| Level | File | What only it can say |
|---|---|---|
| Web unit | `src/lib/spira/vacancy-map.test.ts` | the document reads forgivingly: a half-written or corrupt branch is empty, a requirement's two marks are never both on, an old document without `jobTitle` or a comment's `company` still reads |
| Web unit | `src/components/spira/VacancyMapPage.test.tsx` | which patch each control sends — the mark cycle's exact paths, the four `AddLine`s appending to their own branch, the explainers, the wording |
| Web unit | `src/components/spira/ResourceHead.test.tsx` | the head's two shapes (icons on a laptop, one kebab on a phone), the action with no handler that must not be drawn, and the marquee's measured shift |
| Backend unit | `VacancyMapDocumentTest` | what the coach is told: the three marks in words, an unticked skill named as *not* hers, an unanswered requirement named as missing, and the evidence holding **her** words only — not the advert's figures |
| Backend unit | `VacancyMapPatchTest` · `ResourceServiceVacancyMapTest` | one field written without disturbing its neighbours; the field whitelist and the size cap |
| E2E | `e2e/vacancy-map.spec.ts` | **that a patch actually lands.** The map is left out of every list read, so a write that never reaches the server looks exactly like one that did — until the page is reloaded and the document is fetched again. The spec reloads between every step |

Each of these was checked **red** against the code it guards (CLAUDE.md → 3e-quater): the mark line
removed, both flags set at once, the phone forced onto the desktop branch, the round `+` deleted,
and — for the E2E — the patch ops replaced with an empty list, which leaves the panel looking
correct and loses everything on reload.
