# Validation: Stabilize Current Frontend MVP

Implementation is complete when all checks below pass.

## Automated Checks

- [ ] `pnpm.cmd run build` exits 0.
- [ ] TypeScript accepts the renamed Email resource type.
- [ ] No target weighting remains in domain types or progress logic.
- [ ] No `contact` resource type remains in frontend source.
- [ ] Product UI uses Spira-styled primitives or custom components for interactive controls.
- [ ] Primary action icons are not hidden behind hover-only opacity; compact resource chips may reveal secondary actions through an always-visible expand affordance.
- [ ] Existing text editing is inline and autosaves without bordered edit fields unless the user is in a creation/edit form.
- [ ] Text wraps without horizontal scrolling or internal text-only scroll clipping.
- [ ] Tables use consistent header/body column spacing.
- [ ] Side panel headers and footers use spacing instead of horizontal separator lines.
- [ ] Resources get derived display names when the user leaves the name/title blank.
- [ ] Email resource previews render fields as styled rows, not bare text.
- [ ] Primary create actions keep their established placement and remain visible.
- [ ] Timeline type filters use boxed checkbox rows and do not overlap timeline content.
- [ ] Mobile overview search appears in the same secondary row as Filter and Sort.
- [ ] Sort controls are hidden in Timeline view until timeline sorting is implemented.
- [ ] The mobile header does not include a separate square AI sparkles button.

## Search Checks

Run:

```powershell
rg -n '\.weight|weight\?:|weightSum|t\.weight' src
rg -n 'type: "contact"|type === "contact"|contacts|Contact' src
```

Expected result:

- No target weighting references in `src`.
- No old `contact` resource type references in `src`.

Note: Words such as "weight tracker" in `specs/` are unrelated to target weighting and are allowed.

## Manual Checks

- [ ] Goals dashboard still renders.
- [ ] Goal page still renders.
- [ ] Target progress still calculates for Numeric, Done / Not Done, and Checklist targets.
- [ ] Goal progress is the average of all target progress values.
- [ ] Resources section says "emails" rather than "contacts".
- [ ] Adding an Email resource works.
- [ ] Opening/editing an Email resource works.
- [ ] Copying/sending an Email resource still works.
- [ ] Previously persisted local Email resources using the old `contact` type are migrated to `email`.
- [ ] All text, icons, buttons, and status states meet minimum accessible contrast.
- [ ] Equivalent forms, sheets, dialogs, empty states, row actions, and list items share one consistent base style.
- [ ] Resource, option, target, and task action icons are visible before hover.
- [ ] Inline edits place the cursor in existing text and save automatically.
- [ ] Mobile and narrow container layouts remain composed, aligned, and readable.
- [ ] Target table column gaps look even across all columns.
- [ ] Side panels have no title/body or content/footer divider lines, while spacing remains balanced.
- [ ] Mobile create/add drawers open to a fixed near-full height with a visible top gap (drag handle showing); they never touch the top edge or collapse to content height, and stay stable when the keyboard opens. The bottom action row (Cancel + create) is pinned, with no separator line above it.
- [ ] No drawer or form sheet auto-focuses a field on open; opening a sheet never raises the keyboard by itself. (Confirm no `autoFocus` on first-rendered sheet fields, including no `autoFocus={!isMobile}`.)
- [ ] Note preview text has comfortable left padding from the panel edge.
- [ ] Email preview fields are visually grouped and action buttons remain clear.
- [ ] The New goal `+` button remains in its established floating position and stays visible on All goals and Timeline views.
- [ ] On mobile, Goals / Targets / Tasks timeline filters sit above the timeline and do not cover timeline rows.
- [ ] On desktop, Goals / Targets / Tasks timeline filters span the available timeline width instead of clustering on the left.

## Definition of Done

- The frontend model matches the constitution for this phase.
- The build passes.
- The feature spec is saved under `specs/2026-05-04-stabilize-frontend-mvp/`.
- No backend or AI implementation is introduced in this phase.
