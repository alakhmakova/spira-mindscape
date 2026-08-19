package com.spiramindscape.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
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
 *  - **Width fits its content** ([IntrinsicSize.Max]) with no floor of any kind; it never
 *    stretches full-width and never sits in a box wider than its words.
 *  - **Barely rounded** corners (8dp, the web's `rounded-md`), a **hairline border**, and a soft
 *    hand-drawn **shadow** — reads as a floating card, not a rectangle.
 *  - Items are [SpiraMenuItem]s: **icon on the left, then the label**, with a fixed icon slot so
 *    every label starts at the same x. Destructive items are red; the selected one is a filled
 *    teal row with white content.
 *
 * Anchored just below its trigger, right-edge-aligned to it (the usual place for an app-bar or
 * kebab menu), flipping above if it would run off the bottom. Dismisses on outside tap / back.
 */
/**
 * The menu's corner radius. Matched to the web's `rounded-md` (6px) rather than the pill-like 20dp
 * this used to carry: beside the web menu the round version read as a different component.
 */
private val MENU_RADIUS = 8.dp

/**
 * The menu's drop shadow — **soft**, and nothing like Material's default.
 *
 * `Surface(shadowElevation = 12.dp)` draws Android's elevation shadow at full strength: a dark,
 * tight band that hugs the corners and reads as a grey outline around the card rather than as
 * depth. Beside the web's `shadow-lg` (two very light blurs at 10% black) it looked like a
 * different component, and on the pale page it was the loudest thing on screen.
 *
 * So the shadow is drawn by hand instead: a shorter throw, and both the ambient and the spot pass
 * given their own low-alpha ink so the framework can't paint them at full black.
 */
private val MENU_SHADOW = 6.dp
private val MENU_SHADOW_AMBIENT = Color(0x14222525)
private val MENU_SHADOW_SPOT = Color(0x1F222525)

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
        SpiraMenuSurface(modifier, content)
    }
}

/**
 * The menu's card, without the [Popup] around it.
 *
 * Separate because a popup renders in its **own window**, which the `VisualCheck*` screenshot
 * helper (it draws the activity's decor view) cannot capture — so an open menu is invisible to the
 * one check that would catch a three-column menu running off the side of the screen. A test can
 * render this directly and look at the result.
 */
@Composable
fun SpiraMenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            // **No width floor at all** — the menu is exactly as wide as its widest row
            // (`IntrinsicSize.Max` below) plus 4dp of container padding. A floor is what made every
            // narrow kebab as wide as the sort menu, with a band of empty space beside two short
            // words; 168dp was the first version of that mistake and 120dp was the same mistake,
            // smaller. The web does the same with `w-max` (`ui/dropdown-menu.tsx`).
            .shadow(
                elevation = MENU_SHADOW,
                shape = RoundedCornerShape(MENU_RADIUS),
                ambientColor = MENU_SHADOW_AMBIENT,
                spotColor = MENU_SHADOW_SPOT,
            ),
        shape = RoundedCornerShape(MENU_RADIUS),
        color = SpiraSurfaceRaised,
        border = BorderStroke(1.dp, SpiraBorder),
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .padding(4.dp),
            content = content,
        )
    }
}

/**
 * One menu row: an optional [icon] **on the left**, then the [label].
 *
 * The icon leads the row (it used to sit in a right-hand column); an icon read left-to-right with
 * its word is what the reference menu does, and it stops a row of labels drifting away from their
 * marks. Rows with no icon keep the same indent, so the labels still line up.
 *
 * [destructive] paints the row red (delete-style). [selected] fills it with the brand teal and
 * turns its content white — the reference's active row — instead of the check mark it used to
 * show; a filled row is legible at a glance in a way a small tick on the far side is not.
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
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Icons read grey (muted) through a normal menu; a destructive one is red, and a selected row
    // carries white on teal like its label.
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.spiraExtras.mutedForeground
    }
    val leading = icon ?: if (selected) com.spiramindscape.android.ui.icons.SpiraIcons.Check else null
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A fixed slot, filled or not, so every label starts at the same x.
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
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

/**
 * Lays a menu out as **columns side by side** — one column per question.
 *
 * The owner asked only for the vertical divider between columns to go, not for the columns to be
 * stacked into one tall list (2026-08-15). So the horizontal layout stays; each column is set
 * apart by its own heading's hairline (see [SpiraMenuGroup]), not by a rule between columns.
 * `IntrinsicSize.Min` keeps every column the height of the tallest so the row reads level.
 */
@Composable
fun SpiraMenuColumns(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), content = content)
}

/**
 * One column of a [SpiraMenuColumns] menu: its question, a **hairline under the heading**, then
 * that question's answers below it.
 */
@Composable
fun SpiraMenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(IntrinsicSize.Max)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.spiraExtras.border,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
        )
        content()
    }
}

/** Which look a chosen [SpiraMenuChoice] takes. */
enum class MenuChoiceVariant {
    /** A real filter value — chosen = a filled teal row (the list-menu selection). */
    Primary,

    /** A modifier (Ascending / Descending) — chosen = the pale-grey "hover" look, so it reads as a
     *  modifier rather than as the filter itself. */
    Aux,
}

/** The pale grey a hovered/aux-selected row takes — neutral-150, matching the web's `#F6F6F6`. */
private val MENU_AUX_SELECTED_BG = Color(0xFFF6F6F6)

/**
 * A [SpiraMenuItem] for a grouped menu, with an optional leading [icon] (the direction glyphs on
 * Ascending / Descending carry one).
 *
 * [variant] `Primary` (default) is a real filter value: chosen → a **filled teal row**. `Aux` is a
 * modifier: chosen → the **pale-grey look with Kale text**, so a direction reads as a modifier and
 * not as the filter itself.
 */
@Composable
fun SpiraMenuChoice(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    variant: MenuChoiceVariant = MenuChoiceVariant.Primary,
    icon: ImageVector? = null,
) {
    val primarySelected = selected && variant == MenuChoiceVariant.Primary
    val auxSelected = selected && variant == MenuChoiceVariant.Aux
    val rowBg = when {
        primarySelected -> MaterialTheme.colorScheme.primary
        auxSelected -> MENU_AUX_SELECTED_BG
        else -> Color.Unspecified
    }
    val contentColor = when {
        primarySelected -> MaterialTheme.colorScheme.onPrimary
        auxSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(if (rowBg != Color.Unspecified) Modifier.background(rowBg) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = contentColor,
        )
    }
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
