# Opening the deadline calendar collapses the sheet, and the calendar itself runs off the screen

- **ID:** BUG-063
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-29 — "форма для создания goal на мобильном — нажми на дедлайн,
  появится календарь и ты увидишь какой бред происходит"
- **Area:** Web (`src/components/ui/drawer.tsx`, `src/components/ui/popover.tsx`,
  `src/components/spira/DeadlinePopover.tsx`)
- **Severity:** **High** — the sheet becomes a stub of a form with a floating calendar over it,
  and with the keyboard up the calendar cannot be dismissed or navigated at all

## Summary

Two independent defects, both visible in one tap on **Deadline** in the New goal sheet on a phone.

### 1. The whole sheet scrolls inside its own frame

Measured on a 412×780 phone, immediately after the calendar opened:

| | Before the tap | After |
|---|---|---|
| `drawer.scrollTop` | 0 | **450** |
| Teal head, y | 158 | **−292** |

The head, the Title field, the Description and the Confidence row were all scrolled out of the
sheet's clip box; what was left on screen was the Deadline field and the footer, with the calendar
floating over them. That is the "бред" in the report.

**Root cause: `DrawerContent` was `overflow-hidden`.** `hidden` clips a box but still makes it a
**scroll container** — it only hides the scrollbars. The sheet reported `scrollHeight: 1866`
against `clientHeight: 622`, i.e. 1244 px of slack that nothing on screen accounts for, so it
could be scrolled by anything that scrolls programmatically. `DeadlinePopover` does exactly that:
for `variant="input"` it calls `inputRef.current.scrollIntoView({ block: "start" })` so a long form
makes room for the calendar — and `scrollIntoView` walks up and scrolls **every** scrollable
ancestor, not just the one that was meant. The browser's own "pull the focused element into view"
reaches the same box.

### 2. With the keyboard up, the calendar hangs off the top of the screen

The keyboard leaves ~430 px of layout viewport (`interactive-widget=resizes-content`). The
calendar is 414 px tall, so it fits neither below the field nor above it. Radix **flips** a
popover when there is no room below but does not **shrink** one that fits neither way — it was
measured at **y = −136**: its own head with the close X, the month arrows and the weekday row
were all above the screen. The month could not be changed and the picker could not be dismissed.

## Steps to reproduce

1. On a phone (or a 412×780 viewport), open **New goal**.
2. Tap **Pick a deadline** → the sheet collapses to a stub (defect 1).
3. Or: type a title first so the keyboard is up, then tap **Pick a deadline** → the calendar's top
   is off the screen (defect 2).

## Fix

- **`DrawerContent` is `overflow-clip`, not `overflow-hidden`.** They clip identically, but `clip`
  is not a scroll container at all, so `scrollIntoView`, a focus scroll and a touch pan can none of
  them move the sheet. Verified: setting `scrollTop = 400` leaves it at 0 with `clip`, and moves it
  to 400 with `hidden`. This covers **every** sheet in the app, not just New goal — they are all
  the same component. The body's `overflow-y-auto` remains the one scroller a sheet has, which is
  the shape CLAUDE.md → Sheets already specifies.
- **`PopoverContent` caps itself** at `max-h-(--radix-popover-content-available-height)` with
  `overflow-y-auto`, and takes `collisionPadding={8}` so it never touches a screen edge. Radix only
  publishes that variable because `avoidCollisions` is on. Content that already fits is unaffected.
- **The deadline card is a flex column**, so when the cap bites it is the **grid** that scrolls:
  the "Set deadline" head with its X and the Today / Clear row stay put. Capping the outer card
  alone would have scrolled the close button out of reach — the state this was found in.

`scrollIntoView` in `DeadlinePopover` was left alone deliberately: with the sheet no longer
scrollable it does what it was written to do — scroll the sheet's body — and nothing else.

## How to verify fixed

`e2e/sheet-chrome-stays-put.spec.ts`, three cases, each checked red against the code it guards:

| Case | Red before |
|---|---|
| the sheet itself cannot be scrolled, at any viewport height | `scrollTop` 400, expected 0 |
| opening the deadline calendar leaves the sheet's head where it was | head y −274, expected 176 |
| the calendar stays on screen with the keyboard up | popover bottom past the viewport |

By eye, on a 412-wide viewport: open New goal, tap Deadline — the teal "New goal" head must still
be at the top of the sheet, and with a title typed (keyboard up) the calendar's own head, month
arrows and weekday row must all be on screen.

## Resolution

Fixed 2026-08-29 alongside BUG-060. Both bugs in this session came from the same class of mistake:
**something other than the component's own CSS was moving it** — vaul's inline height in BUG-060,
a stray `scrollIntoView` into a box that should never have been scrollable here.

## Still open, and deliberately not changed

- On a phone the calendar opens as a **popover over the sheet**, so two teal heads end up stacked
  ("New goal" and "Set deadline"). It works and is legible, but the app's own language for "ask me
  something" on a phone is a sheet (CLAUDE.md → Sheets), so a nested date sheet may be the right
  shape. That is a redesign, not a bug fix — ask the owner first.
