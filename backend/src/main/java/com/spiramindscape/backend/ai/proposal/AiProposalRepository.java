package com.spiramindscape.backend.ai.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProposalRepository extends JpaRepository<AiProposal, Long> {

    List<AiProposal> findByAppUserIdAndStatusOrderByCreatedAtDesc(
            Long appUserId, AiProposal.Status status);

    /**
     * Pending proposals for one goal, <b>scoped to their owner</b>. The unscoped
     * {@code findByGoalIdAndStatus…} it replaced let {@code GET
     * /api/ai/proposals/goal/{id}} hand back another user's pending changes — their
     * goal titles, target names and note text — to anyone who guessed a goal id
     * (BUG-054).
     */
    List<AiProposal> findByAppUserIdAndGoalIdAndStatusOrderByCreatedAtDesc(
            Long appUserId, Long goalId, AiProposal.Status status);

    Optional<AiProposal> findByIdAndAppUserId(Long id, Long appUserId);
}
