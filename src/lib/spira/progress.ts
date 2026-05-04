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

export function goalProgress(g: Goal): number {
  if (g.targets.length === 0) return 0;
  let acc = 0;
  for (const t of g.targets) {
    acc += targetProgress(t);
  }
  return acc / g.targets.length;
}
