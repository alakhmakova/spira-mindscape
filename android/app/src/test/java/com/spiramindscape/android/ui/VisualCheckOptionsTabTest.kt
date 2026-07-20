package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.OptionItem
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
class VisualCheckOptionsTabTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `options tab shows the teal header and numbered option cards`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(),
            options = listOf(
                OptionItem("o1", "Take an evening course twice a week", selected = true, position = 0),
                OptionItem("o2", "Pair with a mentor on weekends", selected = false, position = 1),
            ),
            targets = emptyList(), resources = emptyList(),
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = GoalWorkspaceActions(), user = user)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Options").performClick()
        compose.waitForIdle()
        saveWindow("options-tab")

        compose.onNodeWithText("Take an evening course twice a week").assertIsDisplayed()
        compose.onNodeWithText("Pair with a mentor on weekends").assertIsDisplayed()
        // The selected option is marked by a full-width "ACTIVE" Guava band across the card top.
        compose.onNodeWithText("ACTIVE").assertIsDisplayed()
        // Each card's kebab opens the bottom-sheet menu.
        compose.onAllNodesWithContentDescription("Option menu")[0].assertIsDisplayed()
    }
}
