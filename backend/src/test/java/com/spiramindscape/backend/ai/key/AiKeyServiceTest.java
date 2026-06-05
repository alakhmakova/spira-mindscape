package com.spiramindscape.backend.ai.key;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.provider.ProviderType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiKeyService}: every operation must be scoped to the
 * user resolved from {@link CurrentUserProvider}, and the raw key must never be
 * stored unencrypted.
 */
@ExtendWith(MockitoExtension.class)
class AiKeyServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AiApiKeyRepository repo;
    @Mock private EncryptionService encryption;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private AiKeyService service;

    @BeforeEach
    void stubCurrentUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    @Test
    @DisplayName("saveKey encrypts the key and scopes it to the authenticated user")
    void saveKeyScopesToCurrentUser() {
        when(repo.findByAppUserIdAndProvider(USER_ID, "MISTRAL")).thenReturn(Optional.empty());
        when(encryption.encrypt("sk-mistral-123456")).thenReturn("ENC");
        when(repo.save(any(AiApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        KeyInfoResponse res = service.saveKey(
                new SaveKeyRequest("MISTRAL", "sk-mistral-123456", "mistral-large"));

        ArgumentCaptor<AiApiKey> captor = ArgumentCaptor.forClass(AiApiKey.class);
        verify(repo).save(captor.capture());
        AiApiKey saved = captor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProvider()).isEqualTo("MISTRAL");
        assertThat(saved.getEncKey()).isEqualTo("ENC");
        assertThat(res.provider()).isEqualTo("MISTRAL");
        assertThat(res.model()).isEqualTo("mistral-large");
        assertThat(res.hint()).endsWith("3456");
    }

    @Test
    @DisplayName("saveKey reuses (overwrites) an existing key row for the same provider")
    void saveKeyOverwritesExisting() {
        AiApiKey existing = new AiApiKey();
        existing.setAppUserId(USER_ID);
        existing.setProvider("MISTRAL");
        when(repo.findByAppUserIdAndProvider(USER_ID, "MISTRAL")).thenReturn(Optional.of(existing));
        when(encryption.encrypt(any())).thenReturn("ENC2");
        when(repo.save(any(AiApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveKey(new SaveKeyRequest("mistral", "another-key-123", null));

        verify(repo).save(existing);
        assertThat(existing.getEncKey()).isEqualTo("ENC2");
    }

    @Test
    @DisplayName("listKeys returns only the authenticated user's providers, never the key")
    void listKeysScopedToUser() {
        AiApiKey k = new AiApiKey();
        k.setProvider("ANTHROPIC");
        k.setKeyHint("••••abcd");
        k.setModel("claude");
        when(repo.findByAppUserId(USER_ID)).thenReturn(List.of(k));

        List<KeyInfoResponse> result = service.listKeys();

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.provider()).isEqualTo("ANTHROPIC");
            assertThat(r.hint()).isEqualTo("••••abcd");
            assertThat(r.model()).isEqualTo("claude");
        });
        verify(repo).findByAppUserId(USER_ID);
    }

    @Test
    @DisplayName("getKey decrypts the stored key for the current user")
    void getKeyDecrypts() {
        AiApiKey k = new AiApiKey();
        k.setEncKey("ENC");
        k.setModel("m");
        when(repo.findByAppUserIdAndProvider(USER_ID, "MISTRAL")).thenReturn(Optional.of(k));
        when(encryption.decrypt("ENC")).thenReturn("decrypted-key");

        Optional<AiKeyService.StoredKey> result = service.getKey(ProviderType.MISTRAL);

        assertThat(result).isPresent();
        assertThat(result.get().apiKey()).isEqualTo("decrypted-key");
        assertThat(result.get().model()).isEqualTo("m");
    }

    @Test
    @DisplayName("updateModel throws NOT_FOUND when no key exists for the provider")
    void updateModelThrowsWhenMissing() {
        when(repo.findByAppUserIdAndProvider(USER_ID, "MISTRAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateModel("MISTRAL", "x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No key configured");
    }

    @Test
    @DisplayName("deleteKey delegates to the repository scoped by user + provider")
    void deleteKeyScoped() {
        service.deleteKey("mistral");
        verify(repo).deleteByAppUserIdAndProvider(USER_ID, "MISTRAL");
    }

    @Test
    @DisplayName("hasKey delegates to the repository scoped by the current user")
    void hasKeyScoped() {
        when(repo.existsByAppUserIdAndProvider(USER_ID, "ANTHROPIC")).thenReturn(true);
        assertThat(service.hasKey(ProviderType.ANTHROPIC)).isTrue();
    }
}
