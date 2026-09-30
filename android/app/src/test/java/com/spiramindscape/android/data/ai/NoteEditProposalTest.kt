package com.spiramindscape.android.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Kotlin side of the web `note-edit-proposal.test.ts`. An AI note edit used to replace the
 * whole body with the model's copy of the note, so a hand edit made in the meantime was lost on
 * Accept; the card now says whether it adds or rewrites and carries the server's diff.
 */
class NoteEditProposalTest {

    private val payload = """
        {"kind":"edit_note","id":"12","title":"Profile","value":"<p>Kotlin on Android</p>",
         "mode":"append_to_section","section":"Experience",
         "baseUpdatedAt":"2026-09-15T10:02:11.123456Z",
         "diff":{"added":["kotlin on android"],"removed":[]}}
    """.trimIndent()

    @Test
    fun `carries the server's mode, section, version and diff`() {
        val p = proposalFromToolArgs(payload)!!
        assertEquals(ProposalKind.EDIT_NOTE, p.kind)
        assertEquals("12", p.itemId)
        assertEquals("<p>Kotlin on Android</p>", p.body)
        assertEquals("append_to_section", p.noteMode)
        assertEquals("Experience", p.noteSection)
        assertEquals("2026-09-15T10:02:11.123456Z", p.baseUpdatedAt)
        assertEquals(NoteDiff(listOf("kotlin on android"), emptyList()), p.noteDiff)
        assertEquals("Adds to «Experience»", p.detail)
    }

    @Test
    fun `an old payload without a mode is an append, never a rewrite`() {
        val p = proposalFromToolArgs("""{"kind":"edit_note","id":"12","value":"<p>x</p>"}""")!!
        assertEquals("append", p.noteMode)
        assertNull(p.baseUpdatedAt)
        assertNull(p.noteDiff)
        assertEquals("Adds to the end of the note", p.detail)
    }

    @Test
    fun `the wording matches the web`() {
        assertEquals("Rewrites the whole note", noteEditDetail("replace_all", null))
        assertEquals("Rewrites «Contact»", noteEditDetail("replace_section", "Contact"))
        assertEquals("Adds to several sections", noteEditDetail("merge_sections", null))
    }
}
