# Requirements: Stabilize Current Frontend MVP

## Scope

Stabilize the current Spira frontend MVP so it matches the constitution in `specs/mission.md` and `specs/tech-stack.md`.

This phase is focused on preserving the current frontend goal model and removing inconsistencies that would mislead future backend or AI agents.

## Included

- Keep the current goal structure as the MVP source of truth:
  - Goal
  - Reality
  - Resources
  - Options
  - Targets
- Keep target types:
  - Numeric
  - Done / Not Done
  - Checklist
- Keep progress calculated from targets only.
- Remove target weighting from the frontend model and progress logic.
- Use Email as the resource type language instead of Contact.
- Preserve frontend-only local persistence as a temporary prototype mechanism.
- Add local persisted-state migration for previously stored Email resources that used the old internal `contact` type.
- Keep current UI behavior intact unless it conflicts with the constitution.
- Add frontend UI guardrails for accessibility, custom app controls, consistency, inline editing, responsive composition, and table spacing.

## Out of Scope

- No backend implementation.
- No GraphQL implementation.
- No PostgreSQL implementation.
- No AI API integration.
- No GROW session implementation.
- No resource upload architecture changes.
- No native mobile app work.

## Decisions

- The current frontend goal model remains the source of truth for the MVP domain structure.
- Target weights are not part of Spira's model.
- All targets contribute equally to goal progress.
- User-facing and internal resource terminology should use Email for email/person resources.
- Local Zustand persistence may remain temporarily for frontend-only work, but it is not the production persistence model.
- All visible UI elements must meet at least the minimum WCAG accessibility color contrast expectations for their role and state. Do not introduce low-contrast text, icons, controls, status labels, or hover-only affordances.
- Product UI must use custom Spira-styled components or app primitives. Native browser defaults are not acceptable for modals, drawers, dropdowns, date pickers, file upload controls, checkboxes, selects, text actions, or other interactive elements.
- Equivalent components must share the same base visual language. Forms, modals, drawers, empty states, buttons, icon actions, inline editors, tables, and repeated list items may adapt content and color to context, but their radius, borders, shadows, typography, spacing, hover behavior, and focus behavior must remain consistent.
- Primary row/card action icons, including delete/remove actions, must not be hidden until hover. Compact resource chips may keep secondary actions behind an explicit always-visible expand affordance so the collapsed chip stays compact.
- Existing text editing must be inline by default: the text remains fully visible, the cursor appears in place, and changes autosave on blur or commit. Do not replace existing text with bordered inputs, rings, edit buttons, or save/cancel controls unless the user is intentionally filling out a form.
- Text content must not be constrained by internal text scroll areas. Text should wrap and grow so the full content is visible. Horizontal page or component scrolling is prohibited except for explicitly fixed-format data views that have a documented responsive fallback.
- When a UI behavior, visual pattern, or component standard is changed, update the reusable primitive or written standard so future changes inherit the new behavior instead of applying it only once.
- Responsive design must be both functional and polished. Mobile and narrow container layouts must preserve hierarchy, spacing, alignment, touch targets, and composition quality, not merely fit on screen.
- Tables must use consistent spacing between columns. Header and body cells should share the same horizontal padding and alignment pattern unless a specific numeric/action column requires a clearly documented exception.
- Side panels and drawers must not use horizontal separator lines between the title and content or between content and footer actions. Use spacing instead: headers follow the New goal/New target pattern with a compact title block and the body starting close below it.
- Mobile bottom-sheet drawers (create goal/target, add resource, and equivalent forms) must open to a fixed near-full height (~92vh) that leaves a visible gap to the top edge of the screen with the drag handle showing — they must not touch or exceed the top edge, and must not size to content (which collapses and scrambles the layout when the keyboard opens). Override the drawer primitive's default top margin so the gap stays consistent. Layout is a fixed-height flex column: sticky header, scrollable body, and a bottom action row pinned in place; the action row is two buttons (Cancel + the primary create action), without a separator line above them.
- Drawers and form sheets must not auto-focus any field on open. Opening a sheet must never raise the on-screen keyboard by itself — the user taps a field to start typing. (`autoFocus` is prohibited on first-rendered fields in these sheets; note `useIsMobile()` returns false on the first render, so `autoFocus={!isMobile}` still focuses on mobile and is not an acceptable workaround.)
- Resource records must always receive a useful display name when the user does not provide one. Derive names from the resource content, such as an email local-part, URL hostname, file name, or an "Untitled note" fallback.
- Resource preview layouts must use styled field rows for structured data such as Email details. Do not show structured resource fields as bare text.
- Primary create actions must keep their established placement and remain visible. Do not move an existing creation action to a different UI region when the issue is only visibility or anchoring.
- Timeline type filters must use styled boxed checkbox rows and occupy normal layout space above the timeline. They must not be absolutely positioned over timeline content, especially on mobile.
- On mobile overview screens, search belongs in the secondary control row with filters and sorting controls, not in the primary header row. Do not add separate icon-only AI buttons to the mobile header.
- Controls that do not affect the active view must be hidden. Timeline view must not show goal-card sorting controls unless sorting is implemented for timeline rows.

## Context

This phase prepares the repo for future production backend work.

Future agents should be able to read the current frontend and constitution without seeing unused domain concepts that are not intended for production.

The changes should remain narrow and should not redesign the application.
