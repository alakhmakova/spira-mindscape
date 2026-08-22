package com.spiramindscape.android.ui.goals

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * How a list is sorted and filtered, kept across sessions **when its padlock is closed**.
 *
 * # The padlock
 *
 * **A closed padlock pins a list's filters and sort; an open one lets them go** (owner,
 * 2026-08-21). It sits in each filter sheet's Kale head, on the right beside the X, and the web
 * carries the identical control (`src/components/shell/shell-store.ts`).
 *
 *  - **Closed** — the arrangement is written to this store and comes back on the next visit, after
 *    the app has been swiped away, and after a reinstall-free restart. Every later change is
 *    written too, so the lock holds what is on screen rather than a snapshot of when it was shut.
 *  - **Open** — nothing is written and whatever was written is **cleared**, so the list opens on
 *    its defaults. That is what makes opening the padlock enough to forget: there is no separate
 *    "clear" step to find.
 *
 * Before the padlock these stores wrote unconditionally, which meant a filter picked once followed
 * the user around for weeks with nothing on screen admitting it.
 *
 * **The deadline range is pinned like everything else** (owner, 2026-08-22). It was exempt for a
 * day, on the argument that a range is about a moment rather than a standing choice; then the
 * owner set one, shut the padlock, left the app and came back to find it gone. A padlock that
 * promises to keep the arrangement and silently drops two of the answers is the worse failure —
 * and a restored range is no longer invisible, now that a list emptied by its own filter says so
 * in a warning notice and the trigger carries a dot.
 *
 * The **search box** is still never pinned: a query belongs to the screen it was typed on.
 */

/** A stored enum name that no longer maps to an entry (an old build's value) falls back silently. */
private fun <T : Enum<T>> SharedPreferences.readEnum(key: String, entries: List<T>, fallback: T): T {
    val stored = getString(key, null) ?: return fallback
    return entries.firstOrNull { it.name == stored } ?: fallback
}

/** The key every lockable store keeps its own padlock under. */
private const val KEY_LOCKED = "locked"

/**
 * Shared behaviour for a store behind a padlock: reads answer with the default while it is open,
 * writes are dropped, and closing it writes the whole arrangement at once.
 */
abstract class LockablePreferences(protected val prefs: SharedPreferences) {

    init {
        // **The upgrade sweep.** Before the padlock these stores wrote unconditionally, so an
        // installed copy has a filter sitting in here with no padlock behind it — and an open
        // padlock is supposed to mean "nothing is kept". Without this, the first launch after the
        // update would silently apply an arrangement the user has no way of seeing was still on.
        // The web does the same thing with a `persist` version bump (`shell-store.ts`).
        if (!prefs.getBoolean(KEY_LOCKED, false) && prefs.all.isNotEmpty()) {
            prefs.edit().clear().apply()
        }
    }

    /** Whether this list's arrangement is pinned. */
    val locked: Boolean get() = prefs.getBoolean(KEY_LOCKED, false)

    /**
     * Close or open the padlock.
     *
     * Closing pins **what is on screen right now** — [pinCurrent] is how the caller hands it over,
     * because the state lives with the screen, not here. Opening wipes the store, which is both
     * the "stop writing" and the "forget what you had" halves of the same gesture.
     */
    fun setLocked(next: Boolean, pinCurrent: () -> Unit) {
        if (next) {
            prefs.edit().putBoolean(KEY_LOCKED, true).apply()
            pinCurrent()
        } else {
            prefs.edit().clear().apply()
        }
    }

    /** Write one value, or drop it on the floor while the padlock is open. */
    protected fun writeEnum(key: String, value: Enum<*>) {
        if (locked) prefs.edit().putString(key, value.name).apply()
    }

    protected fun writeBoolean(key: String, value: Boolean) {
        if (locked) prefs.edit().putBoolean(key, value).apply()
    }

    protected fun writeString(key: String, value: String) {
        if (locked) prefs.edit().putString(key, value).apply()
    }

    protected fun readString(key: String, fallback: String) = prefs.getString(key, fallback) ?: fallback
}

/** The goal dashboard's sort + status/deadline/confidence filters. */
class GoalViewPreferences(prefs: SharedPreferences) : LockablePreferences(prefs) {

    var sort: SortKey
        get() = prefs.readEnum(KEY_SORT, SortKey.entries, SortKey.Recent)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, false)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var status: StatusFilter
        get() = prefs.readEnum(KEY_STATUS, StatusFilter.entries, StatusFilter.All)
        set(value) = writeEnum(KEY_STATUS, value)

    var deadline: DeadlineFilter
        get() = prefs.readEnum(KEY_DEADLINE, DeadlineFilter.entries, DeadlineFilter.Any)
        set(value) = writeEnum(KEY_DEADLINE, value)

    /** 1..10, or 0 for "any". */
    var confidence: Int
        get() = prefs.getInt(KEY_CONFIDENCE, 0)
        set(value) {
            if (locked) prefs.edit().putInt(KEY_CONFIDENCE, value).apply()
        }

    /** The deadline range's two ends, as ISO instants ("" = an open end). */
    var deadlineFrom: String
        get() = readString(KEY_DEADLINE_FROM, "")
        set(value) = writeString(KEY_DEADLINE_FROM, value)

    var deadlineTo: String
        get() = readString(KEY_DEADLINE_TO, "")
        set(value) = writeString(KEY_DEADLINE_TO, value)

    companion object {
        private const val PREFS_NAME = "spira_goal_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_STATUS = "status"
        private const val KEY_DEADLINE = "deadline"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_DEADLINE_FROM = "deadline_from"
        private const val KEY_DEADLINE_TO = "deadline_to"

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

/** The goal workspace's target sort + filters. */
class TargetViewPreferences(prefs: SharedPreferences) : LockablePreferences(prefs) {

    var sort: TargetSort
        get() = prefs.readEnum(KEY_SORT, TargetSort.entries, TargetSort.Name)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, true)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var filter: TargetFilter
        get() = prefs.readEnum(KEY_FILTER, TargetFilter.entries, TargetFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

    var deadlineFilter: TargetDeadlineFilter
        get() = prefs.readEnum(KEY_DEADLINE, TargetDeadlineFilter.entries, TargetDeadlineFilter.All)
        set(value) = writeEnum(KEY_DEADLINE, value)

    var lockFilter: TargetLockFilter
        get() = prefs.readEnum(KEY_LOCK, TargetLockFilter.entries, TargetLockFilter.All)
        set(value) = writeEnum(KEY_LOCK, value)

    var typeFilter: TargetTypeFilter
        get() = prefs.readEnum(KEY_TYPE, TargetTypeFilter.entries, TargetTypeFilter.All)
        set(value) = writeEnum(KEY_TYPE, value)

    /** The deadline range's two ends, as ISO instants ("" = an open end). */
    var deadlineFrom: String
        get() = readString(KEY_DEADLINE_FROM, "")
        set(value) = writeString(KEY_DEADLINE_FROM, value)

    var deadlineTo: String
        get() = readString(KEY_DEADLINE_TO, "")
        set(value) = writeString(KEY_DEADLINE_TO, value)

    companion object {
        private const val PREFS_NAME = "spira_target_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_FILTER = "filter"
        private const val KEY_DEADLINE = "deadline_filter"
        private const val KEY_LOCK = "lock_filter"
        private const val KEY_TYPE = "type_filter"
        private const val KEY_DEADLINE_FROM = "deadline_from"
        private const val KEY_DEADLINE_TO = "deadline_to"

        fun from(context: Context) = TargetViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

/**
 * A composition-local view state backed by [TargetViewPreferences]: reading it gives the current
 * choice, and setting it both recomposes and offers the choice to the store — which writes it only
 * while the padlock is closed.
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
    private val lockedState: MutableState<Boolean>,
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
                deadlineFrom = ""
                deadlineTo = ""
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
     * **Pinned like every other question** while the padlock is closed (owner, 2026-08-22) — see
     * the note at the top of this file for why the earlier exemption came off.
     */
    var deadlineFrom: String
        get() = deadlineFromState.value
        set(value) {
            deadlineFromState.value = value
            preferences.deadlineFrom = value
            if (value.isNotBlank()) clearNoDeadline()
        }

    var deadlineTo: String
        get() = deadlineToState.value
        set(value) {
            deadlineToState.value = value
            preferences.deadlineTo = value
            if (value.isNotBlank()) clearNoDeadline()
        }

    /** The other half of the rule on [deadlineFilter]: a range takes "No deadline" back off. */
    private fun clearNoDeadline() {
        if (deadlineFilterState.value == TargetDeadlineFilter.None) {
            deadlineFilterState.value = TargetDeadlineFilter.All
            preferences.deadlineFilter = TargetDeadlineFilter.All
        }
    }

    /** Whether this list's padlock is closed — see the note at the top of this file. */
    var locked: Boolean
        get() = lockedState.value
        set(value) {
            lockedState.value = value
            preferences.setLocked(value) {
                // Pin what is on screen, not what the store last happened to hold.
                preferences.sort = sortState.value
                preferences.ascending = ascendingState.value
                preferences.filter = filterState.value
                preferences.deadlineFilter = deadlineFilterState.value
                preferences.lockFilter = lockFilterState.value
                preferences.typeFilter = typeFilterState.value
                preferences.deadlineFrom = deadlineFromState.value
                preferences.deadlineTo = deadlineToState.value
            }
        }

    /**
     * Put every question back to its default — **every** one (owner, 2026-08-21).
     *
     * There used to be a class of "standing preference" that Reset all was not allowed to undo, so
     * a button promising everything quietly kept four answers.
     */
    fun resetAll() {
        sort = TargetSort.Name
        ascending = true
        filter = TargetFilter.All
        deadlineFilter = TargetDeadlineFilter.All
        lockFilter = TargetLockFilter.All
        typeFilter = TargetTypeFilter.All
        // Through the setters, so a reset made while the padlock is closed is written down too —
        // otherwise the cleared range would come back on the next visit.
        deadlineFrom = ""
        deadlineTo = ""
    }

    /** Whether anything is away from its default — what lights the trigger's dot. */
    val activeCount: Int
        get() = listOf(
            filter != TargetFilter.All,
            deadlineFilter != TargetDeadlineFilter.All,
            lockFilter != TargetLockFilter.All,
            typeFilter != TargetTypeFilter.All,
            deadlineFrom.isNotBlank() || deadlineTo.isNotBlank(),
            sort != TargetSort.Name || !ascending,
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
            deadlineFromState = mutableStateOf(preferences.deadlineFrom),
            deadlineToState = mutableStateOf(preferences.deadlineTo),
            lockedState = mutableStateOf(preferences.locked),
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
class ResourceViewPreferences(prefs: SharedPreferences) : LockablePreferences(prefs) {

    var sort: ResourceSort
        get() = prefs.readEnum(KEY_SORT, ResourceSort.entries, ResourceSort.Added)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = prefs.getBoolean(KEY_ASCENDING, true)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var filter: ResourceFilter
        get() = prefs.readEnum(KEY_FILTER, ResourceFilter.entries, ResourceFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

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

/** [TargetViewState]'s twin for resources. */
class ResourceViewState internal constructor(
    private val preferences: ResourceViewPreferences,
    private val sortState: MutableState<ResourceSort>,
    private val ascendingState: MutableState<Boolean>,
    private val filterState: MutableState<ResourceFilter>,
    private val lockedState: MutableState<Boolean>,
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

    var locked: Boolean
        get() = lockedState.value
        set(value) {
            lockedState.value = value
            preferences.setLocked(value) {
                preferences.sort = sortState.value
                preferences.ascending = ascendingState.value
                preferences.filter = filterState.value
            }
        }

    fun resetAll() {
        sort = ResourceSort.Added
        ascending = true
        filter = ResourceFilter.All
    }

    val activeCount: Int
        get() = listOf(
            filter != ResourceFilter.All,
            sort != ResourceSort.Added || !ascending,
        ).count { it }
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
            lockedState = mutableStateOf(preferences.locked),
        )
    }
}

/** The Options list's one question — the thumb lean — behind its own padlock. */
class OptionViewPreferences(prefs: SharedPreferences) : LockablePreferences(prefs) {

    var filter: OptionFilter
        get() = prefs.readEnum(KEY_FILTER, OptionFilter.entries, OptionFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

    companion object {
        private const val PREFS_NAME = "spira_option_view"
        private const val KEY_FILTER = "filter"

        fun from(context: Context) = OptionViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

/** [TargetViewState]'s twin for options. */
class OptionViewState internal constructor(
    private val preferences: OptionViewPreferences,
    private val filterState: MutableState<OptionFilter>,
    private val lockedState: MutableState<Boolean>,
) {
    var filter: OptionFilter
        get() = filterState.value
        set(value) {
            filterState.value = value
            preferences.filter = value
        }

    var locked: Boolean
        get() = lockedState.value
        set(value) {
            lockedState.value = value
            preferences.setLocked(value) { preferences.filter = filterState.value }
        }

    fun resetAll() {
        filter = OptionFilter.All
    }

    val activeCount: Int get() = if (filter != OptionFilter.All) 1 else 0
}

@Composable
fun rememberOptionViewState(): OptionViewState {
    val context = LocalContext.current
    return remember(context) {
        val preferences = OptionViewPreferences.from(context)
        OptionViewState(
            preferences = preferences,
            filterState = mutableStateOf(preferences.filter),
            lockedState = mutableStateOf(preferences.locked),
        )
    }
}
