package com.spiramindscape.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.spiramindscape.android.ui.theme.Ginger200
import com.spiramindscape.android.ui.theme.Guava500
import com.spiramindscape.android.ui.theme.Guava600
import com.spiramindscape.android.ui.theme.Kale300
import com.spiramindscape.android.ui.theme.Kale500
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A short confetti burst, fired when a target is completed — the Android twin of the web
 * `src/lib/spira/celebrate.ts`.
 *
 * Deliberately dependency-free: a few dozen dots on one Canvas, driven by a single animation and
 * removed when it finishes, so nothing lingers and nothing is added to the app. Colours come from
 * the brand palette (Guava, Kale, Ginger).
 */
private val CONFETTI_COLOURS = listOf(Guava500, Guava600, Kale500, Kale300, Ginger200)

private const val PIECES = 44
private const val DURATION_MS = 1800

private class ConfettiPiece(random: Random) {
    val angle = random.nextFloat() * 2f * Math.PI.toFloat()
    val distance = 120f + random.nextFloat() * 260f
    val size = 6f + random.nextFloat() * 6f
    val flat = random.nextBoolean()
    val round = random.nextBoolean()
    val spin = random.nextFloat() * 720f - 360f
    val speed = 0.6f + random.nextFloat() * 0.4f
    val colour = CONFETTI_COLOURS[random.nextInt(CONFETTI_COLOURS.size)]
}

/**
 * Draws a burst whenever [achievedCount] goes up — never on first composition, so opening a goal
 * that already has finished targets doesn't throw confetti at the user.
 *
 * Respects the system "remove animations" setting (the counterpart of the web's
 * `prefers-reduced-motion`): with animations switched off, nothing is drawn at all.
 */
@Composable
fun CelebrationOverlay(achievedCount: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val animationsOn = remember(context) {
        inspecting || runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }

    var previous by remember { mutableStateOf<Int?>(null) }
    var pieces by remember { mutableStateOf<List<ConfettiPiece>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(achievedCount) {
        val last = previous
        previous = achievedCount
        if (last == null || achievedCount <= last || !animationsOn) return@LaunchedEffect
        pieces = List(PIECES) { ConfettiPiece(Random.Default) }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(DURATION_MS, easing = CubicBezierEasing(0.16f, 0.68f, 0.4f, 1f)),
        )
        pieces = emptyList()
    }

    if (pieces.isEmpty()) return

    Canvas(modifier.fillMaxSize()) {
        val originX = size.width / 2f
        val originY = size.height * 0.35f
        pieces.forEach { piece ->
            // Each piece runs at its own pace, so the burst thins out rather than vanishing at once.
            val t = (progress.value / piece.speed).coerceIn(0f, 1f)
            val x = originX + cos(piece.angle) * piece.distance * t
            val y = originY + sin(piece.angle) * piece.distance * t + 220f * t
            val alpha = 1f - t
            val width = piece.size
            val height = if (piece.flat) piece.size * 0.5f else piece.size
            withTransform({ rotate(piece.spin * t, Offset(x, y)) }) {
                if (piece.round) {
                    drawCircle(piece.colour, radius = width / 2f, center = Offset(x, y), alpha = alpha)
                } else {
                    drawRect(
                        piece.colour,
                        topLeft = Offset(x - width / 2f, y - height / 2f),
                        size = Size(width, height),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}
