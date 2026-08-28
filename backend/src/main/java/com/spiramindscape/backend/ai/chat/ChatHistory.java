package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * How much of a conversation is re-sent to the model on each turn (BUG-056).
 *
 * <h2>Why there is a limit at all</h2>
 *
 * <p>There was none. Both clients replayed their whole stored transcript on every message
 * and the server took whatever arrived — {@code ChatRequest.history} had no {@code @Size},
 * its entries had no length cap, and {@code buildMessages} copied the lot into the prompt.
 * So a conversation cost more with every turn it survived: the request grew, the prompt
 * grew, and the time to the first token grew with it. Cloud Run's request log for a single
 * afternoon (27 Aug 2026) shows the same chat re-posting 270 KB, then 276 KB, then 276 KB
 * again — until the user started a new chat and the next request was 3.7 KB.
 *
 * <p>This is not the cause of the three-minute stalls (two of those carried 4 KB), but it
 * is why an old chat felt slower than a fresh one, and it is an unbounded client-controlled
 * input on a paid API.
 *
 * <h2>The rules</h2>
 *
 * <p>Newest first, because the recent turns are the ones the answer depends on. Two limits,
 * not one: {@link #MAX_ENTRIES} bounds the message count the provider has to validate and
 * bill per-message overhead on, and {@link #MAX_CHARS} bounds what actually costs tokens —
 * a single pasted document can blow the budget in one entry while the count says two.
 *
 * <p><b>Trimming may not leave the history starting on an assistant turn.</b> Anthropic
 * rejects a conversation whose first message is not the user's, and a reply with no
 * question above it reads as though the model spoke first. So leading assistant entries go
 * after the trim, not before.
 *
 * <p>That strip has a sharp edge, and not a rare one. The newest history entry is
 * normally the assistant's last reply, so if the user turn before it does not fit in what
 * is left of the budget, the window holds one assistant entry — and stripping it returns
 * <b>nothing</b>. A single long reply near the budget would silently cost the model the
 * whole conversation. When that happens the fall-back is the newest <b>user</b> turn on
 * its own, truncated: one real thing the user said beats no history at all, and it can
 * lead.
 *
 * <p>The clients apply the same two numbers before sending ({@code buildHistory} in
 * {@code proposal-logic.ts} and in {@code Proposal.kt}) so the request stays small on the
 * wire. This class is the backstop: the numbers being in three places is the cost of the
 * limit holding even when the caller is an old build, another client, or a script.
 */
public final class ChatHistory {

    /** The most turns replayed to the model. Merged same-role turns count as one. */
    public static final int MAX_ENTRIES = 60;

    /**
     * The most characters of history replayed, across all kept entries. Roughly 7–8k tokens
     * — a long conversation stays whole, and a runaway one stops growing. Chosen against
     * what production actually sends: an ordinary turn is 2–8 KB, and the chats that had to
     * be abandoned were re-posting 270 KB.
     */
    public static final int MAX_CHARS = 30_000;

    private ChatHistory() {}

    /**
     * The tail of {@code history} that fits the limits, oldest-first, starting on a user
     * turn. An empty or null history returns empty.
     */
    public static List<ChatRequest.MessageEntry> trim(List<ChatRequest.MessageEntry> history) {
        if (history == null || history.isEmpty()) return List.of();

        List<ChatRequest.MessageEntry> kept = new ArrayList<>();
        int chars = 0;
        for (int i = history.size() - 1; i >= 0 && kept.size() < MAX_ENTRIES; i--) {
            ChatRequest.MessageEntry entry = history.get(i);
            if (entry == null || entry.content() == null) continue;
            int cost = entry.content().length();
            // The newest entry is kept even if it alone exceeds the budget — truncated
            // rather than dropped, because dropping it would silently answer a question
            // the model was never shown.
            if (kept.isEmpty() && cost > MAX_CHARS) {
                kept.add(new ChatRequest.MessageEntry(
                        entry.role(), tail(entry.content(), MAX_CHARS)));
                chars = MAX_CHARS;
                continue;
            }
            if (chars + cost > MAX_CHARS) break;
            chars += cost;
            kept.add(entry);
        }

        // Built newest-first above; the model reads it oldest-first.
        java.util.Collections.reverse(kept);

        // Drop any assistant turns now stranded at the front (see the class note).
        int start = 0;
        while (start < kept.size() && !"user".equalsIgnoreCase(kept.get(start).role())) start++;
        if (start < kept.size()) {
            return start == 0 ? kept : List.copyOf(kept.subList(start, kept.size()));
        }
        return newestUserTurn(history);
    }

    /**
     * The most recent thing the user said, truncated to the budget - the fall-back for a
     * window that held only assistant turns. Empty when the conversation contains no user turn
     * at all, which cannot happen in a real transcript.
     */
    private static List<ChatRequest.MessageEntry> newestUserTurn(
            List<ChatRequest.MessageEntry> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatRequest.MessageEntry entry = history.get(i);
            if (entry == null || entry.content() == null) continue;
            if ("user".equalsIgnoreCase(entry.role())) {
                return List.of(new ChatRequest.MessageEntry(
                        entry.role(), tail(entry.content(), MAX_CHARS)));
            }
        }
        return List.of();
    }

    /** The last {@code max} characters — the end of a long message is the part in play. */
    private static String tail(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }
}
