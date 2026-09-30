# BUG-094 — An open requirement closes on every keystroke

**Status:** ✅ Fixed
**Area:** Web / Vacancy map
**Severity:** High — the block cannot be filled in at all

## Summary

On a vacancy map the CV writer had analysed, opening a requirement and typing into an answer box
closed the block after the **first letter**, and took the caret with it. Typing a word meant
re-opening the row seven times (owner, 2026-09-29, on "Systemutvecklare vid LTH:s kansli").

## Steps to reproduce (before the fix)

1. Let the CV writer fill a vacancy map from an advert.
2. Open a requirement and type into "Your experience, employer by employer".
3. The row collapses on the first character.

## Root cause

**Rows the writer appends carry no `id`.** It fills the map through JSON-Pointer patches
(`/requirements/-` with `{"text": …}`), so the stored document holds requirements, companies,
skills and notes with no id of their own — that is the normal state of a freshly analysed map,
not an edge case.

`parseVacancyMap` minted one with `mapItemId()` whenever the document had none. Three facts then
compose into the defect:

- the answer box (`AutoTextarea`) commits on **every keystroke**, not on blur;
- `patchVacancyMap` writes the optimistic document and then **adopts the document the server
  returns** — and the server has only ever been sent the patches, so its copy still has no ids;
- the page re-parses that document, minting **different** ids.

React keys the row by that id, so each letter tore the row down and built a new one. The
accordion's `openId` no longer matched anything (it is state in `RequirementsColumn`, which
survives), the row rendered closed, and the focused textarea was unmounted mid-word.

## Fix

An item the document gives no id is named after **its position**: `at:requirements/2`,
`at:requirements/2/companies/0`, `at:skills/0`, `at:company/comments/1`. Deterministic, so two
reads of the same document agree, which is all the identity such a row needs. `mapItemId()` stays
for items the page creates, which are stored with their id. The padded requirement rows and the
default answer boxes — neither of which is in the document — are named the same way.

`AutoTextarea` in the company block also gained an `aria-label` ("Your experience at ZoCom"): it
had no name at all, so nothing but its position said what it was for, to a screen reader or to a
test.

## How to verify fixed

1. `vacancy-map.test.ts` → "reading the same document twice": parsing the same JSON twice gives
   every row the same id, and a row without a stored id is named after its position.
2. `VacancyMapPage.test.tsx` → "stays open while she types into it". Its mock **strips the ids
   the page sent back**, which is what the server actually holds — without that the test writes
   its own ids straight back and the defect cannot appear at all. Verified red against the old
   `mapItemId()` behaviour: `aria-expanded` flips to `false`.
3. In the running app, on the map that prompted this: open the important requirement, type into
   the ZoCom answer — it stays open (measured 2026-09-29, seven characters, `aria-expanded`
   stayed `"true"`).

## Resolution

Fixed 2026-09-29 in `src/lib/spira/vacancy-map.ts` (`id`, `toComment`, `toCheckItem`, `toCompany`,
`toRequirement`, `toAdditional`, `toNote`, `toCustomFact`, `padRequirements`) and
`src/components/spira/VacancyMapPage.tsx`.
