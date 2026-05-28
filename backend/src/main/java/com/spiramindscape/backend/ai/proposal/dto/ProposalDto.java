package com.spiramindscape.backend.ai.proposal.dto;

import com.spiramindscape.backend.ai.proposal.AiProposal;

import java.time.Instant;

public record ProposalDto(
        Long id,
        Long goalId,
        String type,
        String payload,
        AiProposal.Status status,
        Instant createdAt
) {
    public static ProposalDto from(AiProposal p) {
        return new ProposalDto(
                p.getId(),
                p.getGoalId(),
                p.getType(),
                p.getPayload(),
                p.getStatus(),
                p.getCreatedAt()
        );
    }
}
