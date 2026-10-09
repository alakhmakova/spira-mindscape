import { Link } from "@tanstack/react-router";
import { X, Calendar, AlertTriangle } from "@/components/spira/icons";
import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import type { Goal } from "@/lib/spira/types";
import {
  formatPercent,
  goalProgress,
  goalProgressSteps,
  targetProgress,
} from "@/lib/spira/progress";
import { ProgressRing } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { useSpira } from "@/lib/spira/store";
import { ConfirmDialog, QuotedName } from "./ConfirmDialog";
import { cn } from "@/lib/utils";

/**
 * **error-900** — the palette's semantic red for an overdue state (CLAUDE.md extended ramps). It
 * was error-800 `#D74041`, which is 4.43:1 on the card's `#FAFAFA` body and fails 1.4.3 for the
 * "Due date" line it colours; error-900 is 5.1:1. The stripe takes the same red.
 */
const OVERDUE_RED = "#C53336";

/**
 * The two greys of the card's left half, sampled from the owner's reference (`gusto/cards1.png`,
 * 2026-10-09): **the body is `#FAFAFA` and the band under it `#F2EFF0`** — both grey, and
 * deliberately different shades, while the progress column beside them stays pure white. The
 * palette carries the pair as neutral-100 and Salt-300.
 *
 * This is the one place a Spira surface is not white (CLAUDE.md → Colour: "the page is `#FFFFFF`
 * … and so are the cards"). The owner asked for her picture; the rule is noted, not ignored.
 */
const BODY = "#FAFAFA";
const WELL = "#F4F4F3";

/**
 * **neutral-400** — the cell divider, which is a WEDGE and not a line (owner, 2026-10-09).
 * Measured in her picture: a right triangle about 10x8, its vertical edge on the cell's boundary
 * and its base on the band's bottom edge, in a grey (`#D1CECF`) darker than either of the two the
 * band and the body are drawn in.
 */
const WEDGE = "#D6D6D6";

/**
 * The card's own frame, measured off the owner's reference task card (`gusto/upcomingsynccalendar.png`,
 * 2026-10-09): a **1px outline in a mid grey** — neutral-700, where the old `border/60` hairline
 * all but vanished on white — **4px corners**, and a soft shadow that falls mostly downwards. The
 * shadow is the notice card's own pair (CLAUDE.md 3d), not a new one.
 */
const OUTLINE = "#ABABAB";
const SHADOW = "0 4px 12px rgba(28,28,28,.08), 0 2px 8px rgba(28,28,28,.04)";
const SHADOW_HOVER =
  "0 8px 20px rgba(28,28,28,.10), 0 2px 8px rgba(28,28,28,.06)";

/**
 * The status stripe down the card's left edge, on EVERY card (owner, 2026-10-09) — the reference
 * task card's stripe, measured: **5px wide, 3px in from the outline, 4px short of it top and
 * bottom, fully rounded ends**. Grey at 0%, Kale once the goal has begun or is done on time, red
 * when it is overdue — overdue wins, so a late goal that has not been started is red, not grey.
 */
const STRIPE_IDLE = "#BABABA";
const STRIPE_MOVING = "#0A8080";

function stripeColour(progress: number, isOverdue: boolean) {
  if (isOverdue) return OVERDUE_RED;
  return progress > 0 ? STRIPE_MOVING : STRIPE_IDLE;
}

/** How much of a goal's description the card shows before "View full goal description". */
const DESCRIPTION_LINES = 2;

function formatDeadlineInfo(iso: string | undefined, completed = false) {
  if (!iso) return null;

  const deadline = new Date(iso);
  const now = new Date();
  const deadlineDay = new Date(
    deadline.getFullYear(),
    deadline.getMonth(),
    deadline.getDate(),
  );
  const todayDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffDays = Math.round(
    (deadlineDay.getTime() - todayDay.getTime()) / 86_400_000,
  );
  const isOverdue = !completed && diffDays < 0;

  const dateStr = deadline.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const countdown = completed
    ? "achieved"
    : diffDays === 0
      ? "due today"
      : diffDays === 1
        ? "1 day left"
        : diffDays > 1
          ? `${diffDays} days left`
          : diffDays === -1
            ? "1 day overdue"
            : `${Math.abs(diffDays)} days overdue`;

  return { dateStr, countdown, isOverdue };
}

function formatCreated(iso: string | undefined) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * One of the three cells along the foot of the card's left half.
 *
 * Measured off the owner's reference (2026-10-09, `gusto/cards1.png`): the label is ~13px of grey
 * and the **value under it is BIGGER, ~15px**, in a darker grey than the label but lighter than
 * the title. The band is 48px tall and carries **no rule of its own along the top** — the tint is
 * what separates it from the body.
 *
 * **The cells are told apart by a wedge, not by a line** (owner, 2026-10-09). Every cell carries
 * one in its bottom-right corner, the last one included, exactly as her picture does.
 */
function StatCell({ label, children }: { label: string; children: ReactNode }) {
  return (
    // **Left on a phone, centred on a laptop** (owner, 2026-10-09). Stacked in a column of their
    // own the three cells are a list, and a list reads down its left edge; across the foot of a
    // wide card they are the reference's three centred columns. The phone's gutter is the body's
    // own 20px, so the labels line up with the goal's title above them.
    <div className="relative flex min-w-0 flex-col justify-center gap-[3px] px-5 py-2.5 text-left sm:items-center sm:px-2 sm:py-0 sm:text-center">
      <span className="text-[13px] leading-none text-muted-foreground">
        {label}
      </span>
      <span className="truncate text-[15px] font-medium leading-tight text-foreground/85">
        {children}
      </span>
      {/* Drawn rather than a border: a triangle has no border form, and `clip-path` keeps it out
          of the layout so the cell's own text is unaffected by it. **Bigger on a phone** (owner,
          2026-10-09) — at 10x8 it was a speck against a 56px row. */}
      <span
        aria-hidden="true"
        className="absolute bottom-0 right-0 h-3.5 w-4 sm:h-2 sm:w-[10px]"
        style={{
          backgroundColor: WEDGE,
          clipPath: "polygon(100% 0, 100% 100%, 0 100%)",
        }}
      />
    </div>
  );
}

/**
 * Whether a clamped block of text is actually taller than its clamp — so the control that expands
 * it only appears when there is something to expand. The same measurement `Inline.tsx` makes for
 * its own "Show more": `scrollHeight` reports the full height even while the element is clamped.
 */
function useOverflowing(
  ref: React.RefObject<HTMLElement | null>,
  lines: number,
  enabled: boolean,
) {
  const [overflowing, setOverflowing] = useState(false);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || !enabled) {
      setOverflowing(false);
      return;
    }
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight)) return;
      // `scrollHeight` reports the full height even while the element is clamped — measured at
      // three widths with the clamp on and lifted, 68 either way.
      setOverflowing(el.scrollHeight > lineHeight * lines + 2);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [ref, lines, enabled]);
  return overflowing;
}

export function GoalCard({ goal }: { goal: Goal }) {
  const progress = goalProgress(goal);
  const completed = progress >= 1;
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const updateGoal = useSpira((s) => s.updateGoal);
  const [confirm, setConfirm] = useState(false);

  const [expanded, setExpanded] = useState(false);
  const descriptionRef = useRef<HTMLParagraphElement>(null);
  const descriptionClamped = useOverflowing(
    descriptionRef,
    DESCRIPTION_LINES,
    Boolean(goal.description),
  );

  const displayDate =
    completed && goal.achievedAt ? goal.achievedAt : goal.deadline;
  const deadlineInfo = formatDeadlineInfo(displayDate, completed);
  const isOverdue = deadlineInfo?.isOverdue ?? false;
  const targetCount = goal.targets.length;
  // "2/10" — how many are finished out of all of them (owner, 2026-10-09). `targetProgress` is
  // the same reading the target card and the goal's own percentage use, so the cell cannot
  // disagree with the ring beside it.
  const targetsDone = goal.targets.filter((t) => targetProgress(t) >= 1).length;
  const percentLabel = formatPercent(progress, goalProgressSteps(goal));

  return (
    <div
      className={cn(
        /**
         * **A grid, not two columns of flex** (owner, 2026-10-09). On a phone the three cells
         * stand one under another with the ring BESIDE them, so the band and the ring share a
         * row — which they cannot do while the band lives inside a left-hand column. Three
         * siblings on a grid give both arrangements from one markup:
         *
         *   phone              laptop
         *   ┌───────────┐      ┌───────────┬──────┐
         *   │   body    │      │   body    │      │
         *   ├──────┬────┤      ├───────────┤ ring │
         *   │ band │ring│      │   band    │      │
         *   └──────┴────┘      └───────────┴──────┘
         */
        // **`sm:grid-rows-[1fr_auto]`** — the ring spans both rows on a laptop and is usually
        // taller than the body and the band together. With auto rows the surplus went into the
        // band's row while the band itself stayed 48px, which left a white strip under every
        // card's band (owner, 2026-10-09). A flexible first row takes all of it instead.
        "group relative grid cursor-pointer grid-cols-[minmax(0,1fr)_auto] overflow-hidden rounded-[4px] border bg-card text-card-foreground shadow-[var(--card-shadow)] transition-shadow hover:shadow-[var(--card-shadow-hover)] sm:grid-cols-[minmax(0,1fr)_228px] sm:grid-rows-[1fr_auto]",
      )}
      style={
        {
          borderColor: OUTLINE,
          "--card-shadow": SHADOW,
          "--card-shadow-hover": SHADOW_HOVER,
        } as React.CSSProperties
      }
    >
      {/* Over the body's grey and the band's, so `z-[2]` — above the stretched link's overlay
          (`z-[1]`), which would otherwise paint the band over its lower end. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute bottom-1 left-[3px] top-1 z-[2] w-[5px] rounded-full"
        style={{ backgroundColor: stripeColour(progress, isOverdue) }}
      />

      {/* BODY — what the goal is. Full width on a phone, the top-left cell on a laptop. */}
      <div
        className="col-span-2 col-start-1 row-start-1 min-w-0 sm:col-span-1"
        style={{ backgroundColor: BODY }}
      >
        <div className="flex h-full flex-col gap-2 p-5 pr-12 sm:p-6 sm:pr-6">
          {/* Where the reference card carries a logo: the deadline, or the offer to set one.
              Both triggers carry `py-1`: the line of text is 20px on its own, and a control
              owes 24x24 (WCAG 2.2 AA, 2.5.8). `e2e/a11y-target-size.spec.ts` measures it. */}
          <DeadlinePopover
            iso={goal.deadline}
            achievedAt={goal.achievedAt}
            completed={completed}
            onChange={(next) => updateGoal(goal.id, { deadline: next })}
            renderTrigger={() =>
              deadlineInfo ? (
                <span className="relative z-10 flex min-w-0 items-center gap-2.5 py-1 text-[13px] font-medium transition-opacity hover:opacity-70">
                  <span
                    className="flex items-center gap-1.5"
                    style={{
                      color: isOverdue
                        ? OVERDUE_RED
                        : "var(--muted-foreground)",
                    }}
                  >
                    {isOverdue ? (
                      <AlertTriangle className="h-3.5 w-3.5 translate-y-[1px]" />
                    ) : (
                      <Calendar className="h-3.5 w-3.5 translate-y-[1px] opacity-70" />
                    )}
                    {completed
                      ? deadlineInfo.dateStr
                      : `Due date ${deadlineInfo.dateStr}`}
                  </span>
                  <span className="h-3.5 w-px shrink-0 bg-border" />
                  <span className="truncate font-semibold text-foreground">
                    {completed ? "Achieved" : deadlineInfo.countdown}
                  </span>
                </span>
              ) : (
                <span className="relative z-10 inline-flex items-center py-1 text-[13px] font-medium text-muted-foreground transition-opacity hover:opacity-70">
                  Set deadline
                </span>
              )
            }
          />

          {/* 18px and bolder than the body, with the body's own ink — the reference's own
              hierarchy (title cap height 13px at near-black #2B2B2D, description 15px grey). */}
          <h3 className="line-clamp-2 text-lg font-semibold leading-snug text-foreground">
            <Link
              to="/goals/$goalId"
              params={{ goalId: goal.id }}
              // **`after:z-[1]`, not a bare overlay** — the stat cells and the progress ring are
              // positioned boxes LATER in the DOM, so at `z-auto` they paint over an overlay that
              // has none and swallow the click, leaving the bottom half of a `cursor-pointer` card
              // doing nothing. 1 is still under the `z-10` controls that must stay clickable.
              className="after:absolute after:inset-0 after:z-[1]"
            >
              {goal.title}
            </Link>
          </h3>

          {goal.description ? (
            <p
              ref={descriptionRef}
              className={cn(
                "text-sm leading-relaxed text-muted-foreground",
                expanded ? undefined : "line-clamp-2",
              )}
            >
              {goal.description}
            </p>
          ) : null}

          {/**
           * Where the reference card says "View full plan details" (owner, 2026-10-09). It opens
           * the goal's own words in place rather than navigating — the card itself is the link to
           * the goal, so a second way in would be two controls for one thing.
           *
           * It appears only when the text is actually longer than its two lines, measured rather
           * than assumed; a word that promises more and shows nothing is worse than no word.
           */}
          {goal.description && (descriptionClamped || expanded) ? (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="relative z-10 w-fit py-1.5 text-sm font-semibold leading-[15px] text-primary underline decoration-1 underline-offset-[3px] transition-opacity hover:opacity-70"
            >
              {expanded
                ? "Hide full goal description"
                : "View full goal description"}
            </button>
          ) : null}
        </div>
      </div>

      {/**
       * BAND — the three cells. **Stacked one under another on a phone** and side by side on a
       * laptop (owner, 2026-10-09); either way the same cell, the same two greys and the same
       * corner wedge. 48px per row is the height measured in the reference.
       */}
      <div
        className="col-start-1 row-start-2 grid grid-cols-1 sm:h-12 sm:grid-cols-3"
        style={{ backgroundColor: WELL }}
      >
        <StatCell label="Created">{formatCreated(goal.createdAt)}</StatCell>
        <StatCell label="Confidence">
          {/* No colour dot (owner, 2026-10-09): the reference's cells are words and figures,
              and the confidence's own colour lives on the goal page. */}
          <span className="num">{goal.confidence}/10</span>
        </StatCell>
        <StatCell label="Targets">
          <span className="num">
            {targetsDone}/{targetCount}
          </span>
        </StatCell>
      </div>

      {/**
       * RING — white, against the two greys beside it, exactly as the reference is. It sits to
       * the RIGHT of the three cells on a phone and spans the whole card's height on a laptop,
       * so the word stays above it on both.
       */}
      <div
        className={cn(
          "col-start-2 row-start-2 flex flex-col items-center justify-center gap-2.5 border-l border-t border-border/60 bg-white px-6 py-5 sm:row-span-2 sm:row-start-1 sm:border-t-0 sm:py-6",
        )}
      >
        <span className="text-sm text-muted-foreground">Progress</span>
        {/**
         * Measured off the owner's reference ring (`gusto/home.png`, 2026-10-09): **99px across
         * with a 13px stroke** — 13% of the diameter, where ours was 10% and read as a hairline —
         * round caps, starting at twelve o'clock, and **"71%" set at 18px** in near-black, filling
         * about a fifth of the diameter. The arc is a dark teal on a near-white track; `deep` is
         * that pair, and `ProgressBar.tsx` says what it cost to add it.
         */}
        <ProgressRing value={progress} tone="deep">
          {/* The inner disc is 74px across and `formatPercent` can return ">99.99" — six
              characters that do not fit at 18px. The step down is by length, not by value, so a
              one-decimal percentage keeps the big number. */}
          <span
            className={cn(
              "num font-semibold text-foreground",
              percentLabel.length > 4
                ? "text-xs"
                : percentLabel.length > 2
                  ? "text-sm"
                  : "text-[18px]",
            )}
          >
            {percentLabel}%
          </span>
        </ProgressRing>
      </div>

      {/**
       * **Last in the card, not first** (owner, 2026-09-30, found with a keyboard).
       *
       * It used to sit in the header row, which put it ahead of the goal's own name in the DOM —
       * so tabbing through the dashboard announced "Delete goal, button" before saying WHICH goal,
       * and reaching the sixth goal meant passing through six delete buttons. That is WCAG 2.4.3
       * (Focus Order): the sequence has to keep its meaning, and a destructive action that names
       * itself before its object does not.
       *
       * Now the order is: the deadline, the goal, Start, and only then delete — the same shape as
       * the resource head, where the X closes the group rather than opening it. It is positioned
       * into the card's top-right corner, which is the head of the progress column.
       */}
      <button
        onClick={() => setConfirm(true)}
        className="absolute right-4 top-4 z-10 flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground/40 transition-colors hover:bg-secondary/50 hover:text-muted-foreground"
        // The name carries the goal, so it is unambiguous wherever focus lands and whatever is
        // read out before it.
        aria-label={`Delete "${goal.title || "Untitled goal"}"`}
        title="Delete goal"
      >
        <X className="h-4 w-4" />
      </button>

      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="Delete this goal?"
        description={
          <>
            Are you sure you want to permanently delete{" "}
            <QuotedName>{goal.title}</QuotedName>? All targets, options, and
            resources inside it will be removed. You can&apos;t undo this.
          </>
        }
        confirmLabel="Yes, delete"
        cancelLabel="No, go back"
        onConfirm={() => deleteGoal(goal.id)}
      />
    </div>
  );
}
