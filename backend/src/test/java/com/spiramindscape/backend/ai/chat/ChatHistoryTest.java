package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest.MessageEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **How much conversation is re-sent on every turn** (BUG-056).
 *
 * <p>The answer used to be "all of it, whatever the client sends". Both clients replayed
 * their stored transcript in full and the server copied it straight into the prompt, so a
 * chat got slower the longer it lived and a request could be any size at all. This class
 * covers the server's backstop; the same two numbers are enforced before sending by
 * {@code proposal-logic.test.ts} on the web and {@code BuildHistoryTest} on Android.
 *
 * <p>The two rules that are easy to break later, and are therefore the point of the class:
 * a trimmed history must still <b>start on a user turn</b>, and the newest turn must
 * <b>survive</b> even when it alone is over budget.
 */
class ChatHistoryTest {

    private static MessageEntry user(String text) {
        return new MessageEntry("user", text);
    }

    private static MessageEntry assistant(String text) {
        return new MessageEntry("assistant", text);
    }

    private static String words(int chars) {
        return "x".repeat(chars);
    }

    /** Big enough that no second turn fits beside it, small enough to be kept whole. */
    private static final int MAX_CHARS_MINUS_A_LITTLE = ChatHistory.MAX_CHARS - 100;

    // ─── Nothing to do ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A short conversation is passed through unchanged")
    void shortHistoryIsUntouched() {
        List<MessageEntry> history = List.of(
                user("how do I start?"), assistant("what have you tried?"), user("nothing yet"));

        assertThat(ChatHistory.trim(history)).isEqualTo(history);
    }

    @Test
    @DisplayName("No history at all is empty, not a crash")
    void nullAndEmptyAreEmpty() {
        assertThat(ChatHistory.trim(null)).isEmpty();
        assertThat(ChatHistory.trim(List.of())).isEmpty();
    }

    @Test
    @DisplayName("An entry with no content is skipped rather than sent as a null")
    void nullContentIsSkipped() {
        List<MessageEntry> history = new ArrayList<>();
        history.add(user("first"));
        history.add(new MessageEntry("assistant", null));
        history.add(user("second"));

        assertThat(ChatHistory.trim(history))
                .extracting(MessageEntry::content)
                .containsExactly("first", "second");
    }

    // ─── The count limit ──────────────────────────────────────────────────────

    @Test
    @DisplayName("A long conversation keeps its most recent turns and drops the oldest")
    void keepsTheNewestTurns() {
        List<MessageEntry> history = new ArrayList<>();
        for (int i = 0; i < ChatHistory.MAX_ENTRIES * 2; i++) {
            history.add(i % 2 == 0 ? user("u" + i) : assistant("a" + i));
        }

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).hasSizeLessThanOrEqualTo(ChatHistory.MAX_ENTRIES);
        // The last turn — the one the answer actually depends on — is always there.
        assertThat(trimmed.get(trimmed.size() - 1))
                .isEqualTo(history.get(history.size() - 1));
        assertThat(trimmed).doesNotContain(history.get(0));
    }

    // ─── The character limit ──────────────────────────────────────────────────

    @Test
    @DisplayName("A conversation over the character budget is cut to fit it")
    void keepsWithinTheCharacterBudget() {
        // Ten turns of 5k characters: 50k against a 30k budget.
        List<MessageEntry> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(i % 2 == 0 ? user(words(5_000)) : assistant(words(5_000)));
        }

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        int total = trimmed.stream().mapToInt(e -> e.content().length()).sum();
        assertThat(total).isLessThanOrEqualTo(ChatHistory.MAX_CHARS);
        assertThat(trimmed).isNotEmpty();
    }

    @Test
    @DisplayName("The character budget bites before the count does when one turn is huge")
    void oneHugeTurnDoesNotSmuggleTheBudgetPast() {
        // Two entries — well inside MAX_ENTRIES — but one is a pasted document. Counting
        // messages alone would let this through, which is why there are two limits.
        List<MessageEntry> history = List.of(user(words(90_000)), assistant("noted"));

        int total = ChatHistory.trim(history).stream()
                .mapToInt(e -> e.content().length()).sum();

        assertThat(total).isLessThanOrEqualTo(ChatHistory.MAX_CHARS);
    }

    @Test
    @DisplayName("A newest turn that is over budget on its own is truncated, never dropped")
    void theNewestTurnAlwaysSurvives() {
        // Dropping it would have the model answer a question it was never shown — a far
        // worse failure than losing the beginning of a long paste.
        List<MessageEntry> history = List.of(user(words(ChatHistory.MAX_CHARS + 5_000)));

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0).content()).hasSize(ChatHistory.MAX_CHARS);
        assertThat(trimmed.get(0).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("A truncated turn keeps its END, where the actual question is")
    void truncationKeepsTheTail() {
        String pasted = words(ChatHistory.MAX_CHARS) + "so what should I do about it?";

        String kept = ChatHistory.trim(List.of(user(pasted))).get(0).content();

        assertThat(kept).endsWith("so what should I do about it?");
    }

    // ─── The role rule ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A trimmed history still starts on a user turn")
    void neverStartsOnAnAssistantTurn() {
        // Anthropic rejects a conversation whose first message is not the user's, so a trim
        // that happened to land on a reply would turn a long chat into a 400.
        List<MessageEntry> history = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            history.add(i % 2 == 0 ? user(words(400)) : assistant(words(400)));
        }

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).isNotEmpty();
        assertThat(trimmed.get(0).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("A conversation with no user turn at all drops to nothing")
    void anAssistantOnlyConversationIsEmptied() {
        // Better no history than a history that starts mid-reply: the goal context and the
        // current message still carry the turn, and the provider does not reject it.
        assertThat(ChatHistory.trim(List.of(assistant("a"), assistant("b")))).isEmpty();
    }

    @Test
    @DisplayName("A long reply that crowds out the user turn does not erase the whole history")
    void aLongReplyDoesNotCostTheWholeConversation() {
        // The sharp edge in the strip, and not a hypothetical one: the newest entry is normally
        // the assistant's reply, so a single long one leaves no budget for the user turn before
        // it, the window holds one assistant entry, and stripping it used to return NOTHING —
        // the model silently lost every bit of context.
        List<MessageEntry> history = List.of(
                user("we talked about this before"),
                assistant(words(MAX_CHARS_MINUS_A_LITTLE)),
                user("so what should I do?"),
                assistant(words(MAX_CHARS_MINUS_A_LITTLE)));

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).isNotEmpty();
        assertThat(trimmed.get(0).role()).isEqualTo("user");
        assertThat(trimmed.get(0).content()).contains("so what should I do?");
    }

    @Test
    @DisplayName("The fall-back turn is truncated to the budget like any other")
    void theFallbackTurnIsAlsoBounded() {
        List<MessageEntry> history = List.of(
                user(words(ChatHistory.MAX_CHARS * 2)),
                assistant(words(MAX_CHARS_MINUS_A_LITTLE)));

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0).role()).isEqualTo("user");
        assertThat(trimmed.get(0).content()).hasSize(ChatHistory.MAX_CHARS);
    }

    @Test
    @DisplayName("Trimming never reorders what it keeps")
    void orderIsPreserved() {
        List<MessageEntry> history = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            history.add(i % 2 == 0 ? user("u" + i) : assistant("a" + i));
        }

        List<MessageEntry> trimmed = ChatHistory.trim(history);

        assertThat(trimmed).isEqualTo(
                history.subList(history.size() - trimmed.size(), history.size()));
    }
}
