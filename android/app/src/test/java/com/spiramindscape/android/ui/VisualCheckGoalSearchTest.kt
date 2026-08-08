package com.spiramindscape.android.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.ui.goals.GoalUiState
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.GoalWorkspaceScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The workspace header's goal switcher with its results card open — the one surface where the new
 * header overlaps the page, so it is worth looking at rather than only asserting on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckGoalSearchTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `typing in the header opens the goal switcher`() {
        val goal = GoalDetail(
            id = "g1", title = "Move to Lisbon", description = "", confidence = 5, deadline = null,
            progress = 0.4f, achieved = false,
            actions = emptyList(), obstacles = emptyList(), options = emptyList(),
            targets = emptyList(), resources = emptyList(),
        )
        val others = listOf(
            "Learn conversational Portuguese",
            "Run a half marathon",
            "Learn to sail",
        ).mapIndexed { i, title ->
            GoalSummary(
                id = "g${i + 2}", title = title, confidence = 5, deadline = null,
                progress = 0f, targetCount = 0, achieved = false,
            )
        }

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(
                    state = GoalUiState.Content(goal),
                    actions = GoalWorkspaceActions(),
                    user = user,
                    allGoals = others,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Search goals").performTextInput("Learn")
        compose.waitForIdle()
        saveWindow("goal-search")
    }
}
