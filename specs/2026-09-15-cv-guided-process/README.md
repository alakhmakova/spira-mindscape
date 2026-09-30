# The CV writer as a guided process

**Date:** 2026-09-15 · **Status:** implemented, awaiting live verification on Gemini
**Supersedes:** the phase design in `specs/2026-09-08-cv-and-cover-letter-agent/` §4–6
(VACANCY and DECONSTRUCT as model-driven phases, the profile step ending on the first approved note).

## Why

The owner's first real session (application 24, "System Developer/Tester på Advania") was, in her
words, chaos rather than structured work (GRO-167). The transcript and database showed:

| What she saw | Cause |
|---|---|
| No idea which step she was in; a pill reading "Reading" | Phase names in the header; nothing announced a change; the prompt forbade explaining the method |
| "The advert" while the writer was still on her profile, then a second profile note | The first approved profile note ended the step; later notes were filed by phase |
| Silence after the profile | A phase move ended the turn with the old phase's prompt |
| "I have already picked out the key points" — with 0 requirements stored | Extraction happened only if the model chose to call a tool |
| Questions about Java although her profile and repository showed it | Nothing read her sources before asking |
| JWT, Apollo Client, Testcontainers and invented figures in her profile | The fact gate checked numbers only, only in later phases |
| Proposed edits dropping her own corrections | `edit_note` replaced the whole body (BUG-085) |

## Owner decisions

- Six visible steps: Your profile · Job analysis · Your experience · Your CV · Cover letter · Final check.
- The profile is a **map, not the evidence**: a hint in the profile leads to the primary source; no match is said plainly.
- The job analysis is shown as a short chat summary plus a full "Job analysis — …" note.
- Lines written from her sources are confirmed **in the CV draft**, not in a separate review.
- The story bank is its own note.
- Live verification on Gemini.

## Design

**Steps and phases.** `CvStep` (six, user-facing) over `CvPhase` (internal):
PROFILE → ANALYSIS → EVIDENCE → DRAFT/SUMMARY/CV_REVIEW → LETTER_STRATEGY/LETTER → DONE.
VACANCY and DECONSTRUCT were merged into ANALYSIS (V25 migrates stored rows).

**The server speaks at the seams.** `CvTransitions` (ru/sv/en) writes the opening — who, which vacancy,
the six steps, one question — without a model call, and every step announcement: what finished,
what starts, what is needed. `announced_step` makes each one appear exactly once; an SSE `cv_step`
event updates the floating pill ("Step 2 of 6 · Analyzing the job requirements").

**No silent step changes.** Phase moves (`cv_phase_done`, `cv_confirm_profile`) loop; before every
model call `AiChatService.cvSync` announces a new step, runs the analysis when it is due, and rebuilds
the prompt and tools for the phase the work is in now.

**Profile.** Inherited from the goal's previous application. Approving a note never ends the step;
the user does ("Profile is correct", or `cv_confirm_profile` on her own words). `CvProfileCheck`
names missing sections. A new profile note while one is bound becomes an addition to it; rewriting
the bound profile is refused.

**Job analysis** (`CvAnalysisService`, resumable per stage, JSON validated by the server):
1. Extract topics with the advert's own lines — quotes not in the advert are dropped, topics sharing a
   line are merged, requirement lines left out get one follow-up.
2. Sources: profile, story bank, resources, linked GitHub repositories (tree, build files, dependencies).
3. Plan up to three reads per topic (15 in all), paths validated against the tree.
4. Read into the `cv_source_read` ledger.
5. Assess: covered only with a read source other than the profile (a credential excepted), soft skills
   need a story, draft lines with unbacked figures or technologies or advert prose are cleared.

Covered topics are answered from her sources and never asked about; the interview holds only gaps and
partly backed topics. The analysis note is rendered from rows and not overwritten after a hand edit.
An analysis cut by the stream deadline sends `cv_continue` and resumes.

**Anti-fabrication.** `CvTermGate` (a curated lexicon) and `CvFactGate` run on every CV document in
every step, against her material and what was read — never the advert; the term gate also runs on
the writer's chat prose. The story bank is proposed by the server from recorded handles only.

**Clean adverts.** `UrlReadService` reads a page's schema.org `JobPosting` before its text.

## Verification

Unit and integration tests: `CvTransitionsTest`, `CvAnalysisServiceTest`, `CvTermGateTest`,
`CvProfileCheckTest`, `CvApplicationServiceTest`, `AiChatServiceCvTest`, `UrlReadServiceTest`,
`AiCrossUserIsolationIntegrationTest`. Live criteria (Gemini, Advania advert, scripted in Russian):
opening without a model call; every step announced; one profile note with her edits intact; the Java
topic covered from a repository read and never asked about; no JWT/Apollo/Testcontainers unflagged.
