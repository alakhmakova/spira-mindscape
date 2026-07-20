package com.spiramindscape.android.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.OptionItem
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
class OptionsDragReorderTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `dragging the third option to the top reorders it to any position`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(),
            options = listOf(
                OptionItem("o1", "First strategy here", selected = false, position = 0),
                OptionItem("o2", "Second strategy here", selected = false, position = 1),
                OptionItem("o3", "Third strategy here", selected = false, position = 2),
            ),
            targets = emptyList(), resources = emptyList(),
        )
        var reordered: Pair<String, Int>? = null
        val actions = GoalWorkspaceActions(onReorderOption = { id, pos -> reordered = id to pos })

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme { GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = actions, user = user) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Options").performClick()
        compose.waitForIdle()

        // One continuous gesture: press and hold the THIRD card, then drag far up — past two cards —
        // and release. Real drag-and-drop must land it at the very top (index 0), not just one slot.
        compose.onNodeWithText("Third strategy here").performTouchInput {
            down(center)
            advanceEventTime(700)
            repeat(12) {
                moveBy(Offset(0f, -60f))
                advanceEventTime(16)
            }
            up()
        }
        compose.waitForIdle()

        // Moved from position 2 all the way to position 0.
        assertEquals("o3" to 0, reordered)
    }
}
