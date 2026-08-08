package com.spiramindscape.android.ui.goals

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.net.Network
import com.spiramindscape.android.graphql.type.UpdateResourceInput
import com.spiramindscape.android.ui.components.ResourceTopBar
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.SpiraTextField
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

        fun saveBody(html: String) {
            saveScope.launch {
                runCatching {
                    repository.updateResource(resourceId, UpdateResourceInput(body = Optional.present(html)))
                }
            }
        }
        fun saveTitle(title: String) {
            saveScope.launch {
                runCatching {
                    repository.updateResource(resourceId, UpdateResourceInput(title = Optional.present(title)))
                }
            }
        }

        setContent {
            SpiraTheme {
                NoteEditorScreen(
                    initialTitle = initialTitle,
                    initialHtml = initialHtml,
                    onTitleCommit = ::saveTitle,
                    onBodyChange = ::saveBody,
                    onDone = { finish() },
                )
            }
        }
    }

    companion object {
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

    /**
     * Select the word under a point in the page and report it back.
     *
     * This exists because the WebView's own selection menu offers **only "Select all"** here: the
     * page's selection is ProseMirror's, and Chromium doesn't contribute Cut/Copy for it — so a
     * long press produced a caret and no way to copy anything (BUG-032). Rather than fight a menu
     * that isn't ours, the app selects the word itself and offers its own Copy.
     *
     * [then] receives the selected text and the selection's rectangle in **CSS pixels**, relative
     * to the WebView's top-left, or null when the press didn't land on a word.
     */
    fun selectWordAt(x: Float, y: Float, then: (SelectedWord?) -> Unit) {
        val web = webView ?: return then(null)
        // caretRangeFromPoint is the Chromium spelling; the standard caretPositionFromPoint is
        // the fallback so this keeps working if the WebView ever changes engine.
        val js = """
            (function () {
              var x = $x, y = $y, r = null;
              if (document.caretRangeFromPoint) {
                r = document.caretRangeFromPoint(x, y);
              } else if (document.caretPositionFromPoint) {
                var p = document.caretPositionFromPoint(x, y);
                if (p) { r = document.createRange(); r.setStart(p.offsetNode, p.offset); r.collapse(true); }
              }
              if (!r || r.startContainer.nodeType !== 3) return null;
              var t = r.startContainer.textContent || '', i = r.startOffset;
              var isWord = function (c) { return c && /[^\s]/.test(c); };
              var a = i, b = i;
              while (a > 0 && isWord(t[a - 1])) a--;
              while (b < t.length && isWord(t[b])) b++;
              if (a === b) return null;
              var sel = document.getSelection();
              var range = document.createRange();
              range.setStart(r.startContainer, a);
              range.setEnd(r.startContainer, b);
              sel.removeAllRanges();
              sel.addRange(range);
              // Remember where this selection started. The far end moves with the finger, so the
              // anchor has to live on the page side — a DOM node can't be handed to Kotlin.
              window.__spiraAnchor = { node: r.startContainer, offset: a };
              var box = range.getBoundingClientRect();
              // The RANGE's text, not the selection's: ProseMirror reasserts its own selection in
              // the same tick, so getSelection().toString() comes back empty here. The range is
              // ours and answers regardless.
              return JSON.stringify({
                text: range.toString(),
                left: box.left, top: box.top, right: box.right, bottom: box.bottom
              });
            })()
        """.trimIndent()
        web.evaluateJavascript(js) { raw -> then(parseWord(raw)) }
    }

    /** The JS above answers with a JSON string (or null) — the two callers share this reading. */
    private fun parseWord(raw: String?): SelectedWord? = runCatching {
        val inner = JSONTokener(raw).nextValue() as? String ?: return null
        val o = JSONObject(inner)
        val text = o.getString("text")
        if (text.isBlank()) null
        else SelectedWord(
            text = text,
            left = o.getDouble("left").toFloat(),
            top = o.getDouble("top").toFloat(),
            right = o.getDouble("right").toFloat(),
            bottom = o.getDouble("bottom").toFloat(),
        )
    }.getOrNull()

    /**
     * Grow the open selection so its far end follows the finger, and report what is covered.
     *
     * The near end is the anchor [selectWordAt] left on the page. Dragging backwards past it is
     * handled by comparing document order, so a selection can be pulled either way.
     */
    fun extendSelectionTo(x: Float, y: Float, then: (SelectedWord?) -> Unit) {
        val web = webView ?: return then(null)
        val js = """
            (function () {
              var a = window.__spiraAnchor;
              if (!a || !a.node) return null;
              var x = $x, y = $y, r = null;
              if (document.caretRangeFromPoint) {
                r = document.caretRangeFromPoint(x, y);
              } else if (document.caretPositionFromPoint) {
                var p = document.caretPositionFromPoint(x, y);
                if (p) { r = document.createRange(); r.setStart(p.offsetNode, p.offset); r.collapse(true); }
              }
              if (!r) return null;
              var range = document.createRange();
              // Whichever end comes first in the document is the start — that is what lets the
              // drag run backwards as naturally as forwards.
              var probe = document.createRange();
              probe.setStart(a.node, a.offset);
              var backwards = probe.comparePoint(r.startContainer, r.startOffset) < 0;
              if (backwards) {
                range.setStart(r.startContainer, r.startOffset);
                range.setEnd(a.node, a.offset);
              } else {
                range.setStart(a.node, a.offset);
                range.setEnd(r.startContainer, r.startOffset);
              }
              var sel = document.getSelection();
              sel.removeAllRanges();
              sel.addRange(range);
              var text = range.toString();
              if (!text) return null;
              var box = range.getBoundingClientRect();
              return JSON.stringify({
                text: text,
                left: box.left, top: box.top, right: box.right, bottom: box.bottom
              });
            })()
        """.trimIndent()
        web.evaluateJavascript(js) { raw -> then(parseWord(raw)) }
    }

    /** Whatever is selected right now — the user may have widened the word with the handles. */
    fun withSelection(then: (String) -> Unit) {
        val web = webView ?: return then("")
        web.evaluateJavascript("document.getSelection().toString()") { raw ->
            then(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty())
        }
    }
}

/** A word the user long-pressed, with where it sits so the Copy bubble can point at it. */
private data class SelectedWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Composable
private fun NoteEditorScreen(
    initialTitle: String,
    initialHtml: String,
    onTitleCommit: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val controller = remember { NoteEditorController() }
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var state by remember { mutableStateOf(NoteEditorState()) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var copyBubble by remember { mutableStateOf<BubbleAt?>(null) }

    fun finish() {
        // Capture the very last keystrokes before leaving (continuous autosave covers the rest).
        controller.withHtml { html -> if (html.isNotEmpty()) onBodyChange(html) }
        onDone()
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
            // No right-hand action: the note autosaves as it is typed, so a "Done" here fixed
            // nothing the chevron doesn't — two controls calling the same finish(), one of them
            // implying a save that had already happened.
            onBack = { finish() },
        )

        // Native formatting toolbar.
        NoteToolbar(
            state = state,
            onCmd = { name, arg -> controller.cmd(name, arg) },
            onLink = { showLinkDialog = true },
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))

        // The editor content fills the rest. The Copy bubble is drawn over it, anchored on the
        // selected word, so it has to share a Box with the WebView rather than sit in the column.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NoteEditorWebView(
                controller = controller,
                initialHtml = initialHtml,
                onHtmlChange = onBodyChange,
                onStateChange = { state = it },
                onWordSelected = { word, x, top, bottom ->
                    copyBubble = BubbleAt(x.dp, top.dp, bottom.dp, word)
                },
                onSelectionDismissed = { copyBubble = null },
                modifier = Modifier.fillMaxSize(),
            )
            copyBubble?.let { at ->
                CopySelectionBubble(
                    at = at,
                    onCopy = {
                        // Read the selection at the moment of the tap, not at long-press: the
                        // user may have widened it with the handles in between.
                        controller.withSelection { live ->
                            // Prefer whatever is selected now — the user may have widened the
                            // word with the handles — and fall back to the word we captured,
                            // because the page reasserts its own selection right after ours.
                            val text = live.ifBlank { at.word }
                            if (text.isNotBlank()) {
                                copyPlainText(context, "Note", text)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                        copyBubble = null
                    },
                )
            }
        }
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

/** Where the Copy bubble points: the middle of the word, and the band the word occupies. */
private data class BubbleAt(val x: Dp, val top: Dp, val bottom: Dp, val word: String)

/**
 * The app's own selection menu — a single **Copy**, floating over the word.
 *
 * The WebView's menu offers only "Select all" for this editor (BUG-032), so the copy is carried
 * by the app instead. It is the same white floating card as every other Spira menu, and it sits
 * just above the word, clamped so it can't run off either edge.
 */
@Composable
private fun CopySelectionBubble(at: BubbleAt, onCopy: () -> Unit) {
    val width = 104.dp
    // Squared off with a small radius rather than a pill: it reads as a menu, not as a chip.
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            // Above the word by preference, below it when the word sits too near the top to
            // leave room — otherwise the card lands on the line it is describing and hides it.
            .offset(
                x = (at.x - width / 2).coerceAtLeast(8.dp),
                y = (at.top - 50.dp).let { if (it >= 8.dp) it else at.bottom + 10.dp },
            )
            .width(width)
            // The same floating white card as every other Spira menu: on a white page the fill
            // alone is invisible, so the hairline and the shadow are what make it a card.
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, MaterialTheme.spiraExtras.border, shape)
            .clickable(onClick = onCopy)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Copy",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun NoteEditorWebView(
    controller: NoteEditorController,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    onStateChange: (NoteEditorState) -> Unit,
    onWordSelected: (String, Float, Float, Float) -> Unit,
    onSelectionDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange = rememberUpdatedState(onHtmlChange)
    val onState = rememberUpdatedState(onStateChange)
    val onWord = rememberUpdatedState(onWordSelected)
    val onDismiss = rememberUpdatedState(onSelectionDismissed)
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
                // Where the finger last went down, in CSS pixels — what the long press needs to
                // find the word. The page's coordinate space is density-independent, the view's
                // is not, so the touch has to be converted on the way in.
                var lastX = 0f
                var lastY = 0f
                // True from a long press until the finger lifts: the gesture that picks a range.
                // A word on its own is rarely what someone wants to quote, so the press only
                // OPENS the selection — dragging then grows it, and the Copy appears on release.
                var picking = false
                setOnTouchListener { v, e ->
                    val cssX = e.x / density.density
                    val cssY = e.y / density.density
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = cssX
                            lastY = cssY
                            picking = false
                            onDismiss.value()
                        }
                        MotionEvent.ACTION_MOVE -> if (picking) {
                            // Drag the far end of the selection with the finger. The near end
                            // stays where the press landed, held on the page side.
                            controller.extendSelectionTo(cssX, cssY) { }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (picking) {
                                picking = false
                                // Ask the page what ended up selected and put the Copy on it.
                                controller.extendSelectionTo(cssX, cssY) { word ->
                                    if (word != null) {
                                        onWord.value(
                                            word.text,
                                            (word.left + word.right) / 2f,
                                            word.top,
                                            word.bottom,
                                        )
                                    }
                                }
                            } else if (!v.hasFocus()) {
                                v.requestFocus()
                            }
                        }
                    }
                    // While picking, the events are ours: handing them on would let the page
                    // scroll under the finger and move the caret out from under the selection.
                    picking && e.action == MotionEvent.ACTION_MOVE
                }
                // The press opens a selection on the word under the finger — see
                // NoteEditorController.selectWordAt for why the WebView's own menu can't do this.
                setOnLongClickListener {
                    picking = true
                    controller.selectWordAt(lastX, lastY) { word ->
                        if (word != null) {
                            onWord.value(
                                word.text,
                                (word.left + word.right) / 2f,
                                word.top,
                                word.bottom,
                            )
                        }
                    }
                    // Consumed: letting it through re-opens Chromium's one-item menu on top.
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
    )
}.getOrDefault(NoteEditorState())
