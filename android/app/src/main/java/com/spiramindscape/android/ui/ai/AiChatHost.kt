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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hosts the AI panel as a **drawer** pulled up from the bottom — the same shape the web gives it on
 * a phone (`Drawer` at `sheet-h-92`): it stops short of the top edge, so the page it belongs to stays
 * visible behind a scrim and the assistant never reads as a screen of its own.
 *
 * Where it stops is [drawerTopEdgePx]: **below the page's header**, not at a fraction of the
 * screen. Read that first — the fraction is what was there before and it covered the header.
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
    // The drawer's own height — see [drawerTopEdgePx]: it starts below the page's header.
    var screenHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.systemBars.getTop(density).toFloat()
    val clearancePx = with(density) { HEADER_CLEARANCE.toPx() }
    val heightPx = screenHeightPx - drawerTopEdgePx(screenHeightPx, statusBarPx, clearancePx)
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
                    .height(with(density) { heightPx.toDp() })
                    .zIndex(3f)
                    .testTag(AI_DRAWER_TAG)
                    .offset { IntOffset(0, (heightPx - offset.value).roundToInt()) }
                    .clip(RoundedCornerShape(topStart = DRAWER_CORNER, topEnd = DRAWER_CORNER))
                    // Painted here as well as by the panel: the corners are clipped from THIS box,
                    // so anything the panel doesn't cover would show the page through the curve.
                    .background(PANEL_CHROME)
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

/** So a test can measure where the drawer's top edge actually landed. */
const val AI_DRAWER_TAG = "ai-drawer"

/**
 * Where the drawer's top edge sits, in px from the top of the screen.
 *
 * **The rule is "below the page's header", not "a fraction of the screen"** (owner, 2026-08-29:
 * "на андроид drawer стал слишком высоким, он не должен перекрывать хедер"). A fraction was what
 * it used to be — 0.92 — and the trouble was what it was a fraction OF: `MainActivity` runs
 * `enableEdgeToEdge()`, so the box being measured is the **whole screen, status bar included**.
 * On the owner's phone the remaining 8 % is about the height of the status bar alone, so the
 * drawer's top edge landed inside `GoalWorkspaceTopBar` and covered it.
 *
 * That is also why it looked right on the web and wrong here: there `--app-vh` is one percent of
 * the **layout viewport**, which Chrome has already trimmed of the status bar and its own
 * toolbar, so the same 92 % starts from a lower ceiling. Copying the number across without
 * copying what it measured is the whole bug.
 *
 * So the top edge is stated directly instead: the status bar, then [HEADER_CLEARANCE]. The
 * fraction survives only as a second constraint, for a screen short enough that the clearance
 * would leave a stub of a drawer — there the proportion is the safer of the two.
 */
internal fun drawerTopEdgePx(
    screenHeightPx: Float,
    statusBarPx: Float,
    clearancePx: Float,
): Float = maxOf(
    statusBarPx + clearancePx,
    screenHeightPx * (1f - DRAWER_HEIGHT_FRACTION),
).coerceIn(0f, screenHeightPx)

/**
 * The band below the status bar that the drawer must not cover — the page's own header.
 *
 * It is the goal workspace's chrome, which is the taller of the two screens that host the
 * assistant: `GoalWorkspaceTopBar` is a fixed **64dp** under its status-bar padding, `GrowTabsRow`
 * measures about **46dp**, and the remainder is a deliberate gap so the page reads as being
 * *behind* the sheet rather than exactly abutting it. The dashboard's header is shorter, and gets
 * the same drawer on purpose: the assistant should not change height depending on which screen
 * called it.
 *
 * `AiDrawerClearsTheHeaderTest` renders the real chrome against the real drawer and fails if this
 * number stops clearing it, so the chrome cannot grow past it unnoticed.
 */
private val HEADER_CLEARANCE: Dp = 122.dp

/**
 * The most of the screen the drawer may take when [HEADER_CLEARANCE] would leave too little —
 * 0.92, the web's chat drawer (owner, 2026-08-28: it "должен занимать почти всю высоту … как и
 * другие drawer"). On an ordinary phone the clearance is what binds.
 */
private const val DRAWER_HEIGHT_FRACTION = 0.92f

/** The rounded top edge that says "sheet", not "screen". */
private val DRAWER_CORNER = 20.dp

/** How dark the page behind the drawer goes when it is fully up. */
private const val SCRIM_ALPHA = 0.32f

/** Past this fraction of the height, a released drag settles open rather than springing back. */
private const val SETTLE_FRACTION = 0.33f

/** A flick faster than this decides the direction regardless of how far it got. */
private const val FLING_VELOCITY = 600f
