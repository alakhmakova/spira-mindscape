import { useEditor, EditorContent, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import Link from "@tiptap/extension-link";
import TaskList from "@tiptap/extension-task-list";
import TaskItem from "@tiptap/extension-task-item";
import Highlight from "@tiptap/extension-highlight";
import { useEffect, useRef, useState } from "react";
import { useIsMobile } from "@/hooks/use-mobile";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
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
  Quote,
  Code,
  Link as LinkIcon,
  Minus,
  Undo2,
  Redo2,
  ChevronLeft,
  ChevronRight,
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
  const isMobile = useIsMobile();
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
        class: cn(
          "tiptap-content prose prose-sm max-w-none focus:outline-none text-[15px] leading-relaxed text-foreground/90",
          isMobile ? "min-h-full" : "min-h-[40vh]",
        ),
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

  if (!editor) return null;

  if (isMobile) {
    return (
      <div
        className="flex min-h-0 flex-1 flex-col"
        data-vaul-no-drag
      >
        <div className="min-h-0 flex-1 overflow-y-auto pb-4">
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

function Toolbar({
  editor,
  variant = "desktop",
}: {
  editor: Editor;
  variant?: "desktop" | "mobile";
}) {
  const isMobile = useIsMobile();
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState("");
  const [linkText, setLinkText] = useState("");

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
        "inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md p-0 text-center text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40 disabled:hover:bg-transparent",
        active && "bg-primary-soft text-primary hover:bg-primary-soft",
      )}
    >
      {children}
    </button>
  );

  const Sep = () => <span className="mx-1 h-6 w-px shrink-0 bg-border" />;

  const updateScrollState = () => {
    const node = scrollRef.current;

    if (!node || variant !== "desktop") {
      setCanScrollLeft(false);
      setCanScrollRight(false);
      return;
    }

    const maxScrollLeft = node.scrollWidth - node.clientWidth;
    setCanScrollLeft(node.scrollLeft > 4);
    setCanScrollRight(node.scrollLeft < maxScrollLeft - 4);
  };

  useEffect(() => {
    if (variant !== "desktop") return;

    const node = scrollRef.current;
    if (!node) return;

    updateScrollState();

    const handleResize = () => updateScrollState();
    node.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", handleResize);

    return () => {
      node.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", handleResize);
    };
  }, [variant]);

  const scrollToolbar = (direction: "left" | "right") => {
    const node = scrollRef.current;
    if (!node) return;

    const amount = Math.max(160, node.clientWidth * 0.65) * (direction === "left" ? -1 : 1);
    node.scrollBy({ left: amount, behavior: "smooth" });
  };

  const openLinkDialog = () => {
    if (editor.isActive("link")) {
      editor.chain().focus().extendMarkRange("link").run();
    }

    const previousUrl = editor.getAttributes("link").href as string | undefined;
    const { from, to } = editor.state.selection;
    const selectedText = editor.state.doc.textBetween(from, to, " ").trim();

    setLinkUrl(previousUrl ?? "https://");
    setLinkText(selectedText);
    setLinkDialogOpen(true);
  };

  const closeLinkDialog = () => {
    setLinkDialogOpen(false);
  };

  const submitLink = () => {
    const href = linkUrl.trim();
    if (!href) return;

    const { from, to, empty } = editor.state.selection;
    const selectedText = editor.state.doc.textBetween(from, to, " ").trim();
    const nextText = linkText.trim() || (!selectedText ? href : "");

    if (nextText && (empty || nextText !== selectedText)) {
      editor
        .chain()
        .focus()
        .insertContent({
          type: "text",
          text: nextText,
          marks: [{ type: "link", attrs: { href } }],
        })
        .run();
    } else {
      editor.chain().focus().extendMarkRange("link").setLink({ href }).run();
    }

    closeLinkDialog();
  };

  const removeLink = () => {
    editor.chain().focus().extendMarkRange("link").unsetLink().run();
    closeLinkDialog();
  };

  const toolbarItems = (
    <>
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
      <Sep />
      <Btn label="Quote" active={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()}>
        <Quote className="h-4 w-4" />
      </Btn>
      <Btn label="Code" active={editor.isActive("codeBlock")} onClick={() => editor.chain().focus().toggleCodeBlock().run()}>
        <Code className="h-4 w-4" />
      </Btn>
      <Btn label="Link" active={editor.isActive("link")} onClick={openLinkDialog}>
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
    </>
  );

  if (variant === "mobile") {
    return (
      <>
        <div
          onMouseDown={(e) => e.preventDefault()}
          className="-mx-7 mt-4 flex shrink-0 items-center gap-0.5 overflow-x-auto border border-x-0 border-b-0 hairline bg-surface/95 px-2 py-1.5 backdrop-blur hide-scrollbar"
          style={{
            paddingBottom: "max(env(safe-area-inset-bottom), 0px)",
            WebkitOverflowScrolling: "touch",
          }}
        >
          {toolbarItems}
        </div>
        <LinkDialog
          isMobile={isMobile}
          open={linkDialogOpen}
          onOpenChange={setLinkDialogOpen}
          url={linkUrl}
          text={linkText}
          onUrlChange={setLinkUrl}
          onTextChange={setLinkText}
          onSubmit={submitLink}
          onRemove={removeLink}
          canRemove={editor.isActive("link")}
        />
      </>
    );
  }

  return (
    <>
      <div
        onMouseDown={(e) => e.preventDefault()}
        className="sticky top-0 z-20 flex items-center gap-2 rounded-md border hairline bg-surface/95 px-2 py-1 backdrop-blur"
      >
        <button
          type="button"
          aria-label="Scroll toolbar left"
          onClick={() => scrollToolbar("left")}
          disabled={!canScrollLeft}
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-30 disabled:hover:bg-transparent"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <div
          ref={scrollRef}
          className="flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto hide-scrollbar"
          style={{ WebkitOverflowScrolling: "touch" }}
        >
          {toolbarItems}
        </div>
        <button
          type="button"
          aria-label="Scroll toolbar right"
          onClick={() => scrollToolbar("right")}
          disabled={!canScrollRight}
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-30 disabled:hover:bg-transparent"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
      <LinkDialog
        isMobile={isMobile}
        open={linkDialogOpen}
        onOpenChange={setLinkDialogOpen}
        url={linkUrl}
        text={linkText}
        onUrlChange={setLinkUrl}
        onTextChange={setLinkText}
        onSubmit={submitLink}
        onRemove={removeLink}
        canRemove={editor.isActive("link")}
      />
    </>
  );
}

function LinkDialog({
  isMobile,
  open,
  onOpenChange,
  url,
  text,
  onUrlChange,
  onTextChange,
  onSubmit,
  onRemove,
  canRemove,
}: {
  isMobile: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  url: string;
  text: string;
  onUrlChange: (value: string) => void;
  onTextChange: (value: string) => void;
  onSubmit: () => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  const body = (
    <>
      <div className="px-7 py-5 border-b hairline flex items-center justify-between sticky top-0 bg-surface z-10">
        <div>
          <h2 className="font-sans text-lg font-bold">Add a link</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Add or update a link inside this note.
          </p>
        </div>
      </div>

      <form
        className="px-7 py-6 space-y-5 overflow-y-auto flex-1"
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit();
        }}
      >
        <div>
          <label className="text-sm font-semibold block mb-1.5">Text</label>
          <Input
            value={text}
            onChange={(e) => onTextChange(e.target.value)}
            placeholder="Link text"
            className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
          />
        </div>

        <div>
          <label className="text-sm font-semibold block mb-1.5">
            URL <span className="text-destructive">*</span>
          </label>
          <Input
            value={url}
            onChange={(e) => onUrlChange(e.target.value)}
            placeholder="https://"
            className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
            autoFocus
          />
        </div>

        <div className="px-0 pt-3 border-t hairline flex items-center justify-between gap-3 bg-surface">
          <button
            type="button"
            onClick={canRemove ? onRemove : () => onOpenChange(false)}
            className={cn(
              "h-11 px-4 text-sm font-semibold",
              canRemove ? "text-destructive hover:text-destructive/80" : "link-action",
            )}
          >
            {canRemove ? "Remove link" : "Cancel"}
          </button>
          <button
            type="submit"
            className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 disabled:opacity-50"
            disabled={!url.trim()}
          >
            Save link
          </button>
        </div>
      </form>
    </>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">
          {body}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md p-0 gap-0 overflow-hidden border hairline bg-surface">
        <DialogTitle className="sr-only">Add a link</DialogTitle>
        {body}
      </DialogContent>
    </Dialog>
  );
}
