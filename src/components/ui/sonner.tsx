import { Toaster as Sonner } from "sonner";

import { NOTICE_KINDS, NoticeGlyph } from "@/components/spira/Notice";

type ToasterProps = React.ComponentProps<typeof Sonner>;

/** `--normal-bg` / `--normal-border` / `--normal-text` for one kind, as Tailwind arbitrary props. */
function kindVars(kind: keyof typeof NOTICE_KINDS) {
  const { ink, fill } = NOTICE_KINDS[kind];
  return `[--normal-bg:${fill}] [--normal-border:${ink}] [--normal-text:#222525]`;
}

/**
 * Spira's toast — the same card as every other message in the app.
 *
 * It draws `NoticeCard`'s shape and reads its colour table (`src/components/spira/Notice.tsx`),
 * because a toast and a notice sitting inside a block are one card and differ only in where they
 * sit: the toast floats and times itself out, the banner sits in the flow. Android's `SpiraToast`
 * and `SpiraInlineBanner` are the same pair over one `SpiraNoticeCard`.
 *
 * The colours, the radius and the shadow all come from `NOTICE_KINDS`, so there is one table for
 * the whole app rather than a copy per surface. See CLAUDE.md's notice spec for the values.
 *
 * # Why this drives sonner's own variables instead of styling the card
 *
 * sonner injects its stylesheet **unlayered** at the end of `<head>`, and Tailwind v4 compiles
 * every utility into `@layer utilities`. Unlayered rules beat layered ones whatever the
 * specificity, so a `bg-[#FFFBFB]` on the toast simply loses to sonner's
 * `[data-sonner-toast][data-styled=true] { background: var(--normal-bg) }` — the first attempt at
 * this styled nothing at all, while the in-page `NoticeCard` (inline styles) rendered correctly,
 * which is exactly the "two shapes for one message" split the card exists to end.
 *
 * So the per-kind classes declare **`--normal-bg` / `--normal-border` / `--normal-text` on the toast
 * element itself**. A custom property declared on an element beats one inherited from an ancestor
 * (sonner sets these on the container) with no layer contest at all, and sonner's own unlayered
 * rule then reads the value we put there. Everything sonner hard-codes rather than reading from a
 * variable — the shadow, and every one of the close button's properties — needs `!`.
 */
const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      className="toaster group"
      icons={{
        success: <NoticeGlyph kind="success" />,
        error: <NoticeGlyph kind="error" />,
        warning: <NoticeGlyph kind="warning" />,
        info: <NoticeGlyph kind="info" />,
        loading: <NoticeGlyph kind="success" className="animate-pulse" />,
      }}
      closeButton
      toastOptions={{
        classNames: {
          toast: [
            // `items-start!`: sonner centres the row, which floats the glyph and the X halfway
            // down a message that wraps. Both belong on the first line.
            "group toast items-start! gap-3",
            // sonner hard-codes its shadow, so this one has to be important to land.
            "shadow-[0_4px_12px_rgba(28,28,28,0.08),0_2px_8px_rgba(28,28,28,0.04)]!",
          ].join(" "),
          success: kindVars("success"),
          error: kindVars("error"),
          warning: kindVars("warning"),
          info: kindVars("info"),
          // The glyph sits on the first line of the text, not centred against a two-line message.
          icon: "mt-px shrink-0",
          title: "text-[14px] font-medium leading-[1.5]",
          description:
            "group-[.toast]:text-[13px] group-[.toast]:text-muted-foreground",
          /*
           * **The X sits INSIDE the card, on the right** (owner, 2026-08-21) — top-right when the
           * message wraps, simply right when it is one line.
           *
           * One rule gives both: it stops being absolutely positioned and becomes an ordinary flex
           * item aligned to the message's **first line**, exactly as the glyph on the other side
           * is. On a one-line toast that first line *is* the card, so the button reads as centred;
           * on a three-line one it stays up at the top where it cannot sit in the middle of a
           * sentence.
           *
           * sonner's own rule is a floating circle pinned OUTSIDE the corner (`position:absolute`,
           * `left: var(--toast-close-button-start)`, a translate, a border and a 50% radius), and
           * every one of those properties is unlayered — so each has to be overridden with `!`,
           * not merely restated.
           */
          closeButton: [
            // `transform-none!`, not `translate-none`: sonner moves it with `transform`, and the
            // two are different properties — the first attempt left the translate in place.
            "static! order-last ml-auto shrink-0 transform-none! self-start",
            "h-6! w-6! rounded-md! border-transparent! bg-transparent!",
            "text-muted-foreground! hover:bg-black/5! hover:border-transparent!",
            "hover:text-foreground!",
          ].join(" "),
          actionButton:
            "group-[.toast]:bg-primary group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:bg-muted group-[.toast]:text-muted-foreground",
        },
      }}
      // The radius is sonner's own container variable — inline, so no layer can outrank it.
      style={{ "--border-radius": "8px" } as React.CSSProperties}
      {...props}
    />
  );
};

export { Toaster };
