package com.spiramindscape.backend.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **The chat prompt only carries what can apply here** (2026-08-30).
 *
 * <p>It was one 21,130-character block sent on every model call, and the agentic loop makes
 * several calls per message — each one re-sending the lot. Over a third of it could not apply
 * to where the user was: the All-Goals rules travelled inside every goal-page conversation,
 * and the goal-page rules travelled on the overview, where {@code read_resource} is not even
 * offered as a tool.
 *
 * <p>The bill arrived as {@code Rate limit exceeded} — Mistral's per-minute allowance, spent
 * by one turn, five times in an hour on the owner's phone. So the size of this prompt is a
 * product property now, not an implementation detail, and these tests hold the line: a future
 * edit that drops a block back into the shared core has to fail here rather than quietly cost
 * every conversation another thousand tokens per call.
 */
class ChatPromptScopeTest {

    private static final long A_GOAL = 42L;

    /** What the single block used to cost, on every call, everywhere. */
    private static final int THE_OLD_ONE_BLOCK_PROMPT = 21_130;

    @Test
    @DisplayName("The All-Goals prompt leaves out everything that needs an open goal")
    void theOverviewDoesNotCarryTheGoalPageRules() {
        String prompt = AiChatService.chatPrompt(null, false);

        // read_resource is not even offered as a tool without a goal, so instructions on how
        // to use it were pure cost — and an instruction to call a tool that is absent is worse
        // than absent guidance.
        assertThat(prompt).doesNotContain("read_resource");
        assertThat(prompt).doesNotContain("EDITING A NOTE");
        assertThat(prompt).doesNotContain("delete_checklist_item");

        assertThat(prompt).contains("ALL-GOALS PAGE");
        assertThat(prompt).contains("kind='edit_goal'");
    }

    @Test
    @DisplayName("The goal-page prompt leaves out the All-Goals rules")
    void theGoalPageDoesNotCarryTheOverviewRules() {
        String prompt = AiChatService.chatPrompt(A_GOAL, false);

        // "From the All-Goals overview you can ONLY change name, confidence and deadline" is
        // read by a model that is, right now, inside a goal where all of it is editable.
        assertThat(prompt).doesNotContain("STRICT LIMIT");
        assertThat(prompt).doesNotContain("goals overview");

        assertThat(prompt).contains("A GOAL IS OPEN");
        assertThat(prompt).contains("read_resource");
    }

    @Test
    @DisplayName("Web search is described only when there is a key to search with")
    void searchIsOnlyPromisedWhenItExists() {
        assertThat(AiChatService.chatPrompt(A_GOAL, false))
                .doesNotContain("`web_search`")
                .contains("You cannot SEARCH the web");

        assertThat(AiChatService.chatPrompt(A_GOAL, true)).contains("`web_search`");
    }

    @Test
    @DisplayName("Nobody is asked which language to keep their goals in")
    void theLanguageQuestionIsGone() {
        // The owner's words: "they ask this out of nowhere — simpler to drop it". The chat
        // already answers in the language it is written to; the goal data now follows suit
        // without a question that interrupts the first useful exchange of a conversation.
        for (String prompt : new String[] {
                AiChatService.chatPrompt(null, false), AiChatService.chatPrompt(A_GOAL, true) }) {
            assertThat(prompt).doesNotContain("ask once");
            assertThat(prompt).doesNotContain("their own language or English");
            assertThat(prompt).contains("Never ask which language to use");
        }
    }

    @Test
    @DisplayName("Neither branch costs what the single block did")
    void bothBranchesAreMateriallySmaller() {
        // Not a style preference: this is the per-call input of every turn, multiplied by
        // every iteration of the agentic loop.
        assertThat(AiChatService.chatPrompt(A_GOAL, true).length())
                .isLessThan(THE_OLD_ONE_BLOCK_PROMPT - 3_000);
        assertThat(AiChatService.chatPrompt(null, false).length())
                .isLessThan(THE_OLD_ONE_BLOCK_PROMPT - 7_000);
    }

    @Test
    @DisplayName("No leftover of Java string concatenation reaches the model")
    void theTextIsProseAndNotSourceCode() {
        // "'current' for progress already made, '+ 'unit'" shipped for weeks: a stray  '+
        // from an older concatenated string, sitting in the middle of the one instruction
        // that tells the model how to build a numeric target.
        for (String prompt : new String[] {
                AiChatService.chatPrompt(null, true), AiChatService.chatPrompt(A_GOAL, true) }) {
            assertThat(prompt).doesNotContain("'+ '");
            assertThat(prompt).doesNotContain("\" +");
        }
    }
}
