import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { useEffect, useState, useRef } from "react";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  History,
  Plus,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { goalProgress } from "@/lib/spira/progress";
import { ProgressBar } from "@/components/spira/ProgressBar";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import { Section } from "@/components/spira/Section";
import { InlineList, AutoTextarea } from "@/components/spira/Inline";
import { OptionsList } from "@/components/spira/OptionsList";
import { TargetsList, NewTargetSheet } from "@/components/spira/Targets";
import { ResourcesList, NewResourceSheet } from "@/components/spira/Resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { useAi } from "@/components/ai/ai-store";
import type { Confidence } from "@/lib/spira/types";
import {
  differenceInCalendarDays,
  format,
  formatDistanceToNow,
  formatDistanceToNowStrict,
  isPast,
} from "date-fns";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/goals/$goalId")({
  head: ({ params }) => ({
    meta: [
      { title: "Goal workspace — Spira" },
      {
        name: "description",
        content: `Structured workspace for goal ${params.goalId}.`,
      },
    ],
  }),
  component: GoalWorkspace,
});

function GoalWorkspace() {
  const { goalId } = Route.useParams();
  const router = useRouter();
  const goal = useSpira((s) => s.goals.find((g) => g.id === goalId));
  const {
    updateGoal,
    setConfidence,
    deleteGoal,
    addReality,
    updateReality,
    removeReality,
  } = useSpira();
  const { open: openAi, setContext } = useAi();
  const [newTarget, setNewTarget] = useState(false);
  const [newResource, setNewResource] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  // Local confidence history (mocked, in-memory for demo).
  const [confidenceHistory, setConfidenceHistory] = useState<
    { value: number; at: string }[]
  >(() => [{ value: goal?.confidence ?? 5, at: new Date().toISOString() }]);

  useEffect(() => {
    setContext({ goalId });
    return () => setContext({});
  }, [goalId, setContext]);

  if (!goal) {
    return (
      <div className="mx-auto max-w-3xl px-4 sm:px-6 py-20 text-center">
        <h1 className="font-display text-4xl">Goal not found</h1>
        <Link to="/" className="link-action mt-4 inline-block font-semibold">
          Back to goals
        </Link>
      </div>
    );
  }

  const progress = goalProgress(goal);

  const changeConfidence = (next: number) => {
    setConfidence(goal.id, next as Confidence);
    setConfidenceHistory((h) => [
      { value: next, at: new Date().toISOString() },
      ...h,
    ]);
  };

  const jumpToTargets = () => {
    document.getElementById("targets-section")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  return (
    <>
      <GoalNav />
      <div
        id="goal-top"
        className="spira-goal-workspace mx-auto max-w-7xl scroll-mt-32 px-4 sm:px-6 lg:px-8 py-6 sm:py-10 space-y-6"
      >
        {/* Top bar: back link + delete */}
        <div className="flex items-center justify-between">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-sm text-primary hover:underline transition-colors font-medium"
          >
            <ChevronLeft className="h-4 w-4 shrink-0" />
            Back to All goals
          </Link>
          <button
            onClick={() => setConfirmDelete(true)}
            className="h-9 w-9 grid place-items-center rounded-md text-muted-foreground hover:text-destructive hover:bg-secondary border-2 border-transparent hover:border-destructive/30"
            aria-label="Delete goal"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>

        {/* Title and Description */}
        <header>
          <AutoTextarea
            value={goal.title}
            onChange={(v) => updateGoal(goal.id, { title: v })}
            className="font-heading text-3xl sm:text-4xl text-foreground w-full leading-tight"
            placeholder="Untitled goal"
          />
          <AutoTextarea
            value={goal.description}
            onChange={(v) => updateGoal(goal.id, { description: v })}
            placeholder="Specific, measurable, achievable, relevant, time-bound."
            className="text-muted-foreground mt-1.5 text-sm sm:text-[15px] w-full"
          />
        </header>

        {/* Three KPI cards: Progress · Confidence · Deadline */}
        <div className="spira-kpi-grid grid gap-4">
          <ProgressKpi value={progress} onJump={jumpToTargets} />
          <ConfidenceKpi
            value={goal.confidence}
            onChange={changeConfidence}
            onOpenHistory={() => setHistoryOpen(true)}
          />
          <DeadlineKpi
            iso={goal.deadline}
            achievedAt={goal.achievedAt}
            completed={progress >= 1}
            createdAt={goal.createdAt}
            onChange={(next) => updateGoal(goal.id, { deadline: next })}
          />
        </div>

        <div id="reality-section" className="scroll-mt-32">
          <Section
            title="Reality"
            hint="Where are you now?"
            action={
              <button
                onClick={() => openAi({ goalId })}
                className="text-primary hover:text-primary/80 text-sm font-semibold inline-flex items-center gap-1 transition-colors"
              >
                <Sparkles className="h-3.5 w-3.5" /> Coach
              </button>
            }
          >
            <div className="spira-reality-grid grid gap-0 rounded-lg overflow-hidden border hairline">
              <div className="spira-reality-primary p-5 sm:p-6 bg-[#e5f4f3] hairline">
                <h3 className="font-display text-lg mb-3">Actions taken</h3>
                <InlineList
                  items={goal.reality.actions}
                  emptyHint="Nothing yet — what have you tried?"
                  placeholder="Add an action you've taken…"
                  onAdd={(t) => addReality(goal.id, "actions", t)}
                  onUpdate={(id, t) => updateReality(goal.id, "actions", id, t)}
                  onRemove={(id) => removeReality(goal.id, "actions", id)}
                  marker="check"
                />
              </div>
              <div className="p-5 sm:p-6 bg-[#fff2df]">
                <h3 className="font-display text-lg mb-3">Obstacles</h3>
                <InlineList
                  items={goal.reality.obstacles}
                  emptyHint="What's standing in the way?"
                  placeholder="Add an obstacle…"
                  onAdd={(t) => addReality(goal.id, "obstacles", t)}
                  onUpdate={(id, t) =>
                    updateReality(goal.id, "obstacles", id, t)
                  }
                  onRemove={(id) => removeReality(goal.id, "obstacles", id)}
                  marker="warn"
                  tone="warning"
                />
              </div>
            </div>
          </Section>
        </div>

        <div id="resources-section" className="scroll-mt-32">
          <Section
            title="Resources"
            hint="Notes, links, files, emails"
            count={goal.resources.length}
            action={
              <button
                onClick={() => setNewResource(true)}
                className="inline-flex items-center px-3 h-9 rounded-md border-2 border-primary text-primary text-sm font-semibold hover:bg-primary-soft"
              >
                Add resource
              </button>
            }
          >
            <ResourcesList goal={goal} />
          </Section>
        </div>

        <div id="options-section" className="scroll-mt-32">
          <Section
            title="Options"
            hint="Strategies — pick one to commit"
            count={goal.options.length}
          >
            <OptionsList goal={goal} />
          </Section>
        </div>

        <div id="targets-section" className="scroll-mt-32">
          <Section
            title="Will do"
            hint="How you execute"
            count={goal.targets.length}
            action={
              <button
                onClick={() => setNewTarget(true)}
                className="inline-flex items-center px-3 h-9 rounded-md bg-[#ea580c] text-white text-sm font-semibold hover:bg-[#ea580c]/90"
              >
                Add target
              </button>
            }
          >
            <TargetsList goal={goal} />
          </Section>
        </div>

        <NewTargetSheet
          goalId={goal.id}
          open={newTarget}
          onOpenChange={setNewTarget}
        />
        <NewResourceSheet
          goalId={goal.id}
          open={newResource}
          onOpenChange={setNewResource}
        />

        <ConfidenceHistorySheet
          open={historyOpen}
          onOpenChange={setHistoryOpen}
          history={confidenceHistory}
          current={goal.confidence}
        />

        <ConfirmDialog
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          title="Delete this goal?"
          description={`Are you sure you want to permanently delete "${goal.title}"? Everything inside it — targets, options, resources — will be removed. You can't undo this.`}
          confirmLabel="Yes, delete"
          cancelLabel="No, go back"
          onConfirm={() => {
            deleteGoal(goal.id);
            router.navigate({ to: "/" });
          }}
        />
      </div>
    </>
  );
}

/* ────────────────  KPI cards  ──────────────── */

function KpiCard({
  label,
  children,
  hint,
  footer,
  headerAction,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  headerAction?: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "p-5 sm:p-6 flex flex-col min-h-[140px] rounded-lg border transition-colors duration-200 cursor-default relative",
        "bg-white border-border/80 hover:bg-[#f4fbfc] hover:border-[#4fa8a3]/50",
      )}
    >
      <div className="flex justify-between items-start mb-3">
        <h3 className="font-bold text-[15px] text-foreground/90">{label}</h3>
        {headerAction}
      </div>
      <div className="flex-1 flex flex-col justify-center">
        {children}
        {hint && (
          <div className="text-[13px] text-muted-foreground mt-1">{hint}</div>
        )}
      </div>
      <div className="mt-4">{footer}</div>
    </div>
  );
}

function ProgressKpi({ value, onJump }: { value: number; onJump: () => void }) {
  const pct = Math.round(value * 100);
  return (
    <KpiCard
      label="Progress"
      hint="Across all targets"
      footer={
        <button
          onClick={onJump}
          className="text-primary font-semibold text-[13px] hover:underline inline-flex items-center gap-0.5"
        >
          Jump to targets <ChevronRight className="h-3.5 w-3.5" />
        </button>
      }
    >
      <div className="flex items-baseline gap-1.5 leading-none w-full">
        <span className="num tabular-nums text-5xl font-bold tracking-tight text-foreground/90">
          {pct}
        </span>
        <span className="text-base text-muted-foreground/60 font-medium">
          % completed
        </span>
      </div>
    </KpiCard>
  );
}

function CircularProgress({
  value,
  size = 72,
}: {
  value: number;
  size?: number;
}) {
  const stroke = 8;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(1, value));
  const dash = c * pct;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="var(--color-secondary)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="var(--color-primary)"
          strokeWidth={stroke}
          strokeDasharray={`${dash} ${c - dash}`}
          strokeLinecap="round"
          className="transition-[stroke-dasharray] duration-500"
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center">
        <span className="num tabular-nums text-base font-bold">
          {Math.round(pct * 100)}%
        </span>
      </div>
    </div>
  );
}

function ConfidenceKpi({
  value,
  onChange,
  onOpenHistory,
}: {
  value: number;
  onChange: (v: number) => void;
  onOpenHistory: () => void;
}) {
  const [isEditing, setIsEditing] = useState(false);

  return (
    <KpiCard
      label="Confidence"
      hint="Current confidence level"
      footer={
        <button
          onClick={onOpenHistory}
          className="text-primary font-semibold text-[13px] hover:underline inline-flex items-center gap-0.5"
        >
          Confidence history <ChevronRight className="h-3.5 w-3.5" />
        </button>
      }
      headerAction={
        isEditing && (
          <button
            onClick={() => setIsEditing(false)}
            className="h-6 w-6 -mt-1 -mr-1 grid place-items-center rounded-md hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors"
            title="Cancel editing"
          >
            <X className="h-4 w-4" />
          </button>
        )
      }
    >
      <div className="flex flex-col w-full">
        <button
          onClick={() => setIsEditing(!isEditing)}
          className="flex items-baseline gap-1.5 num tabular-nums justify-start text-left hover:opacity-80 transition-opacity focus:outline-none"
        >
          <span className="text-5xl font-bold tracking-tight text-foreground/90 leading-none">
            {value}
          </span>
          <span className="text-base text-muted-foreground/60 font-medium">
            / 10
          </span>
        </button>
        {isEditing && (
          <div className="flex justify-between w-full mt-1 gap-1">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
              <button
                key={n}
                onClick={() => {
                  onChange(n);
                  setIsEditing(false);
                }}
                className={cn(
                  "flex-1 h-8 rounded-sm border text-xs font-semibold transition-colors",
                  value === n
                    ? "bg-primary border-primary text-primary-foreground"
                    : "bg-surface border-border hover:border-primary/50 text-foreground",
                )}
              >
                {n}
              </button>
            ))}
          </div>
        )}
      </div>
    </KpiCard>
  );
}

function DeadlineKpi({
  iso,
  achievedAt,
  completed = false,
  createdAt,
  onChange,
}: {
  iso?: string;
  achievedAt?: string;
  completed?: boolean;
  createdAt: string;
  onChange: (next: string | undefined) => void;
}) {
  const displayIso = completed && achievedAt ? achievedAt : iso;
  const date = displayIso ? new Date(displayIso) : undefined;
  const days = date ? differenceInCalendarDays(date, new Date()) : null;
  const overdue = !!date && !completed && isPast(date) && (days ?? 0) < 0;

  let distanceValue = "";
  let distanceUnit = "";
  if (!date) {
    const createdDate = new Date(createdAt);
    const distanceStr = formatDistanceToNowStrict(createdDate);
    const [val, ...unitParts] = distanceStr.split(" ");
    distanceValue = val;
    distanceUnit = unitParts.join(" ");
  }

  return (
    <KpiCard
      label={completed && date ? "Achieved" : date ? "Deadline" : "Created"}
      hint={date ? "Click to change or remove" : "Click to set a deadline"}
      footer={
        <DeadlinePopover
          iso={iso}
          achievedAt={achievedAt}
          completed={completed}
          onChange={onChange}
          variant="text"
          placeholder={
            <>
              Set deadline <ChevronRight className="h-3.5 w-3.5" />
            </>
          }
          hideDaysLeft
          disableScroll
          className="text-primary font-semibold text-[13px] hover:underline inline-flex items-center gap-0.5 outline-none"
        />
      }
    >
      <div className="num tabular-nums w-full">
        {date ? (
          <div className="flex items-baseline gap-1.5 leading-none">
            <span
              className={cn(
                "text-5xl font-bold tracking-tight",
                overdue ? "text-destructive" : "text-foreground/90",
              )}
            >
              {Math.abs(days ?? 0)}
            </span>
            <span
              className={cn(
                "text-base font-medium",
                overdue ? "text-destructive" : "text-muted-foreground/60",
              )}
            >
              {completed
                ? "achieved"
                : overdue
                  ? "days overdue"
                  : days === 0
                    ? "today"
                    : "days left"}
            </span>
          </div>
        ) : (
          <div className="flex items-baseline gap-1.5 leading-none">
            <span className="text-5xl font-bold tracking-tight text-foreground/90">
              {distanceValue}
            </span>
            <span className="text-base text-muted-foreground/60 font-medium">
              {distanceUnit} ago
            </span>
          </div>
        )}
      </div>
    </KpiCard>
  );
}

/* ────────────────  Confidence history side panel  ──────────────── */

function ConfidenceHistorySheet({
  open,
  onOpenChange,
  history,
  current,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  history: { value: number; at: string }[];
  current: number;
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-md p-0 flex flex-col bg-surface border-l hairline"
      >
        <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
          <div>
            <h2 className="font-sans font-bold text-lg">Confidence history</h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              Current:{" "}
              <span className="num font-semibold text-foreground">
                {current}/10
              </span>
            </p>
          </div>
          <button
            onClick={() => onOpenChange(false)}
            className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-7 pt-2 pb-6 space-y-3">
          {history.length === 0 && (
            <p className="text-sm text-muted-foreground italic">
              No changes yet.
            </p>
          )}
          {history.map((h, i) => {
            const prev = history[i + 1];
            const delta = prev ? h.value - prev.value : 0;
            return (
              <div
                key={`${h.at}-${i}`}
                className="flex items-center justify-between gap-3 p-3 rounded-md border hairline bg-surface-sunken/50"
              >
                <div>
                  <div className="num tabular-nums text-base font-semibold">
                    {h.value}
                    <span className="text-muted-foreground font-normal">
                      /10
                    </span>
                  </div>
                  <div className="text-[11px] text-muted-foreground mt-0.5">
                    {format(new Date(h.at), "MMM d, yyyy · HH:mm")} ·{" "}
                    {formatDistanceToNow(new Date(h.at), { addSuffix: true })}
                  </div>
                </div>
                {delta !== 0 && (
                  <span
                    className={cn(
                      "inline-flex items-center gap-1 text-xs font-semibold num tabular-nums px-2 py-0.5 rounded",
                      delta > 0
                        ? "text-success bg-success/10"
                        : "text-destructive bg-destructive/10",
                    )}
                  >
                    {delta > 0 ? (
                      <ArrowUp className="h-3 w-3" />
                    ) : (
                      <ArrowDown className="h-3 w-3" />
                    )}
                    {Math.abs(delta)}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function GoalNav() {
  const [scroll, setScroll] = useState(0);
  const [active, setActive] = useState("Goal");
  const isManualRef = useRef(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const onScroll = () => {
      const winScroll = window.scrollY;
      const height = document.documentElement.scrollHeight - window.innerHeight;
      const scrolled = height > 0 ? (winScroll / height) * 100 : 0;
      setScroll(scrolled);

      // If we clicked a link, don't let scroll position override the highlight
      if (isManualRef.current) return;

      const sections = [
        { id: "goal-top", label: "Goal" },
        { id: "reality-section", label: "Reality" },
        { id: "resources-section", label: "Resources" },
        { id: "options-section", label: "Options" },
        { id: "targets-section", label: "Will do" },
      ];

      if (
        winScroll + window.innerHeight >=
        document.documentElement.scrollHeight - 20
      ) {
        setActive(sections[sections.length - 1].label);
        return;
      }

      const threshold = 120;
      const current = [...sections].reverse().find((s) => {
        const el = document.getElementById(s.id);
        if (!el) return false;
        const rect = el.getBoundingClientRect();
        return rect.top <= threshold;
      });

      if (current) setActive(current.label);
    };

    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const items = [
    { label: "Goal", id: "goal-top" },
    { label: "Reality", id: "reality-section" },
    { label: "Resources", id: "resources-section" },
    { label: "Options", id: "options-section" },
    { label: "Will do", id: "targets-section" },
  ];

  const scrollTo = (id: string, label: string) => {
    // Set active state immediately
    setActive(label);
    isManualRef.current = true;

    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => {
      isManualRef.current = false;
    }, 1000);

    const el = document.getElementById(id);
    if (el) {
      const yOffset = -112;
      const y = el.getBoundingClientRect().top + window.scrollY + yOffset;
      window.scrollTo({ top: y, behavior: "smooth" });
    }
  };

  return (
    <div className="sticky top-16 z-20 bg-background/95 backdrop-blur w-full border-b hairline">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-center gap-8 h-12">
          {items.map((item) => (
            <button
              key={item.id}
              onClick={() => scrollTo(item.id, item.label)}
              className={cn(
                "text-[13px] font-medium transition-colors",
                active === item.label
                  ? "text-[#ea580c]"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div className="h-[5px] w-full bg-[#dcfce7] overflow-hidden">
        <div
          className="h-full bg-[#ea580c] transition-all duration-100 ease-out"
          style={{ width: `${scroll}%` }}
        />
      </div>
    </div>
  );
}
