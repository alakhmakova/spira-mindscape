import type { PushState } from "./web-push";

/**
 * When the one-time "Turn on notifications" hint is offered (`NotificationsHint.tsx`): this
 * browser has not allowed notifications, and the hint has not been shown in it before.
 */

const SEEN_KEY = "spira:notifications-hint-seen";

/** Whether this browser should be offered the hint: not allowed yet, and not shown before. */
export function shouldOfferNotificationsHint(state: PushState | null): boolean {
  if (state !== "ask" && state !== "denied") return false;
  try {
    return window.localStorage.getItem(SEEN_KEY) !== "1";
  } catch {
    // No storage (a private window): the hint could not remember being shown, so it would come
    // back on every press. Better not to show it at all.
    return false;
  }
}

export function markNotificationsHintSeen() {
  try {
    window.localStorage.setItem(SEEN_KEY, "1");
  } catch {
    // See above — nothing to remember it in.
  }
}
