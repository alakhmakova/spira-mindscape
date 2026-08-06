package com.spiramindscape.android.ui.goals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.spiramindscape.android.data.goals.ResourceItem
import java.io.File

/** Copy plain text to the clipboard. */
fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Follow an inline resource link (a `{{res:id}}` token tapped inside a target title, a task, an
 * option or a reality item) to the right place for its kind: a link goes to the site, a note opens
 * the note editor, a file opens the full-screen viewer, and a contact opens the mail app.
 * A resource that no longer exists opens nothing.
 */
fun openInlineResource(
    context: Context,
    resource: ResourceItem?,
    onOpenFullScreen: (String) -> Unit,
) {
    if (resource == null) return
    when (resource.type) {
        "link" -> openExternalUri(context, resource.url)
        "note" -> context.startActivity(
            NoteEditorActivity.intent(context, resource.id, resource.title ?: "", resource.body ?: ""),
        )
        "file" -> onOpenFullScreen(resource.id)
        else -> resource.email?.takeIf { it.isNotBlank() }?.let { openExternalUri(context, "mailto:$it") }
    }
}

/** Open a URI in whatever app handles it; a missing/blank URI or no handler is a no-op. */
fun openExternalUri(context: Context, uri: String?) {
    if (uri.isNullOrBlank()) return
    val normalized = if (uri.contains(":")) uri else "https://$uri"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }
}

/** Strip HTML (a note body) to readable plain text for copying. */
fun htmlToPlainText(html: String?): String =
    if (html.isNullOrBlank()) ""
    else HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

private fun mimeExtension(mime: String?): String = when {
    mime == null -> "bin"
    mime == "application/pdf" -> "pdf"
    mime == "image/jpeg" -> "jpg"
    mime.startsWith("image/") -> mime.substringAfter('/')
    else -> "bin"
}

/** Copy an image (raw bytes) to the clipboard via a FileProvider content URI. */
fun copyImageToClipboard(context: Context, mime: String?, bytes: ByteArray) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "clip-image.${mimeExtension(mime)}")
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, "Image", uri))
}

/**
 * A "Save file" action using the Storage Access Framework (no permissions, all API levels): the
 * returned lambda opens the system create-document picker with a suggested name, then writes the
 * given bytes to wherever the user chooses.
 */
@Composable
fun rememberFileSaver(): (suggestedName: String, mime: String, bytes: ByteArray) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val data = pending
        pending = null
        if (uri != null && data != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
        }
    }
    return { name, _, bytes ->
        pending = bytes
        launcher.launch(name)
    }
}

/** A sensible download filename for a resource file, e.g. "Tax return.pdf". */
fun downloadFileName(title: String?, mime: String?): String {
    val base = (title?.takeIf { it.isNotBlank() } ?: "file").replace(Regex("[^A-Za-z0-9 ._-]"), "").trim()
    val ext = mimeExtension(mime)
    return if (base.endsWith(".$ext")) base else "$base.$ext"
}
