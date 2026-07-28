# Brand fonts

Body/labels use **GCentra** and headings use **ITC Clearface** — Gusto's **licensed** brand faces,
self-hosted in this folder; **Playfair Display** is the serif fallback. (Roboto has been removed.)

> **GCentra** ships Book (400) + Medium (500) here as `.otf`. The Medium `@font-face` claims the whole
> `font-weight: 500 900` range, so bold/semibold render GCentra Medium (no faux-bold, no Roboto). Each
> `@font-face` carries `ascent/descent/line-gap-override: 75%/25%/0%` to normalise GCentra's asymmetric
> native metrics so text does **not** shift relative to adjacent icons.
>
> **ITC Clearface** ships `ITCClearface-Bold.otf` + `ITCClearface-BoldItalic.otf` (the only licensed
> weights); the Regular `@font-face` falls back to the Bold file (headings render at 600–700 anyway).

> ⚠️ **Licensing:** GCentra and ITC Clearface are **commercial, licensed** fonts. Only add them if
> you hold a **webfont license** to serve them publicly. Download them from Gusto's Brandfolder
> (the two share links you were given → each asset's **Download** button). Do not commit them to a
> public repo without that license.

## Expected filenames

Drop the files here with **exactly** these names. Any one of `.woff2` / `.otf` / `.ttf` works for
each (the CSS lists all three; the browser uses whichever is present). **`.woff2` is strongly
preferred for the web** — it's the smallest. If your download is `.otf`/`.ttf`, convert to `.woff2`
(e.g. https://cloudconvert.com/otf-to-woff2 or `fonttools`) for best performance, or just keep the
`.otf`/`.ttf` — it still works.

```
public/fonts/
  GCentra-Regular.woff2        (weight 400)
  GCentra-Medium.woff2         (weight 500)
  GCentra-Bold.woff2           (weight 700)
  ITCClearface-Regular.woff2   (weight 400)
  ITCClearface-Bold.woff2      (weight 700)   ← headings use this
```

If GCentra only ships some of those weights, add what you have — the CSS `@font-face` blocks are
per-weight and missing weights simply fall back to Roboto.

## Verify

Run `npm run dev`, open a goal page, and confirm headings render in **ITC Clearface** (serif) and
body in **GCentra** (sans). Before the files are added you'll see a few `404 /fonts/…` in the dev
console — that's expected and disappears once the files are here.

## Android

The web side auto-activates from this folder, but **Android** needs the same files bundled
separately — see `docs/changing-fonts.md` → "Android" for the `res/font/` + `Type.kt` steps.
