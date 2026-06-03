package com.spiramindscape.backend.ai.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProposalRepository extends JpaRepository<AiProposal, Long> {

    List<AiProposal> findByAppUserIdAndStatusOrderByCreatedAtDesc(
            Long appUserId, AiProposal.Status status);

    List<AiProposal> findByGoalIdAndStatusOrderByCreatedAtDesc(
            Long goalId, AiProposal.Status status);

    Optional<AiProposal> findByIdAndAppUserId(Long id, Long appUserId);
}
