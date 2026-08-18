package com.spiramindscape.android.ui.goals

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * How a list is sorted and filtered, remembered across sessions — mirrors the web, where the
 * goal/target status filter and view mode persist in `localStorage` (see
 * `src/components/shell/shell-store.ts`). Re-picking the same filter on every visit was the
 * complaint this fixes; the search box and the date ranges deliberately do NOT persist.
 */

/** A stored enum name that no longer maps to an entry (an old build's value) falls back silently. */
private fun <T : Enum<T>> SharedPreferences.readEnum(key: String, entries: List<T>, fallback: T): T {
    val stored = getString(key, null) ?: return fallback
    return entries.firstOrNull { it.name == stored } ?: fallback
}

private fun SharedPreferences.write(key: String, value: Enum<*>) =
    edit().putString(key, value.name).apply()

/** The goal dashboard's sort + status/deadline filters. */
class GoalViewPreferences(private val prefs: SharedPreferences) {

    var sort: SortKey
        get() = prefs.readEnum(KEY_SORT, SortKey.entries, SortKey.Recent)
        set(value) = prefs.write(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ASCENDING, value).apply()

    var status: StatusFilter
        get() = prefs.readEnum(KEY_STATUS, StatusFilter.entries, StatusFilter.All)
        set(value) = prefs.write(KEY_STATUS, value)

    var deadline: DeadlineFilter
        get() = prefs.readEnum(KEY_DEADLINE, DeadlineFilter.entries, DeadlineFilter.Any)
        set(value) = prefs.write(KEY_DEADLINE, value)

    companion object {
        private const val PREFS_NAME = "spira_goal_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_STATUS = "status"
        private const val KEY_DEADLINE = "deadline"

        fun from(context: Context) = GoalViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

@Composable
fun rememberGoalViewPreferences(): GoalViewPreferences {
    val context = LocalContext.current
    return remember(context) { GoalViewPreferences.from(context) }
}

/** The goal workspace's target sort + filter. */
class TargetViewPreferences(private val prefs: SharedPreferences) {

    var sort: TargetSort
        get() = prefs.readEnum(KEY_SORT, TargetSort.entries, TargetSort.Name)
        set(value) = prefs.write(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, true)
        set(value) = prefs.edit().putBoolean(KEY_ASCENDING, value).apply()

    var filter: TargetFilter
        get() = prefs.readEnum(KEY_FILTER, TargetFilter.entries, TargetFilter.All)
        set(value) = prefs.write(KEY_FILTER, value)

    var deadlineFilter: TargetDeadlineFilter
        get() = prefs.readEnum(KEY_DEADLINE, TargetDeadlineFilter.entries, TargetDeadlineFilter.All)
        set(value) = prefs.write(KEY_DEADLINE, value)

    var lockFilter: TargetLockFilter
        get() = prefs.readEnum(KEY_LOCK, TargetLockFilter.entries, TargetLockFilter.All)
        set(value) = prefs.write(KEY_LOCK, value)

    var typeFilter: TargetTypeFilter
        get() = prefs.readEnum(KEY_TYPE, TargetTypeFilter.entries, TargetTypeFilter.All)
        set(value) = prefs.write(KEY_TYPE, value)

    companion object {
        private const val PREFS_NAME = "spira_target_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_FILTER = "filter"
        private const val KEY_DEADLINE = "deadline_filter"
        private const val KEY_LOCK = "lock_filter"
        private const val KEY_TYPE = "type_filter"

        fun from(context: Context) = TargetViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

/**
 * A composition-local view state backed by [TargetViewPreferences]: reading it gives the stored
 * choice, and setting it both recomposes and writes the choice back.
 */
class TargetViewState internal constructor(
    private val preferences: TargetViewPreferences,
    private val sortState: MutableState<TargetSort>,
    private val ascendingState: MutableState<Boolean>,
    private val filterState: MutableState<TargetFilter>,
    private val deadlineFilterState: MutableState<TargetDeadlineFilter>,
    private val lockFilterState: MutableState<TargetLockFilter>,
    private val typeFilterState: MutableState<TargetTypeFilter>,
    private val deadlineFromState: MutableState<String>,
    private val deadlineToState: MutableState<String>,
) {
    var sort: TargetSort
        get() = sortState.value
        set(value) {
            sortState.value = value
            preferences.sort = value
        }

    var ascending: Boolean
        get() = ascendingState.value
        set(value) {
            ascendingState.value = value
            preferences.ascending = value
        }

    var filter: TargetFilter
        get() = filterState.value
        set(value) {
            filterState.value = value
            preferences.filter = value
        }

    /**
     * The deadline question.
     *
     * **"No deadline" and a date range cannot both be on** (owner, 2026-08-18): a range asks which
     * deadlines to keep and "No deadline" asks for the targets that haven't got one, so together
     * they can only ever match nothing — the list empties and neither control says why. Picking one
     * clears the other rather than leaving the user to guess which of two answers to undo.
     */
    var deadlineFilter: TargetDeadlineFilter
        get() = deadlineFilterState.value
        set(value) {
            deadlineFilterState.value = value
            preferences.deadlineFilter = value
            if (value == TargetDeadlineFilter.None) {
                deadlineFromState.value = ""
                deadlineToState.value = ""
            }
        }

    var lockFilter: TargetLockFilter
        get() = lockFilterState.value
        set(value) {
            lockFilterState.value = value
            preferences.lockFilter = value
        }

    var typeFilter: TargetTypeFilter
        get() = typeFilterState.value
        set(value) {
            typeFilterState.value = value
            preferences.typeFilter = value
        }

    /**
     * The deadline range's two ends, as ISO instants ("" = open end).
     *
     * Deliberately **not** stored: a date range is about a moment ("what is due this month"), not a
     * standing preference, and a range remembered from a fortnight ago would open the page on a
     * list that looks empty for no visible reason. The same rule the search box follows.
     */
    var deadlineFrom: String
        get() = deadlineFromState.value
        set(value) {
            deadlineFromState.value = value
            if (value.isNotBlank()) clearNoDeadline()
        }

    var deadlineTo: String
        get() = deadlineToState.value
        set(value) {
            deadlineToState.value = value
            if (value.isNotBlank()) clearNoDeadline()
        }

    /** The other half of the rule on [deadlineFilter]: a range takes "No deadline" back off. */
    private fun clearNoDeadline() {
        if (deadlineFilterState.value == TargetDeadlineFilter.None) {
            deadlineFilterState.value = TargetDeadlineFilter.All
            preferences.deadlineFilter = TargetDeadlineFilter.All
        }
    }

    /**
     * How many of the questions are narrowing the list — what the trigger shows in brackets.
     * A question left on `All` is not a filter, so an untouched toolbar reads "Filter", not
     * "Filter (0)". The range counts **once**, whichever of its two ends is set: it is one
     * question, and "(2)" for picking a From and a To would say two things are hidden.
     */
    val activeCount: Int
        get() = listOf(
            filter != TargetFilter.All,
            deadlineFilter != TargetDeadlineFilter.All,
            lockFilter != TargetLockFilter.All,
            typeFilter != TargetTypeFilter.All,
            deadlineFrom.isNotBlank() || deadlineTo.isNotBlank(),
        ).count { it }
}

@Composable
fun rememberTargetViewState(): TargetViewState {
    val context = LocalContext.current
    return remember(context) {
        val preferences = TargetViewPreferences.from(context)
        TargetViewState(
            preferences = preferences,
            sortState = mutableStateOf(preferences.sort),
            ascendingState = mutableStateOf(preferences.ascending),
            filterState = mutableStateOf(preferences.filter),
            deadlineFilterState = mutableStateOf(preferences.deadlineFilter),
            lockFilterState = mutableStateOf(preferences.lockFilter),
            typeFilterState = mutableStateOf(preferences.typeFilter),
            deadlineFromState = mutableStateOf(""),
            deadlineToState = mutableStateOf(""),
        )
    }
}

/**
 * The Resources page's sort + filter. Its own store, so it can never disturb the target one — the
 * existing keys are load-bearing for every installed copy of the app.
 *
 * The default is [ResourceSort.Added] ascending, which is the server's own order: the page looked
 * like that before it had a toolbar, so nobody's list rearranges itself on upgrade.
 */
class ResourceViewPreferences(private val prefs: SharedPreferences) {

    var sort: ResourceSort
        get() = prefs.readEnum(KEY_SORT, ResourceSort.entries, ResourceSort.Added)
        set(value) = prefs.write(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, true)
        set(value) = prefs.edit().putBoolean(KEY_ASCENDING, value).apply()

    var filter: ResourceFilter
        get() = prefs.readEnum(KEY_FILTER, ResourceFilter.entries, ResourceFilter.All)
        set(value) = prefs.write(KEY_FILTER, value)

    companion object {
        private const val PREFS_NAME = "spira_resource_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_FILTER = "filter"

        fun from(context: Context) = ResourceViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

/** [TargetViewState]'s twin for resources: read the stored choice, set it to recompose and store. */
class ResourceViewState internal constructor(
    private val preferences: ResourceViewPreferences,
    private val sortState: MutableState<ResourceSort>,
    private val ascendingState: MutableState<Boolean>,
    private val filterState: MutableState<ResourceFilter>,
) {
    var sort: ResourceSort
        get() = sortState.value
        set(value) {
            sortState.value = value
            preferences.sort = value
        }

    var ascending: Boolean
        get() = ascendingState.value
        set(value) {
            ascendingState.value = value
            preferences.ascending = value
        }

    var filter: ResourceFilter
        get() = filterState.value
        set(value) {
            filterState.value = value
            preferences.filter = value
        }
}

@Composable
fun rememberResourceViewState(): ResourceViewState {
    val context = LocalContext.current
    return remember(context) {
        val preferences = ResourceViewPreferences.from(context)
        ResourceViewState(
            preferences = preferences,
            sortState = mutableStateOf(preferences.sort),
            ascendingState = mutableStateOf(preferences.ascending),
            filterState = mutableStateOf(preferences.filter),
        )
    }
}
