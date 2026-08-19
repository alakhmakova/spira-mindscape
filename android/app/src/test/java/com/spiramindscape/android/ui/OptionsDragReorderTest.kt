package com.spiramindscape.android.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import com.spiramindscape.android.ui.components.GROW_TABS_TAG
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.ui.goals.GoalUiState
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.GoalWorkspaceScreen
import com.spiramindscape.android.ui.goals.optionCardTag
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

    /**
     * The **grip** inside one particular card — the drag target since 2026-08-18, when the card
     * itself stopped taking a drag on the first pixel of movement so that an ordinary swipe could
     * scroll the page again (BUG-038). The grip stands where the radio button is.
     *
     * Found through the card's own tag rather than by position: the list reorders *under* the
     * finger, so "the first grip on screen" is a different card after the first swap, and a gesture
     * aimed that way would jump between cards mid-drag.
     */
    private fun grip(optionId: String) = compose.onNode(
        hasContentDescription("Drag to reorder") and hasAnyAncestor(hasTestTag(optionCardTag(optionId))),
    )

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
        // The drawer lists the same phase names, so the label alone is ambiguous even
        // while it is closed — pick the tab that is actually on screen.
        compose.onAllNodesWithText("Options")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()

        // Reordering is a mode (web parity): nothing drags until Reorder is pressed.
        compose.onNodeWithText("Reorder").performClick()
        compose.waitForIdle()

        // One continuous gesture: press the THIRD card's grip and drag far up — past two cards —
        // then release. Real drag-and-drop must land it at the very top (index 0), not one slot.
        grip("o3").performTouchInput {
            down(center)
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

    /**
     * With more options than fit on screen, holding the dragged card against the bottom edge must
     * keep the page scrolling under it (web parity) — otherwise the reachable travel is capped by
     * the height of the screen and the last cards are simply unreachable in one drag.
     *
     * The finger moves only ~360px here, which is worth roughly four slots on its own; the card is
     * then held still. Without auto-scroll a stationary finger produces no events at all and the
     * card stops where it is, so anything past the fifth slot can only come from the page moving.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `holding a dragged card at the bottom edge keeps scrolling the page`() {
        val goal = GoalDetail(
            id = "g1", title = "Learn Kotlin", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = emptyList(), obstacles = emptyList(),
            options = (0 until 9).map {
                OptionItem("o$it", "Strategy number $it", selected = false, position = it)
            },
            targets = emptyList(), resources = emptyList(),
        )
        var reordered: Pair<String, Int>? = null
        val actions = GoalWorkspaceActions(onReorderOption = { id, pos -> reordered = id to pos })

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme { GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = actions, user = user) }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Options")
            .filterToOne(hasAnyAncestor(hasTestTag(GROW_TABS_TAG)))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Reorder").performClick()
        compose.waitForIdle()

        // The auto-scroll runs on `withFrameNanos`, and `performTouchInput`'s `advanceEventTime`
        // moves only the INPUT clock — it produces no Compose frames, so the loop would never tick
        // inside a single injection block. Drive the frame clock by hand instead, and keep the
        // gesture open across the calls (down / moveBy / up are one gesture per test).
        compose.mainClock.autoAdvance = false
        val card = grip("o0")
        card.performTouchInput { down(center) }
        repeat(6) {
            card.performTouchInput { moveBy(Offset(0f, 60f)) }
            compose.mainClock.advanceTimeByFrame()
        }
        // Finger now parked in the bottom edge zone and going nowhere: every further slot is the
        // auto-scroll loop's doing.
        repeat(60) { compose.mainClock.advanceTimeByFrame() }
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals("o0" to 8, reordered)
    }
}
