/**
 * The reorder arithmetic behind the Options list's drag — kept out of `OptionsList.tsx` so it can
 * be unit-tested without layout, and so that file exports only components (fast refresh).
 */

export function moveInArray<T>(arr: T[], from: number, to: number): T[] {
  const next = [...arr];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/**
 * The slot a dragged card should occupy given how far the pointer has travelled.
 * `totalDeltaPx` is the pointer's Y displacement from where the drag started (any
 * page scroll during the drag is folded in by the caller); `stepPx` is the travel
 * that shuffles one slot. Rounds to the nearest slot and clamps to the list bounds
 * so a downward drag moves the card down and an upward drag moves it up. Extracted
 * (and exported) so this math — the source of the "down-drag froze" regression —
 * is unit-testable without real layout.
 */
export function reorderTargetIndex(
  fromIndex: number,
  totalDeltaPx: number,
  stepPx: number,
  length: number,
): number {
  const slots = Math.round(totalDeltaPx / stepPx);
  return Math.max(0, Math.min(length - 1, fromIndex + slots));
}
