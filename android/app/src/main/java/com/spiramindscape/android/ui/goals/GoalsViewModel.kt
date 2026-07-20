package com.spiramindscape.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsRepository
import com.spiramindscape.android.data.goals.GoalsStore
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

enum class SortKey(val label: String) {
    Recent("Most recent"), Deadline("Deadline soonest"),
    Progress("Progress"), Confidence("Confidence"), Title("Title A–Z"),
}

enum class StatusFilter(val label: String) {
    All("All goals"), Achieved("Only achieved"), NotAchieved("Only not achieved"),
}

enum class DeadlineFilter(val label: String) {
    Any("Any deadline"), Has("Has a deadline"), None("No deadline"),
}

/**
 * Dashboard load state (Loading / Error / Content). The actual card list is the shared
 * [GoalsStore] (observed live by the screen), so an edit made on the goal workspace shows up
 * here immediately; this view model just drives loading/error and holds the search/sort/filter
 * controls.
 */
class GoalsViewModel(private val repository: GoalsRepository) : ViewModel() {

    private val _state = MutableStateFlow<GoalsUiState>(GoalsUiState.Loading)
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    val query = MutableStateFlow("")
    val sortKey = MutableStateFlow(SortKey.Recent)
    val sortAscending = MutableStateFlow(false)
    val statusFilter = MutableStateFlow(StatusFilter.All)
    val deadlineFilter = MutableStateFlow(DeadlineFilter.Any)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = GoalsUiState.Loading
            _state.value = fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val next = fetch()
            if (next is GoalsUiState.Content || _state.value !is GoalsUiState.Content) {
                _state.value = next
            }
        }
    }

    private suspend fun fetch(): GoalsUiState =
        try {
            val goals = repository.getGoals()
            GoalsStore.setAll(goals)
            GoalsUiState.Content(goals)
        } catch (e: Exception) {
            GoalsUiState.Error("Couldn't load your goals.")
        }

    fun createGoal(
        title: String,
        description: String?,
        confidence: Int,
        deadline: String?,
        onCreated: (String) -> Unit = {},
    ) {
        if (_creating.value) return
        _creating.value = true
        viewModelScope.launch {
            try {
                val id = repository.createGoal(title.trim(), description?.ifBlank { null }, confidence, deadline)
                _state.value = fetch()
                onCreated(id)
            } catch (e: Exception) {
                // keep the sheet open; the list is unchanged
            } finally {
                _creating.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(ApolloGoalsRepository(Network.apollo)) }
        }
    }
}

/** Apply search + status filter + sort to the goals list (used by the dashboard screen). */
fun applyGoalView(
    goals: List<GoalSummary>,
    query: String,
    sort: SortKey,
    ascending: Boolean,
    status: StatusFilter,
    deadline: DeadlineFilter = DeadlineFilter.Any,
): List<GoalSummary> {
    var list = goals
    if (query.isNotBlank()) {
        list = list.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    list = when (status) {
        StatusFilter.All -> list
        StatusFilter.Achieved -> list.filter { it.achieved }
        StatusFilter.NotAchieved -> list.filterNot { it.achieved }
    }
    list = when (deadline) {
        DeadlineFilter.Any -> list
        DeadlineFilter.Has -> list.filter { it.deadline != null }
        DeadlineFilter.None -> list.filter { it.deadline == null }
    }
    val comparator: Comparator<GoalSummary> = when (sort) {
        SortKey.Recent -> compareBy { it.createdAt }
        SortKey.Deadline -> compareBy(nullsLast()) { it.deadline }
        SortKey.Progress -> compareBy { it.progress }
        SortKey.Confidence -> compareBy { it.confidence }
        SortKey.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
    val sorted = list.sortedWith(comparator)
    return if (ascending) sorted else sorted.reversed()
}
