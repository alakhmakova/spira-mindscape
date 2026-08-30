package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * **The app's month grid — with ISO week numbers** (owner, 2026-08-29: "на андроид тоже нужен
 * календарь с номерами недель").
 *
 * It is drawn by hand rather than taken from Material, and that is not a preference: Material 3's
 * `DatePicker` has **no week-number support at all**, so the only way to have the column is to own
 * the grid. Which is also what CLAUDE.md → Components and chrome → 1 asks for — a bare Material
 * `DatePicker` in a bare `DatePickerDialog` was a raw platform default sitting in the middle of a
 * Spira form.
 *
 * The web draws the same grid (`react-day-picker` with `showWeekNumber` + `ISOWeek` in
 * `src/components/ui/calendar.tsx`), so the two surfaces agree down to the numbers in the left
 * column: **ISO-8601** weeks, Monday first, and the week that contains the year's first Thursday
 * is week 1. `WeekFields.ISO` is that definition; `WeekFields.of(Locale)` is NOT — on a US locale
 * it starts weeks on Sunday and counts differently, which would put a different number beside the
 * same row on the two surfaces.
 *
 * Six rows always, whatever the month needs, so the card does not change height as you page
 * through — the same reason the web passes `fixedWeeks`.
 */
@Composable
fun SpiraMonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var yearMenu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        // ── Month, year, and the way through them ──────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(SpiraIcons.ChevronLeft, "Previous month") {
                onMonthChange(month.minusMonths(1))
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box {
                    Row(
                        Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { yearMenu = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            month.year.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                        Icon(
                            SpiraIcons.ChevronDown,
                            contentDescription = "Choose a year",
                            tint = MaterialTheme.spiraExtras.mutedForeground,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    // The web offers the same span — two years back, twenty forward.
                    SpiraDropdownMenu(yearMenu, { yearMenu = false }) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            for (y in today.year - 2 until today.year + 18) {
                                SpiraMenuItem(
                                    label = y.toString(),
                                    selected = y == month.year,
                                    onClick = {
                                        yearMenu = false
                                        onMonthChange(month.withYear(y))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            NavArrow(SpiraIcons.ChevronRight, "Next month") {
                onMonthChange(month.plusMonths(1))
            }
        }

        // ── The weekday header, with `W` over the week-number column ───────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GridLabel("W", Modifier.width(WEEK_COLUMN))
            for (d in DayOfWeek.entries) {
                GridLabel(
                    d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                    Modifier.weight(1f),
                )
            }
        }

        // ── Six weeks, starting on the Monday of the week the 1st falls in ─────────────────
        val firstCell = month.atDay(1).with(WeekFields.ISO.dayOfWeek(), 1)
        for (row in 0 until 6) {
            val weekStart = firstCell.plusWeeks(row.toLong())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val weekNo = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
                Text(
                    weekNo.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.spiraExtras.mutedForeground.copy(alpha = 0.55f),
                    // Named, not just numbered: a bare "34" beside a row is nothing to a screen
                    // reader, and it is also how a test tells the week number apart from the two
                    // day cells that carry the same digits (July 31 and August 31, in a month
                    // whose rows are 31–36).
                    modifier = Modifier
                        .width(WEEK_COLUMN)
                        .semantics { contentDescription = "Week $weekNo" },
                )
                for (col in 0 until 7) {
                    val date = weekStart.plusDays(col.toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isSelected = date == selected,
                        isToday = date == today,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The week-number column. Narrow on purpose: it is a reference, not a seventh weekday. */
private val WEEK_COLUMN = 28.dp

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun GridLabel(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.spiraExtras.mutedForeground.copy(alpha = 0.55f),
        modifier = modifier.padding(vertical = 6.dp),
    )
}

/**
 * One day.
 *
 * The three states are the web's, so a date looks the same on both surfaces: **selected** is a
 * filled Kale square with white type, **today** is the pale brand tint, and a day from the
 * neighbouring month is the same type at muted ink — shown rather than blanked, because an empty
 * corner reads as a broken grid.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val fill = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .semantics { contentDescription = date.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.spiraExtras.mutedForeground.copy(alpha = 0.6f)
            },
        )
    }
}
