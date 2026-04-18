import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { ArrowLeft, Plus, Sparkles, Trash2 } from "lucide-react";
import { useSpira } from "@/lib/spira/store";
import { goalProgress } from "@/lib/spira/progress";
import { ConfidenceStepper, ConfidencePill } from "@/components/spira/Confidence";
import { ProgressBar } from "@/components/spira/ProgressBar";
import { DeadlineLabel } from "@/components/spira/DeadlineLabel";
import { Section } from "@/components/spira/Section";
import { InlineList, AutoTextarea } from "@/components/spira/Inline";
import { OptionsList } from "@/components/spira/OptionsList";
import { TargetsList, NewTargetSheet } from "@/components/spira/Targets";
import { ResourcesList, NewResourceSheet } from "@/components/spira/Resources";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { useAi } from "@/components/ai/ai-store";
import { Input } from "@/components/ui/input";
import type { Confidence } from "@/lib/spira/types";

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
    <div className="mx-auto max-w-3xl px-4 sm:px-6 py-6 sm:py-10 space-y-6">
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

      {/* Header card */}
      <header className="surface-card p-6 sm:p-8 space-y-6">
        <div>
          <div className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold mb-2">
            Goal
          </div>
          <AutoTextarea
            value={goal.title}
            onChange={(v) => updateGoal(goal.id, { title: v })}
            className="font-display text-3xl sm:text-5xl leading-tight"
            placeholder="Untitled goal"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-6 md:items-start pt-4 border-t hairline">
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted-foreground uppercase tracking-wider font-semibold">
                Progress
              </span>
              <span className="num font-semibold tabular-nums">
                {Math.round(progress * 100)}%
              </span>
            </div>
            <ProgressBar value={progress} />
            <div className="flex items-center gap-3 pt-2 flex-wrap">
              <DeadlineLabel iso={goal.deadline} />
              <Input
                type="date"
                value={goal.deadline ? goal.deadline.slice(0, 10) : ""}
                onChange={(e) =>
                  updateGoal(goal.id, {
                    deadline: e.target.value ? new Date(e.target.value).toISOString() : undefined,
                  })
                }
                className="h-8 w-[160px] bg-surface text-xs num border-2 border-border focus-visible:border-primary"
              />
            </div>
          </div>
          <div className="md:w-80 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground uppercase tracking-wider font-semibold">
                Confidence
              </span>
              <ConfidencePill value={goal.confidence} />
            </div>
            <ConfidenceStepper
              value={goal.confidence}
              onChange={(v) => setConfidence(goal.id, v as Confidence)}
            />
          </div>
        </div>
      </header>

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
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
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
          <div>
            <h3 className="font-display text-lg mb-3">Obstacles</h3>
            <InlineList
              items={goal.reality.obstacles}
              emptyHint="What's standing in the way?"
              placeholder="Add an obstacle…"
              onAdd={(t) => addReality(goal.id, "obstacles", t)}
              onUpdate={(id, t) => updateReality(goal.id, "obstacles", id, t)}
              onRemove={(id) => removeReality(goal.id, "obstacles", id)}
              marker="dot"
              tone="warning"
            />
          </div>
        </div>
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
        defaultOpen={false}
      >
        <ResourcesList goal={goal} />
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
