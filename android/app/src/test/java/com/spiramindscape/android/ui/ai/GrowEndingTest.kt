package com.spiramindscape.android.ui.ai

import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ChatRole
import com.spiramindscape.android.data.ai.ProposalStatus
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * **The coach owns the ending, and it happens in this order** (owner, 2026-08-24).
 *
 * The agreed sequence is: the coach closes the session itself → the **record** for the user to keep
 * or discard → the **changes** it proposes for the goal → the **goodbye** → out. The clock is a
 * guide throughout; it never ends anything.
 *
 * Android implemented none of it. `end_session` was not even an event here, so the coach's ending
 * was invisible; instead the timer hit zero, sent "We're out of time — please close the session
 * now", and flipped to the record card the moment that reply finished. What the owner saw was a
 * goodbye and a save-memory question arriving by themselves, with no proposals and no closing
 * conversation — and the thing it offered to save was the coach's last chat message, i.e. the
 * farewell.
 *
 * These assertions walk the sequence through the real view model, because every one of those
 * failures is about *what happens next*, and nothing smaller than the whole arc shows that.
 */
@RunWith(RobolectricTestRunner::class)
class GrowEndingTest {

    private data class Turn(
        val text: String = "",
        val proposals: List<String> = emptyList(),
        /** Non-null when this turn calls `end_session`, carrying the session record. */
        val record: String? = null,
    )

    private class ScriptedChat(private val script: List<Turn>) : AiChat {
        var calls = 0
            private set
        val prompts = mutableListOf<String>()
        val remainingSent = mutableListOf<Int?>()
        var savedMemory: String? = null
            private set

        override fun streamChat(
            goalId: String?,
            message: String,
            history: List<AiApi.HistoryEntry>,
            provider: String,
            sessionType: String,
            attachments: List<AiApi.ChatAttachment>,
            sessionTotalMinutes: Int?,
            sessionRemainingSeconds: Int?,
        ): Flow<AiApi.ChatEvent> {
            prompts += message
            remainingSent += sessionRemainingSeconds
            val turn = script.getOrNull(calls++) ?: Turn()
            val events = buildList {
                if (turn.text.isNotEmpty()) add(AiApi.ChatEvent.Token(turn.text))
                turn.proposals.forEach { add(AiApi.ChatEvent.Proposal(it)) }
                turn.record?.let {
                    add(AiApi.ChatEvent.SessionEnd("""{"summary":"$it"}"""))
                }
                add(AiApi.ChatEvent.Done)
            }
            return flowOf(*events.toTypedArray())
        }

        override suspend fun listKeys() =
            listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "mistral-large-latest"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "MISTRAL"
        override suspend fun saveProvider(provider: String) = Unit
        override suspend fun getTranscript(goalId: String?) = AiApi.StoredTranscript("", "now")
        override suspend fun putTranscript(goalId: String?, content: String): String? = "now"
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) { savedMemory = summary }
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    private fun note(title: String) =
        """{"kind":"note","title":"$title","value":"<p>from the session</p>"}"""

    /** The opening turn every session spends before anything interesting happens. */
    private val opening = Turn("What would you like to get from our time?")

    /**
     * Let the main looper catch up.
     *
     * `messages` is a `stateIn(...)` over a `combine`, so it is published by a coroutine rather
     * than written directly — its `.value` lags whatever was just done until the looper runs. Left
     * out, the assertions on it read an empty list and pass for the wrong reason; the first draft
     * of this file did exactly that and "no cards yet" was green because there were no messages at
     * all.
     */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // ── The whole arc ───────────────────────────────────────────────────────

    @Test
    fun `record, then proposals, then goodbye, then out`() {
        val chat = ScriptedChat(
            listOf(
                opening,
                // The coach closes the session ITSELF, with the record and its proposals in the
                // same reply — the two steps the plumbing prescribes.
                Turn(
                    text = "",
                    proposals = listOf(note("What I'm taking from this")),
                    record = "Apply to three roles a week, starting Monday.",
                ),
                Turn("Well done for being honest about the waiting."),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)

        vm.startGrow(20)
        idle()
        assertEquals(ChatMode.GROW_ACTIVE, vm.mode.value)

        // The coach ends the session. Step 1: the record, and NOTHING else yet.
        vm.send("I think we're done.")
        idle()
        assertEquals(ChatMode.GROW_END, vm.mode.value)
        assertEquals("Apply to three roles a week, starting Monday.", vm.memoryDraft.value)
        assertEquals(
            "the ending turn's cards WAIT — the record is decided first",
            0,
            vm.messages.value.flatMap { it.proposals }.size,
        )

        // Step 2: the user keeps the record, and the held proposals are released.
        vm.closeSession(save = true)
        idle()
        assertEquals(ChatMode.GROW_REVIEW, vm.mode.value)
        assertEquals("Apply to three roles a week, starting Monday.", chat.savedMemory)
        val pending = vm.messages.value.flatMap { it.proposals }
        assertEquals(1, pending.size)
        assertEquals("What I'm taking from this", pending.single().title)
        assertEquals(ProposalStatus.PENDING, pending.single().status)

        // Step 3: answering the last card asks the coach for its goodbye, by itself.
        val message = vm.messages.value.first { it.proposals.isNotEmpty() }
        vm.settleProposal(message.id, pending.single().id, approved = true)
        idle()
        assertEquals(ChatMode.GROW_FAREWELL, vm.mode.value)
        val goodbyePrompt = chat.prompts.last()
        assertTrue(
            "the goodbye must be told what was actually kept: $goodbyePrompt",
            goodbyePrompt.contains("They saved the session record.") &&
                goodbyePrompt.contains("accepted 1 and declined 0"),
        )
        // …and the session does NOT exit on its own: the goodbye has to stay on screen.
        assertEquals(ChatMode.GROW_FAREWELL, vm.mode.value)
        assertTrue(
            vm.messages.value.last().content.contains("Well done"),
        )

        // Step 4: the user closes it.
        vm.leaveGrow()
        idle()
        assertEquals(ChatMode.CHAT, vm.mode.value)
        val note = vm.messages.value.single { it.role == ChatRole.SYSTEM }
        assertEquals("Session memory saved.", note.content)
    }

    @Test
    fun `discarding the record still goes through the proposals and the goodbye`() {
        val chat = ScriptedChat(
            listOf(
                opening,
                Turn(proposals = listOf(note("A thought")), record = "Some record."),
                Turn("Take care."),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        vm.send("done")
        idle()

        vm.closeSession(save = false)
        idle()

        assertEquals(ChatMode.GROW_REVIEW, vm.mode.value)
        assertEquals("nothing may be written when the user said no", null, chat.savedMemory)
        assertEquals(1, vm.messages.value.flatMap { it.proposals }.size)
    }

    @Test
    fun `an ending with nothing to propose skips straight to the goodbye`() {
        // "Proposing nothing at all is also a legitimate answer" — the method. The review step
        // must not appear as an empty screen the user has to dismiss.
        val chat = ScriptedChat(
            listOf(opening, Turn(record = "We named the block; no commitment yet."), Turn("Bye.")),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        vm.send("done")
        idle()

        vm.closeSession(save = true)
        idle()

        assertEquals(ChatMode.GROW_FAREWELL, vm.mode.value)
    }

    @Test
    fun `the coach's instructions never appear as the user's own words`() {
        // The wrap-up and goodbye instructions are addressed to the coach. One of them showed up
        // on screen as a chat bubble reading "[I am ending this session now, before it reached
        // its natural end. Close it honestly: …]" (owner, 2026-08-24) — the same leak the Edit
        // card had. The model must still receive them; the transcript must not.
        val chat = ScriptedChat(listOf(opening, Turn(record = "A record."), Turn("Bye.")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()
        idle()
        vm.closeSession(save = false)
        idle()

        val shown = vm.messages.value.filter { it.role == ChatRole.USER }.map { it.content }
        assertTrue(
            "no bracketed instruction may be on screen: $shown",
            shown.none { it.startsWith("[") },
        )
        // …and the coach was sent them all the same.
        assertTrue(chat.prompts.any { it.startsWith("[I am ending this session") })
        assertTrue(chat.prompts.any { it.startsWith("[The user has now decided") })
    }

    // ── The clock ───────────────────────────────────────────────────────────

    @Test
    fun `End asks the coach to close - it does not close the session itself`() {
        val chat = ScriptedChat(listOf(opening, Turn(record = "Short session.")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()
        idle()

        // The instruction is a request to the coach, in its own words, not a UI-fabricated end.
        val asked = chat.prompts.last()
        assertTrue("the coach must be asked to close honestly: $asked", asked.contains("end_session"))
        assertTrue(asked.contains("before it reached its natural end"))
        // A wrap-up turn reports zero left, so "there is room to explore" cannot argue back.
        assertEquals(0, chat.remainingSent.last())
        // And it got there through the coach's own `end_session`, not by the button.
        assertEquals(ChatMode.GROW_END, vm.mode.value)
        assertEquals("Short session.", vm.memoryDraft.value)
    }

    @Test
    fun `a coach that will not call end_session is still ended, on its own last words`() {
        // The backstop. Without it a session whose coach never ends it has nothing that can.
        val chat = ScriptedChat(listOf(opening, Turn("We covered the waiting, and stopped there.")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()
        idle()

        assertEquals(ChatMode.GROW_END, vm.mode.value)
        assertEquals("We covered the waiting, and stopped there.", vm.memoryDraft.value)
    }

    @Test
    fun `the record is the coach's, never its goodbye`() {
        // What the card offered to save used to be `messages.last { ASSISTANT }.content` — which,
        // by the time the card appeared, was the farewell. The owner asked what had actually been
        // saved to memory; the answer was "the goodbye", and this is the assertion that says so.
        val chat = ScriptedChat(
            listOf(
                opening,
                Turn(text = "Here is where we got to.", record = "Ask for a decision by Friday."),
                Turn("It was good to work with you."),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        vm.send("done")
        idle()

        assertEquals("Ask for a decision by Friday.", vm.memoryDraft.value)

        vm.closeSession(save = true)
        idle()
        assertEquals("Ask for a decision by Friday.", chat.savedMemory)
        assertTrue(
            "the goodbye must never reach the memory",
            chat.savedMemory?.contains("good to work with you") != true,
        )
    }
}
