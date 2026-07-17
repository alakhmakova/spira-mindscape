package com.spiramindscape.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The web has a single light palette (no functioning dark mode — see styles.css and
// backlog/android-dark-theme.md), so the app is light-only to mirror it faithfully.
private val SpiraColors = lightColorScheme(
    primary = SpiraTeal,
    onPrimary = SpiraOnPrimary,
    primaryContainer = SpiraTealSoft,
    onPrimaryContainer = SpiraForeground,

    secondary = SpiraSurfaceSunken,
    onSecondary = SpiraForeground,
    secondaryContainer = SpiraTealSoft,
    onSecondaryContainer = SpiraForeground,

    // Orange accent (the web's --brand-orange), used sparingly for highlights/warnings.
    tertiary = SpiraAmber,
    onTertiary = SpiraOnPrimary,
    tertiaryContainer = SpiraAmberSoft,
    onTertiaryContainer = SpiraForeground,

    background = SpiraBackground,
    onBackground = SpiraForeground,
    // Menus/dropdowns and default surfaces are white; the page background stays off-white
    // (Scaffold/TopAppBar set `background` explicitly). surfaceTint is cleared so elevated
    // surfaces (menus, dropdowns) don't get Material's teal tonal-elevation overlay — they must
    // be pure white (see CLAUDE.md UI conventions).
    surface = SpiraSurfaceRaised,
    onSurface = SpiraForeground,
    surfaceTint = Color.Transparent,
    surfaceVariant = SpiraSurfaceSunken,
    onSurfaceVariant = SpiraMutedForeground,

    // Cards read as white "raised" surfaces on the off-white background.
    surfaceContainerLowest = SpiraSurfaceRaised,
    surfaceContainerLow = SpiraSurfaceRaised,
    surfaceContainer = SpiraSurfaceRaised,
    surfaceContainerHigh = SpiraSurfaceRaised,
    surfaceContainerHighest = SpiraSurfaceRaised,

    error = SpiraDestructive,
    onError = SpiraOnPrimary,

    outline = SpiraBorderStrong,
    outlineVariant = SpiraBorder,
)

/** Tokens Material 3 has no slot for. Access via [MaterialTheme] extension [SpiraExtras]. */
data class SpiraExtraColors(
    val success: Color = SpiraSuccess,
    val warning: Color = SpiraAmber,
    val primarySoft: Color = SpiraTealSoft,
    val surfaceRaised: Color = SpiraSurfaceRaised,
    val surfaceSunken: Color = SpiraSurfaceSunken,
    val border: Color = SpiraBorder,
    val borderStrong: Color = SpiraBorderStrong,
    val mutedForeground: Color = SpiraMutedForeground,
)

private val LocalSpiraExtraColors = staticCompositionLocalOf { SpiraExtraColors() }

/** Spira design tokens that don't fit the Material 3 color scheme. */
val MaterialTheme.spiraExtras: SpiraExtraColors
    @Composable get() = LocalSpiraExtraColors.current

@Composable
fun SpiraTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpiraExtraColors provides SpiraExtraColors()) {
        MaterialTheme(
            colorScheme = SpiraColors,
            typography = SpiraTypography,
            shapes = SpiraShapes,
            content = content,
        )
    }
}
