package com.spiramindscape.android.debug

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * **Debug builds only.** A bisection harness for the note editor's IME defect (BUG-041).
 *
 * The editor's WebView is hosted in a Compose tree, wrapped in touch and long-click listeners, and
 * carries a Java bridge. Any of those could be what stops Chromium reporting its selection to the
 * IME — and measuring one hypothesis at a time is the only way to tell, because the symptom (the
 * IME is told the caret is at 0) is identical whichever layer causes it.
 *
 * Each mode adds exactly one thing to the one before it:
 *
 * | `-e mode` | What hosts the WebView |
 * |---|---|
 * | `bare`    | a plain `Activity` and a `FrameLayout` — no Compose at all |
 * | `compose` | the same WebView inside Compose's `AndroidView` |
 * | `column`  | …inside a `Column` with `imePadding()`, as the real screen has it |
 * | `touch`   | …plus the real screen's touch and long-click listeners |
 *
 * Launch:
 *     adb shell "am start -n com.spiramindscape.android/.debug.NoteEditorProbeActivity -e mode bare"
 *
 * Then seed and type exactly as against the real editor. The measurement that matters is
 * `adb shell dumpsys input_method | grep initialSelStart` — 6/6 is correct for "START.", 0/0 is the
 * defect.
 */
private const val PROBE = "SpiraProbe"

class NoteEditorProbeActivity : ComponentActivity() {

    /**
     * A WebView that says when the IME asked it for an input connection, and what selection it
     * handed over at that moment.
     *
     * That is the measurement the whole bisection turns on: `initialSelStart` is filled from
     * Chromium's cached selection, so a connection created before the page has a caret carries 0 —
     * and if nothing ever asks again, the IME keeps believing it.
     */
    private inner class ProbeWebView(context: Context) : WebView(context) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val ic = super.onCreateInputConnection(outAttrs)
            Log.i(PROBE, "onCreateInputConnection sel=" + outAttrs.initialSelStart + "/" + outAttrs.initialSelEnd + " ic=" + (ic != null))
            // WHO asked. `InputMethodManager.restartInput()` calls `startInputInner` synchronously,
            // so the caller is still on the stack here — which is the one thing that names the
            // layer responsible rather than leaving it to be guessed.
            Log.i(PROBE, Log.getStackTraceString(Throwable("who asked")))
            return ic
        }

        var swallowRequestLayout = false

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            Log.i(PROBE, "onLayout changed=" + changed + " " + l + "," + t + "-" + r + "," + b)
        }

        override fun requestLayout() {
            if (swallowRequestLayout) { Log.i(PROBE, "requestLayout SWALLOWED"); return }
            super.requestLayout()
        }

        override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect)
            Log.i(PROBE, "onFocusChanged focused=" + focused)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun makeWebView(withListeners: Boolean): WebView =
        ProbeWebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.textZoom = 100
            isFocusable = true
            isFocusableInTouchMode = true
            if (withListeners) {
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_UP && !v.hasFocus()) v.requestFocus()
                    false
                }
                setOnLongClickListener { true }
            }
            loadUrl("file:///android_asset/note-editor/index.html")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        when (intent.getStringExtra("mode") ?: "bare") {
            "bare" -> {
                val root = FrameLayout(this)
                root.addView(
                    makeWebView(withListeners = false),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                setContentView(root)
            }
            "compose" -> setContent {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { makeWebView(false) })
            }
            "column" -> setContent {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Text("probe: column", Modifier.fillMaxWidth())
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { makeWebView(false) })
                }
            }
            "nolayout" -> setContent {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        (makeWebView(false) as ProbeWebView).also { w ->
                            // Let the first layout through, then stop the WebView asking Compose to
                            // measure it again. Diagnostic only: it tests whether the restartInput
                            // storm is driven by the WebView's own requestLayout bouncing back as a
                            // re-layout of the focused editable.
                            w.postDelayed({ w.swallowRequestLayout = true }, 2500)
                        }
                    },
                )
            }
            "wrap" -> setContent {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            addView(
                                makeWebView(false),
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    },
                )
            }
            "restart" -> setContent {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Text("probe: restart", Modifier.fillMaxWidth())
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            makeWebView(false).apply {
                                setOnTouchListener { v, e ->
                                    if (e.action == MotionEvent.ACTION_UP) {
                                        // After the tap has placed the caret, make the IME throw
                                        // away what it thinks it knows and ask again.
                                        v.postDelayed({
                                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.restartInput(v)
                                            Log.i(PROBE, "restartInput()")
                                        }, 250)
                                    }
                                    false
                                }
                            }
                        },
                    )
                }
            }
            "touch" -> setContent {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Text("probe: touch", Modifier.fillMaxWidth())
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { makeWebView(true) })
                }
            }
        }
    }
}
