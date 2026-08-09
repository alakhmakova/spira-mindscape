package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.ui.ai.AiChat
import com.spiramindscape.android.ui.ai.AiChatHost
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
 * The assistant **as a drawer**: it must stop short of the top edge so the page it belongs to stays
 * visible behind the scrim, the way the web's `h-[88vh]` drawer does. Rendered to
 * `app/build/reports/visual/ai-drawer.png` — a full-height panel would look identical in a
 * semantics assertion, which is exactly why this one is checked by eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckAiDrawerTest : VisualCheckTestBase() {

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
    fun `the assistant opens as a drawer, not a screen`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                AiChatHost(
                    open = true,
                    onOpenChange = {},
                    panel = { onClose ->
                        AiChatScreen(
                            viewModel = AiChatViewModel(goalId = "1", api = FakeChat()),
                            onClose = onClose,
                        )
                    },
                    content = {
                        // Stand-in for the page underneath: it must still be visible up top.
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                "the page stays visible above the drawer",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
        saveWindow("ai-drawer")
    }
}
