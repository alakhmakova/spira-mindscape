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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.net.Network
import com.spiramindscape.android.graphql.type.UpdateResourceInput
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
}

@Composable
private fun NoteEditorScreen(
    initialTitle: String,
    initialHtml: String,
    onTitleCommit: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val controller = remember { NoteEditorController() }
    var title by remember { mutableStateOf(initialTitle) }
    var state by remember { mutableStateOf(NoteEditorState()) }
    var showLinkDialog by remember { mutableStateOf(false) }

    fun finish() {
        // Capture the very last keystrokes before leaving (continuous autosave covers the rest).
        controller.withHtml { html -> if (html.isNotEmpty()) onBodyChange(html) }
        onDone()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
    ) {
        // Top bar: back + Done.
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpiraIcons.ArrowLeft, "Back", tint = MaterialTheme.spiraExtras.mutedForeground)
            }
            Text(
                "Note",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(onClick = { finish() }) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        // Editable title (inline, no box — matches the goal-page inline pattern).
        InlineEditText(
            value = title,
            onCommit = {
                title = it
                onTitleCommit(it)
            },
            required = true,
            placeholder = "Untitled note",
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        )

        // Native formatting toolbar.
        NoteToolbar(
            state = state,
            onCmd = { name, arg -> controller.cmd(name, arg) },
            onLink = { showLinkDialog = true },
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.spiraExtras.border))

        // The editor content fills the rest.
        NoteEditorWebView(
            controller = controller,
            initialHtml = initialHtml,
            onHtmlChange = onBodyChange,
            onStateChange = { state = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
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

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun NoteEditorWebView(
    controller: NoteEditorController,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    onStateChange: (NoteEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange = rememberUpdatedState(onHtmlChange)
    val onState = rememberUpdatedState(onStateChange)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val seedHtml = remember { initialHtml }
    val pageBg = MaterialTheme.colorScheme.surface.toArgb()

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
                // A tap should hand keyboard focus to the WebView so the caret + IME appear.
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_UP && !v.hasFocus()) v.requestFocus()
                    false
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
