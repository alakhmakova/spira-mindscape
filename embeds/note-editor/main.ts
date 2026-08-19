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
  editorProps: { attributes: { class: "tiptap" } },
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
function reportState() {
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
  try {
    window.SpiraNote?.onState?.(JSON.stringify(s));
  } catch {
    /* not hosted */
  }
}
/**
 * Report the toolbar state **after** the current transaction, and never mid-word.
 *
 * `window.SpiraNote.*` is an `addJavascriptInterface` bridge, and a call on it is **synchronous**:
 * it blocks the renderer's JS thread while the Java side runs. Calling it straight from
 * `editor.on("transaction")` therefore blocked Chromium in the middle of its own DOM handling — and
 * when that happened during an **IME composition**, the composition was lost and Chromium reported
 * the DOM selection back at the top of the document. ProseMirror faithfully applied it, so the
 * caret jumped to the very beginning of the note and the next word was typed there (BUG-041).
 *
 * Two rules, both needed:
 *  - **off the transaction** (a timeout, so the bridge call runs after ProseMirror has finished);
 *  - **not while `view.composing`** — wait for the IME to commit the word first.
 *
 * The same page in stock Chrome never showed this, because there `window.SpiraNote` is undefined
 * and every one of these calls was a no-op.
 */
let stateTimer: number | undefined;
function scheduleState() {
  clearTimeout(stateTimer);
  stateTimer = window.setTimeout(
    () => (editor.view.composing ? scheduleState() : reportState()),
    editor.view.composing ? COMPOSING_RETRY_MS : 0,
  );
}

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

// ── Content bridge + autosave ───────────────────────────────────
let timer: number | undefined;
function flush() {
  clearTimeout(timer);
  // Same rule as `scheduleState`: the bridge call is synchronous, so firing it while the IME is
  // mid-word blocks the renderer at exactly the wrong moment (BUG-041). The debounce simply waits
  // for the word to land — an autosave is never so urgent that it may not wait 150ms.
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
  reportState();
};
window.spiraGetText = () => editor.getText();
window.spiraGetHtml = () => editor.getHTML();
window.spiraFlush = flush;

reportState();
