package com.spiramindscape.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.core.SpiraLog
import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalsRepository
import com.spiramindscape.android.data.goals.GoalsStore
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.util.stripResourceTokens
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.data.ai.stripHtml
import com.spiramindscape.android.graphql.type.ChecklistItemInput
import com.spiramindscape.android.graphql.type.CreateResourceInput
import com.spiramindscape.android.graphql.type.CreateTargetInput
import com.spiramindscape.android.graphql.type.UpdateResourceInput
import com.spiramindscape.android.ui.util.DetachPatch
import com.spiramindscape.android.ui.util.deadlineInfo
import com.spiramindscape.android.ui.util.isProgressLocked
import com.spiramindscape.android.ui.util.planResourceDetach
import com.spiramindscape.android.ui.util.resourceDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GoalUiState {
    data object Loading : GoalUiState
    data class Error(val message: String) : GoalUiState
    data class Content(val goal: GoalDetail) : GoalUiState
}

/**
 * The goal workspace: loads the full goal and applies low-friction target updates. After a target
 * change the server returns the updated target; we swap it in and recompute goal progress locally
 * (average of target progress — the same rule the backend uses) so the UI reacts immediately.
 */
class GoalWorkspaceViewModel(
    private val goalId: String,
    private val repository: GoalsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GoalUiState>(GoalUiState.Loading)
    val state: StateFlow<GoalUiState> = _state.asStateFlow()

    /**
     * A one-off failure message for an action the user explicitly took, shown over the
     * existing content instead of replacing it. Delete used to fail with no navigation, no
     * message and no log — the screen simply did nothing, which reads as the app ignoring
     * the tap. Cleared by [clearActionError] once shown.
     */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = GoalUiState.Loading
            try {
                setContent(repository.getGoal(goalId))
                markLoadedRevision()
            } catch (e: Exception) {
                SpiraLog.w(TAG, "goal_load_failed goalId=$goalId", e)
                _state.value = GoalUiState.Error("Couldn't load this goal.")
            }
        }
    }

    /**
     * Silent refetch (used on app resume): no spinner, keeps the current goal on failure.
     *
     * Gated on [GoalsStore.needsRefresh] — a resume first asks for the graph's change-signature and
     * refetches the goal only when it moved. Coming back to an unchanged goal used to re-download
     * everything attached to it. The revision is recorded only after the new content is applied,
     * so an interrupted refresh re-checks next time.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val revision = repository.goalsRevision()
                val key = GoalsStore.goalKey(goalId)
                if (!GoalsStore.needsRefresh(key, revision)) return@launch
                setContent(repository.getGoal(goalId))
                GoalsStore.markRefreshed(key, revision)
            } catch (e: Exception) {
                // Keep whatever is on screen — but a resume that silently never refreshes
                // looks like stale data, not like a failure, so it must leave a trace.
                SpiraLog.w(TAG, "goal_refresh_failed goalId=$goalId", e)
            }
        }
    }

    /**
     * Note the revision this freshly-loaded content matches, so the resume that fires right after
     * a cold start doesn't immediately fetch the same goal a second time. Best-effort: if the
     * signature can't be read the gate simply falls back to refetching, and nothing is lost.
     */
    private suspend fun markLoadedRevision() {
        runCatching { repository.goalsRevision() }
            .onSuccess { GoalsStore.markRefreshed(GoalsStore.goalKey(goalId), it) }
    }

    /** Set the goal content AND mirror its summary into the shared store so the dashboard card
     *  (title/confidence/deadline/progress/targets) stays in sync without a refetch. */
    private fun setContent(goal: GoalDetail) {
        _state.value = GoalUiState.Content(goal)
        GoalsStore.patch(goal.id) {
            it.copy(
                title = goal.title,
                confidence = goal.confidence,
                deadline = goal.deadline,
                progress = goal.progress,
                achieved = goal.achieved,
                targetCount = goal.targets.size,
            )
        }
    }

    fun setTargetDone(targetId: String, done: Boolean) =
        applyTargetUpdate { repository.setTargetDone(targetId, done) }

    fun setNumericCurrent(targetId: String, current: Double) =
        applyTargetUpdate { repository.setTargetCurrent(targetId, current) }

    fun toggleChecklistItem(targetId: String, itemId: String) {
        val target = checklistTarget(targetId) ?: return
        val newItems = target.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    /** Add a task to an existing checklist target (new item has a blank id → the server creates it). */
    fun addChecklistTask(targetId: String, text: String) {
        val target = checklistTarget(targetId) ?: return
        val newItems = target.items + ChecklistItemModel(id = "", text = text, done = false)
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    fun updateChecklistTask(targetId: String, itemId: String, text: String) {
        val target = checklistTarget(targetId) ?: return
        val newItems = target.items.map { if (it.id == itemId) it.copy(text = text) else it }
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    fun removeChecklistTask(targetId: String, itemId: String) {
        val target = checklistTarget(targetId) ?: return
        // A checklist target must keep at least one task — the server rejects an empty list, and
        // an emptied checklist has no meaning. The card surfaces this; here we simply refuse.
        if (target.items.size <= 1) return
        val newItems = target.items.filterNot { it.id == itemId }
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    /** Set (or clear, with null) one task's own deadline. */
    fun setChecklistTaskDeadline(targetId: String, itemId: String, deadline: String?) {
        val target = checklistTarget(targetId) ?: return
        val newItems = target.items.map { if (it.id == itemId) it.copy(deadline = deadline) else it }
        applyTargetUpdate { repository.setChecklistItems(targetId, newItems) }
    }

    private fun checklistTarget(targetId: String): TargetItem.Checklist? =
        (_state.value as? GoalUiState.Content)?.goal?.targets?.find { it.id == targetId } as? TargetItem.Checklist

    // ---- Goal fields (optimistic; reload on failure) ----

    fun setGoalTitle(title: String) =
        editGoal({ it.copy(title = title) }) { repository.updateGoal(goalId, title = title) }

    fun setGoalDescription(description: String) =
        editGoal({ it.copy(description = description) }) { repository.updateGoal(goalId, description = description) }

    /**
     * Changing confidence is not a plain field edit: the server also appends a row to
     * `confidenceHistory` (only the persisted history has real ids/timestamps, so the optimistic
     * copy can't predict it). Refetch on success so the "Confidence history" sheet reflects the
     * change immediately — otherwise the new value looked like it only updated the UI, not the
     * database, until the next unrelated reload.
     */
    fun setConfidence(confidence: Int) {
        val content = _state.value as? GoalUiState.Content ?: return
        setContent(content.goal.copy(confidence = confidence))
        viewModelScope.launch {
            try {
                repository.updateGoal(goalId, confidence = confidence)
                setContent(repository.getGoal(goalId))
            } catch (e: Exception) {
                load()
            }
        }
    }

    fun setDeadline(deadline: String?) =
        editGoal({ it.copy(deadline = deadline) }) { repository.updateGoal(goalId, deadline = Optional.present(deadline)) }

    fun deleteGoal(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteGoal(goalId)
                GoalsStore.remove(goalId)
                onDeleted()
            } catch (e: Exception) {
                // Stay on the screen; nothing was deleted — and say so. Confirming a delete
                // and having nothing happen is indistinguishable from a broken button.
                SpiraLog.w(TAG, "goal_delete_failed goalId=$goalId", e)
                _actionError.value = "Couldn't delete this goal. Please try again."
            }
        }
    }

    // ---- Targets ----

    fun setTargetTitle(targetId: String, title: String) =
        applyTargetUpdate { repository.setTargetTitle(targetId, title) }

    /** The padlock on a target card: pin progress so a stray tap can't nudge it. */
    fun setTargetProgressLocked(targetId: String, locked: Boolean) =
        applyTargetUpdate { repository.setTargetProgressLocked(targetId, locked) }

    fun setTargetDeadline(targetId: String, deadline: String?) =
        applyTargetUpdate { repository.setTargetDeadline(targetId, deadline) }

    fun setTargetNumbers(
        targetId: String,
        current: Double? = null,
        total: Double? = null,
        start: Double? = null,
    ) = applyTargetUpdate { repository.setTargetNumbers(targetId, current, total, start) }

    fun setTargetUnit(targetId: String, unit: String?) =
        applyTargetUpdate { repository.setTargetUnit(targetId, unit) }

    fun addTarget(
        title: String,
        type: String,
        deadline: String?,
        start: Double?,
        total: Double?,
        unit: String?,
        checklist: List<String>,
    ) {
        // The backend requires `start` for numeric targets (spec calls it optional with a 0
        // default), so default an empty start to 0 — exactly what the web does.
        val effectiveStart = if (type == "numeric") (start ?: 0.0) else start
        val input = CreateTargetInput(
            title = title,
            type = type,
            deadline = Optional.presentIfNotNull(deadline),
            start = Optional.presentIfNotNull(effectiveStart),
            total = Optional.presentIfNotNull(total),
            unit = Optional.presentIfNotNull(unit),
            items = if (type == "checklist") {
                Optional.present(checklist.map { ChecklistItemInput(text = it) })
            } else {
                Optional.Absent
            },
        )
        mutateThenReload { repository.createTarget(goalId, input) }
    }

    fun deleteTarget(targetId: String) = mutateThenReload { repository.deleteTarget(targetId) }

    // ---- Reality ----

    fun addReality(kind: String, text: String) = mutateThenReload { repository.addReality(goalId, kind, text) }
    fun updateReality(kind: String, itemId: String, text: String) =
        mutateThenReload { repository.updateReality(goalId, kind, itemId, text) }
    fun removeReality(kind: String, itemId: String) =
        mutateThenReload { repository.removeReality(goalId, kind, itemId) }

    // ---- Options ----

    fun addOption(text: String) = mutateThenReload { repository.addOption(goalId, text) }
    fun setOptionText(optionId: String, text: String) =
        mutateThenReload { repository.setOptionText(goalId, optionId, text) }
    fun removeOption(optionId: String) = mutateThenReload { repository.removeOption(goalId, optionId) }

    /**
     * The smiley badge's thumb lean — "none" | "good_idea" | "didnt_work". Optimistic, like the
     * active radio: the badge must flip under the finger, not a round-trip later.
     */
    fun setOptionStatus(optionId: String, status: String) =
        editGoal({ g -> g.copy(options = g.options.map { if (it.id == optionId) it.copy(status = status) else it }) }) {
            repository.setOptionStatus(goalId, optionId, status)
        }

    fun selectOption(optionId: String) =
        editGoal({ g -> g.copy(options = g.options.map { it.copy(selected = it.id == optionId) }) }) {
            repository.selectOption(goalId, optionId)
        }
    fun deselectOption(optionId: String) =
        editGoal({ g -> g.copy(options = g.options.map { if (it.id == optionId) it.copy(selected = false) else it }) }) {
            repository.deselectOption(goalId, optionId)
        }

    /** Move an option to [toPosition] (0-based); everything between shifts to make room. */
    fun reorderOptions(optionId: String, toPosition: Int) {
        val content = _state.value as? GoalUiState.Content ?: return
        val current = content.goal.options.sortedBy { it.position }
        val from = current.indexOfFirst { it.id == optionId }
        if (from == -1) return
        val to = toPosition.coerceIn(0, current.lastIndex)
        if (from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        val newIds = reordered.map { it.id }
        editGoal({ g -> g.copy(options = reordered.mapIndexed { idx, opt -> opt.copy(position = idx) }) }) {
            repository.reorderOptions(goalId, newIds)
        }
    }

    // ---- Resources ----

    fun addResource(
        type: String,
        title: String?,
        body: String?,
        url: String?,
        name: String?,
        email: String?,
        role: String?,
        phone: String?,
        mime: String? = null,
        dataUrl: String? = null,
    ) {
        val input = CreateResourceInput(
            type = type,
            title = Optional.presentIfNotNull(title),
            body = Optional.presentIfNotNull(body),
            url = Optional.presentIfNotNull(url),
            mime = Optional.presentIfNotNull(mime),
            dataUrl = Optional.presentIfNotNull(dataUrl),
            name = Optional.presentIfNotNull(name),
            email = Optional.presentIfNotNull(email),
            role = Optional.presentIfNotNull(role),
            phone = Optional.presentIfNotNull(phone),
        )
        // Remember which resource this call created, so the AI chat can offer "Open note" on the
        // card that proposed it. The id can only come from the refetch: `createResource` returns
        // Unit, so the new row is found by diffing the ids that were there a moment ago.
        val before = (_state.value as? GoalUiState.Content)?.goal?.resources?.map { it.id }.orEmpty().toSet()
        mutateThenReload(
            after = {
                if (type == "note") {
                    _lastCreatedNote.value = (_state.value as? GoalUiState.Content)
                        ?.goal?.resources?.firstOrNull { it.id !in before }
                }
            },
            block = { repository.createResource(goalId, input) },
        )
    }

    /**
     * The note the assistant most recently created, or null.
     *
     * It is a one-shot pointer rather than a list: the chat only ever offers to open the note it
     * just made, and holding more would be state nobody reads.
     */
    private val _lastCreatedNote = MutableStateFlow<ResourceItem?>(null)
    val lastCreatedNote: StateFlow<ResourceItem?> = _lastCreatedNote.asStateFlow()

    /**
     * Pull a file resource's bytes on demand and patch them into state, so the preview can render.
     * [GetGoalQuery] omits `dataUrl` — see `GetGoal.graphql` — because it would otherwise re-fetch
     * every attachment each time the screen resumes.
     *
     * Safe to call from composition on every recomposition: already-loaded and in-flight ids are
     * skipped, so one resource is fetched once.
     */
    fun loadResourceFile(resourceId: String) {
        val content = _state.value as? GoalUiState.Content ?: return
        val resource = content.goal.resources.firstOrNull { it.id == resourceId } ?: return
        if (resource.type != "file" || resource.dataUrl != null) return
        if (!fileLoads.add(resourceId)) return // already in flight

        viewModelScope.launch {
            try {
                val dataUrl = repository.resourceFile(resourceId)
                val latest = _state.value as? GoalUiState.Content
                if (dataUrl != null && latest != null) {
                    setContent(
                        latest.goal.copy(
                            resources = latest.goal.resources.map {
                                if (it.id == resourceId) it.copy(dataUrl = dataUrl) else it
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                // The preview stays empty with no error anywhere on screen, and the file the user
                // attached looks lost — invisible to them, so it has to be visible to us.
                SpiraLog.w(TAG, "resource_file_load_failed resourceId=$resourceId", e)
            } finally {
                fileLoads.remove(resourceId)
            }
        }
    }

    /** Ids whose bytes are being fetched, so concurrent previews share one request. */
    private val fileLoads = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun updateResource(
        id: String,
        title: String? = null,
        body: String? = null,
        url: String? = null,
        name: String? = null,
        email: String? = null,
        role: String? = null,
        phone: String? = null,
        mime: String? = null,
        dataUrl: String? = null,
    ) {
        val input = UpdateResourceInput(
            title = Optional.presentIfNotNull(title),
            body = Optional.presentIfNotNull(body),
            url = Optional.presentIfNotNull(url),
            mime = Optional.presentIfNotNull(mime),
            dataUrl = Optional.presentIfNotNull(dataUrl),
            name = Optional.presentIfNotNull(name),
            email = Optional.presentIfNotNull(email),
            role = Optional.presentIfNotNull(role),
            phone = Optional.presentIfNotNull(phone),
        )
        // Optimistic: callers always pass the full desired resource (unchanged fields echo the
        // current value), so we can apply it locally at once — no gap, no "saving" toast.
        editGoal({ g ->
            g.copy(
                resources = g.resources.map { r ->
                    if (r.id != id) r
                    else r.copy(
                        title = title, body = body, url = url, name = name, email = email,
                        role = role, phone = phone, mime = mime,
                        // A null dataUrl means "the file didn't change" — keep whatever bytes are
                        // already loaded rather than blanking the preview on a title edit.
                        dataUrl = dataUrl ?: r.dataUrl,
                    )
                },
            )
        }) { repository.updateResource(id, input) }
    }

    /**
     * Delete a resource — and first detach it from every element whose text references it, so no
     * `{{res:id}}` is left pointing at something that no longer exists. Each reference becomes the
     * resource's name, so the sentence keeps reading (web parity: `planResourceDetach`).
     *
     * Optimistic on the list itself, so deleting feels instant; the detach writes go through the
     * server one element at a time and the goal is refetched at the end.
     */
    fun removeResource(id: String) {
        val content = _state.value as? GoalUiState.Content ?: return
        val goal = content.goal
        val label = goal.resources.firstOrNull { it.id == id }
            ?.let { resourceDisplayName(it) }
            .orEmpty()
        val patches = planResourceDetach(goal, id, label)

        setContent(goal.copy(resources = goal.resources.filter { it.id != id }))
        viewModelScope.launch {
            try {
                for (patch in patches) {
                    when (patch) {
                        is DetachPatch.Option -> repository.setOptionText(goalId, patch.optionId, patch.text)
                        is DetachPatch.Reality ->
                            repository.updateReality(goalId, patch.kind, patch.itemId, patch.text)
                        is DetachPatch.TargetTitle -> repository.setTargetTitle(patch.targetId, patch.title)
                        is DetachPatch.Checklist -> repository.setChecklistItems(patch.targetId, patch.items)
                    }
                }
                repository.removeResource(id)
                // The detached text was rewritten server-side; pull it back so the cards show the
                // resource's name as plain words instead of the token they still hold locally.
                if (patches.isNotEmpty()) setContent(repository.getGoal(goalId))
            } catch (e: Exception) {
                // Something didn't land — resync rather than leaving a half-detached goal on screen.
                SpiraLog.w(TAG, "resource_detach_failed goalId=$goalId", e)
                load()
            }
        }
    }

    // ---- helpers ----

    /** Optimistically transform the goal, then run [block]; reload on failure to resync. */
    private fun editGoal(optimistic: (GoalDetail) -> GoalDetail, block: suspend () -> Unit) {
        val content = _state.value as? GoalUiState.Content ?: return
        setContent(optimistic(content.goal))
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                // Covers ~12 public edit methods, so this single line is where most silent
                // save failures on this screen become visible. The reload hides the failure
                // from the user by design (their edit just reverts), which is exactly why
                // it needs recording.
                SpiraLog.w(TAG, "goal_edit_failed goalId=$goalId", e)
                load()
            }
        }
    }

    /** Run a structural mutation, then silently refetch the goal so server ids/state are correct. */
    /** [after] runs once the refetch has landed, so it can read the row the mutation created. */
    private fun mutateThenReload(after: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                // Fall through to a refetch either way — it resyncs to the server truth.
                SpiraLog.w(TAG, "goal_mutation_failed goalId=$goalId", e)
            }
            try {
                setContent(repository.getGoal(goalId))
            } catch (e: Exception) {
                // Keep the current content if the refetch itself fails. Not logged: this is
                // the retry of a retry, and the first failure above already told the story.
            }
            after()
        }
    }

    private fun applyTargetUpdate(block: suspend () -> TargetItem) {
        val content = _state.value as? GoalUiState.Content ?: return
        viewModelScope.launch {
            try {
                var updated = block()
                // Reaching 100% pins the target AGAIN, even if it was deliberately unlocked
                // before. The lock belongs to *being* complete, not to the first time it happened:
                // an explicit `false` used to outlive the completion it was meant for, so after one
                // unlock a target never re-locked however often it hit 100%. Only an explicit
                // unlock needs clearing — an unset flag already locks itself at 100%.
                val before = content.goal.targets.firstOrNull { it.id == updated.id }
                if (before != null && before.progress < 1f &&
                    updated.progress >= 1f && updated.progressLocked == false
                ) {
                    updated = repository.setTargetProgressLocked(updated.id, true)
                }
                val newTargets = content.goal.targets.map { if (it.id == updated.id) updated else it }
                val progress =
                    if (newTargets.isEmpty()) 0f
                    else newTargets.map { it.progress }.average().toFloat()
                setContent(
                    content.goal.copy(targets = newTargets, progress = progress, achieved = progress >= 1f),
                )
            } catch (e: Exception) {
                // Reconcile with the server on failure rather than leaving a wrong optimistic value.
                SpiraLog.w(TAG, "target_update_failed goalId=$goalId", e)
                load()
            }
        }
    }

    companion object {
        private const val TAG = "GoalWorkspaceVM"

        fun factory(goalId: String, repository: GoalsRepository) =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GoalWorkspaceViewModel(goalId, repository) as T
            }
    }
}

enum class TargetSort(val label: String) { Name("Name"), Progress("Progress"), Deadline("Deadline") }

/**
 * The three questions the target filter asks, one per column of its menu (see
 * [com.spiramindscape.android.ui.components.SpiraMenuColumns]). Each is independent: a target has
 * to pass all three to be listed, and each is `All` until the user says otherwise.
 *
 * Labels are short on purpose — three columns of "Only not done" would not fit across a phone, and
 * the column's own heading already says what the words are answering.
 */
enum class TargetFilter(val label: String) {
    All("All"),
    Done("Done"),
    NotDone("Not done"),

    /** Under way: some progress recorded, but not finished. */
    Started("Started"),

    /** Untouched: no progress at all. */
    NotStarted("Not started"),
}

/** The deadline question. "Overdue" follows the card's own rule: past, and not yet achieved. */
enum class TargetDeadlineFilter(val label: String) {
    All("All"),
    Overdue("Overdue"),
    NotOverdue("Not overdue"),
    None("No deadline"),
}

/** The padlock question — whether progress is pinned (see `isProgressLocked`). */
enum class TargetLockFilter(val label: String) { All("All"), Locked("Locked"), Unlocked("Unlocked") }

/**
 * [Added] is the server's own order, which is the order the resources were created in — so the
 * word for it is **Created**. It used to read "As added", which named the mechanism rather than
 * the thing, and sat oddly beside "Name" and "Type".
 */
enum class ResourceSort(val label: String) { Added("Created"), Title("Name"), Type("Type") }

/** The kinds a resource can be filtered to. "All" — the column's heading says what of. */
enum class ResourceFilter(val label: String) {
    All("All"), Notes("Notes"), Links("Links"), Files("Files"), Contacts("Contacts")
}

/**
 * Does [text] contain [query]? The shared rule behind every list search: a blank query matches
 * everything, and `{{res:id}}` tokens are stripped first so a query can never match an id the user
 * cannot see on screen.
 */
private fun matches(query: String, vararg text: String?): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return text.any { it != null && stripResourceTokens(it) { "" }.contains(q, ignoreCase = true) }
}

/**
 * The Options page's filter: the thumb lean the user gave each option.
 *
 * [status] is the value stored on the option, so the enum is the single place that knows the
 * server's spelling. `Untried` is the absence of an opinion — the badge's grey outline.
 */
enum class OptionFilter(val label: String, val status: String?) {
    All("All", null),
    GoodIdea("Good idea", "good_idea"),
    BadIdea("Bad idea", "didnt_work"),
    Untried("Didn't try", "none"),
}

/**
 * Apply the Options page's search and lean filter. There is deliberately **no sort**: position is
 * the meaning of this list, and the user sets it by hand in reorder mode.
 */
fun applyOptionView(
    options: List<OptionItem>,
    query: String,
    filter: OptionFilter = OptionFilter.All,
): List<OptionItem> =
    options.sortedBy { it.position }
        // An option saved before the badge existed has an empty status, not "none"; both mean the
        // same thing to the user, so "Didn't try" has to catch either.
        .filter { filter.status == null || it.status.ifEmpty { "none" } == filter.status }
        .filter { matches(query, it.text) }

/**
 * Apply the Resources page's search, sort and filter.
 *
 * There is deliberately no "newest first": [ResourceItem] carries no `createdAt`, so that ordering
 * cannot be written on this side at all — it needs the field adding to the GraphQL `Resource` type
 * first. [ResourceSort.Added] is the server's own order, which is what the page showed before.
 */
fun applyResourceView(
    resources: List<ResourceItem>,
    query: String,
    sort: ResourceSort,
    ascending: Boolean,
    filter: ResourceFilter,
): List<ResourceItem> {
    val kindFor = { r: ResourceItem -> if (r.type == "img" || r.type == "image") "file" else r.type }
    var list = when (filter) {
        ResourceFilter.All -> resources
        ResourceFilter.Notes -> resources.filter { kindFor(it) == "note" }
        ResourceFilter.Links -> resources.filter { kindFor(it) == "link" }
        ResourceFilter.Files -> resources.filter { kindFor(it) == "file" }
        ResourceFilter.Contacts -> resources.filter { kindFor(it) == "email" }
    }
    // A note is remembered by its words, a link by its domain, a contact by the person — so the
    // query is tried against all of them, and against the note body with its HTML taken off.
    list = list.filter {
        matches(query, it.title, it.url, it.name, it.email, it.role, it.body?.let(::stripHtml))
    }
    val comparator: Comparator<ResourceItem> = when (sort) {
        ResourceSort.Added -> return if (ascending) list else list.reversed()
        ResourceSort.Title -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.title }
        ResourceSort.Type -> compareBy<ResourceItem> { kindFor(it) }
            .thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.title }
    }
    list = list.sortedWith(comparator)
    return if (ascending) list else list.reversed()
}

/**
 * Apply the workspace's search, sort and the three filter questions to the goal's targets.
 *
 * The questions are **independent** — a target has to pass state, deadline AND lock — so this is
 * three filters in sequence rather than one enum with every combination in it.
 */
fun applyTargetView(
    targets: List<TargetItem>,
    sort: TargetSort,
    ascending: Boolean,
    filter: TargetFilter,
    query: String = "",
    deadlineFilter: TargetDeadlineFilter = TargetDeadlineFilter.All,
    lockFilter: TargetLockFilter = TargetLockFilter.All,
): List<TargetItem> {
    var list = when (filter) {
        TargetFilter.All -> targets
        TargetFilter.Done -> targets.filter { it.progress >= 1f }
        TargetFilter.NotDone -> targets.filter { it.progress < 1f }
        TargetFilter.Started -> targets.filter { it.progress > 0f && it.progress < 1f }
        TargetFilter.NotStarted -> targets.filter { it.progress <= 0f }
    }
    list = when (deadlineFilter) {
        TargetDeadlineFilter.All -> list
        // "Overdue" is the card's own rule, not merely "in the past": a target that was achieved
        // late is finished, and listing it as overdue would be telling the user to act on it.
        TargetDeadlineFilter.Overdue -> list.filter { deadlineInfo(it.deadline, it.progress >= 1f)?.isOverdue == true }
        TargetDeadlineFilter.NotOverdue ->
            list.filter { it.deadline != null && deadlineInfo(it.deadline, it.progress >= 1f)?.isOverdue != true }
        TargetDeadlineFilter.None -> list.filter { it.deadline == null }
    }
    list = when (lockFilter) {
        TargetLockFilter.All -> list
        TargetLockFilter.Locked -> list.filter { isProgressLocked(it) }
        TargetLockFilter.Unlocked -> list.filter { !isProgressLocked(it) }
    }
    list = list.filter { matches(query, it.title) }
    val comparator: Comparator<TargetItem> = when (sort) {
        TargetSort.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        TargetSort.Progress -> compareBy { it.progress }
        TargetSort.Deadline -> compareBy(nullsLast()) { it.deadline }
    }
    list = list.sortedWith(comparator)
    return if (ascending) list else list.reversed()
}
