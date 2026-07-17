package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.components.TypeSelector

/**
 * Bottom-sheet form to add OR edit a resource (mirrors the web `NewResourceSheet`). Pass
 * [initial] to edit an existing one (its type is then fixed, as on the web). Note bodies are
 * plain text for now (rich text is deferred — see backlog/mobile-notes-rich-text.md). File
 * upload is view-only for now, so it's not offered here.
 */
@Composable
fun NewResourceSheet(
    onDismiss: () -> Unit,
    onSubmit: (
        type: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
    ) -> Unit,
    initial: ResourceItem? = null,
) {
    val editing = initial != null
    var type by remember { mutableStateOf(initial?.type ?: "note") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }

    val valid = when (type) {
        "note" -> title.isNotBlank() || body.isNotBlank()
        "link" -> url.isNotBlank()
        "email" -> name.isNotBlank()
        else -> false
    }

    SpiraFormSheet(
        title = if (editing) "Edit resource" else "New resource",
        onDismiss = onDismiss,
        confirmLabel = if (editing) "Save" else "Add resource",
        onConfirm = {
            onSubmit(
                type,
                title.ifBlank { null }, body.ifBlank { null }, url.ifBlank { null },
                name.ifBlank { null }, email.ifBlank { null }, role.ifBlank { null }, phone.ifBlank { null },
            )
        },
        confirmEnabled = valid,
    ) {
        // Type is fixed once created (mirrors the web), so only offer it when adding.
        if (!editing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Type")
                TypeSelector(
                    options = listOf("note" to "Note", "link" to "Link", "email" to "Contact"),
                    selected = type,
                    onSelect = { type = it },
                )
            }
        }
        when (type) {
            "note" -> {
                SpiraTextField(title, { title = it }, "Title")
                SpiraTextField(body, { body = it }, "Note", singleLine = false, minLines = 4)
            }
            "link" -> {
                SpiraTextField(title, { title = it }, "Title (optional)")
                SpiraTextField(url, { url = it }, "URL", keyboardType = KeyboardType.Uri)
            }
            "email" -> {
                SpiraTextField(name, { name = it }, "Name")
                SpiraTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
                SpiraTextField(role, { role = it }, "Role (optional)")
                SpiraTextField(phone, { phone = it }, "Phone (optional)", keyboardType = KeyboardType.Phone)
            }
        }
    }
}
