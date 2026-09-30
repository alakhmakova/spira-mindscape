# BUG-085 — AI note edits overwrite the user's own changes

**Status:** ✅ Fixed (2026-09-15, awaiting the owner's live check)
**Area:** AI chat (every session kind) · Resources · Web + Android
**Severity:** High — the user's own writing is lost on one tap

## Summary

Any AI chat could propose `edit_note`, and approving it replaced the note's whole body with the
text the model sent. The model sent the note from its own memory of it, so everything the user had
typed into the note since — and anything past the 8,000-character read limit — was gone. In the
CV writer session of 2026-09-15 the agent proposed five profile rewrites, each dropping the owner's
manual corrections; the profile survived only because every card was rejected.

## Steps to reproduce (before the fix)

1. Open a goal with a note; ask the chat to read it.
2. Edit the note by hand (add a line).
3. Ask the chat to "add X to the note" and accept its card.
4. The line from step 2 is gone.

## Root cause

- `edit_note` was a full-body replacement (`updateResource` with `body`), on web and Android.
- Nothing checked that the model had read the note, or that the note had not changed since.
- `read_resource` cut notes at 8,000 characters, so even a faithful copy could be truncated.
- The card showed no difference between "adds a line" and "rewrites everything".

## Fix

- `NoteEdit` (jsoup) + GraphQL `editNote` with modes `append` (default), `append_to_section`,
  `merge_sections`, `replace_section`, `replace_all`. Append modes merge into the note as it is at
  approval time and skip what is already there; replace modes require `expectedUpdatedAt` and fail
  with `NOTE_CHANGED` when the note changed.
- The chat loop refuses, before any card is shown, an edit of a note not on the goal, a rewrite
  without a `read_resource` of the current version in the same request, and an edit that changes
  nothing — and tells the model why so it can correct itself in the same reply.
- The payload's `mode`, `baseUpdatedAt` and block `diff` are stamped by the server, never the model.
- Notes are read whole, with a header naming the version and sections.
- Web and Android cards say "Adds to «section»" / "Rewrites the whole note" and show what is added
  and, struck through, what is removed. Both apply through `editNote`.

## How to verify

- `NoteEditTest`, `ResourceServiceEditNoteTest`, `AiChatServiceNoteEditGuardTest`,
  `note-edit-proposal.test.ts`, `NoteEditProposalTest`.
- Live: repeat the steps above — the hand-written line survives; editing the note in another tab
  before accepting a "Rewrites" card shows "This note changed after the suggestion was made".

## Resolution

Fixed as described; see the tests above.
