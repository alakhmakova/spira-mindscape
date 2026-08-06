package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.ResourceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin side of `src/lib/spira/links.test.ts` + `resources.test.ts`. The token format is
 * shared storage — a mismatch here would render the other surface's text as raw `{{res:42}}`.
 */
class InlineLinksTest {

    private fun note(id: String, title: String?) = ResourceItem(id = id, type = "note", title = title)

    @Test
    fun `plain text is one segment`() {
        assertEquals(listOf(InlineSegment.Text("just words")), splitInline("just words"))
    }

    @Test
    fun `a bare url becomes a link segment`() {
        assertEquals(
            listOf(InlineSegment.Text("see "), InlineSegment.Url("https://x.com/a")),
            splitInline("see https://x.com/a"),
        )
    }

    @Test
    fun `a sentence period is not swallowed into the link`() {
        assertEquals(
            listOf(InlineSegment.Url("https://x.com"), InlineSegment.Text(".")),
            splitInline("https://x.com."),
        )
    }

    @Test
    fun `a resource token becomes a resource segment`() {
        assertEquals(
            listOf(InlineSegment.Text("Read "), InlineSegment.Resource("42"), InlineSegment.Text(" first")),
            splitInline("Read {{res:42}} first"),
        )
    }

    @Test
    fun `an optimistic local id is a valid token`() {
        assertEquals(listOf(InlineSegment.Resource("local-9f2a")), splitInline("{{res:local-9f2a}}"))
    }

    @Test
    fun `token helpers find and replace one specific resource`() {
        val text = "Call {{res:7}} about {{res:8}}"
        assertTrue(hasResourceToken(text))
        assertTrue(referencesResource(text, "7"))
        assertFalse(referencesResource(text, "9"))
        assertEquals(listOf("7", "8"), resourceIdsIn(text))
        assertEquals("Call Anna about {{res:8}}", replaceResourceToken(text, "7", "Anna"))
    }

    @Test
    fun `duplicate references are listed once`() {
        assertEquals(listOf("7"), resourceIdsIn("{{res:7}} and {{res:7}}"))
    }

    @Test
    fun `readable text quotes a resource by name and collapses the gap it leaves`() {
        val resources = listOf(note("42", "Job ad"))
        assertEquals("Read Job ad first", readableText("Read {{res:42}} first", resources))
        // A reference to something that no longer exists disappears rather than showing a raw tag.
        assertEquals("Read first", readableText("Read {{res:99}} first", resources))
    }

    @Test
    fun `tokens survive a name round-trip`() {
        val resources = listOf(note("42", "Job ad"))
        val editing = rewriteResourceTokens("Read {{res:42}}") { inner ->
            resources.firstOrNull { it.id == inner }?.let { resourceDisplayName(it) } ?: inner
        }
        assertEquals("Read {{res:Job ad}}", editing)

        val stored = rewriteResourceTokens(editing) { inner ->
            resources.firstOrNull { it.id == inner }?.id
                ?: resources.firstOrNull { resourceDisplayName(it).equals(inner, ignoreCase = true) }?.id
        }
        assertEquals("Read {{res:42}}", stored)
    }

    @Test
    fun `a tag naming something that no longer exists degrades to plain text`() {
        assertEquals("Read gone", rewriteResourceTokens("Read {{res:gone}}") { null })
    }

    @Test
    fun `appending a token respects the field limit`() {
        assertEquals("Apply {{res:42}}", appendResourceToken("Apply", "42", 200))
        assertNull(appendResourceToken("Apply", "42", 10))
        assertEquals("{{res:42}}", appendResourceToken("   ", "42", 200))
    }

    @Test
    fun `only http urls may be opened`() {
        assertTrue(isSafeHttpUrl("https://x.com"))
        assertTrue(isSafeHttpUrl("http://x.com"))
        assertFalse(isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(isSafeHttpUrl("data:text/html,x"))
    }

    @Test
    fun `a link resource falls back to its host name`() {
        assertEquals("github", titleFromUrl("https://www.github.com/spira"))
        assertEquals(
            "github",
            resourceDisplayName(ResourceItem(id = "1", type = "link", title = "", url = "https://github.com/x")),
        )
    }

    @Test
    fun `each resource kind has a display name`() {
        assertEquals("Untitled note", resourceDisplayName(note("1", "  ")))
        assertEquals("Untitled file", resourceDisplayName(ResourceItem(id = "2", type = "file", title = null)))
        assertEquals(
            "Anna",
            resourceDisplayName(ResourceItem(id = "3", type = "email", title = null, name = "Anna")),
        )
        assertEquals(
            "a@b.se",
            resourceDisplayName(ResourceItem(id = "4", type = "email", title = null, email = "a@b.se")),
        )
    }
}
