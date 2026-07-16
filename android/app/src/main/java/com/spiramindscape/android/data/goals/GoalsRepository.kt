package com.spiramindscape.android.data.goals

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.graphql.GetGoalQuery
import com.spiramindscape.android.graphql.GetGoalsQuery
import com.spiramindscape.android.graphql.UpdateTargetMutation
import com.spiramindscape.android.graphql.type.ChecklistItemInput
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
            options = g.options.map { OptionItem(it.id, it.text, it.selected) },
            targets = g.targets.map { t ->
                buildTarget(
                    id = t.id, type = t.type, title = t.title, progress = t.progress,
                    deadline = t.deadline, achievedAt = t.achievedAt, done = t.done,
                    current = t.current, total = t.total, start = t.start, unit = t.unit,
                    items = t.items.map { ChecklistItemModel(it.id, it.text, it.done) },
                )
            },
            resources = g.resources.map { ResourceItem(it.id, it.type, it.title) },
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
                            id = Optional.present(it.id),
                            text = it.text,
                            done = Optional.present(it.done),
                        )
                    },
                ),
            ),
        )

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
