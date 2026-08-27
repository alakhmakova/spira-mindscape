package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.ai.ResourcePickerSheetContent
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Attach a resource** — the sheet that puts one of a goal's saved resources in front of the
 * assistant (BUG-030).
 *
 * This test exists because the sheet had drifted off the design system and nothing noticed: it
 * wore a **white head with a dark title** and Material's grey **drag handle** above it, while
 * every other sheet in the app wears the Kale band (CLAUDE.md, Design 3e). Existence assertions
 * could never have caught that, which is why the head is checked with a picture — and why the
 * card is rendered directly rather than through `ModalBottomSheet`, whose own window the
 * screenshot helper cannot capture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckResourceAttachTest : VisualCheckTestBase() {

    private val resources = listOf(
        ResourceItem(id = "1", type = "note", title = "Interview notes"),
        ResourceItem(id = "2", type = "link", title = "Job board"),
        ResourceItem(id = "3", type = "file", title = "CV.pdf"),
        ResourceItem(id = "4", type = "email", name = "Recruiter", title = null),
    )

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the picker wears the standard sheet head and lists every resource type`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ResourcePickerSheetContent(
                    resources = resources,
                    alreadyAttached = setOf(3L),
                    onPick = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        saveWindow("resource-attach-sheet")

        // The Kale band's title and its white X — the head every other sheet wears.
        compose.onNodeWithText("Attach a resource").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()

        // All four resource types are offered, not just files.
        compose.onNodeWithText("Interview notes").assertIsDisplayed()
        compose.onNodeWithText("Job board").assertIsDisplayed()
        compose.onNodeWithText("CV.pdf").assertIsDisplayed()
        compose.onNodeWithText("Recruiter").assertIsDisplayed()

        // One already attached is offered as "Added" rather than a second time.
        compose.onNodeWithText("Added").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `picking a resource hands it back, and the search narrows the list`() {
        var picked: ResourceItem? = null
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ResourcePickerSheetContent(
                    resources = resources,
                    alreadyAttached = emptySet(),
                    onPick = { picked = it },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Interview notes").performClick()
        compose.waitForIdle()
        assert(picked?.id == "1") { "expected the note to be picked, got ${picked?.id}" }

        compose.onNodeWithText("Search resources").performTextInput("Job")
        compose.waitForIdle()
        saveWindow("resource-attach-search")
        compose.onNodeWithText("Job board").assertIsDisplayed()
        compose.onNodeWithText("Interview notes").assertDoesNotExist()
    }
}
