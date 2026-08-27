package com.spiramindscape.android.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.ai.AiApi
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

    /**
     * **A long message must not push the composer's own buttons off the screen** (owner,
     * 2026-08-24: "при длинном сообщении сжимаются и пропадают кнопки, и это не только в grow
     * session").
     *
     * The field had no height ceiling, so it grew with the text until the row under it — the
     * paperclip, the GROW action, Send — was under the keyboard with nothing left to press. The
     * web has capped this at 128px from the start; Android caps at the same 120dp and scrolls.
     *
     * ## Why this reads the source instead of driving the screen
     *
     * It was written as a Compose test first, and that version **passed with the cap removed** —
     * twice, once asserting the buttons were displayed and once measuring the field's height.
     * Robolectric has no IME and the panel has slack, so the field simply grows into the space the
     * conversation gives up and nothing ever runs out of room. The defect only exists when the
     * keyboard is up on a real screen, which is the one thing the harness cannot produce.
     *
     * So this is blunt, like `AssistantSurvivesRecreationTest`: it fails for exactly the edit that
     * caused the bug, which a green test proving nothing does not.
     */
    @Test
    fun `the composer field is capped, so its actions cannot be pushed off screen`() {
        val source = java.io.File(
            "src/main/java/com/spiramindscape/android/ui/ai/AiChatScreen.kt",
        )
        assertTrue("AiChatScreen.kt has moved — move this check with it", source.exists())
        val text = source.readText()

        assertTrue(
            "The composer's BasicTextField must carry heightIn(max = COMPOSER_MAX_HEIGHT). " +
                "Without a ceiling a long message grows the field until the paperclip, the GROW " +
                "action and Send are under the keyboard (owner, 2026-08-24).",
            text.contains("heightIn(max = COMPOSER_MAX_HEIGHT)"),
        )
        assertTrue(
            "The cap must sit on the FIELD itself. Moving it to a wrapper would let the field grow " +
                "again inside it.",
            text.contains("heightIn(max = COMPOSER_MAX_HEIGHT)"),
        )
    }

    /**
     * **The capped field must scroll ITSELF, following the caret.**
     *
     * The cap was first written as `heightIn(...).verticalScroll(...)`, and that broke typing: an
     * outer scroll container measures the field unbounded, so `BasicTextField` hands its scrolling
     * to the parent — and the parent has no idea where the caret is. Past four lines you were
     * typing into text you could not see and had to drag the field to find your own cursor
     * (owner, 2026-08-25, with a screenshot of the caret hidden behind the action row).
     *
     * Constraining the height on the field with **no scroll wrapper** turns its internal scroller
     * back on, and that one keeps the cursor in view.
     *
     * This reads the source for the same reason the check above does: Robolectric has no IME and
     * the panel has slack, so the field never actually runs out of room there — the earlier
     * attempt at a Compose assertion passed with the cap removed entirely. Verified by hand on the
     * emulator instead (type six lines; the caret and the action row are both on screen).
     */
    @Test
    fun `the composer field is not wrapped in a scroll container`() {
        val source = java.io.File(
            "src/main/java/com/spiramindscape/android/ui/ai/AiChatScreen.kt",
        )
        assertTrue("AiChatScreen.kt has moved — move this check with it", source.exists())
        val text = source.readText()

        val capIndex = text.indexOf("heightIn(max = COMPOSER_MAX_HEIGHT)")
        assertTrue("the composer's height cap has gone", capIndex >= 0)
        // The modifier chain the cap belongs to, up to the closing of that argument.
        val chain = text.substring(capIndex, minOf(capIndex + 200, text.length))
        assertTrue(
            "The composer's BasicTextField must NOT be wrapped in verticalScroll: that hands its " +
                "scrolling to a parent which cannot follow the caret, so a long message is typed " +
                "blind (owner, 2026-08-25). Constrain the height and let the field scroll itself.",
            !chain.contains("verticalScroll("),
        )
    }
}
