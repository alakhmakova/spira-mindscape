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
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.icons.SpiraIcons

/**
 * The shared app header (from the design mockup): a dark teal bar with the white "SPIRA" wordmark
 * on the left and, on the right, Search / AI assistant / Profile. Used on every screen so the
 * chrome is consistent. Icons are white for contrast on the teal surface; the avatar sits in a
 * translucent circle.
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
            Text(
                "SPIRA",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
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
                Icon(SpiraIcons.Brain, contentDescription = "AI assistant", modifier = Modifier.size(22.dp))
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
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
