# On iOS Safari the keyboard covers the bottom of a sheet

- **ID:** BUG-062
- **Status:** 🐞 Open
- **Reported by:** Agent, 2026-08-29 — a consequence of the BUG-060 fix, surfaced rather than
  shipped silently
- **Area:** Web (`src/components/ui/drawer.tsx`, `src/lib/spira/sheet-height.ts`)
- **Severity:** **Low for now** — nobody on the project uses iOS Safari; the owner tests on
  Android and on the desktop, and the native app is Android-only. It becomes real the day an
  iPhone opens Spira.

## Summary

Fixing BUG-060 meant passing `repositionInputs={false}` to vaul's `Drawer.Root`. On Chromium that
removes a broken height writer and nothing else, because `index.html` asks for
`interactive-widget=resizes-content` and the browser sits the sheet above the keyboard itself.

**iOS Safari does not support the `interactive-widget` viewport key.** There, the keyboard does not
resize the layout viewport at all — it covers it. So a sheet anchored to `bottom: 0` keeps its full
height and the keyboard hides its bottom: the AI composer, a form sheet's Cancel / Add buttons.
vaul's `repositionInputs` used to lift the sheet by the keyboard's height, and no longer does.

Turning the flag off also disables vaul's `usePreventScroll`, whose only effect is the iOS-Safari
scroll workaround (`preventScrollMobileSafari`) — so on iOS the page behind a modal sheet may also
scroll on touch.

## Steps to reproduce

Not reproduced — there is no iOS device or simulator on this project, and no headless browser
models the iOS keyboard. Stated from the code rather than from a screen, which is exactly why it
is filed instead of fixed.

1. Open Spira in Safari on an iPhone.
2. Open the AI coach, or New goal.
3. Tap the text field.
4. Expected: the field and the buttons under it stay visible. Likely actual: the keyboard covers
   the bottom of the sheet.

## Fix approach

Do **not** turn `repositionInputs` back on — that is the BUG-060 defect. Do the same job from the
module that already owns this measurement, `src/lib/spira/sheet-height.ts`, so it composes with
`--app-vh` in CSS instead of fighting it with inline styles:

```ts
// one more published variable, alongside --app-vh
const overlap = Math.max(0, innerHeight - visualViewport.height - visualViewport.offsetTop);
document.documentElement.style.setProperty("--app-kb", `${overlap}px`);
```

```css
.sheet-h-92 { height: min(calc(var(--app-vh) * 92), calc(100dvh - var(--app-kb, 0px))); }
```

plus `bottom: var(--app-kb, 0px)` on `DrawerContent` in place of `bottom-0`.

On Chrome for Android `--app-kb` is **0** in the steady state — the owner's own device readout
(`public/viewport-check.html`, 2026-08-28) shows `window.innerHeight` and `visualViewport.height`
both 888 — so the expression is inert there and nothing about the current behaviour changes. On
iOS it is the keyboard's height and the sheet lifts.

The risk to weigh before shipping it: during the keyboard's opening animation the two heights
diverge for a frame or two even on Android, so `--app-kb` spikes and the sheet could visibly jump —
the exact symptom BUG-060 was about. **Measure it on the owner's phone before merging**, and if it
jumps, only publish a value that has held for two consecutive frames.

## How to verify fixed

On a real iPhone: open each sheet with a field (AI coach, AI providers, New goal, New target, Add a
resource, Add a link), tap the field, and confirm the field and the footer buttons stay above the
keyboard. Then re-check on the owner's Android phone that nothing jumps — the point of the change
is that Android is unaffected.
