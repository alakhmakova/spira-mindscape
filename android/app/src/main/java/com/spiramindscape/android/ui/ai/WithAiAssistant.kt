package com.spiramindscape.android.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.goals.GoalDetail

/**
 * Wraps a screen with the AI assistant: the panel is pulled **up** over the content, either from
 * the assistant icon or by swiping up on whatever surface the screen attaches the gesture
 * modifier to (the goal workspace uses its footer) — see [AiChatHost].
 *
 * [goalId] scopes the conversation: a goal id gives the assistant that goal's reality, options,
 * targets and resources; null is the all-goals chat. Each scope keeps its own transcript, so
 * switching goals never mixes two conversations.
 */
@Composable
fun WithAiAssistant(
    goalId: String?,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onApplyProposal: (Proposal, Set<String>) -> String?,
    /** The open goal, so the empty chat can offer prompts drawn from its actual state. */
    goal: GoalDetail? = null,
    content: @Composable (swipeUpGesture: Modifier) -> Unit,
) {
    val viewModel: AiChatViewModel = viewModel(
        // Key by scope: the goal chat and the all-goals chat are different conversations, and
        // without the key one would inherit the other's messages when the route changes.
        key = "ai-chat-${goalId ?: "global"}",
        factory = AiChatViewModel.factory(goalId),
    )

    // Another device may have moved the conversation on while this screen was away.
    LifecycleResumeEffect(Unit) {
        viewModel.syncTranscript()
        onPauseOrDispose { }
    }

    // Leaving the screen must not leave an answer streaming into a panel nobody can see.
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelStream() }
    }

    AiChatHost(
        open = open,
        onOpenChange = onOpenChange,
        panel = { onClose ->
            AiChatScreen(
                viewModel = viewModel,
                onClose = onClose,
                goal = goal,
                onApplyProposal = onApplyProposal,
            )
        },
        content = content,
    )
}
