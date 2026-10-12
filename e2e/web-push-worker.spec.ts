import { test, expect } from "@playwright/test";

/**
 * The service worker's half of a reminder (`public/sw.js`), in real Chromium: a push that carries
 * nothing but an event id becomes a notification with the event's words, its two buttons and the
 * icons — fetched over the session, never from the push itself.
 *
 * The push is delivered through the DevTools protocol (`ServiceWorker.deliverPushMessage`), which
 * is exactly what a push service hands the browser, without a push service. The notice the worker
 * fetches is stubbed at the network so the words are known; `ReminderControllerIntegrationTest`
 * pins the real endpoint, owner-scoping included.
 */
// **Full Chromium, not the headless shell.** Playwright's default headless browser is
// `chrome-headless-shell`, which has no notification support at all: `showNotification` never
// produces anything and `getNotifications()` stays empty — the same failure on all three CI
// attempts, while a local run against full Chromium passed. `channel: "chromium"` runs the real
// browser in its new headless mode; `npx playwright install chromium` already installs it.
test.use({ channel: "chromium" });

test("a push becomes a notification with the event's words and two buttons", async ({
  page,
  context,
}) => {
  await context.grantPermissions(["notifications"]);
  const fetched: string[] = [];
  await context.route("**/api/reminders/*/notice", async (route) => {
    fetched.push(new URL(route.request().url()).pathname);
    await route.fulfill({
      json: {
        eventId: 77,
        title: "Call the dentist",
        body: "Today at 14:00 · Important date",
      },
    });
  });

  await page.goto("/");
  await page.evaluate(() =>
    navigator.serviceWorker.ready.then(() => undefined),
  );

  const cdp = await context.newCDPSession(page);
  const registrationId = await new Promise<string>((resolve) => {
    cdp.on("ServiceWorker.workerRegistrationUpdated", ({ registrations }) => {
      const ours = registrations.find(
        (r) => r.scopeURL.endsWith("/") && !r.isDeleted,
      );
      if (ours) resolve(ours.registrationId);
    });
    void cdp.send("ServiceWorker.enable");
  });
  await cdp.send("ServiceWorker.deliverPushMessage", {
    origin: new URL(page.url()).origin,
    registrationId,
    data: JSON.stringify({ type: "reminder", eventId: "77" }),
  });

  const shown = async () =>
    page.evaluate(async () => {
      const registration = await navigator.serviceWorker.ready;
      const list = await registration.getNotifications();
      return list.map((n) => ({
        title: n.title,
        body: n.body,
        tag: n.tag,
        icon: n.icon,
        badge: n.badge,
        requireInteraction: n.requireInteraction,
        actions: (
          n as Notification & { actions?: { title: string }[] }
        ).actions?.map((a) => a.title),
        data: n.data as { eventId: string },
      }));
    });
  await expect.poll(shown).toHaveLength(1);
  const [notification] = await shown();

  expect(fetched).toEqual(["/api/reminders/77/notice"]);
  expect(notification).toMatchObject({
    title: "Call the dentist",
    body: "Today at 14:00 · Important date",
    tag: "reminder-77",
    requireInteraction: true,
    actions: ["Done", "Snooze 10 min"],
    data: { eventId: "77" },
  });
  expect(notification.icon).toMatch(/\/icons\/icon-192\.png$/);
  expect(notification.badge).toMatch(/\/icons\/badge-96\.png$/);
});
