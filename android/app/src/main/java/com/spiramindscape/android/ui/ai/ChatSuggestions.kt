package com.spiramindscape.android.ui.ai

import androidx.compose.ui.graphics.vector.ImageVector
import com.spiramindscape.android.ui.icons.SpiraIcons

/**
 * The opening prompts on an empty chat — the Kotlin port of the web `SUGGESTIONS_GLOBAL`.
 *
 * **Only on the dashboard.** There used to be a `buildGoalSuggestions` here too, picking three
 * prompts from what was true about the open goal (low confidence, a deadline closing in, obstacles
 * piling up). The owner had them removed on 2026-08-17: on the dashboard the starters answer "what
 * is this for?", but inside a goal the user already knows why they opened the assistant, and a
 * stack of guesses about their own goal was only in the way of typing. The web dropped its twin at
 * the same time (`AiPanel.tsx`).
 */
data class ChatSuggestion(val id: String, val icon: ImageVector, val text: String)

val GLOBAL_SUGGESTIONS: List<ChatSuggestion> = listOf(
    ChatSuggestion("new-goal", SpiraIcons.ChartColumn, "Help me create a new goal"),
    ChatSuggestion("edit", SpiraIcons.Pencil, "Change a goal's confidence or deadline"),
    ChatSuggestion("delete", SpiraIcons.Trash, "Delete a goal"),
)
