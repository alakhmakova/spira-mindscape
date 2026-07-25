package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TextItem
import com.spiramindscape.android.ui.goals.GoalUiState
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.GoalWorkspaceScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckRealityTabTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp") // a realistic phone window (not the 1400dp-tall one)
    fun `reality tab shows the actions and obstacles toggle with marker icons`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = listOf(TextItem("a1", "Talked to my mentor"), TextItem("a2", "Drafted a plan")),
            obstacles = listOf(TextItem("o1", "Not enough time in the evenings")),
            options = emptyList(), targets = emptyList(), resources = emptyList(),
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = GoalWorkspaceActions(), user = user)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Reality").performClick()
        compose.waitForIdle()
        saveWindow("reality-tab-actions")

        // Obstacles state is verified via semantics (not a second screenshot) — the layout is
        // identical code, only the marker icon/color/copy differ, already covered by the render.
        compose.onNodeWithText("Obstacles").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not enough time in the evenings").assertIsDisplayed()
        // The add affordance is the Guava "+" FAB (icon-only), and it re-labels itself per the
        // selected Reality kind — so on the obstacles list it must read "Add obstacle".
        compose.onNodeWithContentDescription("Add obstacle").assertIsDisplayed()
    }
}
