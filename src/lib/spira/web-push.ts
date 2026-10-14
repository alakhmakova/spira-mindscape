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
  /** The server has no keys — push is off for everyone, and the user must be told so. */
  | "off"
  /** The page is on plain http (a LAN address, say): browsers allow push only over https. */
  | "insecure"
  /** This browser has no Web Push (an iPhone outside the Home Screen, say). */
  | "unsupported"
  /** Not asked yet: saving a reminder will ask. */
  | "ask"
  | "granted"
  | "denied";

let serverKey: Promise<string | null> | null = null;
/** The answer once known: a key, `null` when push is off on the server, `undefined` before. */
let knownServerKey: string | null | undefined;

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
    .then((key) => {
      knownServerKey = key;
      return key;
    })
    .catch(() => {
      serverKey = null;
      return null;
    });
  return serverKey;
}

/** Whether this browser can be reminded, and whether it has been asked. */
export async function pushState(): Promise<PushState> {
  if (!(await vapidKey())) return "off";
  if (typeof window !== "undefined" && !window.isSecureContext)
    return "insecure";
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

function sameKey(a: ArrayBuffer | null | undefined, b: Uint8Array): boolean {
  if (!a || a.byteLength !== b.length) return false;
  const view = new Uint8Array(a);
  return view.every((byte, i) => byte === b[i]);
}

async function subscribe(): Promise<boolean> {
  const key = await vapidKey();
  if (!key) return false;
  const registration = await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  // A subscription made under a different server key (the keys were set up or changed since) is
  // dead: the push service refuses every message signed with the new one. Replace it.
  if (
    subscription &&
    !sameKey(subscription.options.applicationServerKey, keyBytes(key))
  ) {
    await subscription.unsubscribe();
    subscription = null;
  }
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: keyBytes(key),
    });
  }
  await sendSubscription(subscription);
  return true;
}

/**
 * Registers the service worker and, if this browser already allowed notifications, makes sure
 * the server has its subscription (it may have been dropped, or the keys rotated). Asks nothing.
 */
export async function startWebPush(): Promise<void> {
  if (!pushSupported()) return;
  try {
    await navigator.serviceWorker.register("/sw.js");
    void vapidKey(); // known by the time a Save click needs it
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
  // Asking for a permission the server cannot use would only teach the user to say no.
  if (knownServerKey === null) return;
  if (!pushSupported() || Notification.permission === "denied") return;
  const asking =
    Notification.permission === "default"
      ? Notification.requestPermission()
      : Promise.resolve(Notification.permission);
  void asking
    .then((permission) => (permission === "granted" ? subscribe() : undefined))
    .catch(() => undefined);
}

/**
 * From a button (Settings → Notifications): asks for permission if it has not been asked, and
 * subscribes this browser. Resolves to the state afterwards, so the page can say what happened.
 */
export async function enableWebPush(): Promise<PushState> {
  const state = await pushState();
  if (state !== "ask" && state !== "granted") return state;
  const permission =
    Notification.permission === "default"
      ? await Notification.requestPermission()
      : Notification.permission;
  if (permission !== "granted")
    return permission === "denied" ? "denied" : "ask";
  await navigator.serviceWorker.register("/sw.js");
  await subscribe();
  return "granted";
}

export type TestPushResult = {
  enabled: boolean;
  subscriptions: number;
  sent: number;
};

/** Sends a test notification to every browser of this account that is subscribed. */
export async function sendTestPush(): Promise<TestPushResult> {
  const res = await fetch("/api/push/web/test", {
    method: "POST",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() },
  });
  if (!res.ok) throw new Error(`Test push failed (${res.status})`);
  return (await res.json()) as TestPushResult;
}
