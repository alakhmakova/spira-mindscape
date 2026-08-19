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
 *
 * The old fill was a Tailwind orange (`#EA580C`) that is not in the palette; the track was a grey
 * `bg-secondary`. Both are gone — a progress bar is never any other colour.
 */
export function ProgressBar({
  value,
  className,
  tone = "primary",
}: {
  value: number; // 0..1
  className?: string;
  tone?: "primary" | "contrast" | "muted";
}) {
  const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
  const done = pct >= 100;

  // [track, fill] per state, exact palette hexes.
  const [track, fill] =
    tone === "muted"
      ? ["#E5F4F3", "#4CACAC"] // Brand
      : tone === "contrast"
        ? ["#E5F4F3", "#E4523E"] // Contrast — coral on teal
        : done
          ? ["#8DD3D4", "#0A8080"] // Kale (done)
          : ["#FFEDEA", "#F45D48"]; // Guava (in progress)

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
