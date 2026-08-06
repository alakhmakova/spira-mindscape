package com.spiramindscape.android.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transcript is synced between the phone and the browser through
 * `/api/ai/chat/transcript`, so its JSON is a contract with the web `Msg` type — a round-trip that
 * loses a field would quietly drop proposal cards from a conversation opened on the other device.
 */
class TranscriptTest {

    @Test
    fun `a conversation survives a round-trip`() {
        val proposal = proposalFromToolArgs(
            """{"kind":"target","title":"Trip","deadline_value":"2026-12-01","proposalId":9}""",
        )!!
        val messages = listOf(
            ChatMessage("m1", ChatRole.USER, "plan my trip"),
            ChatMessage("m2", ChatRole.ASSISTANT, "Here's what I suggest.", proposals = listOf(proposal)),
        )

        val restored = parseTranscript(encodeTranscript(messages))!!

        assertEquals(2, restored.size)
        assertEquals(ChatRole.USER, restored[0].role)
        assertEquals("plan my trip", restored[0].content)
        val back = restored[1].proposals.single()
        assertEquals(ProposalKind.TARGET, back.kind)
        assertEquals("Trip", back.title)
        assertEquals("2026-12-01", back.deadline)
        assertEquals(9L, back.serverId)
    }

    @Test
    fun `a settled decision survives, so a card never asks twice`() {
        val proposal = proposalFromToolArgs("""{"kind":"option","value":"Evenings"}""")!!
            .copy(status = ProposalStatus.APPROVED)
        val restored = parseTranscript(
            encodeTranscript(listOf(ChatMessage("m", ChatRole.ASSISTANT, "ok", proposals = listOf(proposal)))),
        )!!
        assertEquals(ProposalStatus.APPROVED, restored.single().proposals.single().status)
    }

    @Test
    fun `the in-flight placeholder is never stored`() {
        val messages = listOf(
            ChatMessage("m1", ChatRole.USER, "hi"),
            ChatMessage("m2", ChatRole.ASSISTANT, "typing", streaming = true),
        )
        assertEquals(listOf("m1"), parseTranscript(encodeTranscript(messages))!!.map { it.id })
    }

    @Test
    fun `attachment bytes are stripped from what is stored`() {
        val messages = listOf(
            ChatMessage(
                "m1", ChatRole.USER, "look",
                attachments = listOf(AiApi.ChatAttachment("cv.pdf", "application/pdf", "data:application/pdf;base64,AAA")),
            ),
        )
        val stored = parseTranscript(encodeTranscript(messages))!!
        assertEquals("cv.pdf", stored.single().attachments.single().name)
        assertEquals("", stored.single().attachments.single().dataUrl)
    }

    @Test
    fun `adopting the server copy keeps the bytes this device still holds`() {
        val local = listOf(
            ChatMessage(
                "m1", ChatRole.USER, "look",
                attachments = listOf(AiApi.ChatAttachment("shot.jpg", "image/jpeg", "data:image/jpeg;base64,ZZZ")),
            ),
        )
        val fromServer = parseTranscript(encodeTranscript(local))!!
        val merged = mergeAttachmentBytes(local, fromServer)
        assertEquals("data:image/jpeg;base64,ZZZ", merged.single().attachments.single().dataUrl)
    }

    @Test
    fun `only the last hundred messages are kept`() {
        val many = (1..150).map { ChatMessage("m$it", ChatRole.USER, "x$it") }
        val stored = parseTranscript(encodeTranscript(many))!!
        assertEquals(CHAT_MAX_MESSAGES, stored.size)
        assertEquals("m150", stored.last().id)
    }

    @Test
    fun `unusable content leaves the caller holding what it had`() {
        assertNull(parseTranscript(null))
        assertNull(parseTranscript(""))
        assertNull(parseTranscript("{not an array}"))
    }

    @Test
    fun `a transcript written by the web parses here`() {
        // Field-for-field what `messagesForStore` emits in the browser.
        val webJson = """
            [{"id":"a1","role":"user","content":"hello"},
             {"id":"a2","role":"assistant","content":"hi","proposals":[
               {"id":"p1","kind":"confidence","title":"Confidence → 8/10","status":"pending","rawValue":"8"}]}]
        """.trimIndent()
        val parsed = parseTranscript(webJson)!!
        assertEquals(2, parsed.size)
        val p = parsed[1].proposals.single()
        assertEquals(ProposalKind.CONFIDENCE, p.kind)
        assertEquals(ProposalStatus.PENDING, p.status)
        assertEquals("8", p.rawValue)
        assertTrue(parsed[1].attachments.isEmpty())
    }
}
