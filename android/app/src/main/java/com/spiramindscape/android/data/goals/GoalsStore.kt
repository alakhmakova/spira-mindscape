package com.spiramindscape.android.data.goals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory source of truth for the goals list, shared by the dashboard and the goal workspace
 * (the mobile analogue of the web's Zustand store). Editing a goal on the workspace patches the
 * matching card here immediately, so returning to the dashboard shows the change without waiting
 * for a refetch to land.
 */
object GoalsStore {
    private val _goals = MutableStateFlow<List<GoalSummary>>(emptyList())
    val goals: StateFlow<List<GoalSummary>> = _goals.asStateFlow()

    /** Replace the whole list (after a fetch). */
    fun setAll(list: List<GoalSummary>) {
        _goals.value = list
    }

    /** Optimistically update one goal's card (title/confidence/deadline/progress/…). No-op if absent. */
    fun patch(goalId: String, transform: (GoalSummary) -> GoalSummary) {
        _goals.update { list -> list.map { if (it.id == goalId) transform(it) else it } }
    }

    fun remove(goalId: String) {
        _goals.update { list -> list.filterNot { it.id == goalId } }
    }

    fun clear() {
        _goals.value = emptyList()
        appliedRevisions.clear()
    }

    // ── Refresh gate ──────────────────────────────────────────────────────────
    //
    // Both screens refetch on resume, which on a goal with attachments used to mean pulling the
    // whole goal back every time the app came forward. `goalsRevision` is a short change-signature
    // for the user's entire graph, so a resume can ask "did anything move?" and usually stop there.
    //
    // Keyed per fetch, not globally: the dashboard's query and a workspace's query return
    // different data, so one screen applying a revision must not convince the other it is already
    // up to date.

    private val appliedRevisions = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Key for the dashboard's goals list. */
    const val GOALS_KEY = "goals"

    /** Key for one goal's workspace. */
    fun goalKey(goalId: String) = "goal:$goalId"

    /** True when [revision] is new for [key] — i.e. this fetch has real work to do. */
    fun needsRefresh(key: String, revision: String): Boolean = appliedRevisions[key] != revision

    /**
     * Record the revision a completed fetch was based on. Call it only *after* the data is
     * applied, so a failed or abandoned fetch re-checks next time rather than marking itself done.
     */
    fun markRefreshed(key: String, revision: String) {
        appliedRevisions[key] = revision
    }
}
