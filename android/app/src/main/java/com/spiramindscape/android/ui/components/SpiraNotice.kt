package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * What happened. It decides the card's **border, its tint and its glyph** — one colour family per
 * kind — and nothing else. The **type stays near-black in every kind**, the same rule the pills
 * follow: colouring the words as well only makes them harder to read.
 */
enum class SpiraNoticeKind {
    Success, Error, Warning, Info,

    /**
     * A message **about the assistant**, in the Intelligence violet its own surfaces are drawn in
     * — the one kind whose border is a `400` step rather than a solid `900`, deliberately, so it
     * does not out-shout the panel it sits on (CLAUDE.md → Notices). The web has had it since the
     * spec was written; Android was missing it, which is why the coach's own "moving toward a
     * close" line was still a hand-rolled strip. Never a fifth way to say "success".
     */
    Ai,
}

/**
 * **The** shape every message in the app takes — a floating toast ([SpiraToast]) and a notice
 * sitting inside a block ([SpiraInlineBanner]) are the same card, and the owner's reference
 * (2026-08-18) is one picture for both. The web twin is `src/components/ui/sonner.tsx`.
 *
 *  - a **1px border in the kind's own colour**, over a **very pale tint from that same family** —
 *    the pair the owner specified (2026-08-18). Not a grey hairline on white: the border is what
 *    tells the kinds apart at a glance, and the tint is faint enough that the card still reads as
 *    white against the page;
 *  - an **8px radius** and the owner's two-layer soft shadow;
 *  - a **filled semantic glyph** on the left in the border's colour, aligned to the message's
 *    **first line** — not centred against a message that wraps;
 *  - **near-black text in every kind** — the border and the mark carry the meaning, not the type;
 *  - an **X on the right** to dismiss, so a long message is never in the way.
 *
 * [onDismiss] is optional: a notice the user cannot get rid of (a permanent "nothing matches your
 * filter" line) simply has no X rather than a dead one.
 */
@Composable
fun SpiraNoticeCard(
    message: String,
    kind: SpiraNoticeKind,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(NOTICE_RADIUS)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = NOTICE_SHADOW_AMBIENT, spotColor = NOTICE_SHADOW_SPOT)
            .clip(shape)
            .background(kind.tint())
            .border(1.dp, kind.ink(), shape)
            .padding(14.dp),
        // Top, not centre: the glyph belongs on the first line of a message that wraps.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            kind.glyph(),
            contentDescription = null,
            tint = kind.ink(),
            modifier = Modifier.size(20.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            Icon(
                SpiraIcons.X,
                contentDescription = "Dismiss",
                tint = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
    }
}

/** The owner's radius (2026-08-18): `border-radius: 8px`. */
private val NOTICE_RADIUS = 8.dp

// The owner's shadow is two layers — `0 4px 12px rgba(28,28,28,.08), 0 2px 8px rgba(28,28,28,.04)`.
// Compose paints one, so the ambient/spot pair carries the two alphas against the same #1C1C1C ink;
// letting the framework use its own black would paint a hard band round the corners instead.
private val NOTICE_SHADOW_AMBIENT = Color(0x141C1C1C)
private val NOTICE_SHADOW_SPOT = Color(0x1F1C1C1C)

/**
 * The filled semantic mark for each kind — all of Gravity's `-fill` twins (owner, 2026-08-18), so
 * the four marks read as one family rather than as three solids and an outline.
 */
fun SpiraNoticeKind.glyph(): ImageVector = when (this) {
    SpiraNoticeKind.Success -> SpiraIcons.CircleCheckFill
    SpiraNoticeKind.Error -> SpiraIcons.CircleExclamationFilled
    SpiraNoticeKind.Warning -> SpiraIcons.TriangleExclamationFill
    SpiraNoticeKind.Info -> SpiraIcons.CircleInfoFill
    SpiraNoticeKind.Ai -> SpiraIcons.Sparkles
}

/**
 * The kind's colour — its **border and its glyph**, which are the same ink (owner, 2026-08-18).
 *
 * Warning is **`warning-500` `#C99500`, a real yellow** — deliberately not the ramp's `900`
 * `#896500`, which is brown on screen and was the thing the owner rejected. It is the same yellow
 * the assistant's error turn uses, so the two agree.
 */
fun SpiraNoticeKind.ink(): Color = when (this) {
    SpiraNoticeKind.Success -> Color(0xFF0A8080)
    SpiraNoticeKind.Error -> Color(0xFFC53336)
    SpiraNoticeKind.Warning -> Color(0xFFC99500)
    SpiraNoticeKind.Info -> Color(0xFF006CC1)
    // Intelligence-400 — see the note on the enum constant.
    SpiraNoticeKind.Ai -> Color(0xFFBDAEFF)
}

/**
 * The card's fill: the **`100` step of the same family**, which is nearly white on purpose. The
 * card must still read as white on the page — the border is what carries the kind, and a fill any
 * stronger would turn a message into a block of colour.
 */
fun SpiraNoticeKind.tint(): Color = when (this) {
    // Brand-100, the palest teal.
    SpiraNoticeKind.Success -> Color(0xFFF9FDFC)
    SpiraNoticeKind.Error -> Color(0xFFFFFBFB)
    SpiraNoticeKind.Warning -> Color(0xFFFFFBF7)
    SpiraNoticeKind.Info -> Color(0xFFFDFCFF)
    // Intelligence-100.
    SpiraNoticeKind.Ai -> Color(0xFFFEFBFF)
}
