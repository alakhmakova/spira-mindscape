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
import { TextStyle, Color } from "@tiptap/extension-text-style";

declare global {
  interface Window {
    SpiraNote?: {
      onChange: (html: string) => void;
      onState?: (json: string) => void;
    };
    spiraSetContent?: (html: string) => void;
    spiraGetText?: () => string;
    spiraGetHtml?: () => string;
    spiraFlush?: () => void;
    spiraCmd?: (name: string, arg?: string) => void;
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
  ],
  content: "",
  editorProps: { attributes: { class: "tiptap" } },
});

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
  };
  try {
    window.SpiraNote?.onState?.(JSON.stringify(s));
  } catch {
    /* not hosted */
  }
}
editor.on("selectionUpdate", reportState);
editor.on("transaction", reportState);

// ── Commands from the native toolbar ────────────────────────────
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
    case "link":
      if (arg) c.extendMarkRange("link").setLink({ href: arg }).run();
      break;
    case "unlink":
      c.extendMarkRange("link").unsetLink().run();
      break;
  }
  reportState();
};

// ── Content bridge + autosave ───────────────────────────────────
let timer: number | undefined;
function flush() {
  clearTimeout(timer);
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
