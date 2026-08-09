package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Kale600

/**
 * The **All goals** header (from the design mockup): a Kale-600 bar — the same teal the
 * assistant panel is set on, so the app's two dark surfaces are one colour with the white "spira"
 * wordmark on the left and, on the right, Search / AI assistant / Profile. Icons are white for
 * contrast on the teal surface; the avatar sits in a translucent circle.
 *
 * Inside a goal the chrome is different — see `GoalWorkspaceTopBar` — but the AI assistant is the
 * same sparkle mark on both, so the assistant is recognisably one thing across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiraTopBar(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onAssistant: () -> Unit,
    onProfile: () -> Unit,
    onBrandClick: () -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(SpiraIcons.Menu, contentDescription = "Menu", modifier = Modifier.size(22.dp))
            }
        },
        title = {
            // The wordmark is lowercase "spira" in the body sans, tight-tracked — the same mark
            // the sign-in screen and the web header carry. It was previously set as spaced-out
            // uppercase here, which read as a different brand from one screen to the next.
            Text(
                "spira",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 26.sp,
                    // Leading equal to the size, so the line box is the glyphs themselves and the
                    // wordmark centres against the icons instead of riding on the font's metrics.
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clickable(onClick = onBrandClick),
            )
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(SpiraIcons.Search, contentDescription = "Search", modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onAssistant) {
                Icon(SpiraIcons.NavAi, contentDescription = "AI assistant", modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onProfile) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SpiraIcons.UserRound, contentDescription = "Profile", modifier = Modifier.size(18.dp))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Kale600,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
