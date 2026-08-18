package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras

/** The active formats reported by the editor (drives toolbar highlighting). */
data class NoteEditorState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val highlight: Boolean = false,
    val code: Boolean = false,
    val h1: Boolean = false,
    val h2: Boolean = false,
    val h3: Boolean = false,
    val bullet: Boolean = false,
    val ordered: Boolean = false,
    val task: Boolean = false,
    val quote: Boolean = false,
    val link: Boolean = false,
    /** The format painter is holding a style, waiting to put it down. */
    val painter: Boolean = false,
)

private val TextColors = listOf("#222525", "#0a8080", "#f45d48", "#6c6c72")

/**
 * Font, size and line spacing — **the web's own three lists, value for value**
 * (`RichTextEditor.tsx`). They are the last three controls Android's toolbar was missing, and they
 * could not have been added before: the editor bundle did not register `FontFamily` / `FontSize` /
 * `LineHeight`, so the commands behind them did not exist (owner, 2026-08-17).
 *
 * Each is a `SpiraDropdownMenu` rather than the web's `<select>`, because a native picker is what
 * the rest of the app uses — but the values must stay identical, or the same note reads at a
 * different size on the phone.
 */
private val FontFamilies = listOf(
    "Sans" to "Arial, Helvetica, sans-serif",
    "Serif" to "Georgia, 'Times New Roman', serif",
    "Mono" to "'Courier New', monospace",
)
private val FontSizes = listOf("12", "14", "16", "18", "24", "32")
private val LineHeights = listOf("1.0" to "1", "1.15" to "1.15", "1.5" to "1.5", "2.0" to "2")

/**
 * The NATIVE Spira formatting toolbar for notes. A horizontally scrollable row of themed buttons
 * that send commands to the editor ([onCmd]) and highlight based on the editor's reported
 * [state]. Matches the app's look exactly (this replaced the in-page HTML toolbar).
 */
@Composable
fun NoteToolbar(
    state: NoteEditorState,
    onCmd: (name: String, arg: String?) -> Unit,
    onLink: () -> Unit,
    /** Reads the system clipboard natively — a WebView has no usable `navigator.clipboard.read()`. */
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TbText("H1", state.h1) { onCmd("h1", null) }
        TbText("H2", state.h2) { onCmd("h2", null) }
        TbText("H3", state.h3) { onCmd("h3", null) }
        TbDivider()
        TbIcon(SpiraIcons.Bold, "Bold", state.bold) { onCmd("bold", null) }
        TbIcon(SpiraIcons.Italic, "Italic", state.italic) { onCmd("italic", null) }
        TbIcon(SpiraIcons.Underline, "Underline", state.underline) { onCmd("underline", null) }
        TbIcon(SpiraIcons.Strikethrough, "Strikethrough", state.strike) { onCmd("strike", null) }
        TbIcon(SpiraIcons.PenLine, "Highlight", state.highlight) { onCmd("highlight", null) }
        TbIcon(SpiraIcons.Code, "Code", state.code) { onCmd("code", null) }
        TbDivider()
        TbIcon(SpiraIcons.List, "Bullet list", state.bullet) { onCmd("bullet", null) }
        TbIcon(SpiraIcons.ListOrdered, "Numbered list", state.ordered) { onCmd("ordered", null) }
        TbIcon(SpiraIcons.ListChecks, "Task list", state.task) { onCmd("task", null) }
        TbIcon(SpiraIcons.Quote, "Quote", state.quote) { onCmd("quote", null) }
        TbIcon(SpiraIcons.Minus, "Divider", false) { onCmd("hr", null) }
        TbDivider()
        TbIcon(SpiraIcons.Link, "Link", state.link) { onLink() }
        TbIcon(SpiraIcons.Unlink, "Remove link", false) { onCmd("unlink", null) }
        TbDivider()
        TbMenu("Font", FontFamilies.map { it.first }) { index ->
            onCmd("fontFamily", index?.let { FontFamilies[it].second })
        }
        TbMenu("Size", FontSizes) { index ->
            index?.let { onCmd("fontSize", "${FontSizes[it]}px") }
        }
        TbMenu("Spacing", LineHeights.map { it.first }) { index ->
            index?.let { onCmd("lineHeight", LineHeights[it].second) }
        }
        TbDivider()
        TextColors.forEach { hex ->
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(1.dp, MaterialTheme.spiraExtras.border, CircleShape)
                    .clickable { onCmd("color", hex) },
            )
        }
        // **Paste keeping formatting** and the **format painter** — the two the web toolbar had
        // and this one did not (owner, 2026-08-17). The painter is one button with two steps: the
        // first press picks the style up, the second puts it down, and it stays lit in between so
        // the two steps are visible rather than implied.
        TbIcon(SpiraIcons.Paste, "Paste (keep formatting)", false) { onPaste() }
        TbIcon(
            SpiraIcons.Paintbrush,
            if (state.painter) "Apply the copied formatting" else "Copy formatting from here",
            state.painter,
        ) { onCmd("painter", null) }
        TbIcon(SpiraIcons.Eraser, "Clear formatting", false) { onCmd("clear", null) }
        TbDivider()
        TbIcon(SpiraIcons.Undo, "Undo", false) { onCmd("undo", null) }
        TbIcon(SpiraIcons.Redo, "Redo", false) { onCmd("redo", null) }
    }
}

@Composable
private fun TbIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.spiraExtras.primarySoft else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TbText(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.spiraExtras.primarySoft else Color.Transparent
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * A worded toolbar control that opens a [SpiraDropdownMenu] of choices — Font, Size and Spacing.
 *
 * The word plus a small solid chevron, the same trigger shape the list toolbars use, so the note
 * bar does not invent a fourth kind of control. Picking the first row ("Default") clears the
 * attribute, which is what the web's empty `<option>` does.
 */
@Composable
private fun TbMenu(label: String, options: List<String>, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable { open = true }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
            Icon(
                SpiraIcons.CaretDown,
                contentDescription = label,
                tint = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.size(14.dp),
            )
        }
        SpiraDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SpiraMenuItem(label = "Default", onClick = { open = false; onPick(null) })
            options.forEachIndexed { index, option ->
                SpiraMenuItem(label = option, onClick = { open = false; onPick(index) })
            }
        }
    }
}

@Composable
private fun TbDivider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(MaterialTheme.spiraExtras.border))
}
