# Push notifications (Firebase Cloud Messaging)

How Spira sends a notification to a user's phone — the pieces, why they exist, how to set it up,
and how to verify it end to end.

## What FCM is, and why

**Firebase Cloud Messaging (FCM)** is Google's push-notification pipeline. Our backend hands a
message to FCM, and Google delivers it to the right device — even when the app is closed. This is
how Spira will nudge users about **deadlines, overdue targets, or a daily focus** without them
having the app open. (The reminders themselves — the "when to send" logic — are a later step;
this change builds the **delivery pipeline** they'll ride on.)

## How it flows

```
Android device                         Backend (Cloud Run)                 Google
--------------                         -------------------                 ------
FirebaseMessaging.getToken()  ──POST /api/push/register──►  device_token table
   (a unique per-install token)         (owner-scoped, upsert)

                                        PushNotificationService.sendToUser()
                                            builds a Message per token  ──► FCM ──► 🔔 device
```

1. On sign-in the app fetches its **FCM registration token** and registers it with the backend
   (`POST /api/push/register`), scoped to the signed-in user via the session cookie.
2. The backend stores it in the `device_token` table (one row per device, `token` unique).
3. To notify a user, `PushNotificationService.sendToUser(userId, title, body)` sends one FCM
   message per registered device, using the Firebase Admin SDK.
4. On sign-out the app deletes its local FCM token, so delivery stops; the backend prunes the
   now-dead token the next time a send returns `UNREGISTERED`.

## How it's wired in this repo

### Android (`android/app`)
- **Deps:** `firebase-messaging` (via the Firebase BoM) + `kotlinx-coroutines-play-services`
  (for `Task.await()`).
- **`push/SpiraMessagingService`** — the `FirebaseMessagingService`: `onNewToken` re-registers a
  rotated token; `onMessageReceived` shows a notification for foreground messages.
- **`data/push/PushApi`** — REST calls to `/api/push/register|unregister` over the shared OkHttp
  client (so the session cookie + `X-XSRF-TOKEN` header flow through automatically).
- **`data/push/PushManager`** — fetches/deletes the FCM token; all Firebase calls are guarded so
  a device without Play Services just logs and moves on.
- **`push/PushNotificationsEffect`** — a Compose effect that registers on sign-in (and requests
  the Android-13+ `POST_NOTIFICATIONS` permission) and disables on sign-out. Kept in the UI layer
  so `AuthViewModel` stays free of Firebase and unit-testable.
- **Manifest** — `POST_NOTIFICATIONS` permission, the service declaration, and the default
  notification channel/icon meta-data.

### Backend (`backend`)
- **Dep:** `com.google.firebase:firebase-admin`.
- **`push/DeviceToken` + `DeviceTokenRepository`** — the per-user token store (migration
  `V15__device_token.sql`), always queried scoped to the owner.
- **`push/FirebaseConfig`** — initializes the Admin SDK **only when a service-account credential
  is configured**; otherwise the messaging bean is `null` and sending is a no-op. So local dev,
  CI, and tests run without any Firebase secret.
- **`push/PushNotificationService`** — register/unregister (owner-scoped) + `sendToUser`, which
  prunes tokens FCM reports as gone.
- **`push/PushController`** — `POST /api/push/register`, `/unregister`, and `/test` (a self-push
  to your own devices, for verification). All under `/api/**`, so authenticated + CSRF-protected.

## Setup

### Receiving on the device — already done
The app receives pushes as soon as it has `google-services.json` (present locally, gitignored)
and runs on a device/emulator **with Google Play Services**. FCM is enabled by default for a
Firebase Android app; nothing extra to toggle.

### Sending from the backend — needs credentials
The Admin SDK needs credentials to authenticate to FCM. There are two ways; pick one.

#### Option A (recommended on Cloud Run) — Application Default Credentials, no key file
The backend already runs as a **service account** on Cloud Run; let the Admin SDK use it
directly. No downloadable key — which also sidesteps the common org policy
`iam.disableServiceAccountKeyCreation` (the "Key creation is not allowed on this service account"
error when trying to generate a key).

**Do these in order** — the env var only matters once the ADC-aware code below is deployed:

1. **Deploy the backend that supports ADC.** The code that reads
   `app.fcm.use-application-default` must be live first. Commit + deploy the current backend to
   Cloud Run. (Setting the env var on an older revision does nothing.)
2. **Enable the FCM API.** Google Cloud console → **APIs & Services → Library** → search
   *"Firebase Cloud Messaging API"* (`fcm.googleapis.com`) → **Enable**.
3. **Grant the Cloud Run service account permission to send FCM.** Google Cloud console → **IAM**
   → find the service account your Cloud Run service runs as (Cloud Run → your service →
   **Security** tab shows it; by default it's `PROJECT_NUMBER-compute@developer.gserviceaccount.com`)
   → add role **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`). The
   broader *Firebase Admin* / *Editor* also works.
4. **Set the env var on Cloud Run:**
   - Cloud Run → your service (`spira`) → **Edit & deploy new revision** (top of the page).
   - Scroll to the container's **Variables & Secrets** tab → **+ Add variable**.
   - **Name:** `APP_FCM_USE_APPLICATION_DEFAULT`  **Value:** `true`
   - Click **Deploy** at the bottom.
5. **Confirm.** After the new revision is serving, the startup logs (Cloud Run → **Logs**) show
   `FCM enabled: Firebase Admin initialized for push notifications.` If they instead say
   `FCM disabled: …`, one of steps 1–4 is missing.

#### Option B — an explicit service-account key (only if key creation is allowed)
Firebase console → **Project settings** (gear) → **Service accounts** → **Generate new private
key** → a JSON file. (Blocked by the org policy above? Use Option A.) It's a secret — never commit
it. Then give it to the backend via **one** of:
- `app.fcm.credentials-json` — the JSON **contents** (put it in a Secret Manager secret, exposed
  as env var `APP_FCM_CREDENTIALS_JSON`).
- `app.fcm.credentials-path` — a filesystem **path** to the JSON (handy for local runs).

Either way, without credentials the app still runs — it just skips sending (`isEnabled()` is
false), so local dev, CI, and tests need nothing.

## How to verify it works

### Recommended: the backend self-test (`/api/push/test`)
This is the easiest reliable check for a **physical phone** (no need to copy device tokens
around). It needs the backend credentials from Option A/B configured and the phone signed in
(which registers the device). Signed in as that user, call:
```
POST /api/push/test        →  { "enabled": true, "sent": 1 }
```
`enabled:false` → credentials not configured (redo Option A); `sent:0` with `enabled:true` → the
signed-in user has no registered device yet (open the app so it registers). Because it needs an
authenticated session + CSRF header, the simplest way to trigger it is an in-app "send test
notification" action — ask if you want one added (it doubles as a diagnostic until real reminders
exist).

### Alternative: the Firebase console test message
The Firebase console's Messaging page changed — there is **no** standalone "send test message"
button on the landing page anymore, and the **"Enable Google Analytics"** banner is only about
campaign *targeting/reporting*, **not** about a test push. The test-to-one-device flow now lives
inside campaign creation and does **not** require Analytics:

1. Firebase console → **Messaging** (under "Engage" / "DevOps & Engagement") → **Create your first
   campaign** → choose **Firebase Notification messages**.
2. Fill in **Notification title** and **text**.
3. Click **Send test message** (top-right of the form).
4. Paste an **FCM registration token** for your device → **Test**. The phone shows the
   notification. (You can leave the campaign unsaved after the test.)

Getting the device's FCM token is the catch on a physical phone (it prints to Logcat, which needs
adb). That's why the backend self-test above is the recommended path for App Distribution testers.

## Tests
- **Backend** — `PushNotificationServiceTest` (mocked FCM: send counts, dead-token pruning,
  disabled no-op) and `DeviceTokenIntegrationTest` (real H2 DB: owner-scoping — a user can't
  remove another's token — and the re-register-moves-owner upsert).
- **Android** — `PushApiTest` (MockWebServer: register/unregister request shape + status).

## Security notes
- Registration/unregistration/test are all **owner-scoped**: a caller only ever touches their own
  device tokens and can only test-push to their own devices (no cross-user surface).
- The endpoints are authenticated (`/api/**`) and CSRF-protected; the app sends `X-XSRF-TOKEN`.
- The Firebase **service-account key is a secret** — it lives in env / Secret Manager, never in
  code, logs, or a committed file (same rule as every other secret in `docs/security-model.md`).
- An FCM token identifies a device install; it's stored per-user and never returned through the
  API.

## Not done yet (deliberately deferred)
The **triggers** — a scheduled job that scans for approaching deadlines / overdue targets / a
daily focus and calls `sendToUser` — aren't built. This change is the delivery pipeline; wiring
the reminder rules (and letting users opt in/out per type) is the next push-related step and
dovetails with roadmap Phase 10.

## See also
- `docs/crash-reporting-and-monitoring.md` — Crashlytics (the other Firebase runtime service).
- `docs/mobile-setup-guide.md` — Firebase project + `google-services.json`.
- `docs/security-model.md` — owner-scoping, secrets, CSRF.
