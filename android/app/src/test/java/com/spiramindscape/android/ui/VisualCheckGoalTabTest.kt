package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.ConfidenceHistoryEntry
import com.spiramindscape.android.data.goals.GoalDetail
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
class VisualCheckGoalTabTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h1400dp") // extra tall so all three stat cards are visible at once
    fun `goal tab shows the two stat cards`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin thoroughly and ship a native app",
            description = "A long-form description of the goal, to check wrapping.",
            confidence = 7, deadline = "2026-08-01T00:00:00Z",
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(), options = emptyList(),
            targets = emptyList(), resources = emptyList(),
            confidenceHistory = listOf(
                ConfidenceHistoryEntry("h3", 7, "2026-07-16T10:00:00Z"),
                ConfidenceHistoryEntry("h2", 5, "2026-07-10T10:00:00Z"),
                ConfidenceHistoryEntry("h1", 6, "2026-07-01T10:00:00Z"),
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
        saveWindow("goal-tab")

        // Also verify the confidence-history sheet this screen now opens (real tap, not a stub).
        // ModalBottomSheet renders in its own Popup window, which the decorView screenshot above
        // doesn't capture (it stays blank), so this checks the semantics tree instead.
        compose.onNodeWithText("Confidence history").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Current: 7/10").assertIsDisplayed()
    }
}
