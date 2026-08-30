# The Android chat does not auto-scroll when the keyboard opens

- **ID:** BUG-066
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-29 — "на андроид автопрокрутка чата не работает, когда
  появляется клавиатура", with a screenshot: the keyboard up, a long reply still streaming, the
  reply pinned to the top of the transcript and running on behind the composer
- **Area:** Android (`ui/ai/AiChatScreen.kt`)
- **Severity:** Medium — the answer being written is off-screen, and the Android half of BUG-065's
  bargain (a sheet that keeps its height, and scrolls instead) is not kept

## Summary

The Android counterpart of BUG-065. Both of the chat's scroll effects — the one that follows a
reply as it streams, and the one that brings the conversation down to meet the composer when the
keyboard opens — asked for `scrollToItem(messages.lastIndex)`, which puts that item's **top** at
the top of the viewport. That is not the end of the conversation.

It looked like the end because a list cannot scroll past its own end. With the composer at its
resting height there is barely any padding below the last message, so the call **clamps** and
lands at the true end by accident. The keyboard removes the accident: its height becomes the
transcript's bottom `contentPadding` (the footer is `imePadding()`-ed and measures itself into
it), so there is suddenly a keyboard of room below the last message, the clamp stops biting, and a
reply taller than the panel is parked on its **first line**.

The streaming effect is what the screenshot caught, because it re-fires on every chunk and so has
the last word over the keyboard's.

## Steps to reproduce

1. Open the AI coach on a phone and ask something that gets a long answer — longer than the panel.
2. While it streams, tap the composer so the keyboard comes up.
3. The reply sits at the top of the transcript with its remainder behind the composer, and nothing
   scrolls it down.

## Root cause

`scrollToItem(index)` positions an item's top edge, not the list's end, and the difference is only
visible when the list has room to honour it — which is exactly what the keyboard's padding gives
it. The keyboard effect did try to walk on past with `scrollBy(Float.MAX_VALUE)`; the streaming
effect (`animateScrollToItem`) had no such follow-through at all and overrode it.

## Fix approach

One helper, `scrollToConversationEnd`, used by **both** effects: `scrollToItem(lastIndex)`, then
walk on a viewport at a time — a lazy list only measures what it can see — until `scrollBy`
reports it consumed nothing. The streaming follow became instant rather than animated, for the
reason the keyboard effect already was: the content it is chasing grows every few frames, so an
animation is cancelled and restarted before it can settle. The web does the same thing
(`AiPanel.tsx` puts the transcript at `top: 99999` on every chunk).

## How to verify fixed

`ChatScrollsToTheEndTest` and `ChatScrollsToTheEndWhenTheKeyboardOpensTest` render the real panel
with a reply taller than it — one arriving as a stream, one already there — and assert the
transcript's scroll range reports `value == maxValue`, which is exactly "nothing further down".

Both need a **keyboard**, and Robolectric's window reports no IME inset of its own (the blindness
`ChatFooterConventionTest` documents), so one is dispatched by hand onto Compose's own view.
Without it the tests render the accidental clamp and pass against the defect — they were checked
red against `scrollToItem` alone (1000 of 1100, and 500 of 600).

## Resolution

Fixed 2026-08-29 in `ui/ai/AiChatScreen.kt`. The transcript now lands on the end of the
conversation in both cases; a picture of the panel with the keyboard up shows the last paragraph
and its Copy row resting directly above the composer.
