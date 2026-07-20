package com.spiramindscape.android.data.goals

import com.apollographql.apollo.api.Optional
import com.spiramindscape.android.graphql.type.CreateResourceInput
import com.spiramindscape.android.graphql.type.CreateTargetInput
import com.spiramindscape.android.graphql.type.UpdateResourceInput

/**
 * Base test double for [GoalsRepository]: every method throws until a subclass overrides the
 * ones a given test needs. Keeps individual test fakes small and immune to interface growth.
 */
open class FakeGoalsRepository : GoalsRepository {
    override suspend fun getGoals(): List<GoalSummary> = throw NotImplementedError()
    override suspend fun getGoal(id: String): GoalDetail = throw NotImplementedError()
    override suspend fun setTargetDone(targetId: String, done: Boolean): TargetItem = throw NotImplementedError()
    override suspend fun setTargetCurrent(targetId: String, current: Double): TargetItem = throw NotImplementedError()
    override suspend fun setChecklistItems(targetId: String, items: List<ChecklistItemModel>): TargetItem =
        throw NotImplementedError()

    override suspend fun createGoal(title: String, description: String?, confidence: Int, deadline: String?): String =
        throw NotImplementedError()
    override suspend fun updateGoal(
        id: String,
        title: String?,
        description: String?,
        confidence: Int?,
        deadline: Optional<String?>,
    ) { throw NotImplementedError() }
    override suspend fun deleteGoal(id: String) { throw NotImplementedError() }

    override suspend fun addReality(goalId: String, kind: String, text: String) { throw NotImplementedError() }
    override suspend fun updateReality(goalId: String, kind: String, itemId: String, text: String) { throw NotImplementedError() }
    override suspend fun removeReality(goalId: String, kind: String, itemId: String) { throw NotImplementedError() }

    override suspend fun addOption(goalId: String, text: String) { throw NotImplementedError() }
    override suspend fun setOptionText(goalId: String, optionId: String, text: String) { throw NotImplementedError() }
    override suspend fun selectOption(goalId: String, optionId: String) { throw NotImplementedError() }
    override suspend fun deselectOption(goalId: String, optionId: String) { throw NotImplementedError() }
    override suspend fun removeOption(goalId: String, optionId: String) { throw NotImplementedError() }
    override suspend fun reorderOptions(goalId: String, optionIds: List<String>) { throw NotImplementedError() }

    override suspend fun createTarget(goalId: String, input: CreateTargetInput) { throw NotImplementedError() }
    override suspend fun setTargetTitle(targetId: String, title: String): TargetItem = throw NotImplementedError()
    override suspend fun deleteTarget(targetId: String) { throw NotImplementedError() }

    override suspend fun createResource(goalId: String, input: CreateResourceInput) { throw NotImplementedError() }
    override suspend fun updateResource(id: String, input: UpdateResourceInput) { throw NotImplementedError() }
    override suspend fun removeResource(id: String) { throw NotImplementedError() }
}
