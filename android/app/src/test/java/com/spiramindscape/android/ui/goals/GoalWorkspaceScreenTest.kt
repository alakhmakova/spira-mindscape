package com.spiramindscape.android.ui.goals

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.goals.TextItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalWorkspaceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Regression: the workspace renders one LazyColumn containing targets, options and resources.
     * Their ids come from different backend tables and can collide (e.g. a target and an option
     * both id "5"). With id-only LazyColumn keys that threw "key was already used" and crashed
     * the app. Keys are now namespaced per type; this goal (all three share id "5") must render.
     */
    @Test
    fun `renders even when a target, option and resource share an id`() {
        val goal = GoalDetail(
            id = "g1", title = "My Goal", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = listOf(TextItem("1", "an action")),
            obstacles = emptyList(),
            options = listOf(OptionItem("5", "an option", selected = false)),
            targets = listOf(TargetItem.Binary("5", "a target", 0f, null, false, done = false)),
            resources = listOf(ResourceItem("5", "note", "a resource")),
        )

        compose.setContent {
            GoalWorkspaceScreen(
                state = GoalUiState.Content(goal),
                actions = GoalWorkspaceActions(),
                user = AuthUser(1, "t@e.com", "T", null),
            )
        }

        // The Goal tab shows the title; switching tabs renders the colliding-id target and
        // resource without the old duplicate-key crash.
        assertVisible(hasText("My Goal"), "the goal title")
        // GROW tab bar. The workspace keeps its navigation drawer composed off-screen while
        // closed, and the drawer's rubric rows now carry the same labels *and* click actions
        // as the tabs — so matching on text alone is ambiguous even though only one is
        // reachable. Pick the one actually on screen.
        clickVisible(hasText("Will do") and hasClickAction(), "the Will do tab")
        assertVisible(hasText("a target"), "the target")
        // Resources is no longer a tab — it is the footer's right-hand action.
        clickVisible(hasContentDescription("Resources"), "the Resources footer action")
        assertVisible(hasText("a resource"), "the resource")
    }

    /** The nodes matching [matcher] that are actually on screen (not in the closed drawer). */
    private fun visibleNodes(matcher: SemanticsMatcher): List<Int> {
        val nodes = compose.onAllNodes(matcher)
        return (0 until nodes.fetchSemanticsNodes().size).filter { nodes[it].isDisplayed() }
    }

    private fun assertVisible(matcher: SemanticsMatcher, what: String) {
        assertTrue("expected $what to be visible", visibleNodes(matcher).isNotEmpty())
    }

    private fun clickVisible(matcher: SemanticsMatcher, what: String) {
        val visible = visibleNodes(matcher)
        assertTrue("expected $what to be visible and clickable", visible.isNotEmpty())
        compose.onAllNodes(matcher)[visible.first()].performClick()
    }

    /**
     * The workspace's goal switcher must start empty: a search typed on the All-goals dashboard
     * (or in another goal) may never follow the user into the goal they open.
     */
    @Test
    fun `the header goal search starts empty`() {
        compose.setContent {
            GoalWorkspaceScreen(
                state = GoalUiState.Content(
                    GoalDetail(
                        id = "g1", title = "My Goal", description = "", confidence = 5,
                        deadline = null, progress = 0f, achieved = false,
                        actions = emptyList(), obstacles = emptyList(), options = emptyList(),
                        targets = emptyList(), resources = emptyList(),
                    ),
                ),
                actions = GoalWorkspaceActions(),
                user = AuthUser(1, "t@e.com", "T", null),
                allGoals = listOf(
                    GoalSummary(
                        id = "g2", title = "Another goal", confidence = 5, deadline = null,
                        progress = 0f, targetCount = 0, achieved = false,
                    ),
                ),
            )
        }

        // The placeholder is showing, so nothing was carried in — and with an empty query the
        // switcher shows no results over the page.
        compose.onNodeWithText("Search goals").assertIsDisplayed()
        compose.onNodeWithText("Another goal").assertDoesNotExist()
    }
}
