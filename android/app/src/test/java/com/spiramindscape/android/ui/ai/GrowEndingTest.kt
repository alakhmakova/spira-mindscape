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
        /** Non-null when the provider fails this turn instead of answering (e.g. a rate limit). */
        val error: String? = null,
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
                if (turn.error != null) {
                    // A real provider failure: no Done follows an Error (AiApi.kt's `dispatch`
                    // treats "error" as terminal, same as "done").
                    add(AiApi.ChatEvent.Error(turn.error))
                } else {
                    if (turn.text.isNotEmpty()) add(AiApi.ChatEvent.Token(turn.text))
                    turn.proposals.forEach { add(AiApi.ChatEvent.Proposal(it)) }
                    turn.record?.let {
                        add(AiApi.ChatEvent.SessionEnd("""{"summary":"$it"}"""))
                    }
                    add(AiApi.ChatEvent.Done)
                }
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
    fun `closeSession and leaveGrow ignore a second call - no duplicate messages`() {
        // Owner report, 2026-09-02: "I've prepared this for your review." and "Session memory
        // saved." each appeared twice in a row in a real session. Neither `closeSession` nor
        // `leaveGrow` guarded itself (every other one-shot ending step already does — `wrapUpSent`,
        // `goodbyeSent`), so a double-tap on Save/Discard, or any other double invocation, ran the
        // whole body — including the transcript append — twice.
        val chat = ScriptedChat(
            listOf(
                opening,
                Turn(proposals = listOf(note("What I'm taking from this")), record = "A record."),
                Turn("Take care."),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        vm.send("done")
        idle()

        // Two rapid calls, exactly as a double-click would produce.
        vm.closeSession(save = false)
        vm.closeSession(save = false)
        idle()
        assertEquals(
            "the review card must appear exactly once",
            1,
            vm.messages.value.count { it.proposals.isNotEmpty() },
        )

        val message = vm.messages.value.first { it.proposals.isNotEmpty() }
        vm.settleProposal(message.id, message.proposals.single().id, approved = false)
        idle()

        vm.leaveGrow()
        vm.leaveGrow()
        idle()
        assertEquals(ChatMode.CHAT, vm.mode.value)
        assertEquals(
            "the closing note must appear exactly once",
            1,
            vm.messages.value.count { it.role == ChatRole.SYSTEM },
        )
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
        // card had. The model must still receive them; the transcript must not. (That particular
        // instruction is gone — End no longer speaks to the coach at all — but the leak it caused
        // is a property of every bracketed instruction, so this guards the ones that remain.)
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
        assertTrue(chat.prompts.any { it.startsWith("[We are well past the time") })
        assertTrue(chat.prompts.any { it.startsWith("[The user has now decided") })
    }

    // ── The clock ───────────────────────────────────────────────────────────

    @Test
    fun `a coach-driven close asks the coach - it does not close the session itself`() {
        // This is the overtime backstop's path, and the shape the ending takes when the
        // conversation itself finishes. It is NOT the End button: End is `endGrowNow`, which
        // sends nothing and needs no provider (owner, 2026-09-08).
        val chat = ScriptedChat(listOf(opening, Turn(record = "Short session.")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()
        idle()

        // The instruction is a request to the coach, in its own words, not a UI-fabricated end.
        val asked = chat.prompts.last()
        assertTrue("the coach must be asked to close honestly: $asked", asked.contains("end_session"))
        assertTrue(asked.contains("wrap it up now"))
        // A wrap-up turn reports zero left, so "there is room to explore" cannot argue back.
        assertEquals(0, chat.remainingSent.last())
        // And it got there through the coach's own `end_session`, not by the button.
        assertEquals(ChatMode.GROW_END, vm.mode.value)
        assertEquals("Short session.", vm.memoryDraft.value)
    }

    @Test
    fun `a wrap-up that fails still ends the session, not stuck with End dead forever`() {
        // Owner report, 2026-09-02: once a session ran into overtime, End stopped doing anything.
        // Root cause — `wrapUpSent` was set true right before the wrap-up request and, on a
        // provider failure (Mistral's rate limit, in the reported session), nothing ever reset
        // it: neither the End pill (`canEndEarly` needs `!wrapUpSent`) nor the ten-minute backstop
        // could ever try again, and the session simply sat there. The fix is that a *failed*
        // wrap-up turn still ends the session (`beginEnding("")`), the same way the web's
        // `finishGrow()` already does on its own wrap-up error.
        val chat = ScriptedChat(listOf(opening, Turn(error = "Rate limit exceeded")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()
        idle()

        assertEquals(ChatMode.GROW_END, vm.mode.value)
        // Nothing streaming and nothing left half-done — the failed turn's own error bubble is
        // what explains it.
        assertEquals(false, vm.streaming.value)
        // **And the record is empty, not borrowed.** It used to fall back to the last thing the
        // coach had said — here the opening question, which is not a record of anything. On a
        // wordless ending turn that fallback picked up the sentence the app itself had invented
        // and offered to save it as the session's memory (owner, 2026-09-08). A record nobody
        // wrote stays empty, and `closeSession` refuses to save a blank one.
        assertEquals("", vm.memoryDraft.value)
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

    /**
     * **End is the way out, and it needs nobody.** The owner's session was rate-limited by the
     * provider: she pressed End, then Stop, and from then on there was no way to finish at all
     * (2026-09-08). Two faults met — End asked the coach to write a closing record (so it went
     * through the thing that was broken), and Stop left `wrapUpSent` set, which is the flag End
     * bails on. Now End cancels whatever is in flight and leaves, on its own.
     */
    @Test
    fun `End leaves the session even while a turn is in flight and the provider is failing`() {
        val chat = ScriptedChat(listOf(opening, Turn(error = "Rate limit exceeded")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.send("this one will be refused")
        vm.endGrowNow()
        idle()

        assertEquals(ChatMode.CHAT, vm.mode.value)
        assertEquals(false, vm.streaming.value)
        // Nothing was written: End keeps nothing, by design.
        assertEquals(null, chat.savedMemory)
    }

    @Test
    fun `Stop during a wrap-up does not leave the session unendable`() {
        val chat = ScriptedChat(listOf(opening, Turn("...")))
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.closeGrow()      // asks the coach to wrap up — sets the one-shot guard
        vm.cancelStream()   // Stop, before the answer arrives
        idle()

        // The cancelled turn released its guard, so the coach-driven close can be asked for again
        // — and End, which no longer consults the guard at all, always works.
        vm.closeGrow()
        idle()
        assertEquals(ChatMode.GROW_END, vm.mode.value)
    }

    /**
     * **STEP 2 keeps its proposals even if the coach ends the session again.**
     *
     * The instruction for that turn says not to call `end_session` — but the timing block sent
     * with it says "the planned time is up, end the session now", and a model that obeys the
     * louder of the two used to have every proposal in that reply thrown away: the turn counted
     * as an ending, so its cards were suppressed as "held", and nothing held them. The session
     * then finished having changed nothing about the goal — the exact failure splitting the
     * ending into steps was meant to fix.
     */
    @Test
    fun `proposals survive a coach that calls end_session on the proposals turn`() {
        val chat = ScriptedChat(
            listOf(
                opening,
                // The real ending: a record, no proposals (STEP 1).
                Turn(record = "Send the application on Tuesday."),
                // STEP 2 — proposes, and ends the session a second time.
                Turn(
                    text = "Here is what belongs in the goal.",
                    proposals = listOf(note("Send the application on Tuesday")),
                    record = "Send the application on Tuesday.",
                ),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        vm.startGrow(20)
        idle()

        vm.send("I think we're done.")
        idle()
        assertEquals(ChatMode.GROW_END, vm.mode.value)

        // Deciding the record asks STEP 2, which answers with a card.
        vm.closeSession(save = false)
        idle()

        assertEquals(ChatMode.GROW_REVIEW, vm.mode.value)
        val pending = vm.messages.value.flatMap { it.proposals }
        assertEquals(1, pending.size)
        assertEquals("Send the application on Tuesday", pending.single().title)
    }
}
