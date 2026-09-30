package com.spiramindscape.backend.ai.cv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every lookup carries the owner. There is deliberately no {@code findById} in use:
 * an application id arrives from the client, and a query that does not name the user
 * is one refactor away from reading somebody else's job application.
 */
public interface CvApplicationRepository extends JpaRepository<CvApplication, Long> {

    List<CvApplication> findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(Long appUserId, Long goalId);

    Optional<CvApplication> findByIdAndAppUserId(Long id, Long appUserId);

    /**
     * The most recently touched application for a goal — what "continue where I left
     * off" resolves to when the client names a goal but not an application.
     */
    Optional<CvApplication> findFirstByAppUserIdAndGoalIdOrderByUpdatedAtDesc(Long appUserId, Long goalId);

    long countByAppUserIdAndGoalId(Long appUserId, Long goalId);
}
