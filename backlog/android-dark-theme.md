# Android dark theme — dropped to mirror the web; revisit later

- **ID:** BUG-008 (enhancement — tracked at the user's request)
- **Status:** 🐞 Open
- **Reported by:** User (decision during the mobile design/parity spec)
- **Area:** Android app — `ui/theme/Theme.kt`
- **Severity:** Low (design parity decision, not a defect)

## Summary

The Android app originally shipped a **dark theme** (a hasty first pass). The web app has **no
functioning dark mode** — only a single light palette in `src/styles.css` (a `dark` Tailwind
variant is declared but there is no `.dark {}` block, so nothing re-themes). To make the mobile
app a faithful mirror of the web, the design/parity work makes Android **light-only** and removes
the dark scheme.

## What to do later

Bring back a dark theme once there's something to mirror or a deliberate mobile-native design:
- **Option A:** add a real dark palette to the **web** first (`src/styles.css` `.dark` tokens),
  then mirror it into the Compose theme — keeps the two surfaces consistent.
- **Option B:** design a mobile-native dark palette derived from the teal light tokens (respecting
  contrast/AA), accepting that it won't match a (nonexistent) web dark mode.

Either way, re-introduce the dark `ColorScheme` + `isSystemInDarkTheme()` switching in
`ui/theme/Theme.kt` and verify all kit components + screens in dark.

## How to verify fixed

- Toggle the device to dark mode: the app renders a coherent dark palette with adequate contrast,
  and (if Option A) matches the web's dark tokens.

## Resolution

_(empty — open)_
