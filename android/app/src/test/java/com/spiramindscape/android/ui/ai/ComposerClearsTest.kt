package com.spiramindscape.android.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The composer empties itself when the message goes (GRO-80).
 *
 * The text used to stay in the field after Send, so the next thing you typed was appended to the
 * message you had just sent. The cause was the shared `InlineEditText`, which keeps its own copy
 * of the text and re-seeds it from `value` **only while unfocused** — a deliberate guard against a
 * background refetch wiping what someone is typing, and exactly wrong for a field that keeps focus
 * through the act of sending. This test is here so nobody puts that component back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ComposerClearsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class SilentChat : AiChat {
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

        override suspend fun listKeys() = listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "mistral-large-latest"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "MISTRAL"
        override suspend fun saveProvider(provider: String) = Unit
        override suspend fun getTranscript(goalId: String?) = AiApi.StoredTranscript("", "now")
        override suspend fun putTranscript(goalId: String?, content: String): String? = "now"
        override suspend fun deleteTranscript(goalId: String?) = Unit
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) = Unit
        override suspend fun rejectProposal(id: Long) = Unit
    }

    @Test
    fun `sending empties the field`() {
        val viewModel = AiChatViewModel(goalId = "1", api = SilentChat())
        compose.setContent {
            SpiraTheme { AiChatScreen(viewModel = viewModel, onClose = {}) }
        }
        compose.waitForIdle()

        val placeholder = "Ask, plan, or request an action…"
        compose.onNodeWithText(placeholder).performTextInput("How should I train for a 10k?")
        compose.waitForIdle()
        compose.onNodeWithText("How should I train for a 10k?").assertIsDisplayed()

        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitForIdle()

        // The message left the field: the placeholder is back, which it only is when the field is
        // genuinely empty.
        compose.onNodeWithText(placeholder).assertIsDisplayed()
        // And it really was sent, rather than merely cleared.
        assertEquals("How should I train for a 10k?", viewModel.messages.value.firstOrNull()?.content)
    }
}
