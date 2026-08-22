package com.spiramindscape.android.ui.goals

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The padlock's contract, against a real `SharedPreferences` (Robolectric).
 *
 * **A closed padlock pins a list's filters and sort; an open one lets them go** — and the store is
 * the half of that nobody can see, which is why it had no test until the owner found the gap the
 * hard way (2026-08-22): she set a deadline range, shut the padlock, left the app and came back to
 * an empty field. The range was exempt by design and nothing on screen said so.
 *
 * Reading a *new* instance over the same prefs is how "after the app is closed and reopened" is
 * expressed here: that is exactly what the next launch does.
 */
@RunWith(RobolectricTestRunner::class)
class ViewPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun prefs(name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    // ── The goal dashboard ───────────────────────────────────────────────────

    @Test
    fun `writes nothing while the padlock is open`() {
        val store = prefs("goals_open")
        val view = GoalViewPreferences(store)

        view.sort = SortKey.Deadline
        view.deadlineFrom = "2026-08-15"

        assertEquals(SortKey.Recent, GoalViewPreferences(store).sort)
        assertEquals("", GoalViewPreferences(store).deadlineFrom)
    }

    @Test
    fun `closing the padlock pins what is on screen`() {
        val store = prefs("goals_close")
        val view = GoalViewPreferences(store)

        view.setLocked(true) {
            view.sort = SortKey.Deadline
            view.status = StatusFilter.Achieved
            view.deadlineFrom = "2026-08-15"
            view.deadlineTo = "2026-08-31"
        }

        val next = GoalViewPreferences(store) // the next launch
        assertTrue(next.locked)
        assertEquals(SortKey.Deadline, next.sort)
        assertEquals(StatusFilter.Achieved, next.status)
        assertEquals("2026-08-15", next.deadlineFrom)
        assertEquals("2026-08-31", next.deadlineTo)
    }

    /**
     * The defect this file was written for: the range used to be dropped while everything beside
     * it was kept, so the padlock delivered five of its seven answers without saying so.
     */
    @Test
    fun `the deadline range is pinned like every other question`() {
        val store = prefs("goals_range")
        val view = GoalViewPreferences(store)
        view.setLocked(true) {}

        view.deadlineFrom = "2026-08-15"

        assertEquals("2026-08-15", GoalViewPreferences(store).deadlineFrom)
    }

    @Test
    fun `keeps writing every later change, so it holds what is on screen`() {
        val store = prefs("goals_later")
        val view = GoalViewPreferences(store)
        view.setLocked(true) {}

        view.confidence = 7

        assertEquals(7, GoalViewPreferences(store).confidence)
    }

    @Test
    fun `opening the padlock clears what it was holding`() {
        val store = prefs("goals_unlock")
        val view = GoalViewPreferences(store)
        view.setLocked(true) { view.sort = SortKey.Deadline }

        view.setLocked(false) {}

        val next = GoalViewPreferences(store)
        assertFalse(next.locked)
        assertEquals(SortKey.Recent, next.sort)
    }

    /**
     * The upgrade sweep. Before the padlock these stores wrote unconditionally, so an installed
     * copy carries an arrangement with no padlock behind it — and an open padlock is supposed to
     * mean "nothing is kept".
     */
    @Test
    fun `an arrangement left by an older build is swept on first read`() {
        val store = prefs("goals_upgrade")
        store.edit().putString("sort", SortKey.Deadline.name).commit()

        val view = GoalViewPreferences(store)

        assertFalse(view.locked)
        assertEquals(SortKey.Recent, view.sort)
        assertTrue(store.all.isEmpty())
    }

    // ── A goal's targets ─────────────────────────────────────────────────────

    @Test
    fun `the targets padlock pins its range too`() {
        val store = prefs("targets_range")
        val view = TargetViewPreferences(store)

        view.setLocked(true) {
            view.sort = TargetSort.Deadline
            view.deadlineFrom = "2026-09-01"
            view.deadlineTo = "2026-09-30"
        }

        val next = TargetViewPreferences(store)
        assertEquals(TargetSort.Deadline, next.sort)
        assertEquals("2026-09-01", next.deadlineFrom)
        assertEquals("2026-09-30", next.deadlineTo)
    }

    /** The screen state seeds itself from the store, which is what makes a pinned range come back. */
    @Test
    fun `the targets view state opens on the pinned range`() {
        val store = prefs("targets_state")
        val preferences = TargetViewPreferences(store)
        preferences.setLocked(true) { preferences.deadlineFrom = "2026-09-01" }

        assertEquals("2026-09-01", targetViewState(TargetViewPreferences(store)).deadlineFrom)
    }

    /**
     * Reset all clears the range in the store as well as on screen — otherwise the field would go
     * empty and the old range would come back on the next visit.
     */
    @Test
    fun `reset all clears a pinned range in the store`() {
        val store = prefs("targets_reset")
        val state = targetViewState(TargetViewPreferences(store))
        state.locked = true
        state.deadlineFrom = "2026-09-01"

        state.resetAll()

        assertEquals("", TargetViewPreferences(store).deadlineFrom)
    }

    /**
     * **"No deadline" and a date range can never both be on** (owner, 2026-08-18) — and now that
     * the range is pinned, taking it off has to reach the store as well.
     */
    @Test
    fun `choosing No deadline clears the stored range`() {
        val store = prefs("targets_none")
        val state = targetViewState(TargetViewPreferences(store))
        state.locked = true
        state.deadlineFrom = "2026-09-01"

        state.deadlineFilter = TargetDeadlineFilter.None

        assertEquals("", TargetViewPreferences(store).deadlineFrom)
    }

    /** `rememberTargetViewState`'s body, without a composition to run it in. */
    private fun targetViewState(preferences: TargetViewPreferences) = TargetViewState(
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
