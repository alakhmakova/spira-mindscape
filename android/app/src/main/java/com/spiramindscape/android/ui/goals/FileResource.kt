package com.spiramindscape.android.ui.goals

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Helpers for the two file resource kinds the app renders: images and PDFs (base64 `dataUrl`). */

fun isImageMime(mime: String?): Boolean = mime?.startsWith("image/") == true
fun isPdfMime(mime: String?): Boolean = mime == "application/pdf"

/** Human-friendly byte size, e.g. "820 KB" / "1.2 MB". */
fun humanFileSize(bytes: Int): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

/** Number of pages in a PDF (given raw bytes), or 0 if it can't be read. */
suspend fun pdfPageCount(context: Context, bytes: ByteArray): Int = withContext(Dispatchers.IO) {
    val file = File.createTempFile("spira-pdf-count", ".pdf", context.cacheDir)
    try {
        file.writeBytes(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    } catch (e: Exception) {
        0
    } finally {
        file.delete()
    }
}

/** Decode a `data:<mime>;base64,<payload>` URL to raw bytes (payload only). */
fun decodeDataUrl(dataUrl: String?): ByteArray? {
    if (dataUrl.isNullOrBlank()) return null
    val comma = dataUrl.indexOf(',')
    val payload = if (comma >= 0 && dataUrl.startsWith("data:")) dataUrl.substring(comma + 1) else dataUrl
    return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
}

/** Build a `data:<mime>;base64,<payload>` URL from raw bytes (for uploads). */
fun encodeDataUrl(mime: String, bytes: ByteArray): String =
    "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

/** Decode image bytes to a Compose [ImageBitmap], or null if not decodable. */
fun decodeImageBitmap(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null) return null
    val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
    return bmp.asImageBitmap()
}

/**
 * Render every page of a PDF (given as raw bytes) to a bitmap using the platform [PdfRenderer].
 * Writes to a temp cache file first (PdfRenderer needs a seekable file descriptor). IO-bound, so
 * call from a background dispatcher.
 */
suspend fun renderPdfPages(context: Context, bytes: ByteArray, targetWidthPx: Int = 1080): List<ImageBitmap> =
    withContext(Dispatchers.IO) {
        val file = File.createTempFile("spira-pdf", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    (0 until renderer.pageCount).map { i ->
                        renderer.openPage(i).use { page ->
                            val scale = targetWidthPx.toFloat() / page.width
                            val w = targetWidthPx
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.asImageBitmap()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            file.delete()
        }
    }
