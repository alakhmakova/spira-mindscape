package com.spiramindscape.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.filterToOne
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
 * **A goal's target filters, and its padlock, belong to that goal** (owner, 2026-08-31 — BUG-067).
 *
 * The Android twin of `e2e/per-goal-filters.spec.ts`, and the counterpart to `ViewPreferencesTest`,
 * which pins the *store*. What only a screen can prove is the join: that the workspace hands
 * **this** goal's id to `rememberTargetViewState`, on the write as well as on the read, and that
 * switching goals reseeds every question from the goal now on screen. A store scoped perfectly
 * behind a screen that passed one constant id would pass every test in `ViewPreferencesTest`.
 *
 * The journey is the one the owner walked, in her order: pin a filter on goal A; open goal B and
 * find it untouched; pin a *different* filter on B; return to A and find it exactly as it was left;
 * then change goal C with its padlock **open** and confirm nothing is kept — and that neither
 * padlocked goal moved.
 *
 * Switching goals is done by swapping the state the one composition renders, which is what the real
 * navigation does: the workspace stays, the goal under it changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerGoalFiltersTest : VisualCheckTestBase() {

    private fun goal(id: String, taskTitle: String) = GoalDetail(
        id = id,
        title = "Goal $id",
        description = "",
        confidence = 5,
        deadline = null,
        progress = 0f,
        achieved = false,
        actions = emptyList(),
        obstacles = emptyList(),
        options = emptyList(),
        targets = listOf(
            // Not done, so the Progress = Done answer has something to hide.
            TargetItem.Binary(
                id = "$id-t1",
                title = taskTitle,
                progress = 0f,
                deadline = null,
                achieved = false,
                done = false,
            ),
        ),
        resources = emptyList(),
    )

    /** Which goal the one composition is showing — swapped the way navigation swaps it. */
    private var showing by mutableStateOf(goal("gA", "A task"))

    private fun open(id: String, taskTitle: String) {
        showing = goal(id, taskTitle)
        compose.waitForIdle()
    }

    /** The GROW tab, not the drawer item of the same name. */
    private fun openWillDoTab() {
        compose.onAllNodesWithText("Will do")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
    }

    private fun openFilterSheet() {
        compose.onNodeWithContentDescription("Filter targets").performClick()
        compose.waitForIdle()
    }

    private fun choose(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun closePadlock() {
        compose.onNodeWithContentDescription("Keep these filters and sort").performClick()
        compose.waitForIdle()
    }

    private fun apply() {
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
    }

    /**
     * Assert the whole arrangement this goal is showing.
     *
     * The padlock states itself in its content description, so it can be read directly. Which
     * *answer* is on is read off the rendered list rather than off the pill: a selected pill differs
     * only by colour, which no semantics assertion can see — and what the owner is complaining about
     * is the list, not the pill.
     */
    private fun assertArrangement(narrowed: Boolean, pinned: Boolean) {
        openFilterSheet()
        compose.onNodeWithContentDescription(
            if (pinned) "Filters and sort are kept - unlock to let them reset" else "Keep these filters and sort",
        ).assertIsDisplayed()
        apply()
        assertTargetsVisible(!narrowed)
    }

    /** Is the goal's one (not-done) target on screen, or has a filter hidden it? */
    private fun assertTargetsVisible(visible: Boolean) {
        val rows = compose.onAllNodesWithText(showing.targets.first().title).fetchSemanticsNodes()
        if (visible) {
            assert(rows.isNotEmpty()) { "${showing.id}: its target should be listed" }
        } else {
            assert(rows.isEmpty()) { "${showing.id}: its target should be filtered out" }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `each goal keeps its own target filters and its own padlock`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                GoalWorkspaceScreen(
                    state = GoalUiState.Content(showing),
                    actions = GoalWorkspaceActions(),
                    user = user,
                )
            }
        }
        compose.waitForIdle()
        openWillDoTab()

        // ── Goal A: narrow it to Done — the target is not done, so the list empties — and pin it.
        openFilterSheet()
        choose("Done")
        closePadlock()
        apply()
        assertTargetsVisible(false)

        // ── Goal B has never been touched: its own defaults, its own padlock, still open.
        open("gB", "B task")
        openWillDoTab()
        assertTargetsVisible(true)
        assertArrangement(narrowed = false, pinned = false)

        // ── Pin a different arrangement on B…
        openFilterSheet()
        choose("Checklist")
        closePadlock()
        apply()
        assertTargetsVisible(false)

        // ── …and goal A is exactly as it was left. This is the half the owner reported second and
        // the worse of the two: changing another goal used to write straight through this padlock.
        open("gA", "A task")
        openWillDoTab()
        assertTargetsVisible(false)
        compose.onNodeWithContentDescription("Filter targets").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            "Filters and sort are kept - unlock to let them reset",
        ).assertIsDisplayed()
        apply()

        // ── Goal C, padlock left open: the change holds while it is on screen…
        open("gC", "C task")
        openWillDoTab()
        assertTargetsVisible(true)
        openFilterSheet()
        choose("Done")
        apply()
        assertTargetsVisible(false)

        // ── …and is gone the next time the goal is opened, because nothing was kept.
        open("gA", "A task")
        openWillDoTab()
        open("gC", "C task")
        openWillDoTab()
        assertTargetsVisible(true)

        // ── And after all of it, both padlocked goals still hold their own, different answers.
        open("gA", "A task")
        openWillDoTab()
        assertTargetsVisible(false)
        open("gB", "B task")
        openWillDoTab()
        assertTargetsVisible(false)
    }
}
