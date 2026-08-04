import type { Goal, Target } from "./types";

export function targetProgress(t: Target): number {
  if (t.type === "binary") return t.done ? 1 : 0;
  if (t.type === "numeric") {
    const start = t.start ?? (t.current > t.total ? t.current : 0);
    const distance = Math.abs(t.total - start);
    if (distance === 0) return t.current === t.total ? 1 : 0;
    const completed = t.total >= start ? t.current - start : start - t.current;
    return Math.max(0, Math.min(1, completed / distance));
  }
  if (t.items.length === 0) return 0;
  return t.items.filter((i) => i.done).length / t.items.length;
}

/**
 * Whether a target's progress is pinned. An achieved target locks itself so a stray tap can't
 * undo it; anything else is open unless the user locked it deliberately. Either way the user's
 * explicit choice (`progressLocked`) wins, so a finished target can be unlocked to correct it.
 */
export function isProgressLocked(t: Target): boolean {
  return t.progressLocked ?? targetProgress(t) >= 1;
}

/**
 * How many discrete steps a target's progress can take: 4 for a four-task checklist, 1 900 000
 * for a 0 → 1 900 000 SEK goal, 1 for a binary one. This is what decides how many decimals a
 * percentage needs — see {@link formatPercent}.
 */
export function progressSteps(t: Target): number {
  if (t.type === "binary") return 1;
  if (t.type === "numeric") {
    const start = t.start ?? (t.current > t.total ? t.current : 0);
    return Math.abs(t.total - start);
  }
  return t.items.length;
}

/** The same for a goal: its progress is the mean over targets, so the finest-grained target
 *  sets the resolution and the mean divides each step by the number of targets. */
export function goalProgressSteps(g: Goal): number {
  if (g.targets.length === 0) return 0;
  return g.targets.length * Math.max(...g.targets.map(progressSteps));
}

/** Trailing zeros carry no information: "50.00" is noise where "50" says the same thing. */
function trimZeros(s: string): string {
  return s.includes(".") ? s.replace(/\.?0+$/, "") : s;
}

/**
 * A progress fraction as the percentage to show the user.
 *
 * Whole percent is right for a four-task checklist and wrong for a 1 900 000 SEK target: there,
 * 10 000 and 20 000 both print "1 %", so weeks of saving look like nothing happened. `steps` —
 * how many increments the target actually has ({@link progressSteps}) — decides the precision:
 * when one step is worth less than a tenth of a percent we print two decimals, less than a whole
 * percent one decimal, otherwise none. Trailing zeros are trimmed, so a checklist still reads
 * "50 %", not "50.00 %".
 *
 * Whatever the precision, a genuine 0 and a genuine 1 are the ONLY values that may print as "0"
 * and "100" — anything else that would round to them gets more decimals, or "<0.01" / ">99.99".
 */
export function formatPercent(fraction: number, steps?: number): string {
  const clamped = Math.max(0, Math.min(1, fraction));
  const pct = clamped * 100;
  if (pct === 0 || pct === 100) return String(pct);

  // One step, as a percentage of the whole → the smallest change worth showing.
  const stepPct = steps && steps > 0 ? 100 / steps : 100;
  const decimals = stepPct >= 1 ? 0 : stepPct >= 0.1 ? 1 : 2;

  for (let d = decimals; d <= 2; d++) {
    const text = trimZeros(pct.toFixed(d));
    // Never let rounding claim the target is untouched or finished when it isn't.
    if (text !== "0" && text !== "100") return text;
  }
  return pct < 50 ? "<0.01" : ">99.99";
}

export function goalProgress(g: Goal): number {
  if (g.targets.length === 0) return 0;
  let acc = 0;
  for (const t of g.targets) {
    acc += targetProgress(t);
  }
  return acc / g.targets.length;
}
