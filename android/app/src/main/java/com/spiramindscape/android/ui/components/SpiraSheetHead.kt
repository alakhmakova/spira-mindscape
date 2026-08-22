package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons

/**
 * The one head a Spira sheet wears — a **Kale band** with the title in white and an X on the
 * right. The Android twin of the web's `SheetHead` (`src/components/spira/SheetHead.tsx`), and
 * the measurements below are that component's, so a sheet looks the same on the phone and on the
 * laptop:
 *
 * | | Value |
 * |---|---|
 * | Fill | `colorScheme.primary` (Kale-500 `#0A8080`) — 20dp start, 12dp end, 14dp top and bottom |
 * | Title | `titleMedium`, bold, `onPrimary`, sentence case, left |
 * | Close | an 18dp X in `onPrimary`, right, on a circular ripple |
 *
 * Every sheet gets it, form sheets included: "New goal" used to wear a white head with Material's
 * grey drag handle above it while "Filter & Sort" wore the band, so the two read as parts of two
 * different apps (owner, 2026-08-22).
 *
 * [actions] takes anything that belongs to the sheet as a whole and sits **before** the X — the
 * filter panel's padlock is the only one today.
 */
@Composable
fun SpiraSheetHead(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
        )
        actions()
        Icon(
            SpiraIcons.X,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(6.dp)
                .size(18.dp),
        )
    }
}
