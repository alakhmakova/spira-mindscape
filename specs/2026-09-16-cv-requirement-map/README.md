# The requirement map: the CV writer's process, as the owner wrote it

**Date:** 2026-09-16 · **Status:** backend and collection cards implemented; no live run yet
**Supersedes:** `specs/2026-09-15-cv-guided-process/` — same principles (the server owns the steps,
the analysis is code, nothing is asked that the sources already answer), different order and a
different unit of work.

## Why it changed again

The 2026-09-15 design put the profile first and then asked only about "gaps" the server had
judged. The owner wrote the process out by hand instead (2026-09-16) and the differences are not
cosmetic:

| 2026-09-15 | Now |
|---|---|
| Step 1 was the profile; the advert was analysed second | **The advert is analysed first.** Nothing about the user is collected until the requirement map exists |
| The profile was a large step with its own template | **A short intake form** — the dry skeleton a CV needs, which the writer can fill from an old CV |
| One flat list of "topics", each judged covered / partial / gap | **Five categories**, each collected its own way |
| The server decided which requirements to skip | **Every competence and every requirement is put to the user**, because only she knows her own examples |
| Coverage was assessed by a model against read sources | Sources are read at the **writing** step; the questions are about her own account |

## The five categories

The analysis reads the advert sentence by sentence, drops the connective words and the decoration,
merges what repeats wherever it sits in the text, and keeps:

1. **Competences** — short tags, the way a CV's competence block is written ("Java", "CI/CD").
2. **The core message** — the one thing the advert is really about, assembled from lines spread
   across it, in its own words. It leads SAMMANFATTNING and the letter.
3. **The employer's fuller requirements** — whole sentences, each becoming a question and then a
   bullet of experience.
4. **Personal qualities** — named or implied, each carrying the advert's line as its context.
5. **Extra** — what the company says about itself (motivation for the letter) and anything that
   decides whether she can apply at all (a permit, a stated number of years), which is raised now.

Plus the title, the role's other names and the location, which go straight into the documents.

The map is a note she can open ("Requirement map — …"), rendered from the stored rows and updated
as she answers.

## The six steps

1. **Job analysis** — server-run. `CvAnalysisService` makes one model call for the reading, then
   validates it: a quote that is not in the advert is dropped, a requirement or quality without a
   quote is dropped, and two items quoting the same line are one item.
2. **Your details** — the intake form. The writer reads an attached old CV or a named resource and
   takes only the skeleton: contact, links, employers (company, what it does, role, dates),
   education, courses, internships. Never the old CV's descriptions of the work. The step ends when
   she presses "Details are correct" (or says so, `cv_confirm_intake`).
3. **Competences** — a checklist: yes / no / partly with a comment. No depth, no follow-ups.
4. **Your experience** — one item per turn: the core message, then the requirements, then the
   qualities. Each answer is stored as she told it (`record_answer`, or `no_answer`). Two rules the
   CV depends on: one example per employer for the same requirement, and for a quality the
   situation that shows it, never an adjective.
5. **Your CV** — the writer reads the sources she pointed at, then writes the whole document in one
   pass from the material block, proposes it as a note, and asks what to correct.
6. **Cover letter** — only if she wants one.

Steps 3 and 4 end by themselves when every item of the step is settled; the server announces each
step (`CvTransitions`, ru/sv/en) and the client shows "Step 3 of 6 · Checking which competences you
have".

## Where it lives

- `V26__cv_requirement_map.sql` — `cv_requirement.category / user_verdict / user_answer / context /
  extra_use`, `cv_application.location / core_message / intake_confirmed_at`, and the phase
  migration (`PROFILE|VACANCY|DECONSTRUCT → ANALYSIS`, `EVIDENCE → REQUIREMENTS`).
- `CvPhase` / `CvStep` — ANALYSIS · INTAKE · COMPETENCIES · CORE · REQUIREMENTS · TRAITS · DRAFT ·
  CV_REVIEW · LETTER_STRATEGY · LETTER · DONE, over six visible steps.
- `CvApplicationService` — the map (`map`, `nextOpen`), the checklist (`recordVerdicts`), the
  answers (`recordAnswer`, `recordNoAnswer`), the intake form (`confirmIntake`, `useAsIntake`), and
  the stage transitions.
- `prompts/cv/analysis-extract.md` — the reading, with her own examples of what to merge.
- `AiChatService` — tools per phase (`cv_confirm_intake`, `record_verdicts`, `record_answer`,
  `no_answer`, `cv_phase_done`), one state block per step, and the material block the CV is written
  from.
- `AiController` — `/cv/applications/{id}/map`, `/competences`, `/answers`, `/intake/confirm`,
  `/intake/use`. Each recording endpoint then calls `refreshMapNote`, because `evidenceChanged` —
  the flag the chat path re-renders on — is only ever set by a model tool call, so an answer sent
  from a card refreshed nothing.
- `AiPanel.tsx` — `CvCompetenceChecklist` and `CvEvidenceCard` (both exported, for the Vitest
  spec), `CvCardHead`, `CvQuote`, and the `cv_review` "Save as PDF" action, which calls the app's
  one `printNotePdf` rather than adding a second mechanism. Every card answer is echoed into the
  transcript before the silent `continue` turn, since `silent: true` posts no user bubble and the
  answer would otherwise exist only in the database.

## Done since this was written

- **The collection cards.** The competence checklist (tick / blank / Partly + comment, instructions
  at the top) and the per-item question cards, which show the position in the stack, carry the
  advert's own line as context, and let her say she has no example — the alternative to which is
  inventing one. Covered by `cv-collection-cards.test.tsx`, which asserts what a card *sends*: a
  blank is "no", Partly overrides a tick and carries its comment, an answer is trimmed.
- **A card's content stays visible while she edits it.** `ProposalCard` used to swap its body for
  the instruct box, so saying what to change meant losing sight of the thing being changed;
  `InstructBox`'s headline is now optional so the card's is not shown twice.
- **"Save as PDF"** at the review step.
- **`CvIntakeCheck` recognises the word the app itself asks for.** Its `Employers` pattern matched
  `employment` but not `employers`, so a form written to the app's own instructions was told its
  employers were missing — and the writer asked again for what she had already given.

## Fixed from the live-run log (2026-09-17)

`test-log-2026-09-16.md` records 46 defects from the owner walking the whole process by hand.
These are the ones now implemented; each is numbered as the log numbers it.

**The prompts**

- **1 — the extraction prompt no longer contains the advert it was evaluated on.** Every example
  in `analysis-extract.md` was literally from the Advania posting, so a model reading that advert
  was shown the answers. The examples are now a synthetic Danish logistics advert, and the
  `core_message` guidance states the general rule ("what the advert keeps coming back to")
  instead of this posting's answer.
- **38, 30, 33 — a reference letter is quotation material, not a substitute for her own account.**
  `writer-method.md` gained the two-uses distinction, the rule that a development note in a letter
  ("still junior") reaches neither document, and the owner's own rule verbatim in effect: never
  ask her to type out what one of her documents already says. The "ask for the text instead"
  fallbacks are gone — it asks for the file.
- **42 — the functional CV is triggered by the test, not by the life stage.** "Straight out of
  study" was making the prompt prescribe a functional CV for a candidate with sixteen months on
  the advert's own axis, which would have discarded every per-employer story step 4 collected.
- **43 — `EGET PROJEKT` moves up when it is the strongest evidence**, rather than sitting below
  the languages block while the final check asks whether the most relevant experience has the
  most room.
- **45 — proposed versus saved is settled by ownership.** A document the app produces (the CV, the
  letter, the map) is written, saved and reviewed on the note; a proposal card stays for edits to
  material she owns. The prompt had one rule and her process the opposite.

**The server**

- **2, 3 — a condition of the advert is raised as a question, and the work waits for her answer.**
  The summary used to state it mid-paragraph and announce step 2 in the same turn, so nothing was
  asked and nothing came back to it. It now ends the summary, quoted on its own line, and step 2
  is announced on the turn after she answers (`analysis_reported_at`, V27).
- **5 — no gendered self-reference in Russian** ("Я прочитал", "я бы сформулировал").
- **6, 7 — the intake form asks for languages and for her own projects.** Both are skeleton that
  step 4 cannot recover: there is no "tell me about a time you were fluent in Swedish", and an
  own project is one of the three places step 4 offers as evidence. `CvIntakeCheck` has a
  `Languages` part that also recognises a bare "Svenska – flytande" line.
- **8, 46 — the app's own output is never offered back as her details.** `\bcv\b` is out of the
  title pattern, and the real guard is provenance: every note bound as a document by **any**
  application on the goal is excluded, because no name can tell her own reference CV from the one
  this tool wrote last week.
- **9 — the server says which note it meant** (`intakeCandidateId` / `intakeCandidateTitle`). The
  client's own regex had drifted from the server's, so a step message said "press Use as my
  details" and no button was drawn.
- **15 — step 4 counts the core message**, which is a map item with a phase of its own. It
  announced thirteen items and asked fourteen.
- **16, 13 — the writer sees the advert's full tag and her comment beside every verdict.** Her
  "по сути нет" to `Bash` was about the word; the advert said "Bash eller annan scripting" and the
  honest verdict was yes. A tick is a gesture and a sentence is an intention, so a verdict is
  never shown without the comment that came with it.
- **40 — the trait step is given every answer already recorded**, whatever category it was asked
  under. Three of five qualities were fully answered under the requirements and she refused the
  fourth question outright: "уже много было написано, я не буду повторять".
- **21 — `cv_vision` is dropped** (V27). It was never read or written.

**The cards**

- **10 — the card copy comes from the server** (`CvCardText`, `GET /cv/applications/{id}/copy`),
  in the conversation's language. The cards were the last surface written in English literals, and
  they are where her instructions live. There is deliberately no English fallback in the client: a
  card is not drawn until its words arrive, because a second copy of the wording is exactly how
  the intake-candidate rule drifted.
- **4 — the checklist is grouped** into what the employer requires and what is a plus (7 + 13 rows
  on this advert).
- **13 — every row can carry a comment**, not only a "Partly" one, and the box is three rows deep
  because her real answers were several sentences.
- **17 — the core card is titled with the core message itself**, not with a fixed sentence.
- **27 — "I'm not sure what counts here" is a third action.** It records nothing, leaves the item
  open and puts the judgement to the writer, where it belongs.
- **22 — the CAR prompt stays spelled out**, now pinned by `CvCardTextTest` on the side that owns
  the words.

Covered by `CvCardTextTest`, `CvTransitionsTest`, `CvIntakeCheckTest`, `PromptResourcesCvTest` and
`cv-collection-cards.test.tsx`. **Not verified against a live provider** — same caveat as the rest
of this spec.

## Outstanding

- **The letter's own method (step 6)** — the owner will describe it next.
- **A live run.** Gemini answered 503 for every attempt on 2026-09-15, and nothing since has been
  exercised against a real provider.
- **V25 and V26 are not applied.** Both files exist; the dev database sits at V24, so the
  requirement-map schema has never run against real data. Flyway will apply them on the next
  backend start — it needs the owner's say-so, not an agent's.
- **No CV end-to-end spec.** `stubAiProvider` covers `/api/ai/keys`, `/preferences` and `/chat`
  only; a CV spec also needs the `/api/ai/cv/applications/*` surface stubbed (create, fetch,
  transcript, map, competences, answers). The card logic is covered by Vitest, and the existing
  `ai-proposal-card.spec.ts` was checked against the `ProposalCard` change — its assertions are all
  about the state after "Send to AI", so the restructure does not invalidate them.
- **Android has no CV mode**, so none of this reaches the phone yet.
