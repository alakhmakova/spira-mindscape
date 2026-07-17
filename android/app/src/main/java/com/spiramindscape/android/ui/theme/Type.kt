package com.spiramindscape.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.spiramindscape.android.R

// Headings mirror the web, which renders them in the serif Playfair Display
// (`--font-heading` in styles.css). Bundled as a variable font; we pin the two weights we use.
@OptIn(ExperimentalTextApi::class)
private val Playfair = FontFamily(
    Font(
        R.font.playfair_display,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.playfair_display,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

// Body/labels keep the platform sans (matches the web's system-sans body fallback).
private val base = Typography()

val SpiraTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Playfair, fontWeight = FontWeight.SemiBold),
)
