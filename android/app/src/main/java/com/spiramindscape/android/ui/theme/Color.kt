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

// ── Extended ramps ───────────────────────────────────────────────────────────
// The full-resolution ramps (CLAUDE.md → "Extended ramps (also allowed)"). Only the steps
// something actually uses are declared here — add more from the documented table as needed,
// never an in-between shade of your own.

val Brand1100 = Color(0xFF003737)          // deep teal copy on white, inside a teal panel
val Success400 = Color(0xFF5FCD91)         // "connected" state dot
val Success900 = Color(0xFF007A4B)         // solid success on white
val Warning300 = Color(0xFFFFDEA1)         // soft amber that reads on a teal ground
val Warning400 = Color(0xFFEBAF00)         // "not connected" state dot

// ── Extended ramps (CLAUDE.md → "Extended ramps (also allowed)") ─────────────
// Only the steps the app actually uses are declared; add more from the table as they are needed.

// `intelligence` — the assistant/AI accent, and the only place the app uses violet.
val Intelligence300 = Color(0xFFE6DFF9)
val Intelligence500 = Color(0xFFA28DFF)
val Intelligence700 = Color(0xFF8871EB)
val Intelligence900 = Color(0xFF6E56CF)

// `brand` — the teal ramp at full resolution. 1200 is its deepest step: a near-black teal that
// carries a whole surface without going grey, which is what the AI panel is set on.
val Brand1200 = Color(0xFF182928)

// Badge pairs — the 100 step of each ramp as the pale fill, its solid step as the outline and
// type (see `SpiraBadge`). The 100s are nearly white on purpose: the outline carries the colour.
val Brand100 = Color(0xFFF9FDFC)
val Info100 = Color(0xFFFDFCFF)
val Info900 = Color(0xFF006CC1)
val Intelligence100 = Color(0xFFFEFBFF)
val Success100 = Color(0xFFF8FDF7)
val Warning100 = Color(0xFFFFFBF7)
val Warning900 = Color(0xFF896500)
val Error100 = Color(0xFFFFFBFB)
val Neutral100 = Color(0xFFFAFAFA)
val Neutral1200 = Color(0xFF6B6B6B)

// `error` — semantic state only (a validation failure, a destructive confirmation), never
// decoration. Guava stays the brand accent; these are the red the guidelines reserve for danger.
val Error200 = Color(0xFFFFEDEB)
val Error700 = Color(0xFFE84D4C)
val Error800 = Color(0xFFD74041)      // the overdue / danger tone on white
val Error900 = Color(0xFFC53336)      // a solid destructive button

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

// Destructive/danger is a SEMANTIC state, so it comes from the `error` ramp — Guava is the
// brand accent and must not double as a danger signal (CLAUDE.md → "Extended ramps").
val SpiraDestructive = Error900
// success-900 from the semantic ramp. This was an ad-hoc #337B31 from before the palette
// carried a green of its own.
val SpiraSuccess = Success900

val SpiraBorder = Salt500                  // hairline borders / inputs
val SpiraBorderStrong = Salt600            // stronger outline
