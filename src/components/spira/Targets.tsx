import { useState, useRef, useEffect, useMemo } from "react";
import {
  AlertTriangle,
  Calendar,
  CalendarPlus,
  Check,
  CirclePlus,
  CircleCheck,
  CircleCheckFill,
  CircleX,
  ArrowUturnCwDown,
  Minus,
  Plus,
  Search,
  SlidersHorizontal,
  SquareDashed,
  Trash2,
  TriangleAlert,
  X,
} from "@/components/spira/icons";
import { toast } from "sonner";
import type { Goal, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { FIELD_LIMITS, lengthError } from "@/lib/spira/limits";
import {
  formatPercent,
  isProgressLocked,
  progressSteps,
  targetProgress,
} from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { SheetHead } from "./SheetHead";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { ResizableSheet } from "@/components/spira/Resources";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
import {
  LockFilled,
  LockOpenFilled,
  CaretsExpandVertical,
  ChevronUp,
  ChevronDown,
} from "@/components/spira/icons";
import { CalendarPageArt, PlusMarkArt } from "./TargetTileArt";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { Section } from "@/components/spira/Section";
import {
  FilterIconTrigger,
  RoundAddButton,
  SectionSearchButton,
  SectionSearchField,
  SectionSearchInput,
  SheetChoiceCards,
  SheetDateRange,
  SheetGroup,
  SheetPills,
  SheetSegmented,
  ToolbarSheet,
} from "@/components/spira/ListToolbar";
import { InlineText } from "@/components/spira/Inline";
import {
  AttachResourceButton,
  ElementActionsMenu,
  appendResourceToken,
  namesToTokens,
  tokensToNames,
  useInlineResources,
  useIsSingleLine,
  useReadableText,
  useTallText,
} from "@/components/spira/inline-resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { FilteredEmptyNotice } from "@/components/spira/Notice";
import { stripResourceTokens } from "@/lib/spira/links";
import { resourceDisplayName } from "@/lib/spira/resources";
import { Switch } from "@/components/ui/switch";
import { celebrate } from "@/lib/spira/celebrate";
import {
  useListActive,
  useListLocked,
  useShellFilters,
  type TargetDeadlineFilter,
  type TargetLockFilter,
  type TargetSortKey,
  type TargetStatusFilter,
  type TargetTypeFilter,
} from "@/components/shell/shell-store";

/** The store owns the keys; this alias keeps the two list components reading as they did. */
type SortField = TargetSortKey;
type StatusFilter = TargetStatusFilter;

/** error-800 — the palette's semantic red for an overdue state (CLAUDE.md extended ramps).
 *  Guava is the brand accent and is deliberately not used as a danger signal. */
const OVERDUE_RED = "#D74041";

/**
 * Once a target is achieved, any link in its title (a web URL or an attached resource) drops from
 * teal to Salt-800: the work is done, so the link is a reference, not a call to action. It stays
 * underlined and clickable — only the colour is dialled back. Descendant selectors, because the
 * links are rendered deep inside `InlineText`.
 */
const ACHIEVED_LINK_TONE = (done: boolean) =>
  done ? "[&_a]:text-[#6C6C72] [&_button]:text-[#6C6C72]" : "";

/** Is this deadline in the past and not yet met? Used by the task row's deadline badge. */
function deadlineOverdue(iso: string | undefined, done: boolean): boolean {
  return !!formatDeadlineInfo(iso, done)?.isOverdue;
}

/** Shown wherever a locked target's progress is edited — always names the way out. */
const PROGRESS_LOCKED_MESSAGE =
  "This target is locked. Unlock it to change its progress.";

/** Refuse a progress edit on a locked target, and say why. */
function warnProgressLocked() {
  toast.error(PROGRESS_LOCKED_MESSAGE);
}

/**
 * The padlock on a target: pinned progress can't be nudged by a stray tap. An achieved target
 * starts locked; anything else starts open. Either way the toggle records an explicit choice, so
 * a finished target can be reopened to correct it.
 */
function ProgressLockButton({
  locked,
  onToggle,
  className,
  iconClassName,
  neutralTone = false,
}: {
  locked: boolean;
  onToggle: (next: boolean) => void;
  className?: string;
  iconClassName?: string;
  /**
   * In the desktop table the padlock is a scannable column of state, not a per-row accent: the
   * owner wants it the same grey whether locked or not, going Kale only on hover. Cards keep the
   * default (locked = Kale) so a locked card still reads as locked at a glance.
   */
  neutralTone?: boolean;
}) {
  const Icon = locked ? LockFilled : LockOpenFilled;
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onToggle(!locked);
      }}
      aria-pressed={locked}
      aria-label={locked ? "Unlock progress" : "Lock progress"}
      title={
        locked
          ? "Progress is locked — click to unlock"
          : "Lock progress so it can't be changed by accident"
      }
      className={cn(
        // No plate of its own: in the table the padlock is a bare mark in a column. The CARD
        // passes its own badge chrome (ring + white + shadow) so that corner matches the option
        // card's smiley exactly — the two hang off the same corner and must read as one component.
        "grid h-8 w-8 shrink-0 place-items-center rounded-md transition-colors",
        neutralTone
          ? "text-muted-foreground hover:text-primary"
          : locked
            ? "text-primary hover:text-primary/75"
            : "text-muted-foreground/60 hover:text-foreground",
        className,
      )}
    >
      {/* The glyph nearly fills its circle. At h-4 it was a speck in an 8-unit box, and on the
          mobile card a 14px mark inside a 28px ring read as an empty ring with a dot in it. */}
      <Icon className={cn("h-5 w-5", iconClassName)} />
    </button>
  );
}

/**
 * The achieved tile, still a bitmap — the calendar states are drawn from shared path data now
 * (see `TargetTileArt`), but the popper has no vector twin on either surface, and Android keeps
 * a PNG for it too.
 */
const TILE_ART = { done: "/images/party-popper.png" } as const;

/**
 * Where the calendar tile prints — **the paper's own middle**, used for both the date block and
 * the "no deadline yet" plus so the two states never sit at different heights.
 *
 * In the artwork's 50-unit box the page runs y 11 → 45 and the coral band takes y 11 → 18.6, so
 * the writable paper is 18.6 → 45 and its centre lands at 0.636 of the height. Android's tile
 * carries the identical figure (`TargetCard.kt` → `PAPER_CENTRE`); the two must not drift, because
 * the same card is read on both surfaces.
 */
const PAPER_CENTRE = "63.6%";

/**
 * The deadline as a compact calendar tile — month above, the day in big digits — so a card reads
 * its date at a glance instead of parsing a line of prose. Same footprint in every state (a
 * popper once achieved, a calendar with a plus when no date is set), so the row never jumps.
 *
 * The date is printed ON the illustrated page: the artwork leaves its paper blank for exactly
 * that, which is why the text sits in an absolutely-positioned block rather than in the flow.
 */
function DeadlineTile({
  info,
  done,
}: {
  info: ReturnType<typeof formatDeadlineInfo>;
  done: boolean;
}) {
  const overdue = !!info?.isOverdue && !done;

  // An achieved target keeps the popper — it isn't a date any more, it's a result.
  if (done) {
    return (
      <span className="relative block h-16 w-16 shrink-0 cursor-pointer text-center">
        <img
          src={TILE_ART.done}
          alt=""
          aria-hidden="true"
          className="h-16 w-16 select-none"
          draggable={false}
        />
      </span>
    );
  }

  return (
    <span className="relative block h-16 w-16 shrink-0 cursor-pointer text-center">
      {/* One calendar for every date state, drawn from the same paths as the Android tile. Its
          Guava band never changes: overdue is said by the badge below, and recolouring the band
          as well made the whole tile shout. */}
      <CalendarPageArt className="h-16 w-16" />
      {!info && (
        // No date yet: the page shows the hand-drawn plus, so the tile still invites a tap. Same
        // anchor as the date below — the two states must not sit at different heights.
        <span
          className="absolute inset-x-0 flex -translate-y-1/2 justify-center"
          style={{ top: PAPER_CENTRE }}
        >
          <PlusMarkArt className="h-5 w-5" />
        </span>
      )}
      {overdue && (
        // Bottom-right, clear of the paper so the date keeps its place, on a white ring that
        // separates it from the artwork.
        <span
          className="absolute -bottom-0.5 -right-1 flex h-[18px] w-[18px] items-center
                     justify-center rounded-full bg-white"
          title="Overdue"
        >
          <svg
            viewBox="0 0 16 16"
            className="h-[15px] w-[15px]"
            aria-hidden="true"
          >
            <path
              d="M.5 8a7.5 7.5 0 1 1 15 0 7.5 7.5 0 0 1-15 0Zm7.5-4a.75.75 0 0 1 .75.75v3.5a.75.75 0
                 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 7.5a.9.9 0 1 0 0-1.8.9.9 0 0 0 0 1.8Z"
              fill="#D74041"
              fillRule="evenodd"
              clipRule="evenodd"
            />
          </svg>
        </span>
      )}
      {info && (
        // What is printed sits on the PAPER, not on the tile — see PAPER_CENTRE. The date stays
        // black when overdue: the red badge is what says so, and red digits on the page only
        // muddy it.
        <span
          className="absolute inset-x-0 flex -translate-y-1/2 flex-col items-center
                     text-foreground"
          style={{ top: PAPER_CENTRE }}
        >
          {/* **Both sizes carry their own leading, and both match Android's** (owner, 2026-08-20).
              The day used to be `text-lg` under a `leading-none` parent — and `text-lg` brings
              Tailwind's own 1.75rem line-height with it, which wins over the inherited one. The
              block was therefore 37px tall rather than the ~26px this anchor is calculated for, so
              its ink sat 0.8px ABOVE the paper: "DEC" printed on the coral band, with a wide empty
              strip left under the day. Measured against the Android tile
              (`app/build/reports/visual/target-cards.png`), 8/9 and 16/17 put the date 4.4px below
              the band and 8.9px above the foot — Android's own 4 and 10. */}
          <span className="text-[8px] leading-[9px] font-semibold uppercase tracking-wide">
            {info.monthLabel}
          </span>
          <span className="num text-[16px] leading-[17px] font-bold tabular-nums">
            {info.dayLabel}
          </span>
        </span>
      )}
    </span>
  );
}

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
  // Split parts for the calendar tile (month above, day in big digits).
  const monthLabel = deadline.toLocaleDateString("en-US", { month: "short" });
  const dayLabel = String(deadline.getDate());
  return { dateStr, countdown, isOverdue, monthLabel, dayLabel };
}

/**
 * Is this target past its deadline and still unfinished?
 *
 * The same rule the card's own badge uses ({@link formatDeadlineInfo}), not merely "the date has
 * passed": a target that was achieved late is finished, and listing it as overdue would be telling
 * the user to act on something that is done.
 */
function isTargetOverdue(t: Target): boolean {
  const done = targetProgress(t) >= 1;
  return !!formatDeadlineInfo(t.deadline, done)?.isOverdue;
}

/** The three filter questions and their words, so the menu and the drawer can't disagree. */
// One mutually-exclusive status filter, shown as two blocks: "Status" (done/not-done) and a
// separate "Progress" block (started/not-started), because started-ness is a different question
// from done-ness and reading them in one column ran them together.
/**
 * Done-ness and started-ness are **one** mutually-exclusive filter asked as two questions, because
 * the started answers read as a different question from the done ones.
 *
 * **Done-ness is the "Progress" question and started-ness the "Status" one** (owner, 2026-08-18):
 * finishing is the far end of a progress bar, while having begun is a state the target is in. The
 * web had the two headings the other way round until 2026-08-20; Android is the reference.
 */
const PROGRESS_CHOICES = [
  { value: "all", label: "All" },
  { value: "done", label: "Done" },
  { value: "not-done", label: "Not done" },
] as const;

const STATUS_CHOICES = [
  { value: "started", label: "Started" },
  { value: "not-started", label: "Not started" },
] as const;

/** Which kind of target this is — the twin of Android's `TargetTypeFilter`. */
const TYPE_CHOICES = [
  { value: "all", label: "All" },
  { value: "binary", label: "Done/not done" },
  { value: "numeric", label: "Numeric" },
  { value: "checklist", label: "Checklist" },
] as const;

const DEADLINE_CHOICES = [
  { value: "all", label: "All" },
  { value: "overdue", label: "Overdue" },
  { value: "not-overdue", label: "Not overdue" },
  { value: "none", label: "No deadline" },
] as const;

const LOCK_CHOICES = [
  { value: "all", label: "All" },
  { value: "locked", label: "Locked" },
  { value: "unlocked", label: "Unlocked" },
] as const;

/** The sort question and its direction — shared by the desktop menu and the mobile sheet. */
const TARGET_SORT_CHOICES = [
  { value: "title", label: "Name" },
  { value: "deadline", label: "Deadline" },
  { value: "progress", label: "Progress" },
  { value: "created", label: "Created" },
] as const;

const TARGET_DIRECTION_CHOICES = [
  { value: "asc", label: "Ascending" },
  { value: "desc", label: "Descending" },
] as const;

/* ─────────────────────────────────────────────────────────────────────────────
   TargetsSection — wraps Section with search, filter and mobile-sort controls
───────────────────────────────────────────────────────────────────────────── */

export function TargetsSection({
  goal,
  onNewTarget,
}: {
  goal: Goal;
  onNewTarget: () => void;
}) {
  const isMobile = useIsMobile();
  const [search, setSearch] = useState("");
  // The phone's search is a glyph until it is opened; the field then takes the header row.
  const [searchOpen, setSearchOpen] = useState(false);
  // **Every question this panel asks lives in the store**, so the list's padlock has one thing to
  // pin: the sort and the date range used to be component state, which a lock could not reach and
  // which a remount cleared anyway.
  const setView = useShellFilters((s) => s.setView);
  const resetList = useShellFilters((s) => s.resetList);
  const setLocked = useShellFilters((s) => s.setLocked);
  const statusFilter = useShellFilters((s) => s.targetStatus);
  const deadlineFilter = useShellFilters((s) => s.targetDeadline);
  const lockFilter = useShellFilters((s) => s.targetLock);
  const typeFilter = useShellFilters((s) => s.targetType);
  const deadlineFrom = useShellFilters((s) => s.targetDeadlineFrom);
  const deadlineTo = useShellFilters((s) => s.targetDeadlineTo);
  const sortField = useShellFilters((s) => s.targetSort);
  const sortDesc = useShellFilters((s) => s.targetSortDesc);
  const targetsLocked = useListLocked("targets");
  const targetsActive = useListActive("targets");
  const [mobileOpen, setMobileOpen] = useState(false);

  const setStatusFilter = (next: TargetStatusFilter) =>
    setView({ targetStatus: next });
  const setLockFilter = (next: TargetLockFilter) =>
    setView({ targetLock: next });
  const setTypeFilter = (next: TargetTypeFilter) =>
    setView({ targetType: next });
  const setSortField = (next: TargetSortKey) => setView({ targetSort: next });
  const setSortDesc = (next: boolean) => setView({ targetSortDesc: next });

  // **"No deadline" and a date range can never both be on** (owner, 2026-08-18, "this goes for the
  // filters everywhere"). A range asks which deadlines to keep and "No deadline" asks for the
  // targets that haven't got one, so together they match nothing and neither control says why.
  const setDeadlineFilter = (next: TargetDeadlineFilter) =>
    setView(
      next === "none"
        ? {
            targetDeadline: next,
            targetDeadlineFrom: "",
            targetDeadlineTo: "",
          }
        : { targetDeadline: next },
    );
  const setDeadlineFrom = (value: string) =>
    setView({
      targetDeadlineFrom: value,
      ...(value && deadlineFilter === "none"
        ? { targetDeadline: "all" as const }
        : {}),
    });
  const setDeadlineTo = (value: string) =>
    setView({
      targetDeadlineTo: value,
      ...(value && deadlineFilter === "none"
        ? { targetDeadline: "all" as const }
        : {}),
    });

  // Celebrate a target crossing the line. This lives here, not on the card: completing a target
  // filters its row out of the list, so the row unmounts before any effect of its own could run.
  const achievedCount = goal.targets.filter(
    (t) => targetProgress(t) >= 1,
  ).length;
  const previousAchieved = useRef<number | null>(null);
  useEffect(() => {
    if (
      previousAchieved.current !== null &&
      achievedCount > previousAchieved.current
    ) {
      celebrate();
    }
    previousAchieved.current = achievedCount;
  }, [achievedCount]);

  // A dot, not a bracketed number: the trigger is the bare glyph at every width — the rule from
  // CLAUDE.md, dot on a lone glyph and "(2)" on a worded trigger, never both.
  const hasAnyActive = !!search.trim() || targetsActive;

  const processedTargets = useMemo(() => {
    let ts = [...goal.targets];

    if (search.trim()) {
      const q = search.toLowerCase();
      ts = ts.filter((t) => {
        if (t.title.toLowerCase().includes(q)) return true;
        if (t.type === "checklist") {
          return t.items.some((item) => item.text.toLowerCase().includes(q));
        }
        return false;
      });
    }

    if (deadlineFrom || deadlineTo) {
      ts = ts.filter((t) => {
        if (!t.deadline) return false;
        const d = t.deadline.slice(0, 10);
        if (deadlineFrom && d < deadlineFrom.slice(0, 10)) return false;
        if (deadlineTo && d > deadlineTo.slice(0, 10)) return false;
        return true;
      });
    }

    // The four questions are independent — a target has to pass all of them.
    if (statusFilter === "done") ts = ts.filter((t) => targetProgress(t) >= 1);
    else if (statusFilter === "not-done")
      ts = ts.filter((t) => targetProgress(t) < 1);
    else if (statusFilter === "started")
      ts = ts.filter((t) => targetProgress(t) > 0 && targetProgress(t) < 1);
    else if (statusFilter === "not-started")
      ts = ts.filter((t) => targetProgress(t) <= 0);

    if (deadlineFilter === "none") ts = ts.filter((t) => !t.deadline);
    else if (deadlineFilter === "overdue")
      ts = ts.filter((t) => isTargetOverdue(t));
    else if (deadlineFilter === "not-overdue")
      ts = ts.filter((t) => !!t.deadline && !isTargetOverdue(t));

    if (lockFilter === "locked") ts = ts.filter((t) => isProgressLocked(t));
    else if (lockFilter === "unlocked")
      ts = ts.filter((t) => !isProgressLocked(t));

    if (typeFilter !== "all") ts = ts.filter((t) => t.type === typeFilter);

    return ts;
  }, [
    goal.targets,
    search,
    deadlineFrom,
    deadlineTo,
    typeFilter,
    statusFilter,
    deadlineFilter,
    lockFilter,
  ]);

  const processedGoal = useMemo(
    () => ({ ...goal, targets: processedTargets }),
    [goal, processedTargets],
  );

  return (
    <Section
      title="Will do"
      count={goal.targets.length}
      countVariant="orange"
      // The open search takes the whole header row on a phone — the Android All-goals pattern.
      // On desktop the field has always sat beside the title, so nothing is overridden there.
      // `isMobile` as well as `searchOpen`: the glyph that opens this only exists on a phone, so a
      // window widened while it is open would otherwise leave the header with a field and no title.
      headerOverride={
        searchOpen && isMobile ? (
          <SectionSearchField
            value={search}
            onChange={setSearch}
            onClose={() => setSearchOpen(false)}
            placeholder="Search targets"
          />
        ) : undefined
      }
      action={
        <div className="flex items-center gap-2">
          {/* Desktop keeps the field itself; a phone gets the glyph that opens it. */}
          <SectionSearchInput
            value={search}
            onChange={setSearch}
            placeholder="Search targets"
          />

          {/* A bare search glyph on a phone — the desktop has room for the field itself, which
              sits above. The search takes the whole header row when open (`headerOverride`), the
              way the Android All-goals header does. */}
          <span className="sm:hidden">
            <SectionSearchButton
              onOpen={() => setSearchOpen(true)}
              active={!!search}
              ariaLabel="Search targets"
            />
          </span>

          {/* **The filter glyph is every width's**, and so is the panel behind it (owner,
              2026-08-20). The desktop used to ask these questions in a dropdown of columns; there
              is no dropdown for a filter anywhere now, and the laptop opens the same panel from
              the right that the phone opens from the bottom. The glyph alone is Android's
              treatment too (`SpiraFilterSortTrigger`). */}
          <div className="flex items-center">
            <FilterIconTrigger
              active={hasAnyActive}
              onClick={() => setMobileOpen(true)}
              ariaLabel="Filter and sort targets"
            />
            <ToolbarSheet
              open={mobileOpen}
              onOpenChange={setMobileOpen}
              title="Filter & Sort"
              // **Everything**: the four questions used to be exempt as "standing preferences",
              // so a button promising all of them quietly kept four answers (owner, 2026-08-21).
              onReset={() => resetList("targets")}
              resetDisabled={!targetsActive}
              locked={targetsLocked}
              onLockedChange={(next) => setLocked("targets", next)}
            >
              {/* The questions, in the order and under the headings Android asks them
                  (`GoalWorkspaceScreen.kt`): Progress, Status, Deadline, Type, Lock, then the
                  range. One-line questions are pills, the direction is a segmented control and the
                  sort key is a card — three shapes for three kinds of question. */}
              <SheetGroup title="Progress">
                <SheetPills
                  options={PROGRESS_CHOICES}
                  value={statusFilter}
                  onChange={setStatusFilter}
                />
              </SheetGroup>
              <SheetGroup title="Status">
                <SheetPills
                  options={STATUS_CHOICES}
                  value={statusFilter}
                  onChange={setStatusFilter}
                  tone="info"
                />
              </SheetGroup>
              <SheetGroup title="Deadline">
                <SheetPills
                  options={DEADLINE_CHOICES}
                  value={deadlineFilter}
                  onChange={setDeadlineFilter}
                  tone="warning"
                />
              </SheetGroup>
              <SheetGroup title="Type">
                <SheetPills
                  options={TYPE_CHOICES}
                  value={typeFilter}
                  onChange={setTypeFilter}
                  tone="intelligence"
                />
              </SheetGroup>
              <SheetGroup title="Lock">
                <SheetPills
                  options={LOCK_CHOICES}
                  value={lockFilter}
                  onChange={setLockFilter}
                  tone="teal"
                />
              </SheetGroup>

              {/* The dates themselves, under the "Overdue / Not overdue" question that reads them
                  relative to today. **There is no "achieved between" pair** — it was the web's
                  alone, it asked a question nobody had asked for, and the owner took it off both
                  surfaces on 2026-08-20. */}
              <SheetGroup title="Deadline range">
                <SheetDateRange
                  from={deadlineFrom}
                  to={deadlineTo}
                  onFromChange={setDeadlineFrom}
                  onToChange={setDeadlineTo}
                />
              </SheetGroup>

              <SheetGroup title="Direction">
                <SheetSegmented
                  options={TARGET_DIRECTION_CHOICES}
                  value={sortDesc ? "desc" : "asc"}
                  onChange={(v) => setSortDesc(v === "desc")}
                />
              </SheetGroup>
              <SheetGroup title="Sort by">
                <SheetChoiceCards
                  options={TARGET_SORT_CHOICES}
                  value={sortField}
                  onChange={setSortField}
                />
              </SheetGroup>
            </ToolbarSheet>
          </div>

          {/* A round + on a phone: the word, a search glyph and a filter glyph do not fit across a
              narrow header, and "Add target" is the piece the section's own title already implies. */}
          <span className="sm:hidden">
            <RoundAddButton
              onClick={onNewTarget}
              ariaLabel="Add target"
              tone="accent"
            />
          </span>
          <button
            onClick={onNewTarget}
            className="hidden sm:inline-flex items-center px-3 h-9 rounded-md bg-[#F45D48] text-white text-sm font-medium hover:bg-[#F45D48]/90"
          >
            Add target
          </button>
        </div>
      }
    >
      <TargetsList
        goal={processedGoal}
        unfilteredCount={goal.targets.length}
        sortField={sortField}
        sortDesc={sortDesc}
      />
    </Section>
  );
}

/* ─────────────────────────────────────────────────────────────────────────────
   TargetsList — renders mobile cards + desktop table
───────────────────────────────────────────────────────────────────────────── */

export function TargetsList({
  goal,
  unfilteredCount,
  sortField,
  sortDesc,
}: {
  goal: Goal;
  /**
   * How many targets there are before the search and the filter — the list itself only ever sees
   * what survived them, so without this it cannot tell "no targets yet" from "you have hidden them
   * all", and it showed the invitation for both.
   */
  unfilteredCount?: number;
  sortField?: SortField;
  sortDesc?: boolean;
}) {
  const { updateTarget, removeTarget } = useSpira();
  const [confirmTarget, setConfirmTarget] = useState<Target | null>(null);

  const mobileSorted = useMemo(() => {
    if (!sortField) return goal.targets;
    return [...goal.targets].sort((a, b) => {
      if (sortField === "deadline") {
        const aHas = !!a.deadline,
          bHas = !!b.deadline;
        if (!aHas && !bHas) return 0;
        if (!aHas) return 1;
        if (!bHas) return -1;
        const cmp =
          new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime();
        return (sortDesc ?? false) ? -cmp : cmp;
      }
      let cmp = 0;
      if (sortField === "title") cmp = a.title.localeCompare(b.title);
      // A target with no recorded creation date sorts as the oldest, which is what it is: the
      // column was added after those rows were written.
      else if (sortField === "created")
        cmp = (a.createdAt ?? "").localeCompare(b.createdAt ?? "");
      else cmp = targetProgress(a) - targetProgress(b);
      return (sortDesc ?? false) ? -cmp : cmp;
    });
  }, [goal.targets, sortField, sortDesc]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleHash = () => {
      const hash = window.location.hash;
      if (!hash) return;
      if (hash.startsWith("#target-")) {
        const id = hash.replace("#target-", "");
        window.setTimeout(() => {
          let el = document.getElementById(`target-desktop-${id}`);
          if (!el || el.offsetParent === null) {
            el = document.getElementById(`target-mobile-${id}`);
          }
          if (el) {
            const yOffset = -112;
            const y = el.getBoundingClientRect().top + window.scrollY + yOffset;
            window.scrollTo({ top: y, behavior: "smooth" });
          }
        }, 10);
      }
    };
    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, [goal.targets]);

  return (
    <div className="space-y-3">
      {goal.targets.length === 0 &&
        ((unfilteredCount ?? 0) > 0 ? (
          <FilteredEmptyNotice>
            No targets match that search or filter.
          </FilteredEmptyNotice>
        ) : (
          <p className="text-sm text-muted-foreground italic px-1">
            Targets are how you execute. Add a numeric, binary, or checklist
            target.
          </p>
        ))}
      <ul className="spira-target-mobile-list space-y-3">
        {mobileSorted.map((t) => (
          <TargetRow
            key={t.id}
            target={t}
            onUpdate={(patch) => updateTarget(goal.id, t.id, patch)}
            onRemove={() => setConfirmTarget(t)}
          />
        ))}
      </ul>
      {goal.targets.length > 0 && <DesktopTargetsTable goal={goal} />}
      <TargetDeleteConfirm
        target={confirmTarget}
        open={!!confirmTarget}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
        onConfirm={() => {
          if (!confirmTarget) return;
          removeTarget(goal.id, confirmTarget.id);
          setConfirmTarget(null);
        }}
      />
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────────────────────
   DesktopTargetsTable
   Controlled (sortField/onToggleSort provided) → uses pre-sorted goal.targets.
   Uncontrolled (standalone / tests)            → sorts internally.
───────────────────────────────────────────────────────────────────────────── */

export function DesktopTargetsTable({
  goal,
  sortField: externalSortField,
  sortDesc: externalSortDesc,
  onToggleSort,
}: {
  goal: Goal;
  sortField?: SortField;
  sortDesc?: boolean;
  onToggleSort?: (field: SortField) => void;
}) {
  const { updateTarget, removeTarget } = useSpira();
  const [internalSortField, setInternalSortField] =
    useState<SortField>("deadline");
  const [internalSortDesc, setInternalSortDesc] = useState(false);
  const [editingTasksFor, setEditingTasksFor] = useState<string | null>(null);
  const [editingNumericFor, setEditingNumericFor] = useState<string | null>(
    null,
  );
  const [confirmTarget, setConfirmTarget] = useState<Target | null>(null);

  const isControlled = externalSortField !== undefined;
  const sortField = isControlled ? externalSortField! : internalSortField;
  const sortDesc = isControlled
    ? (externalSortDesc ?? false)
    : internalSortDesc;

  const toggleSort = (field: SortField) => {
    if (onToggleSort) {
      onToggleSort(field);
    } else {
      if (internalSortField === field) setInternalSortDesc((d) => !d);
      else {
        setInternalSortField(field);
        setInternalSortDesc(false);
      }
    }
  };

  // When controlled, data is pre-sorted by parent; when uncontrolled, sort here.
  const displayTargets = isControlled
    ? goal.targets
    : [...goal.targets].sort((a, b) => {
        if (sortField === "deadline") {
          const aHas = !!a.deadline,
            bHas = !!b.deadline;
          if (!aHas && !bHas) return 0;
          if (!aHas) return 1;
          if (!bHas) return -1;
          const cmp =
            new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime();
          return sortDesc ? -cmp : cmp;
        }
        let cmp = 0;
        if (sortField === "title") cmp = a.title.localeCompare(b.title);
        else if (sortField === "created")
          cmp = (a.createdAt ?? "").localeCompare(b.createdAt ?? "");
        else if (sortField === "progress")
          cmp = targetProgress(a) - targetProgress(b);
        return sortDesc ? -cmp : cmp;
      });

  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleHash = () => {
      const hash = window.location.hash;
      if (!hash) return;
      if (hash.startsWith("#task-")) {
        const taskId = hash.replace("#task-", "");
        const target = goal.targets.find(
          (t) =>
            t.type === "checklist" &&
            t.items.some((item) => item.id === taskId),
        );
        if (!target) return;
        setEditingTasksFor(target.id);
        window.setTimeout(
          () =>
            document
              .getElementById(hash.slice(1))
              ?.scrollIntoView({ behavior: "smooth", block: "center" }),
          50,
        );
      }
    };
    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, [goal.targets]);

  // The sort indicator, and the two states say different things.
  //
  // An **inactive** column shows Gravity's `carets-expand-vertical` — the double caret (owner,
  // 2026-08-21). It used to show a faint ChevronUp, which is not "you can sort by this": it is
  // "sorted ascending, quietly", and next to the one column that really was sorted ascending the
  // only thing telling them apart was opacity. The double caret has no direction to misread.
  //
  // The **active** column keeps the single chevron, because that is where direction is real
  // information — up for ascending, down for descending, in Kale.
  const SortIcon = ({ field }: { field: string }) => {
    const active = sortField === field;
    const Icon = active
      ? sortDesc
        ? ChevronDown
        : ChevronUp
      : CaretsExpandVertical;
    return (
      <Icon
        className={cn(
          "ml-1.5 h-4 w-4 shrink-0 transition-opacity",
          active
            ? "text-primary opacity-100"
            : "opacity-30 group-hover:opacity-60",
        )}
      />
    );
  };

  return (
    <div className="spira-target-desktop-table">
      <Table>
        <TableHeader className="bg-muted">
          <TableRow className="border-0 border-b">
            {/* The padlock gets a column of its own rather than trailing the title. Inline, it
                sat at a different x on every row (wherever that target's name happened to end),
                which read as clutter instead of as a column of state you can scan down. */}
            <TableHead className="w-[5%] pl-6">
              <span className="sr-only">Progress lock</span>
            </TableHead>
            <TableHead
              className="cursor-pointer hover:text-foreground w-[40%]"
              onClick={() => toggleSort("title")}
            >
              <div className="flex items-center">
                Target Name <SortIcon field="title" />
              </div>
            </TableHead>
            <TableHead
              className="cursor-pointer hover:text-foreground w-[15%]"
              onClick={() => toggleSort("deadline")}
            >
              <div
                className="flex items-center"
                title="Deadline or Completed date"
              >
                Date <SortIcon field="deadline" />
              </div>
            </TableHead>
            <TableHead className="w-[15%]">
              <div title="Click to update">Update</div>
            </TableHead>
            <TableHead
              className="cursor-pointer hover:text-foreground w-[15%]"
              onClick={() => toggleSort("progress")}
            >
              <div className="flex items-center">
                Progress <SortIcon field="progress" />
              </div>
            </TableHead>
            <TableHead className="w-[10%] text-right pr-6">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {displayTargets.map((t) => {
            const progress = targetProgress(t);
            const done = progress >= 1;
            const locked = isProgressLocked(t);
            return (
              <TableRow
                key={t.id}
                id={`target-desktop-${t.id}`}
                className={cn(
                  "group scroll-mt-24 transition-colors bg-white",
                  // A paler hover step from the palette (CLAUDE.md): Kale-100 when done,
                  // Warning-100 when not — visible, but not the loud earlier tints.
                  done ? "hover:bg-[#F3FAFB]" : "hover:bg-[#FFFBF7]",
                )}
              >
                {/* The padlock is always visible — it is state, not a hidden action — and it now
                    has its own column, so it lines up down the table. It top-aligns to sit level
                    with the title's first line, and stays grey (Kale on hover) rather than
                    lighting up when locked. */}
                <TableCell className="pl-6 align-top">
                  <ProgressLockButton
                    locked={locked}
                    neutralTone
                    onToggle={(next) =>
                      updateTarget(goal.id, t.id, { progressLocked: next })
                    }
                    className="h-6 w-6"
                    iconClassName="h-4 w-4"
                  />
                </TableCell>
                <TableCell className="align-top">
                  <InlineText
                    value={t.title}
                    onChange={(title) => updateTarget(goal.id, t.id, { title })}
                    placeholder="Untitled target"
                    ariaLabel="Edit target title"
                    maxLength={FIELD_LIMITS.targetTitle}
                    maxLengthLabel="Target title"
                    className={cn(
                      "block min-w-0 text-sm font-medium text-foreground",
                      ACHIEVED_LINK_TONE(done),
                    )}
                  />
                </TableCell>
                <TableCell>
                  <span
                    title={
                      (done ? t.achievedAt : t.deadline)
                        ? done
                          ? "Completed"
                          : "Deadline"
                        : undefined
                    }
                  >
                    <DeadlinePopover
                      iso={t.deadline}
                      achievedAt={t.achievedAt}
                      completed={done}
                      variant="text"
                      side="top"
                      hideChevron
                      hideDaysLeft
                      onChange={(next) =>
                        updateTarget(goal.id, t.id, { deadline: next })
                      }
                    />
                  </span>
                </TableCell>
                <TableCell>
                  {t.type === "binary" && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <button
                          title="Click"
                          className="flex items-center gap-2 group h-8"
                        >
                          <div
                            className={cn(
                              "h-2 w-2 rounded-full shrink-0",
                              t.done ? "bg-success" : "bg-muted-foreground/40",
                            )}
                          ></div>
                          <span className="text-sm text-foreground group-hover:text-foreground/75 transition-colors">
                            {t.done ? "Done" : "Not done"}
                          </span>
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent
                        align="start"
                        className="min-w-[120px]"
                      >
                        <DropdownMenuItem
                          onClick={() =>
                            locked
                              ? warnProgressLocked()
                              : updateTarget(goal.id, t.id, { done: false })
                          }
                          className="text-sm"
                        >
                          Not done
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() =>
                            locked
                              ? warnProgressLocked()
                              : updateTarget(goal.id, t.id, { done: true })
                          }
                          className="text-sm"
                        >
                          Done
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                  {t.type === "numeric" && (
                    <button
                      onClick={() => setEditingNumericFor(t.id)}
                      title="Click"
                      className="flex items-center gap-2 group h-8"
                    >
                      <div
                        className={cn(
                          "h-2 w-2 rounded-full shrink-0",
                          done ? "bg-success" : "bg-[#F45D48]",
                        )}
                      ></div>
                      <span className="text-sm text-foreground group-hover:text-foreground/75 transition-colors">
                        {done ? "Complete" : "Update"}
                      </span>
                    </button>
                  )}
                  {t.type === "checklist" && (
                    <button
                      onClick={() => setEditingTasksFor(t.id)}
                      title="Click"
                      className="flex items-center gap-2 group h-8"
                    >
                      <div
                        className={cn(
                          "h-2 w-2 rounded-full shrink-0",
                          done ? "bg-success" : "bg-[#8DD3D4]",
                        )}
                      ></div>
                      <span className="text-sm text-foreground group-hover:text-foreground/75 transition-colors">
                        {done ? "Complete" : "Tasks"}
                      </span>
                    </button>
                  )}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <ProgressBar
                      value={progress}
                      className="w-full max-w-[80px]"
                    />
                    <span className="text-xs font-semibold num tabular-nums text-foreground/80 min-w-[3ch] text-right">
                      {formatPercent(progress, progressSteps(t))}%
                    </span>
                  </div>
                </TableCell>
                <TableCell className="pr-6">
                  <div className="flex items-center justify-end">
                    <ElementActionsMenu
                      ariaLabel="Target actions"
                      deleteLabel="Delete target"
                      attachedTo={t.title}
                      onDelete={() => setConfirmTarget(t)}
                      onAttach={(resourceId) => {
                        const next = appendResourceToken(
                          t.title,
                          resourceId,
                          FIELD_LIMITS.targetTitle,
                        );
                        if (next) updateTarget(goal.id, t.id, { title: next });
                      }}
                      className={cn(
                        // Always visible in the table — the owner wants the actions kebab present
                        // without having to hover the row first (unlike the inline card overlays).
                        "inline-flex rounded-md p-1.5 text-foreground hover:text-primary",
                      )}
                    />
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <TargetDeleteConfirm
        target={confirmTarget}
        open={!!confirmTarget}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
        onConfirm={() => {
          if (!confirmTarget) return;
          removeTarget(goal.id, confirmTarget.id);
          setConfirmTarget(null);
        }}
      />

      {/* Numeric Updates Sheet */}
      <Sheet
        open={!!editingNumericFor}
        onOpenChange={(open) => !open && setEditingNumericFor(null)}
      >
        <SheetContent
          side="right"
          className="w-full sm:max-w-md p-0 flex flex-col bg-surface border-l hairline"
        >
          {editingNumericFor && (
            <div className="flex-1 flex flex-col overflow-hidden">
              <div className="px-6 pt-5 pb-2 flex items-center justify-between bg-surface z-10 sticky top-0">
                <h3 className="font-bold">Update Progress</h3>
                <button
                  onClick={() => setEditingNumericFor(null)}
                  className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="flex-1 px-6 pb-6 pt-0 overflow-y-auto">
                {(() => {
                  const target = goal.targets.find(
                    (t) => t.id === editingNumericFor,
                  );
                  if (!target || target.type !== "numeric") return null;
                  return (
                    <div className="pt-2">
                      <NumericBody
                        target={target}
                        onUpdate={(patch) =>
                          updateTarget(goal.id, target.id, patch)
                        }
                        locked={isProgressLocked(target)}
                      />
                    </div>
                  );
                })()}
              </div>
              <div className="p-4 flex items-center justify-end gap-2 bg-surface">
                <button
                  onClick={() => setEditingNumericFor(null)}
                  className="h-11 px-5 rounded-md border-2 border-border text-foreground font-semibold text-sm hover:bg-secondary transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={() => setEditingNumericFor(null)}
                  className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 transition-colors"
                >
                  Save
                </button>
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Checklist Tasks Sheet */}
      <TasksResizableSheet
        open={!!editingTasksFor}
        onClose={() => setEditingTasksFor(null)}
        items={
          editingTasksFor
            ? goal.targets.find((t) => t.id === editingTasksFor)?.type ===
              "checklist"
              ? (
                  goal.targets.find((t) => t.id === editingTasksFor) as Extract<
                    Target,
                    { type: "checklist" }
                  >
                ).items
              : []
            : []
        }
        title={
          editingTasksFor
            ? (goal.targets.find((t) => t.id === editingTasksFor)?.title ??
              "Tasks")
            : "Tasks"
        }
        onChange={(items) =>
          editingTasksFor && updateTarget(goal.id, editingTasksFor, { items })
        }
        locked={(() => {
          const target = goal.targets.find((t) => t.id === editingTasksFor);
          return target ? isProgressLocked(target) : false;
        })()}
      />
    </div>
  );
}

function TargetDeleteConfirm({
  target,
  open,
  onOpenChange,
  onConfirm,
}: {
  target: Target | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  // Quote the title as prose — an attached resource reads as its name, never as a raw tag.
  const title = useReadableText(target?.title ?? "");
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Delete this target?"
      description={`Are you sure you want to permanently delete "${title || "this target"}"? Progress and checklist tasks inside it will be removed. You can't undo this.`}
      confirmLabel="Yes, delete"
      cancelLabel="No, go back"
      onConfirm={onConfirm}
    />
  );
}

/**
 * A target on mobile: a card with the deadline on the left, the (inline-editable) title beside it
 * and the padlock on the right; a hairline progress strip across the card; and a full-width
 * "Update progress" footer on Kale-200 that reveals the type-specific progress controls plus the
 * target's own actions menu. Modelled on the reference card the owner supplied (2026-08-02).
 */
export function TargetRow({
  target,
  onUpdate,
  onRemove,
}: {
  target: Target;
  onUpdate: (patch: Partial<Target>) => void;
  onRemove: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const progress = targetProgress(target);
  const done = progress >= 1;
  const locked = isProgressLocked(target);
  const { ref: titleRef, tall: tallTitle } = useTallText<HTMLDivElement>(3);
  // What the numbers say while they're being typed — the card's own percentage follows the
  // bar inside it, so the two never disagree mid-edit. Null whenever nothing is being typed.
  const [previewProgress, setPreviewProgress] = useState<number | null>(null);

  const displayIso =
    done && target.achievedAt ? target.achievedAt : target.deadline;
  const deadlineInfo = formatDeadlineInfo(displayIso, done);
  const createdLabel = target.createdAt
    ? `Created · ${new Date(target.createdAt).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
      })}`
    : "";

  return (
    <li
      id={`target-mobile-${target.id}`}
      className={cn(
        // No `overflow-hidden`: the padlock badge deliberately hangs off the corner, the way the
        // rating smiley does on an option card.
        // An achieved target is NOT tinted: the tile's tick, the caption and the 100% footer
        // already say so, and a coloured head made the list noisy.
        "surface-card relative scroll-mt-24",
      )}
    >
      {/* Padlock stuck on the card's corner — always present, never in the content flow.
          **The same badge as the option card's smiley** (`OptionsList.tsx`): 28px, a hairline ring,
          a white plate and a soft shadow. It used to be a bare ring with the page showing through,
          so beside an option it read as a different component hanging off the corner. */}
      <ProgressLockButton
        locked={locked}
        onToggle={(next) =>
          onUpdate({ progressLocked: next } as Partial<Target>)
        }
        className="absolute -right-2 -top-2 z-10 h-7 w-7 rounded-full border border-border bg-surface shadow-sm"
        iconClassName="h-4 w-4"
      />

      {/* Head: the deadline tile, then the title. The tile centres beside a short title and
          moves to the top once the title runs past three lines. */}
      <div
        className={cn(
          "flex gap-3 p-4",
          tallTitle ? "items-start" : "items-center",
        )}
      >
        <DeadlinePopover
          iso={target.deadline}
          achievedAt={target.achievedAt}
          completed={done}
          onChange={(next) => onUpdate({ deadline: next } as Partial<Target>)}
          renderTrigger={() => <DeadlineTile info={deadlineInfo} done={done} />}
        />

        <div ref={titleRef} className="min-w-0 flex-1">
          <InlineText
            value={target.title}
            onChange={(title) => onUpdate({ title } as Partial<Target>)}
            ariaLabel="Edit target title"
            maxLength={FIELD_LIMITS.targetTitle}
            maxLengthLabel="Target title"
            className={cn(
              "text-base font-medium text-foreground",
              ACHIEVED_LINK_TONE(done),
            )}
          />
          <p
            className={cn(
              "mt-1 text-[11px] font-semibold",
              done
                ? "text-primary"
                : deadlineInfo?.isOverdue
                  ? "text-[#D74041]"
                  : "text-muted-foreground",
            )}
          >
            {done && deadlineInfo
              ? `Completed · ${deadlineInfo.dateStr}`
              : deadlineInfo
                ? deadlineInfo.countdown
                : createdLabel}
          </p>
        </div>
      </div>

      {/* Progress strip — the page-scroll bar's shape, carrying this target's progress. */}
      {/* The part still to go is Kale-300, not a grey: the strip then reads as one teal measure
          filling up rather than as a coloured bar sitting on dead space. */}
      <div className="h-[5px] w-full overflow-hidden bg-[#8DD3D4]">
        <div
          className="h-full bg-primary transition-[width] duration-300 ease-out"
          style={{ width: `${Math.round(progress * 100)}%` }}
        />
      </div>

      {/* Footer: reveals the progress controls for this target's type. The label alone carries
          the state — no chevron. */}
      <button
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        className={cn(
          "flex w-full items-center justify-center bg-[#E0F2F5] px-4 py-3 text-[15px] font-semibold text-primary transition-colors hover:bg-[#8DD3D4]/40",
          // The card no longer clips its children (the padlock hangs off the corner), so the
          // footer rounds its own bottom — unless the expanded panel sits below it.
          !expanded && "rounded-b-lg",
        )}
      >
        {done
          ? "100%"
          : expanded
            ? `${formatPercent(previewProgress ?? progress, progressSteps(target))}% progress`
            : "Update progress"}
      </button>

      {expanded && (
        <div className="rounded-b-lg border-t border-border/60 bg-surface px-4 pb-4 pt-3">
          {target.type === "numeric" && (
            <NumericBody
              target={target}
              onUpdate={onUpdate}
              locked={locked}
              onPreviewProgress={setPreviewProgress}
            />
          )}

          {target.type === "binary" && (
            <label className="flex w-full cursor-pointer items-center justify-between gap-3 py-0.5">
              <span
                className={cn(
                  "text-sm",
                  target.done
                    ? "text-muted-foreground"
                    : "font-medium text-foreground",
                )}
              >
                {target.done ? "Done" : "Mark done"}
              </span>
              <Switch
                checked={target.done}
                onCheckedChange={(next) => {
                  if (locked) {
                    warnProgressLocked();
                    return;
                  }
                  onUpdate({ done: next } as Partial<Target>);
                }}
                aria-label={target.done ? "Mark not done" : "Mark done"}
              />
            </label>
          )}

          {target.type === "checklist" && (
            <>
              <ChecklistEditor
                items={target.items}
                onChange={(items) => onUpdate({ items } as Partial<Target>)}
                compact
                hideCountdown
                locked={locked}
              />
              <div className="mt-4">
                <AddTaskControl
                  compact
                  onAdd={(text) => {
                    if (locked) {
                      warnProgressLocked();
                      return;
                    }
                    onUpdate({
                      items: [
                        ...target.items,
                        {
                          id: Math.random().toString(36).slice(2, 9),
                          text,
                          done: false,
                        },
                      ],
                    } as Partial<Target>);
                  }}
                />
              </div>
            </>
          )}

          {/* The target's own actions, spelled out rather than hidden behind a ⋯ menu. */}
          <AttachResourceButton
            className="mt-4"
            attachedTo={target.title}
            onAttach={(resourceId) => {
              const next = appendResourceToken(
                target.title,
                resourceId,
                FIELD_LIMITS.targetTitle,
              );
              if (next) onUpdate({ title: next } as Partial<Target>);
            }}
          />
          {/* Delete is the **twin of "Attach resource"** — a circled mark and a 14px label, in red
              rather than teal. As a filled near-black button it was the heaviest thing on an
              opened card, which is the wrong weight for the one action you can't undo. */}
          <button
            type="button"
            onClick={onRemove}
            className="mt-2 flex items-center gap-2 py-1 text-left text-sm font-semibold text-destructive transition-colors hover:text-destructive/80"
          >
            <CircleX className="h-[18px] w-[18px] shrink-0" />
            Delete target
          </button>
          <div className="mt-4 flex items-center justify-end">
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="h-10 rounded-md border border-border px-5 text-sm font-semibold text-foreground transition-colors hover:bg-secondary/60"
            >
              {/* It collapses the panel; nothing is discarded — every edit here saves as it is
                  made, so "Cancel" promised an undo that never existed. */}
              Close
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

function NumericBody({
  target,
  onUpdate,
  locked = false,
  onPreviewProgress,
}: {
  target: Extract<Target, { type: "numeric" }>;
  onUpdate: (patch: Partial<Target>) => void;
  /** Progress is pinned: the numbers are read-only (the unit and title are not). */
  locked?: boolean;
  /** The typed-but-not-yet-saved progress, so an enclosing card can show the same number
   *  (null once editing ends). */
  onPreviewProgress?: (p: number | null) => void;
}) {
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null,
  );
  // What the card's strip shows WHILE the user is typing. The value itself still commits on
  // blur/Enter (never per keystroke) — but without this the strip sits still until focus moves,
  // which on a large target reads as "progress is broken": typing 4000 against 1 900 000 changes
  // nothing visible until you tab away. The editor no longer draws a bar of its own, so the
  // preview only has to reach the enclosing card.
  const setPreviewProgress = (p: number | null) => onPreviewProgress?.(p);
  const previewFrom = (field: "current" | "total" | "start", raw: string) => {
    const text = raw.trim();
    // Only a plainly valid number previews; anything else (empty, "1.", "-2") leaves the bar
    // where it was rather than flashing a nonsense value.
    if (locked || !/^\d+(\.\d+)?$/.test(text)) {
      setPreviewProgress(null);
      return;
    }
    const next = { ...target, [field]: parseFloat(text) };
    setPreviewProgress(validatePatch(next) ? null : targetProgress(next));
  };
  const start = target.start ?? 0;
  const minValue = Math.min(start, target.total);
  const maxValue = Math.max(start, target.total);

  const validatePatch = (
    patch: Partial<Extract<Target, { type: "numeric" }>>,
  ) => {
    const nextStart = patch.start ?? start;
    const nextCurrent = patch.current ?? target.current;
    const nextTotal = patch.total ?? target.total;
    if (nextStart < 0 || nextCurrent < 0 || nextTotal < 0) {
      return "Numbers cannot be negative.";
    }
    if (nextStart === nextTotal) {
      return "Start and target must be different.";
    }
    const min = Math.min(nextStart, nextTotal);
    const max = Math.max(nextStart, nextTotal);
    if (nextCurrent < min || nextCurrent > max) {
      return `Current must stay between ${min} and ${max}.`;
    }
    return null;
  };

  const commitPatch = (
    patch: Partial<Extract<Target, { type: "numeric" }>>,
  ) => {
    if (locked) {
      setValidationMessage(PROGRESS_LOCKED_MESSAGE);
      return;
    }
    const message = validatePatch(patch);
    if (message) {
      setValidationMessage(message);
      return;
    }
    setValidationMessage(null);
    onUpdate(patch as Partial<Target>);
  };

  return (
    <div
      className="mt-4 space-y-2"
      // Focus leaving the editors ends the preview: by then the value has either committed
      // (the store already holds it) or been reverted, so `progress` is the truth again.
      onBlur={() => setPreviewProgress(null)}
    >
      {validationMessage && (
        <p className="text-xs font-medium text-destructive" role="alert">
          {validationMessage}
        </p>
      )}
      {/*
        The ± pair with the **numbers between them**, not a bar. The inner progress bar and its
        percentage used to sit here; the card's own strip already prints the same measure at the
        top, so the row was saying it twice and the values it edits were exiled to a line above.
        Now the thing the buttons act on is the thing between them.
      */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => commitPatch({ current: target.current - 1 })}
          disabled={target.current <= minValue}
          className="gradient-ring h-9 w-9 grid place-items-center rounded-md hover:text-primary disabled:opacity-40"
          aria-label="Decrement"
        >
          <Minus className="h-4 w-4" />
        </button>
        {/* Inline-editable current / total / unit / start — wraps rather than squeezing, the way
            the Android FlowRow does, so seven small pieces still read as one sentence. */}
        <div className="flex-1 flex flex-wrap items-center justify-center gap-x-1 gap-y-0.5 num font-semibold tabular-nums text-sm text-foreground">
          <InlineEditable
            value={String(target.current)}
            numeric
            onChange={(v) => commitPatch({ current: parseFloat(v) })}
            onTyping={(raw) => previewFrom("current", raw)}
            onInvalid={setValidationMessage}
            ariaLabel="Current value"
          />
          <span>/</span>
          <InlineEditable
            value={String(target.total)}
            numeric
            onChange={(v) => commitPatch({ total: parseFloat(v) })}
            onTyping={(raw) => previewFrom("total", raw)}
            onInvalid={setValidationMessage}
            ariaLabel="Total value"
          />
          <InlineEditable
            value={target.unit ?? ""}
            placeholder="unit"
            onChange={(v) =>
              onUpdate({ unit: v || undefined } as Partial<Target>)
            }
            onInvalid={setValidationMessage}
            maxLength={FIELD_LIMITS.targetUnit}
            maxLengthLabel="Unit"
            ariaLabel="Unit"
          />
          {/* `gap-0`, so the closing bracket sits against its number: the row's gap used to fall
              between them and printed "(from 0 )". The space after "from" is written into the
              word instead of coming from the layout. */}
          <div className="text-muted-foreground font-normal text-xs ml-1 flex items-center gap-0 opacity-70 hover:opacity-100 transition-opacity">
            <span>(from&nbsp;</span>
            <InlineEditable
              value={String(target.start ?? 0)}
              numeric
              onChange={(v) => commitPatch({ start: parseFloat(v) })}
              onTyping={(raw) => previewFrom("start", raw)}
              onInvalid={setValidationMessage}
              ariaLabel="Start value"
            />
            <span>)</span>
          </div>
        </div>
        <button
          onClick={() => commitPatch({ current: target.current + 1 })}
          disabled={target.current >= maxValue}
          className="gradient-ring h-9 w-9 grid place-items-center rounded-md hover:text-primary disabled:opacity-40"
          aria-label="Increment"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function InlineEditable({
  value,
  onChange,
  onTyping,
  placeholder,
  ariaLabel,
  numeric,
  onInvalid,
  className,
  maxLength,
  maxLengthLabel = "This field",
}: {
  value: string;
  onChange: (v: string) => void;
  /** Every keystroke, for a live *preview* only — the value still commits on blur/Enter. */
  onTyping?: (raw: string) => void;
  placeholder?: string;
  ariaLabel: string;
  numeric?: boolean;
  onInvalid?: (message: string) => void;
  className?: string;
  maxLength?: number;
  maxLengthLabel?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);

  // Sync from props if not focused to handle external updates safely
  useEffect(() => {
    if (ref.current && document.activeElement !== ref.current) {
      ref.current.textContent = value;
    }
  }, [value]);

  const handleBlur = (e: React.FocusEvent<HTMLSpanElement>) => {
    let text = e.currentTarget.textContent || "";
    if (numeric) {
      text = text.trim();
      if (!text) {
        e.currentTarget.textContent = value;
        onInvalid?.("Value is required.");
        return;
      }
      // Allow decimals typed by hand (e.g. 1.1) — numeric targets are stored as Float on the
      // server; only the ± steppers move in whole units. Reject negatives and non-numbers.
      if (!/^\d+(\.\d+)?$/.test(text)) {
        e.currentTarget.textContent = value;
        onInvalid?.("Enter a non-negative number.");
        return;
      }
    }

    if (maxLength !== undefined && text.length > maxLength) {
      // Over the server limit — revert and report, so nothing invalid reaches the store.
      e.currentTarget.textContent = value;
      onInvalid?.(
        `${maxLengthLabel} must be ${maxLength} characters or fewer.`,
      );
      return;
    }

    if (e.currentTarget.textContent !== text) {
      e.currentTarget.textContent = text;
    }

    // Only trigger onChange if value actually changed
    if (text !== value) {
      onChange(text);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLSpanElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      e.currentTarget.blur();
    }
  };

  return (
    <span
      ref={ref}
      contentEditable
      suppressContentEditableWarning
      role="textbox"
      aria-label={ariaLabel}
      onBlur={handleBlur}
      onInput={(e) => onTyping?.(e.currentTarget.textContent || "")}
      onKeyDown={handleKeyDown}
      data-placeholder={placeholder}
      className={cn(
        "outline-none cursor-text transition-shadow min-w-[1ch] inline-block empty:before:content-[attr(data-placeholder)] empty:before:text-muted-foreground/75",
        className,
      )}
    />
  );
}

const TASKS_MIN_WIDTH = 420;
const TASKS_RESIZE_KEY = "spira:tasks-panel-width";
const TASKS_DEFAULT_WIDTH = 600;

function TasksResizableSheet({
  open,
  onClose,
  items,
  title,
  onChange,
  locked = false,
}: {
  open: boolean;
  onClose: () => void;
  items: {
    id: string;
    text: string;
    done: boolean;
    deadline?: string;
    achievedAt?: string;
  }[];
  title: string;
  onChange: (
    items: {
      id: string;
      text: string;
      done: boolean;
      deadline?: string;
      achievedAt?: string;
    }[],
  ) => void;
  /** The target's progress is pinned — tasks can be renamed but not ticked. */
  locked?: boolean;
}) {
  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return TASKS_DEFAULT_WIDTH;
    const stored = Number(window.localStorage.getItem(TASKS_RESIZE_KEY));
    return stored >= TASKS_MIN_WIDTH ? stored : TASKS_DEFAULT_WIDTH;
  });
  const draggingRef = useRef(false);
  const [isDragging, setIsDragging] = useState(false);
  const handleRef = useRef<HTMLDivElement>(null);
  const isMobile = useIsMobile();
  const compact = isMobile;

  useEffect(() => {
    const onResize = () => setWidth((w) => Math.min(w, window.innerWidth));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(TASKS_RESIZE_KEY, String(width));
  }, [width]);

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    setIsDragging(true);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    const onMove = (ev: PointerEvent) => {
      if (!draggingRef.current) return;
      const next = Math.max(
        TASKS_MIN_WIDTH,
        Math.min(window.innerWidth, window.innerWidth - ev.clientX),
      );
      setWidth(next);
    };
    const onUp = () => {
      draggingRef.current = false;
      setIsDragging(false);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent
        side="right"
        className={cn(
          "p-0 flex flex-col bg-surface border-l hairline !max-w-none",
          isDragging && "[&_iframe]:pointer-events-none",
        )}
        style={{ width: `${width}px` }}
      >
        <div
          ref={handleRef}
          onPointerDown={startDrag}
          className="resize-handle"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize panel"
        />
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Header */}
          <div
            className={cn(
              "flex items-center justify-between bg-surface z-10 shrink-0",
              compact ? "px-3 pt-3 pb-1" : "px-6 pt-5 pb-2",
            )}
          >
            <h3
              className={cn(
                "font-bold truncate flex-1 min-w-0 pr-2",
                compact && "text-sm",
              )}
            >
              {title}
            </h3>
            <button
              onClick={onClose}
              className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary shrink-0"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {/* Scrollable task list */}
          <div
            className={cn(
              "flex-1 overflow-y-auto",
              compact ? "px-2 pt-0" : "px-6 pt-0",
            )}
          >
            {locked && (
              <p className="mb-2 flex items-center gap-2 rounded-md bg-secondary/60 px-3 py-2 text-[13px] text-muted-foreground">
                <LockFilled className="h-3.5 w-3.5 shrink-0" />
                {PROGRESS_LOCKED_MESSAGE} Task names stay editable; ticking,
                adding and removing tasks are paused.
              </p>
            )}
            <ChecklistEditor
              items={items}
              onChange={onChange}
              compact={compact}
              hideCountdown={compact}
              locked={locked}
            />
          </div>

          {/* Pinned to the bottom of the panel, with the panel's own gutters. */}
          <AddTaskControl
            compact={compact}
            className={cn(
              "shrink-0 bg-surface",
              compact ? "px-2 py-2" : "px-4 py-3",
            )}
            onAdd={(text) => {
              if (locked) {
                warnProgressLocked();
                return;
              }
              onChange([
                ...items,
                {
                  id: Math.random().toString(36).slice(2, 9),
                  text,
                  done: false,
                },
              ]);
            }}
          />
        </div>
      </SheetContent>
    </Sheet>
  );
}

type ChecklistItemShape = {
  id: string;
  text: string;
  done: boolean;
  deadline?: string;
  achievedAt?: string;
};

function ChecklistEditor({
  items,
  onChange,
  compact = false,
  hideCountdown = false,
  locked = false,
}: {
  items: ChecklistItemShape[];
  onChange: (items: ChecklistItemShape[]) => void;
  compact?: boolean;
  /** Accepted for call-site symmetry with the compact layouts; the row hides the countdown itself. */
  hideCountdown?: boolean;
  /** Progress is pinned: ticking tasks is refused (with a message); their text stays editable. */
  locked?: boolean;
}) {
  const [lastItemError, setLastItemError] = useState(false);
  return (
    <div className={cn("space-y-0.5", !compact && "mt-4")}>
      {items.map((it) => (
        <ChecklistRow
          key={it.id}
          item={it}
          items={items}
          onChange={onChange}
          compact={compact}
          locked={locked}
          onLastItemError={setLastItemError}
        />
      ))}
      {lastItemError && items.length <= 1 && (
        <p className="flex items-center gap-1.5 mt-1 px-1 text-[13px] font-medium text-destructive">
          <TriangleAlert className="h-3.5 w-3.5 shrink-0" />A checklist must
          have at least one item
        </p>
      )}
    </div>
  );
}

/**
 * One checklist task, in the "Steps" shape the owner asked for: no card, no border — a round
 * check on the left, the text beside it, and the row's controls (deadline, ⋯) on the right. Done
 * tasks grey out and strike through; a resource link inside them never does (see `ResourceLink`).
 */
function ChecklistRow({
  item: it,
  items,
  onChange,
  compact,
  locked,
  onLastItemError,
}: {
  item: ChecklistItemShape;
  items: ChecklistItemShape[];
  onChange: (items: ChecklistItemShape[]) => void;
  compact: boolean;
  locked: boolean;
  onLastItemError: (value: boolean) => void;
}) {
  const { ref: textRef, singleLine } = useIsSingleLine<HTMLDivElement>();
  const overdue = deadlineOverdue(it.deadline, it.done);

  const toggle = () => {
    if (locked) {
      warnProgressLocked();
      return;
    }
    onChange(items.map((i) => (i.id === it.id ? { ...i, done: !i.done } : i)));
  };

  return (
    <div
      id={`task-${it.id}`}
      className={cn(
        // `group` powers the reveal-on-hover ⋯; the row is plain text, not a card.
        "group flex scroll-mt-24 gap-2.5",
        singleLine ? "items-center" : "items-start",
        compact ? "py-1" : "py-1.5",
      )}
    >
      <button
        type="button"
        onClick={toggle}
        role="checkbox"
        aria-checked={it.done}
        aria-label={it.done ? "Mark subtask not done" : "Mark subtask done"}
        className={cn(
          "shrink-0 rounded-full transition-colors",
          !singleLine && "mt-0.5",
          // Guava once done — the accent mark that says "finished", the same warm colour the
          // in-progress bar uses. A small accent mark, not a fill (CLAUDE.md).
          it.done
            ? "text-[#F45D48]"
            : "text-border-strong hover:text-primary/70",
        )}
      >
        {/*
          Gravity's `circle-check` / `circle-check-fill` pair — one control's two states, which is
          the one case a solid glyph may sit beside its outline twin. The filled one knocks the
          tick OUT of the disc, so a Guava tint prints a Guava disc with a white tick through it;
          the old version passed `fill`/`stroke` props that a Gravity path (which sets its own
          `fill="currentColor"`) simply ignores, so a done task looked identical to an open one.
        */}
        {it.done ? (
          <CircleCheckFill
            className={compact ? "h-[18px] w-[18px]" : "h-5 w-5"}
          />
        ) : (
          <CircleCheck className={compact ? "h-[18px] w-[18px]" : "h-5 w-5"} />
        )}
      </button>

      <div ref={textRef} className="min-w-0 flex-1">
        <InlineText
          value={it.text}
          onChange={(text) =>
            onChange(items.map((i) => (i.id === it.id ? { ...i, text } : i)))
          }
          ariaLabel="Edit subtask"
          maxLength={FIELD_LIMITS.checklistText}
          maxLengthLabel="Task"
          className={cn(
            compact ? "text-sm" : "text-[15px]",
            it.done && "line-through text-muted-foreground",
          )}
        />
      </div>

      {/* Deadline and ⋮ are always visible on a task row: with a fixed control column on the
          right there is nothing for them to overlap, and a task is worked on far more often than
          an option or a reality item. */}
      <DeadlinePopover
        iso={it.deadline}
        achievedAt={it.achievedAt}
        completed={it.done}
        variant="icon"
        size="sm"
        hideDaysLeft
        placeholder="Set deadline"
        renderTrigger={() => (
          <span
            className={cn(
              "grid h-6 w-6 shrink-0 cursor-pointer place-items-center rounded-md transition-colors",
              !singleLine && "mt-0.5",
              it.deadline
                ? overdue
                  ? "text-[#D74041]"
                  : "text-primary"
                : "text-muted-foreground/70",
            )}
            title={it.deadline ? "Change the deadline" : "Set a deadline"}
          >
            {it.deadline ? (
              <Calendar className="h-4 w-4" />
            ) : (
              <CalendarPlus className="h-4 w-4" />
            )}
          </span>
        )}
        onChange={(next) =>
          onChange(
            items.map((i) => (i.id === it.id ? { ...i, deadline: next } : i)),
          )
        }
      />

      <ElementActionsMenu
        ariaLabel="Subtask actions"
        deleteLabel="Delete task"
        attachedTo={it.text}
        onDelete={() => {
          if (locked) {
            warnProgressLocked();
            return;
          }
          if (items.length <= 1) {
            onLastItemError(true);
            return;
          }
          onLastItemError(false);
          onChange(items.filter((i) => i.id !== it.id));
        }}
        onAttach={(resourceId) => {
          const next = appendResourceToken(
            it.text,
            resourceId,
            FIELD_LIMITS.checklistText,
          );
          if (next)
            onChange(
              items.map((i) => (i.id === it.id ? { ...i, text: next } : i)),
            );
        }}
        orientation="vertical"
        className={cn("shrink-0 rounded p-1", !singleLine && "mt-0.5")}
        iconClassName="h-3.5 w-3.5"
      />
    </div>
  );
}

/**
 * Adding a task: a circled + and a link, which swaps itself for an input on click. One component
 * behind every entry point (the mobile card, the tasks panel and the create-target sheet) so the
 * layouts can't drift apart again. Enter commits and keeps the field open for the next task;
 * Escape, or leaving it empty, collapses back to the link.
 */
function AddTaskControl({
  onAdd,
  compact = false,
  className,
}: {
  onAdd: (text: string) => void;
  compact?: boolean;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const overBy =
    draft.trim().length > FIELD_LIMITS.checklistText ? draft.trim().length : 0;

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const commit = () => {
    const text = draft.trim();
    if (!text) return;
    if (text.length > FIELD_LIMITS.checklistText) return; // too long — blocked, message shown
    onAdd(text);
    setDraft("");
    inputRef.current?.focus();
  };

  // **The same mark and the same word-size as "Attach resource"** — an 18px circled plus and a
  // 14px semibold teal label, in every layout. The compact (mobile) variant used to shrink both,
  // so on a phone the two rows sat one under the other at visibly different sizes.
  const plus = (
    <CirclePlus className="h-[18px] w-[18px] shrink-0 text-primary" />
  );

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn(
          "flex items-center gap-2 py-1 text-left text-sm font-semibold text-primary transition-colors hover:text-primary/80",
          className,
        )}
      >
        {plus}
        Add task
      </button>
    );
  }

  return (
    <div className={className}>
      <div className="flex items-center gap-2 py-1">
        {plus}
        <input
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              commit();
            }
            if (e.key === "Escape") {
              setDraft("");
              setOpen(false);
            }
          }}
          onBlur={() => {
            if (!draft.trim()) setOpen(false);
          }}
          placeholder="Add task… (Enter to confirm)"
          className={cn(
            "min-w-0 flex-1 border-b border-border bg-transparent pb-1 outline-none transition-colors placeholder:text-muted-foreground/75 focus:border-primary",
            compact ? "text-sm" : "text-base",
          )}
        />
        {draft.trim() && (
          <button
            onMouseDown={(e) => e.preventDefault()} // keep focus so onBlur can't collapse first
            onClick={commit}
            disabled={overBy > 0}
            aria-label="Add"
            title="Press Enter to add"
            className="shrink-0 rounded-full text-primary transition-colors hover:text-primary/80 disabled:opacity-40"
          >
            {/* The **Enter** mark, not a second circled plus: the row already opens with one, and
                the same glyph at both ends of the field read as two different buttons for the
                same job. Gravity `arrow-uturn-cw-down` is the return key. */}
            <ArrowUturnCwDown className="h-[18px] w-[18px]" />
          </button>
        )}
      </div>
      {overBy > 0 && (
        <p
          className="mt-1 text-[13px] font-medium text-destructive"
          role="alert"
        >
          Task is too long — max {FIELD_LIMITS.checklistText} characters (you
          have {overBy}). Trim it to add.
        </p>
      )}
    </div>
  );
}

export function NewTargetSheet({
  goalId,
  open,
  onOpenChange,
}: {
  goalId: string;
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const isMobile = useIsMobile();
  const Body = (
    <NewTargetForm goalId={goalId} onDone={() => onOpenChange(false)} />
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="mt-0 px-0 h-[92vh] max-h-[92vh] flex flex-col">
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        // The head carries its own white X on the teal band; the corner one would sit on it.
        closeButton={false}
        className="w-full sm:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
        <SheetTitle className="sr-only">New target</SheetTitle>
        {Body}
      </SheetContent>
    </Sheet>
  );
}

function NewTargetForm({
  goalId,
  onDone,
}: {
  goalId: string;
  onDone: () => void;
}) {
  const addTarget = useSpira((s) => s.addTarget);
  // **A resource can be attached before the target exists** (owner, 2026-08-20). It used to be
  // reachable only from a card that had already been created, so adding a target with its reading
  // attached took three steps in two places.
  //
  // The form therefore follows the same two-form rule the inline fields do (`Inline.tsx`): while
  // it is being typed a tag reads as the resource's NAME — `Read {{res:Job ad}} first` — and
  // `toStored` maps it back to the id on submit, so a tag naming something that has since gone
  // degrades to plain text instead of writing a dangling reference.
  const resourcesCtx = useInlineResources();
  const resources = resourcesCtx?.resources ?? [];
  const toStored = (text: string) => namesToTokens(text, resources);
  /**
   * Append a resource tag to a draft field, measuring the STORED form against the field's limit —
   * the ids are what the server sees, and the names on screen are a different length.
   */
  const attachTo = (text: string, resourceId: string, limit: number) => {
    // `appendResourceToken` toasts its own "no room" message and answers null; the caller only has
    // to not pretend it worked. Swallowing the null silently is what made the Android form close
    // its picker and change nothing (the BUG-034 class).
    const next = appendResourceToken(toStored(text), resourceId, limit);
    return next === null ? null : tokensToNames(next, resources);
  };
  /** A draft as plain prose — each tag becomes the resource's name, braces and all removed. */
  const readable = (text: string) =>
    stripResourceTokens(toStored(text), (id) => {
      const resource = resources.find((r) => r.id === id);
      return resource ? resourceDisplayName(resource) : "";
    });
  const [type, setType] = useState<"numeric" | "binary" | "checklist">(
    "numeric",
  );
  const [title, setTitle] = useState("");
  const [start, setStart] = useState("0");
  const [total, setTotal] = useState("10");
  const [unit, setUnit] = useState("");
  const [deadline, setDeadline] = useState("");
  const [checklistItems, setChecklistItems] = useState<
    { id: string; text: string; done: boolean; deadline?: string }[]
  >([]);
  const [checklistLastItemError, setChecklistLastItemError] = useState(false);

  const newTaskUid = () => Math.random().toString(36).slice(2, 9);
  const parsedStart = Number(start);
  const parsedTotal = Number(total);
  const numericMessage = (() => {
    if (type !== "numeric") return null;
    if (!start.trim() || !total.trim()) return "Start and target are required.";
    if (
      !/^\d+(\.\d+)?$/.test(start.trim()) ||
      !/^\d+(\.\d+)?$/.test(total.trim())
    ) {
      return "Start and target must be non-negative numbers.";
    }
    if (parsedStart === parsedTotal) {
      return "Start and target must be different.";
    }
    return null;
  })();

  // **Measured on the STORED form, which is what the server sees.** A tag reads as the resource's
  // name on screen and travels as its id, and the two are different lengths — an optimistic
  // `{{res:local-a1b2c3d4}}` is 22 characters where `{{res:CV}}` is 10. Counting the name form let
  // a title that read 200/200 store at 212 and be rejected by the column, which surfaces as the
  // opaque top-of-page sync banner `limits.ts` exists to prevent; and a long-named resource
  // inflated the counter and blocked a title that would have fitted. `Inline.tsx` measures the
  // stored form for the same reason.
  const titleMessage = lengthError(
    toStored(title),
    FIELD_LIMITS.targetTitle,
    "Title",
  );
  const unitMessage = lengthError(unit, FIELD_LIMITS.targetUnit, "Unit");
  // The tasks are checked too: nothing else looks at them between the attach and the submit, so an
  // over-long one would have gone out and been refused with no field to point at.
  const checklistMessage =
    type === "checklist"
      ? (checklistItems
          .map((i) =>
            lengthError(toStored(i.text), FIELD_LIMITS.checklistText, "Task"),
          )
          .find(Boolean) ?? null)
      : null;

  const canSubmit =
    !!title.trim() &&
    !titleMessage &&
    !unitMessage &&
    !checklistMessage &&
    (type !== "checklist" || checklistItems.length >= 1) &&
    (type !== "numeric" || numericMessage === null);

  const submit = () => {
    if (!canSubmit) return;
    const t = toStored(title.trim());
    const dl = deadline ? new Date(deadline).toISOString() : undefined;
    if (type === "numeric") {
      addTarget(goalId, {
        type: "numeric",
        title: t,
        deadline: dl,
        start: parsedStart,
        total: parsedTotal,
        unit: unit || undefined,
      });
    } else if (type === "binary") {
      addTarget(goalId, {
        type: "binary",
        title: t,
        deadline: dl,
        done: false,
      });
    } else {
      addTarget(goalId, {
        type: "checklist",
        title: t,
        deadline: dl,
        items: checklistItems.map((i) => ({ ...i, text: toStored(i.text) })),
      });
    }
    onDone();
  };

  return (
    <>
      <SheetHead title="New target" onClose={onDone} />

      <div className="px-5 pt-4 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0">
        <div>
          <label className="text-sm font-semibold block mb-2">
            Type <span className="text-destructive">*</span>
          </label>
          <div className="space-y-2">
            {(
              [
                {
                  v: "numeric",
                  t: "Numeric",
                  d: "Track a number toward a target (e.g. 12 / 40 apps)",
                },
                {
                  v: "binary",
                  t: "Binary",
                  d: "A single done / not-done outcome",
                },
                {
                  v: "checklist",
                  t: "Checklist",
                  d: "Subtasks with optional deadlines",
                },
              ] as const
            ).map((opt) => (
              <button
                key={opt.v}
                onClick={() => setType(opt.v)}
                className={cn(
                  "w-full text-left flex items-stretch overflow-hidden rounded-md border transition-colors group",
                  type === opt.v
                    ? "border-primary bg-surface"
                    : "border-border bg-surface hover:border-primary/50",
                )}
              >
                <div
                  className={cn(
                    "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
                    type === opt.v
                      ? // Same tint as the selected slot on an Options card — this control is the
                        // same pattern, so it must not read as a different shade of teal.
                        "bg-[oklch(0.95_0.032_180)] border-primary"
                      : "bg-surface border-border group-hover:bg-secondary/50",
                  )}
                >
                  <span
                    className={cn(
                      "h-5 w-5 rounded-full border-2 grid place-items-center transition-colors",
                      type === opt.v
                        ? "border-primary"
                        : "border-border-strong",
                    )}
                  >
                    {type === opt.v && (
                      <span className="h-2.5 w-2.5 rounded-full bg-primary" />
                    )}
                  </span>
                </div>
                <div className="flex-1 px-4 py-3">
                  <span className="block font-semibold text-sm text-foreground">
                    {opt.t}
                  </span>
                  <span className="block text-xs text-muted-foreground mt-0.5">
                    {opt.d}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-sm font-semibold">
              Title <span className="text-destructive">*</span>
            </label>
            {title.length >= FIELD_LIMITS.targetTitle - 20 && (
              <span
                className={cn(
                  "num text-xs tabular-nums",
                  title.length >= FIELD_LIMITS.targetTitle
                    ? "text-destructive font-semibold"
                    : "text-muted-foreground",
                )}
              >
                {title.length}/{FIELD_LIMITS.targetTitle}
              </span>
            )}
          </div>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Outbound applications"
          />
          {titleMessage && (
            <p
              className="text-xs font-medium text-destructive mt-1.5"
              role="alert"
            >
              {titleMessage}
            </p>
          )}
          {/* Renders nothing when there is no goal resource list to pick from, so a form opened
              outside a workspace is unchanged. */}
          <AttachResourceButton
            attachedTo={toStored(title)}
            onAttach={(resourceId) => {
              const next = attachTo(
                title,
                resourceId,
                FIELD_LIMITS.targetTitle,
              );
              if (next !== null) setTitle(next);
            }}
          />
        </div>
        {type === "numeric" && (
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="text-sm font-semibold block mb-1.5">
                Start <span className="text-destructive">*</span>
              </label>
              <Input
                type="number"
                min={0}
                step="any"
                value={start}
                onChange={(e) => setStart(e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">
                Target <span className="text-destructive">*</span>
              </label>
              <Input
                type="number"
                min={0}
                step="any"
                value={total}
                onChange={(e) => setTotal(e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5 text-muted-foreground">
                Unit
              </label>
              <Input
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                placeholder="apps…"
              />
            </div>
            {numericMessage && (
              <p
                className="col-span-3 text-xs font-medium text-destructive"
                role="alert"
              >
                {numericMessage}
              </p>
            )}
            {unitMessage && (
              <p
                className="col-span-3 text-xs font-medium text-destructive"
                role="alert"
              >
                {unitMessage}
              </p>
            )}
          </div>
        )}
        {type === "checklist" && (
          <div>
            <label className="text-sm font-semibold block mb-2">
              Tasks <span className="text-destructive">*</span>
              {checklistItems.length === 0 && (
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  — add at least one
                </span>
              )}
            </label>
            <div className="space-y-1.5">
              {checklistItems.map((item) => (
                <div
                  key={item.id}
                  className="flex items-stretch overflow-hidden rounded-md border border-border bg-surface min-h-[44px]"
                >
                  <div className="w-12 shrink-0 flex items-center justify-center border-r border-border bg-surface">
                    <SquareDashed className="h-4 w-4 text-muted-foreground/50" />
                  </div>
                  <div className="flex-1 flex items-center min-w-0 gap-1 px-3 py-1.5 bg-surface">
                    <span className="flex-1 text-sm text-foreground truncate">
                      {/* The resource's NAME, with no `{{res:…}}` around it. This row is a
                          read-only strip, and the tag syntax belongs in a field being edited —
                          the same rule `useReadableText` follows wherever a value is quoted
                          rather than rendered. */}
                      {readable(item.text)}
                    </span>
                    {/* The paperclip, not the worded link: this row is a compact strip that
                        already carries its own remove control. */}
                    <AttachResourceButton
                      variant="icon"
                      ariaLabel="Attach a resource to this task"
                      attachedTo={toStored(item.text)}
                      onAttach={(resourceId) => {
                        const next = attachTo(
                          item.text,
                          resourceId,
                          FIELD_LIMITS.checklistText,
                        );
                        if (next === null) return;
                        setChecklistItems((prev) =>
                          prev.map((i) =>
                            i.id === item.id ? { ...i, text: next } : i,
                          ),
                        );
                      }}
                    />
                    <button
                      onClick={() => {
                        if (checklistItems.length <= 1) {
                          setChecklistLastItemError(true);
                          return;
                        }
                        setChecklistLastItemError(false);
                        setChecklistItems((prev) =>
                          prev.filter((i) => i.id !== item.id),
                        );
                      }}
                      className="text-muted-foreground hover:text-destructive p-1 rounded shrink-0 transition-colors"
                      aria-label="Remove task"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              ))}
              {checklistLastItemError && checklistItems.length <= 1 && (
                <p className="flex items-center gap-1.5 mt-1 px-1 text-[13px] font-medium text-destructive">
                  <TriangleAlert className="h-3.5 w-3.5 shrink-0" />A checklist
                  must have at least one item
                </p>
              )}
              {checklistMessage && (
                <p
                  className="mt-1 px-1 text-[13px] font-medium text-destructive"
                  role="alert"
                >
                  {checklistMessage}
                </p>
              )}
              <AddTaskControl
                onAdd={(text) =>
                  setChecklistItems((prev) => [
                    ...prev,
                    { id: newTaskUid(), text, done: false },
                  ])
                }
              />
            </div>
          </div>
        )}
        <div>
          <label className="text-sm font-semibold block mb-1.5">
            Deadline{" "}
            <span className="text-muted-foreground font-normal">
              (optional)
            </span>
          </label>
          <DeadlinePopover
            iso={deadline}
            onChange={(next) => setDeadline(next ?? "")}
            variant="input"
            className="w-full justify-start text-left font-normal"
          />
        </div>
      </div>

      <div
        className="shrink-0 bg-surface px-5 pt-3 flex gap-3"
        style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
      >
        <button
          onClick={onDone}
          className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
        >
          Cancel
        </button>
        <button
          onClick={submit}
          disabled={!canSubmit}
          className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 disabled:opacity-40 transition-colors"
        >
          Add target
        </button>
      </div>
    </>
  );
}
