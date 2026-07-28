package com.spiramindscape.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.R

// Brand typography — the Spira brand guidelines (see CLAUDE.md → "Brand design system").
//
//  • Headlines: ITC Clearface (serif) — falls back to Playfair Display / system serif.
//  • Body/labels: GCentra sans — Book (400) for normal text, Medium (500) for everything heavier
//    (bold/semibold render Medium too). No Roboto anywhere.
//  • Leading: headlines 110%, body 130%.  Tracking: headlines slightly tighter.
//
// To change these fonts (both here and on web), follow docs/changing-fonts.md — change
// ONLY the two FontFamily values below; the leading/tracking/weight rules are
// font-independent and must stay.

// ITC Clearface serif for headlines — bundled static OTFs under res/font (Bold + Bold Italic are
// the only licensed weights). Both the SemiBold (titleLarge) and Bold (display/headline) headline
// weights map to the Bold file. Bundled so the brand serif renders identically on every device and
// in test renders (no network needed).
private val HeadingSerif = FontFamily(
    Font(R.font.itc_clearface_bold, weight = FontWeight.SemiBold),
    Font(R.font.itc_clearface_bold, weight = FontWeight.Bold),
    Font(R.font.itc_clearface_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
)

// GCentra sans for body/labels — bundled Book (400) + Medium (500), the only two weights that ship.
// Medium is registered for BOTH FontWeight.Medium (500) and FontWeight.Bold (700), so bold/semibold
// text resolves to GCentra Medium with no faux-bold synthesis (Compose trusts the declared weight of
// the Font entry) and never falls back to Roboto. There is no Roboto in the app.
private val BodySans = FontFamily(
    Font(R.font.gcentra_book, weight = FontWeight.Normal),
    Font(R.font.gcentra_medium, weight = FontWeight.Medium),
    Font(R.font.gcentra_medium, weight = FontWeight.Bold),
)

// Vertical-metrics normalisation. GCentra ships asymmetric metrics (OS/2 typo ascent/descent 700/300
// with a 30%-of-em line-gap; hhea ascent a full em), so a naive swap would shift body/label text up
// or down INSIDE its line box — i.e. it would no longer sit centred against adjacent icons. Centring
// the glyphs within the line height (and disabling the legacy font padding) makes vertical position
// independent of each font's native metrics, so text stays put relative to icons regardless of which
// face (GCentra Book, GCentra Medium, or ITC Clearface) renders it.
private val CentredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun TextStyle.brandMetrics(): TextStyle =
    copy(platformStyle = NoFontPadding, lineHeightStyle = CentredLineHeight)

private val base = Typography()

// Headline tracking: a small negative letter-spacing so headline serif glyphs sit optically
// balanced (tighter, but never touching).
private val HeadlineTracking = (-0.01).em

val SpiraTypography = base.copy(
    // Headlines: serif + 110% leading + tight tracking.
    displayLarge = base.displayLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 63.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    displaySmall = base.displaySmall.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 40.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 35.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 31.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.Bold,
        lineHeight = 26.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    titleLarge = base.titleLarge.copy(
        fontFamily = HeadingSerif, fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp, letterSpacing = HeadlineTracking,
    ).brandMetrics(),
    // Titles that are UI chrome (not headline serif) use the sans.
    titleMedium = base.titleMedium.copy(fontFamily = BodySans).brandMetrics(),
    titleSmall = base.titleSmall.copy(fontFamily = BodySans).brandMetrics(),
    // Body copy: sans, 130% leading for legibility (default tracking).
    bodyLarge = base.bodyLarge.copy(fontFamily = BodySans, lineHeight = 21.sp).brandMetrics(),
    bodyMedium = base.bodyMedium.copy(fontFamily = BodySans, lineHeight = 18.sp).brandMetrics(),
    bodySmall = base.bodySmall.copy(fontFamily = BodySans, lineHeight = 16.sp).brandMetrics(),
    // Labels (kickers, nav labels, badges): sans.
    labelLarge = base.labelLarge.copy(fontFamily = BodySans).brandMetrics(),
    labelMedium = base.labelMedium.copy(fontFamily = BodySans).brandMetrics(),
    labelSmall = base.labelSmall.copy(fontFamily = BodySans).brandMetrics(),
)
