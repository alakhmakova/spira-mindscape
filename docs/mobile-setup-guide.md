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
| Android Studio | ✅ | installed 2026-07-15 at **`C:\Program Files\Android\Android Studio1`** (v2026.1). NB: went to `Studio1` because the old empty `Android Studio` (only `jre`) still occupies the plain name — that leftover is safe to delete. |
| Android SDK | ✅ | `C:\Users\buale\AppData\Local\Android\Sdk` |
| platform-tools (`adb`), emulator | ✅ | installed |
| SDK platforms | ✅ | API 23–34 (incl. **API 34**) |
| build-tools | ✅ | up to **37.0.0** |
| System images (emulator) | ✅ | API 23/29/30/31/33 |
| cmdline-tools (latest) | ✅ | installed (`sdkmanager` present) — done 2026-07-15 |
| `ANDROID_HOME` + `adb` on PATH | ✅ | configured 2026-07-15 (open a **fresh** terminal to pick it up) |
| npm global bin on PATH | ✅ | `%AppData%\Roaming\npm` restored to PATH 2026-07-15 (see note in B5) |
| JDK for Gradle | ✅ | use Android Studio's **embedded JDK 21** (`...\Android Studio1\jbr`); no separate JDK 17 needed. System JDK 22 is ignored for Android builds. |
| Node / npm | ✅ | v22 — used for the Firebase CLI |
| `gcloud` CLI | ✅ | already used for Cloud Run |
| Firebase CLI | ✅ | installed 2026-07-15 (`firebase --version` → 15.23.0); still need `firebase login` (B5) |
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

Needed so terminal tools (`adb`, `sdkmanager`, the Gradle wrapper) find the SDK.

**`ANDROID_HOME`** is short and safe with `setx`:

```powershell
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
```

> ⚠️ **Do NOT use `setx PATH "$env:PATH;..."` for the PATH.** `setx.exe` truncates the value
> at **1024 characters**, and `$env:PATH` is the *combined* Machine+User path — appending to it
> and saving can silently cut your PATH (and drop the very entries you added). Use the method
> below instead, which edits only the **User** PATH and has no length limit.
>
> On this machine that truncation happened once and dropped the **npm-global** and **gcloud**
> entries from PATH; both were restored with `[Environment]::SetEnvironmentVariable(...,"User")`.
> If any CLI suddenly becomes "not recognized", its PATH entry was likely a casualty — re-add it
> the same way.

For **PATH**, run this in PowerShell (reads only the User PATH, de-duplicates, appends the
three Android folders, writes it back safely):

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$android = @("$sdk\platform-tools","$sdk\cmdline-tools\latest\bin","$sdk\emulator")
$current = [Environment]::GetEnvironmentVariable("Path","User") -split ';'
$seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$clean = @(); foreach ($e in $current) { if ($e -and $seen.Add($e)) { $clean += $e } }
foreach ($a in $android) { if ($seen.Add($a)) { $clean += $a } }
[Environment]::SetEnvironmentVariable("Path", ($clean -join ';'), "User")
```

Then **close and reopen** the terminal (and Android Studio) so the change takes effect —
environment changes never apply to already-open windows.

- **Verify (new terminal):** `adb version` prints a version; `echo $env:ANDROID_HOME` prints
  the SDK path.

> ℹ️ The `cmdline-tools\latest\bin` entry can be added before that folder exists (Step A2
> installs it) — a PATH entry to a missing folder is harmless. `adb` lives in `platform-tools`
> (already installed), so it works immediately.

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

### What Firebase actually is (and the project decision)

- **A Firebase project *is* a Google Cloud (GCP) project** with a set of Firebase services
  enabled on top (App Distribution, FCM, Crashlytics, Analytics). "Adding Firebase" to a
  project just switches those services on for the same GCP project — no separate cloud.
- **Decision (2026-07-15): use the existing Spira GCP project** —
  `project-10702811-5962-4bf3-877` ("My First Project", number **952567559986**, the one
  running Cloud Run and holding the Web OAuth client). Keeping Firebase, OAuth, and Cloud Run
  in one project means the Google Sign-In audiences line up with no extra wiring.
- **"You can't remove Firebase from a project later" — what that means:** once enabled, a
  project stays Firebase-enabled forever. This is **harmless**: it does **not** affect Cloud
  Run, data, or billing (Spark plan is free); it only means the project shows up in the
  Firebase console and has Firebase APIs on. Individual apps/resources inside can still be
  deleted. The irreversibility is cosmetic, not functional.

### ⚠️ Account gotcha (important)

Firebase CLI and gcloud can be logged into **different Google accounts**. On this machine:

- **gcloud / project owner:** `alakhmakova@gmail.com` (owns the Spira project).
- **firebase (first login):** `anastasiya.lakhmakova@gmail.com` — **not** an owner → `addFirebase`
  returned **403 PERMISSION_DENIED**.

**Fix (what was done):** log the Firebase CLI into the **same** account that owns the GCP
project, so no `--account` juggling is needed. Run this in **your own interactive terminal**,
**not** through Claude's `!` prefix — `firebase login` opens a browser and fails in
non-interactive mode (`Cannot run login in non-interactive mode`):

```powershell
firebase logout
firebase login     # pick alakhmakova@gmail.com — sign that account into the browser FIRST,
                   # otherwise the browser auto-selects whatever account is already signed in
```

Verify with `firebase login:list` (should show `alakhmakova@gmail.com`) and `gcloud auth list`
(same account). Both must act on the same project.

### B1. Add Firebase to the existing Spira project (done via CLI)

The Firebase Management API is enabled on the project (via
`gcloud services enable firebase.googleapis.com`). With the CLI logged into the owner account:

```powershell
firebase projects:addfirebase project-10702811-5962-4bf3-877
```

- **Verify:** `firebase projects:list` shows the project.

### B2. Register the Android app ✅ (done via CLI 2026-07-15)

```powershell
firebase apps:create ANDROID "Spira Android" --package-name com.spiramindscape.android \
  --project project-10702811-5962-4bf3-877
firebase apps:android:sha:create <APP_ID> 96:CF:83:C1:31:0E:6C:D3:AC:B2:90:0E:10:CC:FB:9D:F9:0E:15:7E \
  --project project-10702811-5962-4bf3-877
```

- **App ID:** `1:952567559986:android:eff4c02ebb5a77b38a892b`
- **Package:** `com.spiramindscape.android`; **debug SHA-1** added.

### B3. Download `google-services.json` ✅ (done via CLI 2026-07-15)

```powershell
firebase apps:sdkconfig ANDROID <APP_ID> --project project-10702811-5962-4bf3-877 \
  --out android/app/google-services.json
```

- Saved to **`android/app/google-services.json`** (`project_id` and `package_name` verified).
- **Kept out of git:** the file holds an Android **API key**, and this repo is public, so it is
  **gitignored** (`android/app/google-services.json`). It stays local; re-fetch on any machine
  with the `apps:sdkconfig` command above. If you later want it committed (e.g. for CI Android
  builds), restrict the API key in GCP first, then remove the gitignore line.

### B4. Enable the Firebase products we use ❌

In the Firebase console left menu, open each once to enable:

- **Release & Monitor → App Distribution** → Get started (create a tester group, add your own
  email as a tester).
- **Release & Monitor → Crashlytics** → Enable. The app is already wired for it (auto crash
  capture) — see `docs/crash-reporting-and-monitoring.md` for how it works and how to read crashes.
- **Engage → Messaging (Cloud Messaging / FCM)** → it's on by default once the app is
  registered. The app already registers device tokens; to have the **backend send** pushes,
  configure a service-account credential — see `docs/push-notifications-guide.md`.
- **Analytics** → already enabled in B1.

### B5. Install the Firebase CLI ✅ installed — `firebase login` still to do

Used to upload builds to testers from the terminal.

```powershell
npm install -g firebase-tools   # package is firebase-toolS (plural) — not "firebase-tool"
firebase login
```

> ⚠️ **Global npm CLIs need `%AppData%\Roaming\npm` on your PATH**, or `firebase` won't be
> found after install. On this machine that folder was missing from PATH (a casualty of the
> earlier `setx` truncation) and has been restored. If `firebase` is "not recognized" after a
> global install, add it back:
> ```powershell
> $npm = "$env:APPDATA\npm"
> $user = [Environment]::GetEnvironmentVariable("Path","User") -split ';'
> if ($user -notcontains $npm) {
>   [Environment]::SetEnvironmentVariable("Path", (($user + $npm) -join ';'), "User")
> }
> ```
> Then open a fresh terminal.

- **Status:** `firebase-tools` is installed (`firebase --version` → 15.23.0). You still need to
  run **`firebase login`** (opens a browser — your Google account) once, then
  `firebase projects:list` should show your project after Part B1.

---

### B6. Distributing a build (one command)

Once you're a tester (B4) and logged into the Firebase CLI (B5), ship a new build to testers
with a single Gradle task — it builds the debug APK and uploads it:

```powershell
cd android
.\gradlew.bat distributeDebug -PreleaseNotes="what changed in this build"
```

- Uses your `firebase login` (no service-account file needed).
- Testers get an email/App-Tester update; install works from **any network** (the app talks to
  the production backend over the internet).
- Override recipients with `-PdistTesters="a@example.com,b@example.com"`.
- The task is defined in `android/app/build.gradle.kts` (`distributeDebug`), calling
  `firebase appdistribution:distribute` with the app id
  `1:952567559986:android:eff4c02ebb5a77b38a892b`.

For a fully **open** download (no tester invite — e.g. for a portfolio reviewer), attach
`app-debug.apk` to a **GitHub Release** instead: push the repo, then
`gh release create <tag> android/app/build/outputs/apk/debug/app-debug.apk` (or via the GitHub
UI → Releases). Anyone can then download and sideload it.

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

### C1. Get your debug SHA-1 fingerprint ✅ (done)

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

### C2. Create the Android OAuth client ✅ (done 2026-07-16 — this is what makes sign-in work)

This is the step that fixed the "error 10" sign-in failure (BUG-002). Do it **explicitly** in
the console:

1. Go to **Google Cloud Console → APIs & Services → Credentials**:
   <https://console.cloud.google.com/apis/credentials> (select the **same project** as
   Firebase/Cloud Run — `project-10702811-5962-4bf3-877`).
2. **Create Credentials → OAuth client ID → Application type: Android**.
3. **Name:** e.g. "Spira Android".
4. **Package name:** `com.spiramindscape.android`.
5. **SHA-1 certificate fingerprint:** the debug fingerprint from C1 —
   `96:CF:83:C1:31:0E:6C:D3:AC:B2:90:0E:10:CC:FB:9D:F9:0E:15:7E`
   (get it any time with `cd android; .\gradlew.bat signingReport`).
6. **Create**, then wait ~5 minutes for propagation. No app rebuild needed. Add the **release**
   keystore's SHA-1 here too when you publish.

> ⚠️ **Do this step explicitly — do not assume Firebase created it.** On this project, adding
> the SHA-1 via `firebase apps:android:sha:create` registered the fingerprint on the Firebase
> app but did **not** create a usable Android OAuth client: `firebase apps:sdkconfig ANDROID …`
> showed only a `client_type: 3` (web) entry, no `client_type: 1` (Android). Without the Android
> client, Google sign-in fails at runtime with **error 10 "Developer console is not set up
> correctly"** (see `backlog/mobile-sign-in-developer-error-10.md`). Verify a `client_type: 1`
> entry appears (or the Android client shows in Cloud Console Credentials) before expecting
> sign-in to work.

### C3. Web client ID (audience) ✅ found

`google-services.json` already contains a **web** OAuth client (`client_type: 3`) in the same
project:

```
952567559986-pv46g2sr17vltmsnbcoq5scdjbat2l3d.apps.googleusercontent.com
```

This is the **server client ID**: the Android app requests a Google ID token for it, and the
backend (`POST /api/auth/google/mobile`, Part 2) verifies tokens against it. When implementing
Part 2, confirm the backend's configured `GOOGLE_CLIENT_ID` (used by the web app) either equals
this ID or is added alongside it as an accepted audience.

### C4. Production signing (later, not now)

When you publish (Play Store / release builds), you'll add the **release** keystore's SHA-1 as
another fingerprint on the Android OAuth client. For development and Firebase App Distribution
testing, the **debug** SHA-1 from C1 is enough.

---

## Final checklist

Download / install:

- [x] **Android Studio** — installed at `C:\Program Files\Android\Android Studio1` (A1)
- [x] SDK: **cmdline-tools**, build-tools 37, platform-tools, emulator, **API 34** (A2)
- [x] **JDK for Gradle** — use the embedded **JDK 21** in Android Studio; no separate JDK 17 (A4)
- [x] **Firebase CLI** installed — still run `firebase login` (B5)

Configure:

- [x] `ANDROID_HOME` + PATH env vars (A3) — done (open a fresh terminal)
- [ ] Create an **emulator** (AVD) (A5)
- [x] Gradle JDK — Android Studio's embedded JDK 21 (set per-project when the module is created)

Create on your accounts:

- [x] **Firebase project** — added to existing GCP project `project-10702811-5962-4bf3-877` (B1)
- [x] **Register Android app** `com.spiramindscape.android` + SHA-1 (B2)
- [x] Download **`google-services.json`** (B3, gitignored)
- [x] Enable **App Distribution** (tester group) — used by `:app:distributeDebug`
- [ ] Enable **Crashlytics** in the console (B4) — the app is already wired for it
      (`docs/crash-reporting-and-monitoring.md`); enabling in the console starts showing reports
- [x] **Debug SHA-1** fingerprint generated + registered (C1)
- [ ] **Android OAuth client** — must be created **manually** in Cloud Console (the Firebase CLI
      SHA registration did NOT create it → error 10). See C2 + `backlog/mobile-sign-in-developer-error-10.md` (C2)
- [x] **Web OAuth client ID** identified from `google-services.json` (C3)

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
