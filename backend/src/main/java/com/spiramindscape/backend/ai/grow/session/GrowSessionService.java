package com.spiramindscape.backend.ai.grow.session;

import com.spiramindscape.backend.ai.grow.session.dto.GrowSessionDto;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps a running GROW session on the server so it belongs to the user rather than to the device
 * it was started on (owner, 2026-09-08: a session begun on the phone had to be started over on the
 * laptop). The chat transcript has synced since BUG-018; the session itself never did.
 *
 * <p>Every operation is scoped to the authenticated user AND checks the goal is theirs, so one
 * user can neither read nor overwrite another's session. Last write wins, like the transcript:
 * whichever device last took a turn defines where the session is.
 */
@Service
@Transactional
public class GrowSessionService {

    private final GrowSessionRepository repo;
    private final GoalRepository goalRepository;
    private final CurrentUserProvider currentUserProvider;

    public GrowSessionService(
            GrowSessionRepository repo,
            GoalRepository goalRepository,
            CurrentUserProvider currentUserProvider) {
        this.repo = repo;
        this.goalRepository = goalRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** The current user's live session for a goal, or an empty one when there is none. */
    @Transactional(readOnly = true)
    public GrowSessionDto get(Long goalId) {
        Long userId = ownedGoalId(goalId);
        return repo.findByAppUserIdAndGoalId(userId, goalId)
                .map(GrowSessionDto::from)
                .orElseGet(() -> GrowSessionDto.empty(goalId));
    }

    /** Upsert the current user's live session for a goal. */
    public GrowSessionDto save(Long goalId, String content) {
        Long userId = ownedGoalId(goalId);
        GrowSession session = repo.findByAppUserIdAndGoalId(userId, goalId)
                .orElseGet(() -> {
                    GrowSession created = new GrowSession();
                    created.setAppUserId(userId);
                    created.setGoalId(goalId);
                    return created;
                });
        session.setContent(content == null || content.isBlank() ? "{}" : content);
        return GrowSessionDto.from(repo.save(session));
    }

    /** The session is over — nothing about it outlives this call. */
    public void clear(Long goalId) {
        repo.deleteByAppUserIdAndGoalId(ownedGoalId(goalId), goalId);
    }

    /**
     * The goal must exist and be the caller's. Checked on every path: `goalId` is user-supplied,
     * and without this a session could be written against — or read from — somebody else's goal.
     */
    private Long ownedGoalId(Long goalId) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        if (goalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A session needs a goal");
        }
        goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal " + goalId + " not found"));
        return userId;
    }
}
