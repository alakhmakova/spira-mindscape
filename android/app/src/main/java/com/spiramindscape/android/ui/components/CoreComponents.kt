package com.spiramindscape.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras
import kotlin.math.roundToInt

/**
 * A white, softly-bordered card — the raised surface used throughout (mirrors the web card).
 * [overlay] draws on top of the card content, clipped to the card's rounded shape — used for
 * corner accents like the Options tab's "active" check ribbon.
 */
@Composable
fun SpiraCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    borderColor: Color = MaterialTheme.spiraExtras.border,
    borderWidth: Dp = 1.dp,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Dp = 0.dp,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.spiraExtras.surfaceRaised),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(contentPadding)) { content() }
            overlay?.invoke(this)
        }
    }
}

/**
 * Collapsible titled card (mirrors the web `Section.tsx`): a header row with a title, optional
 * count pill and action slot, and expandable content.
 */
@Composable
fun SpiraSection(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    initiallyExpanded: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    SpiraCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                CountPill(count)
                Spacer(Modifier.size(4.dp))
            }
            if (action != null) action()
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) SpiraIcons.ChevronUp else SpiraIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.spiraExtras.mutedForeground,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/** A small rounded count badge in the primary-soft tone. */
@Composable
fun CountPill(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.spiraExtras.primarySoft,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Small uppercase teal label for a sub-section heading (mirrors the web section labels). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Muted one-liner for an empty list/section. */
@Composable
fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.spiraExtras.mutedForeground,
    )
}

/** Circular progress ring with the percentage in the center (used on goal cards). */
@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 5.dp,
) {
    val p = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.spiraExtras.surfaceSunken
    // Orange while in progress, the app's primary (teal) once complete.
    val fill = if (p >= 1f) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFFEA580C)
    Box(Modifier.size(size).then(modifier), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.toPx() - sw, size.toPx() - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
            )
            drawArc(
                color = fill, startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Text(
            "${(p * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fill,
        )
    }
}

/** Themed linear progress bar (teal fill on a sunken track). */
@Composable
fun SpiraLinearProgress(progress: Float, modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.spiraExtras.surfaceSunken,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
}

/** Centered empty state for a whole screen. */
@Composable
fun EmptyState(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}
