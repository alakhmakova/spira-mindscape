package com.spiramindscape.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsRepository
import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GoalsUiState {
    data object Loading : GoalsUiState
    data class Error(val message: String) : GoalsUiState
    data class Content(val goals: List<GoalSummary>) : GoalsUiState
}

/**
 * Loads the goals for the dashboard.
 *
 * - [load] shows a spinner then fetches (initial / retry button).
 * - [refresh] re-fetches **silently**, keeping the current list on failure — used on app resume
 *   so a change made on another device shows up without a reload (the native analogue of the
 *   web's refetch-on-return fix).
 */
class GoalsViewModel(private val repository: GoalsRepository) : ViewModel() {

    private val _state = MutableStateFlow<GoalsUiState>(GoalsUiState.Loading)
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = GoalsUiState.Loading
            _state.value = fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val next = fetch()
            // Don't replace visible goals with an error banner on a transient background failure.
            if (next is GoalsUiState.Content || _state.value !is GoalsUiState.Content) {
                _state.value = next
            }
        }
    }

    private suspend fun fetch(): GoalsUiState =
        try {
            GoalsUiState.Content(repository.getGoals())
        } catch (e: Exception) {
            GoalsUiState.Error("Couldn't load your goals.")
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(ApolloGoalsRepository(Network.apollo)) }
        }
    }
}
