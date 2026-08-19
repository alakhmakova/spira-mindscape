# Android: the keyboard covers the field you are typing in on the goal workspace

- **ID:** BUG-042
- **Status:** ✅ Fixed (2026-08-18) — see Resolution.
- **Reported by:** User (2026-08-18) — "при нажатии на Add task внутри targets на андроид и попытке
  писать название task клавиатура перекрывает поле для ввода"
- **Area:** Android — `ui/goals/GoalWorkspaceScreen.kt`
- **Type:** Defect

## Summary

Open a target, tap **Add task**, and start typing: the keyboard comes up **over** the field. You
are typing into something you cannot see.

It is not limited to Add task. Every field on a phase screen has it — the goal title and
description, a reality item, an option's text, a task's own inline edit — anything low enough on
the screen that the keyboard reaches it.

## Steps to reproduce

1. Open a goal → **Will do** → open a checklist target far enough down the list that it sits in the
   lower half of the screen.
2. Tap **Add task**.
3. The field opens and the keyboard rises over it. Typing works, but nothing is visible.

## Root cause

`MainActivity` calls **`enableEdgeToEdge()`**. That sets `decorFitsSystemWindows = false`, and from
that moment the manifest's `android:windowSoftInputMode="adjustResize"` **stops resizing the
window** — the IME inset becomes the app's to apply, in Compose.

Three screens already did apply it, and they are exactly the three where typing has always felt
right: the AI panel (`AiChatScreen`), the note editor (`NoteEditorActivity`) and the provider sheet
(`ProviderSheet`) all carry an `imePadding()`. The **goal workspace had none**, so its `Scaffold`
kept the full window height and the keyboard simply covered the bottom of it.

Nothing in the workspace was wrong in itself, which is why this survived so long: the manifest says
`adjustResize`, and it reads as though the platform is handling it.

## Fix approach

Apply the IME inset where the workspace's own layout is decided: `Modifier.imePadding()` on the
workspace `Scaffold`. The content then ends above the keyboard, and Compose's built-in
bring-into-view scrolls the focused field into what is left.

## How to verify fixed

- Open a target low on the screen → **Add task** → the field stays visible above the keyboard while
  typing.
- The same for the goal title/description, a reality item and an option's text.
- With the keyboard down, nothing about the workspace has moved.

## Resolution

**2026-08-18 — fixed.** `GoalWorkspaceScreen`'s `Scaffold` takes `Modifier.imePadding()`.

**Not verified on a device by the agent**: reaching the goal workspace needs a completed Google
sign-in, which only the owner can do (CLAUDE.md → "Diagnosing the app"). The diagnosis is from the
code and is unambiguous — `enableEdgeToEdge()` plus a missing `imePadding()` — and the three
screens that behave correctly are the three that have the modifier. Confirm on the phone.

> **The general lesson, worth remembering before the next screen is written:** in this app
> `adjustResize` in the manifest does **nothing**, because `enableEdgeToEdge()` is on. Any screen
> with a text field needs `imePadding()` in its own Compose tree.
