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

    companion object {
        private const val PREFS_NAME = "spira_target_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_FILTER = "filter"

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
        )
    }
}
