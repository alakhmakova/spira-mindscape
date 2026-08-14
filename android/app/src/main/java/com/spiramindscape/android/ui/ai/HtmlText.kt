package com.spiramindscape.android.ui.ai

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.core.text.HtmlCompat

/**
 * Renders a note's stored **HTML** with its formatting intact.
 *
 * The AI proposal card's "Read full content" used to run the body through `stripHtml` and print
 * the result flat, so a note the assistant had carefully laid out — headings, bullets, bold —
 * arrived as one grey paragraph, and the user had to accept it to find out what it looked like
 * (GRO-80). The web solved this by rendering the HTML in its content modal; this is the Android
 * side of the same fix.
 *
 * Deliberately built on [HtmlCompat] rather than a Markdown/HTML library: notes are written by the
 * app's own TipTap editor, so the tag vocabulary is small and known, and the platform parser
 * already handles it.
 */
@Composable
fun rememberHtmlText(html: String): AnnotatedString = remember(html) { htmlToAnnotated(html) }

/** True when this body is markup rather than plain prose — worth parsing, and worth styling. */
fun looksLikeHtml(s: String): Boolean = Regex("<(p|br|h[1-6]|ul|ol|li|strong|b|em|i|u|s)\\b[^>]*>", RegexOption.IGNORE_CASE)
    .containsMatchIn(s)

private fun htmlToAnnotated(html: String): AnnotatedString {
    // A list item's marker cannot be carried by a SpanStyle — Compose has no bullet span — so the
    // glyph is put into the text before parsing. Without this a bulleted note reads as a run-on.
    val prepared = html.replace(Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE), "$0• ")
    val spanned: Spanned = HtmlCompat.fromHtml(prepared, HtmlCompat.FROM_HTML_MODE_COMPACT)
    // HtmlCompat ends a block with two newlines; trailing ones leave an empty gap under the text.
    val text = spanned.toString().trimEnd('\n')

    return buildAnnotatedString {
        append(text)
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = minOf(spanned.getSpanEnd(span), text.length)
            if (start !in 0..end || start == end) return@forEach
            val style = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Typeface.BOLD_ITALIC ->
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    else -> null
                }
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                // Headings arrive as a size multiplier (plus their own bold span), so the scale
                // stays relative to whatever size the caller is drawing at.
                is RelativeSizeSpan -> SpanStyle(fontSize = span.sizeChange.em)
                is TypefaceSpan -> SpanStyle(fontFamily = FontFamily.Monospace)
                // Its marker is already in the text (see above); nothing left to style.
                is BulletSpan -> null
                else -> null
            }
            style?.let { addStyle(it, start, end) }
        }
    }
}
