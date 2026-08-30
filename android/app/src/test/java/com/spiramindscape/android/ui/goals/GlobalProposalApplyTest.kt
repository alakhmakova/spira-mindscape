package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.ai.ProposalKind
import com.spiramindscape.android.ui.ai.AiHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **The all-goals chat can do all four goal-level things** (2026-08-30).
 *
 * Three of them used to be refused. `edit_goal`, `open_goal` and `delete_goal` all fell through
 * to "Open that goal and ask there — I can only create goals from here", said by the very
 * assistant on the very screen where a goal's name, confidence and deadline ARE editable and
 * where `open_goal` is the only card that means anything. Meanwhile the prompt spent roughly
 * 2,900 characters per call — of every conversation, on every model call — teaching the model to
 * produce exactly those cards. The web had applied all four since the kinds existed.
 *
 * Each test here is one of those cards arriving; nothing mocks a screen, because the bug was
 * never in the screen.
 */
class GlobalProposalApplyTest {

    private val applied = mutableListOf<String>()

    private val actions = GlobalProposalActions(
        onCreateGoal = { title, _, confidence, _ -> applied += "create:$title:$confidence" },
        onEditGoal = { id, field, value -> applied += "edit:$id:$field:$value" },
        onOpenGoal = { id -> applied += "open:$id" },
        onConfirmDelete = { id -> applied += "confirmDelete:$id" },
    )

    private fun apply(p: Proposal, askedFor: String? = null) =
        applyGlobalProposal(p, emptySet(), askedFor, actions)

    @Test
    fun `creating a goal still works`() {
        val error = apply(Proposal(
            id = "1", kind = ProposalKind.NEW_GOAL, title = "Learn Spanish", confidence = 9,
        ))

        assertNull(error)
        assertEquals(listOf("create:Learn Spanish:9"), applied)
    }

    @Test
    fun `editing a goal's card field is applied, not refused`() {
        val error = apply(Proposal(
            id = "2", kind = ProposalKind.EDIT_GOAL, title = "8",
            goalId = "12", field = "confidence", rawValue = "8",
        ))

        assertNull(error)
        assertEquals(listOf("edit:12:confidence:8"), applied)
    }

    @Test
    fun `deleting a goal raises the confirmation rather than deleting`() {
        // The card's own subtitle promises one ("Opens a confirmation"), and this is the one
        // deletion reachable from the overview that cannot be undone.
        val error = apply(Proposal(id = "3", kind = ProposalKind.DELETE_GOAL, title = "x", goalId = "12"))

        assertNull(error)
        assertEquals(listOf("confirmDelete:12"), applied)
    }

    @Test
    fun `opening a goal carries the request that could not be answered here`() {
        // Without this the user arrives at an empty chat and has to type the question again —
        // which is the same refusal, one navigation later.
        val error = apply(
            Proposal(
                id = "4", kind = ProposalKind.OPEN_GOAL, title = "x",
                goalId = "12", openSubject = "the description",
            ),
            askedFor = "add a description to Cake goal",
        )

        assertNull(error)
        assertEquals(listOf("open:12"), applied)
        assertEquals("add a description to Cake goal", AiHandoff.take("12"))
    }

    @Test
    fun `a handoff is read once, so returning to the goal does not re-ask it`() {
        apply(
            Proposal(id = "5", kind = ProposalKind.OPEN_GOAL, title = "x", goalId = "77"),
            askedFor = "add a target",
        )

        assertEquals("add a target", AiHandoff.take("77"))
        assertNull(AiHandoff.take("77"))
    }

    @Test
    fun `a goal-level card with no goal id says so instead of doing nothing`() {
        val error = apply(Proposal(id = "6", kind = ProposalKind.EDIT_GOAL, title = "x", field = "title"))

        assertEquals("I couldn't tell which goal that was — say which one and I'll try again.", error)
        assertEquals(emptyList<String>(), applied)
    }

    @Test
    fun `something that belongs inside a goal is still sent there`() {
        val error = apply(Proposal(id = "7", kind = ProposalKind.OPTION, title = "Try evenings"))

        assertEquals("Open that goal and ask there — that change lives inside it.", error)
        assertEquals(emptyList<String>(), applied)
    }
}
