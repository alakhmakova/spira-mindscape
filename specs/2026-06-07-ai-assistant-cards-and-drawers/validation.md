# Validation: AI Assistant Proposal Cards & Create Drawers

Checks that the behavior in `requirements.md` holds. Grouped by the cheapest test that can
prove each one (see `### Test strategy` at the end).

## Automated — unit (vitest, pure logic) ✅ DONE

The pure card logic was extracted to `src/components/ai/proposal-logic.ts` and is covered by
`src/components/ai/proposal-logic.test.ts` (run `npm test`):

- [x] `proposalFromToolArgs` maps each tool `kind` to the right `Proposal` (ids, deadline,
      confidence, items, `openSubject`, goal-level `goalId`; invalid JSON → undefined).
- [x] `dedupCreates` keeps distinct creates and drops only exact duplicates (same kind+title).
- [x] `createAspects` returns the optional fields per kind (goal: confidence/deadline/
      description; target: deadline/done) and `[]` for a bare goal.
- [x] `applyExcludedAspects` strips exactly the unticked fields and nothing else.
- [x] `createSummary` gives the numeric measure / checklist count, `undefined` for goals.
- [x] `isOptionActivate` / `fmtDeadline` edge cases.
- Store regression: `src/lib/spira/store.test.ts` — adding several reality items at once
  keeps them all (concurrent server responses don't clobber).
- [ ] `proposalDisplay` (still in `AiPanel.tsx`): `edit_goal` → headline = goal name, detail =
      the change; `open_goal` → "Open «name»"; delete kinds → "Delete «text»". (Not yet
      extracted/tested — needs the `Goal` type; lower priority.)

## Automated — component (vitest + @testing-library/react, jsdom)

- [ ] One create with fields renders `CreateChecklistCard`; unticking Deadline then Create
      calls `onCreate` with `deadline` removed.
- [ ] A checklist create renders one real checkbox per item; unticking an item omits it from
      the created `items`.
- [ ] Several proposals render the stepper; Back/Next moves steps; "Save N of M" reflects
      ticked steps.
- [ ] A resolved group renders `ResultSummary` (chips + Open), not the full card.
- [ ] `InlineText`/required `AutoTextarea`: clearing restores the old text, shows the inline
      message, and does **not** call `onChange`.

## Automated — E2E (Python/pytest, GraphQL API)

The card UI is not reachable from the API E2E, but the **effects** of approving a card are:

- [ ] Deleting an option / obstacle / action / checklist item via its mutation removes it.
- [ ] An empty title/text mutation is rejected (matches "text is required").

## Manual (device / browser)

- [ ] Drawers: ~92vh, top gap + drag handle, two-button pinned footer, no separators, no
      keyboard on open.
- [ ] Pending card sits in the footer; resolving collapses it to a result line with Open.
- [ ] No duplicated type/field text under a title; no emoji anywhere.
- [ ] "Create 3 goals: …" → 3-step stepper, each step with its field checkboxes; Save all
      creates all three.
- [ ] "Delete <option>" → delete card removes the option; clearing an option's text inline
      restores it with an inline message (no top error, no Try again).
- [ ] All-Goals: changing a description offers Open «Goal»; opening re-runs the request and a
      card appears (not an empty chat).

## Test strategy (recommended order)

1. **Unit** the pure card logic first — highest value, lowest cost. Requires extracting the
   helpers from `AiPanel.tsx` into e.g. `src/components/ai/proposal-logic.ts`.
2. **Component** tests for card rendering/interaction (add `@testing-library/react`).
3. Keep **E2E** for the data effects only (the existing GraphQL suite). A browser-UI E2E
   (Playwright) is optional and only worth it for a few critical end-to-end card flows.
