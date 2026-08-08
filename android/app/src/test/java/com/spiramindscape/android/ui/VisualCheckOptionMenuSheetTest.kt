package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
class VisualCheckOptionMenuSheetTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `tapping an option kebab opens the action bottom sheet`() {
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
            SpiraTheme { GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = GoalWorkspaceActions(), user = user) }
        }
        compose.waitForIdle()
        // The drawer lists the same phase names, so the label alone is ambiguous even
        // while it is closed — pick the tab that is actually on screen.
        compose.onAllNodesWithText("Options")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
        // Open the second (inactive) card's menu → the sheet offers "Make active".
        compose.onAllNodesWithContentDescription("Option menu")[1].performClick()
        compose.waitForIdle()
        saveWindow("options-menu-sheet")

        compose.onNodeWithText("Make active").assertIsDisplayed()
        compose.onNodeWithText("Delete option").assertIsDisplayed()
    }
}
