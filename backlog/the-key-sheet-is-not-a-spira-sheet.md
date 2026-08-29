# The "Bring your own key" sheet is not a Spira sheet — no head, wrong height, and the title scrolls away

- **ID:** BUG-061
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-28 — "drawer где ключи сохранять он короче чем обычный drawer —
  думаю это неверно, и у него нет шапки, весь контент скроллится, унося важную информацию"
- **Area:** Web (`src/components/ai/AiPanel.tsx` → `ProviderSheet`), Android
  (`ui/ai/ProviderSheet.kt`)
- **Severity:** **Medium** — nothing is lost, but this is the screen where a user pastes an API key,
  and the sentence explaining that keys are stored encrypted scrolls out of sight

## Summary

The AI-providers sheet was written before the sheet spec of 2026-08-22 (CLAUDE.md → Design →
Components and chrome → 3e) and never converted. It breaks the same three rules on **both**
surfaces, which is why the owner noticed it on the phone and asked for both to be checked.

| The rule | Web | Android |
|---|---|---|
| A sheet has the **Kale head band** (`SheetHead` / `SpiraSheetHead`) | ✗ white area, an uppercase kicker and a Playfair heading | ✗ a `Kicker` + `Text` in the body |
| **No grab handle, on either surface, ever** | ✗ `w-[38px] h-1 rounded-full bg-[#E5E5E5]` drawn by hand | ✗ Material's default — `ModalBottomSheet` is called without `dragHandle = null` |
| The **head is fixed and the body scrolls**, not the whole card | ✗ `overflow-y-auto` is on the outer container | ✗ `.verticalScroll()` is on the `Column` that contains the head |

And the height, which is what the owner led with:

- **Web**: `max-h-[88%]` — and it is `absolute inset-0` **inside the AI panel**, so that is 88 % of
  the panel, which is itself `88dvh`. About 77 % of the screen, against the 92 % the app's other
  sheets use. Shorter than every other drawer, exactly as reported.
- **Android**: `heightIn(max = 620.dp)` — a fixed cap, so on a tall phone it is a short card
  floating at the bottom while other sheets fill their content.

The scrolling head is the part with real consequence. The sheet's explanation —

> Keys are stored encrypted on your account. Keep several connected and switch anytime.

— is the only place the app says what happens to a pasted API key, and it scrolls away as soon as
the user reaches the provider they want. So does the X. That is the "унося важную информацию" in
the report.

## Steps to reproduce

1. Open the AI coach on a phone, tap **Bring your own key**.
2. Note the sheet is shorter than, say, the Filter & Sort sheet, and has a grey grab handle.
3. Scroll down to the third or fourth provider.
4. The title "AI providers", the encryption sentence and the close button are all gone.

Both surfaces, identically.

## Root cause

It is hand-rolled. On the web `ProviderSheet` is not a `Drawer` or a `Sheet` at all — it is an
`absolute inset-0` overlay with its own `slideUp` animation, so none of the shared drawer's
behaviour applies to it. On Android it is a `ModalBottomSheet` but with the body and the head in
one scrolling `Column`, and without `dragHandle = null`.

It predates the spec, and the spec's own list of stragglers (CLAUDE.md names the note editor's
"Add a link" and the numeric "Update Progress" as the two web sheets still to convert) **does not
mention this one** — so it was not merely deferred, it was missed. Worth adding to that list when
this is fixed, or removing the list in favour of a test.

## Fix approach

- **Web**: replace the hand-rolled overlay with the app's `Drawer` on mobile / `SheetContent
  side="right"` on desktop, `SheetHead` for the head, the body as its own `overflow-y-auto flex-1
  min-h-0` scroller, and the same 20 px gutter. Drop the hand-drawn grab handle.
- **Android**: `dragHandle = null`, `SpiraSheetHead("AI providers", onDismiss)` outside the
  scroller, and the `verticalScroll` moved onto the body `Column` only. Drop `heightIn(max = 620.dp)`
  so it sizes like the other sheets.
- Keep the wording identical on both surfaces, per the spec.

## How to verify fixed

- **Look at the pixels, not at assertions** (CLAUDE.md → Components and chrome → 4). On Android
  render `ProviderSheetContent` directly in a `VisualCheck*` test — a modal sheet renders in its own
  window and the decorView screenshot cannot capture it, which is why the existing check renders the
  content composable. Confirm: Kale band, no handle, and the head still visible after scrolling to
  the last provider.
- A Playwright spec at a phone viewport: open the key sheet, scroll to the bottom provider, assert
  the heading and the close button are still in the viewport.
- Both surfaces side by side — the spec's rule is that when they disagree, the web wins and they
  must end up the same.

## Resolution

Fixed 2026-08-28, on both surfaces.

- **Web**: `SheetHead` for the head, the card is a `flex flex-col overflow-hidden` with the body
  as its own `min-h-0 flex-1 overflow-y-auto`, and the hand-drawn grab handle is gone. The height
  goes from `max-h-[88%]` to `max-h-[92%]`, matching the proportion the app's other sheets use.
- **Android**: `dragHandle = null` and the sheet's own 12dp top corners, `SpiraSheetHead`
  outside the scroller, and `verticalScroll` moved onto the body `Column`. The
  `heightIn(max = 620.dp)` cap is gone, so it sizes like every other sheet.

Verified by looking at the pixels on both, as the design rules require rather than by assertion:
a Playwright screenshot at 412×780 (head band present, no handle) and the same after scrolling to
the last provider (**the head stays put**, and the body reaches the Tavily card and the footer);
and `VisualCheckProviderSheetTest` re-rendered on Android, showing the same band.

## Correction, 2026-08-28 (after merge)

**The height was still wrong, and the owner caught it on the merged build**: "drawers чата и
ключей короткие, они должны занимать почти всю высоту с небольшим отступом от верхнего края
экрана, как и другие drawer."

Measured in a browser at 880 px rather than argued about:

| | Before | Every other sheet | After |
|---|---|---|---|
| Chat drawer | 774 px = **88 %** | 92 % | 810 px = **92 %** |
| Key sheet | 712 px = **81 %** | 92 % | 810 px = **92 %** |

Two mistakes, both mine:

- **`mt-0` was missing** on the chat drawer. Every other caller passes it to cancel
  `DrawerContent`'s base `mt-24`; this was the only one that did not.
- **The key sheet was `max-h-[92%]` of the AI panel**, and the panel is itself a fraction of the
  screen — so 92 % of 88 % came out at 81 %. It is `h-full` now, which lands it on the panel's own
  92 dvh, and it is a fixed height rather than a `max-`, so a short list no longer makes a short
  sheet.

Both now sit at exactly the number the app's other sheets use, with the same 70 px gap from the
top of an 880 px viewport.

