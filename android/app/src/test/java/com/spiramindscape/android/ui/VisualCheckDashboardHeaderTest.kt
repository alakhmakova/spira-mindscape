package com.spiramindscape.android.ui

import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.ui.goals.GoalsDashboardScreen
import com.spiramindscape.android.ui.goals.GoalsUiState
import com.spiramindscape.android.ui.goals.SortKey
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The All-goals page, for the one thing an assertion cannot check: that **"All goals" and its
 * Filter & Sort opener share a line** and neither is pushed off the edge. The screen had no filter
 * chrome at all until GRO-133 — `SortMenu`/`FilterMenu` were written and never mounted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckDashboardHeaderTest : VisualCheckTestBase() {

    @Test
    fun `all goals header carries its filter and sort opener`() {
        compose.activityRule.scenario.onActivity { }
        val goals = listOf(
            GoalSummary(
                id = "g1",
                title = "Change career into product design",
                confidence = 7,
                deadline = "2026-12-24",
                progress = 0.35f,
                targetCount = 4,
                achieved = false,
            ),
            GoalSummary(
                id = "g2",
                title = "Run a half marathon",
                confidence = 5,
                deadline = null,
                progress = 0.8f,
                targetCount = 2,
                achieved = false,
            ),
        )
        compose.setContent {
            SpiraTheme {
                GoalsDashboardScreen(
                    state = GoalsUiState.Content(goals),
                    visibleGoals = goals,
                    user = user,
                    // A sort away from the default, so the trigger's "something is on" dot shows.
                    sortKey = SortKey.Deadline,
                )
            }
        }
        compose.waitForIdle()
        saveWindow("dashboard-header")
    }
}
