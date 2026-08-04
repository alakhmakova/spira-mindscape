package com.spiramindscape.android.ui.util

import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TargetItem

/**
 * Detaching a resource from every element that references it — the Kotlin port of the web
 * `src/lib/spira/resources.ts`. Deleting a resource must never leave a dangling `{{res:id}}`
 * behind: each reference becomes the resource's name, so the sentence still reads.
 */

/** One element whose text references a resource being deleted, with the text it should keep. */
sealed interface DetachPatch {
    data class Option(val optionId: String, val text: String) : DetachPatch

    /** [kind] is "actions" or "obstacles" — the two Reality lists. */
    data class Reality(val kind: String, val itemId: String, val text: String) : DetachPatch

    data class TargetTitle(val targetId: String, val title: String) : DetachPatch

    data class Checklist(val targetId: String, val items: List<ChecklistItemModel>) : DetachPatch
}

/** Keep a replacement inside the field's server limit; a very long title is cut, never rejected. */
private fun fit(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

/**
 * Plan the text changes needed to detach [resourceId] from every element of [goal] that
 * references it, replacing each token with [label] (the resource's title).
 *
 * Pure and client-side: a goal's whole graph is loaded, so no server sweep is needed.
 */
fun planResourceDetach(goal: GoalDetail, resourceId: String, label: String): List<DetachPatch> {
    val text = label.trim().ifEmpty { "resource" }
    val patches = mutableListOf<DetachPatch>()
    fun replace(value: String, max: Int) = fit(replaceResourceToken(value, resourceId, text), max)

    for (option in goal.options) {
        if (referencesResource(option.text, resourceId)) {
            patches += DetachPatch.Option(option.id, replace(option.text, FieldLimits.OPTION_TEXT))
        }
    }

    for ((kind, items) in listOf("actions" to goal.actions, "obstacles" to goal.obstacles)) {
        for (item in items) {
            if (referencesResource(item.text, resourceId)) {
                patches += DetachPatch.Reality(kind, item.id, replace(item.text, FieldLimits.REALITY_TEXT))
            }
        }
    }

    for (target in goal.targets) {
        if (referencesResource(target.title, resourceId)) {
            patches += DetachPatch.TargetTitle(target.id, replace(target.title, FieldLimits.TARGET_TITLE))
        }
        if (target is TargetItem.Checklist &&
            target.items.any { referencesResource(it.text, resourceId) }
        ) {
            patches += DetachPatch.Checklist(
                target.id,
                target.items.map {
                    if (referencesResource(it.text, resourceId)) {
                        it.copy(text = replace(it.text, FieldLimits.CHECKLIST_TEXT))
                    } else {
                        it
                    }
                },
            )
        }
    }

    return patches
}

/** How many elements of the goal have this resource attached (0 = safe to delete silently). */
fun countResourceAttachments(goal: GoalDetail, resourceId: String): Int =
    planResourceDetach(goal, resourceId, "x").size
