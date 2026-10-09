import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import {
  Loader,
  Trophy,
  Cable,
  GlobeOff,
  RefreshCw,
} from "@/components/spira/icons";
import { TabBar } from "@/components/spira/TabBar";
import { GoalCard } from "@/components/spira/GoalCard";
import { FilteredEmptyNotice } from "@/components/spira/Notice";
import { GoalsTable } from "@/components/spira/GoalsTable";
import { NewGoalSheet } from "@/components/spira/NewGoalSheet";
import { Upcoming } from "@/components/spira/Upcoming";
import { useAuth } from "@/lib/spira/auth";
import { greeting } from "@/lib/spira/greeting";
import {
  GOALS_SCOPE,
  useShellFilters,
  useViewField,
} from "@/components/shell/shell-store";
import { goalProgress } from "@/lib/spira/progress";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "All goals - Spira" },
      {
        name: "description",
        content:
          "Your goals workspace. See progress, deadlines, and confidence at a glance, then dive into structured execution.",
      },
    ],
  }),
  component: GoalsOverview,
});

function GoalsOverview() {
  const goals = useSpira((s) => s.goals);
  const isLoading = useSpira((s) => s.isLoading);
  const hasLoaded = useSpira((s) => s.hasLoaded);
  const syncError = useSpira((s) => s.syncError);
  const syncErrorKind = useSpira((s) => s.syncErrorKind);
  const refreshGoals = useSpira((s) => s.refreshGoals);
  const query = useShellFilters((s) => s.query);
  const viewMode = useShellFilters((s) => s.viewMode);
  const setViewMode = useShellFilters((s) => s.setViewMode);
  // The All-goals list is the app's own — the three lists inside a goal each answer for
  // themselves, keyed by that goal's id. See `shell-store.ts` → Scope.
  const sort = useViewField("sort", GOALS_SCOPE);
  const sortDirection = useViewField("sortDirection", GOALS_SCOPE);
  const deadlineFrom = useViewField("deadlineFrom", GOALS_SCOPE);
  const deadlineTo = useViewField("deadlineTo", GOALS_SCOPE);
  const confidence = useViewField("confidence", GOALS_SCOPE);
  const status = useViewField("status", GOALS_SCOPE);
  const goalDeadline = useViewField("goalDeadline", GOALS_SCOPE);
  const [open, setOpen] = useState(false);
  const userName = useAuth((s) => s.user?.name);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    let arr = goals.filter((goal) => goal.title.toLowerCase().includes(q));

    // Whether there is a deadline at all, before asking which dates to keep.
    if (goalDeadline !== "all") {
      arr = arr.filter((goal) =>
        goalDeadline === "has" ? !!goal.deadline : !goal.deadline,
      );
    }

    if (deadlineFrom || deadlineTo) {
      arr = arr.filter((goal) => {
        if (!goal.deadline) return false;
        return (
          (!deadlineFrom || goal.deadline >= deadlineFrom) &&
          (!deadlineTo || goal.deadline <= deadlineTo)
        );
      });
    }

    if (confidence) {
      arr = arr.filter((goal) => goal.confidence === Number(confidence));
    }

    if (status !== "all") {
      arr = arr.filter((goal) =>
        status === "achieved"
          ? goalProgress(goal) >= 1
          : goalProgress(goal) < 1,
      );
    }

    const sorted = [...arr].sort((a, b) => {
      let result = 0;
      switch (sort) {
        case "deadline":
          result = (a.deadline ?? "9999").localeCompare(b.deadline ?? "9999");
          break;
        case "progress":
          result = goalProgress(a) - goalProgress(b);
          break;
        case "confidence":
          result = a.confidence - b.confidence;
          break;
        case "title":
          result = a.title.localeCompare(b.title);
          break;
        default:
          result = a.createdAt.localeCompare(b.createdAt);
      }
      return sortDirection === "asc" ? result : -result;
    });

    return sorted;
  }, [
    goals,
    query,
    sort,
    sortDirection,
    deadlineFrom,
    deadlineTo,
    confidence,
    status,
    goalDeadline,
  ]);

  const filteredGoalIds = useMemo(
    () => new Set(filtered.map((goal) => goal.id)),
    [filtered],
  );

  return (
    // **No page fill.** This carried a hardcoded `bg-[#F4F4F3]/80` — Salt-300 at 80% — painted
    // straight into the markup, past every token. It is what actually made the app read grey: the
    // shell's `bg-background` underneath was already white, and this covered it (owner,
    // 2026-08-21). White is the canvas; the cards are told apart by their hairline.
    <div className="relative min-h-screen">
      {/* pb-24 (not py-*'s bottom) clears the fixed "New goal" FAB (h-14, bottom-5/-7): without
          it, the last card in a short list sits directly under the circle and the FAB covers its
          "Start" control (BUG-072, mobile viewport). */}
      <div className="spira-overview-inner mx-auto max-w-6xl space-y-8 px-4 pt-8 pb-24 sm:px-6 sm:pt-12">
        {/**
         * **The greeting, then the list under its own heading** (owner, 2026-10-09, after
         * `gusto/upcomingsynccalendar.png`). "All goals" was a title naming the page the user had
         * just opened; the greeting is what that reference opens with instead, and the list gets a
         * plain section heading — "Goals" where the reference says "Tasks".
         *
         * **Both in the BODY face, not the heading serif**, at the reference's sizes: the greeting
         * 26px and the section heading 20px, both medium. Measured on its cap heights (19px and
         * 15px at a 0.7 cap ratio — Montserrat's is the same), so the sizes hold for the face the
         * owner is trying out.
         */}
        <header className="spira-overview-header">
          <h1 className="text-[26px] font-medium leading-tight text-foreground">
            {greeting(userName)}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground sm:text-[15px]">
            {goals.length} {goals.length === 1 ? "goal" : "goals"} in motion.
            Pick one to dive into, or shape a new one.
          </p>
        </header>

        {/* Two columns from `lg`: the goals, and Upcoming beside them at the reference's
            proportions (about two to one). Below `lg` Upcoming follows the list. Timeline is a
            wide table, so it keeps the whole width. */}
        <div
          className={cn(
            "grid grid-cols-1 gap-10",
            viewMode === "cards" &&
              "lg:grid-cols-[minmax(0,1fr)_minmax(260px,340px)] lg:gap-9",
          )}
        >
          <div className="min-w-0 space-y-5">
            <div className="flex min-h-11 flex-wrap items-center justify-between gap-x-6 gap-y-3">
              <h2 className="text-xl font-medium leading-tight text-foreground">
                {viewMode === "cards" ? "Goals" : "Timeline"}
              </h2>
              {/* **Tabs, not a segmented control** — the same row Android draws on a goal
                  (`GrowTabsRow`): the word alone, with a Guava underline on the current one. */}
              <TabBar
                className="shrink-0"
                items={[
                  {
                    label: "Cards",
                    active: viewMode === "cards",
                    onSelect: () => setViewMode("cards"),
                  },
                  {
                    label: "Timeline",
                    active: viewMode === "table",
                    onSelect: () => setViewMode("table"),
                  },
                  { label: "Calendar", to: "/calendar" },
                ]}
              />
            </div>
            {viewMode === "table" ? (
              <p className="-mt-2 text-sm text-muted-foreground">
                Goals, targets and tasks with deadlines, ordered by the nearest
                date.
              </p>
            ) : null}

            {isLoading && !hasLoaded ? (
              /* ── Loading ── */
              <div
                className="surface-card p-12 flex flex-col items-center gap-4"
                role="status"
              >
                <Loader className="h-8 w-8 text-[#F45D48] animate-spin" />
                <p className="text-sm text-muted-foreground">
                  Loading your goals…
                </p>
              </div>
            ) : filtered.length === 0 && goals.length > 0 ? (
              /* ── Filtered: the list HAS goals and the user's own search or filter is hiding them.
             **The notice alone, with nothing around it** (owner, 2026-08-22): it used to sit
             centred inside the empty state's `surface-card`, so a warning-yellow outline was
             drawn inside a grey one — two frames for one message, and the grey one said "an
             empty page" while the yellow one said the opposite. The card belongs to the empty
             state's invitation, which is a different sentence; see the notice spec in CLAUDE.md
             (3d). Android already drew it this way.

             **Full width, text left** (owner, 2026-08-22): it stands where the cards would, so it
             takes the same column they do rather than a narrow box centred in it. */
              <FilteredEmptyNotice className="w-full text-left">
                No goals match that search or filter. Clear them to see the
                rest.
              </FilteredEmptyNotice>
            ) : filtered.length === 0 ? (
              /* ── Empty / Error ── */
              <div className="surface-card p-12 text-center">
                {syncError ? (
                  /* Error: backend or network problem — no CTA */
                  <>
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10 text-destructive">
                      {syncErrorKind === "network" ? (
                        <GlobeOff className="h-8 w-8" />
                      ) : (
                        <Cable className="h-8 w-8" />
                      )}
                    </div>
                    <div className="font-display text-2xl text-foreground">
                      {syncErrorKind === "network"
                        ? "You appear to be offline"
                        : "Couldn't load your goals"}
                    </div>
                    <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
                      {syncError}
                    </p>
                    <button
                      type="button"
                      onClick={() => void refreshGoals()}
                      className="mt-5 inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium text-foreground hover:bg-secondary transition-colors"
                    >
                      <RefreshCw className="h-4 w-4" />
                      Refresh
                    </button>
                  </>
                ) : (
                  /* Truly empty — DB has no goals, connection is fine */
                  <>
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-primary-soft text-primary">
                      <Trophy className="h-8 w-8" />
                    </div>
                    <div className="font-display text-2xl text-foreground">
                      Your journey starts here
                    </div>
                    <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
                      Set your first goal and start turning ambition into
                      progress.
                    </p>
                    <button
                      type="button"
                      onClick={() => setOpen(true)}
                      className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
                    >
                      Create your first goal
                    </button>
                  </>
                )}
              </div>
            ) : viewMode === "cards" ? (
              /* One goal per row (owner, 2026-10-09): the card is a two-column spread — what the
             goal is on the left, how far it has come on the right — so it takes the width. */
              <div className="flex flex-col gap-4 sm:gap-5">
                {filtered.map((goal) => (
                  <GoalCard key={goal.id} goal={goal} />
                ))}
              </div>
            ) : (
              <GoalsTable goals={goals} filteredGoalIds={filteredGoalIds} />
            )}
          </div>

          {viewMode === "cards" ? <Upcoming /> : null}
        </div>

        <button
          type="button"
          onClick={() => setOpen(true)}
          className="fixed bottom-5 right-5 z-40 grid h-14 w-14 place-items-center rounded-full bg-primary text-primary-foreground shadow-raised transition-transform hover:scale-105 hover:bg-primary/90 sm:bottom-7 sm:right-7"
          aria-label="New goal"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>

        <NewGoalSheet open={open} onOpenChange={setOpen} />
      </div>
    </div>
  );
}
