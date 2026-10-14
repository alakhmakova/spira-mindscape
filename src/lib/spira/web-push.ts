/**
 * Web Push on the page's side (specs/2026-10-10-upcoming-notifications/plan.md §5).
 *
 * The service worker (`public/sw.js`) is registered on every signed-in page — that asks for
 * nothing. **Permission is asked only after a gesture**: the first time an event with a reminder
 * is saved, which is the moment the user can see why. A refusal is the browser's to remember; the
 * form then says reminders will only show inside Spira.
 *
 * Nothing here is required for reminders to work: with push off, unsupported or refused, the
 * full-screen "decide now" page still takes over Spira when a reminder is due.
 */
import { getCsrfToken } from "./auth";

export type PushState =
  /** The server has no keys — push is off everywhere, and nothing needs saying. */
  | "off"
  /** This browser has no Web Push (an iPhone outside the Home Screen, say). */
  | "unsupported"
  /** Not asked yet: saving a reminder will ask. */
  | "ask"
  | "granted"
  | "denied";

let serverKey: Promise<string | null> | null = null;

export function pushSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

/** The server's VAPID public key, or null when Web Push is off there. Fetched once. */
function vapidKey(): Promise<string | null> {
  serverKey ??= fetch("/api/push/web/key", { credentials: "include" })
    .then((res) => (res.ok ? res.json() : { enabled: false }))
    .then((body: { enabled?: boolean; publicKey?: string }) =>
      body.enabled && body.publicKey ? body.publicKey : null,
    )
    .catch(() => {
      serverKey = null;
      return null;
    });
  return serverKey;
}

/** Whether this browser can be reminded, and whether it has been asked. */
export async function pushState(): Promise<PushState> {
  if (!(await vapidKey())) return "off";
  if (!pushSupported()) return "unsupported";
  if (Notification.permission === "granted") return "granted";
  if (Notification.permission === "denied") return "denied";
  return "ask";
}

function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4));
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

async function sendSubscription(subscription: PushSubscription) {
  await fetch("/api/push/web/subscribe", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCsrfToken(),
    },
    body: JSON.stringify(subscription.toJSON()),
  });
}

async function subscribe(): Promise<void> {
  const key = await vapidKey();
  if (!key) return;
  const registration = await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: keyBytes(key),
    });
  }
  await sendSubscription(subscription);
}

/**
 * Registers the service worker and, if this browser already allowed notifications, makes sure
 * the server has its subscription (it may have been dropped, or the keys rotated). Asks nothing.
 */
export async function startWebPush(): Promise<void> {
  if (!pushSupported()) return;
  try {
    await navigator.serviceWorker.register("/sw.js");
    if (Notification.permission === "granted") await subscribe();
  } catch {
    // Push is an extra: the in-app page covers a reminder whether or not this worked.
  }
}

/**
 * Called from the Save click of a form that has reminders. Asks for permission there — inside
 * the gesture, before anything is awaited — and subscribes if it is given.
 */
export function enableWebPushFromGesture(): void {
  if (!pushSupported() || Notification.permission === "denied") return;
  const asking =
    Notification.permission === "default"
      ? Notification.requestPermission()
      : Promise.resolve(Notification.permission);
  void asking
    .then((permission) => (permission === "granted" ? subscribe() : undefined))
    .catch(() => undefined);
}
