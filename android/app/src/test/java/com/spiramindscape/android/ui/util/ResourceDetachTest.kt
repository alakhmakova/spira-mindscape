package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.goals.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting a resource must never leave a dangling `{{res:id}}` behind — the Kotlin side of the
 * web `resources.test.ts`.
 */
class ResourceDetachTest {

    private fun goal(
        options: List<OptionItem> = emptyList(),
        actions: List<TextItem> = emptyList(),
        obstacles: List<TextItem> = emptyList(),
        targets: List<TargetItem> = emptyList(),
    ) = GoalDetail(
        id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
        progress = 0f, achieved = false,
        actions = actions, obstacles = obstacles, options = options, targets = targets,
        resources = emptyList(),
    )

    @Test
    fun `every element that references the resource is patched`() {
        val g = goal(
            options = listOf(OptionItem("o1", "Apply via {{res:7}}", selected = false)),
            actions = listOf(TextItem("a1", "Read {{res:7}} tonight")),
            obstacles = listOf(TextItem("b1", "No time")),
            targets = listOf(
                TargetItem.Checklist(
                    id = "t1", title = "Prepare {{res:7}}", progress = 0f, deadline = null,
                    achieved = false,
                    items = listOf(
                        ChecklistItemModel("i1", "Skim {{res:7}}", done = false),
                        ChecklistItemModel("i2", "Sleep", done = false),
                    ),
                ),
            ),
        )

        val patches = planResourceDetach(g, "7", "Job ad")

        assertEquals(
            listOf<DetachPatch>(
                DetachPatch.Option("o1", "Apply via Job ad"),
                DetachPatch.Reality("actions", "a1", "Read Job ad tonight"),
                DetachPatch.TargetTitle("t1", "Prepare Job ad"),
                DetachPatch.Checklist(
                    "t1",
                    listOf(
                        ChecklistItemModel("i1", "Skim Job ad", done = false),
                        ChecklistItemModel("i2", "Sleep", done = false),
                    ),
                ),
            ),
            patches,
        )
    }

    @Test
    fun `an unreferenced resource needs no patches`() {
        val g = goal(options = listOf(OptionItem("o1", "Apply somehow", selected = false)))
        assertTrue(planResourceDetach(g, "7", "Job ad").isEmpty())
        assertEquals(0, countResourceAttachments(g, "7"))
    }

    @Test
    fun `a nameless resource still leaves readable words behind`() {
        val g = goal(options = listOf(OptionItem("o1", "Apply via {{res:7}}", selected = false)))
        assertEquals(
            listOf<DetachPatch>(DetachPatch.Option("o1", "Apply via resource")),
            planResourceDetach(g, "7", "  "),
        )
    }

    @Test
    fun `a replacement that would overflow the field is cut, never rejected`() {
        val longName = "x".repeat(FieldLimits.OPTION_TEXT + 50)
        val g = goal(options = listOf(OptionItem("o1", "{{res:7}}", selected = false)))
        val text = (planResourceDetach(g, "7", longName).single() as DetachPatch.Option).text
        assertEquals(FieldLimits.OPTION_TEXT, text.length)
        assertTrue(text.endsWith("…"))
    }
}
