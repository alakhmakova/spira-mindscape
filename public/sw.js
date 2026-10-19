/**
 * Spira's service worker — reminders only (specs/2026-10-10-upcoming-notifications/plan.md §5).
 *
 * It caches nothing and intercepts no request: no `fetch` handler, so the app loads exactly as it
 * would without it. It does three things:
 *
 *  - **push** — a push carries an event id and nothing else, so no goal text sits in a push
 *    service's queue. The words are fetched here, over the user's own session; if that fails (signed
 *    out, offline) a generic notification is shown instead, because a push must always show one.
 *  - **notificationclick** — the buttons depend on when the reminder is (owner, 2026-10-19).
 *    Ahead of the event's day it only informs: **I know** is answered from here without opening
 *    the app, and the next reminder still comes. On the day it asks for a decision: **Open** opens
 *    the event's own card (Done / Won't do / Postpone). **Snooze…** is offered either way and opens
 *    the "decide now" page on its picker, so the user sets how long. A tap on the notification
 *    itself opens that page as it is. Either way an open Spira tab is focused, or one is opened.
 *
 * A reminder carries the bell-and-pencil illustration as its **big picture under the text**
 * (`image`), and no `icon`: the sprout in the icon's place, on the right, said nothing about a
 * reminder (owner, 2026-10-19). The badge stays — it is the status-bar mark, not the picture.
 *  - tells any open Spira tab that the due list changed, so its page updates at once.
 */

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) =>
  event.waitUntil(self.clients.claim()),
);

async function tellTabs(message) {
  const tabs = await self.clients.matchAll({
    type: "window",
    includeUncontrolled: true,
  });
  for (const tab of tabs) tab.postMessage(message);
}

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = {};
  }
  // Settings → Notifications → "Send a test notification": proves the whole path to this device.
  if (data.type === "test") {
    event.waitUntil(
      self.registration.showNotification("Spira", {
        body: "Test notification — your reminders will arrive like this.",
        tag: "spira-test",
        // Looks exactly like a reminder, since that is what it promises.
        image: "/illustrations/reminder.png",
        badge: "/icons/badge-96.png",
        data: { eventId: "" },
      }),
    );
    return;
  }
  const eventId = typeof data.eventId === "string" ? data.eventId : "";
  event.waitUntil(
    (async () => {
      let title = "Spira reminder";
      let body = "A date you chose not to miss.";
      let onTheDay = false;
      if (/^\d+$/.test(eventId)) {
        try {
          const res = await fetch(`/api/reminders/${eventId}/notice`, {
            credentials: "same-origin",
          });
          if (res.ok) {
            const notice = await res.json();
            title = notice.title || title;
            body = notice.body || body;
            onTheDay = notice.onTheDay === true;
          }
        } catch {
          // Offline or signed out: the generic words above still say what this is.
        }
      }
      await self.registration.showNotification(title, {
        body,
        tag: eventId ? `reminder-${eventId}` : "reminder",
        renotify: true,
        requireInteraction: true,
        image: "/illustrations/reminder.png",
        badge: "/icons/badge-96.png",
        vibrate: [200, 100, 200],
        data: { eventId },
        actions: eventId
          ? onTheDay
            ? [
                { action: "open", title: "Open" },
                { action: "snooze", title: "Snooze…" },
              ]
            : [
                { action: "dismiss", title: "I know" },
                { action: "snooze", title: "Snooze…" },
              ]
          : [],
      });
      await tellTabs({ type: "reminders-changed" });
    })(),
  );
});

/** The CSRF token, read the way the page reads it — a service worker has no `document.cookie`. */
async function csrfToken() {
  try {
    if (self.cookieStore) {
      const cookie = await self.cookieStore.get("XSRF-TOKEN");
      return cookie ? decodeURIComponent(cookie.value) : "";
    }
  } catch {
    // Fall through: without a token the server answers 403 and the page is opened instead.
  }
  return "";
}

async function answer(eventId, action) {
  try {
    const res = await fetch(`/api/reminders/${eventId}/answer`, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": await csrfToken(),
      },
      body: JSON.stringify({ action }),
    });
    return res.ok;
  } catch {
    return false;
  }
}

/** `then` — "snooze" opens on the snooze picker, "open" on the event's own card. */
async function openReminder(eventId, then = "") {
  const url = eventId
    ? `/remind/${eventId}${then === "snooze" ? "?snooze=1" : then === "open" ? "?open=1" : ""}`
    : "/";
  const tabs = await self.clients.matchAll({
    type: "window",
    includeUncontrolled: true,
  });
  const tab = tabs.find((t) => new URL(t.url).origin === self.location.origin);
  if (tab) {
    await tab.focus();
    try {
      await tab.navigate(url);
    } catch {
      // A tab this worker does not control yet cannot be navigated; the page does it itself.
      tab.postMessage({ type: "open-reminder", eventId, then });
    }
    return;
  }
  await self.clients.openWindow(url);
}

self.addEventListener("notificationclick", (event) => {
  const eventId =
    (event.notification.data && event.notification.data.eventId) || "";
  event.notification.close();
  event.waitUntil(
    (async () => {
      const known = /^\d+$/.test(eventId);
      // Snooze is never answered blind: how long is the user's choice, made on the page. Open is
      // a decision about the event, made on its card.
      if ((event.action === "snooze" || event.action === "open") && known) {
        await openReminder(eventId, event.action);
        return;
      }
      if ((event.action === "dismiss" || event.action === "done") && known) {
        if (await answer(eventId, event.action)) {
          await tellTabs({ type: "reminders-changed" });
          return;
        }
        // The answer did not go through (signed out, no token): let the page ask instead.
      }
      await openReminder(known ? eventId : "");
    })(),
  );
});
