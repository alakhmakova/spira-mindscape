package com.spiramindscape.android.ui.goals

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.content.ClipboardManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.view.MotionEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.net.Network
import com.spiramindscape.android.graphql.type.UpdateResourceInput
import com.spiramindscape.android.ui.components.ResourceTopBar
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.core.SpiraLog
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.HeaderCircleAction
import com.spiramindscape.android.ui.components.SpiraInlineBanner
import kotlinx.coroutines.withContext
import com.spiramindscape.android.ui.components.SpiraToast
import com.spiramindscape.android.ui.components.SpiraToastKind
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraTheme
import com.spiramindscape.android.ui.theme.spiraExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Full-screen, single-purpose note editor. Hosting the editable TipTap WebView in a real Activity
 * window (not an `AndroidView` inside a Compose list/sheet/dialog) is what makes keyboard input
 * reliable — the embedded editor never got IME focus. Formatting is driven by the NATIVE Compose
 * [NoteToolbar]; the note autosaves as HTML continuously (so nothing is lost even if killed).
 *
 * Launch with [intent]; on return the workspace refetches and shows the updated note.
 */
class NoteEditorActivity : ComponentActivity() {

    // Not tied to the Compose/lifecycle scope: the final save fired as we finish() must still
    // complete. Jobs are short (a single mutation), so the scope is collected once they finish.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Network.init(applicationContext)
        val repository = ApolloGoalsRepository(Network.apollo)

        val resourceId = intent.getStringExtra(EXTRA_RESOURCE_ID).orEmpty()
        val initialTitle = intent.getStringExtra(EXTRA_INITIAL_TITLE).orEmpty()
        val initialHtml = intent.getStringExtra(EXTRA_INITIAL_HTML).orEmpty()

        // A note autosaves as it is typed. A failed save used to be swallowed silently: nothing was
        // logged and nothing was shown, so a note that didn't persist looked exactly like one that
        // did — the "invisible AND lost" class the logging rules call out, and the difference the
        // owner saw versus the web (whose optimistic store surfaces a sync error). Both a save that
        // fails now surface: a WARN for us, and a banner for them.
        fun saveBody(html: String, onError: () -> Unit) {
            saveScope.launch {
                runCatching {
                    repository.updateResource(resourceId, UpdateResourceInput(body = Optional.present(html)))
                }.onFailure { e ->
                    SpiraLog.w(TAG, "note_body_save_failed resourceId=$resourceId", e)
                    withContext(Dispatchers.Main) { onError() }
                }
            }
        }
        fun saveTitle(title: String, onError: () -> Unit) {
            saveScope.launch {
                runCatching {
                    repository.updateResource(resourceId, UpdateResourceInput(title = Optional.present(title)))
                }.onFailure { e ->
                    SpiraLog.w(TAG, "note_title_save_failed resourceId=$resourceId", e)
                    withContext(Dispatchers.Main) { onError() }
                }
            }
        }

        setContent {
            SpiraTheme {
                // A failed delete is invisible and loses the user's intent, so it is reported both
                // ways: a log for us, a banner for them (CLAUDE.md "Logging" — the BUG-034 class).
                var deleteError by remember { mutableStateOf<String?>(null) }
                // Same treatment for a save that didn't land, so lost edits stop being silent.
                var saveError by remember { mutableStateOf<String?>(null) }
                NoteEditorScreen(
                    initialTitle = initialTitle,
                    initialHtml = initialHtml,
                    onTitleCommit = { title ->
                        saveTitle(title) {
                            saveError = "Couldn't save the note title. Check your connection and try again."
                        }
                    },
                    onBodyChange = { html ->
                        saveBody(html) {
                            saveError = "Couldn't save this note — your latest edits may not be stored. Check your connection."
                        }
                    },
                    onDone = { finish() },
                    saveError = saveError,
                    onDismissSaveError = { saveError = null },
                    deleteError = deleteError,
                    onDismissDeleteError = { deleteError = null },
                    onDelete = {
                        saveScope.launch {
                            runCatching { repository.removeResource(resourceId) }
                                .onSuccess { withContext(Dispatchers.Main) { finish() } }
                                .onFailure { e ->
                                    SpiraLog.w(TAG, "note_delete_failed resourceId=$resourceId", e)
                                    withContext(Dispatchers.Main) {
                                        deleteError = "Couldn't delete this note. Check your connection and try again."
                                    }
                                }
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "NoteEditor"
        private const val EXTRA_RESOURCE_ID = "resourceId"
        private const val EXTRA_INITIAL_TITLE = "initialTitle"
        private const val EXTRA_INITIAL_HTML = "initialHtml"

        fun intent(context: Context, resourceId: String, title: String, html: String): Intent =
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
                putExtra(EXTRA_INITIAL_TITLE, title)
                putExtra(EXTRA_INITIAL_HTML, html)
            }
    }
}

/** Holds the live WebView so the toolbar can send commands and the screen can read final HTML. */
private class NoteEditorController {
    var webView: WebView? = null

    fun cmd(name: String, arg: String?) {
        val call = if (arg != null) {
            "window.spiraCmd(${JSONObject.quote(name)}, ${JSONObject.quote(arg)})"
        } else {
            "window.spiraCmd(${JSONObject.quote(name)})"
        }
        webView?.evaluateJavascript(call, null)
    }

    /** Read the current HTML, then run [then] with it (best-effort; empty on failure). */
    fun withHtml(then: (String) -> Unit) {
        val web = webView
        if (web == null) {
            then("")
            return
        }
        web.evaluateJavascript("window.spiraGetHtml ? window.spiraGetHtml() : ''") { raw ->
            then(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty())
        }
    }

    // ── Selection ──────────────────────────────────────────────────────────────
    //
    // The app carries text selection itself, because the platform will not do it here: in a
    // **WebView**, a long press inside a `contenteditable` drops an insertion caret and offers a
    // one-item "Select all" — no word, no drag handles, no Copy. The **same page in stock Chrome**
    // on the same emulator selects the word, raises both handles and offers Cut / Copy / Select all
    // (2026-08-18; the screenshots are in the bug file). So it is not the page, and it is not ours
    // to fix from inside it.
    //
    // Each call answers with the selected text and where its two ends sit — see the matching
    // section of `embeds/note-editor/main.ts`, which does the work through ProseMirror.

    /** Select the word under a point (CSS pixels from the WebView's top-left). */
    fun selectWordAt(x: Float, y: Float, then: (NoteSelection?) -> Unit) =
        call("window.spiraSelectWordAt ? window.spiraSelectWordAt($x, $y) : null", then)

    /** Drag one end of the selection — [which] is "start" or "end" — to a point. */
    fun moveSelectionEnd(which: String, x: Float, y: Float, then: (NoteSelection?) -> Unit) =
        call(
            "window.spiraMoveSelectionEnd ? " +
                "window.spiraMoveSelectionEnd(${JSONObject.quote(which)}, $x, $y) : null",
            then,
        )

    /** Take the whole note. */
    fun selectAll(then: (NoteSelection?) -> Unit) =
        call("window.spiraSelectAll ? window.spiraSelectAll() : null", then)

    /** Where the selection is now — used to redraw the handles after an edit. */
    fun selectionInfo(then: (NoteSelection?) -> Unit) =
        call("window.spiraSelectionInfo ? window.spiraSelectionInfo() : null", then)

    /** Collapse the selection, so the highlight goes when the menu does. */
    fun clearSelection() {
        webView?.evaluateJavascript("window.spiraClearSelection && window.spiraClearSelection()", null)
    }

    /** The selected text, read live — the user may have widened it with a handle. */
    fun withSelection(then: (String) -> Unit) {
        val web = webView ?: return then("")
        web.evaluateJavascript("window.spiraSelectedText ? window.spiraSelectedText() : ''") { raw ->
            then(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty())
        }
    }

    /** Every selection call answers with the same JSON (or null); this is the shared reading. */
    private fun call(js: String, then: (NoteSelection?) -> Unit) {
        val web = webView ?: return then(null)
        web.evaluateJavascript(js) { raw ->
            then(
                runCatching {
                    val inner = JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                    val o = JSONObject(inner)
                    val text = o.getString("text")
                    if (text.isBlank()) {
                        null
                    } else {
                        NoteSelection(
                            text = text,
                            startX = o.getDouble("sx").toFloat(),
                            startY = o.getDouble("sy").toFloat(),
                            startTop = o.getDouble("st").toFloat(),
                            endX = o.getDouble("ex").toFloat(),
                            endY = o.getDouble("ey").toFloat(),
                        )
                    }
                }.getOrNull(),
            )
        }
    }
}

/**
 * A live selection in the note body: what it covers, and where its two ends are.
 *
 * The coordinates are **CSS pixels from the WebView's top-left**, which on Android is the same
 * number as dp — the page is at `initial-scale=1` with `textZoom = 100` — so they can be used as
 * offsets in the Compose overlay without conversion. [startX]/[startY] is the bottom-left of the
 * first character, [endX]/[endY] the bottom-right of the last.
 *
 * [startTop] is the **top** of the first character's line. The menu needs it: lifted a fixed
 * distance above the baseline instead, it sat on the very words it was describing.
 */
private data class NoteSelection(
    val text: String,
    val startX: Float,
    val startY: Float,
    val startTop: Float,
    val endX: Float,
    val endY: Float,
)

@Composable
private fun NoteEditorScreen(
    initialTitle: String,
    initialHtml: String,
    onTitleCommit: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit = {},
    saveError: String? = null,
    onDismissSaveError: () -> Unit = {},
    deleteError: String? = null,
    onDismissDeleteError: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val controller = remember { NoteEditorController() }
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var state by remember { mutableStateOf(NoteEditorState()) }
    var showLinkDialog by remember { mutableStateOf(false) }
    // What is selected in the note body right now, or null. The app draws the highlight's handles
    // and its menu from this — see the note on the editor Box below.
    var selection by remember { mutableStateOf<NoteSelection?>(null) }
    // What the last toolbar action did, in words. A format painter (or a paste) that says nothing
    // is one the user cannot tell has worked — which is exactly what was reported on the web.
    var noteToast by remember { mutableStateOf<NoteToast?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun finish() {
        // Capture the very last keystrokes before leaving (continuous autosave covers the rest).
        //
        // **Wait for the read.** `withHtml` goes through `evaluateJavascript`, which is
        // asynchronous: calling `onDone()` straight after it navigated away and tore the WebView
        // down while the request was still in flight, so the callback carrying the final HTML
        // often never arrived. That is a lost edit with nothing on screen to say so — and it is
        // precisely the "my last changes weren't saved" the owner reported (see
        // `backlog/android-note-edits-can-be-lost-silently.md`). The web has no equivalent because
        // its editor writes through the optimistic store rather than reading itself on the way out.
        //
        // `once` guards the pair: whichever of the callback and the timeout comes first wins, so
        // the screen is never left open by a WebView that fails to answer, and `onDone` is never
        // called twice.
        var left = false
        fun once(block: () -> Unit) {
            if (left) return
            left = true
            block()
        }
        mainHandler.postDelayed({ once(onDone) }, FINAL_READ_TIMEOUT_MS)
        controller.withHtml { html ->
            if (html.isNotEmpty()) onBodyChange(html)
            once(onDone)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
    ) {
        // The shared resource header: the note's own name lives IN it, so the big separate title
        // field below is gone — two editors for one title is one too many.
        ResourceTopBar(
            title = title,
            onTitleCommit = {
                title = it
                onTitleCommit(it)
            },
            // There is still no "Done" — the note autosaves as it is typed, so it would imply a
            // save that had already happened. The X deletes, matching the file viewer's header.
            onBack = { finish() },
            action = {
                HeaderCircleAction(SpiraIcons.X, "Delete note") { confirmDelete = true }
            },
        )

        // The banner carries its own margin, so neither of these adds one.
        SpiraInlineBanner(message = saveError, onDismiss = onDismissSaveError)

        SpiraInlineBanner(message = deleteError, onDismiss = onDismissDeleteError)

        // Native formatting toolbar.
        NoteToolbar(
            state = state,
            onCmd = { name, arg -> controller.cmd(name, arg) },
            onLink = { showLinkDialog = true },
            onPaste = {
                // Read the clipboard NATIVELY: a WebView gives the page no usable
                // `navigator.clipboard.read()`, so the web half's approach (read the rich flavour
                // in JS) simply does nothing here. The HTML flavour is preferred so headings, bold
                // and lists survive; plain text is the fallback, and an empty clipboard says so
                // rather than looking like a dead button.
                val clip = clipboard.primaryClip
                val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                val html = item?.htmlText
                val text = item?.coerceToText(context)?.toString()
                when {
                    !html.isNullOrBlank() -> {
                        controller.cmd("insertHtml", html)
                        noteToast = NoteToast("Pasted, keeping its formatting", error = false)
                    }
                    !text.isNullOrBlank() -> {
                        controller.cmd("insertHtml", text)
                        noteToast = NoteToast("Pasted as plain text", error = false)
                    }
                    else -> noteToast = NoteToast("Nothing to paste", error = true)
                }
            },
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))

        // The editor content fills the rest. The selection handles and the selection menu are
        // drawn OVER it, so they share a Box with the WebView rather than sitting in the column.
        //
        // **One selection system, and it is the app's** (owner, 2026-08-18). What the owner met —
        // one word selected, no handles to widen it, and a platform "Select all" bubble fighting
        // the app's own Copy card — was two systems on one gesture. Re-tested on an emulator that
        // day: in a **WebView** a long press in this editor drops a caret and offers "Select all"
        // and nothing else, while the **same page in stock Chrome** selects the word and raises
        // both handles. The host is what differs, and we cannot reach it from the page — so the
        // platform's half is switched off (the long press is consumed) and the app draws all of
        // it: the word on the press, either end draggable afterwards, and one menu.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NoteEditorWebView(
                controller = controller,
                initialHtml = initialHtml,
                onHtmlChange = onBodyChange,
                onStateChange = { state = it },
                onPainterMessage = { noteToast = NoteToast(it, error = false) },
                onLongPress = { x, y -> controller.selectWordAt(x, y) { selection = it } },
                onTap = {
                    if (selection != null) {
                        selection = null
                        controller.clearSelection()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            selection?.let { sel ->
                // Read the text live rather than trusting what the long press captured: the user
                // may have widened the range with a handle since.
                fun withSelected(action: (String) -> Unit) {
                    controller.withSelection { live ->
                        val text = live.ifBlank { sel.text }
                        if (text.isNotBlank()) action(text)
                    }
                }
                fun finish() {
                    selection = null
                    controller.clearSelection()
                }

                SelectionHandles(
                    selection = sel,
                    onMove = { which, x, y ->
                        controller.moveSelectionEnd(which, x, y) { moved ->
                            if (moved != null) selection = moved
                        }
                    },
                )
                SelectionMenu(
                    selection = sel,
                    onCopy = {
                        withSelected { text ->
                            copyPlainText(context, "Note", text)
                            noteToast = NoteToast("Copied", error = false)
                        }
                        finish()
                    },
                    onCut = {
                        withSelected { text ->
                            copyPlainText(context, "Note", text)
                            // `insertHtml` with an empty string replaces the selection with
                            // nothing, which is exactly a cut.
                            controller.cmd("insertHtml", "")
                            noteToast = NoteToast("Cut", error = false)
                        }
                        selection = null
                    },
                    onPaste = {
                        val item = clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                        val html = item?.htmlText
                        val text = item?.coerceToText(context)?.toString()
                        // Replaces the selection, which is what "paste over what I picked" means.
                        when {
                            !html.isNullOrBlank() -> {
                                controller.cmd("insertHtml", html)
                                noteToast = NoteToast("Pasted, keeping its formatting", error = false)
                            }
                            !text.isNullOrBlank() -> {
                                controller.cmd("insertHtml", text)
                                noteToast = NoteToast("Pasted as plain text", error = false)
                            }
                            else -> noteToast = NoteToast("Nothing to paste", error = true)
                        }
                        selection = null
                    },
                    // The platform's own "Select all" was the only item its menu ever offered here,
                    // and losing it would be a step back — so it is on ours.
                    onSelectAll = { controller.selectAll { if (it != null) selection = it } },
                )
            }

            // The toast floats over the editor at the foot, clear of the toolbar at the top and of
            // the keyboard, which the column above already pads for.
            SpiraToast(
                message = noteToast?.text,
                kind = if (noteToast?.error == true) SpiraToastKind.Error else SpiraToastKind.Success,
                onDismiss = { noteToast = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this note?",
            message = "\"${title.ifBlank { "Untitled" }}\" will be permanently deleted. You can't undo this.",
            subject = "\"${title.ifBlank { "Untitled" }}\"",
            confirmLabel = "Yes, delete",
            cancelLabel = "No, keep it",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showLinkDialog) {
        LinkDialog(
            onConfirm = { url ->
                showLinkDialog = false
                if (url.isBlank()) controller.cmd("unlink", null) else controller.cmd("link", url)
            },
            onDismiss = { showLinkDialog = false },
        )
    }
}

@Composable
private fun LinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link") },
        text = {
            SpiraTextField(value = url, onValueChange = { url = it }, label = "https://…")
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url.trim()) }) {
                Text("Apply", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The two teardrops at the ends of a selection, and the drags that widen it.
 *
 * They exist because the WebView draws none of its own for a selection the page made (see the note
 * on the editor Box). Without them a long press could only ever take one word, which the owner
 * rightly called useless: a quotation is a sentence, not a word.
 *
 * Each handle sits under its end of the highlight, pointing up at the character it holds. A drag
 * moves **that** end only; pulling one past the other swaps them rather than emptying the range, so
 * a handle dragged the wrong way keeps selecting. The page snaps each end to a character boundary
 * and answers with where it landed, which is what moves the teardrop.
 */
@Composable
private fun BoxScope.SelectionHandles(
    selection: NoteSelection,
    onMove: (which: String, x: Float, y: Float) -> Unit,
) {
    SelectionHandle("start", selection.startX, selection.startY, onMove)
    SelectionHandle("end", selection.endX, selection.endY, onMove)
}

/** One end's teardrop. [x]/[y] are CSS pixels — the same number as dp — from the WebView corner. */
@Composable
private fun BoxScope.SelectionHandle(
    which: String,
    x: Float,
    y: Float,
    onMove: (which: String, x: Float, y: Float) -> Unit,
) {
    val density = LocalDensity.current
    // Where the finger is asking to put this end, in the page's own coordinates. Tracked separately
    // from the selection: the page snaps to characters, and re-seeding this from the snapped result
    // would drag the finger backwards a fraction of a character at a time.
    var pointX by remember { mutableStateOf(0f) }
    var pointY by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .align(Alignment.TopStart)
            // The teardrop hangs below the line and to the outside of its end, the way the
            // platform's own do — inside, the pair would cover the first and last characters.
            .offset(
                x = (if (which == "start") x - HANDLE_SIZE.value else x).dp,
                y = y.dp,
            )
            .size(HANDLE_SIZE)
            .pointerInput(which) {
                detectDragGestures(
                    onDragStart = {
                        pointX = x
                        // Aim at the middle of the line rather than at the baseline the handle sits
                        // on: a point exactly on the baseline lands between two lines as often as
                        // on either of them.
                        pointY = y - HANDLE_AIM.value
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        pointX += amount.x / density.density
                        pointY += amount.y / density.density
                        onMove(which, pointX, pointY)
                    },
                )
            }
            .semantics {
                contentDescription =
                    if (which == "start") "Selection start handle" else "Selection end handle"
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            // A circle with one square corner pointing back at the character it holds — the shape
            // Android's own selection handles use, so the gesture reads as the familiar one.
            drawCircle(HANDLE_INK, radius = r, center = Offset(r, r))
            drawPath(
                Path().apply {
                    if (which == "start") {
                        moveTo(r, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, r)
                        close()
                    } else {
                        moveTo(0f, 0f)
                        lineTo(r, 0f)
                        lineTo(0f, r)
                        close()
                    }
                },
                color = HANDLE_INK,
            )
        }
    }
}

/** The teardrop's diameter — a comfortable target without covering the words around it. */
private val HANDLE_SIZE = 22.dp

/** How far above its baseline a dragged handle aims, so it lands on the line rather than between. */
private val HANDLE_AIM = 8.dp

/** Kale, like every other thing in the app the finger can move. */
private val HANDLE_INK = Color(0xFF0A8080)

/**
 * The app's selection menu — **the only menu on this screen** (owner, 2026-08-18).
 *
 * The platform's used to appear beside it offering "Select all" and nothing else, so the two fought
 * over the same words. The long press is consumed now, and everything the platform's menu could do
 * is here: Copy, Cut, Paste over the selection, and Select all.
 *
 * The same floating white card as every other Spira menu. It sits above the selection by preference
 * and drops below it when the words are too near the top to leave room, so it never covers the line
 * it is describing.
 */
@Composable
private fun BoxScope.SelectionMenu(
    selection: NoteSelection,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val width = 296.dp
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    // **Measured, not assumed.** The menu was placed a fixed 54dp above the baseline of the
    // selection's first line, which is only ~10dp above the line itself — so it covered the words
    // it belongs to (owner, 2026-08-18). Its own height is the number that matters, and only the
    // menu knows it.
    var height by remember { mutableStateOf(0.dp) }
    val lineTop = selection.startTop.dp
    val lastBottom = maxOf(selection.startY, selection.endY).dp
    val centre = ((selection.startX + selection.endX) / 2f).dp
    // Above the selection's FIRST line by preference, clear of its top edge. When there is no room
    // up there it goes below the LAST line — and below the handles hanging off it, which would
    // otherwise be buried under the card.
    val above = lineTop - height - MENU_GAP
    Row(
        Modifier
            .align(Alignment.TopStart)
            .offset(
                x = (centre - width / 2).coerceAtLeast(8.dp),
                y = if (above >= 8.dp) above else lastBottom + HANDLE_SIZE + MENU_GAP,
            )
            .onSizeChanged { height = with(density) { it.height.toDp() } }
            .width(width)
            // On a white page the fill alone is invisible: the hairline and the shadow are what
            // make it a card.
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, MaterialTheme.spiraExtras.border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMenuAction("Copy", Modifier.weight(1f), onCopy)
        SelectionMenuDivider()
        SelectionMenuAction("Cut", Modifier.weight(1f), onCut)
        SelectionMenuDivider()
        SelectionMenuAction("Paste", Modifier.weight(1f), onPaste)
        SelectionMenuDivider()
        SelectionMenuAction("Select all", Modifier.weight(1.4f), onSelectAll)
    }
}

/** The air between the menu and the words — above the selection, or below the handles. */
private val MENU_GAP = 10.dp

@Composable
private fun SelectionMenuAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** A hairline between the menu's actions, inset so it doesn't touch the card's edges. */
@Composable
private fun SelectionMenuDivider() {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.spiraExtras.border),
    )
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun NoteEditorWebView(
    controller: NoteEditorController,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    onStateChange: (NoteEditorState) -> Unit,
    /** What the format painter just did, already worded — shown as a toast. */
    onPainterMessage: (String) -> Unit,
    /** A long press, in CSS pixels from the view's top-left: the gesture that selects a word. */
    onLongPress: (Float, Float) -> Unit,
    /** A plain touch: whatever was selected stops being selected. */
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange = rememberUpdatedState(onHtmlChange)
    val onState = rememberUpdatedState(onStateChange)
    val onPainter = rememberUpdatedState(onPainterMessage)
    val onPress = rememberUpdatedState(onLongPress)
    val onTouch = rememberUpdatedState(onTap)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val seedHtml = remember { initialHtml }
    val pageBg = MaterialTheme.colorScheme.surface.toArgb()
    val density = LocalDensity.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                setBackgroundColor(pageBg)
                isFocusable = true
                isFocusableInTouchMode = true
                // Where the finger last went down, in CSS pixels: what the long press needs to
                // find the word. The page's coordinate space is density-independent and the view's
                // is not, so the touch is converted on the way in.
                var lastX = 0f
                var lastY = 0f
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        lastX = e.x / density.density
                        lastY = e.y / density.density
                        // A touch puts the caret somewhere new, so any open selection is over.
                        onTouch.value()
                    }
                    // Take focus on a touch, so the IME opens — that is why this is a WebView at
                    // all. Nothing is consumed; the gesture goes on to the page.
                    if (e.action == MotionEvent.ACTION_UP && !v.hasFocus()) v.requestFocus()
                    false
                }
                // **Consumed.** Returning false would let Chromium put its own caret bubble up
                // beside ours — the "two menus" half of what the owner reported. Its bubble has
                // only ever offered "Select all" in this editor, and ours offers that too.
                setOnLongClickListener {
                    onPress.value(lastX, lastY)
                    true
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onChange(html: String) {
                            mainHandler.post { onChange.value(html) }
                        }

                        @JavascriptInterface
                        fun onState(json: String) {
                            val parsed = parseState(json)
                            mainHandler.post { onState.value(parsed) }
                        }

                        @JavascriptInterface
                        fun onPainter(json: String) {
                            val message = describePainter(json)
                            mainHandler.post { onPainter.value(message) }
                        }
                    },
                    "SpiraNote",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        seedWhenReady(view, seedHtml, mainHandler, attempt = 0)
                    }
                }
                controller.webView = this
                loadUrl("file:///android_asset/note-editor/index.html")
            }
        },
        onRelease = { web ->
            web.evaluateJavascript("window.spiraFlush && window.spiraFlush()", null)
            controller.webView = null
            web.destroy()
        },
    )
}

/** Poll (up to ~3s) for the editor JS to be ready, then seed the initial HTML if it's still empty. */
private fun seedWhenReady(web: WebView, html: String, handler: Handler, attempt: Int) {
    if (attempt > 30) return
    val js = """
        (function () {
          if (!window.spiraSetContent || !window.spiraGetHtml) return "wait";
          var cur = window.spiraGetHtml();
          if (cur === "" || cur === "<p></p>") window.spiraSetContent(${JSONObject.quote(html)});
          return "ok";
        })()
    """.trimIndent()
    web.evaluateJavascript(js) { result ->
        if (result == null || result.contains("wait")) {
            handler.postDelayed({ seedWhenReady(web, html, handler, attempt + 1) }, 100)
        }
    }
}

/**
 * How long leaving the editor waits for the WebView to hand back its final HTML.
 *
 * Long enough for a normal `evaluateJavascript` round trip (single-digit milliseconds), short
 * enough that a wedged page cannot strand the user on a screen they asked to leave.
 */
private const val FINAL_READ_TIMEOUT_MS = 400L

/** One toast's text and whether it reports a failure — the only two things the card varies by. */
private data class NoteToast(val text: String, val error: Boolean)

/**
 * The words for what the format painter just did, from the JSON the editor reports.
 *
 * It names the marks — "Bold and italic copied" — rather than saying "formatting copied", which is
 * the difference between a message that confirms and one that only reassures. The web's
 * `describeMarks` (`RichTextEditor.tsx`) says the same words for the same marks; keep them in step.
 */
private fun describePainter(json: String): String = runCatching {
    val o = JSONObject(json)
    val picked = o.optJSONArray("picked")
    val applied = o.optJSONArray("applied")
    val array = picked ?: applied
    val names = buildList { for (i in 0 until (array?.length() ?: 0)) add(array!!.getString(i)) }
    val words = describeMarks(names)
    // The same words the web says (`RichTextEditor.tsx`): putting an empty style down is not
    // "plain formatting applied", it is the selection's own formatting being taken off.
    if (picked != null) {
        "$words copied — select text to apply it"
    } else if (names.isEmpty()) {
        "Formatting cleared from the selection"
    } else {
        "$words applied"
    }
}.getOrDefault("Formatting copied")

/** The human word for each TipTap mark name. Mirrors the web's map exactly. */
private fun describeMarks(names: List<String>): String {
    val words = names.mapNotNull { name ->
        when (name) {
            "bold" -> "Bold"
            "italic" -> "Italic"
            "underline" -> "Underline"
            "strike" -> "Strikethrough"
            "code" -> "Code"
            "highlight" -> "Highlight"
            "textStyle" -> "Colour"
            else -> name
        }
    }
    return when {
        words.isEmpty() -> "Plain formatting"
        words.size == 1 -> words.single()
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last().lowercase()
    }
}

private fun parseState(json: String): NoteEditorState = runCatching {
    val o = JSONObject(json)
    NoteEditorState(
        bold = o.optBoolean("bold"),
        italic = o.optBoolean("italic"),
        underline = o.optBoolean("underline"),
        strike = o.optBoolean("strike"),
        highlight = o.optBoolean("highlight"),
        code = o.optBoolean("code"),
        h1 = o.optBoolean("h1"),
        h2 = o.optBoolean("h2"),
        h3 = o.optBoolean("h3"),
        bullet = o.optBoolean("bullet"),
        ordered = o.optBoolean("ordered"),
        task = o.optBoolean("task"),
        quote = o.optBoolean("quote"),
        link = o.optBoolean("link"),
        painter = o.optBoolean("painter"),
    )
}.getOrDefault(NoteEditorState())
