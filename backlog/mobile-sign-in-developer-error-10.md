# Mobile Google sign-in fails with DEVELOPER_ERROR (code 10)

- **ID:** BUG-002
- **Status:** ✅ Fixed (2026-07-16) — Android OAuth client created; sign-in + sign-out verified on a real device. See Resolution.
- **Reported by:** User (tested on a real phone)
- **Area:** Android app — Google sign-in via Credential Manager (`data/auth/GoogleSignInClient.kt`)
- **Severity:** High (blocks all mobile sign-in)

## Summary

Tapping "Continue with Google" in the Android app fails immediately (before the account
picker) with a `NoCredentialException`:

```
10: [28444] Developer console is not set up correctly.
getToken() -> BAD_AUTHENTICATION ... Long live credential not available.
```

## Steps to reproduce

1. Install the debug app on a device with Google Play services + a Google account.
2. Tap "Continue with Google".
3. It fails instantly with error code **10 (DEVELOPER_ERROR)**; no account chooser appears.

## Root cause

Google Identity Services (used by `GetGoogleIdOption`) requires an **OAuth 2.0 client of type
"Android"** in the Google Cloud project, matching the app's **package name**
(`com.spiramindscape.android`) and **signing SHA-1**. That client does **not exist**.

Evidence:
- The debug build signs with SHA-1 `96:CF:83:C1:31:0E:6C:D3:AC:B2:90:0E:10:CC:FB:9D:F9:0E:15:7E`,
  and that SHA-1 **is** registered on the Firebase Android app (`apps:android:sha:list` shows it).
- **But** `firebase apps:sdkconfig ANDROID …` returns only a `client_type: 3` (web) entry —
  there is **no `client_type: 1` (Android)** OAuth client. Registering a SHA on the Firebase
  app via the CLI did not produce a usable Android OAuth client.

The audience side is fine: the app requests an ID token for the web client ID
`952567559986-pv46g2sr…`, which equals prod `GOOGLE_CLIENT_ID`. The backend endpoint is
deployed and working (a fake token returns 401). So this is purely the missing Android client.

## Fix approach (one-time, Google Cloud Console — user action)

Create the Android OAuth client explicitly:

1. Google Cloud Console → **APIs & Services → Credentials** (project `project-10702811-5962-4bf3-877`):
   <https://console.cloud.google.com/apis/credentials>
2. **Create Credentials → OAuth client ID → Application type: Android**.
3. **Package name:** `com.spiramindscape.android`
4. **SHA-1:** `96:CF:83:C1:31:0E:6C:D3:AC:B2:90:0E:10:CC:FB:9D:F9:0E:15:7E`
5. Create, wait ~5 minutes for propagation.

No app rebuild is needed — this is server-side config. Add the **release** keystore's SHA-1
too when publishing.

## How to verify fixed

- `firebase apps:sdkconfig ANDROID … ` now shows a `client_type: 1` entry, **or** the Android
  client appears in Cloud Console Credentials.
- On the phone, "Continue with Google" now shows the account picker and completes; Logcat shows
  no error 10; the app reaches the signed-in screen.

## Resolution

The user created the **Android OAuth client** in Google Cloud Console (Credentials → OAuth
client ID → Android) with package `com.spiramindscape.android` and SHA-1
`96:CF:83:C1:31:0E:6C:D3:AC:B2:90:0E:10:CC:FB:9D:F9:0E:15:7E`. After propagation, sign-in and
sign-out work on a real device against the production backend — the full flow (Credential
Manager → Google ID token → `POST /api/auth/google/mobile` → session → `/api/auth/me` →
logout) is confirmed working. No app or backend code change was needed; it was purely the
missing Android OAuth client. Guidance corrected in `docs/mobile-setup-guide.md` §C2.

