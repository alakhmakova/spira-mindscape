import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { ArrowLeft, Plus, Sparkles, Trash2, ChevronUp, ChevronDown } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidencePill } from "@/components/spira/Confidence";
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
import { differenceInCalendarDays, isPast } from "date-fns";

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

  return (
    <div className="mx-auto max-w-5xl px-4 sm:px-6 py-6 sm:py-10 space-y-6">
      {/* Top bar */}
      <div className="flex items-center justify-between">
        <Link
          to="/"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground font-medium"
        >
          <ArrowLeft className="h-4 w-4" /> Back to goals
        </Link>
        <div className="flex items-center gap-2">
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
      </div>

      {/* Title */}
      <header className="space-y-2">
        <div className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
          Goal
        </div>
        <AutoTextarea
          value={goal.title}
          onChange={(v) => updateGoal(goal.id, { title: v })}
          className="font-display text-3xl sm:text-5xl leading-tight"
          placeholder="Untitled goal"
        />
      </header>

      {/* Three KPI cards: Progress · Confidence · Deadline (dashboard style) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <ProgressKpi value={progress} />
        <ConfidenceKpi
          value={goal.confidence}
          onChange={(v) => setConfidence(goal.id, v as Confidence)}
        />
        <DeadlineKpi
            iso={goal.deadline}
            onChange={(next) => updateGoal(goal.id, { deadline: next })}
        />
      </div>

      <Section title="Description" hint="SMART description">
        <AutoTextarea
          value={goal.description}
          onChange={(v) => updateGoal(goal.id, { description: v })}
          placeholder="Specific, measurable, achievable, relevant, time-bound."
          className="text-base leading-relaxed"
        />
      </Section>

      <Section
        title="Reality"
        hint="Where you are now"
        count={goal.reality.actions.length + goal.reality.obstacles.length}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-0 rounded-lg overflow-hidden border hairline">
          {/* Left half — Actions taken (default surface) */}
          <div className="p-5 sm:p-6 bg-surface md:border-r hairline">
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
          {/* Right half — Obstacles (deep teal background, light text) */}
          <div className="p-5 sm:p-6 bg-primary text-primary-foreground">
            <h3 className="font-display text-lg mb-3 text-primary-foreground">Obstacles</h3>
            <InlineList
              items={goal.reality.obstacles}
              emptyHint="What's standing in the way?"
              placeholder="Add an obstacle…"
              onAdd={(t) => addReality(goal.id, "obstacles", t)}
              onUpdate={(id, t) => updateReality(goal.id, "obstacles", id, t)}
              onRemove={(id) => removeReality(goal.id, "obstacles", id)}
              marker="dot"
              tone="warning"
              variant="onPrimary"
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

      <NewTargetSheet goalId={goal.id} open={newTarget} onOpenChange={setNewTarget} />
      <NewResourceSheet goalId={goal.id} open={newResource} onOpenChange={setNewResource} />

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

/* ────────────────  KPI cards (dashboard.png style)  ──────────────── */

function KpiCard({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="surface-card p-5 sm:p-6 flex flex-col gap-3 min-h-[160px]">
      <div>
        <h3 className="font-display text-lg">{label}</h3>
        {hint && <p className="text-xs text-muted-foreground mt-0.5">{hint}</p>}
      </div>
      <div className="flex-1 flex flex-col justify-end">{children}</div>
    </div>
  );
}

function ProgressKpi({ value }: { value: number }) {
  return (
    <KpiCard label="Progress" hint="Across all targets">
      <div className="flex items-baseline gap-1.5 num tabular-nums">
        <span className="text-5xl font-bold tracking-tight text-foreground">
          {Math.round(value * 100)}
        </span>
        <span className="text-2xl text-muted-foreground font-medium">%</span>
      </div>
      <ProgressBar value={value} className="mt-3" />
    </KpiCard>
  );
}

function ConfidenceKpi({
  value,
  onChange,
}: {
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <KpiCard label="Confidence" hint="Tap arrows to change · 1–10">
      <div className="flex items-end justify-between gap-3">
        <div className="flex items-baseline gap-1.5 num tabular-nums">
          <span className="text-5xl font-bold tracking-tight text-foreground">{value}</span>
          <span className="text-2xl text-muted-foreground font-medium">/10</span>
        </div>
        <div className="flex flex-col gap-1">
          <button
            onClick={() => onChange(Math.min(10, value + 1))}
            disabled={value >= 10}
            className="h-7 w-7 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary disabled:opacity-30"
            aria-label="Increase confidence"
          >
            <ChevronUp className="h-4 w-4" />
          </button>
          <button
            onClick={() => onChange(Math.max(1, value - 1))}
            disabled={value <= 1}
            className="h-7 w-7 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary disabled:opacity-30"
            aria-label="Decrease confidence"
          >
            <ChevronDown className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div className="mt-3">
        <ConfidencePill value={value} />
      </div>
    </KpiCard>
  );
}

function DeadlineKpi({
  iso,
  onChange,
}: {
  iso?: string;
  onChange: (next: string | undefined) => void;
}) {
  const date = iso ? new Date(iso) : undefined;
  const days = date ? differenceInCalendarDays(date, new Date()) : null;
  const overdue = !!date && isPast(date) && (days ?? 0) < 0;
  return (
    <KpiCard label="Deadline" hint="Click to change or remove">
      <div className="num tabular-nums">
        {date ? (
          <>
            <div className="flex items-baseline gap-1.5">
              <span className="text-5xl font-bold tracking-tight text-foreground">
                {Math.abs(days ?? 0)}
              </span>
              <span className="text-sm text-muted-foreground font-medium">
                {overdue ? "days overdue" : days === 0 ? "today" : "days left"}
              </span>
            </div>
          </>
        ) : (
          <div className="text-2xl font-semibold text-muted-foreground">No deadline</div>
        )}
      </div>
      <div className="mt-3">
        <DeadlinePopover iso={iso} onChange={onChange} size="md" />
      </div>
    </KpiCard>
  );
}
