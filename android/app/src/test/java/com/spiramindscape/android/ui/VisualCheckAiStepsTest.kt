package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * A reply that proposes **three** things, and a note whose body is real markup (GRO-80).
 *
 * Two things here can only be checked by looking. The three changes must arrive as **one card
 * with steps** rather than a stack of three cards each with its own Accept/Dismiss — an assertion
 * that all three titles exist passes either way. And the note's "Read full content" must show
 * headings and bullets rather than the flat `stripHtml` paragraph it used to print.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckAiStepsTest : VisualCheckTestBase() {

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

        override suspend fun listKeys() = listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "mistral-large-latest"))
        override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
            AiApi.KeyInfo(provider, "…a91f", model)
        override suspend fun listProviderModels(provider: String) = emptyList<String>()
        override suspend fun updateKeyModel(provider: String, model: String) = Unit
        override suspend fun getProvider() = "MISTRAL"
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
    fun `three changes arrive as one stepped card`() {
        val target = proposalFromToolArgs(
            """{"kind":"target","title":"Run 5km three times a week","deadline_value":"2026-10-01"}""",
        )!!
        val option = proposalFromToolArgs(
            """{"kind":"option","title":"Join a running club for the long runs"}""",
        )!!
        // A note's body arrives in `value` (the tool's own shape — see `proposalFromToolArgs`).
        val note = proposalFromToolArgs(
            """{"kind":"note","title":"Training plan","value":"<h3>Week 1</h3><p>Keep every run <strong>conversational</strong>.</p><ul><li>Monday: 3km easy</li><li>Thursday: intervals</li><li>Sunday: <em>long</em> run</li></ul>"}""",
        )!!
        val transcript = com.spiramindscape.android.data.ai.encodeTranscript(
            listOf(
                ChatMessage("m1", ChatRole.USER, "Set me up for a 10k"),
                ChatMessage(
                    "m2",
                    ChatRole.ASSISTANT,
                    "Here's a starting point — three changes:",
                    proposals = listOf(target, option, note),
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
        saveWindow("ai-steps")

        // One card, worded as the web words it: the count, the position, and one Save for the lot.
        compose.onNodeWithText("3 CHANGES").assertIsDisplayed()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithText("Save all 3").assertIsDisplayed()

        // Walk to the note and open its body, so the sheet shows the formatted markup.
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText("Read full content").performClick()
        compose.waitForIdle()
        saveWindow("ai-steps-note")
    }

    /**
     * An **applied** note. The card is gone — the web keeps only a compact pill in the transcript
     * (`ResultSummary`) naming what was saved, with an Open shortcut. The full card only ever
     * exists while it is waiting to be answered, and then it lives in the footer.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `an applied note offers Open`() {
        val note = proposalFromToolArgs(
            """{"kind":"note","title":"Training plan","value":"<p>Keep every run <strong>easy</strong>.</p>"}""",
        )!!.copy(status = com.spiramindscape.android.data.ai.ProposalStatus.APPROVED)
        val transcript = com.spiramindscape.android.data.ai.encodeTranscript(
            listOf(
                ChatMessage("m1", ChatRole.USER, "Write down my plan"),
                ChatMessage("m2", ChatRole.ASSISTANT, "Saved it as a note:", proposals = listOf(note)),
            ),
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                AiChatScreen(
                    viewModel = AiChatViewModel(goalId = "1", api = FakeChat(transcript)),
                    onClose = {},
                    onOpenNote = {},
                )
            }
        }
        compose.waitForIdle()
        saveWindow("ai-note-applied")

        compose.onNodeWithText("Training plan").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed()
        // The card itself is not in the transcript any more.
        compose.onAllNodesWithText("Read full content").assertCountEquals(0)
    }
}
