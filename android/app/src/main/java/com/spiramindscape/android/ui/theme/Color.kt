package com.spiramindscape.android.ui.theme

import androidx.compose.ui.graphics.Color

// Spira brand tokens, mirrored from the web app's single source of truth: the `:root` custom
// properties in `src/styles.css` (oklch), converted to sRGB hex. The web's PRIMARY is teal
// (`--primary: oklch(0.51 0.092 194)`); orange is only a secondary accent (`--brand-orange`).
// Keep these in sync with styles.css so web and mobile stay coherent.

val SpiraTeal = Color(0xFF007675)          // --primary
val SpiraOnPrimary = Color(0xFFFCFCFC)     // --primary-foreground
val SpiraTealSoft = Color(0xFFD9F6EF)      // --primary-soft / --accent
val SpiraAmber = Color(0xFFD59E46)         // --brand-orange (accent) / --warning
val SpiraAmberSoft = Color(0xFFFBE9CA)     // --brand-orange-soft

val SpiraBackground = Color(0xFFF9F9F9)    // --background / --surface
val SpiraSurfaceRaised = Color(0xFFFFFFFF) // --surface-raised (cards)
val SpiraSurfaceSunken = Color(0xFFEBEBEB) // --surface-sunken / --muted

val SpiraForeground = Color(0xFF333333)    // --foreground
val SpiraMutedForeground = Color(0xFF666666) // --muted-foreground

val SpiraDestructive = Color(0xFFE43D38)   // --destructive
val SpiraSuccess = Color(0xFF337B31)       // --success

val SpiraBorder = Color(0xFFDDDDDD)        // --border / --input
val SpiraBorderStrong = Color(0xFFA4A4A4)  // --border-strong
