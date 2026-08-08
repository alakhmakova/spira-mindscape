package com.spiramindscape.android.ui.ai

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.spiramindscape.android.core.SpiraLog
import com.spiramindscape.android.data.ai.AiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Files attached directly to a chat message — images, PDFs and DOCX, matching the web composer's
 * `ATTACH_ACCEPT`. They are ephemeral: sent with this one message so the model can read them, and
 * never saved as a resource.
 */

private const val TAG = "ChatAttachments"

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
 * The two ways to attach something to a message. On a phone the **camera** is the primary one —
 * photographing a document and asking the assistant to read it is the case the OCR work exists
 * for — so it can't be reachable only through the gallery.
 */
class ChatAttachActions(
    /** Open the system document/gallery picker. */
    val pickFile: () -> Unit,
    /** Open the camera and attach the shot. */
    val takePhoto: () -> Unit,
)

/**
 * Opens the picker (or the camera) and returns what was captured, already read and — for images —
 * downscaled. Anything unreadable or too large is skipped rather than failing the whole pick.
 */
@Composable
fun rememberChatAttachmentPicker(
    onPicked: (List<AiApi.ChatAttachment>) -> Unit,
): ChatAttachActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileLauncher = rememberLauncherForActivityResult(
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

    // The camera writes into a file we hand it, so the destination has to exist before launching
    // and be remembered until the result comes back.
    //
    // rememberSaveable, NOT remember: a camera app is memory-hungry and routinely gets this
    // activity recreated — or the whole process killed — while it is in front. A plain `remember`
    // loses the destination in that moment, and the photo then comes back to a null Uri and is
    // dropped without a word. Uri is Parcelable, so the saver keeps it across both.
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pending
        pending = null
        if (!saved || uri == null) {
            // A cancelled shot is not worth a word; a lost destination is — that is the case that
            // used to look like "the camera did nothing".
            if (saved) SpiraLog.w(TAG, "camera_result_without_destination")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                val attachment = readAttachment(context, uri)
                // The photo has been turned into a data URL; the file itself was never something
                // the user asked to keep, so it goes rather than lingering in the cache.
                runCatching { context.contentResolver.delete(uri, null, null) }
                listOfNotNull(attachment)
            }
            if (read.isNotEmpty()) {
                onPicked(read)
            } else {
                // The shot was taken but couldn't be read or was too large. Silence here is what
                // makes a feature feel broken, so say it and record it.
                SpiraLog.w(TAG, "camera_photo_unreadable uri=$uri")
                Toast.makeText(context, "Couldn't attach that photo. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    return ChatAttachActions(
        pickFile = { fileLauncher.launch(ATTACHABLE_MIMES) },
        takePhoto = {
            cameraDestination(context)?.let { uri ->
                pending = uri
                cameraLauncher.launch(uri)
            }
        },
    )
}

/** A fresh file in the cache for the camera to fill, exposed through the app's FileProvider. */
private fun cameraDestination(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

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
