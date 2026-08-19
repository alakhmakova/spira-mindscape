package com.spiramindscape.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.spiramindscape.android.ui.icons.SpiraIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "it went on the clipboard" flash: **every copy button in the app turns into
 * `copy-check` for a couple of seconds and then turns back** (owner, 2026-08-18).
 *
 * Copying is the one action with nothing to show for it — the clipboard is invisible, the screen
 * does not change, and on Android 13+ the system's own paste preview is easy to miss. So the mark
 * you pressed answers for it. It reverts on its own rather than latching: a button stuck on a tick
 * stops being a report about the press that just happened and becomes part of the furniture, which
 * is what the AI panel's copy row used to do.
 *
 * Usage — draw [icon], and call [fire] from the same `onClick` that does the copying:
 *
 * ```kotlin
 * val flash = rememberCopyFlash()
 * ResourceIconButton(flash.icon, "Copy link") {
 *     copyPlainText(context, "Link", url)
 *     flash.fire()
 * }
 * ```
 */
@Stable
class CopyFlash internal constructor(
    private val scope: CoroutineScope,
    private val durationMs: Long,
) {
    /** True while the tick is showing. */
    var copied by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** The mark to draw right now — the tick while [copied], the plain copy glyph otherwise. */
    val icon: ImageVector get() = if (copied) SpiraIcons.CopyCheck else SpiraIcons.Copy

    /**
     * Show the tick, and take it away again after the flash.
     *
     * Pressing again restarts the clock rather than stacking a second timer, so a double tap does
     * not leave the tick up for twice as long.
     */
    fun fire() {
        copied = true
        job?.cancel()
        job = scope.launch {
            delay(durationMs)
            copied = false
        }
    }
}

/** How long the tick stays up: long enough to read, short enough to be about *this* press. */
private const val COPY_FLASH_MS = 2_000L

@Composable
fun rememberCopyFlash(durationMs: Long = COPY_FLASH_MS): CopyFlash {
    val scope = rememberCoroutineScope()
    return remember(durationMs) { CopyFlash(scope, durationMs) }
}
