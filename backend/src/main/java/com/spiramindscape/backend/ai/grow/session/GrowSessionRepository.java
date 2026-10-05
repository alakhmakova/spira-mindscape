package com.spiramindscape.backend.ai.grow.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Every lookup is owner-scoped: a session is only ever reachable by the user who started it. */
public interface GrowSessionRepository extends JpaRepository<GrowSession, Long> {

    Optional<GrowSession> findByAppUserIdAndGoalId(Long appUserId, Long goalId);

    void deleteByAppUserIdAndGoalId(Long appUserId, Long goalId);
}
