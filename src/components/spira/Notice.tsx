import type { ReactNode } from "react";

import {
  CircleCheckFill,
  CircleExclamationFilled,
  CircleInfoFill,
  Sparkles,
  TriangleExclamationFill,
  X,
} from "@/components/spira/icons";
import { cn } from "@/lib/utils";

/**
 * The app's one message card — the web twin of Android's `SpiraNoticeCard`
 * (`ui/components/SpiraNotice.kt`), and the shape CLAUDE.md's notice spec describes.
 *
 * Every message the app shows is this card. A **toast** floats and times itself out; a **notice
 * inside a block** sits in the flow. Those are only *where* it sits — the card is the same, because
 * the app once had four shapes saying the same kind of thing and none of them agreed.
 *
 * The kind changes the border, the fill and the mark, and **never the type**: near-black words in
 * every kind, because colouring them as well only makes them harder to read — the same rule the
 * pills follow.
 *
 * The tint is **nearly white on purpose**. The card still has to read as a white card on the page;
 * the border is what tells the kinds apart, and a fill any stronger turns a message into a block of
 * colour.
 */
export type NoticeKind = "success" | "error" | "warning" | "info" | "ai";

/**
 * **The one colour table for every message in the app** — the toast reads it too
 * (`src/components/ui/sonner.tsx`), so a kind cannot mean one thing floating and another in the
 * flow.
 *
 * Border-and-glyph ink, then the fill. Two of the five the owner gave verbatim (2026-08-21): the
 * blue `#006CC1` over `#FDFCFF` and the green `#0A8080` over `#F9FDFC`. Yellow and red follow the
 * identical rule — the ramp's solid step outlining its own `100` tint.
 *
 * **Warning is a real yellow, never the brown `#896500`.** `warning-900` is brown on screen and a
 * brown triangle on a warning was rejected on sight (owner, 2026-08-18); this is `warning-500`, the
 * same `#C99500` the assistant's error turn uses.
 *
 * **`ai` is the one that breaks the pattern, and deliberately.** Its border is Intelligence-**400**
 * `#BDAEFF` rather than a solid 900 step, because the assistant's surfaces are drawn in that violet
 * throughout and a saturated outline would out-shout them (owner, 2026-08-21).
 */
export const NOTICE_KINDS = {
  success: { ink: "#0A8080", fill: "#F9FDFC", Glyph: CircleCheckFill },
  error: { ink: "#C53336", fill: "#FFFBFB", Glyph: CircleExclamationFilled },
  warning: { ink: "#C99500", fill: "#FFFBF7", Glyph: TriangleExclamationFill },
  info: { ink: "#006CC1", fill: "#FDFCFF", Glyph: CircleInfoFill },
  ai: { ink: "#BDAEFF", fill: "#FEFBFF", Glyph: Sparkles },
} as const satisfies Record<
  NoticeKind,
  { ink: string; fill: string; Glyph: typeof CircleCheckFill }
>;

/** The shape's radius and shadow, shared with the toast. */
export const NOTICE_RADIUS = "8px";
export const NOTICE_SHADOW =
  "0 4px 12px rgba(28,28,28,.08), 0 2px 8px rgba(28,28,28,.04)";

/** One kind's mark, in the kind's own ink — used by the card and by the toast's icon slot. */
export function NoticeGlyph({
  kind,
  className,
}: {
  kind: NoticeKind;
  className?: string;
}) {
  const { ink, Glyph } = NOTICE_KINDS[kind];
  return (
    <Glyph
      className={cn("h-4 w-4 shrink-0", className)}
      style={{ color: ink }}
    />
  );
}

export function NoticeCard({
  kind = "info",
  children,
  onDismiss,
  className,
  role = "status",
}: {
  kind?: NoticeKind;
  children: ReactNode;
  /**
   * Omit — or pass `null` — where the message is not the user's to dismiss.
   *
   * **A notice the user cannot act on has no X.** "No goals match that search or filter" stands
   * until the filter changes, so a dismiss button on it would be a lie.
   */
  onDismiss?: (() => void) | null;
  className?: string;
  role?: "status" | "alert";
}) {
  const { ink, fill } = NOTICE_KINDS[kind];
  return (
    <div
      role={role}
      aria-live={role === "alert" ? "assertive" : "polite"}
      className={cn(
        "flex items-start gap-3 border p-3.5 text-sm text-foreground",
        className,
      )}
      style={{
        borderColor: ink,
        background: fill,
        borderRadius: NOTICE_RADIUS,
        boxShadow: NOTICE_SHADOW,
      }}
    >
      {/* Aligned to the message's **first line**, not centred against a message that wraps. */}
      <NoticeGlyph kind={kind} className="mt-px" />
      {/* `min-w-0` lets the column shrink; `break-words` + `overflow-wrap:anywhere` are what
          actually break the text once it has. A provider's error is one long unbroken URL —
          `generativelanguage.googleapis.com/generate_content_free_tier_requests` — and with the
          first alone it ran straight past the card's right edge (owner, 2026-08-29: "ничего не
          должно вываливаться из блоков"). */}
      <div className="min-w-0 flex-1 break-words leading-[1.5] [overflow-wrap:anywhere]">
        {children}
      </div>
      {onDismiss && (
        // The X keeps the glyph's rule on the other side: first line, so it is top-right on a
        // message that wraps and simply right on one that doesn't.
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss"
          className="-mr-1 -mt-0.5 grid h-6 w-6 shrink-0 self-start place-items-center rounded-md text-muted-foreground transition-colors hover:bg-black/5 hover:text-foreground"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
}

/**
 * The message a list shows when its own search or filter has hidden every row.
 *
 * **A `Warning`, not a grey empty state** (CLAUDE.md): the empty state is for a list with nothing
 * in it — an invitation. A list the user has just hidden is a different sentence, and a muted line
 * centred in a blank page reads as "there is nothing here". Never dismissible, because it stands
 * until the filter changes.
 */
export function FilteredEmptyNotice({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <NoticeCard kind="warning" onDismiss={null} className={className}>
      {children}
    </NoticeCard>
  );
}
