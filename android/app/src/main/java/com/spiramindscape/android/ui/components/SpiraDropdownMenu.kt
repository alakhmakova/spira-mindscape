package com.spiramindscape.android.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spiramindscape.android.ui.theme.SpiraSurfaceRaised

/**
 * Dropdown menu with a guaranteed pure-white background (CLAUDE.md UI conventions: menus and
 * overlays are white, no tint). Material's menu picks its container from theme surface tokens
 * and adds tonal elevation, which has produced grey/greenish menus twice — so this wrapper
 * overrides every candidate token to white and kills the tint, making grey impossible.
 */
@Composable
fun SpiraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = SpiraSurfaceRaised,
            surfaceContainer = SpiraSurfaceRaised,
            surfaceContainerLow = SpiraSurfaceRaised,
            surfaceContainerLowest = SpiraSurfaceRaised,
            surfaceContainerHigh = SpiraSurfaceRaised,
            surfaceContainerHighest = SpiraSurfaceRaised,
            surfaceTint = Color.Transparent,
        ),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}
