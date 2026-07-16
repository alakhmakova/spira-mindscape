package com.spiramindscape.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalsRepository
import com.spiramindscape.android.data.goals.TargetItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GoalUiState {
    data object Loading : GoalUiState
    data class Error(val message: String) : GoalUiState
    data class Content(val goal: GoalDetail) : GoalUiState
}

/**
 * The goal workspace: loads the full goal and applies low-friction target updates. After a target
 * change the server returns the updated target; we swap it in and recompute goal progress locally
 * (average of target progress — the same rule the backend uses) so the UI reacts immediately.
 */
class GoalWorkspaceViewModel(
    private val goalId: String,
    private val repository: GoalsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GoalUiState>(GoalUiState.Loading)
    val state: StateFlow<GoalUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = GoalUiState.Loading
            _state.value = try {
                GoalUiState.Content(repository.getGoal(goalId))
            } catch (e: Exception) {
                GoalUiState.Error("Couldn't load this goal.")
            }
        }
    }

    fun setTargetDone(targetId: String, done: Boolean) =
        applyTargetUpdate { repository.setTargetDone(targetId, done) }

    fun setNumericCurrent(targetId: String, current: Double) =
        applyTargetUpdate { repository.setTargetCurrent(targetId, current) }

    fun toggleChecklistItem(targetId: String, itemId: String) {
        val content = _state.value as? GoalUiState.Content ?: return
        val target = content.goal.targets.find { it.id == targetId } as? TargetItem.Checklist ?: return
        val newItems = target.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    private fun applyTargetUpdate(block: suspend () -> TargetItem) {
        val content = _state.value as? GoalUiState.Content ?: return
        viewModelScope.launch {
            try {
                val updated = block()
                val newTargets = content.goal.targets.map { if (it.id == updated.id) updated else it }
                val progress =
                    if (newTargets.isEmpty()) 0f
                    else newTargets.map { it.progress }.average().toFloat()
                _state.value = GoalUiState.Content(
                    content.goal.copy(targets = newTargets, progress = progress, achieved = progress >= 1f),
                )
            } catch (e: Exception) {
                // Reconcile with the server on failure rather than leaving a wrong optimistic value.
                load()
            }
        }
    }

    companion object {
        fun factory(goalId: String, repository: GoalsRepository) =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GoalWorkspaceViewModel(goalId, repository) as T
            }
    }
}
