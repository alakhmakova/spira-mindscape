package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Error900
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.confidenceColor
import com.spiramindscape.android.ui.theme.spiraExtras
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Themed single/multi-line text input. */
@Composable
fun SpiraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
    )
}

/** Row of mutually-exclusive type chips (used to pick a target/resource kind). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeSelector(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            androidx.compose.material3.FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

enum class SpiraButtonVariant { Primary, Ghost, Destructive }

/** Themed button in three variants (primary / ghost outline / destructive). */
@Composable
fun SpiraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SpiraButtonVariant = SpiraButtonVariant.Primary,
    enabled: Boolean = true,
) {
    // Web buttons are `rounded-md` (--radius-md, 6px) — SpiraShapes.small, not .medium.
    // **One line, always.** A button whose label wraps is a button that has been given too little
    // room, and it should look wrong rather than quietly grow a second line — "Set deadline" did
    // exactly that in the date picker before its dialog was widened (owner, 2026-08-29). The
    // ellipsis makes a genuinely over-long label visible instead of clipped.
    val label: @Composable () -> Unit = {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (variant) {
        SpiraButtonVariant.Primary ->
            Button(onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small) { label() }
        SpiraButtonVariant.Ghost ->
            OutlinedButton(onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small) { label() }
        SpiraButtonVariant.Destructive ->
            Button(
                onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { label() }
    }
}

/** 1–10 confidence picker (mirrors the web `ConfidenceStepper`): tap a number to set it. */
@Composable
fun ConfidenceStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..10).forEach { n ->
            val selected = n <= value
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        color = if (selected) confidenceColor(value) else MaterialTheme.spiraExtras.surfaceSunken,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onChange(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    n.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (n == value) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.spiraExtras.mutedForeground,
                )
            }
        }
    }
}

/**
 * The one date picker in the app — used by [DeadlineField], [DeadlineLinkField], the target card's
 * calendar tile and the task rows.
 *
 * **A Spira modal, not Material's** (owner, 2026-08-29). It was `DatePickerDialog` + `DatePicker`
 * — a raw platform default sitting in the middle of a Spira form, which CLAUDE.md → Components
 * and chrome → 1 forbids — and it could not show **ISO week numbers**, which Material 3's date
 * picker has no support for at all. The web has drawn them since the beginning
 * (`src/components/ui/calendar.tsx`), so the two surfaces disagreed about what a calendar is.
 *
 * The card is the web's, part for part (`DeadlinePopover`'s phone branch): the app's
 * [SpiraSheetHead], the chosen date written out in words, [SpiraMonthGrid], `Today` and `Clear`
 * as worded links, and the app's foot — quiet outline left, filled Kale right.
 *
 * **A day is a draft.** Tapping one does not commit and does not close; the foot does. On a
 * finger-sized grid a mis-tap that instantly commits *and* dismisses leaves nothing to undo, and
 * `Clear` is a draft for the same reason — which is why the confirm word follows it.
 */
@Composable
fun DeadlinePickerDialog(value: String?, onChange: (String?) -> Unit, onDismiss: () -> Unit) {
    // Seed with the CURRENT deadline (not "today") so editing an existing date opens where the
    // user would expect it, rather than always jumping to today's month.
    val initial = remember(value) {
        value?.let {
            try {
                Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate()
            } catch (e: Exception) {
                null
            }
        }
    }
    var picked by remember(value) { mutableStateOf(initial) }
    var month by remember(value) { mutableStateOf(YearMonth.from(initial ?: LocalDate.now())) }

    val clearing = picked == null && initial != null
    val confirmLabel = if (clearing) "Remove deadline" else "Set deadline"

    // **`usePlatformDefaultWidth = false`, and the card states its own width.** The platform
    // default is a fixed fraction that ignores what the content asks for: it handed a seven-column
    // grid plus a week column about 320dp on a 411dp screen, which left each foot button ~139dp
    // and wrapped "Set deadline" onto two lines (owner, 2026-08-29). Sized here it is the web's
    // card — the screen less a 16dp gutter, capped at 380dp, which is `w-[calc(100%-32px)]
    // max-w-[380px]` written in Compose.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.spiraExtras.surfaceRaised),
        ) {
            // **The head states the draft** (owner, 2026-08-29): the date once one is chosen, and
            // "Set deadline" only while there is none — including straight after `Clear`, where
            // reverting to the prompt is exactly what is about to be true. It was a line of its
            // own above the grid, which said the same thing twice: a band with a fixed title,
            // then a sentence restating what the band was for.
            //
            // `MMMM d, yyyy` and nothing else, matching the web's head down to the format. The
            // weekday and the "13d overdue" the line carried are dropped on purpose — with them
            // the title runs past a narrow phone's head and truncates, and a head that is
            // sometimes cut is worse than one that is always short.
            SpiraSheetHead(
                picked?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
                    ?: "Set deadline",
                onDismiss,
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                SpiraMonthGrid(
                    month = month,
                    selected = picked,
                    onSelect = { picked = it },
                    onMonthChange = { month = it },
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val t = LocalDate.now()
                                month = YearMonth.from(t)
                                picked = t
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    if (picked != null) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { picked = null }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                SpiraIcons.Trash,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpiraButton(
                    "Cancel",
                    onDismiss,
                    Modifier.weight(1f),
                    variant = SpiraButtonVariant.Ghost,
                )
                SpiraButton(
                    confirmLabel,
                    {
                        onChange(picked?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toString())
                        onDismiss()
                    },
                    Modifier.weight(1f),
                    enabled = picked != null || clearing,
                )
            }
        }
    }
}

/**
 * Deadline picker (mirrors the web `DeadlinePopover`): a button that opens a Material date
 * picker, with a Clear action. [value]/[onChange] use an ISO-8601 instant string (or null).
 */
@Composable
fun DeadlineField(value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { showPicker = true }, shape = MaterialTheme.shapes.small) {
            Text(value?.take(10) ?: "Set deadline")
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }
    if (showPicker) DeadlinePickerDialog(value, onChange) { showPicker = false }
}

/**
 * A teal, link-styled deadline trigger (mirrors the web's "Nov 1, 2026 ›" link on the Deadline
 * KPI card): the formatted date (or "Set deadline"), a chevron, opens the same date picker.
 */
@Composable
fun DeadlineLinkField(value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier.clickable { showPicker = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value?.let { com.spiramindscape.android.ui.util.formatDeadlineDate(it) } ?: "Set deadline",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            com.spiramindscape.android.ui.icons.SpiraIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
    if (showPicker) DeadlinePickerDialog(value, onChange) { showPicker = false }
}

/**
 * Bottom-sheet form scaffold (the mobile equivalent of the web's create/edit Sheet): the app's
 * Kale head with title + close, scrollable body, pinned footer with Cancel + a confirm action.
 *
 * The head is [SpiraSheetHead], the same band `SpiraFilterSheet` and the web's create sheets wear
 * (owner, 2026-08-22) - so there is **no drag handle**: it would sit on the teal as a grey smudge,
 * and the X in the head is what closes the sheet, plus the usual drag and back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiraFormSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = SpiraRadii.lg, topEnd = SpiraRadii.lg),
    ) {
        SpiraFormSheetContent(title, onDismiss, confirmLabel, onConfirm, confirmEnabled, content)
    }
}

/**
 * The form sheet's card, without the [ModalBottomSheet] around it.
 *
 * Separate for the same reason `SpiraFilterSheetContent` is: a modal sheet renders in its **own
 * window**, which the `VisualCheck*` screenshot helper (it draws the activity's decor view) cannot
 * capture - so an open sheet is simply absent from the PNG, and the check that would catch a head
 * drawn the wrong colour or a field running off the side silently checks nothing.
 */
@Composable
fun SpiraFormSheetContent(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        SpiraSheetHead(title, onDismiss)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpiraButton("Cancel", onDismiss, Modifier.weight(1f), SpiraButtonVariant.Ghost)
            SpiraButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
        }
    }
}

/**
 * [message] with every occurrence of [subject] set bold and in the full-strength ink. Returns the
 * message unchanged when there is no subject to pick out.
 */
private fun emphasise(message: String, subject: String?): AnnotatedString {
    if (subject.isNullOrBlank()) return AnnotatedString(message)
    return buildAnnotatedString {
        var from = 0
        while (true) {
            val at = message.indexOf(subject, from)
            if (at < 0) break
            append(message.substring(from, at))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(subject) }
            from = at + subject.length
        }
        append(message.substring(from))
    }
}

/** Confirmation dialog for destructive actions (mirrors the web `ConfirmDialog`). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Yes, remove",
    cancelLabel: String = "No, go back",
    /** "destructive" (default) = red confirm; primary = teal, for a constructive action. */
    destructive: Boolean = true,
    /**
     * The thing being acted on — its name is drawn **bold** wherever it appears in [message], so
     * the user can see *what* they are about to delete instead of having to find it inside a
     * sentence set in one flat weight.
     */
    subject: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.spiraExtras.surfaceRaised)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    // The title is set in the SANS, not the headline serif — it is interface
                    // copy, not a heading, exactly as on the web.
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    emphasise(message, subject),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    DialogButton(
                        label = cancelLabel,
                        onClick = onDismiss,
                    )
                    DialogButton(
                        label = confirmLabel,
                        onClick = { onConfirm(); onDismiss() },
                        // error-900 from the palette's semantic ramp — the colour reserved for
                        // danger. Guava stays the brand accent and is deliberately not used here.
                        container = if (destructive) Error900 else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Icon(
                com.spiramindscape.android.ui.icons.SpiraIcons.X,
                contentDescription = "Close",
                tint = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

/** One dialog action: outlined by default, solid when [container] is given. */
@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit,
    container: Color? = null,
) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (container != null) {
                    Modifier.background(container)
                } else {
                    Modifier.border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(6.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (container != null) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small label above a form control. */
@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.spiraExtras.mutedForeground,
        textAlign = TextAlign.Start,
    )
}
