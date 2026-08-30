# A drawer grows to the top edge of the screen when the keyboard opens

- **ID:** BUG-065
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-29 — "мне понравилась высота drawer для ai чата, когда клавиатура
  закрыта. сделай чтобы все drawer имели высоту не больше этой — то есть не подскакивали бы до
  верхушки экрана. вместо этого должна проходить автоматическая прокрутка контента в самый низ,
  когда открывается клавиатура и при открытой клавиатуре можно полностью прокручивать контент как
  бы за поле с сообщением и за клавиатурой", with screenshots of the Claude app doing it
- **Area:** Web (`src/styles.css`, `src/components/ai/AiPanel.tsx`)
- **Severity:** Medium — nothing is unreachable, but a sheet that fills the screen stops reading
  as a sheet

## Summary

Every sheet was `min(calc(var(--app-vh) * N), 100dvh)`. The first term is N % of the **screen**;
the second was **100 %** of what is visible. With the keyboard closed the first term binds and a
sheet is the 92 % the owner likes. The moment a field is focused, the keyboard shrinks the layout
viewport, `--app-vh` deliberately holds the pre-keyboard height, and the second term takes over —
at 100 %, so the sheet grew to fill everything above the keyboard and put its own top edge on the
screen's. Measured on a 412×888 phone, chat drawer:

| | Drawer top | Height |
|---|---|---|
| Keyboard closed | 71 | 817 (92 %) |
| Keyboard open, before | **0** | 430 (100 % of what is left) |
| Keyboard open, after | 34 | 396 (92 % of what is left) |

## Fix

Three changes, and the last two are what make the first affordable.

1. **The cap is `N dvh`, not `100dvh`** — the same percentage, read against what is visible. With
   nothing focused the two terms are equal to the pixel (`--app-vh` tracks the layout viewport
   exactly as `dvh` does), so the cap costs nothing and adds no breathing; it binds only while the
   keyboard is up.
2. **The chat's footer floats over its transcript.** The composer's white card sits on the
   gradient with the conversation scrolling underneath it, so the last message can be scrolled
   clear of it. An outer `absolute inset-0 pointer-events-none flex flex-col justify-end` layer
   gives `max-h-[70%]` a definite height to resolve against and lets touches through where the
   footer is not drawn; an inner layer hugs the content, carries the 70 % cap for the whole stack,
   and is measured by `useFooterHeight` into the transcript's `padding-bottom`.
3. **`useKeyboardStickyBottom`** scrolls the transcript to the end when the viewport shrinks with
   something typeable focused. `resize` is the signal, not `focus`: under
   `interactive-widget=resizes-content` the keyboard always resizes the layout viewport, and a
   scroll issued on focus would land on the old layout. Growing back is deliberately not handled —
   the keyboard leaving must not yank a transcript the reader has scrolled up into.

Form sheets keep their solid pinned foot: Cancel and Create are decisions, not a composer, and
content sliding behind them would be wrong. The browser already scrolls a focused field into view
inside the body scroller, which is all a form needs.

## How to verify fixed

`e2e/ai-drawer-height.spec.ts`, each checked red against the code it guards:

| Case | Red before |
|---|---|
| takes 92 % of the room above the keyboard — never all of it | drawer top `0`, `> 0` wanted |
| the composer floats over the transcript … | transcript bottom 668 vs footer bottom 780 |
| … and the keyboard scrolls it to the bottom | 1042 px from the end |

Also checked in **real Chrome 151** (the extension, not headless), by reading the computed height
of a probe element: at rest `sheet-h-92` is 787 of an 855 px viewport; with `--app-vh` forced to a
taller screen — which is exactly the keyboard state — `92 * --app-vh` computes to 983 while the
element stays **787**, so the cap binds and the sheet never fills the viewport. The panel's own
geometry was read the same way: transcript bottom 855 = footer bottom 855 (the footer floats),
`padding-bottom` 124 for a 116 px footer.

By hand, on a phone: open the coach, tap the composer. A strip of the dimmed page must stay
visible above the sheet, the conversation must jump to its end, and scrolling must carry messages
behind the composer's card.


---

## Reopened the same day: 92 % of *anything* still moves (2026-08-29)

The owner, on the rebuilt bundle: *"при открытии клавиатуры на вебе drawer всё-таки подскакивает
немного и начинает перекрывать хедер — все должно быть стабильно, без скачков."* And on the native
app: *"на андроид drawer стал слишком высоким, он не должен перекрывать хедер на андроид."*

`min(92 * --app-vh, 92dvh)` is still a percentage, and a percentage of a viewport the keyboard
resizes cannot hold a top edge still: 8 % of an 888 px viewport is 71 px, 8 % of the 430 px left
above a keyboard is 34. The edge jumped **up 37 px** on every focus — onto the app header, which
is 64 px tall.

Android had the same shape and a second problem on top: `AiChatHost` measured `0.92` of the box it
fills, and `MainActivity` runs `enableEdgeToEdge()`, so that box is the **whole screen, status bar
included**. The remaining 8 % is about a status bar, so the drawer's top edge landed inside
`GoalWorkspaceTopBar`. The same 92 % reads correctly on the web only because `--app-vh` is one
percent of the *layout viewport*, which Chrome has already trimmed. Copying the number across
without copying what it measured is the whole bug.

### Fix — both surfaces state a constant top edge

| | Web | Android |
|---|---|---|
| Rule | `calc(100dvh - var(--sheet-top-gap))` | `screenHeight - drawerTopEdgePx(...)` |
| Gap | **76px** — a 64px sticky header plus 12 of page | **122dp** under the status bar — a 64dp top bar, a 46dp GROW tab row, and 12 of page |
| Clears | the app header | the header **and** the GROW tabs (there they are fixed chrome; on the web that strip is sticky page content) |

`--app-vh` and `src/lib/spira/sheet-height.ts` are **retired**: they existed to make a percentage
stable, and the percentage is what never worked. The four numbered utilities collapse to three
unnumbered ones — `sheet-h`, `sheet-max`, `sheet-inset`.

### And the rest of the same report

- **Auto-scroll jitter.** `useKeyboardStickyBottom` scrolled `behavior: "smooth"` while the
  keyboard was sliding and the footer's `ResizeObserver` was changing the scroll height in the
  same frames — three animations arguing. It jumps to the end instantly now and lets the keyboard
  do the moving. A transcript already resting at its end also stays pinned when the footer changes
  height, so the composer growing a line no longer slides the conversation.
- **Cards on a slab of their own.** The "Finish session" and "Revising…" blocks carried
  `bg-[#F2FFFF]`, so they read as a second container under the floating composer. Removed — the
  gradient is the ground for everything that stands in the composer's place.
- **"Session complete" was a message with a live composer under it.** It is a footer card now and
  the composer stands down for it, so a finished session leaves nothing to type into.
- **Brown warnings.** The chat's warning line was set in `warning-500` as *type*, and mustard on a
  pale teal gradient reads brown; the proposal badge was `warning-900 #896500`, the brown
  `Notice.tsx` already forbids. Both are the app's warning yellow now, and the line took the
  notice shape — a `warning-500` border on a `warning-100` fill with a `warning-500` triangle and
  **near-black words**, which is what CLAUDE.md → Notices asks for: the kind belongs on the mark,
  never on the type. The timer's yellow is **warning-300**, which is in the palette but is a light
  tint meant for the dark teal header; as text on the chat's light gradient it has almost no
  contrast, so it was not used there.

### How it is kept fixed

- `e2e/ai-drawer-height.spec.ts` → *"keeps its top edge exactly where it was when the keyboard
  opens"*: focus, shrink, restore, and the top edge must be the **same number** all three times.
- `src/components/spira/sheet-units.test.ts` → the utilities must read
  `calc(100dvh - var(--sheet-top-gap))`, and `sheet-height.ts` must not exist.
- `android/.../AiDrawerClearsTheHeaderTest.kt` renders the **real** `GoalWorkspaceTopBar` and
  `GrowTabsRow` under the real drawer and fails if the gap drops under 8dp. Measured there: the
  chrome ends at 111dp, the drawer starts at 122.


---

## The Android half, which I had skipped (owner, 2026-08-29)

*"я просила все это сделать и на вебе и на андроид — ты сделал? например прокрутку автоматическую
при открытии клавиатуры я не вижу на андроид."*

Correct, and the omission is the point worth recording: the request said "это касается и веба и
андроид" and only the web was done. Everything below existed on the web already and is now on both.

| | Android, before | Now |
|---|---|---|
| Footer | each branch painted `CHAT_GRADIENT_BOTTOM` itself, below the list | one `Box` holds the transcript and the footer; the gradient is painted **once**, on it |
| Composer | a solid block under a white card | transparent — the gradient shows through, the card is the only container |
| Keyboard | nothing | `WindowInsets.isImeVisible` → `scrollToItem(lastIndex)` |
| Transcript | ended above the footer | full height, bottom `contentPadding` = the footer's measured height |
| Errors in chat | `warning-500` on the **words** — mustard on pale teal reads brown | the notice shape: `warning-500` outline on `warning-100`, yellow triangle, near-black words |

"Session complete" was already in the composer's place on Android (`GROW_FAREWELL` matches before
the `else ->` that draws the composer), so that one needed nothing.

The insets moved with the footer: `imePadding()` and `navigationBarsPadding()` are on the floating
column now rather than repeated on each branch. That keeps the ordering lesson of 2026-08-24 intact
— they still sit **outside** every card's `heightIn`/`verticalScroll`, which is what lifts the box
itself instead of padding its scrollable content.

## Two more from the same report

**Errors are not messages** (owner: "и ошибки это не сообщения"). A stream failure was written into
the transcript as a turn *and* raised as a notice, so a Gemini quota error appeared twice on one
screen in two shapes. It is the notice only now, the failed turn's empty streaming bubble is
removed rather than filled in, and an **error** notice no longer times itself out — it is the only
report of the failure, and six seconds is not long enough to read a quota error. It carries an X.

**Nothing overflows its block** ("ничего не должно вываливаться из блоков"). The same error is one
unbroken URL and ran past the right edge of both the notice and the chat's warning card. Two
causes, and both had to be fixed: **`min-w-0`**, because a flex item's automatic minimum size is
its content's and it will not shrink below an unbroken link; and **`break-words` +
`overflow-wrap: anywhere`**, so the text has somewhere to break once the box can narrow. Either one
alone changes nothing, which is why the first attempt (the parent had the wrapping, the child had
no `min-w-0`) looked like it should have worked.


---

## What the Android changes broke, and how it got through (owner, 2026-08-29)

*"ты проверял то, что ты сделал в эмуляторе? есть ошибки … автопрокрутка не работает, более того,
невозможно прокрутить до конца на андроид. кнопка set deadline."*

No, it was not run on the emulator. It crashed on boot twice earlier in the session (GPU, "bad
color buffer handle") and was abandoned; the Android work was verified with Robolectric renders and
the unit suite, which is real rendering but not a running app. Two defects went out.

### 1. The transcript could not be scrolled to its end

`onSizeChanged` was placed **after** `imePadding()` and `navigationBarsPadding()` in the footer's
modifier chain. Modifiers apply outside-in and each padding reports the *padded* size upward, so
the call measured the footer's content and missed the nav bar — and, while typing, the whole
keyboard. The transcript's bottom `contentPadding` was therefore short by that much, its last
messages sat behind the footer, and nothing could reach them.

It also made the keyboard scroll look broken: it ran, and landed under the composer. One cause,
both symptoms.

The call moved above the two paddings. `scrollBy(Float.MAX_VALUE)` was added after
`scrollToItem(lastIndex)` as well: `scrollToItem` puts an item's **top** at the viewport top, which
is the end of the list only while that item fits — a long answer left the reader on its first line.

### 2. "Set deadline" wrapped onto two lines

A Compose `Dialog` defaults to `usePlatformDefaultWidth = true`, which hands its content a width
the platform chose — ~320dp on the owner's phone. Each foot button got ~139dp and the label wrapped.
The card sizes itself now (`usePlatformDefaultWidth = false`, screen less a 16dp gutter, capped at
380dp), which is the web's `w-[calc(100%-32px)] max-w-[380px]` in Compose. `SpiraButton` also sets
`maxLines = 1` with an ellipsis, so a label with too little room looks wrong instead of quietly
growing the button a second line.

**The web was checked and was never affected** — its card states its own width, and the buttons fit
at 320, 360 and 412px. `e2e/deadline-picker.spec.ts` pins that at all three now.

### The lesson: Robolectric cannot see a platform window's sizing

Worth writing down, because two assertions were written against this defect and **both passed with
the fix reverted**. Robolectric's dialog window is the full 411dp whether `usePlatformDefaultWidth`
is set or not, so the grid measures the same either way — first at `>= 280.dp`, then at `>= 330.dp`.
It is the same wall as the on-screen keyboard: a headless host does not reproduce what the platform
does with a window.

So `DeadlinePickerDialogTest` and `ChatFooterConventionTest` check the **source** instead, the way
`sheet-units.test.ts` checks vaul's `repositionInputs` on the web.

And then the source checks themselves passed on reverted code, three times, for three different
reasons — every one of them found only by running them red first:

1. The KDoc above the call names `usePlatformDefaultWidth = false` twice, so the scan read its own
   explanation as evidence. Comment lines are stripped now.
2. The ordering check searched the whole file from the `onSizeChanged` onwards, so with the order
   reversed it found the *next* `.imePadding()` further down — the composer has one. It is given
   the footer's own chain now.
3. That chain was sliced at the first `") {"`, which lands inside `with(density) {` — so the test
   reported the keyboard padding **missing** rather than mis-ordered, failing in a way that looked
   like the defect it was hunting. It takes the lines now, ending on the one that is exactly `) {`.

The rule that follows: **a source-scanning check is not a check until it has been seen red.**

### What the emulator did and did not give

It boots headless (`-no-window -gpu swiftshader_indirect`); the GPU crash is the windowed mode only.
The app installs, launches and reaches the sign-in screen with no crash in logcat. It stops there:
**Google sign-in is interactive and only the owner can complete it**, so the chat and the goal form
cannot be reached on the emulator. It is worth booting for launch-and-crash checks; it is not a
substitute for the owner's phone on anything behind the login.


---

## The auto-scroll, for real this time (owner, 2026-08-29)

*"переделай чтобы на андроид у тебя был локальный доступ без логина … это полный бред, что ты не
можешь ничего тестировать реально на андроид."*

Right, and nothing had to be built: **the mechanism already existed and went unused for two
rounds.** The backend's `local` profile signs every request in as `dev@local`
(`LocalDevAuthFilter`), and the app's backend URL is a build flag that `build.gradle.kts` documents
on line 35. `installDebug -PspiraApiBaseUrl=http://10.0.2.2:8080` against a local backend lands on
**All goals** with real data and no sign-in. Written up in CLAUDE.md → 3e-quater so it is not
rediscovered a third time.

With that, the reported bug reproduced and the fix was confirmed on a real Android with a real soft
keyboard — the first Android verification in this session that was not a Robolectric render.

### What was still wrong

The previous fix (measuring the footer outside its inset padding) was necessary and not
sufficient. It created a second problem of its own:

1. the IME becomes visible and `isImeVisible` flips at the **start** of the keyboard's slide;
2. the effect scrolls to the end of the list *as measured then*;
3. the footer is `imePadding()`-ed, so it grows by the keyboard's height, and — correctly, since
   the previous fix — feeds that into the transcript's bottom `contentPadding`;
4. the list is now a keyboard's height short of its end. Exactly where it started.

Keyed on `footerHeight` as well as `isImeVisible`, the list tracks the footer as it rises instead
of racing it. Verified: with the keyboard up, the last message sits directly above the composer,
and scrolling up and back down still reaches it.

### Three obstacles worth recording, because each one looked like a dead end

- **The emulator crashes on this machine's GPU** in windowed mode. `-no-window` does not.
- **SystemUI ANRs constantly under software rendering**, and its dialog sits over everything and
  eats every tap. Dismissing or killing it does not help. `settings put global hide_error_dialogs 1`
  stops it being drawn; the app underneath had been fine the whole time.
- **A conversation to scroll** needs no model calls: `PUT /api/ai/chat/transcript` seeds one. It
  **overwrites** what is there, and under the `local` profile that is the same `dev@local`
  transcript the tunnel uses — this run replaced one.
