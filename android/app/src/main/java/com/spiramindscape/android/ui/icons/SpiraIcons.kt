package com.spiramindscape.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
    val Camera = lucide(
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z " +
            "M12 16a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z",
    )
    val Paperclip = lucide(
        "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57" +
            "a2 2 0 0 1-2.83-2.83l8.49-8.48",
    )
    val ArrowUpRight = lucide("M7 7h10v10 M7 17 17 7")
    val Info = lucide("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z M12 16v-4 M12 8h.01")
    val TriangleAlert = lucide(
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3 M12 9v4 M12 17h.01",
    )
    val Leaf = lucide(
        "M7 20h10 M10 20c5.5-2.5.8-6.4 3-10 " +
            "M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z " +
            "M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z",
    )
    val Key = lucide(
        "m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L21 5 M21 2l-9.6 9.6 " +
            "M7.5 10a5.5 5.5 0 1 0 0 11 5.5 5.5 0 1 0 0-11z",
    )
    val Clock = lucide("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z M12 6v6l4 2")
    val Pencil = lucide(
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352" +
            "a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
    )
    val Sparkles = lucide(
        "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936" +
            "A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937" +
            "l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135" +
            "a.5.5 0 0 1-.962 0z",
    )
    val SwitchArrows = lucide("M8 3 4 7l4 4 M4 7h16 m16 21 4-4-4-4 M20 17H4")
    val Shield = lucide(
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1" +
            "c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    )
    val Zap = lucide(
        "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7" +
            "a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
    )
    val TrendingUp = lucide("M16 7h6v6 M22 7l-8.5 8.5-5-5L2 17")

    // ── Filled 16px marks for the target card (the owner's set, 2026-08-07) ──
    // Solid shapes with their detail punched out by the even-odd rule, not stroked outlines, so
    // they go through `filled`. The source markup carried editor leftovers (`nv_id`, `class`,
    // `aria-hidden`, `focusable`) and a plus whose two bars were centred a fraction off 8 — only
    // the geometry is kept here, squared up.

    /** A solid disc with an exclamation cut out of it — the overdue badge. */
    val AlertCircleFilled = filled(
        "M.5 8a7.5 7.5 0 1 1 15 0 7.5 7.5 0 0 1-15 0" +
            "m6.75.25v-3a.75.75 0 0 1 1.5 0v3a.75.75 0 0 1-1.5 0" +
            "M8 11.5A.75.75 0 1 0 8 10a.75.75 0 0 0 0 1.5",
    )

    /**
     * A plus with rounded ends, both bars centred on 8 (the source had 7.98 / 7.946). The bars are
     * 2.2 units thick rather than the source's 1.5: at the 18dp the target tile draws it, the
     * thinner original read as a hairline against the calendar's own outline.
     */
    val PlusFilled = filled(
        "M2.5 8a1.1 1.1 0 0 1 1.1-1.1h8.8a1.1 1.1 0 0 1 0 2.2h-8.8A1.1 1.1 0 0 1 2.5 8z",
        "M8 2.5a1.1 1.1 0 0 1 1.1 1.1v8.8a1.1 1.1 0 0 1-2.2 0v-8.8A1.1 1.1 0 0 1 8 2.5z",
    )

    // ── Goal-workspace navigation chrome ─────────────────────────────────────
    // The owner supplied this second, *filled* 16×16 family for the goal-workspace header,
    // GROW tab bar and footer (the Lucide stroke set above still covers the rest of the app).
    //
    // Two rules when adding one, both learned the hard way — a mis-transcribed path draws
    // NOTHING while every existence assertion stays green, so re-render `VisualCheckNavIconsTest`
    // after touching this block and look at `build/reports/visual/nav-icons.png`:
    //
    //  1. **Space the arc flags out.** Compose's path parser does not accept SVG's compact form
    //     (`a.75.75 0 011.06-1.06`) — the two flags have to be separate tokens
    //     (`a .75 .75 0 0 1 1.06 -1.06`). Every path below is stored in that expanded form.
    //  2. **One argument per source `<path>` element.** Merging an icon's elements into a single
    //     path makes their overlaps cancel under the even-odd rule, hollowing the glyph out.

    /** Header "home" affordance — a chevron pointing back to All goals. */
    val NavChevronLeft = filled(CHEVRON_RIGHT, mirrorX = true)

    /** Header goal search. */
    val NavSearch = filled(
        "M 11.035 12.096 a 6.5 6.5 0 1 1 1.06 -1.06 l 2.935 2.934 a .75 .75 0 0 1 -1.06 1.06 " +
            "l -2.935 -2.934 Z M 12 7 A 5 5 0 1 1 2 7 a 5 5 0 0 1 10 0 Z",
    )

    /** Footer main menu (three bars). The source declares no fill rule, so it winds non-zero. */
    val NavMenu = filled(
        "M 2 4.5 a .75 .75 0 0 1 .75 -.75 h 10.5 a .75 .75 0 0 1 0 1.5 H 2.75 " +
            "A .75 .75 0 0 1 2 4.5 Z M 2 8 a .75 .75 0 0 1 .75 -.75 h 10.5 " +
            "a .75 .75 0 0 1 0 1.5 H 2.75 A .75 .75 0 0 1 2 8 Z m .75 2.75 " +
            "a .75 .75 0 0 0 0 1.5 h 10.5 a .75 .75 0 0 0 0 -1.5 H 2.75 Z",
        fillType = PathFillType.NonZero,
    )

    /** Footer resources page — a document with a paper clip. */
    val NavResources = filled(
        "M 5.745 12.777 a .75 .75 0 0 1 1.061 0 l 1.021 1.021 a .75 .75 0 0 1 -1.06 1.061 " +
            "l -1.022 -1.021 a .75 .75 0 0 1 0 -1.06 Z",
        "M 4.66 10.156 a 1.535 1.535 0 1 0 0 3.071 a 1.535 1.535 0 0 0 0 -3.07 Z " +
            "m -3.035 1.536 a 3.035 3.035 0 1 1 6.07 0 a 3.035 3.035 0 0 1 -6.07 0 Z " +
            "M 9.758 .921 a .75 .75 0 0 1 .75 .75 v 2.462 c 0 .362 .294 .656 .656 .656 " +
            "h 2.462 a .75 .75 0 0 1 0 1.5 h -2.462 a 2.156 2.156 0 0 1 -2.156 -2.156 " +
            "V 1.67 a .75 .75 0 0 1 .75 -.75 Z",
        "M 1.625 3.78 A 2.86 2.86 0 0 1 4.485 .922 h 5.454 a 2.86 2.86 0 0 1 2.022 .838 " +
            "l 1.577 1.577 a 2.86 2.86 0 0 1 .838 2.022 v 6.861 a 2.86 2.86 0 0 1 -2.86 2.86 " +
            "H 10.11 a .75 .75 0 1 1 0 -1.5 h 1.406 c .75 0 1.36 -.609 1.36 -1.36 V 5.358 " +
            "c 0 -.36 -.144 -.706 -.399 -.961 L 10.9 2.82 a 1.36 1.36 0 0 0 -.961 -.399 " +
            "H 4.484 c -.75 0 -1.36 .61 -1.36 1.36 v 3.516 a .75 .75 0 1 1 -1.5 0 V 3.78 Z",
    )

    /** Home (house) — the drawer's Home row, from the same filled family. */
    val NavHome = filled(
        "M 2.667 4.983 a .75 .75 0 0 1 .75 .75 v 7.517 h 9.166 V 5.733 a .75 .75 0 0 1 1.5 0 " +
            "V 14 a .75 .75 0 0 1 -.75 .75 H 2.667 a .75 .75 0 0 1 -.75 -.75 V 5.733 " +
            "a .75 .75 0 0 1 .75 -.75 Z",
        "M 7.57 1.386 a .75 .75 0 0 1 .86 0 l 6.667 4.666 a .75 .75 0 0 1 -.86 1.23 L 8 2.914 " +
            "L 1.763 7.281 a .75 .75 0 1 1 -.86 -1.229 L 7.57 1.386 Z M 5.25 10 " +
            "c 0 -1.15 .932 -2.083 2.083 -2.083 h 1.334 c 1.15 0 2.083 .932 2.083 2.083 v 4 " +
            "a .75 .75 0 0 1 -1.5 0 v -4 a .583 .583 0 0 0 -.583 -.583 H 7.333 " +
            "A .583 .583 0 0 0 6.75 10 v 4 a .75 .75 0 0 1 -1.5 0 v -4 Z",
    )

    /** Help — a question mark inside a speech bubble, for the drawer's About Spira row. */
    val NavHelp = filled(
        "M8.683 6.687 " +
            "a.674 .674 0 0 0 -.708 -.605 .75 .75 0 0 1 -.073 0 .704 .704 0 0 0 -.714 .545 .75 .75 0 0 1 -1.462 -.34 2.204 2.204 0 0 1 2.202 -1.704 2.174 2.174 0 0 1 2.255 2.084 " +
            "c0 .894 -.655 1.433 -.993 1.708 l-.138 .11 " +
            "a3.015 3.015 0 0 0 -.307 .27 .75 .75 0 0 1 -1.495 -.088 c0 -.455 .249 -.78 .432 -.972 " +
            "a4.97 4.97 0 0 1 .476 -.416 l.085 -.068 c.356 -.29 .432 -.42 .44 -.524 z",
        "M11.728 4.272 a5.26 5.26 0 0 0 -8.026 6.736 .75 .75 0 0 1 .118 .594 l-.166 .743 .744 -.165 " +
            "a.75 .75 0 0 1 .594 .118 5.26 5.26 0 0 0 6.736 -8.026 zM3.664 2.814 a6.76 6.76 0 1 1 .748 10.9 " +
            "l-1.583 .351 a.75 .75 0 0 1 -.894 -.894 l.351 -1.583 a6.76 6.76 0 0 1 1.378 -8.774 z",
        "M8.76 10.781 a.75 .75 0 1 1 -1.501 0 .75 .75 0 0 1 1.5 0 z",
    )

    /** The "bring your own key" mark — a key on a ring, from the same filled family. */
    val NavKey = filled(
        "M8.989 5.347 c.158 -.16 .41 -.31 .726 -.31 s.568 .15 .727 .31 " +
            "c.159 .158 .309 .411 .309 .726 0 .316 -.15 .568 -.309 .727 a1.035 1.035 0 0 1 -.727 .309 " +
            "c-.315 0 -.568 -.15 -.726 -.309 a1.035 1.035 0 0 1 -.31 -.727 c0 -.315 .15 -.568 .31 -.726 z",
        "M9.295 2.771 c-.344 -.124 -.584 -.06 -.763 .12 L6.46 4.96 a.804 .804 0 0 0 -.192 .83 " +
            "L6.9 7.194 a.75 .75 0 0 1 -.158 .842 l-4.06 3.993 v1.221 h1.224 l4.065 -4.065 " +
            "a.75 .75 0 0 1 .826 -.159 l1.482 .635 c.344 .125 .584 .06 .763 -.119 l2.043 -2.044 " +
            "c.189 -.239 .244 -.516 .146 -.79 l-1.063 -2.482 -.007 -.016 a.665 .665 0 0 0 -.368 -.368 " +
            "l-.017 -.007 L9.295 2.77 zM7.47 1.83 c.675 -.676 1.574 -.755 2.365 -.458 " +
            "l.032 .013 2.492 1.068 c.54 .219 .97 .65 1.19 1.19 l1.068 2.492 .013 .032 " +
            "c.32 .855 .095 1.706 -.412 2.315 a.755 .755 0 0 1 -.046 .05 l-2.07 2.07 " +
            "c-.676 .676 -1.574 .755 -2.365 .458 a.895 .895 0 0 1 -.033 -.013 l-1.032 -.442 -3.926 3.925 " +
            "a.75 .75 0 0 1 -.53 .22 H1.93 a.75 .75 0 0 1 -.75 -.75 v-2.285 a.75 .75 0 0 1 .225 -.535 " +
            "l3.912 -3.847 -.429 -.952 a2.304 2.304 0 0 1 .51 -2.48 L7.472 1.83 z",
    )

    /**
     * The AI assistant mark — a large four-point sparkle with a small companion. Replaces the
     * old Brain glyph in the footer and in the All-goals header. Its source art is 15×16, so it
     * is nudged half a unit right to sit centred in the shared 16×16 box.
     */
    val NavAi = filled(
        "M 5.737 1.166 c .226 -.688 1.2 -.688 1.425 0 l 1.15 3.494 a .75 .75 0 0 0 .477 .478 " +
            "l 3.495 1.15 c .687 .225 .687 1.198 0 1.424 l -3.495 1.15 " +
            "a .75 .75 0 0 0 -.478 .477 l -1.149 3.495 c -.226 .687 -1.199 .687 -1.425 0 " +
            "L 4.588 9.338 a .75 .75 0 0 0 -.478 -.478 L .616 7.712 " +
            "c -.687 -.226 -.687 -1.199 0 -1.425 L 4.11 5.138 a .75 .75 0 0 0 .478 -.478 " +
            "l 1.15 -3.494 Z m 6.238 9.778 a .5 .5 0 0 1 .95 0 l .312 .95 " +
            "a .5 .5 0 0 0 .319 .319 l .95 .312 a .5 .5 0 0 1 0 .95 l -.95 .312 " +
            "a .5 .5 0 0 0 -.319 .319 l -.312 .95 a .5 .5 0 0 1 -.95 0 l -.313 -.95 " +
            "a .5 .5 0 0 0 -.318 -.319 l -.95 -.312 a .5 .5 0 0 1 0 -.95 l .95 -.313 " +
            "a .5 .5 0 0 0 .319 -.318 l .312 -.95 Z",
        fillType = PathFillType.NonZero,
        offsetX = 0.5f,
    )

    /**
     * A trophy — the mark on the assistant's "help me create a new goal" suggestion. Three source
     * `<path>` elements, each passed separately as the family requires: merged into one argument
     * the cup's bowl and its handles would cancel under even-odd and the glyph would hollow out.
     */
    val NavTrophy = filled(
        "M5.417 3.083v5.25c0 1.059.858 1.917 1.916 1.917h1.334a1.917 1.917 0 0 0 1.916-1.917v-5.25zM3.917 3" +
            "c0-.782.634-1.417 1.416-1.417h5.334c.782 0 1.416.635 1.416 1.417v5.333a3.417 3.417 0 0 1-3.416 3.417" +
            "H7.333a3.417 3.417 0 0 1-3.416-3.417z",
        "M7.25 13.667V11h1.5v2.667z",
        "M5.25 13.667a.75.75 0 0 1 .75-.75h4a.75.75 0 0 1 0 1.5H6a.75.75 0 0 1-.75-.75m5.333-9.334" +
            "a.75.75 0 0 1 .75-.75h2c.783 0 1.417.635 1.417 1.417v1.333A2.75 2.75 0 0 1 12 9.083h-.667" +
            "a.75.75 0 0 1 0-1.5H12c.69 0 1.25-.56 1.25-1.25v-1.25h-1.917a.75.75 0 0 1-.75-.75M1.25 5" +
            "c0-.782.634-1.417 1.417-1.417h2a.75.75 0 0 1 0 1.5H2.75v1.25c0 .69.56 1.25 1.25 1.25h.667" +
            "a.75.75 0 0 1 0 1.5H4a2.75 2.75 0 0 1-2.75-2.75z",
    )
}

/** Shared by [SpiraIcons.NavChevronLeft], which mirrors it into a left chevron. */
private const val CHEVRON_RIGHT =
    "M 5.47 3.47 a .75 .75 0 0 1 1.06 0 l 4 4 a .75 .75 0 0 1 0 1.06 l -4 4 " +
        "a .75 .75 0 0 1 -1.06 -1.06 L 8.94 8 L 5.47 4.53 a .75 .75 0 0 1 0 -1.06"

/**
 * Builds one of the owner-supplied **filled** 16×16 glyphs (the navigation family). Unlike
 * [lucide] these have no stroke — the shape *is* the fill — and each [paths] entry is one
 * `<path>` element of the source SVG, drawn separately so their fills never cancel.
 *
 * [fillType] is the source's `fill-rule` (SVG defaults to non-zero when the attribute is absent),
 * [mirrorX] flips the glyph horizontally, and [offsetX] nudges art that isn't a full 16 units
 * wide back into the centre of the box.
 */
private fun filled(
    vararg paths: String,
    fillType: PathFillType = PathFillType.EvenOdd,
    mirrorX: Boolean = false,
    offsetX: Float = 0f,
): ImageVector =
    ImageVector.Builder(
        name = "spira-nav",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).apply {
        // A group carries the mirror/offset transform; without one the raw path would have to be
        // rewritten by hand, which is where transcription bugs come from.
        addGroup(
            name = "transform",
            pivotX = 8f,
            pivotY = 8f,
            scaleX = if (mirrorX) -1f else 1f,
            translationX = offsetX,
        )
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                pathFillType = fillType,
                fill = SolidColor(Color.Black), // tinted by the Icon composable at draw time
            )
        }
        clearGroup()
    }.build()

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
