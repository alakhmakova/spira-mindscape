package com.spiramindscape.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A notice sitting **inside a block** — an action that failed without changing the screen, a filter
 * that hid everything, a fact the page needs to say.
 *
 * It is the **same card as the toast** ([SpiraNoticeCard]), because the owner's reference for both
 * is one picture (2026-08-18): a white card, a hairline, a filled semantic glyph on the left in the
 * ramp's solid step, near-black type, an X on the right. Only the glyph and its ink change with
 * [kind]. It used to be a red tint-filled block with red type, which made an error shout twice and
 * left the app with two different shapes for one idea.
 *
 * It sits above the content rather than replacing it, so the user keeps what they were looking at.
 * The original reason it exists: a failed delete or create produced nothing at all — no navigation,
 * no message, no log — which is indistinguishable from a dead button.
 *
 * @param message the message to show, or null to hide the banner
 * @param onDismiss clears the message; pass null for a notice the user cannot dismiss (a "nothing
 *   matches your filter" line stands until the filter changes, so an X on it would be a lie)
 */
@Composable
fun SpiraInlineBanner(
    message: String?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    kind: SpiraNoticeKind = SpiraNoticeKind.Error,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        SpiraNoticeCard(
            message = message.orEmpty(),
            kind = kind,
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // Announced by TalkBack the moment it appears — the whole point is that the
                // message is not silent.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
