# Android note editor: the caret jumps to the start of the note while typing

- **ID:** BUG-041
- **Status:** ✅ Fixed (2026-08-21) — see Resolution. The cause was the **host**, not the page: the
  WebView was the view Compose's `AndroidView` factory returned. One `FrameLayout` in between fixes
  it.
- **Reported by:** User (2026-08-18, "пишу заметку и курсор сам внезапно перескакивает в самое
  начало заметки")
- **Area:** Android — the note editor WebView (`ui/goals/NoteEditorActivity.kt`,
  `embeds/note-editor/main.ts`)
- **Severity:** High — it silently scrambles the user's writing

## Summary

While typing a note, the caret jumps to position 1 (the very beginning of the note) at the end of a
word. Typing continues there, so each new word is inserted **before** everything written so far and
the note comes out backwards.

## Steps to reproduce

Reproduced on the `spira_pixel` emulator with the real Google keyboard (it does **not** reproduce
with `adb shell input text`, which bypasses IME composition — that was the first false negative):

1. Launch the editor with content already in it:
   `adb shell "am start -n com.spiramindscape.android/…NoteEditorActivity -e initialHtml '<p>START.</p>' -e initialTitle t -e resourceId 1"`
2. Tap at the end of the line to place the caret.
3. Tap out `h e l l o` on the on-screen keyboard, then **space**. Repeat with more words.

Result: `FIXED AGAIN WORLD START.HELLO` instead of `START.HELLO WORLD AGAIN FIXED`.

## What the instrumentation shows

Logging `beforeinput`, `compositionstart`/`compositionend`, the DOM selection and ProseMirror's
selection gives an unambiguous picture. Typing the letters of a word is correct — the position
advances 8, 9, 10, 11, 12. Then, on the space:

```
beforeinput type=insertText data=" "
PM  sel from=1  composing=true
DOM sel off=0   collapsed=true node=#text text="START.HELLO "
```

The space itself is inserted in the **right** place (the text node reads `START.HELLO `), and then
the caret collapses to offset 0 and ProseMirror follows it to position 1.

Two further facts from the same traces:

- **`compositionstart` fires for every single character and `compositionend` never fires at all**,
  so ProseMirror's `view.composing` is stuck `true` for the whole session.
- The jump's stack is ProseMirror's own DOM reconciliation:
  `If.observer → If.flush → If.handleDOMChange → Ff → Ra.dispatch → dispatchTransaction`.

## The control that matters

**The same page, served over HTTP and opened in stock Chrome on the same emulator with the same
keyboard, does not do this.** Typing `abc hello world` gives `Abc hello world`.

> The first attempt at this control was **invalid** and nearly sent the whole investigation the
> wrong way: it typed into an *empty* document, where a jump to the start is invisible because the
> start is where you are already typing. Any future control must begin with text already present.

So the page, TipTap and ProseMirror are not by themselves at fault: it is something about hosting
them in **our** WebView.

## What has been ruled out

Each of these was measured, not reasoned about:

| Hypothesis | Verdict |
|---|---|
| The note is re-seeded mid-typing (`spiraSetContent`) | **No** — logged; it runs exactly once, at load |
| The WebView is recreated | **No** — logged; the factory runs once |
| The editor loses focus | **No** — no `blur`; `document.hasFocus()` stays true and `activeElement` stays `ProseMirror-focused` |
| A resize (keyboard insets / the save-error banner) | **No** — no resize event at the moment of the jump |
| Compose recomposing per keystroke (the `onState` push) | **No** — disabled the state push entirely; still jumps |
| Synchronous Java-bridge calls inside PM's transaction | **No** — deferred off the transaction and out of composition; still jumps |
| Dispatching the `compositionend` the IME never sends | **No** — `composing` then reads `false`, and it still jumps |
| `autocorrect="off"` / `spellcheck="false"` on the editor | **No** — GBoard composes anyway; still jumps |
| `useWideViewPort = true` (the WebView/Chrome viewport difference) | **No** — still jumps |
| prosemirror-view 1.41.8 → 1.42.2 | **No** — still jumps (the bump was reverted) |

## What to try next

1. **Enable `WebView.setWebContentsDebuggingEnabled(true)` in debug builds** and attach Chrome
   DevTools over the CDP. That gives the renderer's real IME state and a breakpoint inside
   `handleDOMChange`, which is the one thing this investigation never had.
2. **Load a bare `contenteditable` page in the app's WebView** (a debug-only entry point) to
   settle whether the fault needs ProseMirror at all or is WebView-wide. If a bare contenteditable
   also collapses to 0, the fix is in the host, not the page.
3. Compare against a **release** WebView and a **physical device** — the emulator's WebView build
   may be part of it. The owner sees it on a real phone, so it is not emulator-only, but the two
   may differ in degree.
4. If the host cannot be fixed: keep ProseMirror's own selection authoritative by rejecting a DOM
   selection that collapses to the document start while `composing` — i.e. re-assert the pre-input
   selection in a `beforeinput` handler and let PM re-apply the insert.

## Related changes made while investigating (kept, but they are NOT the fix)

`main.ts` now reports toolbar state **off** the ProseMirror transaction and never while the IME is
composing (`scheduleState`), and the autosave `flush()` waits for a composition to finish before
reading the document. Both are hygiene — an `addJavascriptInterface` call is synchronous and blocks
the renderer, so making one from inside PM's own DOM handling was never right — and both are
harmless. Neither changed this bug.


---

## Resolution (2026-08-21)

**Root cause: the editor's WebView was the view Compose's `AndroidView` factory returned.**

Compose treats that view as its own — `AndroidViewHolder` takes it into Compose's focus system and
drives its layout. A WebView does not survive that. Chromium responds by calling
`ImeAdapterImpl.cancelComposition()` → `InputMethodManager.restartInput()` **on every keystroke**,
which was caught on the stack:

```
ProbeWebView.onCreateInputConnection
android.view.inputmethod.InputMethodManager.startInputInner
android.view.inputmethod.InputMethodManager.restartInput
org.chromium.content.browser.input.ImeAdapterImpl.q
org.chromium.content.browser.input.ImeAdapterImpl.cancelComposition
```

**The fix is one `FrameLayout`.** The factory now builds the WebView into a local and returns a
plain `FrameLayout` holding it, so Compose owns the FrameLayout and the WebView keeps its own focus
and IME path. `onRelease` reaches the WebView through `getChildAt(0)` and still destroys it.

### One cause, all four symptoms

Restarting input destroys the IME's composing region. Everything the owner reported follows from
that, which is why they arrived together and why no partial fix ever helped:

| Symptom | Why |
|---|---|
| The caret jumps to the start of the note | Each new `EditorInfo` carries `initialSelStart=0`, so the DOM selection collapses to 0 on commit and ProseMirror follows it |
| **Every letter is capitalised** | The IME asks `getCursorCapsMode` after each restart and, believing the caret is at 0, is told "start of a sentence" |
| **A fragment is duplicated onto a new line** | With the composing region gone, GBoard's next `setComposingText` **inserts** instead of replacing — typing "world" gives `WWo…` |
| **The keyboard covers what is being typed** | The reported cursor rect stays `Rect(0,0-0,0)`, so nothing can scroll the caret above the keyboard |

### The measurements

Typing `hello world` on the emulator's real GBoard, with `<p>START.</p>` in the note and the caret
at its end. `initialSelStart` is from `adb shell dumpsys input_method`; 6 is correct.

| Host | `initialSelStart` | Result |
|---|---|---|
| Plain `Activity` + `FrameLayout`, no Compose | **6** | `START.hello world` ✅ |
| Compose `AndroidView` returning the WebView | **0** | `WWoLDSTART.HELLO ` ❌ |
| Compose `AndroidView` + `Column` + `imePadding()` | **0** | `WWoLDSTART.HELLO ` ❌ |
| **Compose `AndroidView` returning a `FrameLayout`** | **6** | `START.hello world` ✅ |
| Stock Chrome, same page, same emulator, same taps | **6** | `START.hello world` ✅ |

The real editor after the fix, typing a 76-character sentence:
`Notes about the meeting. We agreed to ship the editor fix and then measure typing again on a real
phone` — exact, including the sentence-initial capital, which is `autocapitalize="sentences"`
working from the true caret position.

### Two things the earlier investigation had wrong

- **The Java bridge was not involved.** `main.ts` had been changed to defer, debounce and diff its
  `SpiraNote` calls, and its comments claimed that was the fix for the milder capitalisation and
  "trembling" faults. Setting `window.SpiraNote = undefined` from DevTools — making every bridge
  call a no-op — and typing the same words still produced `WORLDSTART.HELO`. Those changes are kept
  as hygiene (a synchronous bridge call mid-composition is still wrong), and their comments now say
  so instead.
- **The capitalisation and the caret jump were treated as separate, milder faults.** They are the
  same defect.

### What made it findable this time

The backlog's own "what to try next" opened with *enable `setWebContentsDebuggingEnabled`*. That is
what was missing:

1. `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)` in `NoteEditorWebView`, and CDP over
   `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` — which gives the page's real
   `beforeinput`/`composition*` trace and lets the bridge be switched off without a rebuild.
2. A **debug-only** manifest overlay (`app/src/debug/AndroidManifest.xml`) exporting
   `NoteEditorActivity`, so the editor can be launched from `adb` with content already in it.
   Reaching it through the app needs a Google sign-in, which only the owner can do — that is why
   the first investigation could not get a document with text in it.
   > It is exported in **debug builds only**, but debug builds are what Firebase App Distribution
   > sends to the owner's phone, so on that phone another app could launch the editor with its own
   > `resourceId`/`initialHtml`. Low risk and easily removed — delete the `<activity>` block from
   > the overlay — but it is a deliberate trade, not an oversight.
3. `debug/NoteEditorProbeActivity.kt` — a bisection harness that hosts the same page four ways
   (`bare`, `compose`, `column`, `touch`, plus `wrap`), which is what separated "the host" from
   "our customisations" in three runs.

### Guarding it

`NoteEditorWebViewHostTest` asserts the wrapper is still there. Both shapes compile and render
identically, so nothing else would catch a "simplification"; the test's assertions were checked
against the pre-fix source and fail on it.

### Verify

On an emulator: launch the editor with `<p>START.</p>`, tap at the end of the line, type
`hello world` on the on-screen keyboard. The note must read `START.hello world`, the keyboard must
show **lowercase** letters, and `adb shell dumpsys input_method | grep initialSelStart` must say
**6**, not 0.


---

## Follow-up (2026-08-21): the caret sat flush against the keyboard

With the IME fixed, the owner reported the remaining half of "the keyboard covers what I'm writing":
*"the keyboard is right under the line I'm writing, with no space at all"*.

Chromium does scroll the focused editable back into view when the IME opens, but "into view" means
**just** inside the bottom edge, so the line being typed touches the top of the keyboard.

**`scroll-padding-bottom` is the right tool and does nothing here.** Set on `#app` and confirmed to
compute to `80px`, `innerHeight - caretRect.bottom` still measured exactly **0**: Chromium 109's
focused-editable path does not consult it. The CSS was removed rather than left in looking like it
worked.

**The page keeps the caret clear itself** — `keepCaretClear` in `main.ts`, `CARET_CLEARANCE_PX = 48`
(just under two lines at 16px/1.6). It runs after the browser's own scroll (a timeout, then a frame)
and only ever scrolls **down**, so it corrects the final position instead of racing it and cannot
loop. It moves the scroll container only, never the DOM, so it is safe mid-composition.

Measured after the change, typing low on the screen in a 40-line note:

| Case | `innerHeight - caretRect.bottom` |
|---|---|
| Before | **0** |
| Typing mid-note | **48** |
| A long run typed at the bottom | **48**, held throughout |
| The caret on the **last** line | **93** — the scroller hits its end and `.tiptap`'s 80px bottom padding gives more than the 48 asked for |

Manual scrolling is undisturbed: nothing here listens to scroll, so reading back through a note does
not yank the view to the caret.
