# Requirements: Target progress lock

A padlock on every target in **Will do**. Locking pins the target's **progress** so a stray tap
can't move it; the target's **text stays editable** either way (title, unit, deadline, task names).

Status: **built on the web (2026-08-02)** and **on Android (2026-08-04)**.

## Behaviour

- **An achieved target is locked by default** — finished work shouldn't be undone by a mis-tap.
- **An unfinished target is unlocked by default** — the user can lock it deliberately (e.g. a
  number they don't want to nudge while editing around it).
- **The user's choice always wins and survives the target crossing completion**: a finished target
  can be unlocked to correct it, and it stays unlocked until the user says otherwise.
- **What the lock stops** — every edit that moves the number: numeric current/start/total (and the
  ±  buttons), the binary done toggle, ticking a checklist task, and adding or removing tasks
  (both change the denominator).
- **What it never stops** — the target title, the unit, the deadline, task text, attaching
  resources, or deleting the target itself.
- **Refusals explain themselves**: "This target is locked. Unlock it to change its progress." —
  on the web as a toast at the point of the tap, inline under the numeric editor, and as a banner
  at the top of the tasks panel. On Android the expanded card carries the same sentence (naming the
  padlock) under its controls, and the controls themselves go inert — a phone has no toast surface
  in the card, and a message that only appears after a refused tap is easy to miss.

## Storage

`target.progress_locked boolean` — **nullable on purpose** (migration
`V20__target_progress_locked.sql`):

| Value | Meaning |
|---|---|
| `NULL` | The user hasn't decided — the client derives the lock from completion |
| `TRUE` / `FALSE` | An explicit choice that outlives any progress change |

Exposed as `Target.progressLocked: Boolean` and accepted on `UpdateTargetInput`. The server
**stores** the flag; it does not enforce it — the lock is a UI guard against accidents, not a
permission, so an unlock-and-edit in the same session never deadlocks. If the flag ever needs to
be an actual constraint (e.g. to stop the AI assistant from moving a locked target), that
enforcement belongs in `TargetService.update` and needs its own decision.

## Where the padlock lives

- **Desktop table** — always visible immediately after the target's title (it is state, not a
  hidden action). The padlock is the only lock control: the row's ⋯ menu deliberately does **not**
  duplicate it.
- **Mobile card** — a round badge hanging off the card's top-right corner (the way the rating
  smiley hangs off an option card, and the ✕ does on the reference card), so it is always present
  without taking a slot in the content. The target's other actions live inside the
  **Update progress** panel, spelled out rather than hidden behind a ⋯ menu: a plain **Attach
  resource** text link (no icon) and a red **Delete** button matching the one in the confirmation
  dialog.

## Mobile card layout (2026-08-02)

Modelled on the reference card the owner supplied: a compact **calendar tile** on the left — month
above, the day in big digits, same footprint as a dashed "Set deadline" placeholder so nothing
shifts once a date is picked — the inline-editable title beside it with the countdown under it, and
the padlock badge on the corner; a **5px progress strip** spanning the card — the shape of the page-scroll bar, carrying this target's progress
(Salt-400 track, Kale-500 fill); and a full-width **"Update progress"** footer on Kale-200 with
Kale-500 bold text that expands the type-specific controls (numeric ± and values, the binary
"Mark done", or the checklist with its add-task input) plus the target's actions menu. The footer
carries no chevron — its label flips to **"Close details"** while the panel is open.

A title carrying a resource tag is quoted as prose in dialogs and toasts via `useReadableText`
(`stripResourceTokens`), so a raw `{{res:42}}` never reaches the user.

## Code

- `backend/.../db/migration/V20__target_progress_locked.sql`, `target/Target.java`,
  `graphql/input/UpdateTargetInput.java` (a 9-arg convenience constructor keeps every pre-lock
  caller compiling), `target/TargetService.java`, `graphql/schema.graphqls`.
- `src/lib/spira/progress.ts` — `isProgressLocked(target)`, the single place the default lives.
- `src/lib/spira/types.ts`, `src/lib/spira/api.ts` — the field on the wire.
- `src/components/spira/Targets.tsx` — `ProgressLockButton`, `PROGRESS_LOCKED_MESSAGE`,
  `warnProgressLocked()`, and the `locked` prop threaded through `NumericBody`, `ChecklistEditor`
  and `TasksResizableSheet`.

### Android (2026-08-04)

- `ui/util/Progress.kt` — `isProgressLocked`, `progressSteps`, `formatPercent`: the Kotlin port of
  `progress.ts`, so both surfaces derive the default and the precision the same way.
- `ui/goals/TargetCard.kt` — the whole card: `DeadlineTile` (the illustrated artwork under
  `res/drawable-xxhdpi/tile_*.png`), `ProgressLockBadge`, the Kale-200 footer, the numeric editor
  with its typing preview, the binary toggle, the "Steps"-shaped task rows and Close / Delete.
- `data/goals/GoalDetail.kt`, `GoalsRepository.kt`, `ui/goals/GoalWorkspaceViewModel.kt` — the
  `progressLocked` field on the wire plus `setTargetProgressLocked` / `setTargetDeadline` /
  `setTargetNumbers` / `setTargetUnit` / `setChecklistTaskDeadline`.

Tests: `TargetServiceTest` (persists both directions; an absent flag leaves the choice alone),
`progress.test.ts` and its Kotlin twin `ui/util/ProgressTest.kt` (the default, the explicit
override in both directions, and `null` as "no choice"), and `ui/goals/TargetCardTest.kt` (the
footer, the fractional percentage, the padlock, and a locked target refusing progress while its
text stays editable). `VisualCheckTargetCardTest` renders the four deadline states to a PNG.
