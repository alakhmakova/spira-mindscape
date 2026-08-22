import * as React from "react";

import { X } from "./icons";

/**
 * The one head a Spira sheet wears — a **Kale band** with the title in white and an X on the
 * right.
 *
 * There is deliberately a single component for it. The app had four hand-rolled copies of the
 * same band and three form sheets wearing a white head instead, so "New goal" and "Filter & Sort"
 * read as parts of two different apps (owner, 2026-08-22). The band is also what the Android
 * sheets draw — `SpiraFilterSheetContent` and `SpiraFormSheet` — with the same measurements, so
 * the phone and the laptop stay comparable:
 *
 * | | Value |
 * |---|---|
 * | Fill | `bg-primary` (Kale-500 `#0A8080`) — 20dp start / 12dp end, 14dp top and bottom |
 * | Title | white, bold, 16px, sentence case, left |
 * | Close | a 32px white X on the right, `hover:bg-white/15` |
 *
 * `actions` takes anything that belongs to the panel as a whole and sits **before** the X — the
 * filter panel's padlock is the only one today.
 */
export function SheetHead({
  title,
  onClose,
  actions,
}: {
  title: string;
  onClose: () => void;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex shrink-0 items-center gap-1 bg-primary px-5 py-3.5">
      <h2 className="flex-1 text-base font-bold text-primary-foreground">
        {title}
      </h2>
      {actions}
      <button
        type="button"
        onClick={onClose}
        aria-label="Close"
        className={SHEET_HEAD_BUTTON}
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

/** The head's button shape — white-on-teal, so an `actions` control matches the X beside it. */
export const SHEET_HEAD_BUTTON =
  "grid h-8 w-8 place-items-center rounded-md text-primary-foreground/85 transition-colors hover:bg-white/15 hover:text-primary-foreground";
