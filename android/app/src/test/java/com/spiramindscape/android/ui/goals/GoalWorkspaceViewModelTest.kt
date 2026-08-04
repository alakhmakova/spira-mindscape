package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.ConfidenceHistoryEntry
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalSummary
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.data.goals.FakeGoalsRepository
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.goals.TextItem
import com.spiramindscape.android.graphql.type.CreateTargetInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalWorkspaceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun goalWithTwoBinaries() = GoalDetail(
        id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
        progress = 0.5f, achieved = false,
        actions = emptyList(), obstacles = emptyList(), options = emptyList(),
        targets = listOf(
            TargetItem.Binary("t1", "A", 1f, null, true, done = true),
            TargetItem.Binary("t2", "B", 0f, null, false, done = false),
        ),
        resources = emptyList(),
    )

    private class FakeRepo(var goal: GoalDetail) : FakeGoalsRepository() {
        override suspend fun getGoal(id: String): GoalDetail = goal
        override suspend fun setTargetDone(targetId: String, done: Boolean): TargetItem =
            TargetItem.Binary(targetId, "B", if (done) 1f else 0f, null, done, done)
        override suspend fun setTargetCurrent(targetId: String, current: Double): TargetItem =
            TargetItem.Numeric(targetId, "N", 0f, null, false, current, null, null, null)
        override suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>): TargetItem =
            TargetItem.Checklist(targetId, "C", items.count { it.done }.toFloat() / items.size, null, false, items)
    }

    @Test
    fun `loads the goal into Content`() = runTest(dispatcher) {
        val vm = GoalWorkspaceViewModel("g1", FakeRepo(goalWithTwoBinaries()))
        advanceUntilIdle()
        assertTrue(vm.state.value is GoalUiState.Content)
    }

    @Test
    fun `marking the last target done recomputes goal progress to complete`() = runTest(dispatcher) {
        val vm = GoalWorkspaceViewModel("g1", FakeRepo(goalWithTwoBinaries()))
        advanceUntilIdle()

        vm.setTargetDone("t2", true)
        advanceUntilIdle()

        val content = vm.state.value as GoalUiState.Content
        val t2 = content.goal.targets.first { it.id == "t2" } as TargetItem.Binary
        assertTrue(t2.done)
        assertEquals(1f, content.goal.progress, 0.001f) // avg(1, 1)
        assertTrue(content.goal.achieved)
    }

    @Test
    fun `addTarget creates on the server then reloads the goal`() = runTest(dispatcher) {
        val repo = object : FakeGoalsRepository() {
            var goal = goalWithTwoBinaries()
            var created = false
            override suspend fun getGoal(id: String): GoalDetail = goal
            override suspend fun createTarget(goalId: String, input: CreateTargetInput) {
                created = true
                goal = goal.copy(targets = goal.targets + TargetItem.Binary("t3", "C", 0f, null, false, false))
            }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.addTarget("C", "binary", null, null, null, null, emptyList())
        advanceUntilIdle()

        assertTrue(repo.created)
        val content = vm.state.value as GoalUiState.Content
        assertTrue(content.goal.targets.any { it.id == "t3" })
    }

    @Test
    fun `addTarget defaults a numeric target's start to 0 when it's not given`() = runTest(dispatcher) {
        var captured: CreateTargetInput? = null
        val repo = object : FakeGoalsRepository() {
            override suspend fun getGoal(id: String): GoalDetail = goalWithTwoBinaries()
            override suspend fun createTarget(goalId: String, input: CreateTargetInput) { captured = input }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.addTarget("Count", "numeric", null, /*start*/ null, /*total*/ 10.0, null, emptyList())
        advanceUntilIdle()

        assertEquals(Optional.present(0.0), captured?.start) // backend requires start; default to 0
        assertEquals(Optional.present(10.0), captured?.total)
    }

    @Test
    fun `addChecklistTask sends the new task with a blank id so the server creates it`() = runTest(dispatcher) {
        var captured: List<ChecklistItemModel>? = null
        val goal = GoalDetail(
            id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
            progress = 0f, achieved = false, actions = emptyList(), obstacles = emptyList(),
            options = emptyList(),
            targets = listOf(
                TargetItem.Checklist("t1", "Steps", 0f, null, false, listOf(ChecklistItemModel("i1", "one", false))),
            ),
            resources = emptyList(),
        )
        val repo = object : FakeGoalsRepository() {
            override suspend fun getGoal(id: String): GoalDetail = goal
            override suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>): TargetItem {
                captured = items
                return TargetItem.Checklist(targetId, "Steps", 0f, null, false, items)
            }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.addChecklistTask("t1", "two")
        advanceUntilIdle()

        assertTrue(captured!!.any { it.id == "" && it.text == "two" })
    }

    @Test
    fun `setConfidence refetches the goal so confidenceHistory reflects the new entry`() = runTest(dispatcher) {
        // Simulates the server: a successful update appends a history row, only visible after
        // a refetch — confirms the confidence-history sheet isn't stuck showing stale data.
        val repo = object : FakeGoalsRepository() {
            var goal = goalWithTwoBinaries().copy(confidence = 5, confidenceHistory = emptyList())
            override suspend fun getGoal(id: String): GoalDetail = goal
            override suspend fun updateGoal(
                id: String, title: String?, description: String?, confidence: Int?, deadline: Optional<String?>,
            ) {
                if (confidence != null) {
                    goal = goal.copy(
                        confidence = confidence,
                        confidenceHistory = listOf(ConfidenceHistoryEntry("h1", confidence, "2026-07-17T00:00:00Z")) +
                            goal.confidenceHistory,
                    )
                }
            }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.setConfidence(9)
        advanceUntilIdle()

        val content = vm.state.value as GoalUiState.Content
        assertEquals(9, content.goal.confidence)
        assertTrue(content.goal.confidenceHistory.any { it.confidence == 9 })
    }

    /**
     * Deleting a resource that is attached inline must first rewrite every element that references
     * it — otherwise the goal is left holding `{{res:id}}` tokens pointing at nothing.
     */
    @Test
    fun `removeResource detaches the resource everywhere before deleting it`() = runTest(dispatcher) {
        val attached = GoalDetail(
            id = "g1", title = "Goal", description = "", confidence = 5, deadline = null,
            progress = 0f, achieved = false,
            actions = listOf(TextItem("a1", "Read {{res:7}} tonight")),
            obstacles = emptyList(),
            options = listOf(OptionItem("o1", "Apply via {{res:7}}", selected = false)),
            targets = listOf(TargetItem.Binary("t1", "Prepare {{res:7}}", 0f, null, false, done = false)),
            resources = listOf(ResourceItem(id = "7", type = "note", title = "Job ad")),
        )
        val optionWrites = mutableListOf<String>()
        val realityWrites = mutableListOf<String>()
        val titleWrites = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val repo = object : FakeGoalsRepository() {
            override suspend fun getGoal(id: String): GoalDetail = attached
            override suspend fun setOptionText(goalId: String, optionId: String, text: String) {
                optionWrites += text
            }
            override suspend fun updateReality(goalId: String, kind: String, itemId: String, text: String) {
                realityWrites += text
            }
            override suspend fun setTargetTitle(targetId: String, title: String): TargetItem {
                titleWrites += title
                return TargetItem.Binary(targetId, title, 0f, null, false, done = false)
            }
            override suspend fun removeResource(id: String) { deleted += id }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.removeResource("7")
        advanceUntilIdle()

        assertEquals(listOf("Apply via Job ad"), optionWrites)
        assertEquals(listOf("Read Job ad tonight"), realityWrites)
        assertEquals(listOf("Prepare Job ad"), titleWrites)
        assertEquals(listOf("7"), deleted)
    }

    @Test
    fun `deleteGoal calls onDeleted after a successful delete`() = runTest(dispatcher) {
        var deletedCallback = false
        val repo = object : FakeGoalsRepository() {
            override suspend fun getGoal(id: String): GoalDetail = goalWithTwoBinaries()
            override suspend fun deleteGoal(id: String) { /* success */ }
        }
        val vm = GoalWorkspaceViewModel("g1", repo)
        advanceUntilIdle()

        vm.deleteGoal(onDeleted = { deletedCallback = true })
        advanceUntilIdle()

        assertTrue(deletedCallback)
    }
}
