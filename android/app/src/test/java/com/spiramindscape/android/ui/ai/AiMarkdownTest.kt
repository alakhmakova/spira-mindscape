package com.spiramindscape.android.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The little slice of Markdown assistant replies actually use. */
class AiMarkdownTest {

    @Test
    fun `paragraphs are joined across soft line breaks and split on blank ones`() {
        val blocks = parseMarkdownBlocks("one\ntwo\n\nthree")
        assertEquals(2, blocks.size)
        assertEquals("one two", (blocks[0] as MdBlock.Paragraph).text)
        assertEquals("three", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun `headings keep their level`() {
        val blocks = parseMarkdownBlocks("## Next steps")
        assertEquals(2, (blocks.single() as MdBlock.Heading).level)
        assertEquals("Next steps", (blocks.single() as MdBlock.Heading).text)
    }

    @Test
    fun `bulleted and numbered lists become items with their own markers`() {
        val blocks = parseMarkdownBlocks("- first\n* second\n1. third")
        assertEquals(3, blocks.size)
        assertEquals("•", (blocks[0] as MdBlock.ListItem).marker)
        assertEquals("second", (blocks[1] as MdBlock.ListItem).text)
        assertEquals("1.", (blocks[2] as MdBlock.ListItem).marker)
    }

    @Test
    fun `a fenced block keeps its code verbatim`() {
        val blocks = parseMarkdownBlocks("before\n```\nval x = 1\n  indented\n```\nafter")
        val code = blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("val x = 1\n  indented", code.text)
    }

    @Test
    fun `a quote is its own block`() {
        val blocks = parseMarkdownBlocks("> think about it")
        assertEquals("think about it", (blocks.single() as MdBlock.Quote).text)
    }

    @Test
    fun `inline emphasis is applied and its markers removed`() {
        val text = inlineMarkdown("a **bold** and *italic* and `code` end")
        assertEquals("a bold and italic and code end", text.text)
        assertEquals(3, text.spanStyles.size)
    }

    @Test
    fun `text with no markup is passed through untouched`() {
        assertEquals("plain words", inlineMarkdown("plain words").text)
    }

    @Test
    fun `an empty reply produces nothing to draw`() {
        assertTrue(parseMarkdownBlocks("").isEmpty())
    }
}
