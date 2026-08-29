import { useState, useRef } from "react";
import {
  Calendar as CalendarIcon,
  Trash2,
  X,
  ChevronRight,
} from "@/components/spira/icons";
import { format, isPast, differenceInCalendarDays } from "date-fns";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { SheetHead } from "@/components/spira/SheetHead";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

/**
 * Editable deadline trigger — the one way the app asks for a date.
 *
 * **On a laptop it opens a popover; on a phone it opens a sheet** (owner, 2026-08-29). The
 * popover was the only surface for a while, and on a phone it read wrong: a floating card over
 * an open form, with its own teal band directly under the sheet's teal band — two heads stacked,
 * for one question. The app's language for "ask me something without leaving the page" on a
 * phone is a bottom sheet (CLAUDE.md → Sheets), and the date picker is not an exception to it.
 *
 * The two surfaces answer the same question differently on purpose:
 *
 * | | Laptop (popover) | Phone (sheet) |
 * |---|---|---|
 * | Commit | tapping a day commits and closes | a day is a **draft**; the foot commits |
 * | Chrome | its own compact teal strip | the app's `SheetHead` |
 * | Cells | 2rem, a mouse target | ~2.5rem and full width, a finger target |
 * | Clear / Today | small links in a footer strip | worded links under the grid, also drafts |
 *
 * The switch is `useIsMobile()` — 768, the same breakpoint every form sheet uses, so a picker
 * opened from New goal or New target is the same kind of thing as the sheet that launched it.
 * The filter panel deliberately switches at 640 instead (see the note in `ListToolbar.tsx`), so
 * between 640 and 767 a bottom date sheet can open from a right-hand filter panel. That band is
 * the seam that file already documents, and a bottom sheet is the right shape on both sides of
 * it; matching the container instead would mean the picker changed shape depending on who opened
 * it, which is worse.
 *
 * Confirming rather than committing on the phone is the point of the redesign, not an oversight:
 * a sheet in this app always ends in a pinned pair — a quiet outline left, filled Kale right —
 * and on a 48px date grid a mis-tap that instantly commits *and closes* leaves nothing to undo.
 *
 * Visual (trigger): small inline pill — calendar icon, formatted date, and a compact
 * "Xd left / overdue" annotation. When no date is set, shows "Set deadline" placeholder.
 */
export function DeadlinePopover({
  iso,
  achievedAt,
  completed = false,
  onChange,
  size = "sm",
  align = "start",
  variant = "pill",
  placeholder,
  hideDaysLeft = false,
  className,
  disableScroll = false,
  side = "bottom",
  hideChevron = false,
  renderTrigger,
}: {
  iso?: string;
  achievedAt?: string;
  completed?: boolean;
  onChange: (next: string | undefined) => void;
  size?: "sm" | "md";
  align?: "start" | "center" | "end";
  variant?: "pill" | "input" | "button" | "text" | "icon" | "icon-text";
  placeholder?: React.ReactNode;
  hideDaysLeft?: boolean;
  className?: string;
  disableScroll?: boolean;
  side?: "top" | "bottom";
  hideChevron?: boolean;
  renderTrigger?: () => React.ReactNode;
}) {
  const isMobile = useIsMobile();
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLDivElement>(null);
  const displayIso = completed && achievedAt ? achievedAt : iso;
  const date = displayIso ? new Date(displayIso) : undefined;
  const [month, setMonth] = useState<Date>(date || new Date());
  /** The phone sheet's draft: what the foot will commit. Seeded from `date` on every open. */
  const [picked, setPicked] = useState<Date | undefined>(date);

  const days = date ? differenceInCalendarDays(date, new Date()) : 0;
  const overdue = !!date && !completed && isPast(date) && days < 0;
  const relativeText = completed
    ? "achieved"
    : overdue
      ? `${Math.abs(days)}d overdue`
      : days === 0
        ? "today"
        : `${days}d left`;

  let toneClass =
    "text-muted-foreground border-border bg-surface hover:border-primary/40 hover:bg-primary-soft/50";
  if (date) {
    if (overdue) {
      toneClass =
        "text-destructive border-destructive/30 bg-destructive/10 hover:bg-destructive/20 hover:border-destructive/50";
    } else if (days <= 7) {
      toneClass =
        "text-amber-700 border-amber-500/30 bg-amber-500/10 hover:bg-amber-500/20 hover:border-amber-500/50";
    } else {
      toneClass =
        "text-foreground/80 border-border bg-surface hover:border-primary/40 hover:bg-primary-soft/50";
    }
  }

  /**
   * The trigger's look, as data rather than as markup.
   *
   * Seven variants of the same control, and each has to render twice — once as a
   * `PopoverTrigger` on a laptop, once as a plain button that opens the sheet on a phone.
   * Describing the shape once and choosing the element afterwards is what keeps the two
   * surfaces from drifting apart, which is the failure this whole file is a fix for.
   */
  const trigger: { className: string; content: React.ReactNode } = (() => {
    if (variant === "input") {
      return {
        className: cn(
          "w-full flex items-center h-11 bg-surface border border-input focus:outline-none focus-visible:border-primary focus-visible:ring-[3px] focus-visible:ring-ring rounded-md px-3.5 text-base text-left transition-colors",
          !date && "text-muted-foreground",
        ),
        content: (
          <>
            <CalendarIcon className="h-4 w-4 mr-2 opacity-60" />
            {date ? format(date, "PPP") : placeholder || "Pick a deadline"}
          </>
        ),
      };
    }
    if (variant === "button") {
      return {
        className: cn(
          "w-full inline-flex items-center justify-center gap-2 h-10 rounded-md text-sm font-medium",
          // Plain white field with a neutral border and text — a date-range filter reads as an
          // input, not as a coloured teal fill (owner, 2026-08-15).
          "bg-white text-foreground border hairline hover:bg-secondary transition-colors",
          className,
        ),
        content: (
          <>
            <CalendarIcon className="h-3.5 w-3.5 opacity-60" />
            {date ? format(date, "MMM d, yyyy") : placeholder || "Set deadline"}
          </>
        ),
      };
    }
    if (variant === "text") {
      return {
        className: cn(
          "text-sm hover:text-primary transition-colors text-left",
          !date && "text-muted-foreground",
          overdue && "!text-destructive hover:!text-destructive/80",
          className,
        ),
        content: date ? (
          <div className="flex items-center gap-1.5">
            <span>{format(date, "MMM d, yyyy")}</span>
            {!hideDaysLeft && (
              <span className="opacity-60 font-normal">· {relativeText}</span>
            )}
            {!hideChevron && <ChevronRight className="h-3.5 w-3.5 shrink-0" />}
          </div>
        ) : (
          <span className="inline-flex items-center gap-1 whitespace-nowrap">
            {placeholder || "Set deadline"}
          </span>
        ),
      };
    }
    if (renderTrigger) {
      return {
        className: cn("text-left", className),
        content: renderTrigger(),
      };
    }
    if (variant === "icon") {
      return {
        className: cn(
          "h-7 w-7 grid place-items-center rounded-md transition-colors",
          date
            ? overdue
              ? "text-destructive hover:text-destructive/80"
              : "text-primary"
            : "text-muted-foreground hover:text-primary hover:bg-secondary",
          className,
        ),
        content: <CalendarIcon className="h-3.5 w-3.5" />,
      };
    }
    if (variant === "icon-text") {
      return {
        className: cn(
          "inline-flex items-center gap-1 text-xs transition-colors rounded",
          date
            ? overdue
              ? "text-destructive hover:text-destructive/80"
              : days <= 7
                ? "text-amber-700 hover:text-amber-800"
                : "text-foreground/70 hover:text-foreground"
            : "text-muted-foreground hover:text-primary",
          className,
        ),
        content: (
          <>
            <CalendarIcon className="h-3 w-3 shrink-0" />
            {date ? (
              <>
                <span>{format(date, "MMM d, yyyy")}</span>
                {!hideDaysLeft && (
                  <span className="opacity-60">· {relativeText}</span>
                )}
              </>
            ) : (
              <span>{placeholder || "Set deadline"}</span>
            )}
          </>
        ),
      };
    }
    return {
      className: cn(
        "inline-flex items-center gap-1.5 rounded-md border transition-colors num font-medium",
        size === "md" ? "h-9 px-3 text-sm" : "h-7 px-2 text-xs",
        toneClass,
        className,
      ),
      content: (
        <>
          <CalendarIcon
            className={cn(size === "md" ? "h-4 w-4" : "h-3 w-3", "opacity-70")}
          />
          {date ? (
            <>
              <span>{format(date, "MMM d, yyyy")}</span>
              {!hideDaysLeft && (
                <span className="opacity-60 font-normal">· {relativeText}</span>
              )}
            </>
          ) : (
            <span>{placeholder || "Set deadline"}</span>
          )}
        </>
      ),
    };
  })();

  /** The trigger's title, on the icon variant only — it has no words of its own. */
  const iconTitle =
    variant === "icon"
      ? date
        ? overdue
          ? `${format(date, "MMM d, yyyy")} · ${Math.abs(days)}d overdue`
          : format(date, "MMM d, yyyy")
        : "Set deadline"
      : undefined;

  const onOpen = () => {
    setMonth(date || new Date());
    setPicked(date);
  };

  // The `input` variant is a field with a Clear affordance sitting inside it, so its trigger is
  // wrapped rather than standing alone. Both surfaces use the same wrapper.
  const wrap = (el: React.ReactNode) =>
    variant === "input" ? (
      <div
        ref={inputRef}
        className={cn("relative w-full scroll-mt-6", className)}
      >
        {el}
        {date && (
          <button
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onChange(undefined);
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 h-7 w-7 grid place-items-center rounded text-muted-foreground hover:bg-secondary transition-colors"
            aria-label="Clear deadline"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
    ) : (
      el
    );

  if (isMobile) {
    const commit = () => {
      onChange(picked ? picked.toISOString() : undefined);
      setOpen(false);
    };
    // Clearing is a draft like everything else in the sheet, so the confirm word follows the
    // draft: "Remove deadline" only when there is one to remove and the draft has dropped it.
    // With nothing set and nothing picked there is nothing to confirm, and the button says the
    // thing it would do rather than the thing it cannot.
    const clearing = !picked && !!date;
    const confirmLabel = clearing ? "Remove deadline" : "Set deadline";
    const canConfirm = !!picked || clearing;

    return (
      <>
        {wrap(
          <button
            type="button"
            title={iconTitle}
            onClick={() => {
              onOpen();
              setOpen(true);
            }}
            className={trigger.className}
          >
            {trigger.content}
          </button>,
        )}
        <Drawer open={open} onOpenChange={setOpen}>
          <DrawerContent className="sheet-max-92 mt-0 px-0 flex flex-col">
            {/* `SheetHead` is the whole head, exactly as in New goal / New target / the filter
                panel — no second, screen-reader-only title beside it. One heading per sheet. */}
            <SheetHead title="Set deadline" onClose={() => setOpen(false)} />

            <div className="px-5 pt-4 pb-6 overflow-y-auto flex-1 min-h-0">
              <p className="mb-3 text-sm">
                {picked ? (
                  <>
                    <span className="font-semibold text-foreground">
                      {format(picked, "EEEE, MMMM d, yyyy")}
                    </span>
                    <span className="text-muted-foreground">
                      {" · "}
                      {describeDistance(picked)}
                    </span>
                  </>
                ) : (
                  <span className="text-muted-foreground">No deadline</span>
                )}
              </p>

              <MonthGrid
                selected={picked}
                month={month}
                setMonth={setMonth}
                onSelect={setPicked}
                // Full width with finger-sized cells. The popover's 2rem cell is a mouse
                // target; overriding `root` is what drops the shared component's `w-fit`.
                className="w-full p-0 [--cell-size:2.5rem]"
                classNames={{ root: "w-full" }}
              />

              <div className="mt-4 flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => {
                    const today = new Date();
                    setMonth(today);
                    setPicked(today);
                  }}
                  className="h-8 inline-flex items-center text-sm font-semibold text-primary transition-colors"
                >
                  Today
                </button>
                {picked && (
                  <button
                    type="button"
                    onClick={() => setPicked(undefined)}
                    className="h-8 inline-flex items-center gap-1.5 text-sm font-semibold text-destructive/80 hover:text-destructive transition-colors"
                  >
                    <Trash2 className="h-3.5 w-3.5" /> Clear
                  </button>
                )}
              </div>
            </div>

            {/* The app's foot: quiet outline left, filled Kale right, one 20px gutter. */}
            <div
              className="shrink-0 bg-surface px-5 pt-3 flex gap-3"
              style={{
                paddingBottom: "max(env(safe-area-inset-bottom), 12px)",
              }}
            >
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={commit}
                disabled={!canConfirm}
                className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 disabled:opacity-40 transition-colors"
              >
                {confirmLabel}
              </button>
            </div>
          </DrawerContent>
        </Drawer>
      </>
    );
  }

  return (
    <Popover
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (o) {
          onOpen();
          if (variant === "input" && inputRef.current && !disableScroll) {
            setTimeout(() => {
              inputRef.current?.scrollIntoView({
                behavior: "smooth",
                block: "start",
              });
            }, 50);
          }
        }
      }}
    >
      {wrap(
        <PopoverTrigger title={iconTitle} className={trigger.className}>
          {trigger.content}
        </PopoverTrigger>,
      )}
      <PopoverContent
        align={align}
        side={side}
        avoidCollisions={true}
        // A column, so that when the calendar is capped to the room it has (see
        // `PopoverContent`) it is the GRID that scrolls: the "Set deadline" head with its X and
        // the Today / Clear row stay put. Scrolling the whole card instead would put the close
        // button out of reach, which is the state this was found in.
        className="w-auto p-0 bg-surface border hairline shadow-lg overflow-hidden flex flex-col"
        onCloseAutoFocus={(e) => {
          if (variant === "input" && !disableScroll) {
            e.preventDefault();
            const container =
              document.getElementById("new-goal-scroll-container") ||
              inputRef.current?.closest(".overflow-y-auto");
            if (container) {
              setTimeout(() => {
                container.scrollTo({ top: 0, behavior: "smooth" });
              }, 10);
            }
          }
        }}
      >
        <div className="shrink-0 flex items-center justify-between px-3 py-2 bg-primary text-primary-foreground">
          <span className="text-sm font-semibold">
            {date ? format(date, "MMMM d, yyyy") : "Set deadline"}
          </span>
          <button
            onClick={() => setOpen(false)}
            className="h-6 w-6 grid place-items-center rounded hover:bg-black/10 transition-colors"
            aria-label="Close"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">
          <MonthGrid
            selected={date}
            month={month}
            setMonth={setMonth}
            onSelect={(d) => {
              onChange(d ? d.toISOString() : undefined);
              setOpen(false);
            }}
            initialFocus
          />
        </div>
        <div className="shrink-0 flex items-center justify-between px-3 py-2 border-t hairline">
          <button
            onClick={() => {
              const today = new Date();
              onChange(today.toISOString());
              setOpen(false);
            }}
            className="h-6 inline-flex items-center text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors"
          >
            Today
          </button>
          {date ? (
            <button
              onClick={() => {
                onChange(undefined);
                setOpen(false);
              }}
              className="h-6 inline-flex items-center gap-1.5 text-xs font-semibold text-destructive/80 hover:text-destructive transition-colors"
            >
              <Trash2 className="h-3 w-3" /> Clear
            </button>
          ) : (
            <div />
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}

/** "3d left" / "2d overdue" / "today", for a date the user is still choosing. */
function describeDistance(d: Date): string {
  const n = differenceInCalendarDays(d, new Date());
  if (n === 0) return "today";
  return n < 0 ? `${Math.abs(n)}d overdue` : `${n}d left`;
}

/**
 * The month grid both surfaces draw — ISO week numbers, Monday first, and a month name paired
 * with a year dropdown in place of the default caption. One component, so the phone and the
 * laptop cannot disagree about what a date looks like.
 */
function MonthGrid({
  selected,
  month,
  setMonth,
  onSelect,
  className,
  classNames,
  initialFocus,
}: {
  selected: Date | undefined;
  month: Date;
  setMonth: (d: Date) => void;
  onSelect: (d: Date | undefined) => void;
  className?: string;
  classNames?: React.ComponentProps<typeof Calendar>["classNames"];
  initialFocus?: boolean;
}) {
  return (
    <Calendar
      mode="single"
      selected={selected}
      month={month}
      onMonthChange={setMonth}
      onSelect={onSelect}
      showWeekNumber
      weekStartsOn={1}
      ISOWeek
      initialFocus={initialFocus}
      fixedWeeks
      className={className}
      classNames={classNames}
      components={{
        CaptionLabel: () => {
          const currentYear = month.getFullYear();
          const years = Array.from(
            { length: 20 },
            (_, i) => new Date().getFullYear() - 2 + i,
          );
          return (
            <div className="flex items-center gap-1.5 ml-1">
              <span className="text-[15px] font-semibold tracking-tight text-foreground/90">
                {format(month, "MMMM")}
              </span>
              <Select
                value={currentYear.toString()}
                onValueChange={(y) =>
                  setMonth(new Date(parseInt(y), month.getMonth(), 1))
                }
              >
                <SelectTrigger className="h-6 w-fit px-2 py-0 border border-transparent shadow-none bg-transparent hover:bg-secondary focus:ring-0 text-sm font-semibold text-muted-foreground hover:text-foreground transition-colors [&>svg]:ml-2">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="max-h-56 min-w-[5rem]">
                  {years.map((y) => (
                    <SelectItem
                      key={y}
                      value={y.toString()}
                      className="text-sm font-medium"
                    >
                      {y}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        },
      }}
    />
  );
}
