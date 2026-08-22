package com.spiramindscape.android.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The note editor's WebView must be a **child of a plain `FrameLayout`**, never the view an
 * `AndroidView` factory returns.
 *
 * This is a source-text check because the invariant is structural and invisible: both shapes
 * compile, both render identically, and the difference only shows up as garbled text under a real
 * IME. `LoggingConventionTest` on the backend guards a comparable invariant the same way.
 *
 * ## Why it matters (measured on an emulator, 2026-08-21 — BUG-041)
 *
 * Compose treats the view an `AndroidView` factory returns as its own and drives its focus and
 * layout. A WebView hosted that way makes Chromium call `ImeAdapterImpl.cancelComposition()` →
 * `InputMethodManager.restartInput()` **on every keystroke**. The IME is handed a fresh
 * `EditorInfo` each time carrying `initialSelStart=0`, so it believes the caret is at the start of
 * the note: letters come out capitalised, GBoard re-commits its composing text (typing "world"
 * yields "WWo…"), the caret jumps to the top of the note, and the reported cursor rect stays
 * `Rect(0,0-0,0)` so nothing scrolls the caret above the keyboard.
 *
 * With one `FrameLayout` in between, `initialSelStart` is correct and typing is exact. The numbers,
 * typing "hello world" after "START." on the emulator's real keyboard:
 *
 * | Host | `initialSelStart` | Result |
 * |---|---|---|
 * | plain Activity + FrameLayout | 6 | `START.hello world` |
 * | WebView returned by the factory | 0 | `WWoLDSTART.HELLO ` |
 * | FrameLayout wrapping the WebView | 6 | `START.hello world` |
 *
 * If this test fails, someone has "simplified" the wrapper away. Put it back.
 */
class NoteEditorWebViewHostTest {

    private val source: String by lazy {
        val f = File("src/main/java/com/spiramindscape/android/ui/goals/NoteEditorActivity.kt")
        assertTrue("Expected to find ${f.absolutePath}", f.exists())
        f.readText()
    }

    @Test
    fun `the AndroidView factory returns a FrameLayout, not the WebView`() {
        val factory = source.substringAfter("factory = { context ->").substringBefore("onRelease =")
        assertTrue(
            "The note editor's AndroidView factory must return a FrameLayout wrapping the WebView. " +
                "Returning the WebView itself makes Chromium restart the IME on every keystroke — " +
                "see this test's KDoc for the measurements.",
            factory.contains("FrameLayout(context).apply {") && factory.contains("addView("),
        )
        assertTrue(
            "The WebView must be built into a local and added as a child, so the factory's own " +
                "result is the FrameLayout.",
            factory.contains("val web = WebView(context).apply {"),
        )
    }

    @Test
    fun `onRelease tears down the WebView inside the wrapper`() {
        val release = source.substringAfter("onRelease = {").substringBefore("}\n    )")
        assertTrue(
            "onRelease receives the FrameLayout now, so it must reach the WebView through it and " +
                "still destroy it — otherwise the renderer process leaks for the life of the app.",
            release.contains("getChildAt(0) as WebView") && release.contains("web.destroy()"),
        )
    }
}
