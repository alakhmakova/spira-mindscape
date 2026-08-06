package com.spiramindscape.android.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin side of the web `src/components/ai/proposal-logic.test.ts`. Both surfaces parse the
 * same `propose_goal_change` tool call, so a card must mean the same thing on the phone as it does
 * in the browser.
 */
class ProposalTest {

    private fun args(vararg pairs: Pair<String, Any?>): String =
        org.json.JSONObject().apply {
            pairs.forEach { (k, v) -> put(k, v ?: org.json.JSONObject.NULL) }
        }.toString()

    @Test
    fun `a new goal takes its title and description apart`() {
        val p = proposalFromToolArgs(
            args("kind" to "new_goal", "title" to "Run a marathon", "value" to "Sub 4 hours", "confidence" to "7"),
        )!!
        assertEquals(ProposalKind.NEW_GOAL, p.kind)
        assertEquals("Run a marathon", p.title)
        assertEquals("Sub 4 hours", p.body)
        assertEquals(7, p.confidence)
    }

    @Test
    fun `confidence is clamped to the 1-10 the stepper allows`() {
        val high = proposalFromToolArgs(args("kind" to "new_goal", "title" to "G", "confidence" to "44"))!!
        val low = proposalFromToolArgs(args("kind" to "new_goal", "title" to "G", "confidence" to "0"))!!
        assertEquals(10, high.confidence)
        assertEquals(1, low.confidence)
    }

    @Test
    fun `a checklist target is recognised from its items`() {
        val items = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("text", "Book flights").put("done", true))
            put(org.json.JSONObject().put("text", "Pack"))
            put(org.json.JSONObject().put("text", "   ")) // blank items are dropped
        }
        val p = proposalFromToolArgs(args("kind" to "target", "title" to "Trip", "items" to items))!!
        assertEquals(TargetShape.CHECKLIST, p.targetType)
        assertEquals(listOf("Book flights", "Pack"), p.items?.map { it.text })
        assertEquals("New checklist · 1/2 done", p.detail)
        assertEquals("Checklist · 1/2 done", createSummary(p))
    }

    @Test
    fun `a numeric target carries its measure`() {
        val p = proposalFromToolArgs(
            args("kind" to "target", "title" to "Save", "total" to "1900000", "current" to "10000", "unit" to "SEK"),
        )!!
        assertEquals(TargetShape.NUMERIC, p.targetType)
        assertEquals("10000 / 1900000 SEK", createSummary(p))
    }

    @Test
    fun `a bare target is binary`() {
        val p = proposalFromToolArgs(args("kind" to "target", "title" to "Send it"))!!
        assertEquals(TargetShape.BINARY, p.targetType)
        assertNull(createSummary(p))
    }

    @Test
    fun `a goal-level operation carries the goal id, an item edit carries the item id`() {
        val goalOp = proposalFromToolArgs(args("kind" to "delete_goal", "id" to "42"))!!
        assertEquals("42", goalOp.goalId)
        val itemOp = proposalFromToolArgs(args("kind" to "edit_target", "id" to "7", "value" to "New name"))!!
        assertEquals("7", itemOp.itemId)
        assertNull(itemOp.goalId)
    }

    @Test
    fun `a contact collects its fields into a patch`() {
        val p = proposalFromToolArgs(
            args("kind" to "email", "title" to "Anna", "value" to "a@b.se", "role" to "Recruiter"),
        )!!
        assertEquals(mapOf("name" to "Anna", "email" to "a@b.se", "role" to "Recruiter"), p.patch)
    }

    @Test
    fun `an unknown kind is kept rather than dropped`() {
        val p = proposalFromToolArgs(args("kind" to "teleport", "value" to "somewhere"))!!
        assertEquals(ProposalKind.UNKNOWN, p.kind)
    }

    @Test
    fun `malformed arguments produce no card at all`() {
        assertNull(proposalFromToolArgs("not json"))
    }

    @Test
    fun `the server proposal id is carried through so a decision can be recorded`() {
        val p = proposalFromToolArgs(args("kind" to "option", "value" to "Try evenings", "proposalId" to 31))!!
        assertEquals(31L, p.serverId)
    }

    // ── aspects ─────────────────────────────────────────────────────────────

    @Test
    fun `a goal with extras offers one checkbox per extra`() {
        val p = proposalFromToolArgs(
            args(
                "kind" to "new_goal", "title" to "G", "value" to "Why it matters",
                "confidence" to "6", "deadline_value" to "2026-12-01",
            ),
        )!!
        assertEquals(listOf("confidence", "deadline", "description"), createAspects(p).map { it.id })
    }

    @Test
    fun `a bare goal has no extras to tick`() {
        val p = proposalFromToolArgs(args("kind" to "new_goal", "title" to "G"))!!
        assertTrue(createAspects(p).isEmpty())
    }

    @Test
    fun `unticking an aspect strips exactly that field`() {
        val p = proposalFromToolArgs(
            args("kind" to "new_goal", "title" to "G", "value" to "Body", "confidence" to "6", "deadline_value" to "2026-12-01"),
        )!!
        val kept = applyExcludedAspects(p, setOf("deadline", "description"))
        assertNull(kept.deadline)
        assertNull(kept.body)
        assertEquals(6, kept.confidence)
        assertEquals("G", kept.title)
    }

    // ── dedup + context ─────────────────────────────────────────────────────

    @Test
    fun `several distinct creates all survive, an exact repeat does not`() {
        val a = proposalFromToolArgs(args("kind" to "target", "title" to "One"))!!
        val b = proposalFromToolArgs(args("kind" to "target", "title" to "Two"))!!
        val repeat = proposalFromToolArgs(args("kind" to "target", "title" to "one"))!!
        assertEquals(listOf("One", "Two"), dedupCreates(listOf(a, b, repeat)).map { it.title })
    }

    @Test
    fun `the revise context carries every field, not just the headline`() {
        val items = org.json.JSONArray().apply { put(org.json.JSONObject().put("text", "Step one")) }
        val p = proposalFromToolArgs(
            args("kind" to "target", "title" to "Trip", "items" to items, "deadline_value" to "2026-12-01"),
        )!!
        val context = proposalContext(p)
        assertTrue(context.contains("title: Trip"))
        assertTrue(context.contains("deadline: 2026-12-01"))
        assertTrue(context.contains("Step one"))
    }

    @Test
    fun `a very long proposal is truncated so a revise prompt stays bounded`() {
        val p = proposalFromToolArgs(args("kind" to "note", "title" to "N", "value" to "x".repeat(9000)))!!
        assertTrue(proposalContext(p).length <= 4000)
    }

    // ── history ─────────────────────────────────────────────────────────────

    @Test
    fun `history keeps real turns and merges consecutive same-role ones`() {
        val messages = listOf(
            ChatMessage("1", ChatRole.USER, "first"),
            ChatMessage("2", ChatRole.USER, "second"),
            ChatMessage("3", ChatRole.ASSISTANT, "reply"),
            ChatMessage("4", ChatRole.ASSISTANT, "boom", isError = true),
            ChatMessage("5", ChatRole.ASSISTANT, "", streaming = true),
            ChatMessage("6", ChatRole.SYSTEM, "ignored"),
        )
        val history = buildHistory(messages)
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("first\n\nsecond", history[0].content)
        assertEquals("reply", history[1].content)
    }
}
