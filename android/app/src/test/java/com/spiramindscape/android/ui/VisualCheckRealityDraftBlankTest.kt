package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
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
class VisualCheckRealityDraftBlankTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp") // realistic phone window so the sheet isn't clipped
    fun `add new action opens a create form that can be cancelled`() {
        val goal = GoalDetail(
            id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
            progress = 0f, achieved = false,
            actions = listOf(TextItem("a1", "Existing action")),
            obstacles = emptyList(), options = emptyList(), targets = emptyList(), resources = emptyList(),
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

        // "Add new action" opens the "New action" form (a sheet); Cancel dismisses it, so an
        // aborted create is never a dead end.
        compose.onNodeWithText("Add new action").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New action").assertIsDisplayed()
        compose.onNodeWithText("Add action").assertIsDisplayed()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Existing action").assertIsDisplayed()
    }
}
