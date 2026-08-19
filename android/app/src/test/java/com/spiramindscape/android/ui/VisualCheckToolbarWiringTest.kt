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
 * The Will-do toolbar's triggers really open the redesigned **sheet**, and every question is there.
 *
 * What it *looks like* is `VisualCheckFilterSheetTest`; this is the wiring — that the trigger is a
 * word rather than the old pill, and that all four filter questions are behind it.
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

        // One heading and one late answer from each question: a missing or clipped question shows
        // up as a missing node here.
        //
        // **The headings are upper-case** — the filter opens a `SpiraFilterSheet` now, not a
        // dropdown of columns (owner, 2026-08-17), and `SpiraSheetGroup` sets its heading in caps.
        // The answers keep their sentence case, because they are pills.
        compose.onNodeWithText("STATUS").assertIsDisplayed()
        compose.onNodeWithText("Not started").assertIsDisplayed()
        compose.onNodeWithText("DEADLINE").assertIsDisplayed()
        compose.onNodeWithText("No deadline").assertIsDisplayed()
        compose.onNodeWithText("LOCK").assertIsDisplayed()
        compose.onNodeWithText("Unlocked").assertIsDisplayed()
    }
}
