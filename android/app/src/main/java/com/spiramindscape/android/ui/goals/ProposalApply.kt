package com.spiramindscape.android.ui.goals

import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.ai.ProposalKind
import com.spiramindscape.android.data.ai.TargetShape
import com.spiramindscape.android.data.ai.applyExcludedAspects
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TargetItem

/**
 * Applies an accepted AI proposal to the open goal, through the same actions the user's own taps
 * go through — so an AI edit and a hand edit are indistinguishable downstream (optimistic update,
 * server write, refetch on failure).
 *
 * Returns null when the change was applied, or a sentence explaining why it wasn't. The assistant
 * proposes things that only make sense elsewhere (creating a goal belongs to the all-goals chat),
 * and saying so is better than a card that silently does nothing.
 */
fun applyProposalToGoal(
    proposal: Proposal,
    excludedAspects: Set<String>,
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
): String? {
    val p = applyExcludedAspects(proposal, excludedAspects)
    val value = p.rawValue ?: p.title

    when (p.kind) {
        // ── create ──────────────────────────────────────────────────────────
        ProposalKind.TARGET, ProposalKind.TASK -> {
            val type = when (p.targetType) {
                TargetShape.NUMERIC -> "numeric"
                TargetShape.CHECKLIST -> "checklist"
                else -> "binary"
            }
            actions.onAddTarget(
                p.title,
                type,
                p.deadline,
                p.current?.toDoubleOrNull(),
                p.total?.toDoubleOrNull(),
                p.unit,
                p.items?.map { it.text }.orEmpty(),
            )
        }

        ProposalKind.OPTION -> actions.onAddOption(p.title)
        ProposalKind.OBSTACLE -> actions.onAddReality("obstacles", p.title)
        ProposalKind.ACTION -> actions.onAddReality("actions", p.title)

        ProposalKind.NOTE -> actions.onAddResource(
            "note", p.title, p.body, null, null, null, null, null, null, null,
        )
        ProposalKind.LINK -> actions.onAddResource(
            "link", p.patch?.get("title") ?: p.title, null, p.patch?.get("url") ?: value,
            null, null, null, null, null, null,
        )
        ProposalKind.EMAIL -> actions.onAddResource(
            "email", null, null, null,
            p.patch?.get("name"), p.patch?.get("email"), p.patch?.get("role"), p.patch?.get("phone"),
            null, null,
        )

        // ── goal fields ─────────────────────────────────────────────────────
        ProposalKind.EDIT ->
            if (p.field == "description") actions.onSetGoalDescription(p.title)
            else actions.onSetGoalTitle(p.title)

        ProposalKind.CONFIDENCE ->
            value.toIntOrNull()?.coerceIn(1, 10)?.let(actions.onSetConfidence)
                ?: return "That confidence value didn't make sense."

        ProposalKind.DEADLINE -> actions.onSetDeadline(value)

        // ── edit existing ───────────────────────────────────────────────────
        ProposalKind.EDIT_TARGET -> {
            val id = p.itemId ?: return "I couldn't tell which target that was."
            actions.onSetTargetTitle(id, p.title)
            p.deadline?.let { actions.onSetTargetDeadline(id, it) }
        }
        ProposalKind.EDIT_OPTION ->
            actions.onSetOptionText(p.itemId ?: return "I couldn't tell which option that was.", p.title)
        ProposalKind.EDIT_OBSTACLE ->
            actions.onUpdateReality("obstacles", p.itemId ?: return MISSING_ITEM, p.title)
        ProposalKind.EDIT_ACTION ->
            actions.onUpdateReality("actions", p.itemId ?: return MISSING_ITEM, p.title)

        ProposalKind.EDIT_NOTE, ProposalKind.EDIT_LINK, ProposalKind.EDIT_EMAIL -> {
            val id = p.itemId ?: return "I couldn't tell which resource that was."
            val existing = goal.resources.firstOrNull { it.id == id }
                ?: return "That resource isn't on this goal any more."
            // The update endpoint takes the whole resource, so unchanged fields echo their
            // current value rather than being cleared.
            actions.onUpdateResource(
                id,
                p.patch?.get("title") ?: if (p.kind == ProposalKind.EDIT_NOTE) p.title else existing.title,
                if (p.kind == ProposalKind.EDIT_NOTE) p.body else existing.body,
                p.patch?.get("url") ?: existing.url,
                p.patch?.get("name") ?: existing.name,
                p.patch?.get("email") ?: existing.email,
                p.patch?.get("role") ?: existing.role,
                p.patch?.get("phone") ?: existing.phone,
                existing.mime,
                existing.dataUrl,
            )
        }

        // ── state changes ───────────────────────────────────────────────────
        ProposalKind.COMPLETE_TARGET ->
            actions.onSetTargetDone(p.itemId ?: return MISSING_TARGET, p.done ?: true)

        ProposalKind.TARGET_PROGRESS -> {
            val id = p.itemId ?: return MISSING_TARGET
            val next = value.toDoubleOrNull() ?: return "That progress value didn't make sense."
            actions.onSetNumeric(id, next)
        }

        ProposalKind.SELECT_OPTION ->
            actions.onSelectOption(p.itemId ?: return "I couldn't tell which option that was.")

        ProposalKind.ADD_CHECKLIST_ITEM ->
            actions.onAddChecklistTask(p.itemId ?: return MISSING_TARGET, p.title)

        ProposalKind.CHECKLIST_ITEM -> {
            val itemId = p.itemId ?: return MISSING_ITEM
            val target = goal.checklistTargetHolding(itemId) ?: return MISSING_ITEM
            // A tick and a rename arrive through the same kind; the payload says which.
            if (p.rawValue.isNullOrBlank()) {
                val item = target.items.firstOrNull { it.id == itemId } ?: return MISSING_ITEM
                if (item.done != (p.done ?: !item.done)) actions.onToggleChecklistItem(target.id, itemId)
            } else {
                actions.onUpdateChecklistTask(target.id, itemId, p.title)
            }
            p.deadline?.let { actions.onSetChecklistTaskDeadline(target.id, itemId, it) }
        }

        // ── delete ──────────────────────────────────────────────────────────
        ProposalKind.DELETE_TARGET -> actions.onDeleteTarget(p.itemId ?: return MISSING_TARGET)
        ProposalKind.DELETE_OPTION -> actions.onRemoveOption(p.itemId ?: return MISSING_ITEM)
        ProposalKind.DELETE_OBSTACLE -> actions.onRemoveReality("obstacles", p.itemId ?: return MISSING_ITEM)
        ProposalKind.DELETE_ACTION -> actions.onRemoveReality("actions", p.itemId ?: return MISSING_ITEM)
        ProposalKind.DELETE_CHECKLIST_ITEM -> {
            val itemId = p.itemId ?: return MISSING_ITEM
            val target = goal.checklistTargetHolding(itemId) ?: return MISSING_ITEM
            actions.onRemoveChecklistTask(target.id, itemId)
        }

        // ── belongs to the all-goals chat, not to one goal ──────────────────
        ProposalKind.NEW_GOAL ->
            return "Open the assistant from All goals to create a new goal."
        ProposalKind.EDIT_GOAL, ProposalKind.OPEN_GOAL, ProposalKind.DELETE_GOAL ->
            return "That change belongs to the assistant on All goals."

        ProposalKind.UNKNOWN ->
            return "This version of the app doesn't know how to apply that yet."
    }
    return null
}

private const val MISSING_TARGET = "I couldn't tell which target that was."
private const val MISSING_ITEM = "I couldn't find that item on this goal any more."

/** The checklist target that owns a task id — the tool call only names the task. */
private fun GoalDetail.checklistTargetHolding(itemId: String): TargetItem.Checklist? =
    targets.filterIsInstance<TargetItem.Checklist>().firstOrNull { target ->
        target.items.any { it.id == itemId }
    }
