package com.spiramindscape.android.data.goals

import com.apollographql.apollo.ApolloClient
import com.spiramindscape.android.graphql.GetGoalsQuery

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

/** Raised when the goals query fails (network or GraphQL error). */
class GoalsException(message: String) : Exception(message)

/** Loads goals. An interface so the ViewModel can be unit-tested with a fake. */
interface GoalsRepository {
    suspend fun getGoals(): List<GoalSummary>
}

class ApolloGoalsRepository(private val apollo: ApolloClient) : GoalsRepository {
    override suspend fun getGoals(): List<GoalSummary> {
        val response = apollo.query(GetGoalsQuery()).execute()
        response.exception?.let { throw GoalsException(it.message ?: "Network error") }
        if (response.hasErrors()) {
            throw GoalsException(response.errors?.firstOrNull()?.message ?: "GraphQL error")
        }
        val goals = response.data?.goals ?: throw GoalsException("No data")
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
}
