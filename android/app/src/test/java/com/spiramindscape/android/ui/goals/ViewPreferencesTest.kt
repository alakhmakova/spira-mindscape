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

    /** Two goals, because the point of the scoping is that they cannot see each other's answers. */
    private val GOAL_A = "g1"
    private val GOAL_B = "g2"

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
        store.edit().putString("app.sort", SortKey.Deadline.name).putInt("schema", 2).commit()

        val view = GoalViewPreferences(store)

        assertFalse(view.locked)
        assertEquals(SortKey.Recent, view.sort)
    }

    /**
     * A **version 1** file holds one arrangement shared by every goal — the defect the per-goal
     * scoping fixes. It cannot honestly be attributed to any single goal, so it is dropped whole
     * and the lists open on their defaults, padlock included.
     */
    @Test
    fun `a version 1 file is dropped rather than reinterpreted`() {
        val store = prefs("targets_v1")
        store.edit()
            .putBoolean("locked", true)
            .putString("filter", TargetFilter.Done.name)
            .commit()

        val view = TargetViewPreferences(store, GOAL_A)

        assertFalse(view.locked)
        assertEquals(TargetFilter.All, view.filter)
    }

    /**
     * **The All-goals list is the exception, and comes across intact.** It was never part of the
     * defect — it is app-wide, there is only one of it, and its v1 keys mean exactly what they mean
     * now. Losing a pinned dashboard arrangement would be the failure the padlock spec names,
     * inflicted by the fix for a different list.
     */
    @Test
    fun `a version 1 All-goals file is adopted, padlock included`() {
        val store = prefs("goals_v1")
        store.edit()
            .putBoolean("locked", true)
            .putString("sort", SortKey.Deadline.name)
            .putString("status", StatusFilter.Achieved.name)
            .putString("deadline_from", "2026-08-15")
            .putInt("confidence", 7)
            .commit()

        val view = GoalViewPreferences(store)

        assertTrue(view.locked)
        assertEquals(SortKey.Deadline, view.sort)
        assertEquals(StatusFilter.Achieved, view.status)
        assertEquals("2026-08-15", view.deadlineFrom)
        assertEquals(7, view.confidence)
    }

    /**
     * A blank goal id must never become a *shared* scope — that is the one flat bucket this scoping
     * removes. The guard lives in the constructor, so a store built directly cannot skip it.
     */
    @Test
    fun `a blank goal id gets an inert scope, not a shared one`() {
        val store = prefs("targets_blank")
        val blank = TargetViewPreferences(store, "")
        blank.setLocked(true) { blank.filter = TargetFilter.Done }

        assertEquals(TargetFilter.All, TargetViewPreferences(store, GOAL_A).filter)
        assertTrue(store.all.keys.any { it.startsWith("none.") })
    }

    // ── One goal's lists are its own ─────────────────────────────────────────

    /**
     * The report this scoping came from (owner, 2026-08-31): filters set on goal 1 with the padlock
     * shut were already on when goal 2 opened — with goal 2's padlock open — and changing them
     * there rewrote goal 1's pinned arrangement.
     */
    @Test
    fun `one goal's filter does not reach another goal`() {
        val store = prefs("targets_two_goals")
        val a = TargetViewPreferences(store, GOAL_A)
        a.setLocked(true) { a.filter = TargetFilter.Done }

        val b = TargetViewPreferences(store, GOAL_B)

        assertFalse(b.locked)
        assertEquals(TargetFilter.All, b.filter)
    }

    @Test
    fun `another goal cannot write through a closed padlock`() {
        val store = prefs("targets_write_through")
        val a = TargetViewPreferences(store, GOAL_A)
        a.setLocked(true) { a.filter = TargetFilter.Done }

        // Goal B's padlock is open, so this is dropped — and even a pinned B would write its own
        // keys, never A's.
        TargetViewPreferences(store, GOAL_B).filter = TargetFilter.NotDone

        assertEquals(TargetFilter.Done, TargetViewPreferences(store, GOAL_A).filter)
    }

    @Test
    fun `opening one goal's padlock leaves another goal's pinned`() {
        val store = prefs("targets_unlock_one")
        val a = TargetViewPreferences(store, GOAL_A)
        a.setLocked(true) { a.filter = TargetFilter.Done }
        val b = TargetViewPreferences(store, GOAL_B)
        b.setLocked(true) { b.filter = TargetFilter.NotDone }

        b.setLocked(false) {}

        val nextA = TargetViewPreferences(store, GOAL_A)
        assertTrue(nextA.locked)
        assertEquals(TargetFilter.Done, nextA.filter)
        assertFalse(TargetViewPreferences(store, GOAL_B).locked)
    }

    /** Options and resources are scoped the same way. */
    @Test
    fun `options and resources are per goal too`() {
        val optionStore = prefs("options_two_goals")
        val option = OptionViewPreferences(optionStore, GOAL_A)
        option.setLocked(true) { option.filter = OptionFilter.GoodIdea }

        val resourceStore = prefs("resources_two_goals")
        val resource = ResourceViewPreferences(resourceStore, GOAL_A)
        resource.setLocked(true) { resource.filter = ResourceFilter.Notes }

        assertEquals(OptionFilter.All, OptionViewPreferences(optionStore, GOAL_B).filter)
        assertEquals(ResourceFilter.All, ResourceViewPreferences(resourceStore, GOAL_B).filter)
    }

    // ── A goal's targets ─────────────────────────────────────────────────────

    @Test
    fun `the targets padlock pins its range too`() {
        val store = prefs("targets_range")
        val view = TargetViewPreferences(store, GOAL_A)

        view.setLocked(true) {
            view.sort = TargetSort.Deadline
            view.deadlineFrom = "2026-09-01"
            view.deadlineTo = "2026-09-30"
        }

        val next = TargetViewPreferences(store, GOAL_A)
        assertEquals(TargetSort.Deadline, next.sort)
        assertEquals("2026-09-01", next.deadlineFrom)
        assertEquals("2026-09-30", next.deadlineTo)
    }

    /** The screen state seeds itself from the store, which is what makes a pinned range come back. */
    @Test
    fun `the targets view state opens on the pinned range`() {
        val store = prefs("targets_state")
        val preferences = TargetViewPreferences(store, GOAL_A)
        preferences.setLocked(true) { preferences.deadlineFrom = "2026-09-01" }

        assertEquals("2026-09-01", targetViewState(TargetViewPreferences(store, GOAL_A)).deadlineFrom)
    }

    /**
     * Reset all clears the range in the store as well as on screen — otherwise the field would go
     * empty and the old range would come back on the next visit.
     */
    @Test
    fun `reset all clears a pinned range in the store`() {
        val store = prefs("targets_reset")
        val state = targetViewState(TargetViewPreferences(store, GOAL_A))
        state.locked = true
        state.deadlineFrom = "2026-09-01"

        state.resetAll()

        assertEquals("", TargetViewPreferences(store, GOAL_A).deadlineFrom)
    }

    /**
     * **"No deadline" and a date range can never both be on** (owner, 2026-08-18) — and now that
     * the range is pinned, taking it off has to reach the store as well.
     */
    @Test
    fun `choosing No deadline clears the stored range`() {
        val store = prefs("targets_none")
        val state = targetViewState(TargetViewPreferences(store, GOAL_A))
        state.locked = true
        state.deadlineFrom = "2026-09-01"

        state.deadlineFilter = TargetDeadlineFilter.None

        assertEquals("", TargetViewPreferences(store, GOAL_A).deadlineFrom)
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
