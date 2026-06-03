package com.spiramindscape.backend.ai.proposal;

import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Manages AI proposal lifecycle: creation, listing, approval, and rejection.
 *
 * <p>Applying an approved proposal (i.e., mutating goal data) is delegated
 * to the goal service layer — this service only manages the proposal record
 * itself. The frontend is responsible for calling the appropriate GraphQL
 * mutation when the user approves.
 */
@Service
@Transactional
public class AiProposalService {

    private final AiProposalRepository repo;
    private final CurrentUserProvider currentUserProvider;

    public AiProposalService(AiProposalRepository repo, CurrentUserProvider currentUserProvider) {
        this.repo = repo;
        this.currentUserProvider = currentUserProvider;
    }

    /** Create a new pending proposal. Returns the persisted DTO. */
    public ProposalDto create(Long goalId, String type, String payload) {
        AiProposal proposal = new AiProposal();
        proposal.setAppUserId(currentUserId());
        proposal.setGoalId(goalId);
        proposal.setType(type);
        proposal.setPayload(payload);
        proposal.setStatus(AiProposal.Status.PENDING);
        return ProposalDto.from(repo.save(proposal));
    }

    /** List all pending proposals for the current user. */
    public List<ProposalDto> listPending() {
        return repo.findByAppUserIdAndStatusOrderByCreatedAtDesc(
                        currentUserId(), AiProposal.Status.PENDING)
                .stream()
                .map(ProposalDto::from)
                .toList();
    }

    /** List all pending proposals for a specific goal. */
    public List<ProposalDto> listPendingForGoal(Long goalId) {
        return repo.findByGoalIdAndStatusOrderByCreatedAtDesc(goalId, AiProposal.Status.PENDING)
                .stream()
                .map(ProposalDto::from)
                .toList();
    }

    /** Approve a proposal. Returns the updated DTO. */
    public ProposalDto approve(Long proposalId) {
        return updateStatus(proposalId, AiProposal.Status.APPROVED);
    }

    /** Reject a proposal. Returns the updated DTO. */
    public ProposalDto reject(Long proposalId) {
        return updateStatus(proposalId, AiProposal.Status.REJECTED);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** The id of the authenticated user, used to scope every proposal operation. */
    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    private ProposalDto updateStatus(Long proposalId, AiProposal.Status newStatus) {
        AiProposal proposal = repo.findByIdAndAppUserId(proposalId, currentUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != AiProposal.Status.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Proposal is already " + proposal.getStatus());
        }

        proposal.setStatus(newStatus);
        return ProposalDto.from(repo.save(proposal));
    }
}
