package com.spiramindscape.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The file card's preview after its bytes arrive **lazily**.
 *
 * `VisualCheckResourcesTabTest` hands the card a `dataUrl` up front, which is no longer how the app
 * behaves: the goal query omits file bytes (BUG-019), so a card starts with `dataUrl = null` and
 * asks for them from composition. That difference is invisible to an existence assertion — a card
 * whose image never arrives still has all its nodes — so the only way to know the preview actually
 * renders is to look at the pixels.
 *
 * This test starts with no bytes, lets the card's own request supply them, and screenshots the
 * result to `app/build/reports/visual/resource-lazy-file.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckResourceLazyFileTest : VisualCheckTestBase() {

    // A 24x24 solid Guava PNG. Deliberately not the 1x1 fixture the other visual tests use:
    // Robolectric's decoder fails on that one (`codec->getAndroidPixels() failed`), so the preview
    // would come out blank and the screenshot could not tell a harness limitation apart from a
    // real regression — which is the only thing this test exists to distinguish.
    private val redPng =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAIAAABvFaqvAAAAH0lEQVR4nGP4EutBFcQwatCoQaMGjRo0atCoQQNvEAA9eZhur7/gFQAAAABJRU5ErkJggg=="

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `file card renders its preview once the bytes load lazily`() {
        compose.activityRule.scenario.onActivity { }

        // Starts null, exactly as the goal query now returns it.
        var bytes by mutableStateOf<String?>(null)

        compose.setContent {
            val goal = GoalDetail(
                id = "g1", title = "Move to Lisbon", description = "", confidence = 5,
                deadline = null, progress = 0.4f, achieved = false,
                actions = emptyList(), obstacles = emptyList(), options = emptyList(),
                targets = emptyList(),
                resources = listOf(
                    ResourceItem(
                        "r2", "file", title = "Apartment floor plan",
                        mime = "image/png", dataUrl = bytes,
                    ),
                ),
            )
            SpiraTheme {
                GoalWorkspaceScreen(
                    state = GoalUiState.Content(goal),
                    // Stands in for GoalWorkspaceViewModel.loadResourceFile.
                    actions = GoalWorkspaceActions(onLoadResourceFile = { bytes = redPng }),
                    user = user,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Resources").performClick()
        compose.waitForIdle()
        // Expanding the card is what mounts the file body, and therefore what asks for the bytes.
        compose.onNodeWithText("Apartment floor plan").performClick()
        compose.waitForIdle()

        saveWindow("resource-lazy-file")
    }
}
