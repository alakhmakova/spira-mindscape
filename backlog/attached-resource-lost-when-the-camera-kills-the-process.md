# An attached resource is lost when the camera kills the process — but the photo survives

- **ID:** BUG-050 — filed as BUG-042 on 2026-08-23, which was already taken by
  `android-keyboard-covers-fields-on-the-goal-workspace.md` (and mirrored as `GRO-142`).
  Renumbered 2026-08-24; the earlier holder keeps 042.
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-23 — "прикладываю ресурс, делаю фото, перезагрузка приложения —
  ресурс исчезает, а фото остаётся"
- **Area:** Android (`ui/ai/AiChatViewModel.kt`, `ui/ai/ChatAttachments.kt`)
- **Severity:** High — silent data loss in the composer, and the asymmetry makes it look like the
  resource was never attached rather than lost

## Summary

Attach one of the goal's saved resources, then take a photo. If the camera has caused the process
to be killed — routine on a phone with a memory-hungry camera app — the photo comes back and the
**resource is gone**, with nothing said.

The asymmetry is the tell, and it was self-inflicted. The camera's destination `Uri` is held in
`rememberSaveable` (`ChatAttachments.kt`), deliberately, so that a killed process still delivers the
photo. The composer's attachments were held in a plain `MutableStateFlow` inside the view model,
which survives *recreation* but not *process death*. So the half that was saved came back and the
half that was not did not.

The comment above that state said "The ViewModel survives that recreation, so what was
half-composed survives with it" — true for a configuration change, false for the case the camera
actually causes, and the reason this went unnoticed.

## Steps to reproduce

1. Open a goal with at least one saved resource, open the AI coach.
2. Paperclip → **From resources** → pick one. The chip appears.
3. Paperclip → **Take a photo**, take one.
4. Return. (To force it without a low-memory device: enable *Developer options → Don't keep
   activities*, or `adb shell am kill com.spiramindscape.android` while the camera is in front.)
5. The photo chip is there; the resource chip is not.

## Root cause

`AiChatViewModel` had no `SavedStateHandle`. Composer draft and attachments lived only in memory.

## Fix approach

The view model now takes a `SavedStateHandle` and writes the draft and the **resource** chips
through to it, restoring both on construction.

Photo and file chips were deliberately **not** saved in the first round: they carry their bytes as
a data URL, often megabytes, and saved state travels to the system in a Bundle where that is a
`TransactionTooLargeException` waiting to happen. The argument was that a photo needs no saving —
it returns by its own road, the redelivered camera result against the saved destination Uri.

**That argument was too narrow, and the second round fixed it** (see below).

## How to verify fixed

`ui/ai/ComposerAttachmentsTest` builds a view model, attaches a resource, then builds a **second**
view model from the same `SavedStateHandle` — process death in miniature — and asserts the chip and
the draft come back, that no attachment's **bytes** are among what was saved, and that sending
clears the saved copy too.

## Follow-up: the byte-carrying chips too (2026-08-24)

"A photo returns by its own road" is true of *the shot that caused the kill* and of nothing else. A
file picked from storage has no redelivery to lean on, and neither does a photo taken **earlier**
in the same message — both were still lost without a word. The owner asked for the remaining half.

`ChatAttachments.readAttachment` now **parks the bytes** it has just read (the downscaled JPEG, or
the document as-is) in `cacheDir/composer/`, and the chip carries `cachePath`. The view model
persists rows of `kind|payload|mime|name` — `r|<resourceId>` or `f|<path>` — so what goes into the
Bundle is still only a handful of characters, and rebuilds the data URL from the file on restore.
The file is deleted when the chip is removed, sent, or pushed off the end by the six-attachment
cap; `pruneComposerCache` sweeps anything a killed process left behind, after a day.

A chip whose file has since gone — swept, or cleared with the app's cache — is dropped rather than
restored as a name that would send nothing.

Covered by four more assertions in `ui/ai/ComposerAttachmentsTest`: a photo comes back from its
parked copy, the **bytes never appear in a saved row**, a chip with no file behind it is dropped,
and removing a chip deletes what it parked.

## Resolution

Fixed 2026-08-23, completed 2026-08-24. Found together with BUG-046, the visible half of the same trip through the camera:
the panel closing itself on recreation.

The first attempt at the fix shipped a bug of its own, which the new test caught before it left the
machine: the persisted rows were built with a string that looked like interpolation but was a
literal, so every chip was written as the text `${it.resourceId}|…` and silently dropped on restore.
