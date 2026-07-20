package com.spiramindscape.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.spiramindscape.android.ui.theme.SpiraBorder
import com.spiramindscape.android.ui.theme.SpiraSurfaceRaised
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * The one and only dropdown/menu surface for the whole app (CLAUDE.md → "Dropdowns & menus").
 * Built as a raw [Popup] (NOT Material's `DropdownMenu`, whose tonal-elevation surface and tight
 * corners read as a flat grey box) so it matches the reference exactly:
 *
 *  - **Pure white** background — never tinted/elevated grey.
 *  - **Width fits its content** ([IntrinsicSize.Max]); it never stretches full-width.
 *  - **Generously rounded** corners (20dp), a **hairline border**, and a soft **shadow** — reads
 *    as a floating card, not a rectangle.
 *  - Items are [SpiraMenuItem]s: **label on the left, icon in a right-aligned column** that lines
 *    up across every row (the label cell flexes, the icon sits flush right). Destructive items
 *    are red.
 *
 * Anchored just below its trigger, right-edge-aligned to it (the usual place for an app-bar or
 * kebab menu), flipping above if it would run off the bottom. Dismisses on outside tap / back.
 */
@Composable
fun SpiraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    val positionProvider = remember(gap) { AnchorBelowEndPositionProvider(gap) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier.widthIn(min = 180.dp),
            shape = RoundedCornerShape(20.dp),
            color = SpiraSurfaceRaised,
            border = BorderStroke(1.dp, SpiraBorder),
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

/**
 * One menu row: [label] on the left, an optional [icon] in the shared right-hand column. Set
 * [destructive] for a red (delete-style) item. [selected] shows a check in the icon slot when no
 * explicit [icon] is given (for pick-one menus like sort/filter).
 */
@Composable
fun SpiraMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // Icons read grey (muted) throughout every menu; only the destructive (Delete) icon stays red.
    val iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.spiraExtras.mutedForeground
    val trailing = icon ?: if (selected) com.spiramindscape.android.ui.icons.SpiraIcons.Check else null
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        // The icon column: a fixed-width slot on the right so icons align across all rows even
        // when some rows have none.
        Spacer(Modifier.width(20.dp))
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}

/** A hairline divider between menu groups, inset to match item padding. */
@Composable
fun SpiraMenuDivider() {
    HorizontalDivider(
        color = MaterialTheme.spiraExtras.border,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Positions the menu directly below its trigger, right edges aligned; flips above near the bottom. */
private class AnchorBelowEndPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var x = anchorBounds.right - popupContentSize.width
        if (x + popupContentSize.width > windowSize.width) x = windowSize.width - popupContentSize.width
        if (x < 0) x = 0
        var y = anchorBounds.bottom + gapPx
        if (y + popupContentSize.height > windowSize.height) {
            val above = anchorBounds.top - popupContentSize.height - gapPx
            y = if (above >= 0) above else (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
