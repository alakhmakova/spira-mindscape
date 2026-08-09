package com.spiramindscape.android.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hosts the AI panel as a **drawer** pulled up from the bottom — the same shape the web gives it on
 * a phone (`Drawer` at `h-[88vh]`): it stops short of the top edge, so the page it belongs to stays
 * visible behind a scrim and the assistant never reads as a screen of its own.
 *
 * The assistant used to slide in **sideways** from the right edge, which fought with the
 * workspace's horizontal tab swiping. Now the workspace swipes horizontally between GROW phases
 * and the assistant comes **up** — two axes, no ambiguity:
 *
 *  - **Open** by tapping the footer's AI mark, or by swiping up anywhere on that footer
 *    ([content] receives the gesture modifier to attach there).
 *  - **Close** by swiping the drawer **down**, by tapping the page showing above it, with the
 *    system back gesture, or with the panel's own close button.
 *
 * A drag past a third of the drawer's height (or a decisive flick) settles the way it was heading;
 * anything less springs back, so a half-swipe never leaves the user in between.
 */
@Composable
fun AiChatHost(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    panel: @Composable (onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (swipeUpGesture: Modifier) -> Unit,
) {
    // The drawer's own height — a fraction of the screen, so the page shows above it.
    var screenHeightPx by remember { mutableFloatStateOf(0f) }
    val heightPx = screenHeightPx * DRAWER_HEIGHT_FRACTION
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // While a finger is down the offset follows it directly; animation only settles the release.
    var dragging by remember { mutableStateOf(false) }

    // `offset` is how far the panel is pulled up from the bottom: 0 = hidden, height = fully open.
    LaunchedEffect(open, heightPx) {
        if (heightPx <= 0f) return@LaunchedEffect
        if (!dragging) offset.animateTo(if (open) heightPx else 0f, tween(260))
    }

    fun settle(velocity: Float) {
        dragging = false
        val shouldOpen = when {
            velocity < -FLING_VELOCITY -> true // flicked upwards → pull it up
            velocity > FLING_VELOCITY -> false // flicked downwards → push it away
            else -> offset.value > heightPx * SETTLE_FRACTION
        }
        scope.launch {
            offset.animateTo(if (shouldOpen) heightPx else 0f, tween(220))
            if (shouldOpen != open) onOpenChange(shouldOpen)
        }
    }

    fun close() {
        scope.launch {
            offset.animateTo(0f, tween(220))
            onOpenChange(false)
        }
    }

    val dragState = rememberDraggableState { delta ->
        dragging = true
        // Dragging up (negative delta) pulls the panel in.
        scope.launch { offset.snapTo((offset.value - delta).coerceIn(0f, heightPx)) }
    }

    val swipeUpGesture = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> settle(velocity) },
    )

    Box(modifier.fillMaxSize().onSizeChanged { screenHeightPx = it.height.toFloat() }) {
        content(swipeUpGesture)

        val visible = offset.value > 0f

        if (visible) {
            // Back closes the assistant before it leaves the goal — otherwise a back gesture with
            // the panel up would pop the whole screen out from under it.
            BackHandler { close() }

            // The page above the drawer dims as it comes up, and a tap there puts it away.
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA * (offset.value / heightPx)))
                    .pointerInput(Unit) { detectTapGestures { close() } },
            )

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(DRAWER_HEIGHT_FRACTION)
                    .zIndex(3f)
                    .offset { IntOffset(0, (heightPx - offset.value).roundToInt()) }
                    .clip(RoundedCornerShape(topStart = DRAWER_CORNER, topEnd = DRAWER_CORNER))
                    // The whole drawer is draggable downwards: it holds nothing that scrolls
                    // sideways, and the message list scrolls vertically *inside* it, so the
                    // gesture is only ambiguous at the very top — which is where the handle is.
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
            ) {
                panel { close() }
                // The grab handle: a short bar centred on the drawer's top edge, the usual signal
                // that a sheet can be pulled down.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/** How much of the screen the drawer covers — the web's `h-[88vh]`, so the page shows above it. */
private const val DRAWER_HEIGHT_FRACTION = 0.88f

/** The rounded top edge that says "sheet", not "screen". */
private val DRAWER_CORNER = 20.dp

/** How dark the page behind the drawer goes when it is fully up. */
private const val SCRIM_ALPHA = 0.32f

/** Past this fraction of the height, a released drag settles open rather than springing back. */
private const val SETTLE_FRACTION = 0.33f

/** A flick faster than this decides the direction regardless of how far it got. */
private const val FLING_VELOCITY = 600f
