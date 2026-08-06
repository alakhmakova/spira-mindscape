package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The little slice of Markdown an assistant reply actually uses — the Android twin of the web
 * `Markdown` component in `AiPanel.tsx`. Headings, bullet and numbered lists, block quotes, fenced
 * code, and inline `**bold**` / `*italic*` / `` `code` ``.
 *
 * Deliberately not a full parser: a chat reply is short prose, and a general Markdown dependency
 * would be far more surface area than the handful of constructs the models emit.
 */
@Composable
fun AiMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** List markers, quote rules and quoted prose — on the teal panel this is a white tint. */
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val muted = mutedColor
    // Headings are set in the brand serif, as they are on the web (`font-['Playfair_Display']`).
    val headingFamily = MaterialTheme.typography.titleLarge.fontFamily

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(if (block is MdBlock.ListItem) 2.dp else 8.dp))
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(block.text),
                    style = style.copy(
                        fontFamily = headingFamily,
                        fontSize = when (block.level) {
                            1 -> 19.sp
                            2 -> 16.5.sp
                            3 -> 15.sp
                            else -> 14.sp
                        },
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = color,
                )

                is MdBlock.Paragraph -> Text(inlineMarkdown(block.text), style = style, color = color)

                is MdBlock.ListItem -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        block.marker,
                        style = style,
                        color = muted,
                        modifier = Modifier.width(if (block.marker.length > 2) 26.dp else 16.dp),
                    )
                    Text(inlineMarkdown(block.text), style = style, color = color)
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Text("│", style = style, color = muted, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        inlineMarkdown(block.text),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        color = muted,
                    )
                }

                is MdBlock.Code -> Text(
                    block.text,
                    style = style.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(muted.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")

/** Splits the reply into blocks. Blank lines separate paragraphs; ``` fences hold code verbatim. */
internal fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val t = paragraph.toString().trim()
        if (t.isNotEmpty()) blocks += MdBlock.Paragraph(t)
        paragraph.setLength(0)
    }

    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushParagraph()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                blocks += MdBlock.Code(code.toString().trimEnd())
            }
            line.isBlank() -> flushParagraph()
            HEADING.matches(line) -> {
                flushParagraph()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }
            QUOTE.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Quote(QUOTE.find(line)!!.groupValues[1].trim())
            }
            BULLET.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.ListItem("•", BULLET.find(line)!!.groupValues[1].trim())
            }
            NUMBERED.matches(line) -> {
                flushParagraph()
                val m = NUMBERED.find(line)!!
                blocks += MdBlock.ListItem("${m.groupValues[1]}.", m.groupValues[2].trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val INLINE = Regex("""\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|_(.+?)_|`(.+?)`""")

/** `**bold**`, `*italic*` and `` `code` `` inside one line of prose. */
internal fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() || groups[2].isNotEmpty() ->
                withSpan(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(groups[1].ifEmpty { groups[2] })
                }
            groups[3].isNotEmpty() || groups[4].isNotEmpty() ->
                withSpan(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(groups[3].ifEmpty { groups[4] })
                }
            groups[5].isNotEmpty() ->
                withSpan(SpanStyle(fontFamily = FontFamily.Monospace)) { append(groups[5]) }
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

private inline fun AnnotatedString.Builder.withSpan(style: SpanStyle, block: () -> Unit) {
    pushStyle(style)
    block()
    pop()
}
