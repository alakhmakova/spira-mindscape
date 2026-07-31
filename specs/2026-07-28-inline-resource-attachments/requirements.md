# Requirements: Inline resource attachments

Let users **attach resources to any inline-text element** (strategy options, reality
actions/obstacles, checklist tasks, and target titles). Two capabilities:

1. A URL that is too long to store inline can be turned into a **link resource**, leaving only a
   short titled **chip** in the field.
2. Any of those elements can have an existing resource attached from a menu, inserting the same
   chip.

This is the durable fix for the "long URL → over the field limit → optimistic card → top-of-page
`sync failed` banner" problem (see `src/lib/spira/limits.ts` and
`specs/.../` field-limit notes): the URL lives in the resource (limit 1000), the field keeps a short
reference.

Status: **design only — not yet implemented.** Web first; Android parity later.

Primary code to touch when building:
- `src/components/spira/Inline.tsx` — `InlineText` (rendering + commit), extend `splitUrls`/`UrlLink`.
- `src/lib/spira/links.ts` — `splitUrls` (add a resource-token segment type).
- `src/components/spira/OptionsList.tsx`, `Targets.tsx` (checklist items + target title),
  `routes/goals.$goalId.tsx` (reality `InlineList`) — swap the ✕ affordance for a ⋯ menu.
- `src/lib/spira/store.ts` — reuse `addResource(id, input, onCreated)` and `removeResource`.
- `src/components/spira/Resources.tsx` — reuse for editing an attached resource; add a resource picker.
- `src/components/spira/ConfirmDialog.tsx` — reuse for the delete-attached-resource warning.

---

## 1. Storage model — inline token

Elements keep storing a **plain-text string** (no schema change to the many element tables:
`option`, `reality_item`, `checklist_item`, `target`). A reference to a resource is an inline
**token** embedded in that text:

```
Read {{res:42}} before the call
```

- `{{res:<id>}}` where `<id>` is the resource's **server** id.
- Chosen over a polymorphic `element_resource` association table because "element" spans several
  tables; a token works uniformly everywhere text is stored and allows multiple references per
  field with zero schema/migration work.
- Rendering: extend `splitUrls` (in `links.ts`) to also emit a `{ type: "resource"; id }` segment,
  and have `InlineText`'s renderer resolve it against the goal's `resources` to draw a **chip**
  (the resource's title). Bare URLs keep rendering as today.
- **ID timing caveat:** `store.addResource` returns a **temp local id** synchronously and only later
  swaps in the server id (after `createResource` resolves), handing the persisted resource to the
  `onCreated` callback. The token MUST be written with the **resolved** id from `onCreated` — never
  the temp id, which would dangle once the swap happens.

## 2. Auto-convert a URL — trigger is overflow only

Do **not** auto-convert every pasted/typed URL (that would clutter Resources). Only when a committed
value **contains a URL and exceeds the field's limit** (`FIELD_LIMITS.optionText` / `realityText` /
`checklistText` / `targetTitle`):

- Show a modal: *"That link is too long to save here. Create a link resource instead?"*
- **Yes** → create a `link` resource (title defaults to the URL's domain) via
  `addResource(goalId, { type: "link", url, title: domain }, onCreated)`; in `onCreated`, replace
  the URL in the field with a `{{res:<real id>}}` token and commit.
- **No** → revert; the value is **not** saved (the current stopgap message path).

This replaces the interim "too long to save here" stopgap in `InlineText.handlePaste` once built.

## 3. Chip interaction

- **Click / tap** the chip → open the resource's target (for `link`, open the URL in a new tab;
  other types open their preview).
- **Long-press (mobile) / a menu item (desktop)** → open the resource for **editing** in the
  Resources drawer (`Resources.tsx`). Editing the URL/title happens there, not inline.

## 4. Per-element attach menu

Replace each element's delete **✕** with a **⋯ menu** containing:
- **Delete** (the current remove action), and
- **Attach resource** → opens a **picker** listing the goal's `resources`; selecting one inserts its
  `{{res:id}}` chip at the caret / end of the field.

Applies to: **options, reality actions/obstacles, checklist tasks, and target title.**
Does **NOT** apply to: **goal title, goal description** (owner decision — attaching a resource to a
goal's own name/description is out of scope).

The menu must follow the existing menu conventions (white surface, rounded, hairline, shadow — see
CLAUDE.md dropdown anatomy). Reuse the web dropdown primitives already used elsewhere.

## 5. Deleting a resource that is attached

When a resource is deleted (from the Resources tab or the chip's edit view) and it is referenced by
one or more element tokens:
- Show a **warning** via `ConfirmDialog`: it is attached inside other elements; deleting it will turn
  those references into plain text.
- On confirm: **replace each `{{res:id}}` token with plain text = the resource's title** (short,
  always within the field limit, non-clickable). Nothing disappears and no broken link remains —
  only the clickable reference is lost. (Owner decision, over "put back the full URL" and "remove
  entirely".)
- Finding the referencing elements: scan the goal's option/reality/checklist/target text for the
  token. This is client-side (all of a goal's data is loaded); a backend sweep can be added later if
  needed.

## 6. Non-goals / later

- **Android parity** — mirror after the web MVP lands (Compose `InlineEditText`, the Android menu
  kit, Apollo). Out of scope for the first build.
- **Backend** — no schema change for the token itself (text stays a string). Only reuse the existing
  resource create/delete endpoints. A server-side referential sweep for step 5 is optional/later.
- Attaching to goal title/description; multiple-resource batch attach; reordering chips.

## 7. Build order (suggested MVP)

1. Token model + renderer (`links.ts` segment + `InlineText` chip) — read-only display first.
2. Overflow auto-convert modal (replaces the `handlePaste` stopgap).
3. Per-element ⋯ menu + resource picker (attach existing).
4. Delete-attached-resource warning + token→plain-text degrade.
5. Then Android parity.

Verify each step against pixels (CLAUDE.md rule #4) and add Vitest coverage for the token
parse/render and the overflow/delete flows.
