package com.spiramindscape.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spiramindscape.android.core.SpiraLog
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
    Recent("Created"), Deadline("Deadline"),
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

    /** A failure message for an action the user took, shown without wiping the goal list. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    val query = MutableStateFlow("")
    val sortKey = MutableStateFlow(SortKey.Recent)
    val sortAscending = MutableStateFlow(false)
    val statusFilter = MutableStateFlow(StatusFilter.All)
    val deadlineFilter = MutableStateFlow(DeadlineFilter.Any)

    /**
     * The deadline range's two ends, as ISO instants ("" = an open end), and the confidence the
     * user is looking for (0 = any). All three are the web's own dashboard filters
     * (`AppShell.tsx`), which the phone had no way to reach.
     *
     * All three are remembered while the dashboard's padlock is closed, like the sort and the
     * status pills — the range included (owner, 2026-08-22). Only the search box is never kept.
     * `GoalsDashboardScreen` is what does the writing; see the note at the top of
     * `ViewPreferences.kt`.
     */
    val deadlineFrom = MutableStateFlow("")
    val deadlineTo = MutableStateFlow("")
    val confidence = MutableStateFlow(0)

    /**
     * Set the deadline question, keeping it consistent with the range below it.
     *
     * **"No deadline" and a date range cannot both be on** (owner, 2026-08-18): a range asks which
     * deadlines to keep, and "no deadline" asks for the goals that haven't got one — together they
     * can only ever match nothing, so the list empties and neither control says why. Choosing one
     * clears the other rather than leaving the user to work out which of two answers to undo.
     */
    fun setDeadlineFilter(value: DeadlineFilter) {
        deadlineFilter.value = value
        if (value == DeadlineFilter.None) {
            deadlineFrom.value = ""
            deadlineTo.value = ""
        }
    }

    /** Set one end of the range, clearing "No deadline" if that is what was on. See [setDeadlineFilter]. */
    fun setDeadlineFrom(value: String) {
        deadlineFrom.value = value
        if (value.isNotBlank() && deadlineFilter.value == DeadlineFilter.None) {
            deadlineFilter.value = DeadlineFilter.Any
        }
    }

    /** The other end. See [setDeadlineFrom]. */
    fun setDeadlineTo(value: String) {
        deadlineTo.value = value
        if (value.isNotBlank() && deadlineFilter.value == DeadlineFilter.None) {
            deadlineFilter.value = DeadlineFilter.Any
        }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = GoalsUiState.Loading
            val next = fetch()
            _state.value = next
            // Note the revision this list matches so the resume firing right after a cold start
            // doesn't fetch it all over again. Best-effort: without it the gate just refetches.
            if (next is GoalsUiState.Content) {
                runCatching { repository.goalsRevision() }
                    .onSuccess { GoalsStore.markRefreshed(GoalsStore.GOALS_KEY, it) }
            }
        }
    }

    /**
     * Silent refetch on resume, gated on the graph's change-signature: the list is only pulled
     * again when something actually moved. Returning to an unchanged dashboard now costs a short
     * string instead of every goal.
     */
    fun refresh() {
        viewModelScope.launch {
            val revision = runCatching { repository.goalsRevision() }.getOrNull()
            if (revision != null && !GoalsStore.needsRefresh(GoalsStore.GOALS_KEY, revision)) return@launch
            val next = fetch()
            if (next is GoalsUiState.Content || _state.value !is GoalsUiState.Content) {
                _state.value = next
            }
            // Only after the list is applied — an errored fetch must re-check next resume.
            if (revision != null && next is GoalsUiState.Content) {
                GoalsStore.markRefreshed(GoalsStore.GOALS_KEY, revision)
            }
        }
    }

    private suspend fun fetch(): GoalsUiState =
        try {
            val goals = repository.getGoals()
            GoalsStore.setAll(goals)
            GoalsUiState.Content(goals)
        } catch (e: Exception) {
            // The user sees a friendly message; the real cause only exists here.
            SpiraLog.w(TAG, "goals_load_failed", e)
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
                // Keep the sheet open; the list is unchanged — but say why, or pressing
                // Create simply appears to do nothing.
                SpiraLog.w(TAG, "goal_create_failed", e)
                _actionError.value = "Couldn't create this goal. Please try again."
            } finally {
                _creating.value = false
            }
        }
    }

    companion object {
        private const val TAG = "GoalsVM"

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
    /** The **from** end of the deadline range, an ISO date/instant; blank means "no lower bound". */
    deadlineFrom: String = "",
    /** The **to** end, inclusive. */
    deadlineTo: String = "",
    /** One confidence value 1..10, or 0 for "any" — the web's dashboard filter. */
    confidence: Int = 0,
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
    // The range, the web's rule exactly (`index.tsx`): compare the **date part** only, both ends
    // inclusive, and a goal with no deadline is outside every range rather than inside all of them.
    if (deadlineFrom.isNotBlank() || deadlineTo.isNotBlank()) {
        list = list.filter { goal ->
            val day = goal.deadline?.take(10) ?: return@filter false
            if (deadlineFrom.isNotBlank() && day < deadlineFrom.take(10)) return@filter false
            if (deadlineTo.isNotBlank() && day > deadlineTo.take(10)) return@filter false
            true
        }
    }
    if (confidence in 1..10) {
        list = list.filter { it.confidence == confidence }
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
