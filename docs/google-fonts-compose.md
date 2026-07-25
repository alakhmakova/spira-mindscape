# Fonts in Jetpack Compose (Android)

> **Looking to change the fonts?** See **[`changing-fonts.md`](./changing-fonts.md)** — the single
> source of truth for swapping fonts on **both** web and Android. This file just explains *how the
> Android side loads them today*.

## How Android loads the brand fonts

`android/app/src/main/java/com/spiramindscape/android/ui/theme/Type.kt` defines two font families:

- **Headlines → Playfair Display (serif).** Shipped as a **bundled variable TTF**,
  `android/app/src/main/res/font/playfair_display.ttf`. Each weight is a `FontVariation` on the
  `wght` axis (supported on `minSdk 26+`), so a single file covers SemiBold + Bold. Bundling means
  the brand serif renders **identically on every device, offline, and in Robolectric test renders** —
  no network fetch and no Google Play Services dependency.

- **Body / labels → Roboto (sans).** `BodySans = FontFamily.Default`. Roboto is Android's
  **system-default sans**, so the default family resolves to it with no bundled file and no download.

`SpiraTypography` assigns the serif to the display/headline/`titleLarge` scales and the sans to the
body/label/title-chrome scales, then applies the brand **leading** (headlines 110%, body 130%) via
`lineHeight` and the tight headline **tracking** via `letterSpacing`. Those numbers are
font-independent — they stay when the font changes.

## Why bundled TTF instead of downloadable Google Fonts?

The Jetpack Compose *downloadable* Google Fonts API (`androidx.compose.ui.text.googlefonts.GoogleFont`)
fetches faces from a Google Play Services font provider at runtime. We deliberately **don't** use it
for the headline serif because:

- it needs the network on first run (and Play Services present), so the brand serif would flash a
  fallback on cold start or fail entirely on a Play-less device; and
- it **doesn't render in Robolectric/JVM test renders**, which our `VisualCheck*` pixel checks rely on.

A bundled variable TTF avoids all of that. Roboto needs nothing at all because it's the system sans.

## Verifying fonts on Android

- Build: `cd android && ./gradlew.bat :app:assembleDebug`
- Render a `VisualCheck*` PNG (writes to `app/build/reports/visual/`) or screenshot the emulator
  (`adb exec-out screencap`) and **look**: headlines must be the **serif** (Playfair Display), body
  the **sans** (Roboto). (CLAUDE.md rule #4 — existence assertions lie; verify pixels.)
- Ship it: `./gradlew.bat distributeDebug -PreleaseNotes="…"`.

## References

- Swap procedure (web + Android): [`changing-fonts.md`](./changing-fonts.md)
- Brand typography rules: `CLAUDE.md` → Brand design system → Typography
- [Jetpack Compose fonts](https://developer.android.com/jetpack/compose/text/fonts)
- [Downloadable Google Fonts in Compose](https://developer.android.com/jetpack/compose/text/fonts#downloadable-fonts) (intentionally not used here)
