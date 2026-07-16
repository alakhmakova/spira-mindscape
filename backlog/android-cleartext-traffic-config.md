# Android: allow cleartext HTTP for local dev only, keep release HTTPS-only

- **ID:** BUG-003
- **Status:** 🐞 Open (security hardening + dev-convenience)
- **Reported by:** Security review during native-mobile work
- **Area:** Android app — network security config (`android/app/src/main/AndroidManifest.xml`)
- **Severity:** Low (release is already safe; this unblocks local HTTP testing and makes the
  HTTPS-only intent explicit)

## Summary

At `targetSdk 34` the app blocks **cleartext (HTTP)** traffic by default
(`usesCleartextTraffic` defaults to `false` since API 28), and we set no override. Two
consequences:

- ✅ **Release is safe** — the app will only talk HTTPS (prod Cloud Run is HTTPS). Good.
- ⚠️ **Local backend testing over HTTP currently fails** — pointing `API_BASE_URL` at a local
  backend (`http://10.0.2.2:8080` on the emulator, or `http://<LAN-IP>:8080` on a real phone)
  is blocked, because that's cleartext.

We want cleartext permitted **only for debug builds and only for dev hosts**, while release
stays HTTPS-only.

## Steps to reproduce

1. Set `API_BASE_URL` to a local HTTP backend (e.g. `http://10.0.2.2:8080`).
2. Run the **debug** app and try any request (sign-in, GraphQL).
3. The call fails with a cleartext-not-permitted error; only HTTPS backends work today.

## Root cause

`targetSdk >= 28` disables cleartext by default, and the app has no
`android:networkSecurityConfig` / no debug override, so HTTP is blocked in every build type.

## Fix approach

- Add a **debug-only** network security config that permits cleartext for dev hosts only:
  - `android/app/src/debug/res/xml/network_security_config.xml` with a
    `<domain-config cleartextTrafficPermitted="true">` listing `10.0.2.2`, `localhost`, and the
    LAN range(s) used for phone testing.
  - Reference it from a `android/app/src/debug/AndroidManifest.xml` via
    `android:networkSecurityConfig="@xml/network_security_config"` (debug source set only).
- Leave **release/main** at the default (cleartext blocked). Optionally set
  `android:usesCleartextTraffic="false"` explicitly on the main manifest to make the HTTPS-only
  intent obvious.

## How to verify fixed

- **Debug** build reaches a local HTTP backend (sign-in + GraphQL work against `10.0.2.2`/LAN).
- **Release** build refuses cleartext: an `http://` base URL fails, `https://` works.

## Resolution

_(fill in when implemented — likely alongside release/Firebase distribution setup, Part 4.)_
