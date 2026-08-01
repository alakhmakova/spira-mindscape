import { useCallback, useEffect, useRef } from "react";

/**
 * Pauses background polling after `idleMs` without user interaction, so an open-but-idle tab
 * stops hitting the network/DB (and stops keeping the Neon compute awake) for nothing.
 *
 * Returns `isActive()` for a poll to gate on: `if (isActive()) poll()`. When interaction resumes
 * after an idle stretch it calls `onResume` once, so the caller can catch up immediately instead
 * of waiting for the next tick. "Interaction" = pointer, key, wheel, or touch — so active reading
 * (wheel-scrolling on desktop, touch on mobile) counts, not just clicks/keys.
 *
 * The same idle-guard was duplicated across the goals poll (AppShell) and the AI transcript poll
 * (AiPanel); this hook carries it once so a change (e.g. which events count) can't drift between
 * them.
 */
export function useActivityGate(
  idleMs: number,
  onResume: () => void,
): () => boolean {
  const lastActivityRef = useRef(Date.now());
  // Keep the latest onResume without re-subscribing the listeners each render.
  const onResumeRef = useRef(onResume);
  onResumeRef.current = onResume;

  useEffect(() => {
    const markActive = () => {
      const wasIdle = Date.now() - lastActivityRef.current > idleMs;
      lastActivityRef.current = Date.now();
      if (wasIdle) onResumeRef.current();
    };
    const events = ["pointerdown", "keydown", "wheel", "touchstart"] as const;
    events.forEach((e) =>
      window.addEventListener(e, markActive, { passive: true }),
    );
    return () =>
      events.forEach((e) => window.removeEventListener(e, markActive));
  }, [idleMs]);

  return useCallback(
    () => Date.now() - lastActivityRef.current <= idleMs,
    [idleMs],
  );
}
