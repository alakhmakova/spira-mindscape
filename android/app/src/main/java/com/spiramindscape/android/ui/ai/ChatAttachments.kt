package com.spiramindscape.android.ui.ai

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.spiramindscape.android.data.ai.AiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Files attached directly to a chat message — images, PDFs and DOCX, matching the web composer's
 * `ATTACH_ACCEPT`. They are ephemeral: sent with this one message so the model can read them, and
 * never saved as a resource.
 */

/** Matches the backend `ChatRequest` cap. */
const val ATTACH_MAX_COUNT = 6

/** PDFs and DOCX are sent as-is, so their raw size is what has to stay sane. */
private const val ATTACH_MAX_BYTES = 5 * 1024 * 1024

/** A camera photo can be enormous; refuse to even decode beyond this. */
private const val IMAGE_MAX_INPUT_BYTES = 25 * 1024 * 1024

/** Downscale target: plenty for the model to read, a fraction of the bytes. */
private const val IMAGE_MAX_DIM = 1600
private const val IMAGE_JPEG_QUALITY = 80

private val ATTACHABLE_MIMES = arrayOf(
    "image/*",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

/**
 * Opens the system picker and returns the chosen files, already read and (for images) downscaled.
 * Anything unreadable or too large is skipped rather than failing the whole pick.
 */
@Composable
fun rememberChatAttachmentPicker(
    onPicked: (List<AiApi.ChatAttachment>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                uris.take(ATTACH_MAX_COUNT).mapNotNull { readAttachment(context, it) }
            }
            if (read.isNotEmpty()) onPicked(read)
        }
    }
    return { launcher.launch(ATTACHABLE_MIMES) }
}

/** Reads one picked file into a `data:` URL, downscaling images. Null when it can't be used. */
internal fun readAttachment(context: Context, uri: Uri): AiApi.ChatAttachment? = runCatching {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null
    val name = displayName(resolver, uri) ?: "attachment"

    if (mime.startsWith("image/")) {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > IMAGE_MAX_INPUT_BYTES) return null
        val jpeg = downscaleToJpeg(bytes) ?: return null
        AiApi.ChatAttachment(name, "image/jpeg", "data:image/jpeg;base64,${base64(jpeg)}")
    } else {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > ATTACH_MAX_BYTES) return null
        AiApi.ChatAttachment(name, mime, "data:$mime;base64,${base64(bytes)}")
    }
}.getOrNull()

private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

private fun displayName(resolver: ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/**
 * Decode at a sample size first, then scale — decoding a 12-megapixel photo at full size is what
 * runs a phone out of memory (the same guard the goal-page photo attach needed).
 */
internal fun downscaleToJpeg(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > IMAGE_MAX_DIM * 2 || bounds.outHeight / sample > IMAGE_MAX_DIM * 2) {
        sample *= 2
    }

    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null

    val longest = maxOf(decoded.width, decoded.height)
    val scaled = if (longest > IMAGE_MAX_DIM) {
        val ratio = IMAGE_MAX_DIM.toFloat() / longest
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return out.toByteArray()
}
