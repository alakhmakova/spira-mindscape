package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.ui.ai.AI_DRAWER_TAG
import com.spiramindscape.android.ui.ai.AiChat
import com.spiramindscape.android.ui.ai.AiChatHost
import com.spiramindscape.android.ui.ai.AiChatScreen
import com.spiramindscape.android.ui.ai.AiChatViewModel
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
import com.spiramindscape.android.ui.components.GoalWorkspaceTopBar
import com.spiramindscape.android.ui.components.GrowTabsRow
import com.spiramindscape.android.ui.theme.SpiraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The assistant's drawer must not cover the page's header** (owner, 2026-08-29: "на андроид
 * drawer стал слишком высоким, он не должен перекрывать хедер на андроид").
 *
 * It did, and the reason is worth keeping: the height used to be `0.92` of the box `AiChatHost`
 * fills, and because `MainActivity` runs `enableEdgeToEdge()` that box is the **whole screen,
 * status bar included**. The remaining 8 % is about a status bar, so the drawer's top edge landed
 * inside `GoalWorkspaceTopBar`. The same 92 % reads correctly on the web only because `--app-vh`
 * is one percent of the *layout viewport*, which Chrome has already trimmed.
 *
 * So this renders the **real** chrome — the top bar and the GROW tabs, not a stand-in — under the
 * real drawer, and compares their bounds. A stand-in would have proved nothing: the whole defect
 * is a number that is only wrong relative to how tall the app's own header is.
 *
 * It also writes `app/build/reports/visual/ai-drawer-clears-header.png`, because "does not
 * overlap" and "leaves a gap you can see" are different things and only the picture shows the
 * second.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiDrawerClearsTheHeaderTest : VisualCheckTestBase() {

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
    fun `the drawer opens below the workspace header, not over it`() {
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
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            GoalWorkspaceTopBar(
                                query = "",
                                onQueryChange = {},
                                onHome = {},
                                onDelete = {},
                            )
                            GrowTabsRow(
                                labels = listOf("Goal", "Reality", "Options", "Will do"),
                                selectedIndex = 0,
                                onSelect = {},
                            )
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
        saveWindow("ai-drawer-clears-header")

        val tabs = compose.onNodeWithTag(GROW_TABS_TAG).getUnclippedBoundsInRoot()
        val drawer = compose.onNodeWithTag(AI_DRAWER_TAG).getUnclippedBoundsInRoot()
        val gap = drawer.top - tabs.bottom
        val drawerHeight = drawer.bottom - drawer.top

        // Not merely "does not overlap": a drawer flush against the tab bar is what the owner
        // photographed and called too tall. There has to be a band of page you can see.
        //
        // **8dp, because the web's own gap is 11.** On the owner's phone the web drawer is 92 %
        // of an 888px viewport, so its top edge is 71px below a ~60px app header — 11 clear. The
        // Android numbers come out the same by construction: the chrome measures 111dp under its
        // status-bar padding and `HEADER_CLEARANCE` is 122. Robolectric reports a status-bar
        // inset of 0 here, which does not weaken the test: the top bar pads by that same inset,
        // so it cancels on both sides and the gap is inset-independent.
        assertTrue(
            "the drawer's top edge is ${drawer.top} and the GROW tabs end at ${tabs.bottom} — " +
                "gap $gap, expected at least 8dp. HEADER_CLEARANCE in AiChatHost.kt no longer " +
                "clears the workspace chrome.",
            gap >= 8.dp,
        )
        // …and it is still a drawer, not a stub: it must take most of what is left.
        assertTrue(
            "the drawer is only $drawerHeight tall — HEADER_CLEARANCE is too generous.",
            drawerHeight >= 600.dp,
        )
    }
}
