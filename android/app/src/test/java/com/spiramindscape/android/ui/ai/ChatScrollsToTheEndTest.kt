package com.spiramindscape.android.ui.ai

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ChatMessage
import com.spiramindscape.android.data.ai.ChatRole
import com.spiramindscape.android.data.ai.encodeTranscript
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * **The end of the conversation is not the top of its last message** (owner, 2026-08-29: with the
 * keyboard up, "автопрокрутка чата не работает").
 *
 * `scrollToItem(lastIndex)` puts that item's TOP at the top of the viewport, and a list cannot
 * scroll past its own end — so with the composer at its resting height, where there is barely any
 * padding below the last message, the call clamps and lands at the end by accident. That is why
 * this looked right for months, and why the report is about the **keyboard**: the keyboard's
 * height becomes the transcript's bottom `contentPadding`, the clamp stops biting, and a reply
 * taller than the panel is parked on its FIRST line with the rest of it behind the composer.
 *
 * So the keyboard has to be in the test, and Robolectric's window reports no IME inset of its own
 * — the blindness `ChatFooterConventionTest` documents. It is dispatched by hand instead, onto
 * Compose's own view; without that these tests render the accidental clamp and prove nothing.
 * Both were checked red against `scrollToItem` alone.
 *
 * Two classes rather than two methods in one, for the reason `VisualCheckTestBase` documents:
 * `forkEvery` restarts the test JVM per *class*, and Compose/Robolectric state leaks between
 * methods that share one.
 */

/** The transcript is at its end while a reply streams in with the keyboard already up. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScrollsToTheEndTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a streaming reply taller than the panel lands on its end`() {
        val opening = encodeTranscript(
            listOf(ChatMessage("m1", ChatRole.USER, "How do I get a toddler back to sleep?")),
        )
        val viewModel = AiChatViewModel(goalId = "1", api = FakeChat(opening))

        compose.setContent { SpiraTheme { AiChatScreen(viewModel = viewModel, onClose = {}) } }
        compose.waitForIdle()

        compose.raiseKeyboard()
        compose.runOnUiThread { viewModel.send("How do I get a toddler back to sleep?") }
        compose.waitForIdle()

        compose.assertTranscriptIsAtItsEnd()
    }
}

/** And it comes down to meet the composer when the keyboard opens on a reply already there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScrollsToTheEndWhenTheKeyboardOpensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the keyboard brings the end of a long reply down to the composer`() {
        val conversation = encodeTranscript(
            listOf(
                ChatMessage("m1", ChatRole.USER, "How do I get a toddler back to sleep?"),
                ChatMessage("m2", ChatRole.ASSISTANT, LONG_ANSWER),
            ),
        )

        compose.setContent {
            SpiraTheme {
                AiChatScreen(
                    viewModel = AiChatViewModel(goalId = "1", api = FakeChat(conversation)),
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.raiseKeyboard()

        compose.assertTranscriptIsAtItsEnd()
    }
}

private val LONG_ANSWER = (1..30).joinToString("\n\n") {
    "Paragraph $it of an answer that runs on well past the height of the panel."
}

/** A stand-in for the server: a saved key, a stored transcript, and one long reply. */
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
    ): Flow<AiApi.ChatEvent> = flowOf(AiApi.ChatEvent.Token(LONG_ANSWER), AiApi.ChatEvent.Done)

    override suspend fun listKeys() = listOf(AiApi.KeyInfo("MISTRAL", "…a91f", "magistral-medium-latest"))
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

private typealias ChatRule = AndroidComposeTestRule<*, ComponentActivity>

/**
 * A lazy list reports its position as a scroll range, and `value == maxValue` is exactly "there is
 * nothing further down to scroll to" — the end of the conversation, wherever inside the last
 * message that happens to fall.
 */
private fun ChatRule.assertTranscriptIsAtItsEnd() {
    val range = onNodeWithTag(CHAT_TRANSCRIPT_TAG)
        .fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]
    assertEquals(
        "the transcript stopped short of the end of the conversation — a long reply is parked " +
            "on its first line with the rest of it behind the composer",
        range.maxValue(),
        range.value(),
        0.5f,
    )
}

/** The keyboard, since this host has none: a bottom IME inset, dispatched to Compose's own view. */
private fun ChatRule.raiseKeyboard() {
    runOnUiThread {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, KEYBOARD_PX))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        ViewCompat.dispatchApplyWindowInsets(composeView(activity.window.decorView), insets)
    }
    waitForIdle()
}

/** Compose listens for insets on its own view, not on the decor above it. */
private fun composeView(view: View): View {
    if (view.javaClass.simpleName == "AndroidComposeView") return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = runCatching { composeView(view.getChildAt(i)) }.getOrNull()
            if (found != null) return found
        }
    }
    error("no AndroidComposeView under the activity")
}

/** About what a soft keyboard takes on the phone these tests render (density 1). */
private const val KEYBOARD_PX = 330
