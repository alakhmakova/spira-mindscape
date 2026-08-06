package com.spiramindscape.android.ui

import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ChatMessage
import com.spiramindscape.android.data.ai.ChatRole
import com.spiramindscape.android.data.ai.proposalFromToolArgs
import com.spiramindscape.android.ui.ai.AiChat
import com.spiramindscape.android.ui.ai.AiChatScreen
import com.spiramindscape.android.ui.ai.AiChatViewModel
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the assistant panel — a user turn, a streamed reply with Markdown, and a proposal card —
 * to `app/build/reports/visual/ai-chat.png`, so the bubbles, the card and the composer can be
 * checked by eye (CLAUDE.md → "Verify UI changes visually").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckAiChatTest : VisualCheckTestBase() {

    /** A stand-in for the server: a saved key, and a transcript already holding a conversation. */
    private class FakeChat(private val transcript: String) : AiChat {
        override fun streamChat(
            goalId: String?,
            message: String,
            history: List<AiApi.HistoryEntry>,
            provider: String,
            sessionType: String,
            attachments: List<AiApi.ChatAttachment>,
            sessionTotalMinutes: Int?,
            sessionRemainingSeconds: Int?,
        ): Flow<AiApi.ChatEvent> = flowOf(AiApi.ChatEvent.Done)

        override suspend fun listKeys() = listOf(AiApi.KeyInfo("ANTHROPIC", "…a91f", "claude-opus-5"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "ANTHROPIC"
        override suspend fun saveProvider(provider: String) = Unit
        override suspend fun getTranscript(goalId: String?) = AiApi.StoredTranscript(transcript, "now")
        override suspend fun putTranscript(goalId: String?, content: String): String? = "now"
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `assistant panel with a reply and a proposal card`() {
        val proposal = proposalFromToolArgs(
            """{"kind":"target","title":"Run 5km three times a week",
                "items":[{"text":"Monday run"},{"text":"Wednesday run"},{"text":"Saturday long run"}],
                "deadline_value":"2026-10-01","reasoning":"Three sessions is the usual base for a first race."}""",
        )!!
        val transcript = com.spiramindscape.android.data.ai.encodeTranscript(
            listOf(
                ChatMessage("m1", ChatRole.USER, "How should I train for a 10k?"),
                ChatMessage(
                    "m2",
                    ChatRole.ASSISTANT,
                    "Start with **three runs a week** and build slowly:\n\n" +
                        "- one easy run\n- one interval session\n- one long run\n\n" +
                        "Add no more than 10% distance each week.",
                    proposals = listOf(proposal),
                ),
            ),
        )

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                AiChatScreen(
                    viewModel = AiChatViewModel(goalId = "1", api = FakeChat(transcript)),
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()
        saveWindow("ai-chat")
    }
}
