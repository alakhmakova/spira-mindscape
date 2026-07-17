package com.spiramindscape.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide icons, built from their official SVG path data — the same icon set the web uses
 * (lucide-react), mirroring the spec's "lucide icons only (PATHS + Ic)" rule (no emoji, no
 * Material defaults). Icons are 24×24 stroke glyphs (stroke width 2, round caps/joins); the
 * `Icon` composable tints them. Add more here as needed (copy the `d` attribute from
 * https://lucide.dev).
 */
object SpiraIcons {
    val Plus = lucide("M5 12h14 M12 5v14")
    val X = lucide("M18 6 6 18 M6 6l12 12")
    val Trash = lucide(
        "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 " +
            "M10 11v6 M14 11v6",
    )
    val ChevronDown = lucide("m6 9 6 6 6-6")
    val ChevronUp = lucide("m18 15-6-6-6 6")
    val Menu = lucide("M4 12h16 M4 6h16 M4 18h16")
    val Check = lucide("M20 6 9 17l-5-5")
    val User = lucide("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z")
    val LogOut = lucide("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9")
    val Home = lucide("M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10")
    val ArrowLeft = lucide("M19 12H5 M12 19l-7-7 7-7")

    // Goal-workspace bottom-navigation tabs.
    val Trophy = lucide(
        "M6 9H4.5a2.5 2.5 0 0 1 0-5H6 M18 9h1.5a2.5 2.5 0 0 0 0-5H18 M4 22h16 " +
            "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22 " +
            "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22 M18 2H6v7a6 6 0 0 0 12 0V2Z",
    )
    val Eye = lucide(
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 " +
            "10.75 10.75 0 0 1-19.876 0 M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z",
    )
    val Folder = lucide(
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4" +
            "a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
    )
    val Lightbulb = lucide(
        "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5 " +
            "M9 18h6 M10 22h4",
    )
    val Target = lucide(
        "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z M18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z " +
            "M14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z",
    )
    val Search = lucide("m21 21-4.34-4.34 M11 17a6 6 0 1 0 0-12 6 6 0 0 0 0 12Z")
    val ArrowUpDown = lucide("m21 16-4 4-4-4 M17 20V4 M3 8l4-4 4 4 M7 4v16")
    val SlidersHorizontal = lucide(
        "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4",
    )
}

private fun lucide(pathData: String): ImageVector =
    ImageVector.Builder(
        name = "lucide",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = SolidColor(Color.Black), // tinted by the Icon composable at draw time
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
