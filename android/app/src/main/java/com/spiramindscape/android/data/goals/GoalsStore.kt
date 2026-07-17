package com.spiramindscape.android.data.goals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory source of truth for the goals list, shared by the dashboard and the goal workspace
 * (the mobile analogue of the web's Zustand store). Editing a goal on the workspace patches the
 * matching card here immediately, so returning to the dashboard shows the change without waiting
 * for a refetch to land.
 */
object GoalsStore {
    private val _goals = MutableStateFlow<List<GoalSummary>>(emptyList())
    val goals: StateFlow<List<GoalSummary>> = _goals.asStateFlow()

    /** Replace the whole list (after a fetch). */
    fun setAll(list: List<GoalSummary>) {
        _goals.value = list
    }

    /** Optimistically update one goal's card (title/confidence/deadline/progress/…). No-op if absent. */
    fun patch(goalId: String, transform: (GoalSummary) -> GoalSummary) {
        _goals.update { list -> list.map { if (it.id == goalId) transform(it) else it } }
    }

    fun remove(goalId: String) {
        _goals.update { list -> list.filterNot { it.id == goalId } }
    }

    fun clear() {
        _goals.value = emptyList()
    }
}
