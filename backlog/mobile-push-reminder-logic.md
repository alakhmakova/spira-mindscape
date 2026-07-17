# Push notifications: build the reminder-sending logic (triggers)

- **ID:** BUG-006 (feature/enhancement — tracked here at the user's request)
- **Status:** 🐞 Open
- **Reported by:** User
- **Area:** Backend (`push/`) + scheduling; Android (notification handling already done)
- **Severity:** n/a (missing feature, not a defect)

## Summary

The FCM **delivery pipeline** is built and verified end to end (a notification sent from the
Firebase console arrives on the phone; `docs/push-notifications-guide.md`). What's **missing** is
the logic that decides **when** to send and calls `PushNotificationService.sendToUser(...)` — i.e.
the actual reminders. Right now nothing is ever sent automatically.

## What exists already

- `PushNotificationService.sendToUser(userId, title, body)` — sends to a user's devices, prunes
  dead tokens.
- Device-token registration (`/api/push/register`), owner-scoped storage (`device_token`).
- Android receives + displays notifications; `FirebaseConfig` sends once credentials are set
  (Application Default Credentials on Cloud Run).

## ⚠️ First: the rules are a PRODUCT decision — ASK the user, don't invent them

**The agent must NOT decide on its own what the notifications say or when they fire.** These are
product/UX choices that belong to the user (Anastasiya). Before implementing, ask and get explicit
answers to at least:

- **Which events** should notify? (deadline approaching, deadline overdue, daily focus, a target
  untouched for N days, goal achieved, something else?)
- **When / how often** for each? (e.g. "1 day before a deadline, at 09:00 local time"; "daily at
  X"; "not more than once per day per goal"?)
- **Wording/tone** of each message.
- **Opt-in or opt-out** by default, and what granularity of control the user wants.

Only implement rules the user has confirmed. Record the agreed rules here before coding.

## What to build (once rules are agreed)

1. **The confirmed reminder rules** (from the answers above) — no unilateral guesses.
2. **A scheduler** — a Spring `@Scheduled` job (or Cloud Scheduler → an endpoint) that periodically
   scans goals/targets per user and calls `sendToUser` with the agreed message. Mind time zones and
   avoid duplicate sends (record "last notified" state).
3. **User preferences (opt-in/out)** — let users turn reminder types on/off; don't spam. Store per
   user; respect it in the scheduler.
4. **A manual test affordance** — a small in-app "send test notification" button calling
   `/api/push/test`, so a physical-device tester can verify without the Firebase console. (Optional
   but handy; also serves as the `/api/push/test` trigger referenced in the guide.)

## Dependencies / prerequisites

- Backend must have FCM credentials configured to actually send (Cloud Run:
  `APP_FCM_USE_APPLICATION_DEFAULT=true` + the service account granted **Firebase Cloud Messaging
  API Admin**). See `docs/push-notifications-guide.md` → Setup.

## How to verify fixed

- With credentials configured, a goal whose deadline is tomorrow produces exactly one push at the
  scheduled time; none when the user opted out; no duplicate on the next scan.
- Unit tests for the rule (which goals qualify) + the "don't send twice" guard; the send path is
  already covered by `PushNotificationServiceTest`.

## Resolution

_(empty — open)_
