package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsException
import com.spiramindscape.android.data.goals.GoalsRepository
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
class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val goal = GoalSummary("g1", "Learn Kotlin", 7, null, 0.5f, 3, false)

    private class FakeRepo(var result: suspend () -> List<GoalSummary>) : GoalsRepository {
        override suspend fun getGoals(): List<GoalSummary> = result()
        // Not exercised by these dashboard tests:
        override suspend fun getGoal(id: String) = throw NotImplementedError()
        override suspend fun setTargetDone(targetId: String, done: Boolean) = throw NotImplementedError()
        override suspend fun setTargetCurrent(targetId: String, current: Double) = throw NotImplementedError()
        override suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>) =
            throw NotImplementedError()
    }

    @Test
    fun `load emits Content on success`() = runTest(dispatcher) {
        val vm = GoalsViewModel(FakeRepo(result = { listOf(goal) }))
        vm.load()
        advanceUntilIdle()
        assertEquals(GoalsUiState.Content(listOf(goal)), vm.state.value)
    }

    @Test
    fun `load emits Error on failure`() = runTest(dispatcher) {
        val vm = GoalsViewModel(FakeRepo(result = { throw GoalsException("boom") }))
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is GoalsUiState.Error)
    }

    @Test
    fun `refresh keeps the current goals when a background fetch fails`() = runTest(dispatcher) {
        val repo = FakeRepo(result = { listOf(goal) })
        val vm = GoalsViewModel(repo)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is GoalsUiState.Content)

        repo.result = { throw GoalsException("network") }
        vm.refresh()
        advanceUntilIdle()

        // Still showing the goals, not an error banner.
        assertEquals(GoalsUiState.Content(listOf(goal)), vm.state.value)
    }
}
