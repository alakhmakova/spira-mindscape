package com.spiramindscape.android.ui.ai

import androidx.compose.ui.graphics.vector.ImageVector
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The opening prompts on an empty chat — the Kotlin port of the web `SUGGESTIONS_GLOBAL` /
 * `buildGoalSuggestions`. They are picked from what is actually true about the goal right now
 * (low confidence, a deadline closing in, obstacles piling up), so the first tap is never a
 * generic "how can I help".
 */
data class ChatSuggestion(val id: String, val icon: ImageVector, val text: String)

val GLOBAL_SUGGESTIONS: List<ChatSuggestion> = listOf(
    ChatSuggestion("new-goal", SpiraIcons.NavTrophy, "Help me create a new goal"),
    ChatSuggestion("edit", SpiraIcons.Pencil, "Change a goal's confidence or deadline"),
    ChatSuggestion("delete", SpiraIcons.Trash, "Delete a goal"),
)

/** At most three, in the web's order of urgency. */
fun buildGoalSuggestions(goal: GoalDetail): List<ChatSuggestion> {
    val s = mutableListOf<ChatSuggestion>()

    val deadlineDays = goal.deadline?.let { iso ->
        runCatching {
            ChronoUnit.DAYS.between(
                LocalDate.now(),
                Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate(),
            )
        }.getOrNull()
    }
    val totalTargets = goal.targets.size
    val doneTargets = goal.targets.count { target ->
        (target is TargetItem.Binary && target.done) || target.achieved
    }

    if (goal.confidence <= 3) {
        s += ChatSuggestion(
            "confidence",
            SpiraIcons.Brain,
            "My confidence is low — help me identify what's blocking me",
        )
    }

    if (deadlineDays != null && deadlineDays > 0 && deadlineDays <= 14) {
        s += ChatSuggestion(
            "deadline",
            SpiraIcons.Clock,
            "$deadlineDays day${if (deadlineDays == 1L) "" else "s"} left — let's decide what still matters",
        )
    }

    if (goal.obstacles.isNotEmpty()) {
        val n = goal.obstacles.size
        s += ChatSuggestion(
            "obstacles",
            SpiraIcons.Shield,
            "I'm facing $n obstacle${if (n > 1) "s" else ""} — help me think through them",
        )
    }

    if (doneTargets in 1 until totalTargets) {
        s += ChatSuggestion(
            "progress",
            SpiraIcons.TrendingUp,
            "$doneTargets/$totalTargets targets done — what should I focus on next?",
        )
    }

    if (totalTargets == 0) {
        s += ChatSuggestion("targets", SpiraIcons.Target, "Help me define concrete targets for this goal")
    }

    if (goal.options.isEmpty() && s.size < 3) {
        s += ChatSuggestion("options", SpiraIcons.SwitchArrows, "What are my strategic options?")
    }

    if (goal.actions.isEmpty() && s.size < 3) {
        s += ChatSuggestion("action", SpiraIcons.Zap, "What's the best next action I can take today?")
    }

    if (s.isEmpty()) {
        s += ChatSuggestion("reflect", SpiraIcons.Brain, "Help me think through where I'm stuck")
        s += ChatSuggestion("reality", SpiraIcons.Leaf, "What's actually true about this goal right now?")
        s += ChatSuggestion("options", SpiraIcons.SwitchArrows, "What are my options from here?")
    }

    return s.take(3)
}
