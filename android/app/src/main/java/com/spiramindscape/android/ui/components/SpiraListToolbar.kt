package com.spiramindscape.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * One choosable value in a toolbar menu: the value, the word shown for it, and — where the answer
 * has a mark of its own on the cards it filters — that mark.
 *
 * [icon] is drawn by [SpiraSheetPills] only; it exists so the Options lean filter can carry the
 * very smileys its cards wear, rather than describing them in words beside them.
 */
data class SpiraChoice<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * The list chrome shared by the Options, Resources and Targets pages: a search field on the first
 * line, then whichever of sort / filter / an add action the page has on the second.
 *
 * Two lines rather than one, because a search field plus two **worded** triggers plus an add button
 * cannot fit across a phone. Both triggers carry a word now — an icon on its own said neither what
 * it did nor what was chosen.
 */
@Composable
fun SpiraListToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /**
     * Sits on the **same line** as the search field, to its right. For a page whose only other
     * control is one small action (Options and its Reorder toggle), a second line for it alone is
     * wasted height.
     */
    beside: (@Composable () -> Unit)? = null,
    /**
     * The **first** control on the second line. It is the filter on every page — the owner asked
     * for filter-then-anything-else (2026-08-17), so this slot is named for its position and not
     * for what a given page happens to put in it.
     */
    filter: (@Composable () -> Unit)? = null,
    /** The **second** control: the sort trigger, or Options' Reorder toggle. */
    sort: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpiraSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
            beside?.invoke()
        }
        if (sort != null || filter != null || action != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filter?.invoke()
                sort?.invoke()
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
        }
    }
}

/**
 * The shape both toolbar menus open from: a **leading glyph and a word in Kale**.
 * No pill, no border, no fill — it is a piece of text you can open, not a button, and the leading
 * glyph is never bordered even when it stands alone.
 *
 * It is teal in **every** state, including "nothing is chosen". The trigger used to be a hairline
 * pill that went from near-black to teal once a filter was on, which made an untouched toolbar the
 * heaviest row on the page and turned two quiet controls into two competing buttons.
 */
@Composable
fun SpiraToolbarTrigger(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** The mark before the word — the filter glyph, or the current sort direction. Never bordered. */
    leadingIcon: ImageVector? = null,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(SpiraRadii.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        // **No chevron** (owner, 2026-08-17): a glyph and its word already say the thing opens,
        // and the caret was a third mark in a control that has two. The content description moves
        // onto the word, which is now the last thing in the row.
        Text(
            label,
            style = addActionTextStyle(),
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.semantics { contentDescription?.let { this.contentDescription = it } },
        )
    }
}

/**
 * The sort trigger and its menu.
 *
 * The trigger carries the **current sort direction** as its leading glyph (so "which order is
 * chosen" is shown), then the **active key** as its word ("Deadline"). The menu is two stacked
 * groups, each under its heading with a hairline beneath: what to order by, then which way. The
 * direction rows are `Aux` — chosen they take the pale-grey look, so they read as a modifier rather
 * than as two more keys — and carry the ascending/descending glyphs.
 */
@Composable
fun <T> SpiraSortTrigger(
    options: List<SpiraChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    ascending: Boolean,
    onAscendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Sort",
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label ?: "Sort"
    Column(modifier) {
        SpiraToolbarTrigger(
            label = current,
            onClick = { open = true },
            contentDescription = contentDescription,
            leadingIcon = if (ascending) SpiraIcons.SortAscending else SpiraIcons.SortDescending,
        )
        if (open) {
            SpiraFilterSheet(
                title = "Sort",
                onDismiss = { open = false },
                onReset = { onSelect(options.first().value); onAscendingChange(false) },
            ) {
                SpiraSheetGroup("Direction") {
                    SpiraSegmented(
                        options = SORT_DIRECTIONS,
                        value = ascending,
                        onChange = onAscendingChange,
                    )
                }
                SpiraSheetGroup("Sort by") {
                    SpiraSheetCards(options = options, value = selected, onChange = onSelect)
                }
            }
        }
    }
}

/** Ascending / descending, as the two segments of the sort sheet's modifier control. */
private val SORT_DIRECTIONS = listOf(
    SpiraChoice(true, "Ascending"),
    SpiraChoice(false, "Descending"),
)

/**
 * The filter trigger and the **sheet** it opens.
 *
 * [count] is how many filters are narrowing the list; the trigger shows it **in brackets** after
 * the word — "Filter (2)" — and nothing at all at zero. A number says how much is hidden in a way
 * that a colour change never could, and it doesn't need the trigger to change appearance.
 *
 * **A sheet, not a dropdown** (owner, 2026-08-17). Every filter and sort on the phone now opens the
 * same drawer the All-goals page uses — Kale head, questions as pills, Reset all beside Apply — so
 * the pattern is learned once instead of three times. A menu of columns could not fit three
 * questions across a phone anyway; that is what this replaces.
 */
@Composable
fun SpiraFilterTrigger(
    count: Int,
    modifier: Modifier = Modifier,
    label: String = "Filter",
    contentDescription: String = "Filter",
    title: String = "Filter",
    leadingIcon: ImageVector = SpiraIcons.Filter,
    onReset: () -> Unit = {},
    sheet: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        SpiraToolbarTrigger(
            label = if (count > 0) "$label ($count)" else label,
            onClick = { open = true },
            contentDescription = contentDescription,
            leadingIcon = leadingIcon,
        )
        if (open) {
            SpiraFilterSheet(
                title = title,
                onDismiss = { open = false },
                onReset = onReset,
                resetEnabled = count > 0,
                content = sheet,
            )
        }
    }
}

/**
 * The app's "add one of these" button: a filled Kale button carrying the word.
 *
 * Deliberately **not** a circled plus at the top of a list — a round add button belongs at the
 * bottom-right of the screen (the floating buttons on Reality and Resources) and nowhere else.
 */
@Composable
fun SpiraAddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(SpiraRadii.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            // A disabled Reorder keeps the teal, faded — Material's grey-on-grey default read as a
            // different control rather than as the same one, unavailable.
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)),
    ) {
        Text(label, style = addActionTextStyle(), fontWeight = FontWeight.Medium)
    }
}

/**
 * The label style for a word sitting beside a glyph.
 *
 * The type scale centres a word inside its **line box**, and that box reserves descender room a
 * word like "Add task" never uses — so the label rides visibly high above the icon next to it.
 * Trimming the leading centres the word itself instead. Every icon-and-label pair should use this.
 */
@Composable
fun addActionTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/** The gap between a glyph and its label, so every add-action row spaces the pair the same way. */
val AddActionIconGap = 8.dp

/** Sugar for the row shape: a circled plus, the gap, the label. */
@Composable
fun SpiraCirclePlusLabel(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(SpiraRadii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SpiraIcons.CirclePlus, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AddActionIconGap))
        Text(label, style = addActionTextStyle(), fontWeight = FontWeight.SemiBold, color = tint)
    }
}
