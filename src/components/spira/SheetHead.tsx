import * as React from "react";

import { cn } from "@/lib/utils";

import { X } from "./icons";

/**
 * The two heads a Spira sheet wears — see CLAUDE.md → Sheets → "two head types" for the full
 * rule. `"primary"` (default) is the **Kale band**: the shape for a sheet that IS the primary
 * content — a create/edit form, Filter & Sort, the coach itself. `"auxiliary"` is a **plain
 * white band with a hairline underneath**, for a sheet that sits on top of primary content
 * without being that content itself (owner, 2026-09-03: AI providers "это не основной контент" —
 * "is not primary content" — so it must read differently from one). Never pick "auxiliary" for a
 * sheet that creates, edits, or IS the thing the user came here to work on.
 *
 * There is deliberately a single component for each. The app had four hand-rolled copies of the
 * same band and three form sheets wearing a white head instead, so "New goal" and "Filter & Sort"
 * read as parts of two different apps (owner, 2026-08-22). The band is also what the Android
 * sheets draw — `SpiraFilterSheetContent` and `SpiraFormSheet` — with the same measurements, so
 * the phone and the laptop stay comparable:
 *
 * | | `"primary"` | `"auxiliary"` |
 * |---|---|---|
 * | Fill | `bg-primary` (Kale-500 `#0A8080`) | white |
 * | Bottom edge | none | `1px` hairline `#F3F3F3` |
 * | Title | white, bold, 16px, sentence case, left | Salt-1000 `#003737`, bold, 16px, sentence case, left |
 * | Close | a 32px white X, `hover:bg-white/15` | a 32px muted-ink X, `hover:bg-[#003737]/5` |
 * | Padding | 20dp start / 12dp end, 14dp top and bottom | same |
 *
 * `actions` takes anything that belongs to the panel as a whole and sits **before** the X — the
 * filter panel's padlock is the only one today, and it is `"primary"`.
 */
export function SheetHead({
  title,
  onClose,
  actions,
  tone = "primary",
  /**
   * What renders the title. Defaults to a plain `h2`; a **dialog** passes `DialogTitle`, because
   * Radix needs one as the dialog's accessible name and adding a second, screen-reader-only copy
   * beside this band puts two headings called "Set deadline" in the tree. One head, one heading.
   */
  titleComponent: Title = "h2",
}: {
  title: string;
  onClose: () => void;
  actions?: React.ReactNode;
  tone?: "primary" | "auxiliary";
  titleComponent?: React.ElementType;
}) {
  const auxiliary = tone === "auxiliary";
  return (
    <div
      className={cn(
        "flex shrink-0 items-center gap-1 px-5 py-3.5",
        auxiliary ? "border-b border-[#F3F3F3] bg-white" : "bg-primary",
      )}
    >
      <Title
        className={cn(
          "flex-1 text-base font-bold",
          auxiliary ? "text-[#003737]" : "text-primary-foreground",
        )}
      >
        {title}
      </Title>
      {actions}
      <button
        type="button"
        onClick={onClose}
        aria-label="Close"
        className={auxiliary ? AUX_SHEET_HEAD_BUTTON : SHEET_HEAD_BUTTON}
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

/** The head's button shape — white-on-teal, so an `actions` control matches the X beside it. */
export const SHEET_HEAD_BUTTON =
  "grid h-8 w-8 place-items-center rounded-md text-primary-foreground/85 transition-colors hover:bg-white/15 hover:text-primary-foreground";

/** The `"auxiliary"` head's button shape — muted ink on white, matching `ContentModal`'s close. */
export const AUX_SHEET_HEAD_BUTTON =
  "grid h-8 w-8 place-items-center rounded-md text-[#003737]/40 transition-colors hover:bg-[#003737]/5 hover:text-[#003737]";
