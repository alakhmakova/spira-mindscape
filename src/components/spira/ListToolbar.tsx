/**
 * The sort and filter chrome shared by every list on the web — the twin of Android's
 * `ui/components/SpiraListToolbar.kt`, so the two surfaces read as one design.
 *
 * The shape (owner, 2026-08-20):
 *
 *  1. **A trigger is the filter glyph alone**, in Kale, never bordered or filled, carrying a Guava
 *     dot when something is narrowing the list — at every width. Android's `SpiraFilterSortTrigger`
 *     is the same mark.
 *  2. **A filter is never a dropdown.** One panel holds every question a list asks: a drawer from
 *     the bottom on a phone, a side panel from the right on a laptop, and the same tree of
 *     questions inside either. The menus that used to hold them on a wide screen — the trigger
 *     word, the columns, the rows — are gone, so there is nothing left to build one out of.
 *  3. **A question's shape says what kind of question it is**: filter values are pills, a modifier
 *     (Ascending / Descending) is a segmented control, and a sort key is a choice card. Three
 *     shapes, so a glance tells them apart without reading.
 */

import * as React from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { SHEET_HEAD_BUTTON, SheetHead } from "./SheetHead";
import { Input } from "@/components/ui/input";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { SM_BREAKPOINT, useIsNarrowerThan } from "@/hooks/use-mobile";
import {
  Check,
  Filter,
  LockFilled,
  LockOpenFilled,
  Plus,
  Search,
  X,
} from "@/components/spira/icons";
import { PillChoice, type PillTone } from "@/components/spira/Pill";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import { cn } from "@/lib/utils";

/* ────────────────────────────────────────────────────────────────────────────────────────────
 * The mobile half of the same chrome: a **drawer**, not a dropdown.
 *
 * Every list on the web used to solve this differently — the All-goals header had one drawer,
 * "Will do" had a dropdown, Options had another dropdown, Resources had a third layout. The owner
 * asked for one answer everywhere (2026-08-17), and the answer on a phone is a drawer: a dropdown
 * anchored to a small trigger has nowhere to go on a 390px screen.
 *
 * The parts below are what a list assembles: [FilterIconTrigger] to open it, [ToolbarSheet] as the
 * shell, and [SheetGroup] with [SheetPills] / [SheetSegmented] / [SheetChoiceCards] for the
 * questions inside — a shape per kind of question: values, a modifier, a key.
 * ──────────────────────────────────────────────────────────────────────────────────────────── */

/**
 * The mobile trigger: **the filter glyph alone**, with a dot when anything is narrowing the list.
 *
 * No container, no fill, no border — the desktop trigger has none either, and a boxed icon on a
 * phone toolbar was the heaviest thing in the row. The count is a **dot**, not a number: at this
 * size "(2)" beside a 16px glyph is unreadable, and what matters is only "something is on".
 */
export function FilterIconTrigger({
  active,
  onClick,
  ariaLabel = "Filter",
  className,
}: {
  active: boolean;
  onClick: () => void;
  ariaLabel?: string;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      className={cn(
        "grid h-8 w-8 shrink-0 place-items-center rounded-md text-primary transition-colors hover:text-primary/75",
        className,
      )}
    >
      {/* The dot rides the glyph itself — this trigger never carries a word, so the dot is the
          only thing that can say a filter is on. */}
      <span className="relative inline-flex">
        <Filter className="h-[18px] w-[18px]" />
        {active && (
          <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
        )}
      </span>
    </button>
  );
}

/**
 * A list's search field on a **desktop** section header — the twin of {@link SectionSearchButton},
 * which is what the same search is on a phone.
 *
 * One component because there were two, and they had drifted: the targets header drew a raw
 * `<input>` at `w-36 h-8` while Resources used the design-system `Input` at `w-[200px] h-9`. Side
 * by side down the same page that reads as two different controls for one job (owner, 2026-08-21) —
 * and the raw `<input>` was against the app's first UI rule as well.
 */
export function SectionSearchInput({
  value,
  onChange,
  placeholder,
  className,
}: {
  value: string;
  onChange: (next: string) => void;
  placeholder: string;
  className?: string;
}) {
  return (
    <div className={cn("relative hidden w-[200px] sm:block", className)}>
      <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-9 pl-8 pr-8"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange("")}
          aria-label="Clear search"
          className="absolute right-2 top-1/2 grid h-5 w-5 -translate-y-1/2 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}

/**
 * The search **glyph** that opens a list's search — the phone pattern, and the twin of the Android
 * All-goals header (`SpiraTopBar` → `SearchTopBar`): a bare mark in the header row, which on a tap
 * swaps the whole row for a field. A permanently-open field beside a title and its count has
 * nowhere to be on a 390px screen, and the row it forced was the reason each list solved its
 * search differently.
 */
export function SectionSearchButton({
  onOpen,
  active,
  ariaLabel = "Search",
}: {
  onOpen: () => void;
  /** Something is typed — mark it, or closing the field hides the fact the list is narrowed. */
  active?: boolean;
  ariaLabel?: string;
}) {
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-label={ariaLabel}
      className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-primary transition-colors hover:text-primary/75"
    >
      <span className="relative inline-flex">
        <Search className="h-[18px] w-[18px]" />
        {active && (
          <span className="absolute -right-1 -top-1 h-[7px] w-[7px] rounded-full bg-[#F45D48]" />
        )}
      </span>
    </button>
  );
}

/**
 * The open search: a field across the whole header row, with an X that closes it and clears.
 *
 * Pass it as a `Section`'s `headerOverride`, so the title steps out of the way while it is open.
 */
export function SectionSearchField({
  value,
  onChange,
  onClose,
  placeholder,
}: {
  value: string;
  onChange: (next: string) => void;
  onClose: () => void;
  placeholder: string;
}) {
  return (
    <div className="flex w-full items-center gap-2">
      <div className="relative min-w-0 flex-1">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          autoFocus
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              onChange("");
              onClose();
            }
          }}
          placeholder={placeholder}
          aria-label={placeholder}
          className="h-9 w-full rounded-md border border-border bg-surface pl-9 pr-8 text-sm outline-none transition-colors placeholder:text-muted-foreground/75 focus:border-primary"
        />
        {value && (
          <button
            type="button"
            onClick={() => onChange("")}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 grid h-5 w-5 -translate-y-1/2 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
          >
            <X className="h-3 w-3" />
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={() => {
          onChange("");
          onClose();
        }}
        aria-label="Close search"
        className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

/**
 * The round **+** a phone gets in place of a worded add button ("Add target", "Add resource").
 *
 * On a narrow header the word plus a search glyph plus a filter glyph do not fit, and the word is
 * the piece the section's own title already implies.
 */
export function RoundAddButton({
  onClick,
  ariaLabel,
  tone = "primary",
}: {
  onClick: () => void;
  ariaLabel: string;
  /** Guava for "Add target" (it is the accent that section already uses), Kale for the rest. */
  tone?: "primary" | "accent";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      title={ariaLabel}
      className={cn(
        "grid h-9 w-9 shrink-0 place-items-center rounded-full text-white transition-colors",
        tone === "accent"
          ? "bg-[#F45D48] hover:bg-[#F45D48]/90"
          : "bg-primary hover:bg-primary/90",
      )}
    >
      <Plus className="h-[18px] w-[18px]" />
    </button>
  );
}

/**
 * The drawer a mobile list's filters and sort open into.
 *
 * **The head is Kale** (owner, 2026-08-17) — white type on teal, the same band the app header
 * carries, so a sheet reads as part of the app rather than as a white box floating over it. The
 * footer is **Reset all** beside **Apply**; the confirm word is "Apply" and never "Done", because
 * the sheet is a set of choices being applied to a list, not a task being finished.
 */
export function ToolbarSheet({
  open,
  onOpenChange,
  title,
  onReset,
  resetDisabled,
  locked,
  onLockedChange,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Clears every question in the panel — **every** one. Always shown: a control that appears only
   *  once you have made a mess is a control nobody finds. */
  onReset: () => void;
  resetDisabled?: boolean;
  /** Whether this list's padlock is closed. See the padlock note in `shell-store.ts`. */
  locked?: boolean;
  onLockedChange?: (next: boolean) => void;
  children: React.ReactNode;
}) {
  // **`sm`, not the 768 `useIsMobile` uses.** Every toolbar around this panel switches layout on
  // Tailwind's `sm:`, so switching the panel 128px later opened a bottom drawer beside a toolbar
  // that had already gone desktop.
  const isMobile = useIsNarrowerThan(SM_BREAKPOINT);
  const body = (
    <ToolbarPanelBody
      title={title}
      onClose={() => onOpenChange(false)}
      onReset={onReset}
      resetDisabled={resetDisabled}
      locked={locked}
      onLockedChange={onLockedChange}
    >
      {children}
    </ToolbarPanelBody>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        {/* **White**, explicitly — `bg-background` is the app's warm grey and made the sheet read
            as a dimmed panel behind its own teal head (owner, 2026-08-17). */}
        <DrawerContent className="mt-0 flex max-h-[92vh] flex-col bg-white px-0">
          {body}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        // Its own X lives in the teal head; the corner one would sit on top of it.
        closeButton={false}
        className="flex w-full flex-col gap-0 border-l-0 bg-white p-0 sm:max-w-[420px]"
      >
        {body}
      </SheetContent>
    </Sheet>
  );
}

/**
 * The panel's card — head, questions, foot — with neither shell around it.
 *
 * One tree for both widths on purpose: the drawer and the side panel are only *where* the thing
 * sits. Two copies of this markup is how the phone and the laptop drifted into asking the same
 * questions in different shapes, which is what this whole component exists to stop.
 */
function ToolbarPanelBody({
  title,
  onClose,
  onReset,
  resetDisabled,
  locked,
  onLockedChange,
  children,
}: {
  title: string;
  onClose: () => void;
  onReset: () => void;
  resetDisabled?: boolean;
  locked?: boolean;
  onLockedChange?: (next: boolean) => void;
  children: React.ReactNode;
}) {
  return (
    <>
      {/* The same Kale band every sheet wears — see `SheetHead`. */}
      <SheetHead
        title={title}
        onClose={onClose}
        actions={
          /* **The padlock lives on the coloured head, to the right** (owner, 2026-08-21). Closed,
             this list's filters and sort survive a reload and a restart; open, they go back to
             their defaults next time — see the padlock note in `shell-store.ts`. It sits beside
             the X because it is about the panel as a whole, not about any one question inside it. */
          onLockedChange ? (
            <button
              type="button"
              onClick={() => onLockedChange(!locked)}
              aria-pressed={!!locked}
              aria-label={
                locked
                  ? "Filters and sort are kept — unlock to let them reset"
                  : "Keep these filters and sort"
              }
              title={
                locked
                  ? "Kept until you unlock — survives a reload"
                  : "Not kept — these reset next time"
              }
              className={cn(
                SHEET_HEAD_BUTTON,
                locked &&
                  "bg-white/20 text-primary-foreground hover:bg-white/30",
              )}
            >
              {locked ? (
                <LockFilled className="h-4 w-4" />
              ) : (
                <LockOpenFilled className="h-4 w-4" />
              )}
            </button>
          ) : undefined
        }
      />

      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 pb-6 pt-4">
        {children}
      </div>

      <div
        className="flex shrink-0 gap-3 bg-white px-5 pt-3"
        style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
      >
        <button
          type="button"
          onClick={onReset}
          disabled={resetDisabled}
          className="h-12 flex-1 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
        >
          Reset all
        </button>
        <button
          type="button"
          onClick={onClose}
          className="h-12 flex-1 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Apply
        </button>
      </div>
    </>
  );
}

/** One question inside a {@link ToolbarSheet}: its heading, then its answers. */
export function SheetGroup({
  title,
  children,
  className,
}: {
  title: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("min-w-0", className)}>
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </p>
      {children}
    </div>
  );
}

/**
 * A question whose answers are short enough to sit on **one line** — drawn as {@link PillChoice}es.
 *
 * A pill is the shape defined in `Pill.tsx` and Android's `SpiraBadge`: a **bright 1px outline, a
 * nearly-white fill and a near-black word**. It is *not* a solid capsule — filled, a row of them
 * reads as a row of buttons rather than as one family of answers with one chosen.
 *
 * Stacked as full-width rows, three short words ("All", "Achieved", "Not achieved") took three
 * lines of a sheet for nothing.
 */
export function SheetPills<T extends string>({
  options,
  value,
  onChange,
  tone = "success",
}: {
  options: readonly { value: T; label: string; icon?: React.ReactNode }[];
  value: T;
  onChange: (next: T) => void;
  /** The tone the chosen pill takes. The rest are always neutral. */
  tone?: PillTone;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {options.map((opt) => (
        <PillChoice
          key={opt.value}
          label={opt.label}
          icon={opt.icon}
          tone={tone}
          selected={value === opt.value}
          onSelect={() => onChange(opt.value)}
        />
      ))}
    </div>
  );
}

/**
 * A **segmented control** — two or three answers sharing one outlined capsule, the chosen one
 * ringed (owner's reference, 2026-08-17: the CSV / PDF pair).
 *
 * This is what a *modifier* looks like — Ascending / Descending — as against a filter value, which
 * is a pill, and a sort key, which is a card. Three questions, three shapes, so a glance tells them
 * apart without reading.
 *
 * White throughout: the chosen segment is marked by a **teal ring and near-black bold type**, not a
 * fill. A filled segment in a white sheet would out-shout the Apply button below it.
 */
export function SheetSegmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (next: T) => void;
}) {
  return (
    <div className="flex h-11 w-full items-stretch rounded-md border border-border bg-white p-0.5">
      {options.map((opt) => {
        const on = value === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(opt.value)}
            className={cn(
              "flex flex-1 items-center justify-center rounded-sm text-sm transition-colors",
              on
                ? "border-2 border-primary font-bold text-foreground"
                : "font-medium text-primary hover:bg-[#F6F6F6]",
            )}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * A question whose answers are **choice cards** — the owner's reference (2026-08-17).
 *
 * The shape, from that reference:
 *
 *  - a card, **not a button**: the label is **left-aligned** and the card is taller than a control.
 *    An earlier version centred the word in a filled capsule and was indistinguishable from the
 *    sheet's own Reset / Apply buttons two inches below it;
 *  - unchosen: **white** with a hairline border;
 *  - chosen: a **pale teal wash** (Kale-100), a **teal border**, and a **teal triangle folded into
 *    the top-right corner with a white tick in it** — what makes it read as a choice already made
 *    rather than a button waiting to be pressed.
 */
export function SheetChoiceCards<T extends string>({
  options,
  value,
  onChange,
}: {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (next: T) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-2">
      {options.map((opt) => {
        const on = value === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(opt.value)}
            className={cn(
              "relative flex h-[52px] items-center overflow-hidden rounded-md pl-3.5 pr-8 text-left text-sm transition-colors",
              on
                ? "border-[1.5px] border-primary bg-[#F3FAFB] font-semibold text-foreground"
                : "border border-border bg-white font-medium text-foreground hover:border-primary/40",
            )}
          >
            {opt.label}
            {on && (
              // The folded corner. A CSS triangle rather than an image: a right triangle is a
              // border trick, and it stays crisp at any density.
              <span
                aria-hidden
                className="absolute right-0 top-0 h-0 w-0 border-l-[26px] border-t-[26px] border-l-transparent border-t-primary"
              />
            )}
            {on && (
              <Check
                aria-hidden
                className="absolute right-[3px] top-[3px] h-[11px] w-[11px] text-white"
              />
            )}
          </button>
        );
      })}
    </div>
  );
}

/**
 * A **date range** question: From and To side by side, each opening the app's one date picker —
 * the twin of Android's `SpiraSheetDateRange`.
 *
 * Both ends are optional and independent, so "everything before March" is one tap rather than a
 * date the user has to invent for the other end. An end that is set comes off again from the
 * picker's own Clear, which is why there is no third control here.
 *
 * Values are ISO instants, or "" for an open end.
 */
export function SheetDateRange({
  from,
  to,
  onFromChange,
  onToChange,
}: {
  from: string;
  to: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-2">
      <DeadlinePopover
        iso={from || undefined}
        onChange={(next) => onFromChange(next ?? "")}
        variant="button"
        placeholder="From"
        hideDaysLeft
        disableScroll
        className="h-11 justify-start px-3 text-sm"
      />
      <DeadlinePopover
        iso={to || undefined}
        onChange={(next) => onToChange(next ?? "")}
        variant="button"
        placeholder="To"
        hideDaysLeft
        disableScroll
        className="h-11 justify-start px-3 text-sm"
      />
    </div>
  );
}

/**
 * The **confidence** question: 1 to 10 as a two-row grid, one of which can be chosen — the twin of
 * Android's `SpiraSheetConfidence`.
 *
 * Tapping the chosen number again clears it back to "any", which is why there is no "All" cell:
 * ten answers plus an eleventh escape hatch would not fit across a phone, and the number already
 * shows whether it is on.
 *
 * [value] is "1".."10", or "" for any — the spelling the store uses.
 */
export function SheetConfidence({
  value,
  onChange,
}: {
  value: string;
  onChange: (next: string) => void;
}) {
  return (
    <div className="grid grid-cols-5 gap-2">
      {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => {
        const on = value === String(n);
        return (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(on ? "" : String(n))}
            className={cn(
              "h-10 rounded-sm text-sm transition-colors",
              on
                ? "bg-primary font-bold text-primary-foreground"
                : "border border-border bg-white font-medium text-foreground hover:border-primary/40",
            )}
          >
            {n}
          </button>
        );
      })}
    </div>
  );
}
