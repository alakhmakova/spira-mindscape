package com.spiramindscape.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
/** Every pill is this tall, so a row of them lines up whatever words they carry. */
private val PILL_HEIGHT = 30.dp

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
    // **Centred by layout, not by padding.** Two earlier attempts tuned vertical padding by eye —
    // 7/3 pushed the word low, symmetric pushed it high — because padding cannot centre a glyph
    // inside a line box whose ascent and descent are not symmetric. A fixed height with centred
    // content lets the layout do it, and it comes out right for any font the Fonts tab picks
    // (owner, 2026-08-17).
    Row(
        modifier
            .height(PILL_HEIGHT)
            .clip(CircleShape)
            .background(tone.fill)
            .border(1.dp, tone.outline, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone.outline, modifier = iconModifier.size(13.dp))
        }
        Text(
            label,
            // Trimmed metrics **and** centred in the fixed height above: the trim removes the
            // leading the word does not use, the layout centres what is left.
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
