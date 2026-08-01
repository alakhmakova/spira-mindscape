package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.AddItemRow
import com.spiramindscape.android.ui.components.DeadlineField
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.components.TypeSelector

/**
 * Bottom-sheet form to add a target (mirrors the web `NewTargetForm`): pick a type
 * (Done/Not done · Numeric · Checklist), a title, type-specific fields, and an optional deadline.
 */
@Composable
fun NewTargetSheet(
    onDismiss: () -> Unit,
    onCreate: (
        title: String, type: String, deadline: String?,
        start: Double?, total: Double?, unit: String?, checklist: List<String>,
    ) -> Unit,
) {
    var type by remember { mutableStateOf("binary") }
    var title by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var checklist by remember { mutableStateOf(listOf<String>()) }

    val valid = title.isNotBlank() &&
        (type != "checklist" || checklist.isNotEmpty()) &&
        // Numeric needs a target value (start defaults to 0 client-side); the backend requires it.
        (type != "numeric" || total.toDoubleOrNull() != null)

    SpiraFormSheet(
        title = "New target",
        onDismiss = onDismiss,
        confirmLabel = "Add target",
        onConfirm = {
            onCreate(
                title.trim(), type, deadline,
                start.toDoubleOrNull(), total.toDoubleOrNull(), unit.ifBlank { null }, checklist,
            )
        },
        confirmEnabled = valid,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Type")
            TypeSelector(
                options = listOf("binary" to "Done", "numeric" to "Numeric", "checklist" to "Checklist"),
                selected = type,
                onSelect = { type = it },
            )
        }
        SpiraTextField(title, { title = it }, "Title")

        when (type) {
            "numeric" -> {
                SpiraTextField(start, { start = it }, "Start (optional)", keyboardType = KeyboardType.Decimal)
                SpiraTextField(total, { total = it }, "Target", keyboardType = KeyboardType.Decimal)
                SpiraTextField(unit, { unit = it }, "Unit (optional)")
            }
            "checklist" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Tasks")
                checklist.forEachIndexed { i, task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("• $task", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { checklist = checklist.filterIndexed { idx, _ -> idx != i } }) {
                            Icon(com.spiramindscape.android.ui.icons.SpiraIcons.X, contentDescription = "Remove task")
                        }
                    }
                }
                AddItemRow("Add task", onAdd = { checklist = checklist + it })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Deadline")
            DeadlineField(deadline, { deadline = it })
        }
    }
}
