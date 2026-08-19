package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.ui.components.InlineResourcesValue
import com.spiramindscape.android.ui.components.ProvideInlineResources
import com.spiramindscape.android.ui.components.ResourcePickerSheetContent
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Attach a resource" picker, which gained a **search** on 2026-08-18 — a goal accumulates
 * resources faster than anything else on it, and the list has no sort and no filter, so on a real
 * goal the one you want is below the fold.
 *
 * Rendered rather than asserted because the things that can go wrong here are all visual: the
 * field crowding the Kale head, the list losing its scroll height to it, and the
 * nothing-matched notice reading as "this goal has no resources" instead of "your search hid
 * them".
 *
 * Look at `app/build/reports/visual/resource-picker*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckResourcePickerTest : VisualCheckTestBase() {

    private val resources = listOf(
        ResourceItem(id = "r1", type = "link", title = "Job ad — integration specialist", url = "https://solita.fi"),
        ResourceItem(id = "r2", type = "note", title = "Interview notes"),
        ResourceItem(id = "r3", type = "email", title = null, name = "Anna Berg", email = "anna@x.se", role = "Recruiter"),
        ResourceItem(id = "r4", type = "file", title = "CV.pdf"),
    )

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `the picker offers a search over the goal's resources`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ProvideInlineResources(InlineResourcesValue(resources = resources, openResource = {})) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ResourcePickerSheetContent(onDismiss = {}, onPick = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("resource-picker")

        // A search that keeps one row.
        compose.onNodeWithText("Search resources").performTextInput("anna")
        compose.waitForIdle()
        saveWindow("resource-picker-searched")

        // And one that keeps none: a warning, not an empty state — the goal has four resources,
        // the search is what hid them. The field is found by its own text now, not the
        // placeholder, which the first search replaced.
        compose.onNodeWithText("anna").performTextReplacement("zzz")
        compose.waitForIdle()
        saveWindow("resource-picker-no-match")
    }
}
