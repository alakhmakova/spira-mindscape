package com.spiramindscape.backend.ai.grow;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Session memory for GROW coaching: what the user chose to keep when a
 * session ended ("Save memory"). Stored on the goal ({@code goal.ai_memory})
 * and read back into the system prompt of every later GROW session, so the
 * coach continues the thread instead of asking the same questions again.
 */
@Service
@Transactional
public class GoalMemoryService {

    /** Newest entries win: the stored memory is trimmed to this many chars. */
    static final int MAX_MEMORY_CHARS = 6_000;

    /** One entry can't flood the whole memory. */
    static final int MAX_ENTRY_CHARS = 2_000;

    private final GoalRepository goalRepository;
    private final CurrentUserProvider currentUserProvider;

    public GoalMemoryService(GoalRepository goalRepository, CurrentUserProvider currentUserProvider) {
        this.goalRepository = goalRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Appends a dated session summary to the goal's memory (oldest dropped past the cap). */
    public void append(Long goalId, String summary) {
        if (summary == null || summary.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory summary must not be blank");
        }
        Goal goal = ownedGoal(goalId);
        String entry = "[" + LocalDate.now() + "]\n" + truncate(summary.strip(), MAX_ENTRY_CHARS);
        String existing = goal.getAiMemory();
        String combined = (existing == null || existing.isBlank())
                ? entry
                : existing + "\n\n" + entry;
        // Trim from the FRONT so the most recent sessions survive.
        if (combined.length() > MAX_MEMORY_CHARS) {
            combined = "…" + combined.substring(combined.length() - MAX_MEMORY_CHARS);
        }
        goal.setAiMemory(combined);
        goalRepository.save(goal);
    }

    /**
     * The goal's memory as a prompt block, or an empty string when there is
     * none (also for null goalId / someone else's goal — the chat layer treats
     * memory as optional, never an error).
     */
    public String memoryBlock(Long goalId) {
        if (goalId == null) return "";
        return goalRepository.findByIdAndUserId(goalId, currentUserId())
                .map(Goal::getAiMemory)
                .filter(m -> m != null && !m.isBlank())
                .map(m -> """
                        PREVIOUS GROW SESSIONS — the records the user chose to keep, newest last.

                        OPEN THIS SESSION ON IT. Your first turn still names how long the session
                        is and asks what they want from it, but it does not start from nothing:

                        • If the newest record names a COMMITMENT — something they said they
                          would do — say what it was, in their own words, and ask whether it
                          happened. Ask nothing else in that turn. Then, once they have answered,
                          ask whether they want to carry on with that thread today or work on
                          something else. Their answer is the outcome you contract for.
                        • If it names no commitment, say briefly what the last session was about
                          and ask whether they want to continue with it or bring something else
                          today.

                        Do it as a coach, not as a clerk: one short sentence of continuity, not a
                        recital of the record. Never scold, however the answer comes back — "no,
                        I didn't" is information about what is in the way, and it is very often
                        the most useful thing the session will get.

                        This memory is CONTEXT for coaching. It is never material for the record
                        you write at the end: that record is of today's conversation only.

                        %s""".formatted(m))
                .orElse("");
    }

    private Goal ownedGoal(Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, currentUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Goal " + goalId + " not found"));
    }

    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
