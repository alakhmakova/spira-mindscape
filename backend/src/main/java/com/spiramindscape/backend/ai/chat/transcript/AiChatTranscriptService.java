package com.spiramindscape.backend.ai.chat.transcript;

import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptDto;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Stores and retrieves a user's regular-chat transcript per scope so the
 * conversation syncs across devices (BUG-018). Every operation is scoped to the
 * authenticated user, so one user can never read or overwrite another's chat.
 * Last write wins: whichever device last sent a turn defines the shared history.
 */
@Service
@Transactional
public class AiChatTranscriptService {

    private final AiChatTranscriptRepository repo;
    private final CurrentUserProvider currentUserProvider;

    public AiChatTranscriptService(
            AiChatTranscriptRepository repo, CurrentUserProvider currentUserProvider) {
        this.repo = repo;
        this.currentUserProvider = currentUserProvider;
    }

    /** The current user's transcript for a scope (empty when nothing is stored). */
    @Transactional
    public TranscriptDto get(Long goalId) {
        return find(currentUserId(), goalId)
                .map(TranscriptDto::from)
                .orElseGet(() -> TranscriptDto.empty(goalId));
    }

    /** Upsert the current user's transcript for a scope. */
    public TranscriptDto save(Long goalId, String content) {
        Long userId = currentUserId();
        AiChatTranscript transcript = find(userId, goalId).orElseGet(() -> {
            AiChatTranscript created = new AiChatTranscript();
            created.setAppUserId(userId);
            created.setGoalId(goalId);
            return created;
        });
        transcript.setContent(content == null || content.isBlank() ? "[]" : content);
        return TranscriptDto.from(repo.save(transcript));
    }

    /** Delete the current user's transcript for a scope ("New chat"). */
    public void clear(Long goalId) {
        find(currentUserId(), goalId).ifPresent(repo::delete);
    }

    private Optional<AiChatTranscript> find(Long userId, Long goalId) {
        return goalId == null
                ? repo.findByAppUserIdAndGoalIdIsNull(userId)
                : repo.findByAppUserIdAndGoalId(userId, goalId);
    }

    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }
}
