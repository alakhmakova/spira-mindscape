package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.components.InlineResourcesValue
import com.spiramindscape.android.ui.components.ProvideInlineResources
import com.spiramindscape.android.ui.goals.GoalWorkspaceActions
import com.spiramindscape.android.ui.goals.TargetCard
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the four states of a target card to `app/build/reports/visual/target-cards.png` so the
 * illustrated deadline tile, the padlock badge, the progress strip and the teal footer can be
 * checked by eye (CLAUDE.md → "Verify UI changes visually"). Existence assertions alone would not
 * catch a tile whose date prints off its page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckTargetCardTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h1200dp")
    fun `target cards in every deadline state`() {
        val resources = listOf(ResourceItem(id = "r1", type = "link", title = "Job ad", url = "https://x.se"))

        val noDeadline = TargetItem.Checklist(
            id = "t1", title = "Draft the application", progress = 0.5f, deadline = null,
            achieved = false, createdAt = "2026-07-01T00:00:00Z",
            items = listOf(
                ChecklistItemModel("i1", "Collect references", done = true),
                ChecklistItemModel("i2", "Write the letter {{res:r1}}", done = false, deadline = "2026-09-01T00:00:00Z"),
            ),
        )
        val dated = TargetItem.Numeric(
            id = "t2", title = "Save for the deposit", progress = 0.0053f,
            deadline = "2026-12-24T00:00:00Z", achieved = false,
            current = 10_000.0, total = 1_900_000.0, start = 0.0, unit = "SEK",
        )
        val overdue = TargetItem.Binary(
            id = "t3", title = "Send the signed contract", progress = 0f,
            deadline = "2026-06-01T00:00:00Z", achieved = false, done = false,
        )
        val achieved = TargetItem.Binary(
            id = "t4", title = "Book the interview {{res:r1}}", progress = 1f,
            deadline = "2026-07-20T00:00:00Z", achieved = true, done = true,
            achievedAt = "2026-07-18T00:00:00Z",
        )

        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                ProvideInlineResources(InlineResourcesValue(resources = resources, openResource = {})) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf(noDeadline, dated, overdue, achieved).forEach {
                            TargetCard(it, GoalWorkspaceActions())
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("target-cards")
    }
}
