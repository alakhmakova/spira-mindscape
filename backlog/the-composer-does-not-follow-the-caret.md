# The composer scrolls, but not to the caret — you type into text you cannot see

- **ID:** BUG-052
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-25 — "ты сделал скролл для длинных сообщений в чате, но он не
  автоматический, из-за этого часть сообщения все равно оказывается спрятанной… курсор спрятан на
  строке где grow session"
- **Area:** Android (`ui/ai/AiChatScreen.kt` — `Composer`)
- **Severity:** High — past four lines the message is being written blind

## Summary

Type a long message in the AI composer. The field stops growing, as it should — but it does not
follow the cursor. Past the fourth line the caret is below the visible window, hidden behind the
row of actions, and the only way to see what you are writing is to drag the field by hand.

This is a fault **introduced by the fix for the previous one** (the cap that
stopped a long message pushing the buttons off screen, 2026-08-24). The cap was right; how it was
written was not.

## Steps to reproduce

1. Open the AI coach on Android, in ordinary chat or in a GROW session.
2. Type five or six lines into the composer.
3. The caret is no longer on screen; the text you are adding is out of view.

## Root cause

```kotlin
Modifier.heightIn(max = COMPOSER_MAX_HEIGHT).verticalScroll(rememberScrollState())
```

`BasicTextField` has its own scroller, and that one **keeps the cursor in view**. But an outer
scroll container measures the field with unbounded height, so the field hands scrolling over to the
parent — and the parent knows nothing about where the caret is. The result scrolls, and scrolls to
the wrong place.

## Fix approach

Constrain the height on the field **itself** and remove the wrapper:

```kotlin
Modifier.fillMaxWidth().heightIn(max = COMPOSER_MAX_HEIGHT).padding(…)
```

That turns the internal scroller back on, and it follows the caret. The web has never had the
problem: a browser scrolls a focused `textarea` to its caret by itself, and the cap there is a
height in JS rather than a wrapper.

## How to verify fixed

- `ui/ai/ComposerClearsTest` — "the composer field is not wrapped in a scroll container": a source
  scan, confirmed to fail when the `verticalScroll` is put back. It reads the source rather than
  driving the screen because Robolectric has no IME and the panel has slack, so the field never
  runs out of room there; two earlier Compose attempts passed with the fix removed entirely.
- `e2e/composer-long-message.spec.ts` — the web's own version, measured properly: the textarea
  stays capped, `scrollTop > 0`, the caret's line is inside the visible window, `overflow-y` is not
  `hidden`, and Send and Attach are still reachable.
- By hand on the emulator: six lines typed, caret and action row both on screen (2026-08-25).

## Resolution

Fixed 2026-08-25.

The lesson worth keeping: **a height cap and a scroll wrapper are not the same tool.** If a text
field needs to stop growing, constrain the field; wrapping it takes away the one thing that knows
where the cursor is.
