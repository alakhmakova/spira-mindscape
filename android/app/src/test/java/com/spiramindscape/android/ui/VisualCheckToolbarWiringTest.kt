package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
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
 * The Will-do toolbar's triggers really open the redesigned menus, and every column is there.
 *
 * What the menu *looks like* is `VisualCheckToolbarMenusTest`; this is the wiring — that the
 * trigger is a word rather than the old pill, and that all three filter questions are behind it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckToolbarWiringTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the filter trigger opens all three questions`() {
        val goal = GoalDetail(
            id = "g1", title = "Change career into product", description = "", confidence = 6,
            deadline = null, progress = 0.4f, achieved = false,
            actions = emptyList(), obstacles = emptyList(), options = emptyList(),
            resources = emptyList(),
            targets = listOf(
                TargetItem.Binary(
                    id = "t1", title = "Draft the application", progress = 0f,
                    deadline = null, achieved = false, done = false,
                ),
                TargetItem.Binary(
                    id = "t2", title = "Send the signed contract", progress = 1f,
                    deadline = "2026-06-01T00:00:00Z", achieved = true, done = true,
                ),
            ),
        )
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(
                    state = GoalUiState.Content(goal),
                    actions = GoalWorkspaceActions(),
                    user = user,
                )
            }
        }
        compose.waitForIdle()
        // The drawer lists the same phase names, so pick the tab that is actually on screen.
        compose.onAllNodesWithText("Will do")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()

        // The trigger is the word "Filter" — no count in brackets while nothing is narrowed.
        compose.onNodeWithText("Filter").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter targets").performClick()
        compose.waitForIdle()

        // One heading and one late-column word from each group: a missing or clipped column shows
        // up as a missing node here, and as a missing column in the sheet above.
        compose.onNodeWithText("Status").assertIsDisplayed()
        compose.onNodeWithText("Not started").assertIsDisplayed()
        compose.onNodeWithText("Deadline").assertIsDisplayed()
        compose.onNodeWithText("No deadline").assertIsDisplayed()
        compose.onNodeWithText("Lock").assertIsDisplayed()
        compose.onNodeWithText("Unlocked").assertIsDisplayed()
    }
}
