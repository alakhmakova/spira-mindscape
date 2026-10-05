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

## The redesign that followed (owner, 2026-08-29: "сделай редизайн")

Fixing the two defects left the calendar working but still a **popover over a sheet**, so two teal
heads ended up stacked — "New goal" and "Set deadline" — for one question. It became a nested
bottom **sheet**, which fixed that; the owner then asked for the shape Android already had:
*"на вебе вместо того отдельного drawer для календаря сделай модальное окно как на андроид,
кстати, на андроид тоже нужен календарь с номерами недель."*

Both notes are right, and they point in opposite directions — the web had the wrong container, and
Android had the wrong grid inside the right one.

**The container.** A sheet is how the app asks for something *about the page*, and it comes from
the bottom edge because it belongs to what is under it. A calendar is a value being picked inside
a form that is already open, and a second bottom sheet stacked on the first reads as leaving that
form. It is a centred modal now, on both surfaces.

**The grid.** Android used Material's `DatePickerDialog` + `DatePicker` — a raw platform default
in the middle of a Spira form (CLAUDE.md → Components and chrome → 1), and one that **cannot draw
week numbers at all**: Material 3 has no support for the column. So the month grid is the app's
own now (`SpiraMonthGrid`), with the same ISO weeks the web has always drawn. `WeekFields.ISO`,
never `WeekFields.of(Locale)` — the locale form starts weeks on Sunday in the US and would number
the same rows differently on the two surfaces.

The full spec is CLAUDE.md → Components and chrome → **3e-ter**. The short version: the shared
`SheetHead`, the chosen date written out in words, the grid with its `W` column, `Today` / `Clear`
as worded links, and the app's foot — quiet outline left, filled Kale right. **A day is a draft**
on the phone and on Android; the laptop's popover keeps its one-click commit, unchanged.

`SheetHead` grew a `titleComponent` so the dialog can pass `DialogTitle`: Radix needs one for the
dialog's accessible name, and a screen-reader-only second copy beside the band announced the title
twice.

The `overflow-clip` fix above still matters after all of it — it is what every sheet in the app
relies on, and what keeps the form underneath undisturbed while the modal is open.

`e2e/deadline-picker.spec.ts` pins both web surfaces; `VisualCheckDatePickerTest` pins Android's
week numbers against a known month (August 2026 runs 31–36) and writes the picture.


### One more pass on the head (owner, 2026-08-29)

*"эта информация Sunday, August 16, 2026 · 13d overdue должна отображаться в шапке календаря
вместо set deadline, когда дата уже выбрана, только нужно сделать короче … название дня недели или
сколько дней осталось/overdue можно убрать, если нормально не помещается."*

The card had a band with a fixed title and, right under it, a sentence restating what the band was
for. The head carries the date now — `MMMM d, yyyy`, the string the laptop's popover head has
always shown — and the line is gone, on both surfaces.

The weekday and the relative are dropped rather than shortened: with them the title measures
~250px, which fits a 412px phone and truncates on a 320px one. Measured after the change: 212px on
a 320px screen with no truncation. Both facts still show on the trigger the picker was opened
from. `e2e/deadline-picker.spec.ts` asserts the head's text **and** that it does not truncate, so
a longer format cannot creep back in unnoticed.
