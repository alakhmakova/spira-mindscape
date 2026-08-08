package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.theme.Brand100
import com.spiramindscape.android.ui.theme.Error100
import com.spiramindscape.android.ui.theme.Error900
import com.spiramindscape.android.ui.theme.Info100
import com.spiramindscape.android.ui.theme.Info900
import com.spiramindscape.android.ui.theme.Intelligence100
import com.spiramindscape.android.ui.theme.Intelligence900
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.Neutral100
import com.spiramindscape.android.ui.theme.Neutral1200
import com.spiramindscape.android.ui.theme.Salt1000
import com.spiramindscape.android.ui.theme.Success100
import com.spiramindscape.android.ui.theme.Success900
import com.spiramindscape.android.ui.theme.Warning100
import com.spiramindscape.android.ui.theme.Warning900

/**
 * The one status badge shape in the app: a **pill** with a bright 1px outline and a very pale fill
 * tinted to match it, carrying a word and no icon.
 *
 * Each tone is a pair from one ramp — the **100** step as the fill (nearly white on purpose) and
 * the ramp's solid step as the outline. **The word itself stays near-black in every tone**: the
 * outline is what carries the meaning, and colouring the type as well made the label harder to
 * read for no gain. So a row of badges reads as one family in different meanings rather than as
 * blocks of tint.
 *
 * Labels are written in **sentence case** — never in capitals. An all-caps pill shouts, and in a
 * column of proposal cards a dozen of them shout at once. The one exception is `GROW`, which is an
 * acronym and is capitalised wherever it appears.
 */
enum class SpiraBadgeTone(val outline: Color, val fill: Color) {
    /** The brand default — an active choice, a current selection. */
    Teal(Kale500, Brand100),

    /** Anything that belongs to the assistant. */
    Intelligence(Intelligence900, Intelligence100),

    /** A good state: connected, done, on track. */
    Success(Success900, Success100),

    /** Something approaching: a deadline in view, a key missing. */
    Warning(Warning900, Warning100),

    /** Something wrong: overdue, failed, refused. */
    Error(Error900, Error100),

    /** Informational — a kind, a category, a neutral fact worth naming. */
    Info(Info900, Info100),

    /** No colour to carry — a plain, quiet label. */
    Neutral(Neutral1200, Neutral100),
}

@Composable
fun SpiraBadge(
    label: String,
    tone: SpiraBadgeTone = SpiraBadgeTone.Teal,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /**
     * Applied to the icon alone. Some marks aren't centred inside their own box — the assistant's
     * two-star sparkle hangs its small companion low — so the caller can nudge one onto the
     * label's optical centre without moving every badge.
     */
    iconModifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.fill)
            .border(1.dp, tone.outline, CircleShape)
            // Deliberately uneven: GCentra's ascent carries the capitals but its descent sits
            // empty under a word like "Active", so a symmetric pill leaves the label visibly high.
            // The extra 2dp on top drops the word onto the pill's optical centre; the total is
            // unchanged, so the pill keeps its height.
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone.outline, modifier = iconModifier.size(13.dp))
        }
        Text(
            label,
            // Trimmed metrics, not the scale's default: the type scale centres glyphs inside the
            // *line box*, which is the right rule for text sitting beside icons but leaves a short
            // label riding high in a pill — the line box reserves descender room the word may not
            // use. Trimming the leading centres the word against the outline instead.
            style = MaterialTheme.typography.labelMedium.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            fontWeight = FontWeight.SemiBold,
            color = Salt1000,
        )
    }
}
