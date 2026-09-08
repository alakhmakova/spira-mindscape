package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import com.spiramindscape.android.ui.theme.Salt300
import com.spiramindscape.android.ui.theme.Salt1000

/**
 * The two heads a Spira sheet wears — see CLAUDE.md → Sheets → "two head types" for the full
 * rule. [SheetHeadTone.Primary] (default) is the **Kale band**: the shape for a sheet that IS
 * the primary content — a create/edit form, Filter & Sort, the coach itself. [SheetHeadTone.Auxiliary]
 * is a **plain white band with a hairline underneath**, for a sheet that sits on top of primary
 * content without being that content itself (owner, 2026-09-03: AI providers "это не основной
 * контент" — "is not primary content" — so it must read differently from one). Never pick
 * `Auxiliary` for a sheet that creates, edits, or IS the thing the user came here to work on.
 *
 * The Android twin of the web's `SheetHead` (`src/components/spira/SheetHead.tsx`), and the
 * measurements below are that component's, so a sheet looks the same on the phone and on the
 * laptop:
 *
 * | | `Primary` | `Auxiliary` |
 * |---|---|---|
 * | Fill | `colorScheme.primary` (Kale-500 `#0A8080`) | white |
 * | Bottom edge | none | `1dp` hairline, `Salt300 #F4F4F3` |
 * | Title | `titleMedium`, bold, `onPrimary` | `titleMedium`, bold, `Salt1000 #222525` |
 * | Close | an 18dp X in `onPrimary` | an 18dp X in `Salt1000` at 40% alpha |
 * | Padding | 20dp start, 12dp end, 14dp top and bottom | same |
 *
 * Every `Primary` sheet gets it, form sheets included: "New goal" used to wear a white head with
 * Material's grey drag handle above it while "Filter & Sort" wore the band, so the two read as
 * parts of two different apps (owner, 2026-08-22).
 *
 * [actions] takes anything that belongs to the sheet as a whole and sits **before** the X — the
 * filter panel's padlock is the only one today, and it is `Primary`.
 */
enum class SheetHeadTone { Primary, Auxiliary }

@Composable
fun SpiraSheetHead(
    title: String,
    onDismiss: () -> Unit,
    tone: SheetHeadTone = SheetHeadTone.Primary,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val auxiliary = tone == SheetHeadTone.Auxiliary
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (auxiliary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (auxiliary) Salt1000 else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            actions()
            Icon(
                SpiraIcons.X,
                contentDescription = "Close",
                tint = if (auxiliary) Salt1000.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
        if (auxiliary) {
            HorizontalDivider(thickness = 1.dp, color = Salt300)
        }
    }
}
