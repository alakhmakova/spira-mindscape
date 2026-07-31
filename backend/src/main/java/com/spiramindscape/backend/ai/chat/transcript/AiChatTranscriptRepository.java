package com.spiramindscape.backend.ai.chat.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChatTranscriptRepository extends JpaRepository<AiChatTranscript, Long> {

    /** The user's transcript for a specific goal. */
    Optional<AiChatTranscript> findByAppUserIdAndGoalId(Long appUserId, Long goalId);

    /** The user's global (all-goals) transcript. */
    Optional<AiChatTranscript> findByAppUserIdAndGoalIdIsNull(Long appUserId);
}
