package com.spiramindscape.backend.ai.chat.transcript;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AiChatTranscriptRepository extends JpaRepository<AiChatTranscript, Long> {

    /** The user's transcript for a specific goal. */
    Optional<AiChatTranscript> findByAppUserIdAndGoalId(Long appUserId, Long goalId);

    /** The user's global (all-goals) transcript. */
    Optional<AiChatTranscript> findByAppUserIdAndGoalIdIsNull(Long appUserId);

    /**
     * Just the timestamp for a scope — the change-signature behind
     * {@code GET /api/ai/chat/transcript/revision}. Selecting {@code updated_at} alone keeps the
     * {@code content} TEXT blob out of the query, so an open-but-unchanged chat panel stops
     * pulling the whole conversation from the database on every poll.
     */
    @Query("SELECT t.updatedAt FROM AiChatTranscript t "
            + "WHERE t.appUserId = :appUserId AND t.goalId = :goalId")
    Optional<Instant> findUpdatedAtByAppUserIdAndGoalId(
            @Param("appUserId") Long appUserId, @Param("goalId") Long goalId);

    /** The same timestamp-only read for the global (all-goals) transcript. */
    @Query("SELECT t.updatedAt FROM AiChatTranscript t "
            + "WHERE t.appUserId = :appUserId AND t.goalId IS NULL")
    Optional<Instant> findUpdatedAtByAppUserIdAndGoalIdIsNull(@Param("appUserId") Long appUserId);
}
