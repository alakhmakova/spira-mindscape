import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * A progress bar in one of the four allowed variants (CLAUDE.md → "Progress bars"):
 *  - **primary**: **Guava** while in progress (`#FFEDEA` track / `#F45D48` fill), flipping to
 *    **Kale** once done (`#8DD3D4` track / `#0A8080` fill).
 *  - **contrast**: coral on teal — Brand-200 `#E5F4F3` track / Reserved-700 `#E4523E` fill. The
 *    owner's pair for the **All-goals** cards (2026-08-17): the two brand colours against each
 *    other rather than two steps of one, so a bar reads at a glance down a long list. It is the
 *    only variant whose track and fill come from different families, and it does **not** flip on
 *    completion — the card says "achieved" in words.
 *  - **muted**: the quieter **Brand** teal (`#E5F4F3` track / `#4CACAC` fill).
 *  - **deep**: Brand-200 `#E5F4F3` track / **Kale-500 `#0A8080`** fill — the goal card's ring
 *    (owner, 2026-10-09, measured off her reference). Her picture's ring is a dark teal arc on a
 *    near-white track: sampled, `#487679` on `#EAF1F6`. Neither documented pair reaches both ends
 *    of that — **muted**'s fill is 18 L* lighter than her arc, and **Kale**'s track is 15 L*
 *    darker than her track — so this is a FIFTH pair, built from two steps the four already use.
 *    It is here because she asked for the picture; say so rather than quietly widening the rule.
 *
 * The old fill was a Tailwind orange (`#EA580C`) that is not in the palette; the track was a grey
 * `bg-secondary`. Both are gone — a progress bar is never any other colour.
 */
export type ProgressTone = "primary" | "contrast" | "muted" | "deep";

/** [track, fill] for a tone, exact palette hexes. The one place the pairs are written down. */
function pair(tone: ProgressTone, done: boolean): [string, string] {
  return tone === "muted"
    ? ["#E5F4F3", "#4CACAC"] // Brand
    : tone === "deep"
      ? ["#E5F4F3", "#0A8080"] // Brand track, Kale fill
      : tone === "contrast"
        ? ["#E5F4F3", "#E4523E"] // Contrast — coral on teal
        : done
          ? ["#8DD3D4", "#0A8080"] // Kale (done)
          : ["#FFEDEA", "#F45D48"]; // Guava (in progress)
}

export function ProgressBar({
  value,
  className,
  tone = "primary",
}: {
  value: number; // 0..1
  className?: string;
  tone?: ProgressTone;
}) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  const [track, fill] = pair(tone, pct >= 100);

  return (
    <div
      className={cn(
        "relative h-2 w-full overflow-hidden rounded-full",
        className,
      )}
      style={{ backgroundColor: track }}
    >
      <div
        className="absolute inset-y-0 left-0 rounded-full transition-all duration-500"
        style={{ width: `${pct}%`, backgroundColor: fill }}
      />
    </div>
  );
}

/**
 * The same four variants drawn as a ring — the goal card's measure (owner, 2026-10-09), where a
 * bar would have had to span a column of its own.
 *
 * The track is a full circle and the fill an arc over it, both as `stroke`, so the ring is one
 * shape in two colours rather than two stacked elements. The SVG is `aria-hidden`: whatever the
 * caller centres inside it — the percentage — is the readable text, and a second announcement of
 * the same number would be noise.
 */
export function ProgressRing({
  value,
  tone = "primary",
  size = 100,
  stroke = 13,
  className,
  children,
}: {
  value: number; // 0..1
  tone?: ProgressTone;
  size?: number;
  stroke?: number;
  className?: string;
  children?: ReactNode;
}) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  const [track, fill] = pair(tone, pct >= 100);

  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;

  return (
    <div
      className={cn("relative shrink-0", className)}
      style={{ width: size, height: size }}
    >
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        aria-hidden="true"
        // Start the arc at twelve o'clock rather than at three.
        className="-rotate-90"
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={track}
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={fill}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - pct / 100)}
          className="transition-all duration-500"
        />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        {children}
      </div>
    </div>
  );
}
