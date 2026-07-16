package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsRepository
import com.spiramindscape.android.data.goals.TargetItem
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

    private class FakeRepo(var goal: GoalDetail) : GoalsRepository {
        override suspend fun getGoals(): List<GoalSummary> = emptyList()
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
}
