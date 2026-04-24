import { useEditor, EditorContent, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Link from "@tiptap/extension-link";
import TaskList from "@tiptap/extension-task-list";
import TaskItem from "@tiptap/extension-task-item";
import Highlight from "@tiptap/extension-highlight";
import { useEffect } from "react";
import { useIsMobile } from "@/hooks/use-mobile";
import {
  Heading1,
  Heading2,
  Heading3,
  Bold,
  Italic,
  Underline as UnderlineIcon,
  Strikethrough,
  Highlighter,
  ListChecks,
  List,
  ListOrdered,
  Indent,
  Outdent,
  Quote,
  Code,
  Link as LinkIcon,
  Minus,
  Undo2,
  Redo2,
} from "lucide-react";
import { cn } from "@/lib/utils";

export function RichTextEditor({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (html: string) => void;
  placeholder?: string;
}) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
      }),
      Underline,
      Highlight.configure({ multicolor: false }),
      Link.configure({ openOnClick: false, autolink: true }),
      TaskList,
      TaskItem.configure({ nested: true }),
    ],
    content: value || "",
    editorProps: {
      attributes: {
        class:
          "tiptap-content prose prose-sm max-w-none focus:outline-none min-h-[40vh] text-[15px] leading-relaxed text-foreground/90",
        "data-placeholder": placeholder ?? "",
      },
    },
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
  });

  // Sync external value changes (e.g. switching resources)
  useEffect(() => {
    if (!editor) return;
    if (value !== editor.getHTML()) {
      editor.commands.setContent(value || "", { emitUpdate: false });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor, value]);

  const isMobile = useIsMobile();

  if (!editor) return null;

  if (isMobile) {
    return (
      <div className="flex flex-col">
        <div className="pb-16">
          <EditorContent editor={editor} />
        </div>
        <Toolbar editor={editor} variant="mobile" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <Toolbar editor={editor} variant="desktop" />
      <EditorContent editor={editor} />
    </div>
  );
}

function Toolbar({ editor, variant = "desktop" }: { editor: Editor; variant?: "desktop" | "mobile" }) {
  const Btn = ({
    onClick,
    active,
    disabled,
    label,
    children,
  }: {
    onClick: () => void;
    active?: boolean;
    disabled?: boolean;
    label: string;
    children: React.ReactNode;
  }) => (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={cn(
        "h-9 w-9 shrink-0 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors disabled:opacity-40 disabled:hover:bg-transparent",
        active && "bg-primary-soft text-primary hover:bg-primary-soft",
      )}
    >
      {children}
    </button>
  );

  const Sep = () => <span className="mx-1 h-6 w-px shrink-0 bg-border" />;

  const setLink = () => {
    const prev = editor.getAttributes("link").href as string | undefined;
    const url = window.prompt("URL", prev ?? "https://");
    if (url === null) return;
    if (url === "") {
      editor.chain().focus().unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
  };

  return (
    <div
      onMouseDown={(e) => e.preventDefault()}
      className={cn(
        "z-20 flex items-center gap-0.5 rounded-md border hairline bg-surface/95 backdrop-blur px-1 py-1",
        variant === "desktop" && "sticky top-0 -mx-1 flex-wrap",
        variant === "mobile" &&
          "sticky bottom-0 -mx-7 px-2 py-1.5 rounded-none border-x-0 border-b-0 flex-nowrap overflow-x-auto hide-scrollbar",
      )}
      style={variant === "mobile" ? { WebkitOverflowScrolling: "touch" } : undefined}
    >
      <Btn label="Heading 1" active={editor.isActive("heading", { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>
        <Heading1 className="h-4 w-4" />
      </Btn>
      <Btn label="Heading 2" active={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>
        <Heading2 className="h-4 w-4" />
      </Btn>
      <Btn label="Heading 3" active={editor.isActive("heading", { level: 3 })} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>
        <Heading3 className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn label="Bold" active={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}>
        <Bold className="h-4 w-4" />
      </Btn>
      <Btn label="Italic" active={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}>
        <Italic className="h-4 w-4" />
      </Btn>
      <Btn label="Underline" active={editor.isActive("underline")} onClick={() => editor.chain().focus().toggleUnderline().run()}>
        <UnderlineIcon className="h-4 w-4" />
      </Btn>
      <Btn label="Strikethrough" active={editor.isActive("strike")} onClick={() => editor.chain().focus().toggleStrike().run()}>
        <Strikethrough className="h-4 w-4" />
      </Btn>
      <Btn label="Highlight" active={editor.isActive("highlight")} onClick={() => editor.chain().focus().toggleHighlight().run()}>
        <Highlighter className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn label="Task list" active={editor.isActive("taskList")} onClick={() => editor.chain().focus().toggleTaskList().run()}>
        <ListChecks className="h-4 w-4" />
      </Btn>
      <Btn label="Bullet list" active={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()}>
        <List className="h-4 w-4" />
      </Btn>
      <Btn label="Numbered list" active={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
        <ListOrdered className="h-4 w-4" />
      </Btn>
      <Btn label="Indent" onClick={() => editor.chain().focus().sinkListItem("listItem").run()}>
        <Indent className="h-4 w-4" />
      </Btn>
      <Btn label="Outdent" onClick={() => editor.chain().focus().liftListItem("listItem").run()}>
        <Outdent className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn label="Quote" active={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()}>
        <Quote className="h-4 w-4" />
      </Btn>
      <Btn label="Code" active={editor.isActive("codeBlock")} onClick={() => editor.chain().focus().toggleCodeBlock().run()}>
        <Code className="h-4 w-4" />
      </Btn>
      <Btn label="Link" active={editor.isActive("link")} onClick={setLink}>
        <LinkIcon className="h-4 w-4" />
      </Btn>
      <Btn label="Divider" onClick={() => editor.chain().focus().setHorizontalRule().run()}>
        <Minus className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn label="Undo" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()}>
        <Undo2 className="h-4 w-4" />
      </Btn>
      <Btn label="Redo" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()}>
        <Redo2 className="h-4 w-4" />
      </Btn>
    </div>
  );
}
