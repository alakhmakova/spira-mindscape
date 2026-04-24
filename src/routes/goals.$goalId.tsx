import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  ChevronDown,
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
import { differenceInCalendarDays, format, formatDistanceToNow, formatDistanceToNowStrict, isPast } from "date-fns";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/goals/$goalId")({
  head: ({ params }) => ({
    meta: [
      { title: "Goal workspace — Spira" },
      { name: "description", content: `Structured workspace for goal ${params.goalId}.` },
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
  >(() => [
    { value: goal?.confidence ?? 5, at: new Date().toISOString() },
  ]);

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
    setConfidenceHistory((h) => [{ value: next, at: new Date().toISOString() }, ...h]);
  };

  const jumpToTargets = () => {
    document.getElementById("targets-section")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-6 sm:py-10 space-y-6">
      {/* Top bar — no back link, only actions */}
      <div className="flex items-center justify-end gap-2">
        <button
          onClick={() => openAi({ goalId })}
          className="inline-flex items-center gap-1.5 px-3 h-9 rounded-md border-2 border-primary text-primary text-sm font-semibold hover:bg-primary-soft"
        >
          <Sparkles className="h-3.5 w-3.5" /> Coach this goal
        </button>
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
          className="font-semibold text-2xl text-foreground w-full"
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
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <ProgressKpi value={progress} onJump={jumpToTargets} />
        <ConfidenceKpi
          value={goal.confidence}
          onChange={changeConfidence}
          onOpenHistory={() => setHistoryOpen(true)}
        />
        <DeadlineKpi
          iso={goal.deadline}
          createdAt={goal.createdAt}
          onChange={(next) => updateGoal(goal.id, { deadline: next })}
        />
      </div>


      <Section
        title="Reality"
        hint="Where are you now?"
        action={
          <button
            onClick={() => openAi({ goalId })}
            className="link-action text-sm font-medium inline-flex items-center gap-1"
          >
            <Sparkles className="h-3.5 w-3.5" /> Coach
          </button>
        }
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-0 rounded-lg overflow-hidden border hairline">
          <div className="p-5 sm:p-6 bg-[#e5f4f3] md:border-r hairline">
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
              onUpdate={(id, t) => updateReality(goal.id, "obstacles", id, t)}
              onRemove={(id) => removeReality(goal.id, "obstacles", id)}
              marker="warn"
              tone="warning"
            />
          </div>
        </div>
      </Section>

      <Section
        title="Resources"
        hint="Notes, links, files, contacts"
        count={goal.resources.length}
        action={
          <button
            onClick={() => setNewResource(true)}
            className="inline-flex items-center gap-1.5 px-3 h-9 rounded-md border-2 border-primary text-primary text-sm font-semibold hover:bg-primary-soft"
          >
            <Plus className="h-3.5 w-3.5" /> Add resource
          </button>
        }
      >
        <ResourcesList goal={goal} />
      </Section>

      <Section title="Options" hint="Strategies — pick one to commit" count={goal.options.length}>
        <OptionsList goal={goal} />
      </Section>

      <div id="targets-section">
        <Section
          title="Targets"
          hint="How you execute"
          count={goal.targets.length}
          action={
            <button
              onClick={() => setNewTarget(true)}
              className="inline-flex items-center gap-1.5 px-3 h-9 rounded-md bg-primary text-primary-foreground text-sm font-semibold hover:bg-primary/90"
            >
              <Plus className="h-3.5 w-3.5" /> Add target
            </button>
          }
        >
          <TargetsList goal={goal} />
        </Section>
      </div>

      <NewTargetSheet goalId={goal.id} open={newTarget} onOpenChange={setNewTarget} />
      <NewResourceSheet goalId={goal.id} open={newResource} onOpenChange={setNewResource} />

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
  );
}

/* ────────────────  KPI cards  ──────────────── */

function KpiCard({
  label,
  children,
  hint,
  footer,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
  /** Always-visible footer element (link/button) below the hint */
  footer?: React.ReactNode;
}) {
  return (
    <div className="surface-card p-5 sm:p-6 flex flex-col gap-4 min-h-[180px]">
      <h3 className="font-display text-lg">{label}</h3>
      <div className="flex-1 flex items-center">{children}</div>
      <div className="space-y-2">
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
        {footer}
      </div>
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
          className={cn(
            "w-full inline-flex items-center justify-center gap-2 h-10 rounded-md text-sm font-semibold",
            "bg-primary-soft text-primary border border-primary/30 hover:bg-primary-soft/80 transition-colors",
          )}
        >
          Jump to targets <ArrowUpRight className="h-4 w-4" />
        </button>
      }
    >
      <div className="flex items-center justify-start w-full">
        <CircularProgress value={value} />
      </div>
    </KpiCard>
  );
}

function CircularProgress({ value, size = 72 }: { value: number; size?: number }) {
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
        <span className="num tabular-nums text-base font-bold">{Math.round(pct * 100)}%</span>
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
          className={cn(
            "w-full inline-flex items-center justify-center gap-2 h-10 rounded-md text-sm font-semibold",
            "bg-primary-soft text-primary border border-primary/30 hover:bg-primary-soft/80 transition-colors",
          )}
        >
          <History className="h-3.5 w-3.5" />
          Confidence history
        </button>
      }
    >
      <div className="flex flex-col gap-3 w-full mt-2">
        <button 
          onClick={() => setIsEditing(!isEditing)}
          className="flex items-baseline gap-1.5 num tabular-nums justify-start text-left hover:opacity-80 transition-opacity focus:outline-none"
        >
          <span className="text-5xl font-bold tracking-tight text-foreground">{value}</span>
          <span className="text-2xl text-muted-foreground font-medium">/10</span>
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
                    : "bg-surface border-border hover:border-primary/50 text-foreground"
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
  createdAt,
  onChange,
}: {
  iso?: string;
  createdAt: string;
  onChange: (next: string | undefined) => void;
}) {
  const date = iso ? new Date(iso) : undefined;
  const days = date ? differenceInCalendarDays(date, new Date()) : null;
  const overdue = !!date && isPast(date) && (days ?? 0) < 0;
  
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
      label={date ? "Deadline" : "Created"}
      hint={date ? "Click to change or remove" : "Click to set a deadline"}
      footer={
        <DeadlinePopover 
          iso={iso} 
          onChange={onChange} 
          variant="button" 
          placeholder="Set deadline" 
          hideDaysLeft 
          disableScroll
          className="w-full"
        />
      }
    >
      <div className="num tabular-nums w-full">
        {date ? (
          <div className="flex items-baseline gap-1.5">
            <span className={cn("text-5xl font-bold tracking-tight", overdue ? "text-destructive" : "text-foreground")}>
              {Math.abs(days ?? 0)}
            </span>
            <span className={cn("text-sm font-medium", overdue ? "text-destructive" : "text-muted-foreground")}>
              {overdue ? "days overdue" : days === 0 ? "today" : "days left"}
            </span>
          </div>
        ) : (
          <div className="flex items-baseline gap-1.5">
            <span className="text-5xl font-bold tracking-tight text-foreground">
              {distanceValue}
            </span>
            <span className="text-sm text-muted-foreground font-medium">
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
        <div className="px-7 py-5 border-b hairline flex items-center justify-between sticky top-0 bg-surface z-10">
          <div>
            <h2 className="font-sans font-bold text-lg">Confidence history</h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              Current: <span className="num font-semibold text-foreground">{current}/10</span>
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
        <div className="flex-1 overflow-y-auto px-7 py-6 space-y-3">
          {history.length === 0 && (
            <p className="text-sm text-muted-foreground italic">No changes yet.</p>
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
                    <span className="text-muted-foreground font-normal">/10</span>
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
