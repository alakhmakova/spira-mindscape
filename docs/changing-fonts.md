# Changing the Spira fonts (web + Android)

This is the **single source of truth** for swapping the app's fonts.

| Role | Brand font | Loaded fallback (web) | Loaded fallback (Android) |
|---|---|---|---|
| **Headline** (serif) | **ITC Clearface** | Playfair Display → Georgia | bundled Playfair Display → system serif |
| **Body / labels** (sans) | **GCentra** | Roboto → system sans | Roboto (system sans) |

> ### Activating the brand fonts (ITC Clearface + GCentra)
>
> **Status: the brand files are now added** (`public/fonts/` for web, `res/font/` for Android), so
> ITC Clearface + GCentra are active on both surfaces. Playfair Display remains the declared serif
> fallback; **Roboto has been removed entirely** — GCentra is the sole sans. Three details a future
> swap must preserve:
>
> - **GCentra ships Book (400) + Medium (500) only**, and Medium covers everything heavier. On web
>   the Medium `@font-face` claims `font-weight: 500 900`; on Android `Font(R.font.gcentra_medium,
>   FontWeight.Bold)` is registered alongside the Medium entry. So bold/semibold text renders GCentra
>   Medium with no faux-bold synthesis and no Roboto fallback (neither CSS nor Compose falls through
>   to another family for a missing weight, so the family must carry its own heavy slot).
> - **ITC Clearface ships Bold + Bold Italic only**; the Regular slot falls back to Bold.
> - **Vertical-metrics normalisation** keeps text from shifting relative to icons: web uses
>   `ascent/descent/line-gap-override` on the `@font-face` blocks; Android centres each style in its
>   line box (`LineHeightStyle(alignment = Center)` + `includeFontPadding = false` in `Type.kt`).
>   GCentra's native metrics are asymmetric (full-em ascent, 30% typo line-gap), so a naive swap
>   would push body/label text off the icon centre line.
>
> The original "how to add them" steps below are kept for reference / re-adding after a font swap.
>

## The Fonts tab — trying a candidate without a swap (GRO-122, 2026-08-13)

The owner is choosing Spira's **body** face and wanted to judge candidates in the product rather
than on a specimen sheet, so the app carries a **runtime switch**: **Settings → Fonts** offers
GCentra (the default) plus **Toronto, Guidy, Kalamayka, Leggibilmente, BIM and Spartan**, and
picking one re-fonts the whole app at once. The choice is per device and survives a restart.

This is deliberately **not** the swap procedure above:

- It moves **only the body face**. `--font-heading` / `HeadingSerif` stay on ITC Clearface, because
  the heading serif is not what is being chosen. A candidate that only wins by also replacing the
  headline face has not been compared fairly.
- It applies **no metric overrides**. GCentra's `ascent/descent/line-gap-override` exist to stop
  body text drifting against adjacent icons — but forcing every candidate into GCentra's box would
  hide exactly the difference the owner is looking at. So a candidate that sits high or low in its
  line is *showing you that*, not misbehaving.
- Most candidates ship **Regular only**, so their bolds are synthesised (Kalamayka and
  Leggibilmente are variable; Spartan ships SemiBold). Worth remembering when judging a heavy label.

### The Cyrillic group (2026-08-14)

That first shortlist is **Latin-only — GCentra included**, so Russian in any of those rows is drawn
by the system sans and the row is quietly showing two faces at once. Only Guidy and BIM carry the
Russian alphabet.

So the tab now has a second group, **With Cyrillic**, and the list is ordered with it first:
**Moderustic, Akt, LT Superior, Fontimer, LINE Seed, KS Bistra, Lineyka, Stetica, Involve,
Hikasami, Farabee, J Audio Cassette, Gros Ventre, Deledda Open** and **Deledda Closed** — fifteen
grotesques, all free for commercial use, each carrying the full 66-letter Russian alphabet.
(Deledda ships as two families that differ only in aperture, so both are offered rather than one
being chosen on the owner's behalf.) They were picked from the intersection of
fontesk's *grotesk* and *Cyrillic* tags by measuring **x-height and average advance width against
GCentra's** and then looking at a rendered specimen, because metrics alone cannot tell a grotesque
from a display face. Moderustic is the closest on both numbers (3% / 1%).

Two things to know about the files:

- Eleven of them ship a second, heavier face registered for 500–900 exactly as GCentra's Medium is,
  so their bold labels are real rather than synthesised (for LINE Seed and the two Deleddas that
  face is a Bold/SemiBold — neither has a Medium). KS Bistra, Lineyka and J Audio Cassette are
  single-weight.
- **LINE Seed is the one file we modified.** Cyrillic only exists in its Korean release, which
  carries the whole syllabary at 3.2 MB, so it ships subset to Latin + Cyrillic (30 KB). Every other
  file is exactly as the foundry released it. Licences are in `public/fonts/licences/`.

| Where | File |
|---|---|
| Web catalogue + the CSS-variable switch | `src/lib/spira/app-font.ts` |
| Web `@font-face` blocks | `src/styles.css` ("Candidate body faces") |
| Web page | `src/routes/settings.tsx` |
| Android catalogue + preference | `ui/theme/AppFont.kt` |
| Android type scale, now a function of the body family | `ui/theme/Type.kt` → `spiraTypography(bodySans)` |
| Android page | `ui/settings/UserSettingsScreen.kt` |

**Keep the two lists in step.** The point of the tab is comparing a face on a phone against the
same face on a laptop; if one surface offers a candidate the other doesn't, that comparison is
quietly broken.

**When the choice is made**, this tab is throwaway: follow the swap steps above to make the winner
the real `--font-sans` / `BodySans` (with its own metric overrides), then delete the catalogue, the
tab and the unused font files. Font files are not small — the twenty candidates now cost the APK
about 2.7 MB, which is worth deleting the moment the choice is made.

> **Web:** download the licensed files from Gusto's Brandfolder and drop them into **`public/fonts/`**
> with the names listed in **`public/fonts/README.md`** (`GCentra-*.woff2`, `ITCClearface-*.woff2`).
> The `@font-face` blocks in `src/styles.css` pick them up automatically — no code change.
>
> **Android:** bundle the same faces (`.otf`/`.ttf`) under `res/font/` (lowercase `snake_case`
> names) and point `HeadingSerif` → ITC Clearface and `BodySans` → GCentra in `Type.kt` (see the
> Android section below — this is a code change, unlike web).
>
> ⚠️ **Licensing:** only add these files if you hold a **webfont license** to serve them; don't
> commit them to a public repo otherwise.

---

The rest of this doc is the generic swap procedure for **any** font (the current fallbacks are
Playfair Display + Roboto).

> **Golden rule:** the **leading / tracking / alignment / line-length** rules in
> `CLAUDE.md → Brand design system → Typography` are **font-independent**. They live on the type
> scale, not on any specific face. When you change a font, change **only** the font-family values
> below — never the leading/tracking. If a heading font is swapped, only the *serif* family changes;
> if a body font is swapped, only the *sans* family changes.

There are exactly **four** places to touch. Do the web two and the Android two together so the
surfaces stay in parity.

---

## Web

### 1. `src/styles.css` — load the font + point the CSS vars at it

At the very top, the Google Fonts `@import` lists every family the app uses (families must stay in
**alphabetical** order or the `css2` endpoint 400s):

```css
@import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500&family=Playfair+Display:wght@600;700&family=Roboto:wght@400;500;700&display=swap');
```

Then in the `@theme inline { … }` block, the family vars:

```css
--font-sans:    Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
--font-display: Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
--font-heading: "Playfair Display", Georgia, serif;
```

- `--font-heading` → the **serif headline** face (the `.font-heading` class + `font-heading` Tailwind
  utility). This is the one users mean by "the Playfair headings".
- `--font-sans` → **body copy** (the default on `html, body`).
- `--font-display` → **secondary display / bold UI text** (`.font-display`, `h1–h3`, big stat
  numbers). This is intentionally the **sans**, not the serif — only `.font-heading` is the serif.
- `--font-mono` → code/monospace (JetBrains Mono); rarely changed.

**To swap a font:** change the family name in the `@import` (keep alphabetical order + the weights
you actually use) **and** in the matching `--font-*` var. Keep a sensible fallback after it.

### 2. `index.html` — the preload `<link>`

`index.html` mirrors the same Google Fonts URL in a `<link rel="stylesheet">` (with `preconnect`s)
so the fonts start downloading before the CSS parses. **Keep this URL identical to the `@import` in
`styles.css`** — if you change one, change the other.

### 3. The login page is self-contained

`src/routes/login.tsx` has its own scoped CSS variables (`--lp-font-heading`, `--lp-font-sans`) so
the marketing/login screen can be styled independently. If a font swap should also apply to the
login screen, update those two vars too. (It already uses `"Playfair Display"` for headings and a
system/Roboto sans for body + the **`spira` wordmark**.)

---

## Android

Android does **not** use the web CSS. Fonts live in `Type.kt`.

### 4. `android/app/src/main/java/com/spiramindscape/android/ui/theme/Type.kt`

Two `FontFamily` values drive everything:

```kotlin
// Headlines — bundled variable TTF, weights via FontVariation.
private val HeadingSerif = FontFamily(
    listOf(FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.playfair_display, weight = w,
             variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

// Body/labels — Android's system sans (Roboto). No bundled file, no network.
private val BodySans = FontFamily.Default
```

`SpiraTypography` then assigns `HeadingSerif` to the display/headline/titleLarge scales and
`BodySans` to body/label/title-chrome scales. **Don't touch those assignments or the
`lineHeight` / `letterSpacing` values** — only the two families above.

#### Changing the **headline** (serif) font

The serif is a **bundled variable font** so it renders offline and in Robolectric test renders.
To swap it:

1. Download the new family's **variable** TTF and drop it in `android/app/src/main/res/font/` with a
   lowercase, `snake_case` name (Android resource names must be `[a-z0-9_]`), e.g.
   `res/font/playfair_display.ttf`. A quick way to grab a Google font's variable TTF:
   ```bash
   curl -sL -o android/app/src/main/res/font/<name>.ttf \
     "https://github.com/google/fonts/raw/main/ofl/<family>/<FileName>%5Bwght%5D.ttf"
   ```
2. Point `HeadingSerif` at `R.font.<name>` (the filename without extension).
3. Delete the old TTF from `res/font/` once nothing references it (`grep -r R.font. android/app/src`).

> If the new font is a **static** (non-variable) family, register one `Font(R.font.x, FontWeight.Y)`
> per weight instead of the `FontVariation` loop (see git history of this file for the old Spectral
> setup, which bundled static weights).

#### Changing the **body** (sans) font

- If you want to keep **Roboto**, leave `BodySans = FontFamily.Default` (Roboto is the Android system
  sans — free, offline, no bundling).
- For a **different** body font, bundle its TTF(s) under `res/font/` and build a `FontFamily` the same
  way as the serif, then set `BodySans` to it. Prefer a variable TTF so all weights come from one file.

---

## After any font change — verify

Run the Definition-of-Done checks and **look at pixels** (CLAUDE.md rule #4 — existence assertions
lie):

- **Web:** `npm run lint`, `npx tsc --noEmit`, `npm test`; then `npm run dev` and eyeball a headline
  (serif), body copy (sans), and the `spira` logo.
- **Android:** `cd android && ./gradlew.bat :app:assembleDebug`; render a `VisualCheck*` PNG (or
  screenshot the emulator) and confirm headlines are the serif and body is the sans. Then
  **distribute the APK** (`./gradlew.bat distributeDebug -PreleaseNotes="font change"`).
- **Typography compliance:** re-read `CLAUDE.md → Typography` and confirm headings use the serif,
  everything else the sans, headline leading ≈110% / body ≈130%, headline tracking slightly negative,
  and clear space between header and body.

## Where each rule is enforced

| Concern | Web | Android |
|---|---|---|
| Which family | `--font-*` in `src/styles.css` | `HeadingSerif` / `BodySans` in `Type.kt` |
| Font download | `@import` in `styles.css` + `<link>` in `index.html` | bundled TTF in `res/font/` (serif) / system (Roboto) |
| Leading (110% / 130%) | per-usage `leading-*` utilities | `lineHeight` in `Type.kt` (don't change) |
| Tracking (tight / 0) | per-usage `tracking-*` utilities | `letterSpacing` in `Type.kt` (don't change) |
| Logo wordmark | `AppShell.tsx` + `login.tsx` (`spira`, sans, extra-bold) | n/a (no wordmark screen yet) |
