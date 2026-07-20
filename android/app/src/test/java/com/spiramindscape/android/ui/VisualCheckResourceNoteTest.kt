package com.spiramindscape.android.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.ResourceItem
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
 * Renders an expanded NOTE resource card. Its own class (one `@Test`) so it gets a fresh test JVM —
 * see [VisualCheckTestBase] / BUG-009. The note card is now a plain-text preview + "Open note"
 * (editing moved to the full-screen `NoteEditorActivity`), so it renders fully under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckResourceNoteTest : VisualCheckTestBase() {

    private val goal = GoalDetail(
        id = "g1", title = "Move to Lisbon", description = "", confidence = 5, deadline = null,
        progress = 0.4f, achieved = false,
        actions = emptyList(), obstacles = emptyList(), options = emptyList(), targets = emptyList(),
        resources = listOf(
            ResourceItem(
                "r1", "note", title = "D7 visa checklist",
                body = "<p>Passport valid <strong>6+ months</strong></p>" +
                    "<ul><li>Proof of income</li><li>Accommodation contract</li></ul>",
            ),
        ),
    )

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `note card shows preview and open note`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme { GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = GoalWorkspaceActions(), user = user) }
        }
        compose.waitForIdle()
        compose.onNode(hasText("Resources") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("D7 visa checklist").performClick()
        compose.waitForIdle()
        saveWindow("resource-note-card")
    }
}
