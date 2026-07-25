# Brand fonts (drop the licensed files here)

Spira's brand fonts are **GCentra** (body) and **ITC Clearface** (headings) — Gusto's licensed
brand faces. The CSS (`src/styles.css`) and the login page already reference them, with **Roboto**
and **Playfair Display** as the loaded fallbacks. The moment the files below exist in this folder,
the real fonts activate — no code change needed.

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
