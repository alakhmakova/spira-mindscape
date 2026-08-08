package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsException
import com.spiramindscape.android.data.goals.GoalsStore
import com.spiramindscape.android.data.goals.FakeGoalsRepository
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
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        GoalsStore.clear() // the store is a shared singleton — start each test clean
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        GoalsStore.clear()
    }

    private val goal = GoalSummary("g1", "Learn Kotlin", 7, null, 0.5f, 3, false)

    private class FakeRepo(var result: suspend () -> List<GoalSummary>) : FakeGoalsRepository() {
        override suspend fun getGoals(): List<GoalSummary> = result()
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

    @Test
    fun `applyGoalView filters goals by title (case-insensitive)`() {
        val kotlin = GoalSummary("1", "Learn Kotlin", 5, null, 0f, 0, false)
        val marathon = GoalSummary("2", "Run a marathon", 5, null, 0f, 0, false)
        val result = applyGoalView(
            goals = listOf(kotlin, marathon),
            query = "kOtLiN",
            sort = SortKey.Title,
            ascending = true,
            status = StatusFilter.All,
        )
        assertEquals(listOf(kotlin), result)
    }

    @Test
    fun `createGoal creates then refreshes so the new goal appears`() = runTest(dispatcher) {
        var createdWith: Triple<String, Int, String?>? = null
        val newGoal = GoalSummary("g2", "New goal", 8, null, 0f, 0, false)
        val repo = object : FakeGoalsRepository() {
            var goals = listOf(goal)
            override suspend fun getGoals(): List<GoalSummary> = goals
            override suspend fun createGoal(title: String, description: String?, confidence: Int, deadline: String?): String {
                createdWith = Triple(title, confidence, deadline)
                goals = goals + newGoal
                return "g2"
            }
        }
        val vm = GoalsViewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.createGoal("New goal", "", 8, null)
        advanceUntilIdle()

        assertEquals(Triple("New goal", 8, null), createdWith) // blank description → null
        val content = vm.state.value as GoalsUiState.Content
        assertTrue(content.goals.any { it.id == "g2" })
        assertEquals(null, vm.actionError.value)
    }

    @Test
    fun `a failed createGoal explains itself instead of leaving the sheet silent`() =
        runTest(dispatcher) {
            // Pressing Create and having nothing happen — no goal, no message — reads as a
            // broken button. The list must survive so the user can retry.
            var navigated = false
            val repo = object : FakeGoalsRepository() {
                override suspend fun getGoals(): List<GoalSummary> = listOf(goal)
                override suspend fun createGoal(
                    title: String,
                    description: String?,
                    confidence: Int,
                    deadline: String?,
                ): String = throw GoalsException("network down")
            }
            val vm = GoalsViewModel(repo)
            vm.load()
            advanceUntilIdle()

            vm.createGoal("New goal", null, 8, null) { navigated = true }
            advanceUntilIdle()

            assertEquals(false, navigated)
            assertEquals("Couldn't create this goal. Please try again.", vm.actionError.value)
            // The button must be usable again, not stuck in its in-flight state.
            assertEquals(false, vm.creating.value)
            assertTrue(vm.state.value is GoalsUiState.Content)
        }

    @Test
    fun `dismissing the create failure clears it`() = runTest(dispatcher) {
        val repo = object : FakeGoalsRepository() {
            override suspend fun getGoals(): List<GoalSummary> = listOf(goal)
            override suspend fun createGoal(
                title: String,
                description: String?,
                confidence: Int,
                deadline: String?,
            ): String = throw GoalsException("network down")
        }
        val vm = GoalsViewModel(repo)
        vm.load()
        advanceUntilIdle()
        vm.createGoal("New goal", null, 8, null)
        advanceUntilIdle()

        vm.clearActionError()

        assertEquals(null, vm.actionError.value)
    }
}
