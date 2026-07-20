# Maestro — mobile E2E testing (guide)

Maestro is the tool the project uses for **end-to-end tests of the Android app**: it drives a
real build on a device/emulator like a user would — tapping buttons, typing, asserting what's on
screen — across multiple screens. It complements, not replaces, the lower test levels:

| Level | Tool | Tests | Runs |
|---|---|---|---|
| Unit | JUnit/Kotlin | pure logic (state machine, parsing) | JVM, no device |
| Component (UI) | Compose UI Test (+ Robolectric) | one screen/composable | JVM, no device |
| **E2E** | **Maestro** | **whole user journeys across screens on a real build** | **device/emulator** |

## When Maestro is actually worth it (why it's deferred for now)

Maestro pays off once there are **real multi-screen user journeys** to protect — e.g. *sign in
→ see the goals dashboard → open a goal → update a target → see progress change → sign out*.
Right now the app only has sign-in + a placeholder screen, so a single login flow
(`android/.maestro/login.yaml`) is all there is, and **you've already verified that manually**.

So: **don't invest in Maestro yet.** It becomes valuable when Steps 4–6 land (dashboard, goal
workspace, low-friction target updates). At that point flows like the one above are exactly what
Maestro is for — journeys that unit and Compose tests can't cover because they span screens and
the real network/session.

## Prerequisites

- A **device or emulator** with **Google Play services + a Google account** (your phone works).
- **adb** on PATH (already set up) and **Java** (already installed).
- A freshly installed **debug build** (`cd android; .\gradlew.bat installDebug`, or Run ▶).
- **Maestro CLI** (below).

## Installing Maestro (Windows)

Maestro officially recommends **WSL** on Windows (native Windows isn't first-class). Steps:

1. Install WSL (Ubuntu) if you don't have it: `wsl --install` in an admin PowerShell, reboot.
2. In the WSL/Ubuntu shell, install Maestro (official one-liner):
   ```bash
   curl -fsSL "https://get.maestro.mobile.dev" | bash
   ```
3. Make sure `adb` inside WSL can see your device (`adb devices`). If not, use `adb` from Windows
   or share the adb server — see Maestro's docs.

Full, always-current instructions: <https://maestro.mobile.dev/getting-started/installing-maestro>

## Running the existing flow

```bash
maestro test android/.maestro/login.yaml
```

It launches the app, taps **Continue with Google**, expects the signed-in screen, then signs out.

> ⚠️ **The Google account chooser is system UI**, not the app. On a device with a single account
> and One Tap it often proceeds automatically; otherwise you tap the account by hand. Completing
> a **real** Google login can't run fully headless — the same limitation as the web OAuth E2E, and
> the reason this can't run unattended in CI.

## Writing a new flow

A flow is a YAML file under `android/.maestro/`. Start with `appId` and a `---`, then a list of
commands. Common ones:

```yaml
appId: com.spiramindscape.android
---
- launchApp:
    clearState: true          # fresh start (logged out)
- assertVisible: "Continue with Google"
- tapOn: "Continue with Google"
- assertVisible:
    text: "Signed in"
    timeout: 30000            # wait up to 30s (network/login)
- tapOn: "Sign out"
- assertVisible: "Continue with Google"
```

Useful commands: `tapOn`, `assertVisible` / `assertNotVisible`, `inputText`, `scrollUntilVisible`,
`back`, `runFlow` (compose flows), `takeScreenshot`. Selectors can be visible **text** or a
Compose **testTag** (add `Modifier.testTag("…")` in the UI, then `id: "…"`).

**Example — a future dashboard journey (Step 4+):**
```yaml
appId: com.spiramindscape.android
---
- launchApp
- assertVisible: "My goals"          # dashboard heading
- tapOn: "Learn Kotlin"              # open a goal card
- assertVisible: "Targets"
- tapOn: "Read 10 pages"             # a target
# … increment progress, then assert the new value
```

## What belongs in Maestro (and what doesn't)

- **Yes:** cross-screen journeys on the real build — sign-in, navigating to a goal, editing a
  target and seeing progress update, offline/error banners, sign-out.
- **No:** business logic (→ unit tests) or single-component behavior (→ Compose UI Test). Those
  are faster and run without a device.

## CI note

Maestro needs a running emulator (or device), so it is **not** part of the per-push CI. When we
add it, run it by **tag / manual dispatch** on a job that boots an Android emulator — the fast
unit + Compose (Robolectric) tests stay on every push.
