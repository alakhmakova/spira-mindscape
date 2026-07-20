# Google Fonts in Jetpack Compose (Implementation Plan)

## Current State (as of Compose BOM 2024.06.00)

The Android app currently uses **system fallback fonts**:
- **Spectral** (target) → **Georgia** (system serif fallback)
- **Hanken Grotesk** (target) → **Roboto** (system sans, default Android font)

The typography rules (110% leading, tight tracking, alignment) are applied to whatever fonts render, so the UI is usable and readable. The web already loads Spectral and Hanken Grotesk via Google Fonts, ensuring web/mobile parity on browsers.

## Why We Can't Use Google Fonts Yet on Android

The Jetpack Compose Google Fonts API (`androidx.compose.ui.text.googlefonts.GoogleFont`) was added in Compose BOM **2024.10.00** (released October 2024), but this project uses **2024.06.00** (June 2024).

Updating the Compose BOM to 2024.10+ would enable direct Google Fonts loading without external dependencies, but it requires:
- Testing compatibility with existing Material 3 components
- Verifying Apollo Kotlin GraphQL client compatibility
- Validating with the rest of the Android build

## Future: Upgrading to Compose 2024.10+

When the Compose BOM is upgraded, update `Type.kt` to this:

```kotlin
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

// Spectral serif for headlines (Google Fonts, Georgia fallback)
private val HeadingSerif = FontFamily(
    Font(GoogleFont("Spectral"), weight = FontWeight.SemiBold),
    Font(GoogleFont("Spectral"), weight = FontWeight.Bold),
)

// Hanken Grotesk sans for body/labels (Google Fonts, Roboto fallback)
private val BodySans = FontFamily(
    Font(GoogleFont("Hanken Grotesk"), weight = FontWeight.Normal),
    Font(GoogleFont("Hanken Grotesk"), weight = FontWeight.Medium),
    Font(GoogleFont("Hanken Grotesk"), weight = FontWeight.SemiBold),
    Font(GoogleFont("Hanken Grotesk"), weight = FontWeight.Bold),
)
```

Then the app will:
1. On cold start: fetch fonts from Google Fonts CDN (takes ~1–3 sec, cached after)
2. On subsequent launches: use cached fonts (instant)
3. If network unavailable: gracefully degrade to system fonts

## Testing Fonts Locally (Without Google Fonts)

The current system fonts work for all brand typography rules. To visually verify:

- Launch the app: `./gradlew.bat :app:assembleDebug && adb install ...`
- Navigate to **Goal Workspace** → any tab
- Check that **headlines appear in a serif font** (Georgia on most devices)
- Check that **body text appears in sans** (Roboto on Android)
- The typography hierarchy (110%/130% leading, spacing) is correct regardless of which font faces render

## Web (Already Done)

The web app (`src/styles.css`) already loads Spectral and Hanken Grotesk from Google Fonts:

```css
@import url('https://fonts.googleapis.com/css2?family=Spectral:wght@600;700&family=Hanken+Grotesk:wght@400;500;600;700&display=swap');

--font-heading: Spectral, Georgia, serif;
--font-sans: "Hanken Grotesk", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
```

So web users see the full Spectral + Hanken Grotesk experience already.

## Implementation Checklist

- [x] Update CLAUDE.md with new font names (Spectral + Hanken Grotesk)
- [x] Web styles.css: Google Fonts @import + font-family updates
- [x] Android Type.kt: Document upgrade path to Compose 2024.10+
- [ ] **Future**: Upgrade Compose BOM to 2024.10+ and implement Google Fonts API
- [ ] **Future**: Verify compatibility after Compose BOM upgrade
- [ ] **Future**: Test fonts load and cache correctly on device

## References

- [Jetpack Compose Google Fonts](https://developer.android.com/jetpack/compose/text/fonts#google-fonts) (requires Compose 2024.10+)
- [Google Fonts](https://fonts.google.com)
- [Compose BOM Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-bom)
- Spira brand design system: `CLAUDE.md` → Typography section
