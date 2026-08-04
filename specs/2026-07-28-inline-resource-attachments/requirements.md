# Requirements: Inline resource attachments

Let users **attach resources to any inline-text element** (strategy options, reality
actions/obstacles, checklist tasks, and target titles). Two capabilities:

1. A URL that is too long to store inline can be turned into a **link resource**, leaving only a
   short **link** (the resource's name) in the field.
2. Any of those elements can have an existing resource attached from a menu, inserting the same
   link.

This is the durable fix for the "long URL → over the field limit → optimistic card → top-of-page
`sync failed` banner" problem (see `src/lib/spira/limits.ts`): the URL lives in the resource
(limit 1000), the field keeps a short reference.

Status: **built on the web (2026-08-01)** — steps 1–4 below — and **on Android (2026-08-04)**,
step 5. The overflow auto-convert modal (a too-long pasted URL becoming a link resource) is the
one piece the Android client still lacks.

Code that implements it:

- `src/lib/spira/links.ts` — the token grammar: `splitInline` (text / URL / resource segments),
  `resourceToken`, `hasResourceToken`, `referencesResource`, `replaceResourceToken`.
- `src/lib/spira/resources.ts` — `resourceDisplayName` / `titleFromUrl` (shared with the Resources
  section) and the pure detach planner `planResourceDetach` / `countResourceAttachments`.
- `src/components/spira/inline-resources.tsx` — the goal-scoped context, `ResourceLink`,
  `ElementActionsMenu` (the ⋯ / ⋮ menu) + its resource picker, `appendResourceToken`.
- `src/components/spira/Resources.tsx` — `InlineResourcesProvider` (owns the preview panel a link
  opens) and the delete-while-attached confirmation.
- `src/components/spira/Inline.tsx` — inline-link rendering and the over-limit URL → resource flow.
- Call sites: `OptionsList.tsx`, `Targets.tsx`, `Inline.tsx`'s `InlineList`,
  `routes/goals.$goalId.tsx` (wraps the workspace in the provider).

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
- Rendering: `splitInline` (in `links.ts`) emits `{ type: "resource"; id }` segments, and
  `InlineText` draws each as an **inline link**: the resource type's icon, its name underlined in
  the app's primary Kale, and a diagonal ↗ arrow marking the jump-out. It renders `display: inline`
  (not inline-flex) so it shares the surrounding text's baseline. Bare URLs render as before.
- **Unresolved token** (resource deleted on another device, or the field rendered outside a goal
  workspace): the link degrades to muted, non-clickable italic **"unavailable"** text — never the raw
  token and never a broken link.
- **Editing is plain text**, and the tag reads by **name** there — `{{res:Job ad}}`, not the stored
  id. `tokensToNames` / `namesToTokens` (inline-resources.tsx) map between the two on entering edit
  mode and on every commit; a tag naming something unknown degrades to plain text rather than being
  stored as a dangling reference, and the field's length limit is measured on the **stored** (id)
  form. A one-line hint with an ⓘ icon explains that deleting the whole tag detaches the resource.
- **ID timing caveat:** `store.addResource` returns a **temp local id** synchronously and only later
  swaps in the server id (after `createResource` resolves), handing the persisted resource to the
  `onCreated` callback. The token MUST be written with the **resolved** id from `onCreated` — never
  the temp id, which would dangle once the swap happens. `createLinkResource` in the provider wraps
  that callback in a promise.

## 2. Auto-convert a URL — trigger is overflow only

Do **not** auto-convert every pasted/typed URL (that would clutter Resources). Only when a value
**contains a URL and exceeds the field's limit** (`FIELD_LIMITS.optionText` / `realityText` /
`checklistText` / `targetTitle`) — on either path that hits the limit: a **pure-URL paste**, or a
**commit** (blur / Enter) of an over-limit value.

- The offer is only made when swapping that URL for a token would actually bring the value back
  under the limit (`RESOURCE_TOKEN_BUDGET` reserves room for the id); the longest such URL wins.
  Otherwise the plain "too long" message stands.
- Modal: *"That link is too long to save here — keep it as a resource instead?"* (teal/constructive
  confirm, not the red destructive one).
- **Yes** → create a `link` resource (title = the URL's domain) via
  `addResource(goalId, { type: "link", url, title: domain }, onCreated)`; in `onCreated`, replace
  the URL in the field with a `{{res:<real id>}}` token and commit.
- **No** → nothing is saved; the field stays in edit mode with the "too long" message, and that URL
  is **not** offered again (no nagging on every blur).

## 3. Link interaction

- **Click / tap** the link → open the resource's target (for `link`, open the URL in a new tab;
  other types open their preview).
- **Long-press (mobile) / right-click (desktop)** → open the resource's **preview panel**, which is
  where the title/URL/body are edited. The panel is owned by `InlineResourcesProvider`, so a link
  anywhere in the workspace can open it.

## 4. Per-element attach menu

Each element's delete **✕** became a **⋯ menu** (`ElementActionsMenu`, `vertical` for a ⋮
trigger) containing:

- **Attach resource** → a **picker dialog** listing the goal's **not-yet-attached** resources
  (icon + name + type; anything already referenced by this element is filtered out, so the same
  resource can't land on the same place twice),
  sized like `ConfirmDialog` (`w-[calc(100%-2rem)] max-w-[440px] sm:max-w-[600px]`) so it never runs
  edge-to-edge on a phone; picking one inserts its `{{res:id}}` link, and
- **Delete** (the element's existing remove action), in red.

Applies to: **options, reality actions/obstacles, checklist tasks, and targets** — on a target the
menu carries *Attach resource* + *Delete target* (mobile card and the desktop table, whose last
column is now **Actions** instead of Delete). It does **not** apply to **goal title / description**
(owner decision), nor to the checklist rows inside the *create-target* sheet (nothing to attach to
before the target exists).

Placement: the link is appended at the **end** of the field's text (the menu is used from the read
view, so there is no caret). If the token wouldn't fit within the field's limit, the attach is
refused with a toast rather than saving an over-length value.

The menu follows the existing menu conventions — white surface, rounded, hairline, shadow — using
the shared web dropdown primitives. It is **hidden until the row is hovered or focused**
(`REVEAL_ON_ROW_ACTIVITY`): tabbing into a row reveals the menu at the same moment the inline
editing caret appears. On options and reality items it floats over the text rather than reserving
a column, so the text keeps the full width; while hidden it is `pointer-events-none` so it can't
swallow a click meant for the text underneath.

**Placement follows the text** (`useIsSingleLine` + `rowControlPlacement`): a row's floating
controls sit **vertically centred** beside a one-line element and jump to the **top-right** as soon
as the text wraps, so they never hover mid-paragraph. The same rule moves the checklist row's
deadline control. On an option card the menu is additionally held clear of the rating badge on the
card's corner (`right-9`) — overlapping the text is fine, overlapping the smiley is not.

**Options card layout** (owner decision, 2026-08-02): the **⋮ menu sits inside the card**, floated
top-right of the strategy text, and the **rating smiley moved out to the card's top-right edge** as
the circle badge the menu used to occupy. The menu's delete item reads **"Delete option"**; the
checklist one reads **"Delete task"**.

Because the Options menu now renders *inside* an `InlineText` read view, `ElementActionsMenu` wraps
itself in a `display: contents` span that stops click propagation — menus and dialogs are portalled
in the DOM but still bubble through the React tree, which would otherwise drop the field into edit
mode and unmount the open menu.

## 5. Deleting a resource that is attached

When a resource is deleted from the Resources section and it is referenced by one or more element
tokens:

- Show a **warning** via `ConfirmDialog` (an ⓘ icon before the explanation): it is attached in N
  places; deleting it will turn those references into plain text.
- On confirm: **replace each `{{res:id}}` token with plain text = the resource's title** (truncated
  if that would exceed the field's limit), then delete the resource. Nothing disappears and no
  broken link remains — only the clickable reference is lost. (Owner decision, over "put back the
  full URL" and "remove entirely".)
- Finding the referencing elements: `planResourceDetach` scans the goal's option / reality /
  checklist / target text for **that resource's** token (matching *any* token would inflate every
  other resource's warning). This is client-side (all of a goal's data is loaded); a backend sweep
  can be added later if needed.
- A resource with **no** attachments is still deleted with no extra confirmation, as before.

## 6. Non-goals / later

- **Overflow auto-convert on Android** — a too-long pasted URL is not yet swapped for a link
  resource there. Android inline fields simply cap at the server limit instead. The rest of the
  feature (inline links, the ⋮ menu + picker, the delete degrade) shipped on 2026-08-04.
- **Backend** — no schema change for the token itself (text stays a string); only the existing
  resource create/delete endpoints are reused. A server-side referential sweep for step 5 is
  optional/later.
- Attaching to goal title/description; multiple-resource batch attach; reordering links; inserting
  a link at the caret while editing.

## 7. Build order (as built)

1. ✅ Token model + renderer (`links.ts` segments + the `InlineText` inline link).
2. ✅ Overflow auto-convert modal (replaces the `handlePaste` "too long" stopgap on the paths where
   a swap is possible; the message remains for URLs that can't be swapped).
3. ✅ Per-element ⋯ menu + resource picker (attach existing).
4. ✅ Delete-attached-resource warning + token→plain-text degrade.
5. ✅ Android parity (2026-08-04) — `ui/util/InlineLinks.kt` (the Kotlin port of `links.ts`),
   `ui/util/ResourceDetach.kt` (the port of `resources.ts`), and
   `ui/components/InlineResources.kt` (`InlineRichText` read/edit field, `ElementActionsMenu`,
   `AttachResourceButton`, `ResourcePickerSheet`). Tapping an inline link opens the resource the
   way its kind wants: a link goes to the browser, a note to the note editor, a file to the
   full-screen viewer, a contact to the mail app. The overflow auto-convert modal is not ported.

Tests: `src/lib/spira/links.test.ts` (token parsing), `src/lib/spira/resources.test.ts` (detach
planning, per-resource scoping, limit truncation), `src/components/spira/Inline.resources.test.tsx`
(link render/open, raw token while editing, the convert modal, attaching from the menu), and the
end-to-end flow in `e2e/resource-attachments.spec.ts`.
