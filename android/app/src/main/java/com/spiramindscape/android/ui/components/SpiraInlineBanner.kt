package com.spiramindscape.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Error200

/**
 * An inline error banner for an action that failed without changing the screen.
 *
 * It exists because a failed delete or create previously produced nothing at all — no
 * navigation, no message, no log — which is indistinguishable from a dead button. It sits
 * above the content rather than replacing it, so the user keeps what they were looking at.
 *
 * Themed from the semantic `error` ramp (Guava is the brand accent and must never double as
 * a danger signal), on a light tint rather than a fill, with the same 1dp hairline and
 * rounding as the rest of the kit.
 *
 * @param message the failure to show, or null to hide the banner
 * @param onDismiss clears the message
 */
@Composable
fun SpiraInlineBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = Error200,
            contentColor = MaterialTheme.colorScheme.error,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // Announced by TalkBack the moment it appears — the whole point is that the
                // failure is not silent.
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            ) {
                Icon(
                    imageVector = SpiraIcons.TriangleAlert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = SpiraIcons.X,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
