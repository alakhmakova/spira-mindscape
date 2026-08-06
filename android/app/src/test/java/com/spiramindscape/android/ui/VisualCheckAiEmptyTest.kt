package com.spiramindscape.android.ui

import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TextItem
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
 * The panel's opening screen — the leaf medallion, the orientation line and the prompts built from
 * the goal's actual state — rendered to `app/build/reports/visual/ai-chat-empty.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckAiEmptyTest : VisualCheckTestBase() {

    private class FakeChat : AiChat {
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
        override suspend fun getTranscript(goalId: String?): AiApi.StoredTranscript? = null
        override suspend fun putTranscript(goalId: String?, content: String): String? = null
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `empty chat with goal-shaped prompts`() {
        // Low confidence and two obstacles: the prompts should speak to exactly that.
        val goal = GoalDetail(
            id = "1", title = "Change career into product design", description = "",
            confidence = 2, deadline = null, progress = 0f, achieved = false,
            actions = emptyList(),
            obstacles = listOf(TextItem("o1", "No portfolio"), TextItem("o2", "No time after work")),
            options = emptyList(), targets = emptyList(), resources = emptyList(),
        )

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                AiChatScreen(
                    viewModel = AiChatViewModel(goalId = "1", api = FakeChat()),
                    onClose = {},
                    goal = goal,
                )
            }
        }
        compose.waitForIdle()
        saveWindow("ai-chat-empty")
    }
}
