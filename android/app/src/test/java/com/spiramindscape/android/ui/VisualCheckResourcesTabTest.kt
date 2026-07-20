package com.spiramindscape.android.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckResourcesTabTest : VisualCheckTestBase() {

    // A 1x1 red PNG as a data URL — enough to exercise the image decode + preview path.
    private val redPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    private val goal = GoalDetail(
        id = "g1", title = "Move to Lisbon", description = "", confidence = 5, deadline = null,
        progress = 0.4f, achieved = false,
        actions = emptyList(), obstacles = emptyList(), options = emptyList(), targets = emptyList(),
        resources = listOf(
            ResourceItem(
                "r1", "note", title = "D7 visa checklist",
                body = "<p>Passport valid <strong>6+ months</strong></p><ul><li>Proof of income</li><li>Accommodation contract</li></ul>",
            ),
            ResourceItem("r2", "file", title = "Apartment floor plan", mime = "image/png", dataUrl = redPng),
            ResourceItem("r3", "link", title = "SEF appointment portal", url = "https://aima.gov.pt/pt/agendamento"),
        ),
    )

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `resources tab shows collapsed cards`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme { GoalWorkspaceScreen(state = GoalUiState.Content(goal), actions = GoalWorkspaceActions(), user = user) }
        }
        compose.waitForIdle()
        compose.onNode(hasText("Resources") and hasClickAction()).performClick()
        compose.waitForIdle()
        saveWindow("resources-tab")
    }

}
