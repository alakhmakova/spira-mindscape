package com.spiramindscape.android.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.ui.goals.GoalUiState
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.GoalWorkspaceScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckRealityDraftSaveTest : VisualCheckTestBase() {

    /**
     * DISABLED — hangs indefinitely under Robolectric. Now that a Reality item is created through a
     * [com.spiramindscape.android.ui.goals.NewRealitySheet] (a `ModalBottomSheet`) instead of an
     * inline page field, `performTextInput` into the sheet's focused field never lets the Compose
     * clock go idle (cursor-blink + the sheet's own animation), so the following `waitForIdle`
     * spins forever. Attempted `mainClock.advanceTimeBy(...) + autoAdvance = false` did not help.
     * This is the still-unresolved side of BUG-009 (see
     * `backlog/android-visual-test-suite-flaky-appnotidle.md`). Do NOT re-enable until that's fixed
     * — it wedges the whole `:app:testDebugUnitTest` run. The save wiring is meanwhile covered by
     * `GoalWorkspaceViewModelTest` (addReality) + the manual/visual check of the sheet.
     */
    @Ignore("Hangs: performTextInput inside a ModalBottomSheet never idles — BUG-009 (unresolved)")
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the new-action form saves via onAddReality`() {
        var saved: Pair<String, String>? = null
        val goal = GoalDetail(
            id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
            progress = 0f, achieved = false,
            actions = emptyList(), obstacles = emptyList(), options = emptyList(), targets = emptyList(), resources = emptyList(),
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(
                    state = GoalUiState.Content(goal),
                    actions = GoalWorkspaceActions(onAddReality = { kind, text -> saved = kind to text }),
                    user = user,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Reality").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add new action").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Describe this action").performTextInput("Talk to my coach")
        compose.onNodeWithText("Add action").performClick()

        assertEquals("actions" to "Talk to my coach", saved)
    }
}
