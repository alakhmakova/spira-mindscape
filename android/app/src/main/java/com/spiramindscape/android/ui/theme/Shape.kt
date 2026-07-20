package com.spiramindscape.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radius scale mirroring the web (`--radius: 0.5rem` = 8px base, with ±steps in styles.css).
object SpiraRadii {
    val sm = 6.dp   // radius - 2px
    val md = 8.dp   // radius (base)
    val lg = 12.dp  // radius + 4px
    val xl = 16.dp  // radius + 8px
}

val SpiraShapes = Shapes(
    extraSmall = RoundedCornerShape(SpiraRadii.sm),
    small = RoundedCornerShape(SpiraRadii.sm),
    medium = RoundedCornerShape(SpiraRadii.md),
    large = RoundedCornerShape(SpiraRadii.lg),
    extraLarge = RoundedCornerShape(SpiraRadii.xl),
)
