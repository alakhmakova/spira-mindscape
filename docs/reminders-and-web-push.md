# Reminders and Web Push

How an Upcoming reminder gets from the form to the user, and the **one-time setup only the
owner can do** in Google Cloud. The design and the decisions behind it are in
`specs/2026-10-10-upcoming-notifications/plan.md`; this is the operating manual.

---

## 1. What happens, end to end

1. **The form** (`AddEventDialog` + `ReminderFields`) saves an event with an optional time and up
   to five reminders. The device's IANA time zone is sent once (`setTimeZone`), because "on the
   day at 09:00" means 09:00 *where the user is*.
2. **Nothing is scheduled.** A reminder's moment is computed on every read from the event's date
   — for a link, the goal's, target's or task's deadline, read live — in the user's zone
   (`ReminderSchedule.java`). Moving a deadline moves its reminders with no extra work.
3. **Every minute the dispatcher runs** (`ReminderDispatcher.java`): every reminder whose moment
   has come and has not been sent is marked `last_fired_for = <that moment>` in one transaction,
   then each affected event is pushed to its owner's browsers. Idempotent, so an extra run in the
   same minute sends nothing. A snooze that has run out is sent again. Anything found more than an
   hour late is marked without a push.
4. **The push carries the event id and nothing else.** The service worker (`public/sw.js`)
   fetches the words — `GET /api/reminders/{id}/notice` — over the user's own session, and shows
   the notification: the event's name, "Today at 14:00 · Goal", the sprout icon, the status-bar
   badge, and **Done** / **Snooze 10 min**.
5. **A button answers without opening Spira** (`POST /api/reminders/{id}/answer`). A tap on the
   notification opens Spira on `/remind/{id}`, the full-screen "decide now" page.
6. **The page does not depend on push at all.** Every signed-in page asks for the due list every
   30 seconds and whenever it comes back to the front, and a due, unanswered reminder covers the
   app (`DecideNow.tsx`) until it is answered. Due is the server's fact, so answering on the laptop
   releases the phone.

Without the Cloud Scheduler job below (production only), step 3 never runs, so steps 3–5 are off
and step 6 still works — inside Spira only.

## 2. Where it runs

| | Locally (`local` profile) | Production (Cloud Run) |
|---|---|---|
| Who runs the dispatcher | an in-process timer, every minute (`spira.reminders.local-scheduler=true`) | **Cloud Scheduler** → `POST /internal/reminders/dispatch` with an OIDC token |
| Web Push | **on with no setup**: the server creates a key pair on first use and keeps it in the database; the `SPIRA_WEBPUSH_*` env vars win if set | **on with no setup** too: the same stored pair, shared by every instance |
| Why the difference | the app is always up | Cloud Run scales to zero, so an in-process timer would not run while nobody uses the app |

## 3. One-time setup (owner)

All of this happens in Google Cloud, which the agent cannot reach. Project and region as in
`docs/deploy-gcp-cloud-run.md` (`europe-west1`, service `spira`). PowerShell-friendly: one command
per line.

### 3a. VAPID keys — nothing to do

The keys tell the push services which server is sending. **The server makes its own pair** the
first time it needs one and keeps it in the database (`web_push_vapid_key`, V43), the private key
encrypted with the same key as the AI provider keys (`AI_ENCRYPTION_KEY`). Every instance and
every later start reads that one row, so the pair never changes under a subscription. On a local
run the first start adopts the pair from the old `~/.spira/vapid-keys.properties`, if there is
one, so browsers already subscribed there keep working.

**Optional — pinning a pair of your own.** `SPIRA_WEBPUSH_PUBLIC_KEY` + `SPIRA_WEBPUSH_PRIVATE_KEY`
in the environment win over the stored pair (generate one with `npx web-push generate-vapid-keys`;
the private key goes in Secret Manager, never the repo). Switching from the stored pair to a pinned
one, or back, changes the public key: browsers re-subscribe on their next visit, and until then
they get nothing. `SPIRA_WEBPUSH_SUBJECT` (`mailto:<your address>`) is the contact the push
services see; it is optional too.

**Do not rotate `AI_ENCRYPTION_KEY`** without clearing `web_push_vapid_key` as well: the stored
private key can no longer be read, push turns off (the app says so), and deleting the row makes a
fresh pair on the next push.

### 3b. Cloud Scheduler → the dispatcher

```
gcloud services enable cloudscheduler.googleapis.com
gcloud iam service-accounts create spira-scheduler --display-name "Spira reminders scheduler"
```

The service's URL, and the dispatch URL that is also the token's **audience**:

```
gcloud run services describe spira --region europe-west1 --format "value(status.url)"
# → https://spira-xxxx.europe-west1.run.app ; the audience is that + /internal/reminders/dispatch
```

Tell the app which token to accept:

```
gcloud run services update spira --region europe-west1 `
  --update-env-vars "SPIRA_REMINDERS_DISPATCH_AUDIENCE=https://spira-xxxx.europe-west1.run.app/internal/reminders/dispatch,SPIRA_REMINDERS_DISPATCH_SERVICE_ACCOUNT=spira-scheduler@<PROJECT_ID>.iam.gserviceaccount.com"
```

Create the job — every minute, POST, with an OIDC token for that account and audience:

```
gcloud scheduler jobs create http spira-reminders --location europe-west1 `
  --schedule "* * * * *" --http-method POST `
  --uri "https://spira-xxxx.europe-west1.run.app/internal/reminders/dispatch" `
  --oidc-service-account-email "spira-scheduler@<PROJECT_ID>.iam.gserviceaccount.com" `
  --oidc-token-audience "https://spira-xxxx.europe-west1.run.app/internal/reminders/dispatch"
```

If that last command says you may not act as the service account, grant yourself
`roles/iam.serviceAccountUser` on `spira-scheduler` and run it again.

**Cost.** Cloud Scheduler gives three jobs free per billing account; 1,440 short wake-ups a day
sit far inside Cloud Run's free tier. The push services charge nothing.

### 3c. Check it

**Settings → Notifications** says whether reminders can reach the device in hand and why not
(push off on the server, plain http, a browser without push, permission blocked), and **Send a
test notification** pushes through the whole path — server, push service, service worker — to
every subscribed device of the account. Start there on a phone.

```
gcloud scheduler jobs run spira-reminders --location europe-west1
gcloud logging read "resource.type=cloud_run_revision AND textPayload:reminders_dispatched" --limit 5
```

The job's own page shows the last result: **200** is right. **401** means no token reached the
app (the job has no OIDC settings); **403** means the audience or the account does not match the
two env vars — compare them character for character, including `https://` and the path.

Then on a phone: Chrome on Android, or an iPhone **with Spira added to the Home Screen** (iOS
16.4+; a plain Safari tab cannot receive web pushes). Add an event a few minutes ahead with "5
minutes before", allow notifications when Save asks, lock the phone, and wait.

## 4. What this cannot do

- **Draw over the phone or the desktop.** A browser cannot; the full-screen page covers Spira
  only, and it is one tap away from the notification. A reminder that truly takes over the phone
  is the Android app's (planned, deferred).
- **Buttons on an iPhone.** Safari shows web notifications without action buttons; the tap still
  opens the "decide now" page.
- **A button needs the Cookie Store API in the service worker** to read the CSRF token. Where a
  browser has none, the answer comes back 403 and the worker opens `/remind/{id}` instead, so the
  user answers there — slower, never lost.

## 5. Where the code is

| Concern | File |
|---|---|
| Fire-time rules | `backend/.../upcoming/ReminderSchedule.java` |
| Due / answer / time zone | `upcoming/ReminderService.java` |
| The dispatcher | `upcoming/ReminderDispatcher.java`, `LocalReminderScheduler.java` |
| The scheduler's endpoint and its security chain | `upcoming/ReminderDispatchController.java`, `config/InternalSecurityConfig.java` |
| Encryption and VAPID | `push/web/WebPushCrypto.java` (checked against RFC 8291's own example) |
| Where the key pair comes from | `push/web/WebPushProperties.java`, `VapidKeyStore.java` |
| Endpoint allowlist (SSRF) | `push/web/WebPushEndpoints.java` |
| Subscriptions | `push/web/WebPushService.java`, `WebPushController.java` |
| Service worker | `public/sw.js` |
| Page side | `src/lib/spira/web-push.ts`, `due-reminders.ts`, `src/components/spira/DecideNow.tsx` |
| The form | `src/components/spira/AddEventDialog.tsx`, `ReminderFields.tsx`, `src/lib/spira/reminders.ts` |
