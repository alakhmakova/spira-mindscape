/**
 * The sort and filter chrome shared by every list on the web — the twin of Android's
 * `ui/components/SpiraListToolbar.kt`, so the two surfaces read as one design.
 *
 * Two things define it (owner, 2026-08-13):
 *
 *  1. **A trigger is a word, not a button.** Kale text and a small solid chevron, no border, no
 *     fill, no pill — and teal in *every* state, including "nothing chosen". The triggers used to
 *     be bordered chips that went from near-black to teal once something was picked, which made an
 *     untouched toolbar the heaviest row on the page.
 *  2. **A menu is columns, not a list.** A sort menu asks two questions (what to order by, and
 *     which way) and a target filter asks three; each is its own short column under its own word,
 *     with a hairline between. Stacked, they were a dozen rows where the user had to remember
 *     which group each row belonged to.
 */

import * as React from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ChevronDownSolid } from "@/components/spira/brand-icons";
import { cn } from "@/lib/utils";

/**
 * The word-and-chevron a toolbar menu opens from.
 *
 * `asChild` on the Radix trigger, so this really is the button — wrapping it would put a second
 * focusable element in the tab order.
 */
export function ToolbarTrigger({
  label,
  count,
  ariaLabel,
  className,
}: {
  label: string;
  /** Filters narrowing the list. Shown in brackets — "Filter (2)" — and hidden at zero. */
  count?: number;
  ariaLabel?: string;
  className?: string;
}) {
  return (
    <DropdownMenuTrigger asChild>
      <button
        type="button"
        aria-label={ariaLabel}
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-1.5 py-1.5 text-sm font-semibold",
          "text-primary transition-colors hover:text-primary/75",
          "outline-none focus-visible:ring-2 focus-visible:ring-ring",
          className,
        )}
      >
        {count ? `${label} (${count})` : label}
        <ChevronDownSolid className="h-3 w-3 shrink-0" />
      </button>
    </DropdownMenuTrigger>
  );
}

/** The menu body: its groups laid out side by side, each separated by a hairline. */
export function MenuColumns({ children }: { children: React.ReactNode }) {
  return <div className="flex items-stretch">{children}</div>;
}

/** One column of a {@link MenuColumns} menu: its question, then that question's answers. */
export function MenuGroup({
  title,
  children,
  /** A hairline down the left — put it on every group but the first. */
  divided = false,
}: {
  title: string;
  children: React.ReactNode;
  divided?: boolean;
}) {
  return (
    <div
      className={cn("min-w-0 px-1", divided && "border-l hairline ml-1 pl-2")}
    >
      <p className="px-2 pb-1 pt-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </p>
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

/**
 * One answer in a {@link MenuGroup}. The chosen one is a **filled teal row** — the same mark
 * Android's menu uses, and readable at a glance in a way a tick on the far side is not.
 *
 * Deliberately not `DropdownMenuItem`: Radix items reserve an indicator gutter and set their own
 * focus tint, and three columns of that would not fit across a phone-width menu.
 */
export function MenuChoice({
  label,
  selected,
  onSelect,
  icon,
}: {
  label: string;
  selected: boolean;
  onSelect: () => void;
  icon?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitemradio"
      aria-checked={selected}
      onClick={onSelect}
      className={cn(
        "flex w-full items-center gap-2 whitespace-nowrap rounded-sm px-2 py-1.5 text-left text-sm font-medium transition-colors",
        selected
          ? "bg-primary text-primary-foreground"
          : "text-foreground hover:bg-secondary",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

/** Sugar for the whole shape: a trigger, and the columns it opens. */
export function ToolbarMenu({
  label,
  count,
  ariaLabel,
  children,
}: {
  label: string;
  count?: number;
  ariaLabel?: string;
  children: React.ReactNode;
}) {
  return (
    <DropdownMenu>
      <ToolbarTrigger label={label} count={count} ariaLabel={ariaLabel} />
      <DropdownMenuContent align="end" className="w-auto p-1">
        <MenuColumns>{children}</MenuColumns>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
