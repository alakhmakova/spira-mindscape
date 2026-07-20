package com.spiramindscape.android.ui.goals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.SpiraFormSheet
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.components.TypeSelector
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Kale100
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.Parsnip100
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * Bottom-sheet form to add OR edit a resource (mirrors the web `NewResourceSheet`). Pass [initial]
 * to edit an existing one (its type is then fixed). Notes use the rich-text editor (HTML); files
 * are picked from the device (image or PDF) and stored as a base64 `dataUrl` + `mime`.
 */
@Composable
fun NewResourceSheet(
    onDismiss: () -> Unit,
    onSubmit: (
        type: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
        mime: String?, dataUrl: String?,
    ) -> Unit,
    initial: ResourceItem? = null,
) {
    val context = LocalContext.current
    val editing = initial != null
    var type by remember { mutableStateOf(initial?.type ?: "note") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var noteHtml by remember { mutableStateOf(initial?.body ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var fileMime by remember { mutableStateOf<String?>(initial?.mime) }
    var fileDataUrl by remember { mutableStateOf<String?>(initial?.dataUrl) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                fileMime = mime
                fileDataUrl = encodeDataUrl(mime, bytes)
                fileName = uri.lastPathSegment?.substringAfterLast('/')
                if (title.isBlank() && fileName != null) title = fileName!!
            }
        }
    }

    val valid = when (type) {
        "note" -> true
        "link" -> url.isNotBlank()
        "email" -> name.isNotBlank() && email.isNotBlank()
        "file" -> fileDataUrl != null
        else -> false
    }

    SpiraFormSheet(
        title = if (editing) "Edit resource" else "New resource",
        onDismiss = onDismiss,
        confirmLabel = if (editing) "Save" else "Add resource",
        onConfirm = {
            onSubmit(
                type,
                title.ifBlank { null },
                if (type == "note") noteHtml.ifBlank { null } else null,
                url.ifBlank { null },
                name.ifBlank { null }, email.ifBlank { null }, role.ifBlank { null }, phone.ifBlank { null },
                fileMime, fileDataUrl,
            )
        },
        confirmEnabled = valid,
    ) {
        if (!editing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Type")
                TypeSelector(
                    options = listOf("note" to "Note", "link" to "Link", "file" to "File", "email" to "Contact"),
                    selected = type,
                    onSelect = { type = it },
                )
            }
        }
        when (type) {
            "note" -> {
                // Create captures the title only; the note body is written in the full-screen
                // NoteEditorActivity (tap "Open note" on the card) where the editor is reliable.
                SpiraTextField(title, { title = it }, "Title")
                Text(
                    "You'll write the note after adding it — tap \"Open note\" on the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                )
            }
            "link" -> {
                SpiraTextField(title, { title = it }, "Title (optional)")
                SpiraTextField(url, { url = it }, "URL", keyboardType = KeyboardType.Uri)
            }
            "file" -> {
                FilePickerRow(fileName ?: initial?.title, mimePresent = fileDataUrl != null) {
                    picker.launch(arrayOf("image/*", "application/pdf"))
                }
                SpiraTextField(title, { title = it }, "Title (optional)")
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

/** A dashed "choose a file" row (image or PDF) matching the mockup's upload control. */
@Composable
private fun FilePickerRow(pickedName: String?, mimePresent: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Parsnip100)
            .border(1.5.dp, MaterialTheme.spiraExtras.borderStrong, RoundedCornerShape(13.dp))
            .clickable(onClick = onPick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Kale100),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpiraIcons.Download, contentDescription = null, tint = Kale600, modifier = Modifier.size(19.dp))
        }
        Text(
            if (mimePresent) (pickedName ?: "File selected") else "Choose a file (image or PDF)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.spiraExtras.mutedForeground,
        )
    }
}
