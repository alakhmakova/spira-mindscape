package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.util.formatDeadlineDate
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * The sheet a list's filters and sort open into — the Android twin of the web's `ToolbarSheet`
 * (`src/components/spira/ListToolbar.tsx`), so the phone and the laptop ask the same questions in
 * the same shapes.
 *
 * The shape (owner, 2026-08-17):
 *
 *  - **A Kale head**, white type on teal — the same band the app header carries, so the sheet reads
 *    as part of the app rather than as a white box floating over it.
 *  - The questions in between, each under a small uppercase heading.
 *  - **Reset all** beside **Apply** at the foot. The confirm word is "Apply", never "Done": these
 *    are choices being applied to a list, not a task being finished. Reset is always present — a
 *    control that appears only once you have made a mess is a control nobody finds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiraFilterSheet(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    resetEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // **White**, explicitly — not the page background, which is a warm grey and made the sheet
        // read as a dimmed panel behind its own teal head (owner, 2026-08-17).
        containerColor = Color.White,
        // The head is the sheet's own band, so the drag handle would sit on top of teal as a grey
        // smudge. The X in the head is what closes it, plus the usual drag and back gesture.
        dragHandle = null,
        shape = RoundedCornerShape(topStart = SpiraRadii.lg, topEnd = SpiraRadii.lg),
    ) {
        SpiraFilterSheetContent(title, onDismiss, onReset, resetEnabled, content)
    }
}

/**
 * The sheet's card, without the [ModalBottomSheet] around it.
 *
 * Separate for the same reason `SpiraMenuSurface` is: a modal sheet renders in its **own window**,
 * which the `VisualCheck*` screenshot helper (it draws the activity's decor view) cannot capture —
 * so an open sheet is simply absent from the PNG, and the one check that would catch a column
 * hanging off the side of the screen silently checks nothing.
 */
@Composable
fun SpiraFilterSheetContent(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    resetEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
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

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetFooterButton(
                label = "Reset all",
                onClick = onReset,
                enabled = resetEnabled,
                filled = false,
                modifier = Modifier.weight(1f),
            )
            SheetFooterButton(
                label = "Apply",
                onClick = onDismiss,
                filled = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One of the sheet's two footer buttons: outlined (Reset all) or filled Kale (Apply). */
@Composable
private fun SheetFooterButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(SpiraRadii.md)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.background(Color.White)
                },
            )
            .then(if (filled) Modifier else Modifier.border(2.dp, MaterialTheme.spiraExtras.border, shape))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = addActionTextStyle(),
            fontWeight = FontWeight.SemiBold,
            color = when {
                filled -> MaterialTheme.colorScheme.onPrimary
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.spiraExtras.mutedForeground
            },
        )
    }
}

/** One question inside a [SpiraFilterSheet]: its heading, then its answers. */
@Composable
fun SpiraSheetGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/**
 * A question whose answers are short enough to sit on **one line**, drawn as [SpiraBadge] pills.
 *
 * A pill is that one shape — **a bright 1px outline, a nearly-white fill from the same ramp's 100
 * step, and a near-black word**. It is *not* a solid capsule: filled, a row of them reads as a row
 * of buttons rather than as one family of answers with one chosen. The chosen answer carries
 * [tone]; the rest are `Neutral`, which is what makes the choice legible without a second colour.
 *
 * Stacked as full-width rows, three short words took three lines of a sheet for nothing. The web
 * `SheetPills` draws exactly this (`Pill.tsx`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SpiraSheetPills(
    options: List<SpiraChoice<T>>,
    value: T,
    onChange: (T) -> Unit,
    tone: SpiraBadgeTone = SpiraBadgeTone.Success,
) {
    // A **FlowRow**: four answers ("All / Overdue / Not overdue / No deadline") do not fit across a
    // phone, and a plain Row simply ran the last one off the right edge of the sheet where nothing
    // could reach it. Wrapping costs one line only when there is one line too many.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            SpiraBadge(
                label = option.label,
                tone = if (option.value == value) tone else SpiraBadgeTone.Neutral,
                // An answer that has a mark on the cards it filters carries that same mark here,
                // so "Good idea" and the smiley on the card are visibly the one thing.
                icon = option.icon,
                modifier = Modifier.clickable { onChange(option.value) },
            )
        }
    }
}

/**
 * A **segmented control** — two or three answers sharing one outlined capsule, the chosen one
 * ringed (owner's reference, 2026-08-17: the CSV / PDF pair).
 *
 * This is what a *modifier* looks like — Ascending / Descending — as opposed to a filter value,
 * which is a pill, or a sort key, which is a card. Three different questions, three different
 * shapes, so a glance tells them apart without reading.
 *
 * White throughout: the chosen segment is marked by a **2dp Kale ring and near-black bold type**,
 * not by a fill. A filled segment in a white sheet would out-shout the Apply button.
 */
@Composable
fun <T> SpiraSegmented(
    options: List<SpiraChoice<T>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpiraRadii.md)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.spiraExtras.border, shape),
    ) {
        options.forEach { option ->
            val on = option.value == value
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(SpiraRadii.sm))
                    .then(
                        if (on) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(SpiraRadii.sm))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onChange(option.value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = addActionTextStyle(),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * A question whose answers are **choice cards** — the owner's reference (2026-08-17).
 *
 * The shape, from that reference:
 *
 *  - a card, **not a button**: the label is **left-aligned**, the card is full-width-in-its-column
 *    and taller than a control. An earlier version centred the word in a filled capsule and was
 *    indistinguishable from the sheet's own Reset / Apply buttons two inches below it;
 *  - unchosen: **white** with a hairline border;
 *  - chosen: a **pale teal wash** (Kale-100), a **teal border**, and a **teal triangle folded into
 *    the top-right corner with a white tick in it**. The tick is what makes it read as a choice
 *    that has been made rather than as a button waiting to be pressed.
 */
@Composable
fun <T> SpiraSheetCards(
    options: List<SpiraChoice<T>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    SpiraChoiceCard(
                        label = option.label,
                        selected = option.value == value,
                        onClick = { onChange(option.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd count leaves the last row half-full; the spacer keeps those cards the same
                // width as the rows above rather than letting the last one stretch across.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One card of a [SpiraSheetCards] grid. Public so a single card can be used on its own. */
@Composable
fun SpiraChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpiraRadii.md)
    val teal = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) CARD_SELECTED_WASH else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) teal else MaterialTheme.spiraExtras.border, shape)
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = addActionTextStyle(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.CenterStart)
                // Room on the right for the corner tick, so a long label never runs under it.
                .padding(start = 14.dp, end = 30.dp),
        )
        if (selected) {
            // The folded corner: a right triangle in the card's top-right, with the tick centred
            // in its thick half. Drawn rather than composed, because a triangle is not a shape
            // Compose hands out.
            Box(Modifier.align(Alignment.TopEnd).size(CORNER)) {
                Canvas(Modifier.matchParentSize()) {
                    drawPath(
                        Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, 0f)
                            close()
                        },
                        color = teal,
                    )
                }
                Icon(
                    SpiraIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(11.dp),
                )
            }
        }
    }
}

/** The chosen card's wash — Kale-100, the palette's palest teal. */
private val CARD_SELECTED_WASH = Color(0xFFF3FAFB)

/** The folded corner's side. */
private val CORNER = 26.dp

/**
 * A **date range** question: two date buttons side by side, From and To, each opening the app's one
 * date picker. The web draws exactly this pair (`Targets.tsx` / `AppShell.tsx` → two
 * `DeadlinePopover`s in a two-column grid).
 *
 * Both ends are optional and independent, so "everything before March" is one tap rather than a
 * date the user has to invent for the other end. An end that is set can be taken off again from
 * the picker's own **Clear**, which is why there is no third control here.
 *
 * Values are ISO instants, or "" for an open end — the same spelling the range filters use.
 */
@Composable
fun SpiraSheetDateRange(
    from: String,
    to: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateRangeEnd("From", from, onFromChange, Modifier.weight(1f))
        DateRangeEnd("To", to, onToChange, Modifier.weight(1f))
    }
}

/** One end of a [SpiraSheetDateRange]: the calendar glyph, then the date or its placeholder. */
@Composable
private fun DateRangeEnd(
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(SpiraRadii.md)
    val set = value.isNotBlank()
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (set) 1.5.dp else 1.dp, if (set) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.border, shape)
            .clickable { open = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            SpiraIcons.Calendar,
            contentDescription = null,
            tint = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.size(15.dp),
        )
        Text(
            if (set) formatDeadlineDate(value) else placeholder,
            style = addActionTextStyle(),
            fontWeight = if (set) FontWeight.SemiBold else FontWeight.Medium,
            color = if (set) MaterialTheme.colorScheme.onSurface else MaterialTheme.spiraExtras.mutedForeground,
            maxLines = 1,
        )
    }
    if (open) {
        DeadlinePickerDialog(
            value = value.ifBlank { null },
            onChange = { onChange(it ?: "") },
            onDismiss = { open = false },
        )
    }
}

/**
 * The **confidence** question: the numbers 1 to 10 as a two-row grid, one of which can be chosen.
 * The web draws the same grid, five to a row (`AppShell.tsx`).
 *
 * Tapping the chosen number again clears it back to "any", which is why there is no "All" cell:
 * ten answers plus an eleventh escape hatch would not fit across a phone, and the number already
 * shows whether it is on.
 *
 * [value] is 1..10, or 0 for "any".
 */
@Composable
fun SpiraSheetConfidence(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1..5, 6..10).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n ->
                    ConfidenceCell(n, n == value, { onChange(if (n == value) 0 else n) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** One number of a [SpiraSheetConfidence] grid. */
@Composable
private fun ConfidenceCell(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpiraRadii.sm)
    val teal = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) teal else Color.White)
            .then(if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.spiraExtras.border, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = addActionTextStyle(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A question whose answers are rows.
 *
 * [aux] is the "modifier" look (Ascending / Descending): chosen → pale grey with Kale text, so a
 * direction cannot be mistaken for a second sort key. A real value chosen is a filled teal row,
 * exactly as in the dropdown menus.
 */
@Composable
fun <T> SpiraSheetChoices(
    options: List<SpiraChoice<T>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    aux: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            SpiraMenuChoice(
                label = option.label,
                onClick = { onChange(option.value) },
                selected = option.value == value,
                variant = if (aux) MenuChoiceVariant.Aux else MenuChoiceVariant.Primary,
            )
        }
    }
}

/**
 * The **Filter & Sort** opener that sits on the same line as a page's own heading: **the filter
 * glyph alone**, in Kale, with no border, no fill, no word and no chevron (owner, 2026-08-17).
 *
 * A page heading is already a full line of type; a second worded control beside it made the row
 * read as two headings competing. So this one is bare — and because it is bare, it carries the
 * **dot**: with no word and no bracketed count, the dot is the only thing that can say a filter is
 * on. (The rule, from CLAUDE.md: dot on a lone glyph, "(2)" on a worded trigger, never both.)
 */
@Composable
fun SpiraFilterSortTrigger(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Filter and sort",
) {
    Box(
        modifier
            .clip(RoundedCornerShape(SpiraRadii.sm))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Icon(
            SpiraIcons.Filter,
            contentDescription = contentDescription,
            tint = Kale500,
            modifier = Modifier.size(20.dp),
        )
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}
