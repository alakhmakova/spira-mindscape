# Plan: reminders for Upcoming events

Status: **the web is built** (2026-10-11, owner: "Реализуй уведомления") — §1, §2, §3, §4, §5a's
mobile-web half and §5b, with the tests §7 lists for the web. **Email and the Android app are still
deferred**, as decided. What it takes to switch Web Push on in production — VAPID keys in Secret
Manager and the Cloud Scheduler job — is the owner's, and is written out in
`docs/reminders-and-web-push.md`.

Where the build differs from this plan, and why:

- **`amount` can be 0** ("On the day at 09:00", "At the time of the event"); the plan's 1–999 is
  what the Custom dialog asks for. The server accepts 0–999.
- **The Custom dialog is a modal on a phone too**, not a sheet: it is a value picked inside a form
  that is already open, which is CLAUDE.md 3e-ter's rule for the date picker.
- **No Web Push library.** The encryption (RFC 8291) and VAPID (RFC 8292) are on the JDK's own
  crypto in `WebPushCrypto.java`, checked byte for byte against the RFC's worked example —
  `nl.martijndwars:web-push` would have brought BouncyCastle and an HTTP client for ~200 lines.
- **A reminder found more than an hour late is marked without a push** (the server was down, or
  the scheduler paused): a notification about a moment long gone is noise. It still shows on the
  "decide now" page.
- **A reminder never fires for a moment from before it existed** — "1 week before" added three
  days ahead of the date stays silent rather than going off as it is saved.
- **The big picture** (`public/images/reminder.png`) is not used yet: the owner supplies it.

---

**Priority: the web first. The Android app is planned here but NOT to be built yet** (owner,
2026-10-10: "андроид пока делать не нужно, приоритет у веба"). Every Android section below is
marked **Deferred** and is kept so the design does not have to be rediscovered later.

Reference: Google Calendar's reminder picker (presets + "Custom…" with a number, a unit and a
channel), drawn in Spira's own form style — the Gusto "Edit compensation" fields and radios the Add
event form already follows (CLAUDE.md → Design → 3g).

---

## 0. The answers first

| Question | Answer |
|---|---|
| **Is email free?** | **Yes, at this app's volume.** Brevo's free plan sends 300 a day, and Resend's sends 100 a day (3,000 a month). Either covers one person's reminders many times over. The one cost that may appear is a **domain** (~$10 a year): since 2024 Gmail and Yahoo reject or spam-folder bulk-looking mail without SPF/DKIM on a domain you control. Brevo can send from a verified single address without a domain, but deliverability is worse. If a domain is ruled out, Gmail SMTP with an app password is free (500 a day) and fine for a personal app. |
| **Mobile web, app closed — can it notify?** | **Android Chrome: yes.** Web Push and a service worker deliver to the system tray with the tab and the browser closed. **iPhone: only if the site is added to the Home Screen** (iOS 16.4+), never from a plain Safari tab. |
| **Can a notification block the screen until the user decides?** | **From the web: no, never.** A browser cannot draw over the OS or the lock screen. **From the Android app: yes, as alarm clocks do** — a full-screen activity over the lock screen that turns the screen on and stays until the user picks Done / Snooze / Open. It cannot literally lock the phone: Home and the power button still work, and nothing short of device-admin or kiosk mode can take them away. Those are not allowed for this purpose, and they are not what an alarm does either. |
| **Desktop?** | Web Push shows an **OS notification** (Windows notification centre, macOS Notification Centre) while the browser process runs, even with Spira's tab closed. It **cannot lock the computer** or draw over other apps. What it can do is show a **blocking modal inside Spira**: a due Upcoming reminder takes over the app until it is answered. |

**So a reminder that truly takes over the phone is an Android-app feature**, and it is
achievable there. The web gets OS notifications, plus a full-screen "decide now" page that one tap
on the notification opens, and that blocks Spira itself until it is answered. §5a walks through
both, scenario by scenario. **The web is built first; Android is deferred.**

---

## 1. One problem to settle before the presets: our events have no time

Every Upcoming date is a **day**, stored at local midnight (`event_date`; the deadline a link
reads). "5 minutes before" a day means 23:55 the evening before, which is not what anyone means.
Google Calendar has the same split: timed events offer "5 minutes before…", and all-day events
offer "On the day at 09:00 / The day before at 09:00 / 1 week before at 09:00".

**Decided (owner, 2026-10-10): an optional time on EVERY Upcoming date** — important dates and
linked goals, targets and tasks alike. "All day" is switched on by default; switching it off
shows a time field. Then the presets follow the event:

| Event | Presets | Custom |
|---|---|---|
| **All day** (the default) | On the day at 09:00 · 1 day before at 09:00 · 2 days before · 1 week before | number + **Days / Weeks** before, **at** a time of day |
| **With a time** | 5 minutes · 10 minutes · 1 hour · 2 hours · 1 day · 1 week before | number + **Minutes / Hours / Days / Weeks** before |

**The time belongs to the Upcoming entry, not to the item.** A goal's, target's or task's
deadline stays a day everywhere else in the app; `upcoming_event.event_time` holds "remind me
about this deadline as if it were at 14:00". Moving the deadline moves the date and keeps the
time. Removing the event drops the time with it (and, as today, the deadline).

---

## 2. The form — reminders live in Add event

A **Reminders** block under the date, in the form's own field style (CLAUDE.md 3g: 40px, a 1px
`#9F9F9F` frame, 4px corners, a 16px semibold label).

- Each reminder is **one row**: a Spira `Select` reading "1 day before at 09:00 · Notification",
  with an X to remove it. Never a raw `<select>`.
- Under the rows, **"Add reminder"** in the same link style as "Add event": the outline
  `circle-plus` and an underlined Kale word.
- **At most 5** reminders per event. The link disappears at five.
- The Select lists the presets, then **"Custom…"**, which opens the custom dialog.
- **Every new event gets one reminder by default** (decided, owner 2026-10-10), and the form
  **says plainly when it will arrive** — this is the part that matters:
  - All day: **"On the day at 09:00"**. Timed: **"1 hour before"**.
  - The row is already in the form when it opens, so the default is never invisible.
  - Under the rows, one sentence states the **actual moment**, recomputed live as the date, the
    time or the reminders change: *"You'll be reminded on Monday, October 19 at 09:00."* — or,
    for two, *"…on October 12 at 09:00 and October 19 at 09:00."* No date yet: *"Pick a date to
    see when you'll be reminded."*
  - Removing the last reminder is allowed, and the sentence then says *"No reminder — this date
    will only show in Upcoming."*
  - The time zone in which 09:00 is meant is the device's, shown on hover / long-press of the
    sentence ("09:00 Europe/Berlin").
- **Upcoming rows show a small bell** when the event has reminders. Tapping the row opens the same
  form to edit them. That needs an **Edit event** mode, which the form does not have yet.

### The Custom dialog

A modal on a laptop and a sheet on a phone, like Add event itself:

- **Number** — a 40px field, digits only, 1–999, validated inline ("Enter a number from 1 to
  999"). Never an empty save.
- **Unit** — **radio buttons** in the Edit compensation style: a 20px circle with a 1px `#9F9F9F`
  edge, and when chosen a 2px Kale ring with a Kale dot. One per line: *Minutes before / Hours
  before / Days before / Weeks before*. Minutes and Hours only on a timed event.
- **At** — the time of day, only for days and weeks on an all-day event.
- **Channel** — **not asked yet.** Email and the Android app are both deferred (owner,
  2026-10-10), which leaves one channel, and a radio group with one answer is not a question.
  Every reminder is a notification plus the full-screen "decide now" page (§5a) — **for every
  Upcoming event, not only important dates** (decided, owner 2026-10-10). When email or Android
  arrive, the radios come back: *As notification / As email*, and on Android full screen is the
  behaviour of every reminder rather than a separate choice.
- **Cancel / Done** at the foot, Done disabled until the number is valid. Done writes the row
  into the form; **nothing is saved until the form's own Save**.

**Accessibility:** the unit radio set (and the channel set, once it exists) is real `role="radiogroup"` with arrow keys (the `TypeChoice`
pattern), the number field has a visible label and `aria-invalid` plus `aria-describedby` on
error, and focus returns to the "Custom…" option on close. `a11y-home.spec.ts` gets two new
surfaces, "Add event — reminders" and "Custom reminder".

---

## 3. Data

### `upcoming_reminder` (Flyway V41)

| Column | Notes |
|---|---|
| `id` | |
| `event_id` | FK `upcoming_event` **ON DELETE CASCADE** — removing an event removes its reminders |
| `user_id` | FK `app_user` CASCADE; every query is owner-scoped, as with events |
| `amount`, `unit` | `unit` CHECK in (`minute`,`hour`,`day`,`week`); `amount` 1–999 |
| `at_minute_of_day` | 0–1439, all-day events only; NULL for a timed one |
| `channel` | CHECK in (`notification`,`email`,`fullscreen`) |
| `last_fired_for` | the fire instant this reminder was last sent for — see below |
| `answered_at` | when the fire in `last_fired_for` was answered (Done, or Open); NULL while it is **due and unanswered** — what the web's "decide now" page blocks on (§5a) |
| `snoozed_until` | a Snooze sets it; the reminder fires again then, and stays unanswered |
| `created_at`, `updated_at` | |

`upcoming_event` gains `event_time` (nullable `TIME`, any kind — §1).
`app_user` gains `time_zone` (IANA, e.g. `Europe/Berlin`). The client sends it, and "the day
before at 09:00" is meaningless without it.

**The fire time is computed, never stored.** A linked event's date is the item's deadline, read
live (3g), so a stored fire time goes stale the moment a deadline moves. The dispatcher computes
`date − offset` (in the user's zone) on each run. `last_fired_for` makes it idempotent: send when
`now ≥ fire_at > last_fired_for`, then write `last_fired_for = fire_at`. If a deadline moves
*later*, the new `fire_at` is greater, and the reminder fires again at the right time. If it moves
earlier and past, it does not fire twice.

### Web push subscriptions — `web_push_subscription`

`user_id`, `endpoint` (unique), `p256dh`, `auth`, `user_agent`, `created_at`. Android keeps the
existing `device_token` (V15).

### Limits

5 reminders per event; 500 events per user already. So a dispatcher run touches at most a few
thousand rows for the whole app.

---

## 4. When they fire — the scheduler

**Cloud Run scales to zero** (`--min-instances 0`), so a `@Scheduled` method in the app would not
run while nobody is using it. This app has no scheduler yet (see the note in
`SharedRateLimitStore`).

**Recommendation: Cloud Scheduler → `POST /internal/reminders/dispatch` every minute.**

- **Free**: Cloud Scheduler gives 3 jobs free per billing account. 1,440 wake-ups a day at
  roughly a second each sit far inside Cloud Run's free 180,000 vCPU-seconds a month.
- **Authenticated by Google, not by a user session.** The job sends an OIDC token for a dedicated
  service account. The endpoint verifies issuer, audience and the service account's email, is
  exempt from CSRF only (not from authentication), and returns 403 to everything else. It is the
  app's first non-user caller, so it gets its own `SecurityFilterChain` matcher and its own
  integration test.
- **Every 5 minutes instead** halves nothing that matters and makes "5 minutes before" up to 5
  minutes late. One minute is the right cadence.
- Considered and not chosen: **Cloud Tasks** (exact per-reminder timing, also free at this volume,
  but every reminder edit has to cancel and re-create a task); **GitHub Actions cron** (free, but
  routinely 5–30 minutes late).

**Android does not depend on this** — see §5.

---

## 5. How they are delivered

### 5a. The full-screen reminder, scenario by scenario

The owner's question (2026-10-10): if the screen cannot be locked, can the reminder at least
fill the screen **once the phone is unlocked**? It depends on the surface and on what the phone
was doing when the reminder fired.

#### Mobile web — **build this first**

A browser cannot draw over the home screen or another app, before unlock or after. Everything
the web can do happens through the notification and inside Spira:

| When the reminder fires | What the user sees | What they can do |
|---|---|---|
| Phone **locked**, screen off | A notification on the lock screen (if the phone's settings show them there), with vibration (`vibrate`, honoured by Chrome on Android) | Unlock: it is still in the notification shade. It does **not** fill the screen |
| Phone **unlocked**, another app open | A heads-up banner at the top, then the shade | **Done** / **Snooze 10 min** straight from the banner (notification `actions`, supported by Chrome on Android, not on iPhone) — or tap it |
| **Tap** on the notification, in either case | Spira opens (or comes to the front) on a **full-screen "decide now" page** for that event: `/remind/$eventId` | Done / Snooze / Open. The page has no way out except those three |
| **Spira already open** when it fires | The same page takes over the app at once, with no tap | Nothing else in Spira is usable until it is answered — the in-app blocking modal below |
| **iPhone** | Only for Spira **added to the Home Screen** (iOS 16.4+): a plain notification, no action buttons | Tap → the same "decide now" page |

So on mobile web the full-screen moment is **one tap away**, never forced. That is the honest
ceiling of the platform.

How it is built:

- The service worker's `notificationclick` calls `clients.matchAll()` and **focuses an open Spira
  tab and navigates it** to `/remind/$eventId`, or `clients.openWindow()` if there is none. An
  action button (`event.action === "done" | "snooze"`) is answered from the service worker with a
  `fetch` to the API, **without opening the app at all**.
- `requireInteraction: true` on the notification, so on a laptop it stays on screen until it is
  dealt with rather than fading after a few seconds (Chrome on Android ignores it; the shade keeps
  it anyway).
- A **due, unanswered reminder blocks Spira** — for any Upcoming event: every route renders the "decide now" page
  over itself until the event is answered, so opening the app any other way — from the Home
  Screen, from a bookmark — lands on it too. "Unanswered" is a server fact (`answered_at` on the
  reminder's fire), so it holds across devices: answer it on the laptop and the phone lets go.
- The page itself is a Spira surface (Kale head, the app's buttons). Its exact look is for later,
  as agreed.

#### Android app — **Deferred: planned, not to be built yet**

Here the reminder **can** fill the screen, as an alarm clock does — with one platform rule that
decides how:

| When the reminder fires | Default behaviour | With "Display over other apps" granted |
|---|---|---|
| Phone **locked** / screen off | The screen turns on and the reminder fills it over the lock screen. **After unlock it is still there** until Done / Snooze / Open is chosen — exactly how an alarm behaves | The same |
| Phone **unlocked and in use** | Android shows a full-screen intent as a **heads-up banner** instead, so it does not interrupt what the user is doing (the system's decision, not the app's) | Spira **opens the reminder full screen over whatever app is in front** |
| Spira itself open | The full-screen reminder at once | The same |

- **"Display over other apps"** is `SYSTEM_ALERT_WINDOW`, granted once by the user in settings;
  the app explains why and sends them there (`ACTION_MANAGE_OVERLAY_PERMISSION`) when the first
  full-screen reminder is set. It is what lifts Android 10+'s ban on starting an activity from the
  background. Google Play restricts it too; Spira ships through Firebase App Distribution, so that
  does not apply (and the same caveat as the full-screen intent below holds if that ever changes).
- **Back does nothing; Home still works.** Nothing can take the Home button away. Leaving with
  Home does not answer it: the reminder stays in the shade and comes back to the front at the next
  snooze interval until it is answered.

### Android app — local alarms, and the full-screen one (**Deferred**)

The app **schedules its reminders on the device** with `AlarmManager.setAlarmClock()`, from the
reminders it syncs with the goals. This is exact to the minute, works offline and in Doze, and
is the only way a full-screen alert can be on time. The server's FCM push is the fallback for a
phone that has not synced since the reminder was added. The two are de-duplicated by reminder id
and fire instant.

- **Notification channel** "Reminders" (`IMPORTANCE_HIGH`). `POST_NOTIFICATIONS` is already
  declared, and the runtime prompt is asked when the first reminder is added, not at launch.
- **Full screen** (every reminder, every Upcoming event — owner, 2026-10-10): a notification with
  `setFullScreenIntent()` opening a `ReminderAlertActivity` with `showWhenLocked` +
  `turnScreenOn`. It shows the date's name and three actions: **Done** (mark handled), **Snooze
  10 min**, **Open in Spira**. Back does nothing until one is chosen. Home and power still work
  (see §0).
- **Permissions**: `USE_FULL_SCREEN_INTENT`, `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED` (re-arm after a reboot).
- **Android 14+ restricts full-screen intents to alarm and calling apps.** The **Play Store**
  revokes the permission from other apps at install. Spira ships through **Firebase App
  Distribution, not Play**, so the revocation does not apply. The app still checks
  `NotificationManager.canUseFullScreenIntent()`, and if it is off, sends the user to
  `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. **If Spira ever goes to Play**, this feature must be
  declared as alarm functionality and may be rejected for a reminders app; the fallback is a
  heads-up notification.
- **Design**: the alert screen is a Spira screen (the kit, Gravity icons, no emoji). Its look is
  for later, as agreed.

### Web — push, and an in-app blocking modal

- **Web Push** with VAPID keys (the private key in Secret Manager, never in the repo), a service
  worker (`/sw.js`) and a web app manifest (needed for iOS Home Screen). Server side: a maintained
  Web Push library (e.g. `nl.martijndwars:web-push`). **No third-party service and free.** The
  push services (FCM for Chrome, Mozilla's, Apple's) charge nothing.
- **Permission** is asked only after a gesture: the first time a reminder with "As notification"
  is saved, with a sentence saying why. A refusal is remembered, and the form then says
  notifications are blocked in this browser.
- **A due reminder while Spira is open** takes over the app with a modal (an AlertDialog,
  no X, not dismissible by Escape or a scrim tap): Done / Snooze / Open. This is the "block until
  answered" behaviour, inside Spira only.
- A push payload carries **the event id only**. The service worker fetches the title over the
  session, so no goal text sits in a push service's queue.

### 5b. What a mobile-web notification looks like — and how much of it is ours

**The banner itself is drawn by the phone, not by Spira.** A web notification is the operating
system's own card, in the system's font, colours and layout, the same shape as every other app's.
There is no HTML or CSS in it and its design cannot be changed. What Spira chooses is **what goes
into it**:

| Part | Android (Chrome) | iPhone (Home Screen app, iOS 16.4+) |
|---|---|---|
| **Title** — one line, e.g. the event's name | ✓ | ✓ |
| **Body** — a line or two, e.g. "Today at 14:00 · Goal: Run a marathon" | ✓ | ✓ |
| **Icon** — Spira's logo, a square 192 px PNG | ✓ (round crop on some phones) | ✓ (the Home Screen icon, from the manifest) |
| **Badge** — the small monochrome mark in the status bar | ✓ — a white-on-transparent 96 px PNG; without one Android shows a generic bell | — |
| **Big picture** under the text | ✓ (`image`, Chrome on Android only) | — |
| **Action buttons** — up to 2, text only | ✓ "Done" · "Snooze 10 min" | — |
| **Vibration pattern** | ✓ | — |
| **Sound** | system default only — no custom sound from the web | system default |
| **The site's name** under the title | always added by Chrome ("spira…app") — cannot be removed | the app's name |

So the "look" there is to design is small and concrete: **the wording** (title and body), **the
logo icon**, **the status-bar badge** (a one-colour silhouette of the logo — it has to read at
24 px), and **the two button labels**. No emoji in any of them, per the app's rule.

**The assets, concretely** (owner's questions, 2026-10-10). The web has one mark: the **Kale
sprout on a white tile** in `public/favicon.svg` (it replaced the Guava flower the same day); the
"spira" in the header is type, not an image.
There is **no web app manifest** and **no PNG icon** yet, and a notification needs PNGs:

| File | What it is | Notes |
|---|---|---|
| `public/icons/icon-192.png` (+ `icon-512.png`) | **The icon** — the Kale sprout on white, as the favicon | **Padded ~15 % on every side**, as the favicon already is: phones that crop icons round would otherwise cut the leaf tips off. Also used by the manifest and the iPhone Home Screen (`apple-touch-icon`, 180 px) |
| `public/icons/badge-96.png` | **The status-bar badge** — the sprout's silhouette, white on transparent, **without** the tile | Android reads **only the alpha**, so colour is thrown away — and a tile would turn into a white square. The filled sprout has no holes, so its silhouette reads as itself (the old flower would have needed its centre cut out). Without a badge Chrome shows its own generic bell |
| `public/images/reminder.png` | **The big picture** — the owner supplies it | Chrome on Android only. **2:1**, about **1350 × 675**, under ~1 MB; Chrome crops the edges on narrow phones, so nothing important near them. One picture for every reminder unless the owner wants several |
| `public/manifest.webmanifest` | name "spira", the icons, `theme_color` | Needed for the iPhone (notifications only for a Home Screen app) and so Android shows the app's name |

**The words are English**, like the rest of the UI: title = the event's name ("Run a marathon"),
body = when and what ("Today at 14:00 · Goal", "Tomorrow · Target: 10 km run", "In 7 days ·
Important date"). Buttons: **"Done"** and **"Snooze 10 min"**.

**Deferred with Android:** the native app's existing push notifications
(`SpiraMessagingService`) use the full-colour launcher icon as the status-bar icon, which Android
renders as a white blob. It needs the same monochrome flower as a vector drawable.

**Everything after the tap is ours, fully.** The "decide now" page (`/remind/$eventId`, §5a) is
an ordinary Spira screen: Kale head, the app's fonts and buttons, the event's name, date and time,
which goal it belongs to, Done / Snooze / Open. That is where the reminder can genuinely look like
Spira, and it is the screen the owner will design later, as agreed.

### Email (**Deferred**)

- **Brevo or Resend free tier** (§0), through a small `EmailSender` interface so the provider is
  one class. The API key lives in Secret Manager.
- The address is the account's Google email, which is never shown to anyone else and **never
  logged** (`LoggingConventionTest` already forbids it).
- Every email carries a **one-click unsubscribe** (`List-Unsubscribe` + a link holding a signed,
  single-purpose token) that switches that reminder off without a login. Gmail requires it for
  senders at volume, and it is right regardless.
- A plain text part and a minimal HTML part, with the event's name and date and a link into
  Spira. No tracking pixels.

---

## 6. Security (the boundaries this creates)

| Boundary | How | Test |
|---|---|---|
| Reminders are owner-scoped | create/update/delete through `findByIdAndUserId` on the event | cross-user: B cannot add, read or delete A's reminders → "not found" |
| Input validated server-side | amount 1–999, unit/channel enums, `at_minute_of_day` 0–1439, ≤5 per event | integration test per rule, and a refused create writes nothing |
| The dispatcher is not a public trigger | OIDC token from the scheduler's service account only | 401 without a token, 403 with a token for the wrong audience or account |
| A push endpoint is not an SSRF primitive | the server POSTs to whatever endpoint a client registers, so it accepts **https only, on the known push hosts** (`fcm.googleapis.com`, `*.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`) | registering `http://169.254.169.254/…` or `https://internal…` is refused |
| Unsubscribe tokens | HMAC-signed, scoped to one reminder, expiring; never a session | a tampered or expired token is refused |
| Rate limits | the existing `graphql` bucket covers the mutations, and the subscribe endpoint gets its own small bucket | `RateLimitFilterTest` case |
| Nothing personal in logs | `reminder_sent reminderId=… channel=…`, never the title or the address | `LoggingConventionTest` (existing) |

---

## 7. Tests, at every level the app already uses

- **Backend unit**: fire-time computation, including all-day vs timed, DST changes (a "1 day
  before at 09:00" across the March/October switch), a moved deadline re-arming, and a past one
  not re-firing. Idempotency of `last_fired_for`.
- **Backend integration (GraphQL)**: create/edit/delete reminders, validation, the cascade with
  the event, cross-user isolation, and the dispatcher end to end with a fake `EmailSender` and a
  fake push sender (a clock injected, no real time).
- **Security (MockMvc)**: the dispatcher's OIDC gate, CSRF and 401 on the mutations, the push
  host allow-list.
- **Python E2E**: the reminders API against the running backend.
- **Vitest**: the preset list for all-day vs timed, the Custom dialog's validation, radios with
  arrow keys, the 5-reminder cap.
- **Playwright**: add an event with a custom reminder end to end; the in-app blocking modal for a
  due date (clock mocked with `page.clock`); axe on the reminders block and the Custom dialog.
- **Android**: unit tests for the alarm scheduling and re-arm after boot; a Compose UI test for
  the alert screen; and once this lands, **the first real Maestro flow** (add event → reminder →
  alert), per the deferred Maestro reminder in CLAUDE.md.

---

## 8. Order of work

**The web first** (owner, 2026-10-10).

1. **The form and the data**: optional time, reminders block, Custom dialog, V41, API, and the
   in-app blocking "decide now" page. Useful on its own, all on existing infrastructure.
2. **Web Push**: service worker, VAPID, subscriptions, Cloud Scheduler dispatcher, action buttons,
   and the notification tap that opens `/remind/$eventId` (§5a).
3. **Email — Deferred, not to be started until the owner asks**: provider account, (optionally)
   domain + SPF/DKIM, unsubscribe.
4. **Android — Deferred, not to be started until the owner asks**: local alarms + notification,
   then the full-screen alert and "Display over other apps" (§5a). The data model and API from
   step 1 already carry everything it needs (`channel = fullscreen` is accepted and stored, and
   simply has no Android sender yet — until then the form does not offer it).

### Decisions (owner, 2026-10-10)

1. **Time** — optional, on **every** Upcoming date, not only important dates (§1).
2. **Default reminder** — **yes**, on every new event, and the form states exactly when it will
   arrive (§2).
3. **Email** — **deferred**, like Android. Brevo vs Resend and the domain are decided when it is
   picked up.
4. **Full screen** — for **every** Upcoming event, not only important dates (§2, §5a).
5. **The form explains what Upcoming is for** — done already, in the Add event form's
   description, in the owner's words: *"Use Upcoming for dates you simply can’t miss. Nothing
   gets added here automatically — you control what’s important, whether it’s a specific date or
   deadline for a goal or task."*
