package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.HeaderCircleAction
import com.spiramindscape.android.ui.components.ResourceTopBar
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Parsnip200
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * A full-screen view for a **file** resource (image / PDF pages). Shown as a full-bleed dialog over
 * everything, with a pinch-to-zoom viewer and a Download footer. The title is editable in the
 * header. Notes are edited in the dedicated [NoteEditorActivity], not here. Opened from
 * [ResourcesTabContent] via its `onOpenFull` callback.
 */
@Composable
fun ResourceFullScreen(
    res: ResourceItem,
    actions: GoalWorkspaceActions,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.spiraExtras.surfaceRaised) {
            Column(Modifier.fillMaxSize()) {
                FileFullScreen(res, actions, onClose)
            }
        }
    }
}

@Composable
private fun ColumnScope.FileFullScreen(res: ResourceItem, actions: GoalWorkspaceActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val bytes = remember(res.dataUrl) { decodeDataUrl(res.dataUrl) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = res.title ?: "File"
    // The same bar the note editor opens under — see ResourceTopBar. A file's right-hand action is
    // the delete cross, which asks before it removes anything.
    ResourceTopBar(
        title = name,
        onTitleCommit = { commitResource(actions, res, title = it.ifBlank { null }) },
        onBack = onClose,
        action = {
            HeaderCircleAction(SpiraIcons.X, "Delete resource") { confirmDelete = true }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this resource?",
            message = "\"$name\" will be permanently deleted. You can't undo this.",
            subject = "\"$name\"",
            confirmLabel = "Yes, delete",
            cancelLabel = "No, go back",
            onConfirm = { confirmDelete = false; actions.onRemoveResource(res.id); onClose() },
            onDismiss = { confirmDelete = false },
        )
    }
    val saveFile = rememberFileSaver()
    Box(Modifier.weight(1f).fillMaxWidth().background(Parsnip200), contentAlignment = Alignment.Center) {
        when {
            isImageMime(res.mime) && bytes != null -> {
                val img = remember(bytes) { decodeImageBitmap(bytes) }
                if (img != null) {
                    ZoomableBox(Modifier.fillMaxSize().padding(12.dp)) {
                        Image(
                            bitmap = img,
                            contentDescription = res.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            isPdfMime(res.mime) && bytes != null -> {
                // High-res render so pages stay sharp when pinch-zoomed.
                val pages by produceState<List<ImageBitmap>?>(initialValue = null, bytes) {
                    value = renderPdfPages(context, bytes, targetWidthPx = 1600)
                }
                when (val p = pages) {
                    null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(p) { page ->
                            // Pinch (two fingers) zooms a page; one finger still scrolls the list.
                            ZoomableBox(Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = page,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                        }
                    }
                }
            }
            else -> Text("Can't preview this file.", color = MaterialTheme.spiraExtras.mutedForeground)
        }
    }
    if (bytes != null) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            com.spiramindscape.android.ui.components.SpiraButton(
                "Download",
                onClick = { saveFile(downloadFileName(res.title, res.mime), res.mime ?: "application/octet-stream", bytes) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Pinch-to-zoom + pan container. Transform is handled only for **two-finger** gestures, so a single
 * finger still passes through to a surrounding scroll (e.g. the PDF page list).
 */
@Composable
private fun ZoomableBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentAlignment = Alignment.Center,
    ) { content() }
}

