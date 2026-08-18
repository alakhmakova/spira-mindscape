# Android note editor: the caret jumps to the start of the note while typing

- **ID:** BUG-041
- **Status:** 🐞 Open — **reproduced and localised, not yet fixed.** Five candidate fixes were tried
  and measured; all failed. See "What has been ruled out" before trying anything.
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
