# The keyboard covers the field you are typing into on a proposal card

- **ID:** BUG-051
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-24 — "при редактировании карточки поле для ввода наполовину
  скрыто клавиатурой… проверь и обычные чаты и grow session"
- **Area:** Android (`ui/ai/AiChatScreen.kt`), Web (`index.html`, the bottom sheets)
- **Severity:** Medium — you can see roughly half of what you are typing, and on the GROW record
  card the Save / Discard buttons were under the keys as well

## Summary

Tap **Edit** on a proposal card, or the "Change the record…" field on the GROW session-record
card, and the keyboard opens **over** the card. The field is half-hidden; on the record card the
two buttons below it are gone entirely.

It happened in ordinary chat and in a GROW session alike, and the web had the same fault on a
phone browser.

## Root cause

**Modifier order.** Compose applies modifiers outside-in, and both card hosts had:

```kotlin
Modifier.fillMaxWidth().background(…).heightIn(max = …).verticalScroll(…).imePadding()
```

With `imePadding()` **last** it applies innermost: the keyboard's height becomes padding on the
*scrollable content*, so the box stays exactly where it was — under the keyboard — and the padding
it gained is only reachable by scrolling. The composer had it first all along, which is why typing
a message worked while typing into a card did not.

On the **web** the equivalent is that the on-screen keyboard is drawn over the page and the layout
viewport does not change, so a sheet measured in `vh` keeps its full height and the field you just
tapped ends up behind the keys.

## Fix approach

- Android: `imePadding().navigationBarsPadding()` moved **before** the height cap and the scroll,
  in both card hosts — the proposal card (chat and the GROW review step) and `cardHost` (the
  record, review and farewell cards).
- The record preview gives up height while the keyboard is open (200dp → 96dp) so Save memory and
  Discard stay on screen; it is the one part of that card that is scrollable in its own right.
- Web: `interactive-widget=resizes-content` on the viewport meta, and `vh` → `dvh` on every bottom
  sheet (the AI panel, New goal, Filter & Sort, Targets, Resources, the note editor).

## How to verify fixed

By hand on the emulator, which is the only place an IME exists: open a proposal card's Edit box in
ordinary chat and in a GROW session, and the record card's "Change the record…" — field and buttons
are above the keyboard in all three. Done 2026-08-24.

There is no automated guard for the ordering itself; Robolectric has no keyboard, so a Compose test
passes whether or not the fix is present (proved twice while writing one — see the note in
`ComposerClearsTest`).

## Resolution

Fixed 2026-08-24. Found the same day as BUG-052, and the two share a cause in spirit: a layout rule
that only misbehaves when a real keyboard is on screen.
