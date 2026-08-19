/**
 * The sort and filter chrome shared by every list on the web — the twin of Android's
 * `ui/components/SpiraListToolbar.kt`, so the two surfaces read as one design.
 *
 * The shape (owner, 2026-08-15):
 *
 *  1. **A trigger is `icon · word`.** A leading glyph (the filter mark, or the sort direction) sits
 *     before the word, and it is never bordered or filled — even alone. The word is Kale. No pill,
 *     no chip, and **no caret**: two marks are enough to say the thing opens (owner, 2026-08-17).
 *  2. **A menu is stacked groups, each under its own heading with a hairline beneath it** — not
 *     side-by-side columns divided by a vertical rule.
 *  3. **A row hovers to a pale grey with Kale text.** The chosen *filter value* is a filled-teal
 *     row; the chosen *modifier* (Ascending / Descending) instead keeps the hover look, so it reads
 *     as a modifier rather than as the filter itself.
 */

import * as React from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Check, Filter, Plus, Search, X } from "@/components/spira/icons";
import { PillChoice, type PillTone } from "@/components/spira/Pill";
import { cn } from "@/lib/utils";

/** Pale grey + Kale text — the hover look, reused as the "aux selected" look. */
const HOVER = "hover:bg-[#F6F6F6] hover:text-primary";

/**
 * Closes the menu a {@link MenuChoice} sits in.
 *
 * Provided only by a {@link ToolbarMenu} marked `closeOnSelect` — for a question with exactly one
 * answer (the Options lean filter), where leaving the menu open after a pick means the user has to
 * dismiss a menu that has nothing left to say.
 */
const MenuCloseContext = React.createContext<(() => void) | null>(null);

/**
 * The word a toolbar menu opens from: a leading glyph, the word, and a caret.
 *
 * `asChild` on the Radix trigger, so this really is the button — wrapping it would put a second
 * focusable element in the tab order.
 */
export function ToolbarTrigger({
  label,
  count,
  ariaLabel,
  className,
  leadingIcon,
  iconOnlyOnMobile,
}: {
  label: string;
  /** Filters narrowing the list. Shown in brackets — "Filter (2)" — and hidden at zero. */
  count?: number;
  ariaLabel?: string;
  className?: string;
  /** The mark before the word (the filter glyph, or the current sort direction). Never bordered. */
  leadingIcon?: React.ReactNode;
  /**
   * On a phone, shrink to **the glyph alone with a dot**: the word and the caret are dropped and
   * `count` becomes a dot rather than a number. A narrow toolbar has no room for a second word
   * beside the list's own controls, and at 16px "(1)" is unreadable anyway.
   */
  iconOnlyOnMobile?: boolean;
}) {
  return (
    <DropdownMenuTrigger asChild>
      <button
        type="button"
        aria-label={ariaLabel}
        className={cn(
          "relative inline-flex items-center gap-1.5 rounded-md px-1.5 py-1.5 text-sm font-semibold",
          "text-primary transition-colors hover:text-primary/75",
          "outline-none focus-visible:ring-2 focus-visible:ring-ring",
          className,
        )}
      >
        {leadingIcon}
        {/* **No chevron** (owner, 2026-08-17) — a glyph and its word are enough to say "this
            opens". The caret was a third mark in a control that already has two. */}
        <span className={cn(iconOnlyOnMobile && "hidden sm:inline")}>
          {count ? `${label} (${count})` : label}
        </span>
        {iconOnlyOnMobile && !!count && (
          <span className="absolute right-0.5 top-0.5 h-[7px] w-[7px] rounded-full bg-[#F45D48] sm:hidden" />
        )}
      </button>
    </DropdownMenuTrigger>
  );
}

/**
 * The menu body: its groups laid out as **columns side by side**. The owner asked only for the
 * vertical divider between columns to go — not for the columns to be stacked into one tall list —
 * so the horizontal layout stays and each column is separated by its heading's rule, not a rule
 * between columns.
 */
export function MenuColumns({ children }: { children: React.ReactNode }) {
  return <div className="flex items-start gap-2">{children}</div>;
}

/**
 * One column of a menu: its question, a **hairline under the heading**, then that question's
 * answers below. No vertical rule between columns — the heading rules do the separating.
 */
export function MenuGroup({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
  /** Accepted for compatibility with older call sites; the layout no longer uses a vertical rule. */
  divided?: boolean;
}) {
  return (
    <div className="px-1">
      <p className="mx-1 mb-1 whitespace-nowrap border-b hairline px-1 pb-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </p>
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

/**
 * One answer in a {@link MenuGroup}.
 *
 * `variant="primary"` (default) is a real filter value: chosen → a **filled teal row**. `variant="aux"`
 * is a modifier (Ascending / Descending): chosen → the **hover look** (pale grey + Kale text), so it
 * reads as a modifier rather than as the filter itself. Either way, hovering any row gives the same
 * pale-grey-and-Kale look.
 *
 * Deliberately not `DropdownMenuItem`: Radix items reserve an indicator gutter and set their own
 * focus tint.
 */
export function MenuChoice({
  label,
  selected,
  onSelect,
  icon,
  variant = "primary",
}: {
  label: string;
  selected: boolean;
  onSelect: () => void;
  icon?: React.ReactNode;
  variant?: "primary" | "aux";
}) {
  const close = React.useContext(MenuCloseContext);
  return (
    <button
      type="button"
      role="menuitemradio"
      aria-checked={selected}
      onClick={() => {
        onSelect();
        close?.();
      }}
      className={cn(
        "flex w-full items-center gap-2 whitespace-nowrap rounded-sm px-2 py-1.5 text-left text-sm font-medium transition-colors",
        selected
          ? variant === "aux"
            ? "bg-[#F6F6F6] text-primary"
            : "bg-primary text-primary-foreground"
          : cn("text-foreground", HOVER),
      )}
    >
      {icon}
      {label}
    </button>
  );
}

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
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Clears every question in the sheet. Always shown — a control that appears only once you have
   *  made a mess is a control nobody finds. */
  onReset: () => void;
  resetDisabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      {/* **White**, explicitly — `bg-background` is the app's warm grey and made the sheet read as
          a dimmed panel behind its own teal head (owner, 2026-08-17). */}
      <DrawerContent className="mt-0 flex max-h-[92vh] flex-col bg-white px-0">
        <div className="flex shrink-0 items-center justify-between bg-primary px-5 py-3.5">
          <h2 className="text-base font-bold text-primary-foreground">
            {title}
          </h2>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            aria-label="Close"
            className="grid h-8 w-8 place-items-center rounded-md text-primary-foreground/85 transition-colors hover:bg-white/15 hover:text-primary-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

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
            onClick={() => onOpenChange(false)}
            className="h-12 flex-1 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
          >
            Apply
          </button>
        </div>
      </DrawerContent>
    </Drawer>
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
  options: readonly { value: T; label: string }[];
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

/** Sugar for the whole shape: a trigger, and the groups it opens. */
export function ToolbarMenu({
  label,
  count,
  ariaLabel,
  triggerClassName,
  leadingIcon,
  iconOnlyOnMobile,
  closeOnSelect,
  children,
}: {
  label: string;
  count?: number;
  ariaLabel?: string;
  /** Passed to the trigger — e.g. white text when the toolbar sits on the teal header. */
  triggerClassName?: string;
  /** The mark before the trigger word (filter glyph, or sort direction). */
  leadingIcon?: React.ReactNode;
  /** Shrink the trigger to its glyph and a dot on a phone. */
  iconOnlyOnMobile?: boolean;
  /**
   * Dismiss as soon as an answer is picked. For a menu asking **one** question with one answer
   * (the Options lean filter) there is nothing left to do once you have chosen, so staying open
   * only asks the user to close it. Menus asking several questions must stay open.
   */
  closeOnSelect?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = React.useState(false);
  const close = React.useCallback(() => setOpen(false), []);
  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <ToolbarTrigger
        label={label}
        count={count}
        ariaLabel={ariaLabel}
        className={triggerClassName}
        leadingIcon={leadingIcon}
        iconOnlyOnMobile={iconOnlyOnMobile}
      />
      <DropdownMenuContent align="end" className="w-auto p-1">
        <MenuCloseContext.Provider value={closeOnSelect ? close : null}>
          <MenuColumns>{children}</MenuColumns>
        </MenuCloseContext.Provider>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
