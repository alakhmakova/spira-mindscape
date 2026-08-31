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
 *
 * # Scope — whose questions are these?
 *
 * **A goal's lists remember their own arrangement** (owner, 2026-08-31). Targets, options and
 * resources are lists *inside a goal*, so each goal answers the panel's questions for itself and
 * carries its own padlock; only the All-goals dashboard's own list is app-wide. Every key such a
 * store writes is prefixed with the goal id (the `namespace` below), so one goal can neither read
 * nor clear another's.
 *
 * Before this, each list had one flat set of keys shared by every goal. Two things went wrong at
 * once, and the second is the worse of them: a filter set and pinned on goal 1 was already on when
 * goal 2 opened — with goal 2's padlock sitting open — and then changing it there rewrote goal 1's
 * arrangement behind goal 1's *closed* padlock. A lock another screen can write through is not a
 * lock. The web carries the identical fix (`src/components/shell/shell-store.ts`).
 */

/** The key every lockable store keeps its own padlock under. */
private const val KEY_LOCKED = "locked"

/**
 * The layout of a preferences file, so a change of shape can retire what it cannot reinterpret.
 *
 * Version 2 is the per-goal namespacing above, and a version 1 file holds one arrangement shared by
 * every goal. What happens to it depends on whose arrangement it actually was:
 *
 *  - **A goal-owned file** — targets, options, resources — is **dropped**. One global answer cannot
 *    honestly be attributed to any single goal, so the lists open on their defaults with every
 *    padlock open, exactly as a fresh install does.
 *  - **The app-wide file** (`spira_goal_view`) is **adopted**: its keys are renamed into
 *    [APP_SCOPE] and everything, the padlock included, comes across. The All-goals list was never
 *    part of the defect — there is only one of it and its v1 keys mean exactly what they mean now.
 *    Dropping a dashboard arrangement the user had pinned would be the failure the padlock spec
 *    names, inflicted by the fix for a different list.
 *
 * The web splits it the same way, through a `persist` version bump.
 */
private const val KEY_SCHEMA = "schema"
private const val SCHEMA_PER_GOAL = 2

/** The scope of the one list that belongs to the app rather than to a goal. */
private const val APP_SCOPE = "app"

/**
 * The scope a goal-owned store falls back to before its screen knows which goal it is showing.
 *
 * **A legibility guard, not an isolation one** — worth being precise about, because the sloppy
 * version of this sentence has already been wrong once here. No goal has a blank id, so a blank one
 * could never have collided with a real goal's answers whatever the fallback; every goal-less caller
 * shares this one scope, which is fine, because nothing meaningful is written before a goal loads.
 * What the name buys is that `none.filter` in the file says plainly "something wrote without a
 * goal", where the alternative is a key beginning with a bare `.` that reads as corruption.
 *
 * The web's `NO_GOAL` is the same guard.
 */
private const val NO_GOAL = "none"

/**
 * Shared behaviour for a store behind a padlock: reads answer with the default while it is open,
 * writes are dropped, and closing it writes the whole arrangement at once.
 *
 * @param namespace which scope's answers these are: [APP_SCOPE] for a list that belongs to the app,
 *   the goal id for one that belongs to a goal. It prefixes **every** key this store touches, which
 *   is what keeps two goals apart inside one file — including on a clear, which must never reach
 *   past its own scope. A blank one is normalised to [NO_GOAL] **here** rather than at the call
 *   sites, so a store constructed directly (the tests do) cannot skip it.
 * @param adoptLegacyKeys whether a pre-scoping file's unprefixed keys belong to *this* scope and
 *   can simply be renamed into it. True only for the app-wide store — see [KEY_SCHEMA].
 */
abstract class LockablePreferences(
    protected val prefs: SharedPreferences,
    namespace: String,
    private val adoptLegacyKeys: Boolean = false,
) {
    /** Never blank — see the note on [NO_GOAL]. Declared above `init`, which already uses it. */
    private val namespace: String = namespace.ifBlank { NO_GOAL }

    init {
        if (prefs.getInt(KEY_SCHEMA, 1) < SCHEMA_PER_GOAL) migrateToScopes()
        // **The upgrade sweep.** Before the padlock these stores wrote unconditionally, so an
        // installed copy has a filter sitting in here with no padlock behind it — and an open
        // padlock is supposed to mean "nothing is kept". Without this, the first launch after the
        // update would silently apply an arrangement the user has no way of seeing was still on.
        if (!locked && ownKeys().isNotEmpty()) clearOwn()
    }

    /**
     * Move a pre-scoping file to version 2: drop its unprefixed keys, or — for the app-wide store —
     * rename them into this scope, which is where they already belonged.
     */
    private fun migrateToScopes() {
        val legacy = prefs.all.filterKeys { it != KEY_SCHEMA && !it.contains('.') }
        val editor = prefs.edit().clear().putInt(KEY_SCHEMA, SCHEMA_PER_GOAL)
        if (adoptLegacyKeys) {
            for ((name, value) in legacy) {
                when (value) {
                    is String -> editor.putString(key(name), value)
                    is Boolean -> editor.putBoolean(key(name), value)
                    is Int -> editor.putInt(key(name), value)
                    else -> Unit // No other type is written by any of these stores.
                }
            }
        }
        editor.apply()
    }

    /** Where one of this scope's answers is filed. */
    private fun key(name: String) = "$namespace.$name"

    /**
     * Every key this scope owns — what an opening padlock clears, and nothing beyond it.
     *
     * The schema marker carries no prefix because it describes the file rather than any one scope,
     * so it can never be swept by one.
     */
    private fun ownKeys(): Set<String> =
        prefs.all.keys.filterTo(mutableSetOf()) { it.startsWith("$namespace.") }

    private fun clearOwn() {
        val editor = prefs.edit()
        for (name in ownKeys()) editor.remove(name)
        editor.apply()
    }

    /** Whether this list's arrangement is pinned, for this goal. */
    val locked: Boolean get() = prefs.getBoolean(key(KEY_LOCKED), false)

    /**
     * Close or open the padlock.
     *
     * Closing pins **what is on screen right now** — [pinCurrent] is how the caller hands it over,
     * because the state lives with the screen, not here. Opening wipes this scope, which is both
     * the "stop writing" and the "forget what you had" halves of the same gesture — and wipes only
     * this scope, so opening one goal's padlock leaves every other goal's pinned.
     */
    fun setLocked(next: Boolean, pinCurrent: () -> Unit) {
        if (next) {
            prefs.edit().putBoolean(key(KEY_LOCKED), true).apply()
            pinCurrent()
        } else {
            clearOwn()
        }
    }

    /** A stored enum name that no longer maps to an entry (an old build's value) falls back silently. */
    protected fun <T : Enum<T>> readEnum(name: String, entries: List<T>, fallback: T): T {
        val stored = prefs.getString(key(name), null) ?: return fallback
        return entries.firstOrNull { it.name == stored } ?: fallback
    }

    /** Write one value, or drop it on the floor while the padlock is open. */
    protected fun writeEnum(name: String, value: Enum<*>) {
        if (locked) prefs.edit().putString(key(name), value.name).apply()
    }

    protected fun writeBoolean(name: String, value: Boolean) {
        if (locked) prefs.edit().putBoolean(key(name), value).apply()
    }

    protected fun writeString(name: String, value: String) {
        if (locked) prefs.edit().putString(key(name), value).apply()
    }

    protected fun writeInt(name: String, value: Int) {
        if (locked) prefs.edit().putInt(key(name), value).apply()
    }

    protected fun readBoolean(name: String, fallback: Boolean) = prefs.getBoolean(key(name), fallback)

    protected fun readInt(name: String, fallback: Int) = prefs.getInt(key(name), fallback)

    protected fun readString(name: String, fallback: String) =
        prefs.getString(key(name), fallback) ?: fallback
}

/**
 * The All-goals dashboard's sort + status/deadline/confidence filters.
 *
 * The one list that belongs to the app rather than to a goal, so it takes no namespace — there is
 * only ever one of it. See "Scope" at the top of this file.
 */
class GoalViewPreferences(
    prefs: SharedPreferences,
) : LockablePreferences(prefs, APP_SCOPE, adoptLegacyKeys = true) {

    var sort: SortKey
        get() = readEnum(KEY_SORT, SortKey.entries, SortKey.Recent)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = readBoolean(KEY_ASCENDING, false)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var status: StatusFilter
        get() = readEnum(KEY_STATUS, StatusFilter.entries, StatusFilter.All)
        set(value) = writeEnum(KEY_STATUS, value)

    var deadline: DeadlineFilter
        get() = readEnum(KEY_DEADLINE, DeadlineFilter.entries, DeadlineFilter.Any)
        set(value) = writeEnum(KEY_DEADLINE, value)

    /** 1..10, or 0 for "any". */
    var confidence: Int
        get() = readInt(KEY_CONFIDENCE, 0)
        set(value) = writeInt(KEY_CONFIDENCE, value)

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

/** One goal's target sort + filters — see "Scope" at the top of this file. */
class TargetViewPreferences(
    prefs: SharedPreferences,
    goalId: String,
) : LockablePreferences(prefs, goalId) {

    var sort: TargetSort
        get() = readEnum(KEY_SORT, TargetSort.entries, TargetSort.Name)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = readBoolean(KEY_ASCENDING, true)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var filter: TargetFilter
        get() = readEnum(KEY_FILTER, TargetFilter.entries, TargetFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

    var deadlineFilter: TargetDeadlineFilter
        get() = readEnum(KEY_DEADLINE, TargetDeadlineFilter.entries, TargetDeadlineFilter.All)
        set(value) = writeEnum(KEY_DEADLINE, value)

    var lockFilter: TargetLockFilter
        get() = readEnum(KEY_LOCK, TargetLockFilter.entries, TargetLockFilter.All)
        set(value) = writeEnum(KEY_LOCK, value)

    var typeFilter: TargetTypeFilter
        get() = readEnum(KEY_TYPE, TargetTypeFilter.entries, TargetTypeFilter.All)
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

        fun from(context: Context, goalId: String) = TargetViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            goalId,
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

/**
 * This goal's target view state. Keyed on [goalId], so switching goals reseeds every question from
 * that goal's own store rather than carrying the last goal's answers across.
 */
@Composable
fun rememberTargetViewState(goalId: String): TargetViewState {
    val context = LocalContext.current
    return remember(context, goalId) {
        val preferences = TargetViewPreferences.from(context, goalId)
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
class ResourceViewPreferences(
    prefs: SharedPreferences,
    goalId: String,
) : LockablePreferences(prefs, goalId) {

    var sort: ResourceSort
        get() = readEnum(KEY_SORT, ResourceSort.entries, ResourceSort.Added)
        set(value) = writeEnum(KEY_SORT, value)

    var ascending: Boolean
        get() = readBoolean(KEY_ASCENDING, true)
        set(value) = writeBoolean(KEY_ASCENDING, value)

    var filter: ResourceFilter
        get() = readEnum(KEY_FILTER, ResourceFilter.entries, ResourceFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

    companion object {
        private const val PREFS_NAME = "spira_resource_view"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_FILTER = "filter"

        fun from(context: Context, goalId: String) = ResourceViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            goalId,
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

/** This goal's resource view state — keyed on [goalId], like the targets one. */
@Composable
fun rememberResourceViewState(goalId: String): ResourceViewState {
    val context = LocalContext.current
    return remember(context, goalId) {
        val preferences = ResourceViewPreferences.from(context, goalId)
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
class OptionViewPreferences(
    prefs: SharedPreferences,
    goalId: String,
) : LockablePreferences(prefs, goalId) {

    var filter: OptionFilter
        get() = readEnum(KEY_FILTER, OptionFilter.entries, OptionFilter.All)
        set(value) = writeEnum(KEY_FILTER, value)

    companion object {
        private const val PREFS_NAME = "spira_option_view"
        private const val KEY_FILTER = "filter"

        fun from(context: Context, goalId: String) = OptionViewPreferences(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            goalId,
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

/** This goal's option view state — keyed on [goalId], like the targets one. */
@Composable
fun rememberOptionViewState(goalId: String): OptionViewState {
    val context = LocalContext.current
    return remember(context, goalId) {
        val preferences = OptionViewPreferences.from(context, goalId)
        OptionViewState(
            preferences = preferences,
            filterState = mutableStateOf(preferences.filter),
            lockedState = mutableStateOf(preferences.locked),
        )
    }
}
