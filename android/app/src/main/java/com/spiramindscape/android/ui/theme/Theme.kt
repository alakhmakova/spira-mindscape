package com.spiramindscape.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SpiraOrange,
    onPrimary = Color.White,
    primaryContainer = SpiraSoft,
    onPrimaryContainer = SpiraInk,
    background = Color.White,
    onBackground = SpiraInk,
    surface = Color.White,
    onSurface = SpiraInk,
)

private val DarkColors = darkColorScheme(
    primary = SpiraOrangeDark,
    onPrimary = SpiraInk,
    background = SpiraSurfaceDark,
    onBackground = SpiraOnDark,
    surface = SpiraSurfaceDark,
    onSurface = SpiraOnDark,
)

@Composable
fun SpiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SpiraTypography,
        content = content,
    )
}
