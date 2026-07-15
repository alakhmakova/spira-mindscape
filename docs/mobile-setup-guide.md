# Mobile App Setup Guide (preparation for the native Android app)

This guide lists **exactly what to download, install, and create** before we build the native
Kotlin/Jetpack Compose Android app (see `specs/2026-07-15-native-mobile-app/plan.md`). It is
written for someone doing this for the first time. Do the steps in order; each has a
**verify** step so you know it worked.

> **Legend:** ✅ already present on this machine · ⚠️ needs attention · ❌ still to do.

---

## 0. Current state on this machine (checked 2026-07-15)

| Component | Status | Note |
|---|---|---|
| Android Studio | ❌ | **Not installed.** `C:\Program Files\Android\Android Studio` only contains a leftover `jre` folder (no `studio64.exe`) — a remnant of a previous install. Install it (Step A1). |
| Android SDK | ✅ | `C:\Users\buale\AppData\Local\Android\Sdk` — real and usable (exists independently of Studio); the new Studio install will reuse it. |
| platform-tools (`adb`), emulator | ✅ | installed |
| SDK platforms | ✅ | API 23–33 |
| build-tools | ✅ | up to 33.0.0 |
| System images (emulator) | ✅ | API 23/29/30/31/33 |
| cmdline-tools (latest) | ❌ | install via SDK Manager (Step A2) |
| `ANDROID_HOME` + `adb` on PATH | ❌ | set env vars (Step A3) |
| JDK for Gradle | ⚠️ | system JDK is 22; use **JDK 17** or Android Studio's embedded JDK (Step A4) |
| Node / npm | ✅ | v22 — used for the Firebase CLI |
| `gcloud` CLI | ✅ | already used for Cloud Run |
| Firebase CLI | ❌ | install (Step B5) |
| Firebase project + `google-services.json` | ❌ | create on your account (Part B) |
| Google OAuth **Android** client + SHA-1 | ❌ | create (Part C) |

Bottom line: **the Android SDK is already installed, but Android Studio is not.** You need to
install Android Studio, add `cmdline-tools`, set environment variables, pick the right JDK,
create an emulator, and then set up Firebase and one Google OAuth client.

---

## Part A — Android tooling

### A1. Install Android Studio ❌

Android Studio is **not** actually installed — the `C:\Program Files\Android\Android Studio`
folder is just a leftover `jre` from a previous install.

1. Download the installer: <https://developer.android.com/studio> (Windows `.exe`).
2. Run it. During setup choose **Standard** install.
3. **Point it at the existing SDK** so it doesn't re-download everything: when asked for the
   **Android SDK Location**, use the one you already have —
   `C:\Users\buale\AppData\Local\Android\Sdk`. (You can also set/confirm this later in
   **Settings → Languages & Frameworks → Android SDK**.)
4. *(Optional cleanup)* the old `C:\Program Files\Android\Android Studio` folder with only
   `jre` inside can be deleted once the new install goes to its own location — but leave it if
   unsure; it's harmless.

- **Verify:** Android Studio opens to the welcome screen, and **Settings → Android SDK** shows
  the SDK path `...\AppData\Local\Android\Sdk` with your existing platforms listed.

### A2. Install "Android SDK Command-line Tools (latest)" ❌

Modern Gradle builds expect the new `cmdline-tools`, which is currently missing.

1. Android Studio → **More Actions ▾ → SDK Manager** (or **Settings → Languages & Frameworks →
   Android SDK**).
2. Tab **SDK Tools** → check **Android SDK Command-line Tools (latest)**.
3. While here, also confirm these are checked (install if not):
   - **Android SDK Build-Tools** (latest, e.g. 34.x)
   - **Android SDK Platform-Tools**
   - **Android Emulator**
4. Tab **SDK Platforms** → check **Android 14 (API 34)** (a current target). API 33 already
   exists; 34 is recommended for `targetSdk`.
5. Click **Apply** → **OK** and let it download.

- **Verify:** the folder `C:\Users\buale\AppData\Local\Android\Sdk\cmdline-tools\latest\bin`
  now exists.

### A3. Set environment variables (`ANDROID_HOME`, PATH) ❌

Needed so terminal tools (`adb`, `sdkmanager`, the Gradle wrapper) find the SDK. In
**PowerShell**, run once (user-level, permanent):

```powershell
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
setx PATH "$env:PATH;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin;$env:LOCALAPPDATA\Android\Sdk\emulator"
```

Then **close and reopen** the terminal (and Android Studio) so the change takes effect.

- **Verify (new terminal):** `adb version` prints a version; `echo $env:ANDROID_HOME` prints
  the SDK path.

### A4. Pick a JDK for Gradle (important) ⚠️

The system JDK here is **22**, which is newer than the Android Gradle Plugin officially
supports and can cause confusing build errors. Use one of these instead:

- **Easiest — Android Studio's embedded JDK:** Android Studio → **Settings → Build, Execution,
  Deployment → Build Tools → Gradle → Gradle JDK** → choose the **jbr** / "Embedded JDK".
  Builds started from Android Studio then use a compatible JDK automatically.
- **For terminal builds (`gradlew`)** — install **JDK 17 (LTS)**:
  <https://adoptium.net/temurin/releases/?version=17> (Windows x64 `.msi`). After install,
  point Gradle to it per project via `android/gradle.properties`
  (`org.gradle.java.home=...`) — we'll set this when the module is created.

- **Verify:** in Android Studio, **Gradle JDK** shows 17 or the embedded jbr (not 22).

### A5. Create an emulator (Android Virtual Device) ❌

1. Android Studio → **More Actions ▾ → Virtual Device Manager** → **Create Device**.
2. Pick a phone (e.g. **Pixel 6**) → **Next**.
3. Choose a system image you already have — **API 33 (Tiramisu)** is a good default → **Next**
   → **Finish**.
4. Press ▶ to boot it once.

- **Verify (terminal):** `emulator -list-avds` lists your AVD; with it running, `adb devices`
  shows one `emulator-5554  device`.

> The running emulator reaches your local backend at **`http://10.0.2.2:8080`** (that special
> IP is the host machine from inside the emulator). We'll use this in the app's `local` build.

---

## Part B — Firebase project

Firebase distributes the app to testers (App Distribution) and provides push (FCM) and
crash/usage telemetry (Crashlytics/Analytics). It does **not** host the backend — that stays
on Cloud Run.

### B1. Create a Firebase project ❌

1. Go to <https://console.firebase.google.com> → **Add project**.
2. You can **link it to your existing Google Cloud project** (the one already running Cloud
   Run) by picking that project name, or create a new one — either is fine. Linking keeps
   everything under one project.
3. Google Analytics: **enable** (needed for Analytics; you can accept defaults).

- **Verify:** the project shows in the Firebase console dashboard.

### B2. Register the Android app ❌

1. In the project, click the **Android** icon ("Add app").
2. **Android package name:** `com.spiramindscape.android` (this must match the app we build —
   keep it exactly).
3. **App nickname:** e.g. "Spira Android" (optional).
4. **Debug signing certificate SHA-1:** paste the SHA-1 from **Part C, Step C1** (you can also
   add it later — Google Sign-In needs it, so don't skip it).
5. Click **Register app**.

### B3. Download `google-services.json` ❌

1. Firebase gives you **`google-services.json`** — download it.
2. Place it at **`android/app/google-services.json`** (this folder is created when we scaffold
   the module; for now save the file somewhere safe).
3. **Do not commit real secrets** casually — but note `google-services.json` is generally safe
   to commit for a client app. We'll confirm gitignore rules when scaffolding.

- **Verify:** you have the file and it contains your `package_name` `com.spiramindscape.android`.

### B4. Enable the Firebase products we use ❌

In the Firebase console left menu, open each once to enable:

- **Release & Monitor → App Distribution** → Get started (create a tester group, add your own
  email as a tester).
- **Release & Monitor → Crashlytics** → Enable.
- **Engage → Messaging (Cloud Messaging / FCM)** → it's on by default once the app is
  registered.
- **Analytics** → already enabled in B1.

### B5. Install the Firebase CLI ❌

Used to upload builds to testers from the terminal.

```powershell
npm install -g firebase-tools
firebase login
```

- **Verify:** `firebase --version` prints a version; `firebase projects:list` shows your
  project.

---

## Part C — Google Sign-In OAuth (for the mobile auth endpoint)

The app signs in with Google and sends a **Google ID token** to the backend
(`POST /api/auth/google/mobile`, added in Part 2 of the plan). For this to work you need an
**Android OAuth client** (identifies the app to Google) and you reuse the **Web OAuth client**
as the token *audience* the backend verifies against.

> **Mental model (read this once):**
> - The **Android OAuth client** = "this specific app (by package + signing fingerprint) is
>   allowed to request Google sign-in."
> - The app asks Google for an ID token whose **audience is the Web client ID** (the same one
>   the website already uses). The backend then verifies the token was minted for *that* Web
>   client ID. This is Google's standard pattern — the server trusts one audience for both web
>   and mobile.

### C1. Get your debug SHA-1 fingerprint ❌

Google needs the SHA-1 of the key that signs your **debug** builds (created automatically on
first build at `~/.android/debug.keystore`). Get it with either method:

**Option 1 — keytool (PowerShell):**
```powershell
keytool -list -v -alias androiddebugkey -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android -keypass android
```
Copy the line `SHA1: XX:XX:...`.

**Option 2 — Gradle (after the module exists):** `cd android; .\gradlew.bat signingReport`
and read the `SHA1` under `Variant: debug`.

> If `~/.android/debug.keystore` doesn't exist yet, it's created the first time you build/run
> any Android app; run once then re-run the command.

### C2. Create the Android OAuth client ❌

1. Go to **Google Cloud Console → APIs & Services → Credentials**:
   <https://console.cloud.google.com/apis/credentials> (select the **same project** as
   Firebase/Cloud Run).
2. **Create Credentials → OAuth client ID → Application type: Android**.
3. **Package name:** `com.spiramindscape.android`.
4. **SHA-1 certificate fingerprint:** paste the value from C1.
5. Create. (Registering the Android app in Firebase B2 with the SHA-1 often creates this for
   you automatically — if an Android client already exists there, you can skip C2.)

### C3. Note the Web client ID (audience) ✅ exists / ⚠️ confirm

- The website already has a **Web application** OAuth client (used by the current Google
  Sign-In). Find it in the same Credentials page under **OAuth 2.0 Client IDs → Web client**.
- Copy its **Client ID** (looks like `1234567890-abc...apps.googleusercontent.com`). We will:
  - give it to the **Android app** as the "server client ID" it requests an ID token for, and
  - configure the **backend** to accept that client ID as the valid token audience.

### C4. Production signing (later, not now)

When you publish (Play Store / release builds), you'll add the **release** keystore's SHA-1 as
another fingerprint on the Android OAuth client. For development and Firebase App Distribution
testing, the **debug** SHA-1 from C1 is enough.

---

## Final checklist

Download / install:

- [ ] **Android Studio** — install and point it at the existing SDK (A1)
- [ ] SDK: **cmdline-tools (latest)** + confirm build-tools/platform-tools/emulator + **API 34** (A2)
- [ ] **JDK 17** (Temurin) or set Gradle to the embedded jbr (A4)
- [ ] **Firebase CLI** (`npm i -g firebase-tools`) (B5)

Configure:

- [ ] `ANDROID_HOME` + PATH env vars (A3)
- [ ] Create an **emulator** (AVD) (A5)
- [ ] Set **Gradle JDK** to 17/embedded in Android Studio (A4)

Create on your accounts:

- [ ] **Firebase project** (link to existing GCP project) (B1)
- [ ] **Register Android app** `com.spiramindscape.android` + SHA-1 (B2)
- [ ] Download **`google-services.json`** (B3)
- [ ] Enable **App Distribution, Crashlytics, Analytics, FCM** (B4)
- [ ] **Debug SHA-1** fingerprint (C1)
- [ ] **Android OAuth client** (C2)
- [ ] Copy the existing **Web OAuth client ID** (C3)

---

## Who does what

- **You** must do everything that needs your Google/Firebase account and interactive UI:
  installing SDK components in Android Studio, setting env vars, creating the emulator, the
  Firebase project, `google-services.json`, and the OAuth clients (Parts A–C).
- **Claude** will then scaffold the `android/` module, wire Apollo/GraphQL, implement the
  screens, and add the backend `POST /api/auth/google/mobile` endpoint — using the client IDs
  and `google-services.json` you produce here. Claude cannot log into your Google/Firebase
  accounts or click through those consoles.

When Parts A–C are done, tell Claude and we start **Part 2 (backend mobile-auth endpoint)** and
**Part 3, Step 1 (Android scaffold)** from the plan.
