import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * The one pill shape in the app — the web twin of Android's `SpiraBadge`
 * (`ui/components/SpiraBadge.kt`). Anywhere a short word is set in a capsule (a status, a filter
 * answer, a kind, a state) it is **this**, on both surfaces.
 *
 * The shape:
 *
 *  - **A bright 1px outline**, a **very pale fill** from the same ramp's `100` step, fully rounded.
 *  - **The word stays near-black in every tone.** The outline is what carries the meaning;
 *    colouring the type as well only made the label harder to read.
 *  - **Sentence case**, never capitals. An all-caps pill shouts, and a column of them shouts at
 *    once. (`GROW` is the one exception — it is an acronym.)
 *
 * A **filled** capsule is not a pill: it is a button. This is the mistake to avoid — a row of solid
 * green capsules reads as a row of buttons, not as one family of states.
 */
export type PillTone =
  | "teal"
  | "intelligence"
  | "success"
  | "warning"
  | "error"
  | "info"
  | "neutral";

/** Outline + fill per tone: the ramp's solid step, and its nearly-white `100`. */
const TONES: Record<PillTone, string> = {
  teal: "border-[#0A8080] bg-[#F9FDFC]",
  intelligence: "border-[#6E56CF] bg-[#FEFBFF]",
  success: "border-[#007A4B] bg-[#F8FDF7]",
  warning: "border-[#896500] bg-[#FFFBF7]",
  error: "border-[#C53336] bg-[#FFFBFB]",
  info: "border-[#006CC1] bg-[#FDFCFF]",
  neutral: "border-[#6B6B6B] bg-[#FAFAFA]",
};

export function Pill({
  children,
  tone = "teal",
  className,
}: {
  children: React.ReactNode;
  tone?: PillTone;
  className?: string;
}) {
  return (
    <span
      className={cn(
        // **Centred by layout, not by padding.** A fixed height with `items-center` puts the word
        // on the capsule's middle whatever the font's ascent and descent do; tuning `py-` by eye
        // put it low, then high (owner, 2026-08-17).
        "inline-flex h-[30px] items-center rounded-full border px-3 text-xs font-semibold text-foreground",
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/**
 * A pill you can press — one answer of a one-line question (the goal status, the deadline
 * question). Chosen carries its tone; the rest are `neutral`, so the row reads as one family with
 * one member lit rather than as a row of coloured buttons.
 */
export function PillChoice({
  label,
  selected,
  tone = "success",
  onSelect,
  icon,
}: {
  label: string;
  selected: boolean;
  tone?: PillTone;
  onSelect: () => void;
  /**
   * The mark this answer wears on the cards it filters — the Options lean pills carry the very
   * smileys the card's badge does, so the answer and the thing it hides are one mark rather than
   * two words that happen to agree (owner, 2026-08-18).
   */
  icon?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
    >
      <Pill
        tone={selected ? tone : "neutral"}
        className={cn(
          "cursor-pointer whitespace-nowrap transition-colors",
          !selected && "text-muted-foreground hover:text-foreground",
        )}
      >
        {icon && <span className="mr-1.5 inline-flex shrink-0">{icon}</span>}
        {label}
      </Pill>
    </button>
  );
}
