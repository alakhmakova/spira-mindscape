# BUG-086 — The CV conversation does not follow you between devices

**Status:** ✅ Fixed (2026-09-15, awaiting the owner's live check)
**Area:** AI chat — CV writer · Web
**Severity:** High — turns taken on one device are invisible on the other, and can be overwritten

## Summary

In the owner's CV session of 2026-09-15 the conversation continued on the phone did not appear on
the desktop web, even after a reload. The profile note created on the phone was there; the
messages were not. A previously approved proposal card also came back as pending after the chat
was closed and reopened.

## Steps to reproduce (before the fix)

1. Start a CV application on the laptop and take a turn.
2. Continue it on the phone (Continue list) and take two more turns.
3. Reload the laptop: the panel restores the laptop's cached copy, without the phone's turns.
4. Take a turn on the laptop: its save overwrites the phone's turns on the server.

## Root cause

- The panel's mount restore read only `localStorage`; the server transcript was read only when an
  application was picked from the Continue list.
- Saves were last-write-wins with no revision, so a stale device overwrote newer turns.
- The transcript was mirrored only at the end of a model turn; approving a card changed React
  state alone, so the card returned as pending and could be approved twice.
- `putCvTranscript` never checked the response, so a failed save was silent.

## Fix

- `cv_application.transcript_revision` (V25): bumped on every save; GET returns it; a PUT naming an
  older `baseRevision` is refused with 409.
- The panel reads the server copy on every entry — mount restore included — and keeps the newer
  one; nothing is saved before that read.
- A 409 reloads the server copy and says so; a failed save is shown.
- Every change to the conversation outside streaming (approvals included) is mirrored after a
  short debounce.

## How to verify

- `CvApplicationServiceTest.aStaleTranscriptIsRefused`.
- Live, through the tunnel: take turns on the phone, reload the laptop — the phone's turns are
  shown; approve a card, close and reopen — it stays approved.

## Resolution

Fixed as described. Android has no CV writer yet, so web↔Android sync of it does not arise.
