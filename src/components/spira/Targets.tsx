import { useState, useRef, useEffect, useMemo } from "react";
import {
  AlertTriangle,
  Calendar,
  CalendarPlus,
  Check,
  CirclePlus,
  CircleCheck,
  Minus,
  Plus,
  Search,
  SlidersHorizontal,
  SquareDashed,
  Trash2,
  TriangleAlert,
  X,
  Lock,
  LockOpen,
} from "lucide-react";
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
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { ResizableSheet } from "@/components/spira/Resources";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
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
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from "@/components/ui/dropdown-menu";
import { Section } from "@/components/spira/Section";
import { InlineText } from "@/components/spira/Inline";
import {
  AttachResourceButton,
  ElementActionsMenu,
  REVEAL_ON_ROW_ACTIVITY,
  appendResourceToken,
  useIsSingleLine,
  useReadableText,
  useTallText,
} from "@/components/spira/inline-resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { Switch } from "@/components/ui/switch";
import { celebrate } from "@/lib/spira/celebrate";
import {
  useShellFilters,
  type TargetStatusFilter,
} from "@/components/shell/shell-store";

type SortField = "title" | "deadline" | "progress";
type StatusFilter = TargetStatusFilter;

/** Guava-600 — the brand's destructive tone (CLAUDE.md), used for an overdue deadline. */
const OVERDUE_RED = "#EF523C";

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
}: {
  locked: boolean;
  onToggle: (next: boolean) => void;
  className?: string;
  iconClassName?: string;
}) {
  const Icon = locked ? Lock : LockOpen;
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
        "grid h-8 w-8 shrink-0 place-items-center rounded-md transition-colors",
        locked
          ? "text-primary hover:bg-primary-soft"
          : "text-muted-foreground/60 hover:text-foreground",
        className,
      )}
    >
      <Icon className={cn("h-4 w-4", iconClassName)} />
    </button>
  );
}

/** The four states of the deadline tile, as illustrations. Each carries its own outline and
 *  colour, so the tile itself draws no frame. */
const TILE_ART = {
  done: "/images/party-popper.png",
  overdue: "/images/calendar-overdue.png",
  dated: "/images/calendar-date.png",
  empty: "/images/calendar-add.png",
} as const;

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
  const art = done
    ? TILE_ART.done
    : overdue
      ? TILE_ART.overdue
      : info
        ? TILE_ART.dated
        : TILE_ART.empty;

  return (
    <span className="relative block h-16 w-16 shrink-0 cursor-pointer text-center">
      <img
        src={art}
        alt=""
        aria-hidden="true"
        className="h-16 w-16 select-none"
        draggable={false}
      />
      {!done && info && (
        // The date is centred on the PAPER, not on the tile — and the two calendars are drawn at
        // different tilts, so the overdue one needs its own nudge: its page sits ~5% to the left
        // (the badge hangs off the right edge). The date stays black in both: the red badge is
        // what says "overdue", and red digits on a warm page only muddy it.
        <span
          className={cn(
            "absolute inset-x-0 bottom-[4%] flex flex-col items-center leading-none text-foreground",
            overdue && "-translate-x-[3px]",
          )}
        >
          <span className="text-[9px] font-semibold uppercase tracking-wide">
            {info.monthLabel}
          </span>
          <span className="num text-lg font-bold tabular-nums">
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
  const [search, setSearch] = useState("");
  const [deadlineFrom, setDeadlineFrom] = useState("");
  const [deadlineTo, setDeadlineTo] = useState("");
  const [achievedFrom, setAchievedFrom] = useState("");
  const [achievedTo, setAchievedTo] = useState("");
  // The status choice is a stored preference (see shell-store): it survives navigation and
  // reloads, and only changes when the user picks something else.
  const statusFilter = useShellFilters((s) => s.targetStatus);
  const setStatusFilter = useShellFilters((s) => s.setTargetStatus);
  const [sortField, setSortField] = useState<SortField>("deadline");
  const [sortDesc, setSortDesc] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

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

  const isDefaultSort = sortField === "deadline" && !sortDesc;
  // The status is a standing preference, not a filter — only the date ranges light the chip and
  // only they are cleared by "Reset filters".
  const filtersActive =
    !!deadlineFrom || !!deadlineTo || !!achievedFrom || !!achievedTo;
  const hasAnyActive = !!search.trim() || filtersActive || !isDefaultSort;

  const resetFilters = () => {
    setDeadlineFrom("");
    setDeadlineTo("");
    setAchievedFrom("");
    setAchievedTo("");
    // The status filter is deliberately left alone — it is the user's choice, not a filter.
  };

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

    if (achievedFrom || achievedTo) {
      ts = ts.filter((t) => {
        if (!t.achievedAt) return false;
        const d = t.achievedAt.slice(0, 10);
        if (achievedFrom && d < achievedFrom.slice(0, 10)) return false;
        if (achievedTo && d > achievedTo.slice(0, 10)) return false;
        return true;
      });
    }

    if (statusFilter === "done") ts = ts.filter((t) => targetProgress(t) >= 1);
    else if (statusFilter === "not-done")
      ts = ts.filter((t) => targetProgress(t) < 1);

    return ts;
  }, [
    goal.targets,
    search,
    deadlineFrom,
    deadlineTo,
    achievedFrom,
    achievedTo,
    statusFilter,
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
      action={
        <div className="flex items-center gap-2">
          {/* Desktop: search + filters */}
          <div className="hidden sm:flex items-center gap-1.5">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search…"
                className="h-8 pl-8 pr-7 rounded-md border border-border bg-surface text-sm outline-none focus:border-primary w-36 placeholder:text-muted-foreground/75 transition-colors"
              />
              {search && (
                <button
                  onClick={() => setSearch("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label="Clear search"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  className={cn(
                    "h-8 px-2.5 rounded-md border text-sm flex items-center gap-1.5 transition-colors whitespace-nowrap",
                    filtersActive
                      ? "border-primary/40 text-primary bg-primary/5"
                      : "border-border text-muted-foreground hover:text-foreground hover:border-border-strong",
                  )}
                >
                  <SlidersHorizontal className="h-3.5 w-3.5 shrink-0" />
                  <span className="hidden lg:inline text-xs">
                    {filtersActive ? "Filters on" : "Filters"}
                  </span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-72 p-2 space-y-2">
                <DropdownMenuLabel>Deadline range</DropdownMenuLabel>
                <div
                  className="grid grid-cols-2 gap-2 px-2"
                  onClick={(e) => e.stopPropagation()}
                >
                  <DeadlinePopover
                    iso={deadlineFrom || undefined}
                    onChange={(next) => setDeadlineFrom(next ?? "")}
                    variant="button"
                    placeholder="From"
                    hideDaysLeft
                    disableScroll
                    className="h-9 justify-start px-2 text-xs"
                  />
                  <DeadlinePopover
                    iso={deadlineTo || undefined}
                    onChange={(next) => setDeadlineTo(next ?? "")}
                    variant="button"
                    placeholder="To"
                    hideDaysLeft
                    disableScroll
                    className="h-9 justify-start px-2 text-xs"
                  />
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuLabel>Achieved date range</DropdownMenuLabel>
                <div
                  className="grid grid-cols-2 gap-2 px-2"
                  onClick={(e) => e.stopPropagation()}
                >
                  <DeadlinePopover
                    iso={achievedFrom || undefined}
                    onChange={(next) => setAchievedFrom(next ?? "")}
                    variant="button"
                    placeholder="From"
                    hideDaysLeft
                    disableScroll
                    className="h-9 justify-start px-2 text-xs"
                  />
                  <DeadlinePopover
                    iso={achievedTo || undefined}
                    onChange={(next) => setAchievedTo(next ?? "")}
                    variant="button"
                    placeholder="To"
                    hideDaysLeft
                    disableScroll
                    className="h-9 justify-start px-2 text-xs"
                  />
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuLabel>Status</DropdownMenuLabel>
                <DropdownMenuRadioGroup
                  value={statusFilter}
                  onValueChange={(v) => setStatusFilter(v as StatusFilter)}
                >
                  <DropdownMenuRadioItem value="all">All</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="done">
                    Done
                  </DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="not-done">
                    Not done
                  </DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
                {filtersActive && (
                  <>
                    <DropdownMenuSeparator />
                    <button
                      onClick={resetFilters}
                      className="w-full text-left text-xs text-primary hover:text-primary/80 font-semibold px-2 py-1.5 rounded-md hover:bg-primary/5 transition-colors"
                    >
                      Reset filters
                    </button>
                  </>
                )}
              </DropdownMenuContent>
            </DropdownMenu>

            {filtersActive && (
              <button
                onPointerDown={resetFilters}
                onClick={resetFilters}
                className="h-8 w-8 grid place-items-center rounded-md border border-primary/40 text-primary hover:bg-primary/5 transition-colors"
                aria-label="Reset filters"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          {/* Mobile: single icon → drawer with search + sort + filters */}
          <div className="sm:hidden">
            <button
              onClick={() => setMobileOpen(true)}
              className={cn(
                "h-9 w-9 rounded-md border flex items-center justify-center transition-colors",
                hasAnyActive
                  ? "border-primary/40 text-primary bg-primary/5"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
              aria-label="Search, sort and filter targets"
            >
              <SlidersHorizontal className="h-4 w-4" />
            </button>
            <Drawer open={mobileOpen} onOpenChange={setMobileOpen}>
              <DrawerContent className="mt-0 px-0 h-[92vh] max-h-[92vh] flex flex-col">
                <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
                  <h2 className="font-sans font-bold text-lg">
                    Filters & Sort
                  </h2>
                  <button
                    onClick={() => setMobileOpen(false)}
                    className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
                    aria-label="Close"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>

                <div className="overflow-y-auto flex-1 min-h-0 px-6 pt-2 pb-8 space-y-5">
                  {/* Search */}
                  <div className="relative">
                    <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
                    <input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Search targets…"
                      className="h-9 w-full pl-8 pr-7 rounded-md border border-border bg-surface text-sm outline-none focus:border-primary placeholder:text-muted-foreground/75 transition-colors"
                    />
                    {search && (
                      <button
                        onClick={() => setSearch("")}
                        className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    )}
                  </div>

                  {/* Sort by */}
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground mb-1.5 uppercase tracking-wide">
                      Sort by
                    </p>
                    <div className="space-y-0.5">
                      {(
                        [
                          { value: "title", label: "Name" },
                          { value: "deadline", label: "Deadline" },
                          { value: "progress", label: "Progress" },
                        ] as const
                      ).map((opt) => (
                        <button
                          key={opt.value}
                          onClick={() => setSortField(opt.value)}
                          className={cn(
                            "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                            sortField === opt.value
                              ? "bg-primary/10 text-primary font-semibold"
                              : "text-foreground hover:bg-secondary",
                          )}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Direction */}
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground mb-1.5 uppercase tracking-wide">
                      Direction
                    </p>
                    <div className="space-y-0.5">
                      {(
                        [
                          { value: false, label: "Ascending" },
                          { value: true, label: "Descending" },
                        ] as const
                      ).map((opt) => (
                        <button
                          key={String(opt.value)}
                          onClick={() => setSortDesc(opt.value)}
                          className={cn(
                            "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                            sortDesc === opt.value
                              ? "bg-primary/10 text-primary font-semibold"
                              : "text-foreground hover:bg-secondary",
                          )}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Deadline range */}
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground mb-1.5 uppercase tracking-wide">
                      Deadline range
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      <DeadlinePopover
                        iso={deadlineFrom || undefined}
                        onChange={(next) => setDeadlineFrom(next ?? "")}
                        variant="button"
                        placeholder="From"
                        hideDaysLeft
                        disableScroll
                        className="h-9 justify-start px-2 text-xs"
                      />
                      <DeadlinePopover
                        iso={deadlineTo || undefined}
                        onChange={(next) => setDeadlineTo(next ?? "")}
                        variant="button"
                        placeholder="To"
                        hideDaysLeft
                        disableScroll
                        className="h-9 justify-start px-2 text-xs"
                      />
                    </div>
                  </div>

                  {/* Achieved date range */}
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground mb-1.5 uppercase tracking-wide">
                      Achieved date range
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      <DeadlinePopover
                        iso={achievedFrom || undefined}
                        onChange={(next) => setAchievedFrom(next ?? "")}
                        variant="button"
                        placeholder="From"
                        hideDaysLeft
                        disableScroll
                        className="h-9 justify-start px-2 text-xs"
                      />
                      <DeadlinePopover
                        iso={achievedTo || undefined}
                        onChange={(next) => setAchievedTo(next ?? "")}
                        variant="button"
                        placeholder="To"
                        hideDaysLeft
                        disableScroll
                        className="h-9 justify-start px-2 text-xs"
                      />
                    </div>
                  </div>

                  {/* Status */}
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground mb-1.5 uppercase tracking-wide">
                      Status
                    </p>
                    <div className="space-y-0.5">
                      {(
                        [
                          { value: "all", label: "All" },
                          { value: "done", label: "Done" },
                          { value: "not-done", label: "Not done" },
                        ] as const
                      ).map((opt) => (
                        <button
                          key={opt.value}
                          onClick={() => setStatusFilter(opt.value)}
                          className={cn(
                            "w-full text-left text-sm px-2.5 py-2 rounded-md transition-colors",
                            statusFilter === opt.value
                              ? "bg-primary/10 text-primary font-semibold"
                              : "text-foreground hover:bg-secondary",
                          )}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div
                  className="shrink-0 bg-surface px-6 pt-3 flex gap-3"
                  style={{
                    paddingBottom: "max(env(safe-area-inset-bottom), 12px)",
                  }}
                >
                  {hasAnyActive && (
                    <button
                      onClick={() => {
                        setSearch("");
                        resetFilters();
                        setSortField("deadline");
                        setSortDesc(false);
                      }}
                      className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
                    >
                      Reset all
                    </button>
                  )}
                  <button
                    onClick={() => setMobileOpen(false)}
                    className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 transition-colors"
                  >
                    Done
                  </button>
                </div>
              </DrawerContent>
            </Drawer>
          </div>

          <button
            onClick={onNewTarget}
            className="inline-flex items-center px-3 h-9 rounded-md bg-[#ea580c] text-white text-sm font-medium hover:bg-[#ea580c]/90"
          >
            Add target
          </button>
        </div>
      }
    >
      <TargetsList
        goal={processedGoal}
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
  sortField,
  sortDesc,
}: {
  goal: Goal;
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
      {goal.targets.length === 0 && (
        <p className="text-sm text-muted-foreground italic px-1">
          Targets are how you execute. Add a numeric, binary, or checklist
          target.
        </p>
      )}
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

  const SortIcon = ({ field }: { field: string }) => {
    const active = sortField === field;
    return (
      <span
        className={cn(
          "inline-flex flex-col items-center justify-center gap-[3px] ml-1.5",
          !active && "opacity-30 group-hover:opacity-60 transition-opacity",
        )}
      >
        <svg
          width="8"
          height="5"
          viewBox="0 0 8 5"
          className={cn(active && !sortDesc ? "opacity-100" : "opacity-50")}
        >
          <path d="M4 0L8 5H0L4 0Z" fill="currentColor" />
        </svg>
        <svg
          width="8"
          height="5"
          viewBox="0 0 8 5"
          className={cn(active && sortDesc ? "opacity-100" : "opacity-50")}
        >
          <path d="M4 5L0 0H8L4 5Z" fill="currentColor" />
        </svg>
      </span>
    );
  };

  return (
    <div className="spira-target-desktop-table">
      <Table>
        <TableHeader className="bg-muted">
          <TableRow className="border-0 border-b">
            <TableHead
              className="cursor-pointer hover:text-foreground w-[45%] pl-6"
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
                  done ? "hover:bg-[#E0F2F5]" : "hover:bg-[#fff2df]",
                )}
              >
                <TableCell className="pl-6">
                  {/* `w-fit` keeps the padlock hugging the title instead of drifting out to the
                      column's edge; the title still wraps when it runs out of room. */}
                  <div className="flex w-fit max-w-full items-center gap-1">
                    <InlineText
                      value={t.title}
                      onChange={(title) =>
                        updateTarget(goal.id, t.id, { title })
                      }
                      placeholder="Untitled target"
                      ariaLabel="Edit target title"
                      maxLength={FIELD_LIMITS.targetTitle}
                      maxLengthLabel="Target title"
                      className={cn(
                        "block min-w-0 text-sm font-medium text-foreground",
                        ACHIEVED_LINK_TONE(done),
                      )}
                    />
                    {/* The padlock is always visible — it is state, not a hidden action. */}
                    <ProgressLockButton
                      locked={locked}
                      onToggle={(next) =>
                        updateTarget(goal.id, t.id, { progressLocked: next })
                      }
                      className="h-5 w-5 shrink-0"
                      iconClassName="h-3.5 w-3.5"
                    />
                  </div>
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
                          done ? "bg-success" : "bg-[#ea580c]",
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
                        REVEAL_ON_ROW_ACTIVITY,
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
                        progress={targetProgress(target)}
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
      {/* Padlock stuck on the card's corner — always present, never in the content flow. */}
      <ProgressLockButton
        locked={locked}
        onToggle={(next) =>
          onUpdate({ progressLocked: next } as Partial<Target>)
        }
        className="absolute -right-2 -top-2 z-10 h-7 w-7 rounded-full border border-border bg-surface shadow-sm"
        iconClassName="h-3.5 w-3.5"
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
                  ? "text-[#EF523C]"
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
      <div className="h-[5px] w-full overflow-hidden bg-[#EAEAEA]">
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
              progress={progress}
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
          <div className="mt-4 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="h-10 rounded-md border border-border px-5 text-sm font-semibold text-foreground transition-colors hover:bg-secondary/60"
            >
              {/* It collapses the panel; nothing is discarded — every edit here saves as it is
                  made, so "Cancel" promised an undo that never existed. */}
              Close
            </button>
            <button
              type="button"
              onClick={onRemove}
              className="h-10 rounded-md bg-[#222525] px-5 text-sm font-semibold text-white transition-colors hover:bg-[#525257]"
            >
              Delete target
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
  progress,
  locked = false,
  onPreviewProgress,
}: {
  target: Extract<Target, { type: "numeric" }>;
  onUpdate: (patch: Partial<Target>) => void;
  progress: number;
  /** Progress is pinned: the numbers are read-only (the unit and title are not). */
  locked?: boolean;
  /** The typed-but-not-yet-saved progress, so an enclosing card can show the same number
   *  (null once editing ends). */
  onPreviewProgress?: (p: number | null) => void;
}) {
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null,
  );
  // What the bar shows WHILE the user is typing. The value itself still commits on blur/Enter
  // (never per keystroke) — but without this the bar sits still until focus moves, which on a
  // large target reads as "progress is broken": typing 4000 against 1 900 000 changes nothing
  // visible until you tab away.
  const [preview, setPreview] = useState<number | null>(null);
  const setPreviewProgress = (p: number | null) => {
    setPreview(p);
    onPreviewProgress?.(p);
  };
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
  const shownProgress = preview ?? progress;
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
      {/* Inline-editable current / total / unit — centered above the bar */}
      <div className="flex items-center justify-center gap-1 num font-semibold tabular-nums text-sm text-foreground">
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
          className="ml-0.5"
        />
        <div className="text-muted-foreground font-normal text-xs ml-2 flex items-center gap-1 opacity-70 hover:opacity-100 transition-opacity">
          <span>(from</span>
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
      {validationMessage && (
        <p className="text-xs font-medium text-destructive" role="alert">
          {validationMessage}
        </p>
      )}
      {/* Single progress bar with ± controls; percentage sits inline before the + */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => commitPatch({ current: target.current - 1 })}
          disabled={target.current <= minValue}
          className="h-9 w-9 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary disabled:opacity-40"
          aria-label="Decrement"
        >
          <Minus className="h-4 w-4" />
        </button>
        <ProgressBar value={shownProgress} className="flex-1" />
        <span className="num text-xs font-semibold tabular-nums text-foreground/80 min-w-[4ch] text-right">
          {formatPercent(shownProgress, progressSteps(target))}%
        </span>
        <button
          onClick={() => commitPatch({ current: target.current + 1 })}
          disabled={target.current >= maxValue}
          className="h-9 w-9 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary disabled:opacity-40"
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
                <Lock className="h-3.5 w-3.5 shrink-0" />
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
          it.done ? "text-primary" : "text-border-strong hover:text-primary/70",
        )}
      >
        <CircleCheck
          className={compact ? "h-[18px] w-[18px]" : "h-5 w-5"}
          strokeWidth={2}
          fill={it.done ? "currentColor" : "none"}
          stroke={it.done ? "#FFFFFF" : "currentColor"}
        />
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
                  ? "text-[#EF523C]"
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

  const plus = (
    <CirclePlus
      className={cn(
        "shrink-0 text-primary",
        compact ? "h-4 w-4" : "h-[18px] w-[18px]",
      )}
    />
  );

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn(
          "flex items-center gap-2 py-1 text-left font-semibold text-primary transition-colors hover:text-primary/80",
          compact ? "text-sm" : "text-[15px]",
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
            className="shrink-0 rounded-full text-primary transition-colors hover:text-primary/80 disabled:opacity-40"
          >
            <CirclePlus className="h-5 w-5" />
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
        className="w-full sm:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
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

  const titleMessage = lengthError(title, FIELD_LIMITS.targetTitle, "Title");
  const unitMessage = lengthError(unit, FIELD_LIMITS.targetUnit, "Unit");

  const canSubmit =
    !!title.trim() &&
    !titleMessage &&
    !unitMessage &&
    (type !== "checklist" || checklistItems.length >= 1) &&
    (type !== "numeric" || numericMessage === null);

  const submit = () => {
    if (!canSubmit) return;
    const t = title.trim();
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
        items: checklistItems,
      });
    }
    onDone();
  };

  return (
    <>
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
        <h2 className="font-sans font-bold text-lg">New target</h2>
        <button
          type="button"
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="px-6 pt-2 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0">
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
                      {item.text}
                    </span>
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
        className="shrink-0 bg-surface px-6 pt-3 flex gap-3"
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
