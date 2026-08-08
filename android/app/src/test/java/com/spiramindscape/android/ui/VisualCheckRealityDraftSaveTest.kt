package com.spiramindscape.android.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.ui.goals.GoalUiState
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.GoalWorkspaceScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckRealityDraftSaveTest : VisualCheckTestBase() {

    /**
     * Types into the [com.spiramindscape.android.ui.goals.NewRealitySheet] `ModalBottomSheet` and
     * checks the form actually saves.
     *
     * This test was `@Ignore`d for a long time because it hung the whole run — the cause turned
     * out to be the tab tap, which used to `animateScrollToPage` across the pager; that animation
     * never settled under Robolectric, so nothing after it could reach idle. The tap now jumps
     * (see `GoalWorkspaceScreen`) and the test finishes in seconds. See BUG-009 in
     * `backlog/android-visual-test-suite-flaky-appnotidle.md`.
     */
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
        // The drawer lists the same phase names, so the label alone is ambiguous even
        // while it is closed — pick the tab that is actually on screen.
        compose.onAllNodesWithText("Reality")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
        // The FAB opens the create form (its label is a contentDescription, so it does not
        // collide with the sheet's own "Add action" button below).
        compose.onNodeWithContentDescription("Add action").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Describe this action").performTextInput("Talk to my coach")
        compose.onNodeWithText("Add action").performClick()

        assertEquals("actions" to "Talk to my coach", saved)
    }
}
