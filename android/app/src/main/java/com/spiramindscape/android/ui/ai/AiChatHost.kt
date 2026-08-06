package com.spiramindscape.android.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hosts the AI panel beside the main content and lets the user **swipe** between them, which is
 * how the assistant is meant to be reached on a phone — a tap on the header icon works too, but
 * the swipe is the fast path.
 *
 * Why a sliding pane rather than a second page of a pager: the goal workspace already swipes
 * horizontally between its five tabs, and nesting pagers would make every tab swipe ambiguous.
 * Instead the panel is a layer over the content:
 *
 *  - **Open** it by dragging in from the right screen edge (a narrow grab strip, so a tab swipe
 *    that starts anywhere else is untouched), or by tapping the assistant icon.
 *  - **Close** it by dragging the panel back to the right — the panel itself has nothing that
 *    scrolls horizontally, so the whole surface is a safe place to grab.
 *
 * A drag past a third of the width (or a decisive flick) settles the way it was heading; anything
 * less springs back, so a half-swipe never leaves the user in between.
 */
@Composable
fun AiChatHost(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    panel: @Composable (onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // While a finger is down the offset follows it directly; animation only settles the release.
    var dragging by remember { mutableStateOf(false) }

    // `offset` is how far the panel is pulled in from the right: 0 = hidden, width = fully open.
    LaunchedEffect(open, widthPx) {
        if (widthPx <= 0f) return@LaunchedEffect
        if (!dragging) offset.animateTo(if (open) widthPx else 0f, tween(260))
    }

    fun settle(velocity: Float) {
        dragging = false
        val shouldOpen = when {
            velocity < -FLING_VELOCITY -> true // flicked leftwards → pull it in
            velocity > FLING_VELOCITY -> false // flicked rightwards → push it away
            else -> offset.value > widthPx * SETTLE_FRACTION
        }
        scope.launch {
            offset.animateTo(if (shouldOpen) widthPx else 0f, tween(220))
            if (shouldOpen != open) onOpenChange(shouldOpen)
        }
    }

    val dragState = rememberDraggableState { delta ->
        dragging = true
        // Dragging left (negative delta) pulls the panel in.
        scope.launch { offset.snapTo((offset.value - delta).coerceIn(0f, widthPx)) }
    }

    Box(modifier.fillMaxSize().onSizeChanged { widthPx = it.width.toFloat() }) {
        content()

        val visible = offset.value > 0f

        // A scrim over the content while the panel is out, so the two layers read apart. It also
        // takes the tap that dismisses the panel.
        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Color.Black.copy(alpha = 0.32f * (offset.value / widthPx)))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            scope.launch {
                                offset.animateTo(0f, tween(200))
                                onOpenChange(false)
                            }
                        }
                    },
            )
        }

        // The grab strip along the right edge: the only place a drag can START the panel, so tab
        // swiping elsewhere on the screen keeps working exactly as before.
        if (!visible) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(EDGE_GRAB_WIDTH)
                    .zIndex(2f)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
            )
        }

        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .offset { IntOffset((widthPx - offset.value).roundToInt(), 0) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
            ) {
                panel {
                    scope.launch {
                        offset.animateTo(0f, tween(220))
                        onOpenChange(false)
                    }
                }
            }
        }
    }
}

/** How wide the right-edge strip that can pull the panel in is. */
private val EDGE_GRAB_WIDTH = 24.dp

/** Past this fraction of the width, a released drag settles open rather than springing back. */
private const val SETTLE_FRACTION = 0.33f

/** A flick faster than this decides the direction regardless of how far it got. */
private const val FLING_VELOCITY = 600f
