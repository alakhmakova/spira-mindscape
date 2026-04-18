import type { Goal, Target } from "./types";

export function targetProgress(t: Target): number {
  if (t.type === "binary") return t.done ? 1 : 0;
  if (t.type === "numeric") return t.total > 0 ? Math.min(1, t.current / t.total) : 0;
  if (t.items.length === 0) return 0;
  return t.items.filter((i) => i.done).length / t.items.length;
}

export function goalProgress(g: Goal): number {
  if (g.targets.length === 0) return 0;
  let weightSum = 0;
  let acc = 0;
  for (const t of g.targets) {
    const w = t.weight ?? 1;
    weightSum += w;
    acc += targetProgress(t) * w;
  }
  return weightSum > 0 ? acc / weightSum : 0;
}
