package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun `options tab shows strategy rows with the active radio and rating badge`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(),
            options = listOf(
                OptionItem(
                    "o1", "Take an evening course twice a week", selected = true, position = 0,
                    status = "good_idea",
                ),
                OptionItem(
                    "o2", "Pair with a mentor on weekends", selected = false, position = 1,
                    status = "didnt_work",
                ),
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
        // The drawer lists the same phase names, so the label alone is ambiguous even
        // while it is closed — pick the tab that is actually on screen.
        compose.onAllNodesWithText("Options")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
        saveWindow("options-tab")

        compose.onNodeWithText("Take an evening course twice a week").assertIsDisplayed()
        compose.onNodeWithText("Pair with a mentor on weekends").assertIsDisplayed()
        // The active option is marked by the filled radio in the left cell, not a band or a label.
        compose.onNodeWithContentDescription("Deselect option").assertIsDisplayed()
        compose.onNodeWithContentDescription("Select option").assertIsDisplayed()
        // Each row carries the smiley rating badge.
        compose.onAllNodesWithContentDescription("Rate option")[0].assertIsDisplayed()
        // Strategies are created from the round button bottom-right, the same way a new goal is
        // — an add action never sits at the top of a list.
        compose.onNodeWithContentDescription("Add option").assertIsDisplayed()
        compose.onNodeWithText("Search options").assertIsDisplayed()
        // Two options, so the Reorder toggle is offered.
        compose.onNodeWithText("Reorder").assertIsDisplayed()

        // The ⋯ menu is NOT part of the resting card: it appears with the editing caret, which is
        // what tapping the strategy text asks for (web parity).
        compose.onAllNodesWithContentDescription("Option actions").assertCountEquals(0)
        compose.onNodeWithText("Pair with a mentor on weekends").performClick()
        compose.waitForIdle()
        saveWindow("options-tab-editing")
        compose.onAllNodesWithContentDescription("Option actions").assertCountEquals(1)
    }

    /**
     * The two states the first test can't show: a strategy long enough to be clamped behind
     * "Show more", and reorder mode — where the creation field and the per-card controls go away
     * and the drag hint takes their place.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a long strategy clamps behind Show more and reorder mode strips the controls`() {
        val long = "Move to a city with a stronger job market for this field, give myself six " +
            "months to find a team that mentors juniors properly, and keep the current job " +
            "until the offer is signed so the runway never runs out."
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(),
            options = listOf(
                OptionItem("o1", long, selected = false, position = 0),
                OptionItem("o2", "Pair with a mentor on weekends", selected = true, position = 1),
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
        compose.onAllNodesWithText("Options")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Show more").assertIsDisplayed()
        compose.onNodeWithText("Show more").performClick()
        compose.waitForIdle()
        saveWindow("options-tab-expanded")
        compose.onNodeWithText("Show less").assertIsDisplayed()

        compose.onNodeWithText("Reorder").performClick()
        compose.waitForIdle()
        saveWindow("options-tab-reordering")

        // In reorder mode the card is a drag handle: no ⋯ menu, no creation field, and the long
        // strategy is forced back to its collapsed height so one slot is one small finger move.
        compose.onNodeWithText("Drag cards to reorder.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Option actions").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Add option").assertCountEquals(1)
        compose.onAllNodesWithText("Show less").assertCountEquals(0)
    }
}
