# Confidence does not take on the first tap after typing — the keyboard just closes

- **ID:** BUG-064
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-29 — "пишу текст, например, название цели или описание и после
  этого не закрывая клавиатуру нажимаю на confidence — новая оценка не ставится, а просто
  закрывается клавиатура, выглядит как будто confidence не работает с первого раза"
- **Area:** Web (`src/lib/spira/sheet-height.ts`)
- **Severity:** **High** — it is not only Confidence. **Every** control in a sheet is affected:
  the first tap on anything, after typing, is swallowed. It only reads as a Confidence bug
  because that is where a lost tap is most obvious.

## Summary

In the New goal sheet on a phone: type a title, and then — without dismissing the keyboard — tap a
Confidence number. The keyboard closes and nothing else happens. The second tap works.

**No `click` event is dispatched at all.** Measured on a 412×430 viewport (what is left above an
Android keyboard under `interactive-widget=resizes-content`), with the title focused, at every
finger travel from 0 to 12 px:

| | Events the button received | Result |
|---|---|---|
| Before the fix | `pointerdown touchstart pointerup touchend mousedown` | **no `click`** — value unchanged |
| After | `pointerdown touchstart pointerup touchend mousedown click` | value set |

## Root cause

`sheet-height.ts` publishes `--app-vh` — one percent of the keyboard-free viewport — and it
listened for **`focusout`** as well as `resize`, on the reasoning that "a field losing focus is
the keyboard going away".

It is not. It is the keyboard *starting* to go away; for the next couple of hundred milliseconds
the viewport is still the small one. So `focusout` ran `publish()` at the single instant when
`couldBeTyping()` had just turned false while `window.innerHeight` was still keyboard-sized, and
the guard `if (height < stableHeight && couldBeTyping()) return;` no longer held. It therefore
**republished the keyboard's height as the screen's**: `--app-vh` 7.8 → 4.3.

Every sheet is `min(calc(var(--app-vh) * 92), 100dvh)`, so the sheet lost 34 px — and a sheet is
`position: fixed; bottom: 0`, so shrinking it moves everything inside it **down**. The sequence
inside one tap:

1. `mousedown` on the Confidence button → the title field blurs
2. `focusout` → `publish()` → `--app-vh` drops → the sheet shrinks → the button moves
3. `mouseup` lands on whatever is now at those coordinates
4. Chrome dispatches `click` only when `mousedown` and `mouseup` share a target — so **no click**

From the user's side: the keyboard closes, and the tap is gone.

The listener was not merely early, it was **useless**: `stableHeight` already holds the
pre-keyboard maximum for as long as a field is focused, so the case it was written for — "the
last keyboard-shrunk height could be the one that sticks" — cannot arise. All it could ever do
was shrink. The keyboard closing resizes the layout viewport, and `resize` already publishes.

## Fix

Delete the `focusout` listener. Three lines, in `trackViewportHeight()`.

## How to verify fixed

`e2e/sheet-chrome-stays-put.spec.ts` → *"tapping Confidence right after typing sets the value on
the FIRST tap"*. It uses a **real touch** (`hasTouch`, `page.touchscreen.tap`), because the defect
is in what Chrome does with a `click` whose element moved between `mousedown` and `mouseup` —
Playwright's synthetic `.click()` never goes through that path, which is why every existing spec
missed it. **Checked red with the listener restored: 5/10 where 7/10 was expected.**

By hand, on a phone: New goal → type a title → without closing the keyboard, tap Confidence 7. It
must read 7/10 after one tap.

## Resolution

Fixed 2026-08-29. Third bug of the session in the same family, and the sharpest statement of the
lesson: **something other than the component's own CSS was moving it.** BUG-060 was vaul writing
an inline height; BUG-063 was a `scrollIntoView` scrolling a box that should never have been a
scroll container; this one was our own viewport module republishing at the wrong moment. The
common tell in all three is a sheet that changes size at an instant nobody asked it to.
