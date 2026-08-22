// Spira note editor — the SAME TipTap editor the web uses (src/components/spira/RichTextEditor.tsx),
// bundled into one self-contained HTML asset and hosted in a native Android Activity's WebView.
// Formatting is driven by a NATIVE Compose toolbar via window.spiraCmd(...); the editor reports its
// active formats back via window.SpiraNote.onState(json). Edits stream back (debounced + on blur)
// via window.SpiraNote.onChange(html); content is seeded via window.spiraSetContent.
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import Highlight from "@tiptap/extension-highlight";
import TaskList from "@tiptap/extension-task-list";
import TaskItem from "@tiptap/extension-task-item";
// FontFamily / FontSize / LineHeight come from the same `extension-text-style` package the web
// uses. **They were missing here**, which is why the Android toolbar had no font, size or spacing
// control to offer: without the extension the command does not exist, so the button could not have
// been added even if it were drawn (owner, 2026-08-17 — "the same editing options as mobile web").
import {
  TextStyle,
  Color,
  FontFamily,
  FontSize,
  LineHeight,
} from "@tiptap/extension-text-style";

declare global {
  interface Window {
    SpiraNote?: {
      onChange: (html: string) => void;
      onState?: (json: string) => void;
      /**
       * The format painter reporting what it just did, so the native side can toast it: a JSON
       * `{ picked: string[] } | { applied: string[] }`. The editor is the only side that knows
       * which marks were under the caret, and a painter that says nothing is a painter the user
       * cannot tell has worked — which is exactly what the owner reported on the web.
       */
      onPainter?: (json: string) => void;
    };
    spiraSetContent?: (html: string) => void;
    spiraGetText?: () => string;
    spiraGetHtml?: () => string;
    spiraFlush?: () => void;
    spiraCmd?: (name: string, arg?: string) => void;
    /** Selection bridge — see the "Selection" section below. */
    spiraSelectWordAt?: (x: number, y: number) => string | null;
    spiraMoveSelectionEnd?: (
      which: string,
      x: number,
      y: number,
    ) => string | null;
    spiraSelectAll?: () => string | null;
    spiraSelectionInfo?: () => string | null;
    spiraClearSelection?: () => void;
    spiraSelectedText?: () => string;
  }
}

const editor = new Editor({
  element: document.getElementById("editor")!,
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      link: { openOnClick: false, autolink: true },
    }),
    Highlight.configure({ multicolor: true }),
    TaskList,
    TaskItem.configure({ nested: true }),
    TextStyle,
    Color,
    FontFamily,
    FontSize,
    LineHeight,
  ],
  content: "",
  editorProps: {
    attributes: {
      class: "tiptap",
      // Say what the keyboard should do, rather than leaving it to the host's default for a
      // `contenteditable`. A note is prose: capitalise the start of a sentence, and nothing else.
      autocapitalize: "sentences",
      autocorrect: "on",
      spellcheck: "true",
    },
  },
});

/**
 * The format painter's held style, or null when it is empty.
 *
 * One button with two steps, exactly as on the web (`RichTextEditor.tsx`): the first press picks
 * the marks up from the caret, the second puts them on the selection. Links are skipped — copying
 * a href onto unrelated text is rarely intended.
 */
let painterMarks: { type: string; attrs: Record<string, unknown> }[] | null =
  null;

function reportPainter(payload: { picked?: string[]; applied?: string[] }) {
  try {
    window.SpiraNote?.onPainter?.(JSON.stringify(payload));
  } catch {
    /* not hosted */
  }
}

// ── Report active formats to the native toolbar ─────────────────
/**
 * The last payload actually sent across the bridge.
 *
 * **The toolbar only cares when the answer changes**, and while someone is typing a sentence it
 * never does — bold stays bold, a paragraph stays a paragraph. Comparing against this is what
 * turns "a synchronous bridge call on every keystroke" into "a bridge call when a format
 * changes", which is the whole point (see `scheduleState`).
 */
let lastStateJson = "";

function reportState(force = false) {
  const s = {
    bold: editor.isActive("bold"),
    italic: editor.isActive("italic"),
    underline: editor.isActive("underline"),
    strike: editor.isActive("strike"),
    highlight: editor.isActive("highlight"),
    code: editor.isActive("code"),
    h1: editor.isActive("heading", { level: 1 }),
    h2: editor.isActive("heading", { level: 2 }),
    h3: editor.isActive("heading", { level: 3 }),
    bullet: editor.isActive("bulletList"),
    ordered: editor.isActive("orderedList"),
    task: editor.isActive("taskList"),
    quote: editor.isActive("blockquote"),
    link: editor.isActive("link"),
    // Lit while the painter is holding a style, so its two steps are visible rather than implied.
    painter: painterMarks !== null,
  };
  const json = JSON.stringify(s);
  if (!force && json === lastStateJson) return;
  lastStateJson = json;
  try {
    window.SpiraNote?.onState?.(json);
  } catch {
    /* not hosted */
  }
}
/**
 * Report the toolbar state **after** the current transaction, and never mid-word.
 *
 * `window.SpiraNote.*` is an `addJavascriptInterface` bridge, and a call on it is **synchronous**:
 * it blocks the renderer's JS thread while the Java side runs. Making one from inside ProseMirror's
 * own DOM handling, on every keystroke, was never right, so this defers it, skips it while the IME
 * is composing, and sends it only when the answer has actually changed. The formats under the caret
 * do not change while a sentence is being typed, so the comparison drops nearly every call and the
 * debounce collapses what is left of a burst into one. The toolbar is unaffected: it is redrawn on
 * a format change, which is the only time it has anything new to draw.
 *
 * > **This is hygiene, and it is NOT the fix for BUG-041** — an earlier version of this comment
 * > said it was, and that was wrong (measured 2026-08-21). Setting `window.SpiraNote = undefined`
 * > from DevTools, so that every call here and in `flush` becomes a no-op, and then typing
 * > "hello world" after "START." on the emulator's real keyboard still produced
 * > `WORLDSTART.HELO`. The bridge is not involved.
 * >
 * > The actual cause was in the **host**: the editor's WebView was the view Compose's `AndroidView`
 * > factory returned, so Compose drove its focus and layout and Chromium answered by calling
 * > `ImeAdapterImpl.cancelComposition()` → `InputMethodManager.restartInput()` on every keystroke.
 * > See `NoteEditorActivity.kt`'s `NoteEditorWebView`, and `backlog/`.
 */
let stateTimer: number | undefined;
function scheduleState() {
  clearTimeout(stateTimer);
  stateTimer = window.setTimeout(
    () => (editor.view.composing ? scheduleState() : reportState()),
    editor.view.composing ? COMPOSING_RETRY_MS : STATE_DEBOUNCE_MS,
  );
}

/**
 * How long the toolbar waits after the last edit before it asks what is under the caret.
 *
 * Long enough that a run of keystrokes reports once instead of once each, short enough that
 * tapping into a bold word lights the button before the finger has left the screen.
 */
const STATE_DEBOUNCE_MS = 120;

/** How long to wait before looking again while the IME is still building a word. */
const COMPOSING_RETRY_MS = 150;

editor.on("selectionUpdate", scheduleState);
editor.on("transaction", scheduleState);

// ── Commands from the native toolbar ────────────────────────────
/**
 * The marks the format painter should pick up.
 *
 * **Not `$from.marks()`.** ProseMirror resolves the marks *at* a position, and at a position that
 * sits on a boundary — which is exactly where a word selection starts — it answers with the marks
 * of the node **before** it. Long-press a bold word in the middle of a plain sentence and
 * `$from.marks()` returns the plain text's marks, so the painter picked up nothing at all and the
 * second press then correctly applied nothing. Measured on an emulator, 2026-08-18: the bridge sent
 * `{"picked":[]}` for a selection that was visibly bold.
 *
 * `marksAcross` asks the other question — the marks of the node **after** `$from`, i.e. of the text
 * actually selected — which is what "copy this text's formatting" means. For a bare caret there is
 * no "after", so the stored marks (what the next keystroke would carry) are the honest answer.
 */
function marksToCopy() {
  const sel = editor.state.selection;
  if (sel.empty) return editor.state.storedMarks ?? sel.$from.marks();
  return sel.$from.marksAcross(sel.$to) ?? sel.$from.marks();
}

window.spiraCmd = (name: string, arg?: string) => {
  const c = editor.chain().focus();
  switch (name) {
    case "bold":
      c.toggleBold().run();
      break;
    case "italic":
      c.toggleItalic().run();
      break;
    case "underline":
      c.toggleUnderline().run();
      break;
    case "strike":
      c.toggleStrike().run();
      break;
    case "highlight":
      c.toggleHighlight().run();
      break;
    case "code":
      c.toggleCode().run();
      break;
    case "h1":
      c.toggleHeading({ level: 1 }).run();
      break;
    case "h2":
      c.toggleHeading({ level: 2 }).run();
      break;
    case "h3":
      c.toggleHeading({ level: 3 }).run();
      break;
    case "bullet":
      c.toggleBulletList().run();
      break;
    case "ordered":
      c.toggleOrderedList().run();
      break;
    case "task":
      c.toggleTaskList().run();
      break;
    case "quote":
      c.toggleBlockquote().run();
      break;
    case "hr":
      c.setHorizontalRule().run();
      break;
    case "undo":
      c.undo().run();
      break;
    case "redo":
      c.redo().run();
      break;
    case "clear":
      c.unsetAllMarks().clearNodes().run();
      break;
    case "color":
      if (arg) c.setColor(arg).run();
      break;
    case "highlightColor":
      if (arg) c.setHighlight({ color: arg }).run();
      break;
    case "fontFamily":
      if (arg) c.setFontFamily(arg).run();
      else c.unsetFontFamily().run();
      break;
    case "fontSize":
      if (arg) c.setFontSize(arg).run();
      break;
    case "lineHeight":
      if (arg) c.setLineHeight(arg).run();
      break;
    case "link":
      if (arg) c.extendMarkRange("link").setLink({ href: arg }).run();
      break;
    case "unlink":
      c.extendMarkRange("link").unsetLink().run();
      break;
    case "painter": {
      if (!painterMarks) {
        const marks = marksToCopy().filter((m) => m.type.name !== "link");
        painterMarks = marks.map((m) => ({
          type: m.type.name,
          attrs: { ...m.attrs },
        }));
        reportPainter({ picked: painterMarks.map((m) => m.type) });
        break;
      }
      let chain = c.unsetAllMarks();
      for (const m of painterMarks) chain = chain.setMark(m.type, m.attrs);
      chain.run();
      reportPainter({ applied: painterMarks.map((m) => m.type) });
      painterMarks = null;
      break;
    }
    // Paste keeping formatting. The HTML comes from the NATIVE clipboard, because a WebView has no
    // usable `navigator.clipboard.read()` — the web half reads its own clipboard instead.
    case "insertHtml":
      if (arg) c.insertContent(arg).run();
      break;
  }
  reportState();
};

// ── Selection ───────────────────────────────────────────────────
//
// **The app owns text selection in the note body, end to end.**
//
// Not by choice: in an Android **WebView**, a long press inside a `contenteditable` drops an
// insertion caret and offers a one-item "Select all" — it does not take the word. The same page in
// stock **Chrome** on the same device selects the word, raises both drag handles and offers
// Cut / Copy / Select all (verified on an emulator, 2026-08-18, screenshots in
// `backlog/android-note-text-cannot-be-selected-or-copied.md`). It is a host difference we cannot
// reach from the page, so the choice is between the platform's caret and a selection we draw
// ourselves — and a caret cannot copy a sentence.
//
// Everything here therefore goes **through ProseMirror** (`setTextSelection`), never through
// `document.getSelection().addRange(...)`: ProseMirror owns the selection and re-asserts its own on
// the next tick, which silently collapses a DOM range to a caret. That was the trap the first
// attempt at this fell into.
//
// Every call answers with the same JSON, so the app can redraw its handles and its menu from any of
// them: the selected text, and where each end of it sits in **CSS pixels relative to the viewport**
// (`sx`/`sy` the bottom-left of the first character, `st` the top of its line, and `ex`/`ey` the
// bottom-right of the last).

/** Describe the current selection for the app, or null when it is empty. */
function selectionInfo(): string | null {
  const view = editor.view;
  const { from, to } = editor.state.selection;
  if (from === to) return null;
  const text = editor.state.doc.textBetween(from, to, " ");
  if (!text) return null;
  const s = view.coordsAtPos(from);
  const e = view.coordsAtPos(to);
  return JSON.stringify({
    text,
    sx: s.left,
    sy: s.bottom,
    // The TOP of the first line as well, so the menu can be placed clear of the words rather
    // than lifted a guessed distance above their baseline — which landed it on them.
    st: s.top,
    ex: e.right,
    ey: e.bottom,
  });
}

/** The document position under a point, or null when the point isn't on text. */
function posAt(x: number, y: number): number | null {
  const at = editor.view.posAtCoords({ left: x, top: y });
  return at ? at.pos : null;
}

/**
 * Select the word under a point — what a long press does.
 *
 * A press just past the end of a word still takes that word, which is what makes tapping at the end
 * of a line feel right rather than like a miss.
 */
window.spiraSelectWordAt = (x: number, y: number) => {
  const view = editor.view;
  const pos = posAt(x, y);
  if (pos == null) return null;

  const $pos = view.state.doc.resolve(pos);
  const parent = $pos.parent;
  if (!parent.isTextblock) return null;

  const text = parent.textContent;
  const start = $pos.start();
  let i = pos - start;
  if (i < 0 || i > text.length) return null;
  if (i > 0 && (i === text.length || /\s/.test(text[i]))) i -= 1;
  if (/\s/.test(text[i] ?? " ")) return null;

  let a = i;
  let b = i;
  while (a > 0 && !/\s/.test(text[a - 1])) a--;
  while (b < text.length && !/\s/.test(text[b])) b++;
  if (a === b) return null;

  editor
    .chain()
    .focus()
    .setTextSelection({ from: start + a, to: start + b })
    .run();
  return selectionInfo();
};

/**
 * Drag one end of the selection to a point — what a handle does, and what a finger still held down
 * after the long press does.
 *
 * [which] is "start" or "end". Dragging one end **past** the other swaps them rather than
 * collapsing the range, so a handle pulled the wrong way keeps selecting instead of vanishing. A
 * range is never allowed to become empty: the end being dragged stops one character short.
 */
window.spiraMoveSelectionEnd = (which: string, x: number, y: number) => {
  const { from, to } = editor.state.selection;
  const pos = posAt(x, y);
  if (pos == null) return selectionInfo();
  const fixed = which === "start" ? to : from;
  if (pos === fixed) return selectionInfo();
  const a = Math.min(pos, fixed);
  const b = Math.max(pos, fixed);
  editor.chain().focus().setTextSelection({ from: a, to: b }).run();
  return selectionInfo();
};

/** Take the whole note — the app's menu offers this where the platform's used to. */
window.spiraSelectAll = () => {
  editor.chain().focus().selectAll().run();
  return selectionInfo();
};

/** What is selected right now, for redrawing after an edit. */
window.spiraSelectionInfo = () => selectionInfo();

/** Collapse the selection to its start — a tap elsewhere, or a menu action that is finished. */
window.spiraClearSelection = () => {
  const { from } = editor.state.selection;
  editor.commands.setTextSelection({ from, to: from });
};

/**
 * The selected text.
 *
 * Read from `state.selection`, **not** from `document.getSelection()`: the DOM selection is
 * whatever ProseMirror last re-asserted, and asking it came back empty.
 */
window.spiraSelectedText = () => {
  const { from, to } = editor.state.selection;
  return editor.state.doc.textBetween(from, to, " ");
};

// ── Keep the caret clear of the keyboard ────────────────────────
/**
 * How much empty page to keep below the line being typed, in CSS pixels (= dp on Android).
 *
 * Just under two lines at this type scale (16px x 1.6 leading = 25.6px a line). Enough to see the
 * line you are on sitting *above* the keyboard rather than jammed against it, without giving away
 * screen height that a phone has little of.
 */
const CARET_CLEARANCE_PX = 48;

/**
 * Scroll the note so the caret is never flush against the top of the keyboard.
 *
 * Chromium already scrolls a focused editable back into view when the IME opens — but "into view"
 * means *just* inside the bottom edge, so the line being typed ends up touching the keyboard
 * (owner, 2026-08-21: "the keyboard is right under the line I'm writing, with no space at all").
 *
 * The CSS answer, `scroll-padding-bottom` on the scroller, is the right tool and **does nothing
 * here**: measured on the emulator with it set and computing to `80px`, `innerHeight -
 * caretRect.bottom` was still exactly **0**. Chromium 109's focused-editable path does not consult
 * it. So the page does it itself.
 *
 * Two properties keep this from fighting the browser:
 *  - it only ever scrolls **down** (`scrollTop` up), and only when the caret is below the limit.
 *    Once it has run, Chromium considers the caret visible and has no reason to scroll back;
 *  - it runs **after** the browser's own handling (a timeout, then a frame), so it corrects the
 *    final position rather than racing it.
 *
 * It moves the scroll container only, never the DOM, so it is safe during an IME composition.
 */
function keepCaretClear() {
  const app = document.getElementById("app");
  const sel = document.getSelection();
  if (!app || !sel || sel.rangeCount === 0 || !editor.isFocused) return;
  const rect = sel.getRangeAt(0).getBoundingClientRect();
  // A collapsed range at the very start of a line can report an all-zero rect; `bottom` is then
  // meaningless and scrolling on it would jump the note to the top.
  const bottom = rect.bottom || rect.top;
  if (!bottom) return;
  const limit = window.innerHeight - CARET_CLEARANCE_PX;
  if (bottom > limit) app.scrollTop += bottom - limit;
}

let caretTimer: number | undefined;
function scheduleCaretClear() {
  clearTimeout(caretTimer);
  caretTimer = window.setTimeout(
    () => requestAnimationFrame(keepCaretClear),
    CARET_CLEAR_DELAY_MS,
  );
}

/** Long enough for Chromium's own scroll-into-view to have settled, short enough to be unseen. */
const CARET_CLEAR_DELAY_MS = 60;

document.addEventListener("selectionchange", scheduleCaretClear);
editor.on("update", scheduleCaretClear);
// The keyboard opening is a resize, and it is the moment that matters most.
window.addEventListener("resize", scheduleCaretClear);
window.visualViewport?.addEventListener("resize", scheduleCaretClear);

// ── Content bridge + autosave ───────────────────────────────────
let timer: number | undefined;
function flush() {
  clearTimeout(timer);
  // Same rule as `scheduleState`: the bridge call is synchronous, so firing it while the IME is
  // mid-word blocks the renderer at exactly the wrong moment. The debounce simply waits for the
  // word to land — an autosave is never so urgent that it may not wait 150ms. (Hygiene, not the
  // BUG-041 fix; see `scheduleState` for what that turned out to be.)
  if (editor.view.composing) {
    timer = window.setTimeout(flush, COMPOSING_RETRY_MS);
    return;
  }
  try {
    window.SpiraNote?.onChange(editor.getHTML());
  } catch {
    /* not hosted */
  }
}
editor.on("update", () => {
  clearTimeout(timer);
  timer = window.setTimeout(flush, 400);
});
editor.on("blur", flush);

window.spiraSetContent = (html: string) => {
  editor.commands.setContent(html || "", { emitUpdate: false });
  // Forced: seeding is the one moment the native toolbar has nothing to compare against, so it
  // must be told even if the formats happen to match what was last sent.
  reportState(true);
};
window.spiraGetText = () => editor.getText();
window.spiraGetHtml = () => editor.getHTML();
window.spiraFlush = flush;

reportState(true);
