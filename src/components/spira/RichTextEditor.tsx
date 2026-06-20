import { useEditor, EditorContent, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TaskList from "@tiptap/extension-task-list";
import TaskItem from "@tiptap/extension-task-item";
import Highlight from "@tiptap/extension-highlight";
import {
  TextStyle,
  Color,
  FontFamily,
  FontSize,
  LineHeight,
} from "@tiptap/extension-text-style";
import { useCallback, useEffect, useReducer, useRef, useState } from "react";
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
  Baseline,
  Eraser,
  Copy,
  Paintbrush,
  Type,
} from "lucide-react";
import { cn } from "@/lib/utils";

export function RichTextEditor({
  value,
  onChange,
  placeholder,
  embedded = false,
}: {
  value: string;
  onChange: (html: string) => void;
  placeholder?: string;
  // `embedded`: rendered as a bordered field inside a form (e.g. the create sheet)
  // rather than a full-screen editor — keeps the toolbar inside the box.
  embedded?: boolean;
}) {
  const isMobile = useIsMobile();
  const editor = useEditor({
    extensions: [
      // StarterKit 3.x already bundles link, underline and the list extensions —
      // configure them here rather than re-registering (a duplicate registration
      // breaks the link mark).
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
    content: value || "",
    editorProps: {
      attributes: {
        class: cn(
          "tiptap-content prose prose-sm max-w-none focus:outline-none text-[15px] leading-relaxed text-foreground/90",
          embedded && isMobile
            ? "min-h-[110px]"
            : isMobile
              ? "min-h-full"
              : "min-h-[40vh]",
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
  }, [editor, value]);

  if (!editor) return null;

  if (isMobile && embedded) {
    // A self-contained bordered field, matching the form's <Input> chrome exactly
    // (border-input, shadow-none, the same focus border + ring) so the note field
    // looks like the Title field, with the formatting toolbar tucked inside.
    return (
      <div
        className="rounded-md border border-input bg-surface shadow-none overflow-hidden transition-colors focus-within:border-primary focus-within:outline-none focus-within:ring-[3px] focus-within:ring-ring"
        data-vaul-no-drag
      >
        {/* No inner scroll — the field grows with content and the sheet's body
            scrolls, so the browser/vaul can scroll the cursor above the keyboard
            (a nested scroll here makes the field overlap the footer buttons). */}
        <div className="px-3.5 py-2">
          <EditorContent editor={editor} />
        </div>
        <Toolbar editor={editor} variant="mobile" embedded />
      </div>
    );
  }

  if (isMobile) {
    return (
      <div className="flex min-h-0 flex-1 flex-col" data-vaul-no-drag>
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
  embedded = false,
}: {
  editor: Editor;
  variant?: "desktop" | "mobile";
  embedded?: boolean;
}) {
  const isMobile = useIsMobile();
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  // Mobile: keep the formatting controls hidden behind a "Format" toggle so the
  // create sheet isn't crowded — most notes don't need them.
  const [showFmt, setShowFmt] = useState(false);

  // @tiptap/react v3's useEditor does not re-render on selection changes, so the
  // toolbar's active states and the font/size/spacing/colour controls would show
  // stale values. Re-render the toolbar whenever the selection or document changes
  // so every control reflects the CURRENT selection.
  const [, forceUpdate] = useReducer((n: number) => n + 1, 0);
  useEffect(() => {
    const rerender = () => forceUpdate();
    editor.on("selectionUpdate", rerender);
    editor.on("transaction", rerender);
    return () => {
      editor.off("selectionUpdate", rerender);
      editor.off("transaction", rerender);
    };
  }, [editor]);

  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState("");
  const [linkText, setLinkText] = useState("");
  const [copiedMarks, setCopiedMarks] = useState<
    { type: string; attrs: Record<string, unknown> }[] | null
  >(null);

  // ── Format painter / clear formatting ──────────────────────────────────────
  const copyFormatting = () => {
    // Marks active at the start of the selection. Skip links — copying a href
    // onto unrelated text is rarely intended.
    const marks = editor.state.selection.$from
      .marks()
      .filter((m) => m.type.name !== "link");
    setCopiedMarks(
      marks.map((m) => ({ type: m.type.name, attrs: { ...m.attrs } })),
    );
  };

  const applyFormatting = () => {
    if (!copiedMarks) return;
    let chain = editor.chain().focus().unsetAllMarks();
    for (const m of copiedMarks) chain = chain.setMark(m.type, m.attrs);
    chain.run();
  };

  const clearFormatting = () => {
    editor.chain().focus().unsetAllMarks().clearNodes().run();
  };

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

  const updateScrollState = useCallback(() => {
    const node = scrollRef.current;

    if (!node || variant !== "desktop") {
      setCanScrollLeft(false);
      setCanScrollRight(false);
      return;
    }

    const maxScrollLeft = node.scrollWidth - node.clientWidth;
    setCanScrollLeft(node.scrollLeft > 4);
    setCanScrollRight(node.scrollLeft < maxScrollLeft - 4);
  }, [variant]);

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
  }, [variant, updateScrollState]);

  const scrollToolbar = (direction: "left" | "right") => {
    const node = scrollRef.current;
    if (!node) return;

    const amount =
      Math.max(160, node.clientWidth * 0.65) * (direction === "left" ? -1 : 1);
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
      <Btn
        label="Heading 1"
        active={editor.isActive("heading", { level: 1 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
      >
        <Heading1 className="h-4 w-4" />
      </Btn>
      <Btn
        label="Heading 2"
        active={editor.isActive("heading", { level: 2 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
      >
        <Heading2 className="h-4 w-4" />
      </Btn>
      <Btn
        label="Heading 3"
        active={editor.isActive("heading", { level: 3 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
      >
        <Heading3 className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn
        label="Bold"
        active={editor.isActive("bold")}
        onClick={() => editor.chain().focus().toggleBold().run()}
      >
        <Bold className="h-4 w-4" />
      </Btn>
      <Btn
        label="Italic"
        active={editor.isActive("italic")}
        onClick={() => editor.chain().focus().toggleItalic().run()}
      >
        <Italic className="h-4 w-4" />
      </Btn>
      <Btn
        label="Underline"
        active={editor.isActive("underline")}
        onClick={() => editor.chain().focus().toggleUnderline().run()}
      >
        <UnderlineIcon className="h-4 w-4" />
      </Btn>
      <Btn
        label="Strikethrough"
        active={editor.isActive("strike")}
        onClick={() => editor.chain().focus().toggleStrike().run()}
      >
        <Strikethrough className="h-4 w-4" />
      </Btn>
      <Sep />
      {/* Font family / size / colors. stopPropagation so the toolbar's
          mousedown-preventDefault (which keeps the editor selection for the
          icon buttons) doesn't stop these native controls from opening. */}
      <select
        aria-label="Font family"
        title="Font"
        value={(editor.getAttributes("textStyle").fontFamily as string) || ""}
        onMouseDown={(e) => e.stopPropagation()}
        onChange={(e) => {
          const v = e.target.value;
          if (v) editor.chain().focus().setFontFamily(v).run();
          else editor.chain().focus().unsetFontFamily().run();
        }}
        className="h-9 shrink-0 rounded-md border hairline bg-transparent px-1.5 text-xs text-foreground"
      >
        <option value="">Font</option>
        <option value="Arial, Helvetica, sans-serif">Sans</option>
        <option value="Georgia, 'Times New Roman', serif">Serif</option>
        <option value="'Courier New', monospace">Mono</option>
      </select>
      <select
        aria-label="Font size"
        title="Size"
        value={(editor.getAttributes("textStyle").fontSize as string) || ""}
        onMouseDown={(e) => e.stopPropagation()}
        onChange={(e) => {
          const v = e.target.value;
          if (v) editor.chain().focus().setFontSize(v).run();
          else editor.chain().focus().unsetFontSize().run();
        }}
        className="h-9 shrink-0 rounded-md border hairline bg-transparent px-1.5 text-xs text-foreground"
      >
        <option value="">Size</option>
        <option value="12px">12</option>
        <option value="14px">14</option>
        <option value="16px">16</option>
        <option value="18px">18</option>
        <option value="24px">24</option>
        <option value="32px">32</option>
      </select>
      <select
        aria-label="Line spacing"
        title="Line spacing"
        value={(editor.getAttributes("textStyle").lineHeight as string) || ""}
        onMouseDown={(e) => e.stopPropagation()}
        onChange={(e) => {
          const v = e.target.value;
          if (v) editor.chain().focus().setLineHeight(v).run();
          else editor.chain().focus().unsetLineHeight().run();
        }}
        className="h-9 shrink-0 rounded-md border hairline bg-transparent px-1.5 text-xs text-foreground"
      >
        <option value="">Spacing</option>
        <option value="1">1.0</option>
        <option value="1.15">1.15</option>
        <option value="1.5">1.5</option>
        <option value="2">2.0</option>
      </select>
      <label
        title="Text color"
        onMouseDown={(e) => e.stopPropagation()}
        className="relative inline-flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
      >
        <Baseline className="h-4 w-4" />
        <input
          type="color"
          aria-label="Text color"
          value={
            (editor.getAttributes("textStyle").color as string) || "#111111"
          }
          onInput={(e) =>
            editor
              .chain()
              .focus()
              .setColor((e.target as HTMLInputElement).value)
              .run()
          }
          className="absolute inset-0 cursor-pointer opacity-0"
        />
      </label>
      <label
        title="Highlight color"
        onMouseDown={(e) => e.stopPropagation()}
        className="relative inline-flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
      >
        <Highlighter className="h-4 w-4" />
        <input
          type="color"
          aria-label="Highlight color"
          value={
            (editor.getAttributes("highlight").color as string) || "#fff2a8"
          }
          onInput={(e) =>
            editor
              .chain()
              .focus()
              .setHighlight({ color: (e.target as HTMLInputElement).value })
              .run()
          }
          className="absolute inset-0 cursor-pointer opacity-0"
        />
      </label>
      <Sep />
      <Btn
        label="Task list"
        active={editor.isActive("taskList")}
        onClick={() => editor.chain().focus().toggleTaskList().run()}
      >
        <ListChecks className="h-4 w-4" />
      </Btn>
      <Btn
        label="Bullet list"
        active={editor.isActive("bulletList")}
        onClick={() => editor.chain().focus().toggleBulletList().run()}
      >
        <List className="h-4 w-4" />
      </Btn>
      <Btn
        label="Numbered list"
        active={editor.isActive("orderedList")}
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
      >
        <ListOrdered className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn
        label="Quote"
        active={editor.isActive("blockquote")}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
      >
        <Quote className="h-4 w-4" />
      </Btn>
      <Btn
        label="Code"
        active={editor.isActive("codeBlock")}
        onClick={() => editor.chain().focus().toggleCodeBlock().run()}
      >
        <Code className="h-4 w-4" />
      </Btn>
      <Btn
        label="Link"
        active={editor.isActive("link")}
        onClick={openLinkDialog}
      >
        <LinkIcon className="h-4 w-4" />
      </Btn>
      <Btn
        label="Divider"
        onClick={() => editor.chain().focus().setHorizontalRule().run()}
      >
        <Minus className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn label="Copy formatting" onClick={copyFormatting}>
        <Copy className="h-4 w-4" />
      </Btn>
      <Btn
        label="Apply copied formatting"
        disabled={!copiedMarks}
        onClick={applyFormatting}
      >
        <Paintbrush className="h-4 w-4" />
      </Btn>
      <Btn label="Clear formatting" onClick={clearFormatting}>
        <Eraser className="h-4 w-4" />
      </Btn>
      <Sep />
      <Btn
        label="Undo"
        disabled={!editor.can().undo()}
        onClick={() => editor.chain().focus().undo().run()}
      >
        <Undo2 className="h-4 w-4" />
      </Btn>
      <Btn
        label="Redo"
        disabled={!editor.can().redo()}
        onClick={() => editor.chain().focus().redo().run()}
      >
        <Redo2 className="h-4 w-4" />
      </Btn>
    </>
  );

  if (variant === "mobile") {
    return (
      <>
        {embedded ? (
          // CREATE form: collapse the formatting behind a "Format" toggle so the
          // bottom sheet isn't crowded.
          <div
            onMouseDown={(e) => e.preventDefault()}
            className="shrink-0 border-t hairline bg-surface"
          >
            <div className="flex items-center gap-1 px-2 py-1.5">
              <button
                type="button"
                onClick={() => setShowFmt((v) => !v)}
                className={cn(
                  "inline-flex items-center gap-1.5 h-9 px-2.5 rounded-md text-[13px] font-semibold transition-colors",
                  showFmt
                    ? "bg-primary-soft text-primary"
                    : "text-muted-foreground hover:bg-secondary hover:text-foreground",
                )}
              >
                <Type className="h-4 w-4" /> Format
              </button>
              {!showFmt && (
                <>
                  <Btn
                    label="Bold"
                    active={editor.isActive("bold")}
                    onClick={() => editor.chain().focus().toggleBold().run()}
                  >
                    <Bold className="h-4 w-4" />
                  </Btn>
                  <Btn
                    label="Bullet list"
                    active={editor.isActive("bulletList")}
                    onClick={() =>
                      editor.chain().focus().toggleBulletList().run()
                    }
                  >
                    <List className="h-4 w-4" />
                  </Btn>
                </>
              )}
            </div>
            {showFmt && (
              <div
                className="flex items-center gap-0.5 overflow-x-auto border-t hairline px-2 py-1.5 hide-scrollbar"
                style={{ WebkitOverflowScrolling: "touch" }}
              >
                {toolbarItems}
              </div>
            )}
          </div>
        ) : (
          // OPEN / edit a note: the full formatting bar (all icons, scrollable).
          <div
            onMouseDown={(e) => e.preventDefault()}
            className="-mx-7 mt-4 flex shrink-0 items-center gap-0.5 overflow-x-auto border border-x-0 border-b-0 hairline bg-surface px-2 py-1.5 hide-scrollbar"
            style={{
              paddingBottom: "max(env(safe-area-inset-bottom), 0px)",
              WebkitOverflowScrolling: "touch",
            }}
          >
            {toolbarItems}
          </div>
        )}
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
        className="sticky top-0 z-20 flex items-center gap-2 rounded-md border hairline bg-surface px-2 py-1"
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
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
        <div>
          <h2 className="font-sans text-lg font-bold">Add a link</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Add or update a link inside this note.
          </p>
        </div>
      </div>

      <form
        className="px-7 pt-2 pb-6 space-y-5 overflow-y-auto flex-1"
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
            autoFocus
          />
        </div>

        <div className="px-0 pt-3 flex items-center justify-between gap-3 bg-surface">
          <button
            type="button"
            onClick={canRemove ? onRemove : () => onOpenChange(false)}
            className={cn(
              "h-11 px-4 text-sm font-semibold",
              canRemove
                ? "text-destructive hover:text-destructive/80"
                : "link-action",
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
