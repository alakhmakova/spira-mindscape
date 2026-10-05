package com.spiramindscape.android.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ProposalStatus
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **What happens to a proposal card when it is answered** — Accept, Dismiss and Edit, driven
 * through the real screen and the real view model (owner, 2026-08-24).
 *
 * All three were reported from the phone, and none of them had a test on either surface. The one
 * that was actually broken was **Edit**: it went out as an ordinary `send(...)`, so the revised
 * proposal arrived as a *second* card while the original stayed PENDING in the footer. Worse, only
 * the first pending message gets the footer, so the new card fell through to the transcript's
 * result line — which, having nothing approved in it, drew the card the user had just asked for as
 * a muted **"Dismissed"** pill that could not be answered at all.
 *
 * These press the buttons rather than calling the view model, because every one of those failures
 * is about **what is on screen**: how many cards there are, which one, and what the transcript says
 * underneath. A view-model assertion would have been green throughout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ProposalCardLifecycleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** One scripted assistant turn: some prose, and the proposals it comes with. */
    private data class Turn(val text: String, val proposals: List<String> = emptyList())

    private fun note(title: String, bodyHtml: String, proposalId: Long? = null): String =
        buildString {
            append("""{"kind":"note","title":"""").append(title)
            append("""","value":"""").append(bodyHtml).append('"')
            proposalId?.let { append(""","proposalId":""").append(it) }
            append('}')
        }

    /**
     * A transport that answers from a script, one [Turn] per call, and records what it was asked.
     * The prompt is kept because the Edit path builds one and the transcript must NOT show it.
     */
    private class ScriptedChat(private val script: List<Turn>) : AiChat {
        var calls = 0
            private set
        val prompts = mutableListOf<String>()
        val rejected = mutableListOf<Long>()
        val approved = mutableListOf<Long>()

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
            val turn = script.getOrNull(calls++) ?: Turn("")
            val events = buildList {
                if (turn.text.isNotEmpty()) add(AiApi.ChatEvent.Token(turn.text))
                turn.proposals.forEach { add(AiApi.ChatEvent.Proposal(it)) }
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
        override suspend fun saveGoalMemory(goalId: String, summary: String) = Unit
        override suspend fun approveProposal(id: Long) { approved += id }
        override suspend fun rejectProposal(id: Long) { rejected += id }
    }

    private val placeholder = "Ask, plan, or request an action…"

    private fun show(vm: AiChatViewModel) {
        compose.setContent {
            SpiraTheme {
                AiChatScreen(
                    viewModel = vm,
                    onClose = {},
                    // Applied without complaint, as the goal screen does when the write succeeds.
                    onApplyProposal = { _, _, _ -> null },
                    onOpenNote = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun ask(text: String) {
        compose.onNodeWithText(placeholder).performTextInput(text)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitForIdle()
    }

    /** How many nodes with this exact label are composed right now. */
    private fun countOf(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** How many answerable cards are on screen. Every card carries exactly one Accept. */
    private fun cardsOnScreen(): Int = countOf("Accept")

    // ── Accept ──────────────────────────────────────────────────────────────

    @Test
    fun `Accept leaves one result line, and it carries an Open link to the note`() {
        val chat = ScriptedChat(
            listOf(Turn("", listOf(note("Interview prep", "<p>Questions to prepare</p>", 11)))),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        show(vm)

        ask("Create a note titled Interview prep")
        compose.onNodeWithText("Interview prep").assertIsDisplayed()

        compose.onNodeWithText("Accept").performClick()
        compose.waitForIdle()

        // The card is gone — only the compact result line stays.
        assertEquals(0, cardsOnScreen())
        // …and it is the LINK the owner asked for, on Android as on the web.
        compose.onNodeWithText("Open").assertIsDisplayed()
        compose.onNodeWithText("Interview prep").assertIsDisplayed()

        val proposal = vm.messages.value.flatMap { it.proposals }.single()
        assertEquals(ProposalStatus.APPROVED, proposal.status)
        // The decision reached the server, so it survives a reload.
        assertEquals(listOf(11L), chat.approved)
    }

    // ── Dismiss ─────────────────────────────────────────────────────────────

    @Test
    fun `a dismissed card stays dismissed - talking on, and asking again, never revives it`() {
        val chat = ScriptedChat(
            listOf(
                Turn("", listOf(note("Salary research", "<p>Ranges</p>", 21))),
                Turn("Here are three things you could do next."),
                Turn("", listOf(note("Company research", "<p>Who they are</p>", 22))),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        show(vm)

        ask("Create a note titled Salary research")
        compose.onNodeWithText("Dismiss").performClick()
        compose.waitForIdle()

        assertEquals(0, cardsOnScreen())
        compose.onNodeWithText("Dismissed").assertIsDisplayed()
        assertEquals(listOf(21L), chat.rejected)

        // Carry on talking: an ordinary turn must not bring the card back.
        ask("Thanks. What else could help me here?")
        assertEquals(0, cardsOnScreen())

        // Ask for another note: exactly one card, and it is the NEW one.
        ask("Create a note titled Company research")
        assertEquals(1, cardsOnScreen())
        compose.onNodeWithText("Company research").assertIsDisplayed()

        val settled = vm.messages.value.flatMap { it.proposals }
        assertEquals(
            "the dismissed proposal must still be REJECTED",
            ProposalStatus.REJECTED,
            settled.single { it.title == "Salary research" }.status,
        )
        assertEquals(
            "and only the new one may be waiting",
            1,
            settled.count { it.status == ProposalStatus.PENDING },
        )
    }

    // ── Edit ────────────────────────────────────────────────────────────────

    @Test
    fun `Edit replaces the card in place - never a second one, and never a false Dismissed`() {
        val chat = ScriptedChat(
            listOf(
                Turn("", listOf(note("Interview prep", "<p>Questions</p>", 31))),
                Turn("", listOf(note("SQL interview prep", "<p>Joins, indexes</p>", 32))),
            ),
        )
        val vm = AiChatViewModel(goalId = "1", api = chat)
        show(vm)

        ask("Create a note titled Interview prep")
        val originalId = vm.messages.value.flatMap { it.proposals }.single().id

        compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("e.g. make it shorter, or due next Friday")
            .performTextInput("make it about SQL")
        compose.waitForIdle()
        compose.onNodeWithText("Send to AI").performClick()
        compose.waitForIdle()

        // ONE card, and it is the revised one.
        assertEquals(1, cardsOnScreen())
        compose.onNodeWithText("SQL interview prep").assertIsDisplayed()

        // The old card must not linger anywhere — not as a card, and not as the muted
        // "Dismissed" pill it used to be drawn as when it fell out of the footer.
        assertEquals(0, countOf("Dismissed"))

        val proposals = vm.messages.value.flatMap { it.proposals }
        assertEquals("exactly one proposal survives the revision", 1, proposals.size)
        assertEquals(ProposalStatus.PENDING, proposals.single().status)
        assertEquals(
            "the revision takes the original card's slot, so its id is unchanged",
            originalId,
            proposals.single().id,
        )
        // The superseded server row is closed, or it comes back later as a card nobody asked for.
        assertEquals(listOf(31L), chat.rejected)

        // The transcript shows what the USER asked for, not the prompt built around it.
        compose.onNodeWithText("make it about SQL").assertIsDisplayed()
        assertEquals(
            "the model's scaffolding must never be shown as a chat bubble",
            0,
            countOf("Revise the resource note you proposed."),
        )
        assertTrue(
            "…though the model must still be sent it",
            chat.prompts.last().contains("Current proposal:"),
        )
    }
}
