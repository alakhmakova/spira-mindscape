package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.ui.ai.AiChat
import com.spiramindscape.android.ui.ai.AiChatViewModel
import com.spiramindscape.android.ui.ai.ProviderSheetContent
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "AI providers" sheet — a card per provider with its model dropdown, key hint and connect
 * form, plus Tavily below. Rendered to `app/build/reports/visual/provider-sheet.png` so it can be
 * held against the web panel it is ported from.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckProviderSheetTest : VisualCheckTestBase() {

    /** One provider connected with a model, one web-search key saved, the rest untouched. */
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

        override suspend fun listKeys() = listOf(
            AiApi.KeyInfo("ANTHROPIC", "…a91f", "claude-sonnet-4-6"),
            AiApi.KeyInfo("TAVILY", "…7d2c", null),
        )
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
    fun `the AI providers sheet`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                // The body on its own: the modal sheet that normally carries it draws into a
                // separate window, which the decorView screenshot can't see.
                Box(Modifier.fillMaxSize().background(Color.White), Alignment.BottomCenter) {
                    ProviderSheetContent(
                        viewModel = AiChatViewModel(goalId = "1", api = FakeChat()),
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        saveWindow("provider-sheet")
    }
}
