package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.theme.confidenceColor
import com.spiramindscape.android.ui.theme.spiraExtras
import java.time.Instant
import java.time.ZoneOffset

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
    when (variant) {
        SpiraButtonVariant.Primary ->
            Button(onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small) { Text(text) }
        SpiraButtonVariant.Ghost ->
            OutlinedButton(onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small) { Text(text) }
        SpiraButtonVariant.Destructive ->
            Button(
                onClick, modifier, enabled = enabled, shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(text) }
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
 * calendar tile and the task rows. "Clear" removes the date; "OK" keeps whatever is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinePickerDialog(value: String?, onChange: (String?) -> Unit, onDismiss: () -> Unit) {
    // Seed the calendar with the CURRENT deadline (not "today") so editing an existing date
    // opens where the user would expect it, rather than always jumping to today's month.
    val initialMillis = value?.let {
        try { Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
    }
    val stateDp = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                stateDp.selectedDateMillis?.let {
                    onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toInstant().toString())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                // Clearing a date must be reachable from wherever the picker opens — the calendar
                // tile and the task rows have no separate "Clear" affordance beside them.
                if (value != null) {
                    TextButton(onClick = { onChange(null); onDismiss() }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(state = stateDp) }
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
 * Bottom-sheet form scaffold (the mobile equivalent of the web's create/edit Sheet): sticky
 * header with title + close, scrollable body, pinned footer with Cancel + a confirm action.
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
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(com.spiramindscape.android.ui.icons.SpiraIcons.X, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpiraButton("Cancel", onDismiss, Modifier.weight(1f), SpiraButtonVariant.Ghost)
                SpiraButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
            }
        }
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
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
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
