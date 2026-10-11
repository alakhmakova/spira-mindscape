/**
 * The page's opening line (owner, 2026-10-09): a greeting by the time of day, with the user's
 * first name — "Good afternoon, Anthony" in her reference. Without a name it is the greeting alone.
 */
export function greeting(name: string | undefined, now: Date = new Date()) {
  const hour = now.getHours();
  const part =
    hour >= 5 && hour < 12
      ? "Good morning"
      : hour >= 12 && hour < 18
        ? "Good afternoon"
        : "Good evening";
  const first = name?.trim().split(/\s+/)[0];
  return first ? `${part}, ${first}` : part;
}
