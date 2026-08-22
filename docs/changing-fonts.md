# Fonts on both surfaces (web + Android)

The single source of truth for **adding a font to the Fonts tab** and for **swapping a brand font**.
They are two different jobs and the first one is the common one, so it comes first.

| Role | Face | Where it is set |
|---|---|---|
| **Headline** (serif) | **ITC Clearface** → Playfair Display → Georgia | web `--font-heading`; Android `HeadingSerif` in `Type.kt` |
| **Body / labels** (sans) | **whatever the Fonts tab is set to** — GCentra is the brand default | web `--font-sans` / `--font-display`, rewritten at runtime; Android `AppFont.fontFamily` |
| Mono | JetBrains Mono | `--font-mono` |

Roboto is **gone**. Nothing in the app falls back to it.

> **Golden rule:** the **leading / tracking / alignment / line-length** rules in
> `CLAUDE.md → Design → Brand → Typography` are **font-independent** — they live on the type
> scale, not on any face. When you change a font, change **only** the family. Never the leading or
> the tracking.

---

## 1. Adding one more candidate to the Fonts tab

**Settings → Fonts** carries **many** faces at once and a tap re-fonts the whole app (GRO-122). The
owner is choosing Spira's sans by looking at each one *in the product* — a face that is handsome in
a specimen paragraph can fall apart in a goal card, a badge and a 10px label — so adding a candidate
is a routine, repeatable operation, not a migration.

It swaps the **body** face only. Headings stay on ITC Clearface: a candidate that only wins by also
replacing the headline serif has not been compared fairly.

### The five files, every time

Web and Android must be done **together**. The whole point of the tab is comparing a face on a phone
against the same face on a laptop; if one surface offers a candidate the other doesn't, that
comparison is quietly broken.

| # | Surface | File | What to add |
|---|---|---|---|
| 1 | web | `public/fonts/` | the files themselves — `<Family>-Regular.*` and `<Family>-Medium.*` |
| 2 | web | `src/styles.css` → "Candidate body faces" | two `@font-face` blocks |
| 3 | web | `src/lib/spira/app-font.ts` | an id in `AppFontId`, an entry in `APP_FONTS` |
| 4 | Android | `android/app/src/main/res/font/` | the same two faces, **lowercase `snake_case`** names |
| 5 | Android | `ui/theme/AppFont.kt` | an enum constant, and its arm in `fontFamily` |

Nothing else. The Settings page on either surface reads the catalogue and needs no edit
(`src/routes/settings.tsx`, `ui/settings/UserSettingsScreen.kt`).

### Step by step

**1 — the files.** Two faces: a **Regular (400)** and one heavier face that will play **Medium**.
Web takes `.woff2` (smallest), `.ttf` or `.otf`; Android takes `.ttf` or `.otf` — **not `.woff2`**,
which Android cannot load. If the archive only has `.woff2`, convert for Android; if it only has
`.ttf`, use the `.ttf` on both.

Android resource names must match `[a-z0-9_]` — `FuturaFuturisC-Bold.ttf` has to become
`futura_futuris_medium.ttf` or the build fails.

```
public/fonts/FuturaFuturis-Regular.woff2      android/app/src/main/res/font/futura_futuris_regular.ttf
public/fonts/FuturaFuturis-Medium.woff2       android/app/src/main/res/font/futura_futuris_medium.ttf
```

**2 — `src/styles.css`.** Two blocks, in the "Candidate body faces" section:

```css
@font-face {
  font-family: "Futura Futuris";
  src: url("/fonts/FuturaFuturis-Regular.woff2") format("woff2");
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: "Futura Futuris";
  src: url("/fonts/FuturaFuturis-Medium.woff2") format("woff2");
  font-weight: 500 900;   /* see "the 500–900 trick" below */
  font-style: normal;
  font-display: swap;
}
```

The `format()` must match the file: `woff2` / `truetype` / `opentype`. Get it wrong and the browser
silently skips the source and draws the system sans — which looks like "the font didn't load" with
nothing in the console.

**Do not add `ascent-override` to a candidate.** GCentra has those overrides to stop body text
drifting against adjacent icons, but forcing every candidate into GCentra's box hides exactly the
difference being looked at. A candidate that sits high or low in its line is *showing you that*.

**3 — `src/lib/spira/app-font.ts`.** Add the id to the `AppFontId` union and an entry to
`APP_FONTS`:

```ts
{
  id: "futura-futuris",
  label: "Futura Futuris",
  family: '"Futura Futuris"',        // quote it if the name has a space
  note: "ParaType's Cyrillic Futura — the real face, not the Jost* revival.",
  cyrillic: true,
},
```

`note` is one line on what the face is, so a name in a list means something. `cyrillic` is not
cosmetic — the tab **groups by it** (`With Cyrillic` first), because a Latin-only face draws Russian
in the system sans and the row is then quietly showing two fonts at once.

**4 + 5 — Android.** Drop the two files under `res/font/`, then in `AppFont.kt` add the constant and
its arm:

```kotlin
FuturaFuturis(
    "Futura Futuris",
    "ParaType's Cyrillic Futura — the real face, not the Jost* revival.",
    cyrillic = true,
);

// …in fontFamily:
FuturaFuturis -> twoWeights(R.font.futura_futuris_regular, R.font.futura_futuris_medium)
```

`twoWeights` is the shared helper. Use it — see the next section for why.

**Keep `label`, `note` and `cyrillic` identical to the web entry**, word for word.

### The 500–900 trick (both surfaces, and it matters)

**Neither CSS nor Compose falls through to another family for a missing weight.** A family with only
400 and 500 registered, asked for 700, does not borrow the system sans's bold — it synthesises a
smeared faux-bold, or on Android drops to the system font entirely. Every heavy label in the app
would then be showing a different face from the body text beside it.

So the heavier file always claims the **whole** heavy range:

- **Web:** `font-weight: 500 900` on the Medium `@font-face`.
- **Android:** the Medium file is registered at `FontWeight.Medium` **and** `FontWeight.Bold` —
  which is all `twoWeights()` does.

This is GCentra's own arrangement (it ships Book + Medium and nothing heavier), and every candidate
copies it. Where a family ships a **Bold** rather than a Medium, the Bold takes the Medium slot; its
bolds will simply read heavier than the others', which is worth knowing while judging it.

### Verify

- `npm run lint`, `npx tsc --noEmit`, `npm test`
- `cd android && ./gradlew.bat :app:compileDebugKotlin`
- **Look at pixels** (CLAUDE.md → Design → Components and chrome → 4): switch to the new face in Settings → Fonts on both
  surfaces and read a goal card, a badge and a 10px label. An existence assertion cannot tell you a
  font failed to load — the text is still there, in the wrong face.
- If the face claims Cyrillic, **type Russian into a goal title** and check it is not the system
  sans. This is the single most common thing to be wrong.
- Distribute the APK (`./gradlew.bat distributeDebug -PreleaseNotes="new font candidate"`).

### Weight and licensing

The candidates cost about **3.1 MB per surface** at the current count. That is real, and it is the
price of comparing faces in the product; it comes back the moment the choice is made (see §3).

Only bundle a face that is **free for commercial use** or that we hold a webfont licence for.
Foundry licence files belong in `public/fonts/licences/`. Files that had to be modified — subset to
Latin + Cyrillic, say — should say so in the `@font-face` comment, because that is a change to the
foundry's release and the next person needs to know it happened.

---

## 2. The current catalogue

Fifteen candidates, all grotesques, all but GCentra and Archivo carrying the Russian alphabet.
**Tilda Sans** is the web's working default; Android defaults to GCentra.

| Face | Cyrillic | Note |
|---|---|---|
| GCentra | — | the brand default, Book + Medium |
| Tilda Sans | ✓ | ParaType/Tilda, geometric grotesque |
| PT Root UI | ✓ | the closest here to GCentra |
| Fixel | ✓ | MacPaw, geometric-humanist, Text cut |
| Garet | ✓ | Book + Heavy only, so its bold is heavy |
| Bartina | ✓ | geometric grotesque |
| Liberation Sans | ✓ | metric-compatible with Arial |
| DejaVu Sans | ✓ | humanist, Verdana mould |
| Archivo | — | grotesque for headlines and small text alike |
| Arimo | ✓ | metric-compatible with Arial |
| IBM Plex Sans | ✓ | neutral corporate grotesque |
| Onest | ✓ | contemporary, drawn for screens |
| Golos Text | ✓ | Russian-first |
| Montserrat | ✓ | wide, even letterforms |
| Futura (Jost*) | ✓ | indestructible type's free Futura revival |
| Futura Futuris | ✓ | ParaType's real Cyrillic Futura (owner-supplied). Shipped **a weight down**: its Light is the body face, its regular the bold — the family's own regular read too heavy at UI sizes |

Jost* and Futura Futuris are **both** listed on purpose: a revival and its original differ most at
exactly the small sizes this app is made of.

---

## 3. Swapping a brand font (the real thing, not a candidate)

Different job. A brand face is not switchable, gets metric overrides, and applies to the login page
too.

### Web

1. **`src/styles.css`** — the `@font-face` blocks at the top (not the candidates section), plus the
   `--font-heading` / `--font-sans` / `--font-display` vars in `@theme inline`.
   - `--font-heading` → the serif headline face (`.font-heading`).
   - `--font-sans` → body copy (the default on `html, body`).
   - `--font-display` → secondary display / bold UI (`.font-display`, `h1`–`h3`, big numbers). It is
     deliberately the **sans**; only `.font-heading` is the serif.
   - A Google-hosted family also goes in the `@import` at the top **and** the matching `<link>` in
     `index.html` — keep those two URLs identical, and the families in **alphabetical** order or the
     `css2` endpoint 400s.
2. **`src/routes/login.tsx`** is self-contained, with its own `--lp-font-heading` / `--lp-font-sans`.
   A brand swap has to update those too or the login screen keeps the old face.

### Android

3. **`ui/theme/Type.kt`** — `HeadingSerif` and the default body family. `spiraTypography(bodySans)`
   assigns them across the scale; **don't touch those assignments or the `lineHeight` /
   `letterSpacing` values.**
   - A **variable** TTF registers one `Font(...)` per weight with `FontVariation.Settings`.
   - A **static** family registers one `Font(R.font.x, FontWeight.Y)` per weight.
4. **`ui/theme/AppFont.kt`** — if the winner was a candidate, it becomes the default here.

### Both

5. **Vertical-metrics normalisation.** A brand face gets it; a candidate does not.
   - Web: `ascent-override` / `descent-override` / `line-gap-override` on the `@font-face`.
   - Android: each style is centred in its line box (`LineHeightStyle(alignment = Center)` +
     `includeFontPadding = false` in `Type.kt`).

   GCentra's native metrics are asymmetric — a full-em hhea ascent and a 30%-of-em typo line-gap —
   so without the overrides body and label text sits off the icon centre line and drifts as the
   font loads.

6. **Two families ship incomplete**, and both workarounds must survive any swap:
   - **GCentra** has Book (400) + Medium (500) only; Medium covers everything heavier.
   - **ITC Clearface** has Bold + Bold Italic only; the Regular slot falls back to the Bold file.

**When the body face is finally chosen**, the Fonts tab is throwaway: make the winner the real
`--font-sans` / `BodySans` **with its own metric overrides**, then delete the catalogue, both
Settings tabs and every unused font file. That is ~3.1 MB per surface waiting to be reclaimed.

---

## Where each rule lives

| Concern | Web | Android |
|---|---|---|
| Candidate catalogue | `src/lib/spira/app-font.ts` | `ui/theme/AppFont.kt` |
| Candidate `@font-face` | `src/styles.css` → "Candidate body faces" | `res/font/` + `twoWeights()` |
| Settings page | `src/routes/settings.tsx` | `ui/settings/UserSettingsScreen.kt` |
| Chosen face is applied | `useApplyAppFont()` rewrites `--font-sans` / `--font-display` on `:root` | `LocalAppFont` → `spiraTypography(bodySans)` |
| Brand family | `--font-*` in `src/styles.css` | `HeadingSerif` / `BodySans` in `Type.kt` |
| Leading (110% / 130%) | per-usage `leading-*` utilities | `lineHeight` in `Type.kt` — don't change |
| Tracking (tight / 0) | per-usage `tracking-*` utilities | `letterSpacing` in `Type.kt` — don't change |
| Logo wordmark | `AppShell.tsx` + `login.tsx` | `SpiraTopBar` |
| Licences | `public/fonts/licences/` | same files, one copy |
