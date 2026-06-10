# Requirements: AI Assistant Proposal Cards & Create Drawers

This documents how the AI assistant's **proposal cards** and the app's **create
drawers** must look and behave, so future changes preserve the design we settled on.

Primary code: `src/components/ai/AiPanel.tsx` (cards), `src/components/spira/Inline.tsx`
(inline editing), `src/components/spira/*Sheet*` + drawers, and the AI prompt/schema in
`backend/.../ai/chat/AiChatService.java`.

Drawer-specific rules also live in
`specs/2026-05-04-stabilize-frontend-mvp/{requirements,validation}.md` — this spec is the
source of truth for the AI cards and consolidates the drawer rules for reference.

---

## 1. Mobile create drawers (goal / target / resource)

- Open to a **fixed near-full height (~92vh)** with a visible gap to the top edge (drag
  handle showing). Never touch the top edge; never size to content (which collapses when
  the keyboard opens). Override the drawer primitive's default top margin (`mt-0`).
- Layout is a fixed-height flex column: sticky header, scrollable body, **pinned bottom
  action row of two buttons (Cancel + the primary Create/Add)**, with **no separator line**
  above the buttons and no separators between header/body/footer.
- **No field auto-focuses on open** — opening a sheet must never raise the keyboard by
  itself. `autoFocus` is prohibited on first-rendered fields, including `autoFocus={!isMobile}`
  (note `useIsMobile()` is `false` on the first render, so that still focuses on mobile).
- Note creation: the body uses the rich editor with a collapsible "Format" toolbar when
  **creating**; the full toolbar shows when **opening/editing** a note. Desktop toolbar is a
  solid pinned header (no scrolling content behind it).

## 2. Where proposal cards live

- A **pending** card renders **in the footer** of the chat — it replaces the composer (the
  card *is* the input while a proposal is open).
- Once a card is **resolved** (approved/dismissed), the full card disappears from the chat
  and is replaced by a compact **result line** (`ResultSummary`): a check + what was saved
  as chips, with an **Open** shortcut to the created item; if nothing was approved → a muted
  "Dismissed". This mirrors a collapsed action summary (like "Read 2 files").
- **Revising a card updates it in place.** "Type a change for the AI…" must re-ask the model
  and swap the revised proposal back into the SAME card slot (same message, same proposal id)
  — never spawn a new card/message, never show the internal "Revise your proposed…" text as a
  chat bubble, and never pile up duplicate cards. Implemented by `reviseInPlace`. If the model
  replies with text instead of a proposal (e.g. a clarifying question), surface that text and
  leave the original card untouched. Applies to every card type (single, option, stepper).

## 3. Card taxonomy & routing (`ProposalGroup`)

The number and shape of proposals decides the card:

| Situation | Card | Notes |
|---|---|---|
| 1 proposal, an **option made active** | `OptionAspectCard` | two checkboxes: *Create «X»* + *Make it the active option* |
| 1 **create** with optional fields, or a numeric/checklist target | `CreateChecklistCard` | first checkbox is the entity; one checkbox per optional field; checklist items as real checkboxes |
| 1 **bare create** (name only / bare binary target) | `CreateConfirmCard` | one-tap Create/Add; no duplicate type text |
| 1 of anything else (edits, state changes, deletes) | `ProposalCard` | badge + headline + detail |
| **Several** proposals | `SteppedProposalCard` | one step each (Back/Next), per-step checkboxes, Save all / Save N of M |

"One thing or several at once" must work everywhere there are lists/collections (e.g.
"create 3 goals" → 3 steps). The backend emits one tool call per listed item; the frontend
keeps every **distinct** create (drops only exact duplicates — `dedupCreates`).

## 4. Checkboxes & field selection ("aspects")

- Checkboxes must be **real**: a visual `<span>` (`CheckBox`) inside a
  `<button role="checkbox" aria-checked>` — never a `<button>` inside a `<button>` (invalid
  HTML, the toggle won't fire).
- A single create with optional fields shows the entity as the first checkbox, then **one
  checkbox per optional field** (`createAspects`): goal → Confidence, Deadline, Description;
  target → Deadline, "Already done".
- **Unticking a field drops it from what gets created** (`applyExcludedAspects`). Unticking
  the entity dismisses the whole creation.
- A **checklist target** shows its items as **real, tickable checkboxes** (`ChecklistItems`);
  **unticking an item means that item is not created**. No markdown preview, no bullets,
  no fake/disabled checkboxes.
- The stepper carries the same per-step checkboxes (fields + checklist items + option
  "make active"); "Save all" applies only the ticked parts.

## 5. No duplicated information

- The kind **badge** already names the action ("New goal", "New target", "Edit goal"). Do
  **not** repeat it in the line under the title.
- A field shown as a checkbox is **never** also restated as text under the title (no
  "Deadline → …" line when there's a Deadline checkbox).
- For a create, the under-title line shows only **structural** info that *defines* the item
  and has no checkbox: numeric measure (`0 / 20 applications`) or — for a bare binary
  target — its type ("Done / not done"). A checklist shows items instead of a count line.
- `edit_goal` shows the **goal's name** as the headline and the **change** as the detail
  (e.g. headline `«Goal 1»`, detail `Confidence → 7/10`) — for every field, not just
  confidence. `open_goal` shows `Open «Goal»`.

## 6. Expanding large content

- Content too big for a card (a note body, a goal description) gets a **"Read full
  content"** expander that opens it in `ContentModal` (HTML for notes, Markdown otherwise).
- The Description **checkbox** carries its own "Read full content" (`AspectRow`) — the
  description content lives **only** at its checkbox, never restated elsewhere.

## 7. Deletion via the assistant

- The assistant can propose deleting: a **goal** (`delete_goal`), a **target**
  (`delete_target`), an **option** (`delete_option`), an **obstacle**/**action**
  (`delete_obstacle`/`delete_action`), and a **checklist item** (`delete_checklist_item`).
- It must use the kind that **matches the item's type** (an option → `delete_option`, never
  `delete_target`) with the item's **real id** from context.
- It must **never** "delete" by clearing text (every item's text is required). `edit_*` with
  empty text is rejected and shows a toast pointing to delete.
- Goal/target deletions open the destructive `ConfirmDialog`; smaller items confirm via the
  card's own action. The frontend only acts when the goal/target/item **actually exists**
  (otherwise an explanatory toast, never a phantom dialog or silent no-op).
- The assistant **cannot** delete resources, notes, or a deadline — it explains the user
  removes those manually (× / trash / Clear control).

## 8. Required-text editing (manual, inline)

- Goal name, target name, option, obstacle, action, and checklist item text are
  **required**. Clearing one inline must **restore the previous text** and show a short
  **"can't be empty" message right next to that field** — never a top-of-page error and
  never a "Try again" button. The empty value is never sent to the store/backend.
- Implemented by `InlineText` (contentEditable, `required` default true) and the goal title's
  `AutoTextarea` (`required`, local buffer so the user can clear-and-retype without the empty
  value being pushed).

## 9. Iconography & tone

- **No emoji anywhere** — UI and assistant replies. Use lucide icons only (`PATHS` + `Ic`).
  Suggestion chips, error bubbles, and badges all use lucide paths.
- All-Goals empty-state suggestions: *Help me create a new goal* (trophy), *Change a goal's
  confidence or deadline* (pencil), *Delete a goal* (trash).

## 10. Scope of the All-Goals chat

- From the All-Goals overview the assistant can only change a goal's **name, confidence,
  deadline**, or create/delete a goal. Anything inside a goal (description, targets, options,
  reality, notes) needs the goal open: it proposes `open_goal` with the concrete subject, the
  card explains "you can't edit «the description» here — open «Goal»", and on open the user's
  original request is **re-run inside the goal** so the right card appears (handoff via
  `localStorage`, read-once).
- It must **ask which goal** when the user didn't name one and more than one exists; it never
  invents an id.
