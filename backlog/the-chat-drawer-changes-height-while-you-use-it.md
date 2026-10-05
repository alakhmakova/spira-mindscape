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

## Correction, 2026-08-28 (after merge): `svh` reverted to `dvh`

The `dvh → svh` half of this fix has been **taken back out**, and it is worth saying why plainly.

The reasoning for it still stands on paper: `dvh` tracks Chrome's collapsing toolbar, so a fixed
drawer sized in it resizes as the page scrolls. But **it was never reproduced by a test** — the
spec that actually went red was about the proposal card overflowing at a short viewport, which is
a separate change and stays. So `svh` was an unmeasured fix.

And it had a cost the owner saw immediately: `svh` is the *smallest* viewport, so with the toolbar
hidden the drawer sits visibly short of the screen. It also made this the only sheet in the app
using a different unit from the other six, all of which are `h-[92dvh]`.

An unmeasured change that makes the app visibly worse and breaks consistency is the wrong trade.
If the resizing does come back, the fix has to be measured first — and it belongs in
`DrawerContent`, since every sheet shares the unit, not in this one caller.

## Second correction, 2026-08-28: the dead space came back, and I put it there

The owner's original report had two halves — the drawer resizing, and a band of empty gradient
under the content. The second half returned on the merged build, with a screenshot of a short
proposal card and a hand's width of nothing beneath it.

**It was this fix that caused it.** The card's block had been `shrink-0 max-h-[60%]`, and I made
it `flex-1 basis-auto` so it could give way on a short drawer. `flex-1` is `flex: 1 1 0%` — and
the `grow: 1` claims a share of the panel whether or not there is anything to put in it. On a tall
phone with a two-line card, that share is empty space.

The correct value is **`flex-initial`** (`flex: 0 1 auto`), where all three numbers matter:

| | | |
|---|---|---|
| `grow: 0` | take no space the card does not need | fixes the dead band |
| `shrink: 1` | give way when the drawer is short | fixes the unreachable Accept |
| `basis: auto` | size to the card | |

plus `max-h-[70%]` so a very tall proposal cannot squeeze the transcript away, and the action row
stays `sticky bottom-0`.

**Why it got through.** `ai-drawer-height.spec.ts` asserted that Accept was *reachable* and that
the drawer fitted the viewport — nothing about what sat **below** the card, and both those things
were true of the broken version. The spec now measures the gap between the card's bottom edge and
the drawer's, at a roomy 412×900 where dead space is visible at all. **Confirmed red on
`flex-1`** and green on `flex-initial`.

The lesson worth keeping: *"Accept is reachable"* and *"the layout is right"* are different
claims, and only the first was being tested.

## Third correction, 2026-08-28: the actual cause, found by looking at the history

The owner ended four rounds of my guessing with one question — *"посмотри на момент например
1 августа, никаких проблем с drawer для чата не было, что изменилось?"* — and the answer was in
`git log`:

| Commit | Date | Height |
|---|---|---|
| c9e6c86 | **1 Aug** | `h-[88vh]` |
| … unchanged all month … | | `h-[88vh]` |
| 223c02f | 18 Aug | `h-[88vh]` |
| **62197ad** "Grow session new logic" | **27 Aug** | **`h-[88dvh]`** |

`git show 62197ad -- AiPanel.tsx` confirms that commit changed **one token** of the drawer's
geometry and nothing else: `vh` → `dvh`. The owner's report starts there.

**Restored to `88vh`.** Not chosen — restored. `dvh` follows Chrome's collapsing toolbar by
definition, and `svh`, which I tried next, is the smallest viewport and therefore sits short.
Both were guesses at the unit. `vh` has a month of evidence behind it.

The number stays 88 on purpose. The complaint was never about 88 versus 92 — *"дело не в том 92
или 88 процентов, а в том что он вообще коротким становится … меньше половины экрана"* — and
changing the number in the same edit as the unit would make the next report impossible to
attribute.

### What I got wrong, since it is the more useful part

Four changes, each verified in headless Chromium at a fixed viewport, each measuring 88–92 % and
each still wrong in the owner's hand — because that environment has no collapsing toolbar and so
cannot tell `vh`, `dvh` and `svh` apart at all. **The tests were green the whole time and proved
nothing about the thing being reported.**

The two cheap moves I skipped: reading the history for when it last worked, and taking the
owner's numbers literally. "Less than half the screen" is arithmetically impossible for any
percentage between 88 and 92, and that alone ruled out the entire direction I spent four rounds
on.


## Fourth correction, 2026-08-28: the ruler on the device, and the real cause

The third correction blamed commit `62197ad` (`h-[88vh]` → `h-[88dvh]`). That was a real
regression and worth fixing, but it is **not** what makes the drawer half a screen tall, and the
owner said so plainly: *"дело не в том 92 или 88 процентов, а в том что он вообще коротким
становится — меньше половины экрана"*.

### The measurement that ended the guessing

Rather than write a fifth fix, `public/viewport-check.html` was published to the tunnel: three
fixed, bottom-anchored boxes — one per viewport unit — with a live readout, screenshotted on the
owner's phone.

| | Chrome's toolbar hidden | toolbar visible |
|---|---|---|
| visible area | 888 | 832 |
| `92vh` | **817** | **817** |
| `92dvh` | 817 | 765 |
| `92svh` | 765 | 765 |

Every single value is ~92 % of the screen. **No viewport unit can produce the reported drawer**,
so the unit was never the cause of the collapse. `vh` is still the right unit against `dvh` — it
does not breathe as the toolbar moves — but that is a different, smaller bug.

### What actually does it

`index.html` sets `interactive-widget=resizes-content`. That is deliberate and correct: it makes
the on-screen keyboard shrink the **layout viewport**, so a bottom-anchored sheet sits above the
keyboard instead of behind it. The consequence, which nothing in the codebase had accounted for,
is that **`vh`, `dvh` and `svh` are all percentages of that same shrinking viewport**.

So `h-[92vh]` never meant "92 % of the screen". With a keyboard up it means 92 % of the ~300 px
that remain — about **276 px of an 888 px phone**, under a third of the screen. That is the
reported drawer, exactly.

And it explains the split the owner spotted, which is what identified the cause:

| Sheet | Has a field you type into | Reported |
|---|---|---|
| AI chat | yes (the composer) | collapses |
| AI keys | yes | collapses |
| New target | yes — *"когда появилась прокрутка все схлопнулось"*, i.e. while typing tasks | collapses |
| **Filter & sort** | **no** | **"там всё хорошо"** |

### The fix

Sheets no longer state a height at all. `src/lib/spira/sheet-height.ts` publishes `--app-vh` —
one percent of the **keyboard-free** viewport. What separates a keyboard from an ordinary resize
is **focus**, not a pixel threshold: a keyboard cannot be up unless something you type into is
focused, so with nothing focused the current height is simply the truth and is followed *down*
(a desktop window dragged shorter, split-screen, a folding phone), while with a field focused the
largest height seen holds, because a keyboard can only ever shrink the viewport. The maximum
resets when the width changes, which is what a rotation looks like.

Four utilities in `styles.css` build on it:

```css
.sheet-h-92  { height: min(calc(var(--app-vh, 1vh) * 92), 100dvh); max-height: … }
.sheet-h-88  { …88… }          /* the key sheet, one step shorter on purpose */
.sheet-max-92, .sheet-max-85   /* content-sized sheets: a cap only */
```

The `min(…, 100dvh)` is the other half: while the keyboard **is** up, the sheet fills the space
above it exactly rather than running off the top of the screen. Ten sheets were converted; the
note editor keeps `100dvh` because full-screen genuinely means "match what is visible".

### How it is kept fixed

- `src/components/spira/sheet-units.test.ts` — rewritten. It now fails the build on **any**
  viewport-unit height under `src/components` outside a documented `ACCEPTED` map, checks that
  every `sheet-*` class a component uses is actually defined in `styles.css` (an undefined
  Tailwind class is not an error anywhere — it silently does nothing), and checks that `main.tsx`
  still calls `trackViewportHeight()`, without which every sheet falls back to `1vh` and the bug
  returns invisibly.
- `e2e/ai-drawer-height.spec.ts` — a new case **focuses the composer** and then shrinks the
  viewport to 412×300, which is what `resizes-content` does when the keyboard opens, and asserts
  the drawer fills it. The order is the test: shrinking with nothing focused is a smaller
  *window*, and the sheets are supposed to follow that down. **Checked against the old markup:
  276 px, red.** A second assertion was added to the existing
  restore case, because it only checked the drawer had not *overgrown* — a permanently collapsed
  drawer passed it.

### The lesson, since this is the fourth entry on one bug

Four fixes were written from reasoning about CSS, and all four measured a correct 92 % in
Playwright, because **a headless browser has no keyboard and no collapsing toolbar**. What worked
was putting a ruler on the actual screen — a five-minute static page — and taking the owner's
number literally. "Less than half the screen" is arithmetically impossible for anything between 88
and 92, and that single sentence ruled out the entire direction the four attempts were spent on.

Android is **not** affected: `MainActivity` runs `enableEdgeToEdge()`, so the drawer is measured
against the full screen and `AiChatScreen` applies `imePadding()` inside it — the same
architecture the web now has.

### What `/code-review` caught on top of it (2026-08-28)

Seven findings, all fixed in the same change:

| Finding | What was wrong |
|---|---|
| **The `max-h-[70%]` cap without sticky footers** | The cap is new, so any card can now scroll — but only `ProposalCard` had a `sticky bottom-0` action row. A multi-change review on a squeezed viewport put `Save all N` below the fold, i.e. back to the un-answerable card this bug exists for. There is one `CARD_ACTIONS_CLS` now and all four cards use it. |
| Two comments describing code that no longer exists | One claimed an inline `92vh` style that had been removed (a maintainer following it would have re-added the bug); one claimed the key sheet leaves the coach visible above it, when 88 % of the screen inside a 92 % drawer leaves ~36 px. Both rewritten to say what the code does. |
| `--app-vh` could go stale | See above: focus, not a maximum. |
| The convention test's blind spot | It only matched *bracketed* heights, so `h-dvh` and `h-screen` — the same layout viewport — passed silently, and it scanned only `src/components`, not `src/routes`. Both fixed, and `min-h-*` is now deliberately ignored: a floor cannot make a sheet short. Verified by putting `h-dvh` on the drawer and watching it go red. |
| A leaked Playwright route | The `hangOnRevise` stub slept 30 s and never fulfilled or aborted, so the timer outlived the test body — a teardown stall with two retries. It aborts after 2 s now. |
| Two stale Android KDoc references | `AiChatHost.kt` still described the web as `h-[88vh]` / `h-[92dvh]`. |

---

## Reopened, and the actual cause (2026-08-29)

The owner, on the merged branch: *"сейчас что-то ужасное происходит со всеми drawers где
открывается клавиатура: их высота скачет, иногда контент ужимается настолько, что невозможно
пользоваться и вылазят снизу какие-то цветные полосы фона… в прошлый раз ты не смог это
исправить."* Everything above shipped, and the bug did not move.

### What everything above missed

**vaul was overwriting all of it with an inline style.** `Drawer.Root` takes a
`repositionInputs` prop that defaults to **`true`**, and what it does
(`onVisualViewportChange`, `vaul/dist/index.js:1136`) is listen for `visualViewport` resizes
and — whenever something typeable is focused — write **`style.height`** and **`style.bottom`**
straight onto the drawer element. An inline declaration beats every class, so `sheet-h-92`,
`sheet-max-92`, `--app-vh` and the whole `min(…, 100dvh)` scheme stopped applying the moment the
user touched a field.

That single fact explains every observation on this page, including the ones that had been
attributed to four different causes:

- **why the four CSS fixes were no-ops** — none of them was the declaration in effect;
- **the exact split the owner reported** — the sheets that misbehaved are the ones with a field
  (chat, keys, New goal, New target, GROW), and filter & sort, which has no keyboard, never did.
  That was read as evidence for the `vh`/keyboard theory; it is equally the signature of a
  handler that only runs while an input is focused;
- **"меньше половины экрана"** — the pixel value vaul writes is `initialDrawerHeight`, captured
  on the *first* resize the handler ever sees and then reapplied for the life of the drawer;
- **the jumping height** — a fresh pixel value on each viewport event;
- **the band of background below the content** — the sheet pinned to a pixel height its content
  no longer fills, showing the drawer's own `#0A8080`.

### The measurement

`e2e/ai-drawer-height.spec.ts`, 412×780, coach open, composer focused, viewport shrunk to 300
and back — the shape of a keyboard opening and closing on Android with
`interactive-widget=resizes-content`:

| Viewport | Inline style vaul wrote | Drawer height |
|---|---|---|
| 780 | — | 718 (92 %) |
| 300 (keyboard up) | `height: 300px` | 300 |
| **780 (keyboard gone)** | **`height: 300px`** | **300 — 38 % of the screen** |

With `repositionInputs={false}`: no inline style at any point, 718 → 300 → 718.

### Fix

One line, in `src/components/ui/drawer.tsx`, on the shared `Drawer` wrapper — so it reaches every
sheet in the app at once:

```tsx
<DrawerPrimitive.Root shouldScaleBackground={…} repositionInputs={false} {...props} />
```

It is a removal, not a workaround. `repositionInputs` exists for browsers where the keyboard does
**not** resize the layout viewport; `index.html` asks for `interactive-widget=resizes-content`, so
Chrome resizes it and puts the sheet above the keyboard natively. vaul was a second hand on the
same wheel, working from stale measurements. Everything else the flag guards is iOS-only
(`usePreventScroll` → `preventScrollMobileSafari`), so on Android and the desktop nothing but the
height writer goes away. The iOS consequence is recorded separately in
`backlog/ios-safari-keyboard-covers-the-sheet-composer.md`.

**None of the work above is wasted** — `--app-vh` and the `sheet-*` utilities are still what sizes
the sheets, and they were correct throughout the measurement (`--app-vh` stayed at 7.8 px while
vaul wrote 300). They simply had nothing to do with the symptom.

### How it is kept fixed

- `e2e/ai-drawer-height.spec.ts` → *"comes back to full height after the keyboard closes, with
  the field still focused"*. It asserts the height **and** that `style.height` is empty, so the
  test pins the cause and not just the symptom. **Checked red on the old code: 300, expected
  > 663.** The reason the five existing cases in that file all passed is worth keeping: each of
  them either shrank the viewport with nothing focused, or shrank it and never grew it back — and
  the defect lives in a handler that only runs while a field is focused, and only shows itself on
  the way back up.
- `src/components/spira/sheet-units.test.ts` → *"keeps vaul's `repositionInputs` switched off"*.
  The E2E does not run in the `Stop` hooks; a `npm i vaul@latest` that reset the default would
  otherwise reach a phone before anything said a word.

### The lesson, updated

The previous entry concluded "put a ruler on the real screen". Right, but incomplete: the ruler
was put on the *screen* and never on the *element*. Four rounds asked "what height does the CSS
compute?" and none asked "what is actually setting this element's height?" — a two-line
`el.style.height` probe in a headless browser, which is where this was finally found in one run.
**When a style you own does not take effect, read the element's computed and inline style before
writing another rule.** A third-party component that positions itself is entitled to overwrite you,
and vaul says so in its own prop list.
