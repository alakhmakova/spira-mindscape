package com.spiramindscape.android.ui.ai

/**
 * The request the All-Goals assistant could not carry out, handed to the goal it belongs in.
 *
 * <p>Only three fields of a goal can be changed from the overview — name, confidence, deadline —
 * so anything else ("add a description to Goal 1", "add a target") is answered with an
 * `open_goal` card. Opening the goal is only half an answer, though: arriving at an empty chat
 * and having to type the request a second time is the half that makes it feel like a refusal.
 * So the original words travel with the navigation and the goal's own assistant sends them on
 * arrival, and a card to make the change is waiting.
 *
 * <p>**Read-once**, so a second visit to the goal does not re-ask a question that was already
 * answered. In memory rather than on disk because, unlike the web — where `stashHandoff` uses
 * `localStorage` to survive a route change that may reload the page — this navigation never
 * leaves the process. A handoff dropped by process death is the right outcome: the user is no
 * longer in the middle of that thought.
 */
object AiHandoff {

    private val pending = mutableMapOf<String, String>()

    /** Remembers [instruction] for [goalId], replacing any earlier one. */
    fun stash(goalId: String, instruction: String) {
        pending[goalId] = instruction
    }

    /** The instruction waiting for [goalId], removed as it is read. */
    fun take(goalId: String): String? = pending.remove(goalId)
}
