package com.spiramindscape.android.data.goals

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.graphql.AddOptionMutation
import com.spiramindscape.android.graphql.AddRealityItemMutation
import com.spiramindscape.android.graphql.CreateGoalMutation
import com.spiramindscape.android.graphql.CreateResourceMutation
import com.spiramindscape.android.graphql.CreateTargetMutation
import com.spiramindscape.android.graphql.DeleteGoalMutation
import com.spiramindscape.android.graphql.DeleteResourceMutation
import com.spiramindscape.android.graphql.DeleteTargetMutation
import com.spiramindscape.android.graphql.GetGoalQuery
import com.spiramindscape.android.graphql.GetGoalsQuery
import com.spiramindscape.android.graphql.RemoveOptionMutation
import com.spiramindscape.android.graphql.RemoveRealityItemMutation
import com.spiramindscape.android.graphql.ReorderOptionsMutation
import com.spiramindscape.android.graphql.SelectOptionMutation
import com.spiramindscape.android.graphql.UpdateGoalMutation
import com.spiramindscape.android.graphql.UpdateOptionMutation
import com.spiramindscape.android.graphql.UpdateRealityItemMutation
import com.spiramindscape.android.graphql.UpdateResourceMutation
import com.spiramindscape.android.graphql.UpdateTargetMutation
import com.spiramindscape.android.graphql.type.ChecklistItemInput
import com.spiramindscape.android.graphql.type.CreateGoalInput
import com.spiramindscape.android.graphql.type.CreateResourceInput
import com.spiramindscape.android.graphql.type.CreateTargetInput
import com.spiramindscape.android.graphql.type.UpdateGoalInput
import com.spiramindscape.android.graphql.type.UpdateOptionInput
import com.spiramindscape.android.graphql.type.UpdateResourceInput
import com.spiramindscape.android.graphql.type.UpdateTargetInput

/** A goal as the dashboard cards need it — mapped from the GraphQL model, decoupled from Apollo. */
data class GoalSummary(
    val id: String,
    val title: String,
    val confidence: Int,
    val deadline: String?,
    val progress: Float, // 0f..1f, computed server-side
    val targetCount: Int,
    val achieved: Boolean,
    val createdAt: String = "", // ISO; used for "most recent" sort
)

/** Raised when a goal query/mutation fails (network or GraphQL error). */
class GoalsException(message: String) : Exception(message)

/** Loads goals and applies target updates. An interface so view models can use fakes in tests. */
interface GoalsRepository {
    suspend fun getGoals(): List<GoalSummary>
    suspend fun getGoal(id: String): GoalDetail
    suspend fun setTargetDone(targetId: String, done: Boolean): TargetItem
    suspend fun setTargetCurrent(targetId: String, current: Double): TargetItem
    suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>): TargetItem

    // Goals
    suspend fun createGoal(title: String, description: String?, confidence: Int, deadline: String?): String
    suspend fun updateGoal(
        id: String,
        title: String? = null,
        description: String? = null,
        confidence: Int? = null,
        deadline: Optional<String?> = Optional.Absent,
    )
    suspend fun deleteGoal(id: String)

    // Reality (kind = "actions" | "obstacles")
    suspend fun addReality(goalId: String, kind: String, text: String)
    suspend fun updateReality(goalId: String, kind: String, itemId: String, text: String)
    suspend fun removeReality(goalId: String, kind: String, itemId: String)

    // Options
    suspend fun addOption(goalId: String, text: String)
    suspend fun setOptionText(goalId: String, optionId: String, text: String)
    suspend fun selectOption(goalId: String, optionId: String)
    suspend fun deselectOption(goalId: String, optionId: String)
    suspend fun removeOption(goalId: String, optionId: String)
    suspend fun reorderOptions(goalId: String, optionIds: List<String>)

    // Targets
    suspend fun createTarget(goalId: String, input: CreateTargetInput)
    suspend fun setTargetTitle(targetId: String, title: String): TargetItem
    suspend fun deleteTarget(targetId: String)

    // Resources
    suspend fun createResource(goalId: String, input: CreateResourceInput)
    suspend fun updateResource(id: String, input: UpdateResourceInput)
    suspend fun removeResource(id: String)
}

class ApolloGoalsRepository(private val apollo: ApolloClient) : GoalsRepository {

    override suspend fun getGoals(): List<GoalSummary> {
        val goals = apollo.query(GetGoalsQuery()).executeOrThrow().data?.goals ?: emptyList()
        return goals.map { g ->
            GoalSummary(
                id = g.id,
                title = g.title,
                confidence = g.confidence,
                deadline = g.deadline,
                progress = g.progress.toFloat().coerceIn(0f, 1f),
                targetCount = g.targets.size,
                achieved = g.achievedAt != null,
                createdAt = g.createdAt,
            )
        }
    }

    override suspend fun getGoal(id: String): GoalDetail {
        val g = apollo.query(GetGoalQuery(id)).executeOrThrow().data?.goalById
            ?: throw GoalsException("Goal not found")
        return GoalDetail(
            id = g.id,
            title = g.title,
            description = g.description,
            confidence = g.confidence,
            deadline = g.deadline,
            progress = g.progress.toFloat().coerceIn(0f, 1f),
            achieved = g.achievedAt != null,
            actions = g.reality.actions.map { TextItem(it.id, it.text) },
            obstacles = g.reality.obstacles.map { TextItem(it.id, it.text) },
            options = g.options.map { OptionItem(it.id, it.text, it.selected, it.position) },
            targets = g.targets.map { t ->
                buildTarget(
                    id = t.id, type = t.type, title = t.title, progress = t.progress,
                    deadline = t.deadline, achievedAt = t.achievedAt, done = t.done,
                    current = t.current, total = t.total, start = t.start, unit = t.unit,
                    items = t.items.map { ChecklistItemModel(it.id, it.text, it.done) },
                )
            },
            resources = g.resources.map {
                ResourceItem(
                    id = it.id, type = it.type, title = it.title, body = it.body, url = it.url,
                    mime = it.mime, dataUrl = it.dataUrl, driveWebViewLink = it.driveWebViewLink,
                    name = it.name, email = it.email, role = it.role, phone = it.phone,
                )
            },
            confidenceHistory = g.confidenceHistory.map { ConfidenceHistoryEntry(it.id, it.confidence, it.at) },
        )
    }

    override suspend fun setTargetDone(targetId: String, done: Boolean): TargetItem =
        updateTarget(targetId, UpdateTargetInput(done = Optional.present(done)))

    override suspend fun setTargetCurrent(targetId: String, current: Double): TargetItem =
        updateTarget(targetId, UpdateTargetInput(current = Optional.present(current)))

    override suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>): TargetItem =
        updateTarget(
            targetId,
            UpdateTargetInput(
                items = Optional.present(
                    items.map {
                        ChecklistItemInput(
                            // A blank id marks a new item — omit it so the server creates one.
                            id = if (it.id.isBlank()) Optional.Absent else Optional.present(it.id),
                            text = it.text,
                            done = Optional.present(it.done),
                        )
                    },
                ),
            ),
        )

    // ---- Goals ----

    override suspend fun createGoal(title: String, description: String?, confidence: Int, deadline: String?): String {
        val input = CreateGoalInput(
            title = title,
            description = Optional.presentIfNotNull(description),
            confidence = confidence,
            deadline = Optional.presentIfNotNull(deadline),
        )
        return apollo.mutation(CreateGoalMutation(input)).executeOrThrow().data?.createGoal?.id
            ?: throw GoalsException("Create failed")
    }

    override suspend fun updateGoal(
        id: String,
        title: String?,
        description: String?,
        confidence: Int?,
        deadline: Optional<String?>,
    ) {
        val input = UpdateGoalInput(
            title = Optional.presentIfNotNull(title),
            description = Optional.presentIfNotNull(description),
            confidence = Optional.presentIfNotNull(confidence),
            deadline = deadline,
        )
        apollo.mutation(UpdateGoalMutation(id, input)).executeOrThrow()
    }

    override suspend fun deleteGoal(id: String) {
        apollo.mutation(DeleteGoalMutation(id)).executeOrThrow()
    }

    // ---- Reality ----

    override suspend fun addReality(goalId: String, kind: String, text: String) {
        apollo.mutation(AddRealityItemMutation(goalId, kind, text)).executeOrThrow()
    }

    override suspend fun updateReality(goalId: String, kind: String, itemId: String, text: String) {
        apollo.mutation(UpdateRealityItemMutation(goalId, kind, itemId, text)).executeOrThrow()
    }

    override suspend fun removeReality(goalId: String, kind: String, itemId: String) {
        apollo.mutation(RemoveRealityItemMutation(goalId, kind, itemId)).executeOrThrow()
    }

    // ---- Options ----

    override suspend fun addOption(goalId: String, text: String) {
        apollo.mutation(AddOptionMutation(goalId, text)).executeOrThrow()
    }

    override suspend fun setOptionText(goalId: String, optionId: String, text: String) {
        val input = UpdateOptionInput(text = Optional.present(text))
        apollo.mutation(UpdateOptionMutation(goalId, optionId, input)).executeOrThrow()
    }

    override suspend fun selectOption(goalId: String, optionId: String) {
        apollo.mutation(SelectOptionMutation(goalId, optionId)).executeOrThrow()
    }

    override suspend fun deselectOption(goalId: String, optionId: String) {
        val input = UpdateOptionInput(selected = Optional.present(false))
        apollo.mutation(UpdateOptionMutation(goalId, optionId, input)).executeOrThrow()
    }

    override suspend fun removeOption(goalId: String, optionId: String) {
        apollo.mutation(RemoveOptionMutation(goalId, optionId)).executeOrThrow()
    }

    override suspend fun reorderOptions(goalId: String, optionIds: List<String>) {
        apollo.mutation(ReorderOptionsMutation(goalId, optionIds)).executeOrThrow()
    }

    // ---- Targets ----

    override suspend fun createTarget(goalId: String, input: CreateTargetInput) {
        apollo.mutation(CreateTargetMutation(goalId, input)).executeOrThrow()
    }

    override suspend fun setTargetTitle(targetId: String, title: String): TargetItem =
        updateTarget(targetId, UpdateTargetInput(title = Optional.present(title)))

    override suspend fun deleteTarget(targetId: String) {
        apollo.mutation(DeleteTargetMutation(targetId)).executeOrThrow()
    }

    // ---- Resources ----

    override suspend fun createResource(goalId: String, input: CreateResourceInput) {
        apollo.mutation(CreateResourceMutation(goalId, input)).executeOrThrow()
    }

    override suspend fun updateResource(id: String, input: UpdateResourceInput) {
        apollo.mutation(UpdateResourceMutation(id, input)).executeOrThrow()
    }

    override suspend fun removeResource(id: String) {
        apollo.mutation(DeleteResourceMutation(id)).executeOrThrow()
    }

    private suspend fun updateTarget(targetId: String, input: UpdateTargetInput): TargetItem {
        val t = apollo.mutation(UpdateTargetMutation(targetId, input)).executeOrThrow().data?.updateTarget
            ?: throw GoalsException("Update failed")
        return buildTarget(
            id = t.id, type = t.type, title = t.title, progress = t.progress,
            deadline = t.deadline, achievedAt = t.achievedAt, done = t.done,
            current = t.current, total = t.total, start = t.start, unit = t.unit,
            items = t.items.map { ChecklistItemModel(it.id, it.text, it.done) },
        )
    }

    private fun buildTarget(
        id: String,
        type: String,
        title: String,
        progress: Double,
        deadline: String?,
        achievedAt: String?,
        done: Boolean?,
        current: Double?,
        total: Double?,
        start: Double?,
        unit: String?,
        items: List<ChecklistItemModel>,
    ): TargetItem {
        val p = progress.toFloat().coerceIn(0f, 1f)
        val achieved = achievedAt != null
        return when (type) {
            "binary" -> TargetItem.Binary(id, title, p, deadline, achieved, done ?: false)
            "numeric" -> TargetItem.Numeric(id, title, p, deadline, achieved, current ?: 0.0, total, start, unit)
            "checklist" -> TargetItem.Checklist(id, title, p, deadline, achieved, items)
            else -> TargetItem.Other(id, title, p, deadline, achieved)
        }
    }
}

/** Execute an Apollo call, turning network/GraphQL errors into a [GoalsException]. */
private suspend fun <D : Operation.Data> ApolloCall<D>.executeOrThrow(): ApolloResponse<D> {
    val response = execute()
    response.exception?.let { throw GoalsException(it.message ?: "Network error") }
    if (response.hasErrors()) {
        throw GoalsException(response.errors?.firstOrNull()?.message ?: "GraphQL error")
    }
    return response
}
