package com.spiramindscape.backend.ai.chat.transcript;

import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptDto;
import com.spiramindscape.backend.ai.chat.transcript.dto.TranscriptRevisionDto;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiChatTranscriptService} scopes every read/write to the authenticated
 * user (cross-device sync stays per-user), upserts last-write-wins, and never
 * lets one user read or overwrite another user's transcript.
 */
@ExtendWith(MockitoExtension.class)
class AiChatTranscriptServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private AiChatTranscriptRepository repo;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private AiChatTranscriptService service;

    @BeforeEach
    void stubCurrentUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    @Test
    @DisplayName("get returns an empty transcript when nothing is stored for the scope")
    void getEmptyWhenNone() {
        when(repo.findByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.empty());

        TranscriptDto dto = service.get(3L);

        assertThat(dto.content()).isEqualTo("[]");
        assertThat(dto.goalId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("get uses the global (goalId IS NULL) row when goalId is null")
    void getGlobalScope() {
        when(repo.findByAppUserIdAndGoalIdIsNull(USER_ID)).thenReturn(Optional.empty());

        service.get(null);

        verify(repo).findByAppUserIdAndGoalIdIsNull(USER_ID);
        verify(repo, never()).findByAppUserIdAndGoalId(any(), any());
    }

    @Test
    @DisplayName("save creates a new transcript scoped to the authenticated user")
    void saveCreatesScopedToUser() {
        when(repo.findByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.empty());
        when(repo.save(any(AiChatTranscript.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(3L, "[{\"role\":\"user\"}]");

        ArgumentCaptor<AiChatTranscript> captor = ArgumentCaptor.forClass(AiChatTranscript.class);
        verify(repo).save(captor.capture());
        AiChatTranscript saved = captor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo(USER_ID);
        assertThat(saved.getGoalId()).isEqualTo(3L);
        assertThat(saved.getContent()).isEqualTo("[{\"role\":\"user\"}]");
    }

    @Test
    @DisplayName("save overwrites the existing row (last write wins) without changing owner/scope")
    void saveUpdatesExisting() {
        AiChatTranscript existing = new AiChatTranscript();
        existing.setId(11L);
        existing.setAppUserId(USER_ID);
        existing.setGoalId(3L);
        existing.setContent("[\"old\"]");
        when(repo.findByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.of(existing));
        when(repo.save(any(AiChatTranscript.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(3L, "[\"new\"]");

        assertThat(existing.getContent()).isEqualTo("[\"new\"]");
        assertThat(existing.getId()).isEqualTo(11L); // updated in place, not a new row
        assertThat(existing.getAppUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("blank content is normalised to an empty JSON array")
    void saveNormalisesBlank() {
        when(repo.findByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.empty());
        when(repo.save(any(AiChatTranscript.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(3L, "  ");

        ArgumentCaptor<AiChatTranscript> captor = ArgumentCaptor.forClass(AiChatTranscript.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("[]");
    }

    @Test
    @DisplayName("SECURITY: a user only ever reads/writes rows scoped to THEIR id")
    void scopedToAuthenticatedUserOnly() {
        // Even if a caller passes a goal that belongs to someone else, the lookup
        // is keyed by the authenticated user's id — another user's row is invisible.
        when(repo.findByAppUserIdAndGoalId(USER_ID, 999L)).thenReturn(Optional.empty());

        service.get(999L);
        service.clear(999L);

        verify(repo, atLeastOnce()).findByAppUserIdAndGoalId(USER_ID, 999L); // scoped to USER_ID
        // clear found nothing for this user → never deletes another user's row
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("clear deletes only the current user's row for the scope")
    void clearDeletesOwnRow() {
        AiChatTranscript existing = new AiChatTranscript();
        existing.setAppUserId(USER_ID);
        existing.setGoalId(3L);
        when(repo.findByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.of(existing));

        service.clear(3L);

        verify(repo).delete(existing);
    }

    // ── revision (the cheap poll target, BUG-019) ─────────────────────────────
    //
    // The chat panel polls every few seconds while it is open. These tests pin the property that
    // makes that affordable: the revision path must read the timestamp *only*. Loading the entity
    // and returning `t.getUpdatedAt()` would satisfy any assertion on the value while still
    // pulling the whole conversation out of the database — which is the bug being fixed.

    @Test
    @DisplayName("revision reads the timestamp alone, never the transcript entity")
    void revisionReadsTimestampOnly() {
        Instant updatedAt = Instant.parse("2026-08-09T10:00:00Z");
        when(repo.findUpdatedAtByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.of(updatedAt));

        TranscriptRevisionDto dto = service.revision(3L);

        assertThat(dto.updatedAt()).isEqualTo(updatedAt);
        assertThat(dto.goalId()).isEqualTo(3L);
        verify(repo, never()).findByAppUserIdAndGoalId(any(), any());
    }

    @Test
    @DisplayName("revision uses the global (goalId IS NULL) row when goalId is null")
    void revisionGlobalScope() {
        when(repo.findUpdatedAtByAppUserIdAndGoalIdIsNull(USER_ID)).thenReturn(Optional.empty());

        service.revision(null);

        verify(repo).findUpdatedAtByAppUserIdAndGoalIdIsNull(USER_ID);
        verify(repo, never()).findUpdatedAtByAppUserIdAndGoalId(any(), any());
    }

    @Test
    @DisplayName("revision is null when the scope has no transcript yet")
    void revisionNullWhenNothingStored() {
        when(repo.findUpdatedAtByAppUserIdAndGoalId(USER_ID, 3L)).thenReturn(Optional.empty());

        assertThat(service.revision(3L).updatedAt()).isNull();
    }
}
