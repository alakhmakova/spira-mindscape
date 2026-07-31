package com.spiramindscape.backend.ai.preference;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiPreferenceService} persists the user's provider choice against their
 * own account (so it syncs across devices) and reads it back from the DB truth.
 */
@ExtendWith(MockitoExtension.class)
class AiPreferenceServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private AppUserRepository users;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private AiPreferenceService service;

    private AppUser user;

    @BeforeEach
    void stubCurrentUser() {
        user = new AppUser();
        user.setId(USER_ID);
        AppUser principal = new AppUser();
        principal.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(principal);
        lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void getReturnsTheStoredProvider() {
        user.setPreferredAiProvider("GEMINI");

        assertThat(service.getProvider()).isEqualTo("GEMINI");
    }

    @Test
    void getReturnsNullWhenUnset() {
        assertThat(service.getProvider()).isNull();
    }

    @Test
    void setPersistsTheProviderOnTheCurrentUser() {
        when(users.save(user)).thenReturn(user);

        String result = service.setProvider("MISTRAL");

        assertThat(result).isEqualTo("MISTRAL");
        assertThat(user.getPreferredAiProvider()).isEqualTo("MISTRAL");
        verify(users).save(user); // written against THIS user's row (per-user sync)
    }
}
