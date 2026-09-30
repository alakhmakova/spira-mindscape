import { createContext, useContext, useEffect, useRef, useState } from "react";
import { ChevronLeft, KebabVertical, X } from "@/components/spira/icons";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

/**
 * One action of the head. It is described once and rendered twice — as an icon button on a
 * laptop, as a worded row in the kebab menu on a phone — so the two can never offer different
 * things (the same shape `DeadlinePopover` uses for its trigger).
 *
 * `items` turns the action into a submenu: the note's "Download as…" is a choice, not a click, and
 * on a phone its choices are folded into the one menu as a labelled group.
 */
/**
 * Whether the resource fills the screen. **The mark out follows it** (owner, 2026-09-22): a panel
 * that covers everything is a place you go BACK from, so it wears a chevron; a panel sitting over
 * a page you can still see is something you CLOSE, so it wears a cross. The button does the same
 * thing either way — only the promise it makes about what is behind it differs.
 *
 * A phone is always full screen, and so is the note editor; the resizable desktop panel supplies
 * the real answer through this context.
 */
export const ResourceFullScreen = createContext(false);

export type ResourceHeadAction = {
  key: string;
  /** The wording on the menu row and in the tooltip. */
  label: string;
  icon: React.ReactNode;
  onClick?: () => void;
  items?: { label: string; onClick: () => void }[];
  tone?: "default" | "destructive";
};

/**
 * The band every resource panel opens under — **the web twin of Android's `ResourceTopBar`**
 * (owner, 2026-09-20). One component, so a note, a file, an email and a vacancy map cannot drift
 * into four different-looking headers.
 *
 * - **The chevron disc on the left belongs to the FULL-SCREEN state alone** (owner, 2026-09-24).
 *   A panel that covers the screen has somewhere to go back to and says so; a panel sitting over
 *   a page you can still see has not, and there the disc was a cross in a circle that said
 *   nothing about where it led.
 * - **The way out is the X, at the end of the action group on the right**, with the rest of the
 *   actions — one row of controls rather than one on each side.
 * - **On a laptop those actions are ONE segmented control**, and on a phone a kebab. A phone has
 *   no room for four buttons beside a title, and the kebab is the app's own per-element menu
 *   (CLAUDE.md → 6).
 * - **A name too long to fit scrolls**, rather than being cut off — see `MarqueeText`.
 */
export function ResourceHead({
  title,
  onBack,
  backLabel,
  actions = [],
  children,
}: {
  /** The plain name, used when the caller has no editor of its own. */
  title: string;
  onBack: () => void;
  /** Overrides the wording; by default it follows the mark — "Back" or "Close". */
  backLabel?: string;
  actions?: ResourceHeadAction[];
  /** An editable title — rendered in the title's place when supplied. */
  children?: React.ReactNode;
}) {
  const isMobile = useIsMobile();
  const fullScreen = useContext(ResourceFullScreen) || isMobile;
  const shown = actions.filter((action) => action.onClick || action.items);
  const label = backLabel ?? (fullScreen ? "Back" : "Close");

  /**
   * Closing, as one more action in the group (owner, 2026-09-24). It is last, because it is the
   * one that ends the panel — everything before it acts ON the resource.
   *
   * It is named "Close" whatever the left disc says: the chevron promises a place to go back to,
   * this simply shuts the panel, and two controls that do the same thing still describe it
   * differently.
   */
  const closeAction: ResourceHeadAction = {
    key: "close",
    label: "Close",
    icon: <X className="h-4 w-4" />,
    onClick: onBack,
  };

  return (
    <div className="sticky top-0 z-10 flex shrink-0 items-center gap-2.5 bg-primary px-3 py-3 text-white">
      {/* Solid white with a teal glyph, like Android's `HeaderCircleButton` — a wash of white on
          teal reads as barely there. It is drawn **only when the panel IS the screen**: there the
          chevron says where it goes, which is a different statement from "close this", and
          closing is what the X on the right is for. */}
      {fullScreen && (
        <button
          type="button"
          onClick={onBack}
          aria-label={label}
          title={label}
          className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white text-primary transition-colors hover:bg-white/90"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
      )}

      {/* The name scrolls whether it is editable or not: `InlineText` shows a plain span until it
          is tapped, and that span is what overflows. The animation stops while the field has the
          caret — see `.marquee` in styles.css. */}
      <MarqueeText
        className={
          children ? undefined : "font-sans text-lg font-bold text-white"
        }
      >
        {children ?? title}
      </MarqueeText>

      {/* A phone keeps the kebab and no cross: the chevron is already there, because a phone is
          always full screen. A laptop gets the group, always — it holds the way out. */}
      {(isMobile ? shown.length > 0 : true) &&
        (isMobile ? (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                aria-label="Actions"
                title="Actions"
                className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white text-primary transition-colors hover:bg-white/90"
              >
                <KebabVertical className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {shown.map((action, index) =>
                action.items ? (
                  <div key={action.key}>
                    {index > 0 && <DropdownMenuSeparator />}
                    {action.items.map((item) => (
                      <DropdownMenuItem key={item.label} onClick={item.onClick}>
                        {item.label}
                      </DropdownMenuItem>
                    ))}
                  </div>
                ) : (
                  <DropdownMenuItem
                    key={action.key}
                    onClick={action.onClick}
                    className={cn(
                      action.tone === "destructive" &&
                        "text-destructive focus:text-destructive",
                    )}
                  >
                    {action.icon}
                    {action.label}
                  </DropdownMenuItem>
                ),
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          /* **One segmented control, not a row of loose glyphs** (owner, 2026-09-24, from her
             reference): white buttons with grey marks, the one under the pointer filled Kale
             with a white mark, and a hairline round the group so it reads as one thing on the
             teal band. Closing is the last button in it. */
          <div className={HEAD_GROUP}>
            {[...shown, closeAction].map((action) =>
              action.items ? (
                <DropdownMenu key={action.key}>
                  <DropdownMenuTrigger asChild>
                    <button
                      type="button"
                      aria-label={action.label}
                      title={action.label}
                      className={HEAD_ICON}
                    >
                      {action.icon}
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    {action.items.map((item) => (
                      <DropdownMenuItem key={item.label} onClick={item.onClick}>
                        {item.label}
                      </DropdownMenuItem>
                    ))}
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <button
                  key={action.key}
                  type="button"
                  onClick={action.onClick}
                  aria-label={action.label}
                  title={action.label}
                  className={HEAD_ICON}
                >
                  {action.icon}
                </button>
              ),
            )}
          </div>
        ))}
    </div>
  );
}

/**
 * The group the head's actions sit in (owner, 2026-09-24, from her reference).
 *
 * Its border has to read against **two** backgrounds at once — the teal band outside it and the
 * white buttons inside — so it is neutral-700 `#ABABAB`: light enough to show on Kale-500, dark
 * enough to show on white. A lighter grey vanished into the buttons; a darker one drew a black
 * frame on the band.
 */
const HEAD_GROUP =
  "flex shrink-0 items-center divide-x divide-[#ABABAB] overflow-hidden rounded-[4px] border border-[#ABABAB] bg-white";

/**
 * One button in it: white with a grey mark at rest, **filled Kale with a white mark under the
 * pointer** — the pair her reference uses for the chosen one, so hovering previews what choosing
 * looks like. The focus ring is that same fill, since a ring would be lost between two borders.
 */
const HEAD_ICON =
  "grid h-8 w-9 place-items-center bg-white text-[#6C6C72] transition-colors hover:bg-primary hover:text-white focus-visible:bg-primary focus-visible:text-white focus-visible:outline-none";

/**
 * Text that scrolls itself when it is wider than the room it has, and sits still when it is not
 * (owner, 2026-09-20 — "если название полностью не помещается оно должно прокручиваться", as on
 * Android). It measures rather than guesses, because a name's width depends on the panel's, which
 * the user drags.
 *
 * The animation walks the text to its own overflow and back, at a constant **40px a second**, so a
 * name twice too long does not scroll twice as fast. It pauses at each end (the eased keyframes in
 * `styles.css`), which is what makes it readable rather than a ticker.
 */
export function MarqueeText({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const outer = useRef<HTMLDivElement>(null);
  const inner = useRef<HTMLSpanElement>(null);
  const [overflow, setOverflow] = useState(0);

  useEffect(() => {
    const box = outer.current;
    const text = inner.current;
    if (!box || !text) return;
    const measure = () =>
      setOverflow(Math.max(0, text.scrollWidth - box.clientWidth));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    observer.observe(text);
    return () => observer.disconnect();
  }, [children]);

  return (
    <div ref={outer} className="marquee min-w-0 flex-1 overflow-hidden">
      <span
        ref={inner}
        // The descendants are held to one line too: `InlineText` sets `whitespace-pre-wrap` on
        // its own display span and on its textarea, so without this a long name simply wrapped
        // to three lines in the head and there was nothing left to scroll.
        className={cn(
          "block whitespace-nowrap [&_*]:whitespace-nowrap",
          className,
        )}
        style={
          overflow > 0
            ? {
                animation: `resource-title-marquee ${(overflow / 40 + 3).toFixed(1)}s ease-in-out infinite`,
                // The distance is the overflow itself, so the last letter stops at the edge
                // rather than scrolling off it.
                ["--marquee-shift" as string]: `-${overflow}px`,
              }
            : undefined
        }
      >
        {children}
      </span>
    </div>
  );
}
