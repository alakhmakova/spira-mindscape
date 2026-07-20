package com.spiramindscape.android.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Spira brand palette — the Spira brand guidelines (see CLAUDE.md → "Brand design
// system"). These are the EXACT brand hexes. The semantic tokens below (SpiraTeal,
// SpiraForeground, …) are mapped onto this ramp so every component picks up the brand
// automatically via the Material colorScheme + `spiraExtras`.
//
// Colour roles (brand rules — do not break):
//  • Kale (teal) is the UI primary: buttons, active states, teal bands/backgrounds.
//  • Guava (coral) is the brand ACCENT/highlight only — NEVER a background fill,
//    NEVER as text over Kale.
//  • White is the primary canvas; Parsnip/Ginger/Salt are the supporting neutrals.
//  • Tints (the -100/-200/-300 steps) are used sparingly.
//
// NOTE: the web (`src/styles.css`) still carries the older teal/orange tokens — it must
// be migrated to this same palette for web/mobile parity (see backlog).
// ─────────────────────────────────────────────────────────────────────────────

// Guava (coral) — primary brand accent. Never a background fill.
val Guava100 = Color(0xFFFFF3EF)
val Guava200 = Color(0xFFFEEFE8)
val Guava300 = Color(0xFFFAC6B9)
val Guava400 = Color(0xFFF49582)
val Guava500 = Color(0xFFF45D48)
val Guava600 = Color(0xFFEF523C)

// Kale (teal) — UI primary (surfaces, buttons, active states).
val Kale100 = Color(0xFFF3FAFB)
val Kale200 = Color(0xFFE0F2F5)
val Kale300 = Color(0xFF8DD3D4)
val Kale400 = Color(0xFF2BABAD)
val Kale500 = Color(0xFF0A8080)
val Kale600 = Color(0xFF005961)

// Ginger — warm supporting background.
val Ginger100 = Color(0xFFFFFAF2)
val Ginger200 = Color(0xFFFFF2DF)

// Parsnip — neutral warm-grey supporting background.
val Parsnip100 = Color(0xFFFBFAFA)
val Parsnip200 = Color(0xFFF8F5F2)

// Salt — the neutral grey ramp (borders, text, surfaces).
val Salt200 = Color(0xFFFBFAFA)
val Salt300 = Color(0xFFF4F4F3)
val Salt400 = Color(0xFFEAEAEA)
val Salt500 = Color(0xFFDCDCDC)
val Salt600 = Color(0xFFBABABC)
val Salt700 = Color(0xFF919197)
val Salt800 = Color(0xFF6C6C72)
val Salt900 = Color(0xFF525257)
val Salt1000 = Color(0xFF222525)

val White = Color(0xFFFFFFFF)

// ── Semantic tokens (what components reference) ──────────────────────────────

val SpiraTeal = Kale500                    // UI primary
val SpiraOnPrimary = White                 // white copy on Kale (never black on Kale)
val SpiraTealSoft = Kale200                // soft teal tint (nav indicator, containers)

// Brand accent/highlight — Guava coral. Only for small marks/emphasis, NEVER a fill.
val SpiraAccent = Guava500
val SpiraAccentSoft = Guava200
// Back-compat aliases (older code called the accent "amber"); both now resolve to Guava.
val SpiraAmber = SpiraAccent
val SpiraAmberSoft = SpiraAccentSoft

val SpiraBackground = Parsnip100           // the near-white page canvas
val SpiraSurfaceRaised = White             // cards / menus (pure white)
val SpiraSurfaceSunken = Salt300           // sunken wells

val SpiraForeground = Salt1000             // primary text
val SpiraMutedForeground = Salt800         // secondary/muted text

val SpiraDestructive = Guava600            // destructive/delete (kept within the Guava ramp)
val SpiraSuccess = Color(0xFF337B31)       // functional success green (brand has no green)

val SpiraBorder = Salt500                  // hairline borders / inputs
val SpiraBorderStrong = Salt600            // stronger outline
