package com.spiramindscape.backend.ai.proposal;

import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiProposalService}: proposals are scoped to the
 * authenticated user from {@link CurrentUserProvider}, and the lifecycle is
 * {@code PENDING → APPROVED | REJECTED} with conflict / not-found guards.
 */
@ExtendWith(MockitoExtension.class)
class AiProposalServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private AiProposalRepository repo;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private AiProposalService service;

    @BeforeEach
    void stubCurrentUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    @Test
    @DisplayName("create scopes the proposal to the authenticated user and starts PENDING")
    void createScopesToUser() {
        when(repo.save(any(AiProposal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(99L, "ADD_TARGET", "{\"kind\":\"target\"}");

        ArgumentCaptor<AiProposal> captor = ArgumentCaptor.forClass(AiProposal.class);
        verify(repo).save(captor.capture());
        AiProposal saved = captor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo(USER_ID);
        assertThat(saved.getGoalId()).isEqualTo(99L);
        assertThat(saved.getType()).isEqualTo("ADD_TARGET");
        assertThat(saved.getStatus()).isEqualTo(AiProposal.Status.PENDING);
    }

    @Test
    @DisplayName("listPending queries proposals scoped to the authenticated user")
    void listPendingScopedToUser() {
        when(repo.findByAppUserIdAndStatusOrderByCreatedAtDesc(USER_ID, AiProposal.Status.PENDING))
                .thenReturn(List.of());

        service.listPending();

        verify(repo).findByAppUserIdAndStatusOrderByCreatedAtDesc(USER_ID, AiProposal.Status.PENDING);
    }

    @Test
    @DisplayName("approve transitions a PENDING proposal to APPROVED")
    void approveTransitions() {
        AiProposal p = pending(5L);
        when(repo.findByIdAndAppUserId(5L, USER_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(AiProposal.class))).thenAnswer(inv -> inv.getArgument(0));

        ProposalDto dto = service.approve(5L);

        assertThat(dto.status()).isEqualTo(AiProposal.Status.APPROVED);
    }

    @Test
    @DisplayName("reject transitions a PENDING proposal to REJECTED")
    void rejectTransitions() {
        AiProposal p = pending(6L);
        when(repo.findByIdAndAppUserId(6L, USER_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(AiProposal.class))).thenAnswer(inv -> inv.getArgument(0));

        ProposalDto dto = service.reject(6L);

        assertThat(dto.status()).isEqualTo(AiProposal.Status.REJECTED);
    }

    @Test
    @DisplayName("approve throws NOT_FOUND when the proposal does not belong to the user")
    void approveNotFound() {
        when(repo.findByIdAndAppUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Proposal not found");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("approve throws CONFLICT when the proposal is no longer pending")
    void approveConflict() {
        AiProposal p = pending(5L);
        p.setStatus(AiProposal.Status.APPROVED);
        when(repo.findByIdAndAppUserId(5L, USER_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.approve(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
        verify(repo, never()).save(any());
    }

    private AiProposal pending(Long id) {
        AiProposal p = new AiProposal();
        p.setId(id);
        p.setAppUserId(USER_ID);
        p.setGoalId(1L);
        p.setType("ADD_TARGET");
        p.setPayload("{}");
        p.setStatus(AiProposal.Status.PENDING);
        return p;
    }
}
