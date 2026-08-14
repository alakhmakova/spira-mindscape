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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras

/** One choosable value in a toolbar menu: the value, and the word shown for it. */
data class SpiraChoice<T>(val value: T, val label: String)

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
    sort: (@Composable () -> Unit)? = null,
    filter: (@Composable () -> Unit)? = null,
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
                sort?.invoke()
                filter?.invoke()
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
        }
    }
}

/**
 * The shape both toolbar menus open from: a **word in Kale, then a small solid chevron**. No pill,
 * no border, no fill — it is a piece of text you can open, not a button.
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
        Text(label, style = addActionTextStyle(), fontWeight = FontWeight.SemiBold, color = tint)
        Icon(
            SpiraIcons.CaretDown,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The sort trigger and its menu.
 *
 * The trigger's word is the **active key** ("Deadline"), and the menu answers the two questions
 * side by side: what to order by in the left column, which way in the right, with a hairline
 * between them. Stacked, the direction rows sat at the bottom of a list of keys and read as two
 * more keys.
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
        )
        SpiraDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SpiraMenuColumns {
                SpiraMenuGroup("Sort by") {
                    options.forEach { choice ->
                        SpiraMenuChoice(
                            label = choice.label,
                            selected = choice.value == selected,
                            onClick = { onSelect(choice.value); open = false },
                        )
                    }
                }
                SpiraMenuColumnDivider()
                SpiraMenuGroup("Direction") {
                    SpiraMenuChoice(
                        label = "Ascending",
                        selected = ascending,
                        onClick = { onAscendingChange(true); open = false },
                    )
                    SpiraMenuChoice(
                        label = "Descending",
                        selected = !ascending,
                        onClick = { onAscendingChange(false); open = false },
                    )
                }
            }
        }
    }
}

/**
 * The filter trigger and its menu.
 *
 * [count] is how many filters are narrowing the list; the trigger shows it **in brackets** after
 * the word — "Filter (2)" — and nothing at all at zero. A number says how much is hidden in a way
 * that a colour change never could, and it doesn't need the trigger to change appearance.
 *
 * [menu] is a slot so a page can put one group of choices in it, or several columns separated by
 * [SpiraMenuColumnDivider].
 */
@Composable
fun SpiraFilterTrigger(
    count: Int,
    modifier: Modifier = Modifier,
    label: String = "Filter",
    contentDescription: String = "Filter",
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        SpiraToolbarTrigger(
            label = if (count > 0) "$label ($count)" else label,
            onClick = { open = true },
            contentDescription = contentDescription,
        )
        SpiraDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu { open = false }
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
fun SpiraAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(SpiraRadii.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
