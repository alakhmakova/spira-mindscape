package com.spiramindscape.android.ui.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.components.InlineResourcesValue
import com.spiramindscape.android.ui.components.ProvideInlineResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The target card's behaviour: the footer that reveals the progress controls, the padlock, and the
 * boundary the lock creates — progress is refused while the title and task text stay editable.
 */
@RunWith(RobolectricTestRunner::class)
class TargetCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = listOf(ResourceItem(id = "r1", type = "note", title = "Job ad"))

    private fun render(target: TargetItem, actions: GoalWorkspaceActions) {
        compose.setContent {
            ProvideInlineResources(InlineResourcesValue(resources = resources, openResource = {})) {
                TargetCard(target, actions)
            }
        }
    }

    private fun checklist(locked: Boolean? = null, done: Int = 0) = TargetItem.Checklist(
        id = "t1", title = "Draft the application", progress = done / 2f, deadline = null,
        achieved = false, progressLocked = locked,
        items = listOf(
            ChecklistItemModel("i1", "Collect references", done = done >= 1),
            ChecklistItemModel("i2", "Write the letter", done = done >= 2),
        ),
    )

    @Test
    fun `the footer reveals the progress controls, and Close puts them away`() {
        render(checklist(), GoalWorkspaceActions())

        compose.onNodeWithText("Update progress").performClick()
        compose.onNodeWithText("Collect references").assertIsDisplayed()
        compose.onNodeWithText("Add task").assertIsDisplayed()

        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Update progress").assertIsDisplayed()
    }

    @Test
    fun `an expanded card shows the percentage at the targets own resolution`() {
        val target = TargetItem.Numeric(
            id = "t2", title = "Save", progress = (10_000.0 / 1_900_000).toFloat(), deadline = null,
            achieved = false, current = 10_000.0, total = 1_900_000.0, start = 0.0, unit = "SEK",
        )
        render(target, GoalWorkspaceActions())

        // Whole percent would read "1% progress" here — and so would 20 000, which is the bug.
        compose.onNodeWithText("Update progress").performClick()
        compose.onNodeWithText("0.53% progress").assertIsDisplayed()
    }

    @Test
    fun `an achieved card reads 100 percent and starts locked`() {
        val target = TargetItem.Binary(
            id = "t3", title = "Send it", progress = 1f, deadline = null, achieved = true,
            done = true, achievedAt = "2026-07-18T00:00:00Z",
        )
        render(target, GoalWorkspaceActions())

        compose.onNodeWithText("100%").assertIsDisplayed()
        // Locked by default, so the padlock offers the way out rather than a second lock.
        compose.onNodeWithContentDescription("Unlock progress").assertIsDisplayed()
    }

    @Test
    fun `the padlock records an explicit choice`() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        render(
            checklist(),
            GoalWorkspaceActions(onSetTargetProgressLocked = { id, locked -> toggles += id to locked }),
        )

        compose.onNodeWithContentDescription("Lock progress").performClick()
        assertEquals(listOf("t1" to true), toggles)
    }

    @Test
    fun `a locked target refuses progress edits but keeps its text editable`() {
        val toggled = mutableListOf<String>()
        val renamed = mutableListOf<String>()
        render(
            checklist(locked = true),
            GoalWorkspaceActions(
                onToggleChecklistItem = { _, itemId -> toggled += itemId },
                onUpdateChecklistTask = { _, _, text -> renamed += text },
            ),
        )

        compose.onNodeWithText("Update progress").performClick()
        compose.onNodeWithText("Collect references").assertIsDisplayed()
        // The lock is explained rather than silently swallowing the tap.
        compose.onNodeWithText(PROGRESS_LOCKED_MESSAGE).assertIsDisplayed()
        // Ticking a task is refused (both tasks are open, so take the first)...
        compose.onAllNodesWithContentDescription("Mark subtask done").onFirst().performClick()
        assertTrue("a locked target must not change progress", toggled.isEmpty())
        // ...while the task's own text stays editable (tapping it opens the editor).
        compose.onNodeWithText("Add task").assertIsDisplayed()
    }

    @Test
    fun `a locked binary target cannot be toggled`() {
        val marked = mutableListOf<Boolean>()
        val target = TargetItem.Binary(
            id = "t4", title = "Send it", progress = 0f, deadline = null, achieved = false,
            done = false, progressLocked = true,
        )
        render(target, GoalWorkspaceActions(onSetTargetDone = { _, done -> marked += done }))

        compose.onNodeWithText("Update progress").performClick()
        compose.onNodeWithText("Mark done").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        assertTrue("a locked target must not be marked done", marked.isEmpty())
    }
}
