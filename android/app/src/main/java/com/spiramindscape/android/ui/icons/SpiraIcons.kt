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
    val ChevronRight = lucide("m9 18 6-6-6-6")
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
    val ArrowUp = lucide("M12 19V5 M5 12l7-7 7 7")
    val ArrowDown = lucide("M12 5v14 M19 12l-7 7-7-7")
    val SlidersHorizontal = lucide(
        "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4",
    )

    // "ellipsis-vertical" (kebab menu) — Lucide draws it as three tiny stroked circles; the
    // path helper here is stroke-only, so each dot is an r=1 circle arc (a 2px stroke on a 1px
    // radius reads as a solid dot, same as Lucide's own rendering).
    val MoreVertical = lucide(
        "M13 5A1 1 0 1 0 11 5A1 1 0 1 0 13 5 M13 12A1 1 0 1 0 11 12A1 1 0 1 0 13 12 " +
            "M13 19A1 1 0 1 0 11 19A1 1 0 1 0 13 19",
    )

    // "ellipsis-horizontal" — the horizontal sibling of MoreVertical (three dots across).
    val MoreHorizontal = lucide(
        "M13 12A1 1 0 1 0 11 12A1 1 0 1 0 13 12 M20 12A1 1 0 1 0 18 12A1 1 0 1 0 20 12 " +
            "M6 12A1 1 0 1 0 4 12A1 1 0 1 0 6 12",
    )

    // "star" / "star-off" — used by the Options bottom-sheet (Make active / Remove active).
    val Star = lucide(
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 " +
            ".294.904l-3.736 3.638a2.12 2.12 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 " +
            "2.12 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 " +
            "9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.12 2.12 0 0 0 1.597-1.16z",
    )
    val StarOff = lucide(
        "M8.34 8.34 2 9.03l4.62 4.5L5.53 20 12 16.6l6.47 3.4-.24-1.4 " +
            "M18.42 12.76 22 9.03l-6.34-.69L12.2 2.6l-1.66 3.4 M2 2l20 20",
    )

    // "brain" — the AI-assistant glyph in the header (from the design mockup).
    val Brain = lucide(
        "M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z " +
            "M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z " +
            "M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4 M17.599 6.5a3 3 0 0 0 .399-1.375 " +
            "M6.003 5.125A3 3 0 0 0 6.401 6.5 M3.477 10.896a4 4 0 0 1 .585-.396 " +
            "M19.938 10.5a4 4 0 0 1 .585.396 M6 18a4 4 0 0 1-1.967-.516 " +
            "M19.967 17.484A4 4 0 0 1 18 18",
    )

    // "user-round" — the profile/avatar glyph in the header (from the design mockup).
    val UserRound = lucide("M18 20a6 6 0 0 0-12 0 M12 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z")

    // Resource-type glyphs (from the Resources design mockup).
    val FileText = lucide(
        "M15 3v4a2 2 0 0 0 2 2h4 M18 17h-7 M18 13h-7 " +
            "M14 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9l6 6v10a2 2 0 0 1-2 2Z",
    )
    val Link = lucide(
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71 " +
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    )
    val File = lucide("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z M14 2v4a2 2 0 0 0 2 2h4")
    val Mail = lucide(
        "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z " +
            "M22 7l-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7",
    )
    val ExternalLink = lucide(
        "M15 3h6v6 M10 14 21 3 M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    )
    val Download = lucide("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10 12 15 17 10 M12 15V3")
    val Phone = lucide(
        "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384",
    )

    // Rich-text note toolbar glyphs.
    val Bold = lucide("M6 12h9a4 4 0 0 1 0 8H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h7a4 4 0 0 1 0 8")
    val Italic = lucide("M19 4h-9 M14 20H5 M15 4 9 20")
    val Underline = lucide("M6 4v6a6 6 0 0 0 12 0V4 M4 20h16")
    val Strikethrough = lucide("M16 4H9a3 3 0 0 0-2.83 4 M14 12a4 4 0 0 1 0 8H6 M4 12h16")
    val Highlighter = lucide(
        "m9 11-6 6v3h9l3-3 M22 12l-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4",
    )
    val Quote = lucide(
        "M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v1a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z " +
            "M4 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v1a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z",
    )
    val Code = lucide("m16 18 6-6-6-6 M8 6l-6 6 6 6")
    val List = lucide("M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01")
    val ListOrdered = lucide(
        "M10 6h11 M10 12h11 M10 18h11 M4 6h1v4 M4 10h2 M6 18H4c0-1 2-2 2-3s-1-1.5-2-1",
    )
    val ListChecks = lucide(
        "M11 6h10 M11 12h10 M11 18h10 M3 6l1.5 1.5L7 5 M3 12l1.5 1.5L7 11 M3 18l1.5 1.5L7 17",
    )
    val Maximize = lucide("M8 3H5a2 2 0 0 0-2 2v3 M21 8V5a2 2 0 0 0-2-2h-3 M3 16v3a2 2 0 0 0 2 2h3 M16 21h3a2 2 0 0 0 2-2v-3")

    val Copy = lucide(
        "M20 8H10a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2z " +
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )

    // Note-editor toolbar extras.
    val Undo = lucide("M9 14 4 9l5-5 M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5 5.5 5.5 0 0 1-5.5 5.5H11")
    val Redo = lucide("m15 14 5-5-5-5 M20 9H9.5A5.5 5.5 0 0 0 4 14.5 5.5 5.5 0 0 0 9.5 20H13")
    val Minus = lucide("M5 12h14")
    val Unlink = lucide(
        "M18.84 12.25 20 11a5 5 0 0 0-7.07-7.07L11 5 M5.17 11.75 4 13a5 5 0 0 0 7.07 7.07L13 19 " +
            "M8 8l8 8 M2 2l20 20",
    )
    val Eraser = lucide(
        "m7 21-4.3-4.3a1 1 0 0 1 0-1.4l9.6-9.6a1 1 0 0 1 1.4 0l5.6 5.6a1 1 0 0 1 0 1.4L13 21 M22 21H7 M5 11l9 9",
    )

    // ── Target card: progress lock, deadlines, tasks, attachments ────────────
    val Lock = lucide(
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z M7 11V7a5 5 0 0 1 10 0v4",
    )
    val LockOpen = lucide(
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z M7 11V7a5 5 0 0 1 9.9-1",
    )
    val Calendar = lucide(
        "M8 2v4 M16 2v4 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M3 10h18",
    )
    val CalendarPlus = lucide(
        "M8 2v4 M16 2v4 M21 13V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8 M3 10h18 " +
            "M16 19h6 M19 16v6",
    )
    val CirclePlus = lucide("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z M8 12h8 M12 8v8")
    val CircleCheck = lucide("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z m9 12 2 2 4-4")
    val Paperclip = lucide(
        "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57" +
            "a2 2 0 0 1-2.83-2.83l8.49-8.48",
    )
    val ArrowUpRight = lucide("M7 7h10v10 M7 17 17 7")
    val Info = lucide("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z M12 16v-4 M12 8h.01")
    val TriangleAlert = lucide(
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3 M12 9v4 M12 17h.01",
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
