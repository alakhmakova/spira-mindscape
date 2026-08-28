# The chat drawer changes height while you use it, and then squeezes the content out of reach

- **ID:** BUG-060
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-28 — "drawer чата меняет свой размер — уменьшается по высоте …
  внезапно контент потом вообще сузился так что стало невозможно использовать чат", with three
  screenshots from Chrome on Android over a cloudflared tunnel
- **Area:** Web (`src/components/ai/AiPanel.tsx`), and to be checked on Android
  (`ui/ai/AiChatHost.kt`)
- **Severity:** **High** — the assistant is the product's centre and the owner reached a state
  where the chat could not be used at all

## Summary

On a phone the chat drawer does not keep a stable height. Over three screenshots taken in half an
hour on the same device and the same page:

| Time | What the screenshot shows |
|---|---|
| 13:53 | Drawer with its teal head visible ("spira ai coach", the key row, the model). Occupies roughly half the screen |
| 14:20 | **No head at all** — the drawer's top edge is a mid-transcript "Copy" affordance. A block of empty teal below the content |
| 14:22 | Same, and a proposal card is cut off across its Accept / Edit buttons, which cannot be reached |

Two distinct symptoms, probably one cause:

1. **The drawer's height changes** during ordinary use.
2. **The content ends well above the drawer's bottom edge**, leaving a dead teal band, while the
   head has scrolled off the top — so the whole drawer is scrolling as one block rather than the
   transcript scrolling inside a fixed frame.

## What is confirmed in the code

**The web drawer is sized in `dvh`** (`AiPanel.tsx:635`):

```tsx
<DrawerContent className="h-[88dvh] flex flex-col px-0 border-0 bg-[#0A8080] text-white">
```

`dvh` is the *dynamic* viewport unit: it is **defined** to change as the browser's UI expands and
collapses. On Chrome for Android the toolbar hides and reappears as the page scrolls, and the
on-screen keyboard shrinks it again — so a `dvh` height on a fixed overlay is a height that moves
while the user is typing and scrolling. `svh` (the small viewport, toolbar always assumed present)
is the stable unit for exactly this case.

**Android sizes the same drawer by fraction** (`AiChatHost.kt:137`, `DRAWER_HEIGHT_FRACTION = 0.88f`),
which does not have the `dvh` problem — but it has not been checked against the same symptoms and
the owner asked for it to be.

**A percentage cap sits inside the flex column** (`AiPanel.tsx:3065`):

```tsx
<div className="px-3 pb-3 pt-1 shrink-0 max-h-[60%] overflow-y-auto …">
```

`shrink-0` plus `max-h-[60%]` on a proposal card area, inside a `flex-1 min-h-0` column. When the
drawer shrinks, a `shrink-0` child cannot give ground; if the sum of the header, the transcript's
minimum, this card area and the composer exceeds the new height, the column overflows its own
frame. That is a plausible mechanism for both "the head scrolled away" and "the card is cut across
its buttons" — and the 14:22 screenshot is a proposal card, which is when this element exists.

## Steps to reproduce

Not yet reduced to a reliable sequence — that is the first job. What is known:

1. Open a goal on a phone browser (the owner used Chrome on Android through the tunnel).
2. Open the AI coach drawer.
3. Hold a conversation long enough to scroll, and get the assistant to produce a **proposal card**
   (e.g. ask it to revise a resource note).
4. Observed: the drawer's height changes, the head scrolls out of view, and the card's buttons end
   up below the visible area.

## What to investigate first

- **Reproduce with devtools attached**, not by reading CSS. Chrome on Android can be inspected over
  `chrome://inspect`; log `window.innerHeight`, `visualViewport.height` and the drawer element's
  measured height on `resize`, `scroll` and `visualViewport.resize`, and watch which one moves when
  the symptom appears.
- Confirm whether the drawer itself is scrolling (the head leaving the top is the tell) or whether
  it is being translated by vaul's drag.
- Then decide between `svh`, `100%` of a fixed-position container, or pinning the height once on
  open.
- **Check Android against the same sequence** before assuming it is web-only.

## Fix approach (provisional — do not apply before reproducing)

Likely `88dvh → 88svh`, plus making the proposal-card area able to shrink rather than
`shrink-0 max-h-[60%]`. Both are one-line changes, which is exactly why they should not be made on
a hypothesis: the failure is a layout interaction, and a plausible-looking edit that does not fix
it is worse than none, because it will be believed.

## How to verify fixed

- The reproduction above, on a real phone, with the browser toolbar collapsing and the keyboard
  opening and closing: the drawer's top edge must not move, and the head must stay put.
- A Playwright spec at a mobile viewport that opens the drawer, surfaces a proposal, and asserts
  the Accept button is in the viewport — the existing specs press it, so a cut-off card would fail
  them, but only if a spec exercises this at a phone size.
- On Android, the Compose visual check (`VisualCheck*`) rendering the chat host with a proposal
  pending — per CLAUDE.md, look at the PNG rather than at an assertion.

## Resolution

Fixed 2026-08-28 (web). Reproduced first, as the section above insisted.

**What the reproduction showed, including where the hypothesis was wrong.** A Playwright spec at
412 px wide opens the drawer with a proposal pending and then shrinks the viewport, which is what
Chrome's toolbar and the keyboard do. At 412×420 it **passed on the unfixed code** — so
`setViewportSize` does not reproduce the owner's symptom, and that is itself the finding: the
keyboard case was already handled, by `interactive-widget=resizes-content` in `index.html`, which
shrinks the *layout* viewport so the units follow it. At **412×300** it failed, with Accept below
the drawer's edge — the owner's second screenshot, reproduced.

So there were two defects and one non-defect:

1. **`h-[88dvh]` → `h-[88svh]`.** `dvh` is defined to move as the browser's UI expands and
   collapses, and Chrome for Android hides and re-shows its toolbar on every scroll — so the
   drawer resized while being read. `svh` assumes the toolbar is always there and never changes
   with it.
2. **The proposal-card area could not give ground.** It was `shrink-0 max-h-[60%]` in a flex
   column, so on a short drawer it kept its size while everything else was squeezed, and its
   Accept row went past the bottom edge. It is now `min-h-0 flex-1 basis-auto`, and **the card's
   action row is `sticky bottom-0`** so Accept is reachable however tall the proposal is.
3. **The keyboard was not a defect** — already fixed by the viewport meta. Worth recording, since
   it was the obvious suspect.

**Android was checked and left alone, with numbers.** It sizes by `fillMaxHeight(0.88f)`, so it
has no `dvh` problem, and its card host is capped at 460–520 dp inside a drawer that is ~805 dp on
an ordinary phone — comfortable even with the keyboard up, since `imePadding()` is applied. The
structure is the same shape as the web's, so if a short-screen report ever arrives, the fix is to
lift the action row out of that scroller.

## How it is kept fixed

`e2e/ai-drawer-height.spec.ts` — two cases at 412×300, with the model and **the transcript**
stubbed. Stubbing the transcript matters: the first run opened on the developer's own saved
conversation, with a card already pending and the composer therefore hidden, so the spec was
testing whatever happened to be in the database. **Confirmed red on the unfixed layout** and green
after.
