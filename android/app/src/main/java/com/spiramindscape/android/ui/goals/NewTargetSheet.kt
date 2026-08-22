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
import com.spiramindscape.android.ui.components.AttachResourceButton
import com.spiramindscape.android.ui.components.DeadlineField
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.LocalInlineResources
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraInlineBanner
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.components.TypeSelector
import com.spiramindscape.android.ui.util.FieldLimits
import com.spiramindscape.android.ui.util.appendResourceToken
import com.spiramindscape.android.ui.util.namesToTokens
import com.spiramindscape.android.ui.util.readableText
import com.spiramindscape.android.ui.util.tokensToNames

/**
 * Bottom-sheet form to add a target (mirrors the web `NewTargetForm`): pick a type
 * (Done/Not done · Numeric · Checklist), a title, type-specific fields, and an optional deadline.
 *
 * **A resource can be attached here, before the target exists** (owner, 2026-08-20). It used to be
 * reachable only from a card that had already been created, so adding a target with its reading
 * attached took three steps in two places.
 *
 * The form follows the same two-form rule the inline fields do: while it is being typed a tag
 * reads as the resource's NAME — `Read {{res:Job ad}} first` — and `toStored` maps it back to the
 * id on confirm, so a tag naming something that has since gone degrades to plain text rather than
 * writing a dangling reference.
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

    val resources = LocalInlineResources.current?.resources.orEmpty()
    fun toStored(text: String) = namesToTokens(text, resources)
    // Why an attach was refused. `appendResourceToken` answers `null` when the tag would push the
    // field past the server's limit, and swallowing that is exactly the defect the logging rules
    // call out (BUG-034): the picker closes, nothing changes, and there is nothing on screen to
    // say why. The web twin toasts the same sentence.
    var attachError by remember { mutableStateOf<String?>(null) }
    // Append a tag, measuring the STORED form against the field's limit — the ids are what the
    // server sees, and the names on screen are a different length.
    fun attachTo(text: String, resourceId: String, limit: Int): String? {
        val next = appendResourceToken(toStored(text), resourceId, limit)
        if (next == null) {
            attachError =
                "No room to attach a resource here \u2014 this field is limited to $limit characters."
            return null
        }
        attachError = null
        return tokensToNames(next, resources)
    }

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
                toStored(title.trim()), type, deadline,
                start.toDoubleOrNull(), total.toDoubleOrNull(), unit.ifBlank { null },
                checklist.map { toStored(it) },
            )
        },
        confirmEnabled = valid,
    ) {
        // The banner carries its own margin, so nothing here adds one. It sits at the top of the
        // form because either of the two attach controls below can raise it.
        SpiraInlineBanner(message = attachError, onDismiss = { attachError = null })

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Type")
            TypeSelector(
                options = listOf("binary" to "Done", "numeric" to "Numeric", "checklist" to "Checklist"),
                selected = type,
                onSelect = { type = it },
            )
        }
        SpiraTextField(title, { title = it }, "Title")
        // Renders nothing outside a goal workspace, where there is no list to pick from — so a
        // form opened without one is unchanged.
        AttachResourceButton(
            attachedTo = toStored(title),
            onAttach = { id -> attachTo(title, id, FieldLimits.TARGET_TITLE)?.let { title = it } },
        )

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
                        // The resource's NAME, with no `{{res:...}}` around it: this row is a
                        // read-only strip, and the tag syntax belongs in a field being edited.
                        Text(
                            "• ${readableText(toStored(task), resources)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // The paperclip alone: this row is a compact strip that already carries
                        // its own remove control, so a worded link would be wider than the row.
                        AttachResourceButton(
                            iconOnly = true,
                            contentDescription = "Attach a resource to this task",
                            attachedTo = toStored(task),
                            onAttach = { id ->
                                attachTo(task, id, FieldLimits.CHECKLIST_TEXT)?.let { next ->
                                    checklist = checklist.mapIndexed { idx, t -> if (idx == i) next else t }
                                }
                            },
                        )
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
