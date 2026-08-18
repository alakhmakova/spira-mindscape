package com.spiramindscape.android.ui.ai

import com.spiramindscape.android.data.ai.AiApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A GROW session is a **separate, ephemeral** conversation: its messages must never land in the
 * general chat, nor be written to the persisted transcript. A regression here dropped the whole
 * coaching session into the chat, which defeats the point of a session (owner, 2026-08-17).
 *
 * The web enforces the same rule with its `gmsgs`/`msgs` split and a persist effect that returns
 * early `if (inGrow)` (`AiPanel.tsx`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiChatGrowSeparationTest {

    private val dispatcher = StandardTestDispatcher()

    /** A fake that answers with one token and records every transcript write. */
    private class RecordingChat : AiChat {
        val persisted = mutableListOf<String>()

        override fun streamChat(
            goalId: String?,
            message: String,
            history: List<AiApi.HistoryEntry>,
            provider: String,
            sessionType: String,
            attachments: List<AiApi.ChatAttachment>,
            sessionTotalMinutes: Int?,
            sessionRemainingSeconds: Int?,
        ): Flow<AiApi.ChatEvent> = flowOf(AiApi.ChatEvent.Token("Ok."), AiApi.ChatEvent.Done)

        override suspend fun listKeys() = listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "mistral-large-latest"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "MISTRAL"
        override suspend fun saveProvider(provider: String) = Unit
        override suspend fun getTranscript(goalId: String?): AiApi.StoredTranscript? = null
        override suspend fun putTranscript(goalId: String?, content: String): String? {
            persisted += content
            return "now"
        }
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a GROW session stays out of the chat and is never persisted`() = runTest(dispatcher) {
        val chat = RecordingChat()
        val vm = AiChatViewModel(goalId = "1", api = chat)
        advanceUntilIdle()

        // A normal chat turn is persisted.
        vm.send("plain chat message")
        advanceUntilIdle()
        val persistsAfterChat = chat.persisted.size
        assertTrue("a chat turn should persist", persistsAfterChat >= 1)
        assertTrue(vm.messages.value.any { it.content == "plain chat message" })

        // Start a GROW session: its opening turn shows in the panel. `runCurrent()`, not
        // `advanceUntilIdle()`, so the session's countdown timer (a `delay` loop) doesn't run to
        // zero and auto-close the session out from under the assertions.
        vm.startGrow(5)
        runCurrent()
        assertTrue(
            "the GROW opener should be in the displayed list",
            vm.messages.value.any { it.content.contains("GROW session") },
        )
        // …but nothing new is persisted, and what was persisted never carried the session.
        assertEquals(
            "a GROW turn must not write the transcript",
            persistsAfterChat,
            chat.persisted.size,
        )
        assertFalse(
            "the persisted chat must not contain the GROW conversation",
            chat.persisted.last().contains("GROW session"),
        )

        // Ending the session drops its messages; the chat is exactly what it was.
        vm.finishGrow()
        runCurrent()
        assertFalse(
            "GROW messages must not survive into the chat",
            vm.messages.value.any { it.content.contains("GROW session") },
        )
        assertTrue(
            "the earlier chat message is still there",
            vm.messages.value.any { it.content == "plain chat message" },
        )
    }
}
