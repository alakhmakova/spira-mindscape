import { useState, useRef, useEffect, useMemo } from "react";
import {
  AlertTriangle,
  Calendar,
  Check,
  Minus,
  Plus,
  Search,
  SlidersHorizontal,
  SquareDashed,
  Trash2,
  TriangleAlert,
  X,
} from "lucide-react";
import type { Goal, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { targetProgress } from "@/lib/spira/progress";
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
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";

type SortField = "title" | "deadline" | "progress";
type StatusFilter = "all" | "done" | "not-done";

const OVERDUE_RED = "#d13239";

function formatDeadlineInfo(iso: string | undefined, completed = false) {
  if (!iso) return null;
  const deadline = new Date(iso);
  const now = new Date();
  const deadlineDay = new Date(deadline.getFullYear(), deadline.getMonth(), deadline.getDate());
  const todayDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffDays = Math.round((deadlineDay.getTime() - todayDay.getTime()) / 86_400_000);
  const isOverdue = !completed && diffDays < 0;
  const dateStr = deadline.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
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
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [sortField, setSortField] = useState<SortField>("deadline");
  const [sortDesc, setSortDesc] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const isDefaultSort = sortField === "deadline" && !sortDesc;
  const filtersActive =
    !!deadlineFrom || !!deadlineTo || !!achievedFrom || !!achievedTo || statusFilter !== "all";
  const hasAnyActive = !!search.trim() || filtersActive || !isDefaultSort;

  const resetFilters = () => {
    setDeadlineFrom("");
    setDeadlineTo("");
    setAchievedFrom("");
    setAchievedTo("");
    setStatusFilter("all");
  };

  const processedTargets = useMemo(() => {
    let ts = [...goal.targets];

    if (search.trim()) {
      const q = search.toLowerCase();
      ts = ts.filter((t) => t.title.toLowerCase().includes(q));
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
    else if (statusFilter === "not-done") ts = ts.filter((t) => targetProgress(t) < 1);

    return ts;
  }, [goal.targets, search, deadlineFrom, deadlineTo, achievedFrom, achievedTo, statusFilter]);

  const processedGoal = useMemo(
    () => ({ ...goal, targets: processedTargets }),
    [goal, processedTargets],
  );

  return (
    <Section
      title="Will do"
      count={goal.targets.length}
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
                  <DropdownMenuRadioItem value="done">Done</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="not-done">Not done</DropdownMenuRadioItem>
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
                  <h2 className="font-sans font-bold text-lg">Filters & Sort</h2>
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
                  style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
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
            className="inline-flex items-center px-3 h-9 rounded-md bg-[#ea580c] text-white text-sm font-semibold hover:bg-[#ea580c]/90"
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
        const aHas = !!a.deadline, bHas = !!b.deadline;
        if (!aHas && !bHas) return 0;
        if (!aHas) return 1;
        if (!bHas) return -1;
        const cmp = new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime();
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
      {goal.targets.length > 0 && (
        <DesktopTargetsTable goal={goal} />
      )}
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
  const [internalSortField, setInternalSortField] = useState<SortField>("deadline");
  const [internalSortDesc, setInternalSortDesc] = useState(false);
  const [editingTasksFor, setEditingTasksFor] = useState<string | null>(null);
  const [editingNumericFor, setEditingNumericFor] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<Target | null>(null);

  const isControlled = externalSortField !== undefined;
  const sortField = isControlled ? externalSortField! : internalSortField;
  const sortDesc = isControlled ? (externalSortDesc ?? false) : internalSortDesc;

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
          const aHas = !!a.deadline, bHas = !!b.deadline;
          if (!aHas && !bHas) return 0;
          if (!aHas) return 1;
          if (!bHas) return -1;
          const cmp = new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime();
          return sortDesc ? -cmp : cmp;
        }
        let cmp = 0;
        if (sortField === "title") cmp = a.title.localeCompare(b.title);
        else if (sortField === "progress") cmp = targetProgress(a) - targetProgress(b);
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
              <div className="flex items-center" title="Deadline or Completed date">
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
            <TableHead className="w-[10%] text-right pr-6">Delete</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {displayTargets.map((t) => {
            const progress = targetProgress(t);
            const done = progress >= 1;
            return (
              <TableRow
                key={t.id}
                id={`target-desktop-${t.id}`}
                className={cn(
                  "scroll-mt-24 transition-colors bg-white",
                  done ? "hover:bg-[#e5f4f3]" : "hover:bg-[#fff2df]",
                )}
              >
                <TableCell className="pl-6">
                  <InlineText
                    value={t.title}
                    onChange={(title) => updateTarget(goal.id, t.id, { title })}
                    placeholder="Untitled target"
                    ariaLabel="Edit target title"
                    className={cn(
                      "block w-full font-medium text-sm",
                      done ? "line-through text-muted-foreground" : "text-foreground",
                    )}
                  />
                </TableCell>
                <TableCell>
                  <span
                    title={
                      (done ? t.achievedAt : t.deadline)
                        ? done ? "Completed" : "Deadline"
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
                        <button title="Click" className="flex items-center gap-2 group h-8">
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
                            updateTarget(goal.id, t.id, { done: false })
                          }
                          className="text-sm"
                        >
                          Not done
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() =>
                            updateTarget(goal.id, t.id, { done: true })
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
                          done ? "bg-success" : "bg-[#B8A9D4]",
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
                      {Math.round(progress * 100)}%
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right pr-6">
                  <button
                    onClick={() => setConfirmTarget(t)}
                    className="text-foreground opacity-100 hover:text-destructive p-1.5 rounded-md hover:bg-secondary transition-colors inline-flex"
                    title="Delete target"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
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
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Delete this target?"
      description={`Are you sure you want to permanently delete "${target?.title ?? "this target"}"? Progress and checklist tasks inside it will be removed. You can't undo this.`}
      confirmLabel="Yes, delete"
      cancelLabel="No, go back"
      onConfirm={onConfirm}
    />
  );
}

export function TargetRow({
  target,
  onUpdate,
  onRemove,
}: {
  target: Target;
  onUpdate: (patch: Partial<Target>) => void;
  onRemove: () => void;
}) {
  const progress = targetProgress(target);
  const done = progress >= 1;

  const displayIso = done && target.achievedAt ? target.achievedAt : target.deadline;
  const deadlineInfo = formatDeadlineInfo(displayIso, done);

  return (
    <li
      id={`target-mobile-${target.id}`}
      className={cn(
        "surface-card scroll-mt-24 overflow-hidden",
        done && "!bg-[#e5f4f3] !border-[#b8dad8]",
      )}
    >
      <div className="p-4 sm:p-5">
        {/* Top row: deadline/completed + trash */}
        <div className="flex items-start justify-between gap-3 mb-2.5">
          <DeadlinePopover
            iso={target.deadline}
            achievedAt={target.achievedAt}
            completed={done}
            onChange={(next) => onUpdate({ deadline: next } as Partial<Target>)}
            renderTrigger={() =>
              deadlineInfo ? (
                <span
                  className="inline-flex items-center gap-1.5 text-xs font-medium cursor-pointer hover:opacity-70 transition-opacity"
                  style={{
                    color: done
                      ? "var(--color-success)"
                      : deadlineInfo.isOverdue
                        ? OVERDUE_RED
                        : "var(--color-muted-foreground)",
                  }}
                >
                  {done ? (
                    <Check className="h-3 w-3 shrink-0" strokeWidth={3} />
                  ) : deadlineInfo.isOverdue ? (
                    <AlertTriangle className="h-3 w-3 shrink-0" />
                  ) : (
                    <Calendar className="h-3 w-3 shrink-0 opacity-70" />
                  )}
                  {done ? "Completed" : "Deadline"} {deadlineInfo.dateStr}
                  {!done && (
                    <>
                      <span className="w-px h-3 bg-border/60 shrink-0" />
                      <span
                        className={cn(
                          "font-semibold",
                          deadlineInfo.isOverdue ? "text-[#d13239]" : "text-foreground/70",
                        )}
                      >
                        {deadlineInfo.countdown}
                      </span>
                    </>
                  )}
                </span>
              ) : (
                <span className="text-xs text-muted-foreground/60 cursor-pointer hover:text-muted-foreground transition-colors">
                  Set deadline
                </span>
              )
            }
          />
          <button
            onClick={onRemove}
            className="shrink-0 text-muted-foreground hover:text-destructive p-2 -m-1 rounded-md hover:bg-secondary"
            aria-label="Delete target"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>

        {/* Title */}
        <InlineText
          value={target.title}
          onChange={(title) => onUpdate({ title } as Partial<Target>)}
          ariaLabel="Edit target title"
          className={cn(
            "block w-full text-base font-semibold",
            done ? "line-through text-muted-foreground" : "text-foreground",
          )}
        />

        {/* Body */}
        {target.type === "numeric" && (
          <NumericBody target={target} onUpdate={onUpdate} progress={progress} />
        )}

        {target.type === "binary" && (
          <button
            onClick={() => onUpdate({ done: !target.done } as Partial<Target>)}
            className={cn(
              "mt-4 flex items-stretch overflow-hidden rounded-md border transition-colors min-h-[44px] w-full",
              target.done ? "border-primary" : "border-border hover:border-primary/50",
            )}
          >
            <div
              className={cn(
                "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
                target.done
                  ? "bg-primary-soft border-primary"
                  : "bg-surface border-border hover:bg-secondary/50",
              )}
            >
              <div
                className={cn(
                  "h-4 w-4 rounded-sm border-2 grid place-items-center transition-colors",
                  target.done ? "bg-primary border-primary" : "border-border-strong",
                )}
              >
                {target.done && (
                  <Check className="h-3 w-3 text-primary-foreground" strokeWidth={3} />
                )}
              </div>
            </div>
            <div className="flex-1 flex items-center px-3 bg-surface">
              <span
                className={cn(
                  "text-sm",
                  target.done ? "text-muted-foreground" : "text-foreground font-medium",
                )}
              >
                {target.done ? "Done" : "Mark done"}
              </span>
            </div>
          </button>
        )}

        {target.type === "checklist" && (
          <>
            <ChecklistEditor
              items={target.items}
              onChange={(items) => onUpdate({ items } as Partial<Target>)}
              compact
              hideCountdown
            />
            <div className="mt-1">
              <NewTaskInlineInput
                onAdd={(text) =>
                  onUpdate({
                    items: [
                      ...target.items,
                      { id: Math.random().toString(36).slice(2, 9), text, done: false },
                    ],
                  } as Partial<Target>)
                }
              />
            </div>
            <div className="mt-4 flex items-center gap-3">
              <ProgressBar value={progress} className="flex-1" />
              <span className="num text-xs text-muted-foreground font-semibold">
                {Math.round(progress * 100)}%
              </span>
            </div>
          </>
        )}
      </div>
    </li>
  );
}

function NumericBody({
  target,
  onUpdate,
  progress,
}: {
  target: Extract<Target, { type: "numeric" }>;
  onUpdate: (patch: Partial<Target>) => void;
  progress: number;
}) {
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null,
  );
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
    const message = validatePatch(patch);
    if (message) {
      setValidationMessage(message);
      return;
    }
    setValidationMessage(null);
    onUpdate(patch as Partial<Target>);
  };

  return (
    <div className="mt-4 space-y-2">
      {/* Inline-editable current / total / unit — centered above the bar */}
      <div className="flex items-center justify-center gap-1 num font-semibold tabular-nums text-sm text-foreground">
        <InlineEditable
          value={String(target.current)}
          numeric
          onChange={(v) => commitPatch({ current: parseInt(v, 10) })}
          onInvalid={setValidationMessage}
          ariaLabel="Current value"
        />
        <span>/</span>
        <InlineEditable
          value={String(target.total)}
          numeric
          onChange={(v) => commitPatch({ total: parseInt(v, 10) })}
          onInvalid={setValidationMessage}
          ariaLabel="Total value"
        />
        <InlineEditable
          value={target.unit ?? ""}
          placeholder="unit"
          onChange={(v) =>
            onUpdate({ unit: v || undefined } as Partial<Target>)
          }
          ariaLabel="Unit"
          className="ml-0.5"
        />
        <div className="text-muted-foreground font-normal text-xs ml-2 flex items-center gap-1 opacity-70 hover:opacity-100 transition-opacity">
          <span>(from</span>
          <InlineEditable
            value={String(target.start ?? 0)}
            numeric
            onChange={(v) => commitPatch({ start: parseInt(v, 10) })}
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
        <ProgressBar value={progress} className="flex-1" />
        <span className="num text-xs font-semibold tabular-nums text-foreground/80 min-w-[3ch] text-right">
          {Math.round(progress * 100)}%
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
  placeholder,
  ariaLabel,
  numeric,
  onInvalid,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  ariaLabel: string;
  numeric?: boolean;
  onInvalid?: (message: string) => void;
  className?: string;
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
      if (!/^\d+$/.test(text)) {
        e.currentTarget.textContent = value;
        onInvalid?.("Enter a non-negative whole number.");
        return;
      }
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
            <ChecklistEditor
              items={items}
              onChange={onChange}
              compact={compact}
              hideCountdown={compact}
            />
          </div>

          {/* Sticky bottom input — chat-style */}
          <ChecklistAddInput
            compact={compact}
            onAdd={(text) =>
              onChange([
                ...items,
                {
                  id: Math.random().toString(36).slice(2, 9),
                  text,
                  done: false,
                },
              ])
            }
          />
        </div>
      </SheetContent>
    </Sheet>
  );
}

function ChecklistEditor({
  items,
  onChange,
  compact = false,
  hideCountdown = false,
}: {
  items: {
    id: string;
    text: string;
    done: boolean;
    deadline?: string;
    achievedAt?: string;
  }[];
  onChange: (
    items: {
      id: string;
      text: string;
      done: boolean;
      deadline?: string;
      achievedAt?: string;
    }[],
  ) => void;
  compact?: boolean;
  hideCountdown?: boolean;
}) {
  const [lastItemError, setLastItemError] = useState(false);
  return (
    <div className={cn("space-y-1", !compact && "mt-4")}>
      {items.map((it) => (
        <div
          id={`task-${it.id}`}
          key={it.id}
          className={cn(
            "flex scroll-mt-24 items-stretch overflow-hidden rounded-md border transition-colors group/task",
            compact ? "min-h-[40px]" : "min-h-[44px]",
            it.done
              ? "border-primary"
              : "border-border hover:border-primary/50",
          )}
        >
          <button
            onClick={() =>
              onChange(
                items.map((i) =>
                  i.id === it.id ? { ...i, done: !i.done } : i,
                ),
              )
            }
            className={cn(
              "shrink-0 flex items-center justify-center border-r transition-colors",
              compact ? "w-10" : "w-12",
              it.done
                ? "bg-primary-soft border-primary"
                : "bg-surface border-border hover:bg-secondary/50",
            )}
          >
            <div
              className={cn(
                "h-4 w-4 rounded-sm border-2 grid place-items-center transition-colors",
                it.done ? "bg-primary border-primary" : "border-border-strong",
              )}
            >
              {it.done && (
                <Check
                  className="h-3 w-3 text-primary-foreground"
                  strokeWidth={3}
                />
              )}
            </div>
          </button>

          <div
            className={cn(
              "flex-1 flex items-center min-w-0 gap-1 relative bg-surface",
              compact ? "px-2 py-1" : "px-3 py-1.5",
            )}
          >
            <InlineText
              value={it.text}
              onChange={(text) =>
                onChange(
                  items.map((i) => (i.id === it.id ? { ...i, text } : i)),
                )
              }
              ariaLabel="Edit subtask"
              className={cn(
                "flex-1 text-sm truncate",
                it.done && "line-through text-muted-foreground",
              )}
            />
            {/* Fixed-width deadline column – keeps icons aligned */}
            <div
              className={cn(
                "shrink-0 flex items-center",
                compact ? "w-7" : "w-[6.5rem]",
              )}
            >
              <DeadlinePopover
                iso={it.deadline}
                achievedAt={it.achievedAt}
                completed={it.done}
                variant={compact ? "icon" : "icon-text"}
                size="sm"
                hideDaysLeft
                placeholder="Set deadline"
                onChange={(next) =>
                  onChange(
                    items.map((i) =>
                      i.id === it.id ? { ...i, deadline: next } : i,
                    ),
                  )
                }
              />
            </div>

            <button
              onClick={() => {
                if (items.length <= 1) { setLastItemError(true); return; }
                setLastItemError(false);
                onChange(items.filter((i) => i.id !== it.id));
              }}
              className="text-muted-foreground hover:text-destructive p-1 rounded hover:bg-secondary shrink-0"
              aria-label="Remove subtask"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      ))}
      {lastItemError && items.length <= 1 && (
        <p className="flex items-center gap-1.5 mt-1 px-1 text-[13px] font-medium text-destructive">
          <TriangleAlert className="h-3.5 w-3.5 shrink-0" />
          A checklist must have at least one item
        </p>
      )}
    </div>
  );
}

/** Sticky chat-style input pinned to the bottom of the tasks panel */
function ChecklistAddInput({
  compact,
  onAdd,
}: {
  compact: boolean;
  onAdd: (text: string) => void;
}) {
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const commit = () => {
    const text = draft.trim();
    if (!text) return;
    onAdd(text);
    setDraft("");
    inputRef.current?.focus();
  };

  return (
    <div
      className={cn("shrink-0 bg-surface", compact ? "px-2 py-2" : "px-4 py-3")}
    >
      <div
        className={cn(
          "flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary",
        )}
      >
        {/* Plus icon left column */}
        <div
          className={cn(
            "shrink-0 flex items-center justify-center border-r border-border bg-secondary/30",
            compact ? "w-10" : "w-12",
          )}
        >
          <Plus className="h-4 w-4 text-muted-foreground" />
        </div>

        {/* Text input */}
        <div
          className={cn(
            "flex-1 flex items-center gap-2 px-3 py-1",
            compact ? "py-1" : "py-1",
          )}
        >
          <input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") commit();
            }}
            placeholder="Add task…"
            className={cn(
              "flex-1 bg-transparent outline-none placeholder:text-muted-foreground/75",
              compact ? "text-sm min-h-[36px]" : "text-base min-h-[40px]",
            )}
          />
          {draft.trim() && (
            <button
              onClick={commit}
              className="ml-1 rounded-md bg-primary/10 px-2 py-1 text-sm font-semibold text-primary hover:bg-primary/20 shrink-0"
            >
              Add
            </button>
          )}
        </div>
      </div>
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
    if (!/^\d+$/.test(start.trim()) || !/^\d+$/.test(total.trim())) {
      return "Start and target must be non-negative whole numbers.";
    }
    if (parsedStart === parsedTotal) {
      return "Start and target must be different.";
    }
    return null;
  })();

  const canSubmit =
    !!title.trim() &&
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
                      ? "bg-primary-soft border-primary"
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
          <label className="text-sm font-semibold block mb-1.5">
            Title <span className="text-destructive">*</span>
          </label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Outbound applications"
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
                step={1}
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
                step={1}
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
                        if (checklistItems.length <= 1) { setChecklistLastItemError(true); return; }
                        setChecklistLastItemError(false);
                        setChecklistItems((prev) => prev.filter((i) => i.id !== item.id));
                      }}
                      className="text-muted-foreground hover:text-destructive p-1 rounded hover:bg-secondary shrink-0 transition-colors"
                      aria-label="Remove task"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              ))}
              {checklistLastItemError && checklistItems.length <= 1 && (
                <p className="flex items-center gap-1.5 mt-1 px-1 text-[13px] font-medium text-destructive">
                  <TriangleAlert className="h-3.5 w-3.5 shrink-0" />
                  A checklist must have at least one item
                </p>
              )}
              <NewTaskInlineInput
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

function NewTaskInlineInput({ onAdd }: { onAdd: (text: string) => void }) {
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const commit = () => {
    if (!draft.trim()) return;
    onAdd(draft.trim());
    setDraft("");
    inputRef.current?.focus();
  };

  return (
    <div className="flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary min-h-[40px]">
      <div className="w-10 shrink-0 flex items-center justify-center border-r border-border bg-secondary/30">
        <Plus className="h-4 w-4 text-muted-foreground" />
      </div>
      <div className="flex-1 flex items-center px-3 py-1 gap-2">
        <input
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              commit();
            }
          }}
          placeholder="Add task… (Enter to confirm)"
          className="flex-1 bg-transparent outline-none text-sm placeholder:text-muted-foreground/75 min-h-[38px]"
        />
        {draft.trim() && (
          <button
            onClick={commit}
            className="shrink-0 rounded-md bg-primary/10 px-2 py-1 text-sm font-semibold text-primary hover:bg-primary/20 transition-colors"
          >
            Add
          </button>
        )}
      </div>
    </div>
  );
}
