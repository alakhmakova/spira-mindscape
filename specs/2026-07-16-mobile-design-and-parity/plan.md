# Spec: Mobile design system + web feature parity (Android)

> Status: approved 2026-07-16. Aligns with Roadmap Phase 13 (native mobile).
> Built in reviewable slices (see Suggested execution order at the bottom).

## Context

The native Android app today is **read-only + limited target updates** (it wires only
`GetGoals`, `GetGoal`, `UpdateTarget`), and its visuals are a rough first pass. The user wants
the app to (a) look like the web and (b) do everything the web does (create/edit/delete goals,
targets, reality, options, resources). Crucially, these are **inseparable**: each editing
feature has its own UI, so building features "rough" now and restyling later = double work. So we
build a **design foundation first**, then implement every feature already-styled on top of it.

Exploration also surfaced a real discrepancy: **the web's primary color is teal**
(`--primary: oklch(0.51 0.092 194)` in `src/styles.css`), while the Android theme uses orange
(`#EA580C` in `ui/theme/Color.kt`). The app never actually matched the web. This redesign
corrects that.

**Decisions locked with the user:**
- Primary color → **teal** (true web mirror); orange stays only as a secondary accent.
- Theme → **light-only** (web has no dark mode); Android's current dark theme is dropped. Dark → backlog.
- Headings → bundle **Playfair Display** (what the web actually renders for headings).
- Notes → **plain text** for v1 (web's rich-text TipTap editor is deferred → backlog).
- Backend is **unchanged** — every mutation already exists and the web uses it; this is 100% client-side.

Sources of truth: web tokens in `src/styles.css`, web components in `src/components/spira/`, web
sync in `src/lib/spira/store.ts`, schema in `backend/src/main/resources/graphql/schema.graphqls`.

---

## Phase 1 — Design foundation (do first; everything else builds on it)

### 1a. Theme tokens — mirror `src/styles.css` `:root`
Rewrite `android/app/src/main/java/com/spiramindscape/android/ui/theme/Color.kt` with the full web
token set (convert each oklch precisely at implementation; approximate hex below):

| Token | Web oklch | ~hex | Compose role |
|---|---|---|---|
| primary | 0.51 0.092 194 | ~#0E8A82 teal | `primary` |
| primary-foreground | 0.99 0 0 | ~#FCFCFC | `onPrimary` |
| primary-soft | 0.95 0.032 180 | ~#E5F3F1 | `primaryContainer` |
| brand-orange (accent) | 0.735 0.123 76 | ~#D18A3E | `tertiary` / accent |
| background/surface | 0.982 0 0 | ~#FAFAFA | `background`/`surface` |
| surface-raised | 1 0 0 | #FFFFFF | card / `surfaceContainerHighest` |
| surface-sunken | 0.94 0 0 | ~#EDEDED | sunken |
| foreground | 0.321 0 0 | ~#383838 | `onBackground`/`onSurface` |
| muted-foreground | 0.51 0 0 | ~#7A7A7A | secondary text |
| destructive | 0.61 0.205 27 | ~#D64545 | `error` |
| success | 0.52 0.13 143 | ~#2E8A4F | success (custom) |
| warning = brand-orange | 0.735 0.123 76 | ~#D18A3E | warning (custom) |
| border | 0.899 0 0 | ~#E0E0E0 | `outlineVariant` |
| border-strong | 0.72 0 0 | ~#ABABAB | `outline` |

- Rewrite `ui/theme/Theme.kt`: **single light** `lightColorScheme(...)`; remove the dark scheme +
  `isSystemInDarkTheme()` branch. Add a small `SpiraExtraColors` (success/warning/soft) via a
  `CompositionLocal` for tokens MD3 doesn't have.
- Add `ui/theme/Shape.kt`: radius scale from `--radius: 0.5rem` → `Shapes(small=6.dp,
  medium=8.dp, large=12.dp)` plus named `dp` constants (4/6/8/12/16) for direct use.

### 1b. Typography — `ui/theme/Type.kt` + bundled font
- Add Playfair Display to `res/font/` (600/700) + a `FontFamily`; body stays system/Roboto
  (matches the web's system-sans fallback), mono optional (JetBrains Mono, low priority).
- Build a `Typography` whose `headline*`/`title*` use Playfair (weights 600/700), body/label use
  the default sans, mirroring the web scale (h1 ~1.6rem/700, h2 ~1.3rem/700, h3 ~1.1rem/600).

### 1c. Confidence colors — port `confidence-color.ts`
New `ui/theme/ConfidenceColor.kt`: `≤4 → #EF7B6C`, `≤7 → #F8D068`, `>7 → #7ECEC4`.

### 1d. Component kit — new package `ui/components/`
Reusable Compose analogues of the web primitives (`src/components/ui/*` + `spira/*`), each themed:
- `SpiraButton` (primary / ghost / destructive), `SpiraTextField` + `SpiraTextArea`.
- `SpiraSection` — collapsible card with title + count pill + action slot (mirrors `Section.tsx`).
- `InlineListRow` / `InlineEditText` — add / edit-on-blur / delete row (mirrors `Inline.tsx`
  `InlineList` + `InlineText`), the pattern used by Reality, Options, checklist.
- `ConfidenceStepper` (1–10, mirrors `Confidence.tsx`).
- `DeadlineField` — trigger + Material3 `DatePickerDialog` with Today/Clear (mirrors `DeadlinePopover`).
- `SpiraBottomSheet` — `ModalBottomSheet` wrapper with sticky header (title + X) + scroll body +
  pinned footer (the mobile equivalent of the web's Sheet/Drawer create forms).
- `ConfirmDialog` (mirrors `ConfirmDialog.tsx`), `EmptyState`, `SpiraProgressBar`, `TypePickerCards`
  (radio-cards used by new-target / new-resource type choice).
- Compose UI tests (Robolectric) for the non-trivial ones (InlineListRow, ConfidenceStepper,
  bottom-sheet form submit/validation).

### Phase 2 — Restyle existing screens with the kit
Rework `ui/goals/GoalsDashboardScreen.kt` and `ui/goals/GoalWorkspaceScreen.kt` to use the new
theme + components (cards, sections, progress, spacing/type) so what already exists looks right.
Keep behavior; swap ad-hoc widgets for kit components.

---

## Phase 3 — Feature parity (CRUD), built on the kit, feature-by-feature

Each feature ships as a small, fully-styled slice. Backend already supports all of it.

### 3a. Data layer (once, up front)
- **New Apollo `.graphql` operations** under `android/app/src/main/graphql/` (names match web):
  `CreateGoal`, `UpdateGoal`, `DeleteGoal`; `AddRealityItem`, `UpdateRealityItem`,
  `RemoveRealityItem`; `AddOption`, `UpdateOption`, `SelectOption`, `RemoveOption`,
  `ReorderOptions`; `CreateTarget`, `DeleteTarget`; `CreateResource`, `UpdateResource`,
  `DeleteResource`. (Apollo generates the input types — `CreateGoalInput`, `UpdateGoalInput`,
  `UpdateOptionInput`, `CreateTargetInput`, `CreateResourceInput`, etc. — from the schema.)
- **Expand domain models** in `data/goals/GoalDetail.kt` to editable parity: `OptionItem.position`,
  `ChecklistItemModel.deadline/achievedAt`, and model resource subtypes properly
  (note `body`, link `url`, email `name/role/email/phone`, file `mime/dataUrl`) instead of the
  collapsed `ResourceItem(id,type,title)`. Widen the `GetGoal.graphql` selection to fetch them.
- **Extend `GoalsRepository`** with the mirror of the web store's action surface:
  `createGoal / updateGoal / deleteGoal / addReality / updateReality / removeReality /
  addOption / updateOption / selectOption / removeOption / reorderOptions / createTarget /
  removeTarget / createResource / updateResource / removeResource`.

### 3b. Optimistic + debounced sync (mirror `store.ts` principles, proportionally)
Extend `GoalWorkspaceViewModel` (and add a small `Debouncer` util) to mirror the web pattern:
- Field edits (title/description/confidence, target values, option/resource text): apply to local
  state immediately, then **debounce ~500 ms per entity key** before the network write; skip the
  write for not-yet-persisted temp ids.
- Creates/deletes: optimistic with a temp `local-…` id, fire immediately, **reconcile by id** on
  success, **roll back** on failure; surface a `syncError` state (network vs service), 401 → re-auth.
- The existing refetch-on-resume must not clobber pending edits (guard on pending debounce / temp ids).

### 3c. Feature slices (each = kit-styled UI + repo call + optimistic update)
1. **Create goal** — `SpiraBottomSheet` form: title, description, `ConfidenceStepper`, `DeadlineField`
   (mirrors `NewGoalSheet.tsx`). FAB on the dashboard.
2. **Edit goal fields** — inline title/description, inline confidence, `DeadlineField` on the
   workspace header (mirrors the goal route KPIs).
3. **Delete goal** — `ConfirmDialog` → `deleteGoal` → back to dashboard.
4. **Targets** — add (bottom sheet: `TypePickerCards` numeric/binary/checklist + type fields +
   deadline), edit title inline, edit values inline in the card (numeric stepper / binary toggle /
   checklist add-edit-remove), delete via `ConfirmDialog`, per-target deadline. Type is immutable
   (delete+recreate), same as web. Mirrors the web **mobile** `TargetRow` (inline everything).
5. **Reality** — `InlineListRow` add/edit/remove for actions & obstacles.
6. **Options** — add, single-select radio, inline edit, remove; reorder can be a later slice.
7. **Resources** — add/edit/delete for **note (plain text)**, **link**, **email**; file = view
   only for now; note body stored as plain text into the `body` string field (round-trips fine).

---

## Backlog entries to add (on execution)
- `backlog/mobile-notes-rich-text.md` — mobile notes are plain text; web has a rich-text (TipTap)
  editor. Restore rich formatting on mobile later.
- `backlog/android-dark-theme.md` — Android shipped a dark theme; dropped to mirror the web
  (light-only). Revisit once the web gains a real dark palette (or design a mobile-native one).

## Follow-ups (not in this spec)
- **Maestro E2E** — with real create→edit→delete journeys landing, add Maestro flows
  (`docs/maestro-e2e-guide.md`), per the CLAUDE.md deferred reminder.
- **Push reminder logic** — separate initiative (`backlog/mobile-push-reminder-logic.md`).

## Suggested execution order
1. Phase 1 (theme tokens → typography → confidence colors → component kit) — the foundation.
2. Phase 2 (restyle dashboard + workspace) — immediate visible payoff, validates the kit.
3. Phase 3a (data layer: operations + models + repo methods) then 3b (optimistic sync).
4. Phase 3c feature slices in order: create goal → edit goal → delete goal → targets → reality →
   options → resources. Each slice is independently reviewable and shippable.

## Verification
- `cd android && .\gradlew.bat :app:testDebugUnitTest :app:jacocoDebugReport` green (new component
  Compose UI tests + repository tests via MockWebServer/Apollo test harness for the new operations).
- Launch on the `spira_pixel` emulator against a local backend (`10.0.2.2:8080`): create a goal,
  edit every part (title/description/confidence/deadline, add/edit/delete a target of each type,
  reality items, options, a note/link/email resource), delete a goal — each round-trips and
  survives an app-resume refetch.
- Cross-surface parity: a goal created/edited on the phone appears correctly on the web and vice
  versa (same GraphQL data).
- Distribute via `:app:distributeDebug` and eyeball the new look on a physical device.
- Full backend suite still green (no backend changes, but run once): `cd backend && .\mvnw.cmd test`.
