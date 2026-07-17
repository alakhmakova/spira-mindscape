package com.spiramindscape.android.ui.theme

import androidx.compose.ui.graphics.Color

// Semantic confidence colors, ported 1:1 from the web (`src/components/spira/confidence-color.ts`).
private val ConfidenceLow = Color(0xFFEF7B6C)   // <= 4  (coral)
private val ConfidenceMid = Color(0xFFF8D068)   // <= 7  (yellow)
private val ConfidenceHigh = Color(0xFF7ECEC4)  // > 7   (teal-green)

/** Color for a 1–10 confidence value, matching the web. */
fun confidenceColor(value: Int): Color = when {
    value <= 4 -> ConfidenceLow
    value <= 7 -> ConfidenceMid
    else -> ConfidenceHigh
}
