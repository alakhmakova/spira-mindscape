package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.SpiraButton
import com.spiramindscape.android.ui.components.SpiraButtonVariant
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava100
import com.spiramindscape.android.ui.theme.Guava600
import com.spiramindscape.android.ui.theme.Kale100
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.spiraExtras
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The Options card action menu — a white bottom sheet (mirrors the design mockup). Opened by the
 * card's kebab. Lists "Make active" (or "Remove active status" when it already is), then a
 * destructive "Delete option", each an icon-chip + label + sub-label row, plus a Cancel button.
 * No text input here, so it's clear of the ModalBottomSheet typing hazard (BUG-009).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionMenuSheet(
    optionNumber: Int,
    isActive: Boolean,
    onMakeActive: () -> Unit,
    onRemoveActive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Option $optionNumber",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            HorizontalDivider(color = MaterialTheme.spiraExtras.border)
            Spacer(Modifier.height(8.dp))

            if (isActive) {
                OptionMenuItem(
                    icon = SpiraIcons.StarOff,
                    label = "Remove active status",
                    sub = "Keep the option, stop pursuing it",
                    onClick = onRemoveActive,
                )
            } else {
                OptionMenuItem(
                    icon = SpiraIcons.Star,
                    label = "Make active",
                    sub = "Pursue this option for the goal",
                    onClick = onMakeActive,
                )
            }
            OptionMenuItem(
                icon = SpiraIcons.Trash,
                label = "Delete option",
                sub = "Remove it from this goal",
                danger = true,
                onClick = onDelete,
            )

            Spacer(Modifier.height(8.dp))
            SpiraButton(
                "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = SpiraButtonVariant.Ghost,
            )
        }
    }
}

/** One action row in [OptionMenuSheet]: a rounded icon chip, then a bold label over a muted sub. */
@Composable
private fun OptionMenuItem(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val chipBg = if (danger) Guava100 else Kale100
    val chipTint = if (danger) Guava600 else Kale600
    val labelColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(chipBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = chipTint, modifier = Modifier.size(19.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = labelColor)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.spiraExtras.mutedForeground)
        }
    }
}
