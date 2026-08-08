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
import androidx.compose.ui.draw.clip
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
/**
 * The menu's corner radius. Matched to the web's `rounded-md` (6px) rather than the pill-like 20dp
 * this used to carry: beside the web menu the round version read as a different component.
 */
private val MENU_RADIUS = 8.dp

@Composable
fun SpiraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    // The menu must never sit flush against the screen. Clamping only to 0 let it touch the left
    // edge, which reads as a rendering fault rather than as a floating card.
    val edge = with(density) { 12.dp.roundToPx() }
    val positionProvider = remember(gap, edge) { AnchorBelowEndPositionProvider(gap, edge) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier.widthIn(min = 168.dp),
            shape = RoundedCornerShape(MENU_RADIUS),
            color = SpiraSurfaceRaised,
            border = BorderStroke(1.dp, SpiraBorder),
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(4.dp),
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
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        // The icon column: a fixed-width slot on the right so icons align across all rows even
        // when some rows have none.
        Spacer(Modifier.width(14.dp))
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        } else {
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** A hairline divider between menu groups, inset to match item padding. */
@Composable
fun SpiraMenuDivider() {
    HorizontalDivider(
        color = MaterialTheme.spiraExtras.border,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Positions the menu directly below its trigger, right edges aligned; flips above near the bottom. */
private class AnchorBelowEndPositionProvider(
    private val gapPx: Int,
    private val edgePx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgePx).coerceAtLeast(edgePx)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(edgePx, maxX)
        var y = anchorBounds.bottom + gapPx
        if (y + popupContentSize.height > windowSize.height - edgePx) {
            val above = anchorBounds.top - popupContentSize.height - gapPx
            y = if (above >= edgePx) {
                above
            } else {
                (windowSize.height - popupContentSize.height - edgePx).coerceAtLeast(edgePx)
            }
        }
        return IntOffset(x, y)
    }
}
