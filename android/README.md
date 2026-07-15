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
.\gradlew.bat :app:assembleDebug          # build the debug APK
.\gradlew.bat installDebug                # install on a running emulator/device
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Run in Android Studio

Open the `android/` folder (not the repo root) in Android Studio, let it sync, then Run ▶ on
an emulator. It picks up the SDK from `local.properties` (gitignored) and its embedded JDK.

## Backend URL

The emulator reaches a locally-running backend at `http://10.0.2.2:8080` (that IP is the host
machine as seen from inside the emulator). Build config wiring for `local` vs `prod` is added
with the GraphQL layer (Step 2).

## Notes

- `local.properties` and `google-services.json` are **gitignored** (machine-specific / holds an
  API key). Re-create `google-services.json` with `firebase apps:sdkconfig` — see
  `docs/mobile-setup-guide.md`, Part B.
- Launcher icon is an adaptive XML icon (orange background + white dot) — no binary assets.
