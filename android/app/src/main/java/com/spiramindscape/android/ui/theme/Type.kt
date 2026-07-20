package com.spiramindscape.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.R

// Brand typography — the Spira brand guidelines (see CLAUDE.md → "Brand design system").
//
//  • Headlines: Spectral (serif).
//  • Body/labels: Hanken Grotesk (sans).
//  • Leading: headlines 110%, body 130%.  Tracking: headlines slightly tighter.
//
// The font files are BUNDLED under res/font (no network / Play Services dependency), so the
// brand faces render identically on every device and in test renders. See
// docs/google-fonts-compose.md.

// Spectral serif for headlines — bundled static weights.
private val HeadingSerif = FontFamily(
    Font(R.font.spectral_semibold, FontWeight.SemiBold),
    Font(R.font.spectral_bold, FontWeight.Bold),
)

// Hanken Grotesk sans for body/labels — a single bundled VARIABLE font; each weight is a
// FontVariation on the same `wght` axis (supported on minSdk 26+).
@OptIn(ExperimentalTextApi::class)
private val BodySans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(
            R.font.hanken_grotesk,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    },
)

private val base = Typography()

// Headline tracking: a small negative letter-spacing so headline serif glyphs sit optically
// balanced (tighter, but never touching).
private val HeadlineTracking = (-0.01).em

val SpiraTypography = base.copy(
    // Headlines: serif + 110% leading + tight tracking.
    displayLarge = base.displayLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 63.sp, letterSpacing = HeadlineTracking,
    ),
    displaySmall = base.displaySmall.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 40.sp, letterSpacing = HeadlineTracking,
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 35.sp, letterSpacing = HeadlineTracking,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 31.sp, letterSpacing = HeadlineTracking,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 26.sp, letterSpacing = HeadlineTracking,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp, letterSpacing = HeadlineTracking,
    ),
    // Titles that are UI chrome (not headline serif) use the sans.
    titleMedium = base.titleMedium.copy(fontFamily = BodySans),
    titleSmall = base.titleSmall.copy(fontFamily = BodySans),
    // Body copy: sans, 130% leading for legibility (default tracking).
    bodyLarge = base.bodyLarge.copy(fontFamily = BodySans, lineHeight = 21.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = BodySans, lineHeight = 18.sp),
    bodySmall = base.bodySmall.copy(fontFamily = BodySans, lineHeight = 16.sp),
    // Labels (kickers, nav labels, badges): sans.
    labelLarge = base.labelLarge.copy(fontFamily = BodySans),
    labelMedium = base.labelMedium.copy(fontFamily = BodySans),
    labelSmall = base.labelSmall.copy(fontFamily = BodySans),
)
