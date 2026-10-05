# Spira Android

Native Kotlin / Jetpack Compose app for Spira. It reuses the same backend as the web app
(GraphQL API + Google sign-in via `POST /api/auth/google/mobile`). See
`specs/2026-07-15-native-mobile-app/plan.md` (Part 3) and `docs/mobile-setup-guide.md`.

## Toolchain

- **JDK 17–21** for Gradle. Android Studio uses its embedded JDK automatically. For terminal
  builds, point `JAVA_HOME` at a compatible JDK — e.g. Android Studio's embedded one:
  `C:\Program Files\Android\Android Studio1\jbr` (JDK 21). The system JDK 22 is **not**
  supported by the Android Gradle Plugin.
- Gradle **8.9** (via the wrapper), AGP **8.6.1**, Kotlin **2.0.20**, Compose BOM **2024.06.00**.
- `compileSdk`/`targetSdk` **34**, `minSdk` **26**.

## Build / run (terminal)

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
.\gradlew.bat :app:installDev             # local backend, NO sign-in — use this locally
.\gradlew.bat :app:assembleDebug          # build the production-pointing debug APK
.\gradlew.bat installDebug                # install that on a running emulator/device
```

The debug APK lands in `app/build/outputs/apk/debug/`, the dev one in `app/build/outputs/apk/dev/`.

### Distribute to testers (one command)

```powershell
.\gradlew.bat distributeDebug -PreleaseNotes="what changed"
```

Builds the debug APK and uploads it to **Firebase App Distribution** using your `firebase login`
(no service-account file). Testers install from any network. See
`docs/mobile-setup-guide.md` §B6.

## Run in Android Studio

Open the `android/` folder (not the repo root) in Android Studio, let it sync, then Run ▶ on
an emulator. It picks up the SDK from `local.properties` (gitignored) and its embedded JDK.

## Backend URL — and whether you have to sign in

Three build types, differing only in the backend baked into `BuildConfig.API_BASE_URL`. That one
difference is also what decides the login, because the app has no bypass of its own: it asks
`/api/auth/me` on start and believes the answer.

| Build type | Backend | Sign-in |
|---|---|---|
| **`dev`** | `http://10.0.2.2:8080` — the host machine as seen from inside the emulator | **none**, when that backend runs the `local` Spring profile (`LocalDevAuthFilter` signs every request in as `dev@local`) |
| **`debug`** | production (Cloud Run) | real Google — this is what `distributeDebug` sends to testers |
| **`release`** | production (Cloud Run) | real Google |

For a real phone on the same Wi-Fi, or a tunnel, override the URL instead of editing a file:
`-PspiraApiBaseUrl=http://<your-PC-LAN-IP>:8080`. `distributeDebug` **refuses to run** with that
flag set, so a distributed APK can never carry a local URL.

Full walkthrough — starting the backend, the emulator, and how to verify the dev build is really
what is running — is in the repo `README.md` → "Build variants — skip Google login for quick
local checks".

## Notes

- `local.properties` and `google-services.json` are **gitignored** (machine-specific / holds an
  API key). Re-create `google-services.json` with `firebase apps:sdkconfig` — see
  `docs/mobile-setup-guide.md`, Part B.
- Launcher icon is an adaptive XML icon (orange background + white dot) — no binary assets.
