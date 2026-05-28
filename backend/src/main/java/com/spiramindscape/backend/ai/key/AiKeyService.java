package com.spiramindscape.backend.ai.key;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.provider.ProviderType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Manages per-user API keys for AI providers.
 *
 * <p>Keys are encrypted before being stored and are never returned in
 * decrypted form through the public API — only the {@link #getKey} method
 * (used internally by the AI chat service) decrypts them.
 *
 * <p>TODO: Replace {@code currentUserId()} stub with real authenticated user
 * lookup once Google OAuth is merged from the {@code testing/e2e} branch.
 */
@Service
@Transactional
public class AiKeyService {

    /**
     * Development stub user ID used until authentication is available.
     * All stored keys belong to this user in the {@code feature/ai} branch.
     */
    public static final Long DEV_USER_ID = 1L;

    private final AiApiKeyRepository repo;
    private final EncryptionService encryption;

    public AiKeyService(AiApiKeyRepository repo, EncryptionService encryption) {
        this.repo = repo;
        this.encryption = encryption;
    }

    /** Save or update the API key for a provider. Existing key is overwritten. */
    public KeyInfoResponse saveKey(SaveKeyRequest request) {
        Long userId = currentUserId();
        String provider = ProviderType.fromString(request.provider()).name();

        AiApiKey entity = repo.findByAppUserIdAndProvider(userId, provider)
                .orElseGet(AiApiKey::new);

        String hint = buildHint(request.apiKey());

        entity.setAppUserId(userId);
        entity.setProvider(provider);
        entity.setModel(request.model());
        entity.setEncKey(encryption.encrypt(request.apiKey()));
        entity.setKeyHint(hint);

        repo.save(entity);
        return new KeyInfoResponse(provider, hint, request.model());
    }

    /** List all configured providers for the current user (no keys exposed). */
    public List<KeyInfoResponse> listKeys() {
        return repo.findByAppUserId(currentUserId()).stream()
                .map(k -> new KeyInfoResponse(k.getProvider(), k.getKeyHint(), k.getModel()))
                .toList();
    }

    /** Delete the key for the given provider. No-op if no key exists. */
    public void deleteKey(String provider) {
        String normalized = ProviderType.fromString(provider).name();
        repo.deleteByAppUserIdAndProvider(currentUserId(), normalized);
    }

    /**
     * Retrieves and decrypts the API key for the given provider.
     * Used internally by the AI chat service — not exposed via the API.
     *
     * @return the decrypted API key, or {@link Optional#empty()} if not configured
     */
    public Optional<StoredKey> getKey(ProviderType providerType) {
        return repo.findByAppUserIdAndProvider(currentUserId(), providerType.name())
                .map(k -> new StoredKey(encryption.decrypt(k.getEncKey()), k.getModel()));
    }

    /** Whether any key is configured for the given provider. */
    public boolean hasKey(ProviderType providerType) {
        return repo.existsByAppUserIdAndProvider(currentUserId(), providerType.name());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * TODO: Replace with {@code SecurityContextHolder} lookup once auth is merged.
     */
    private Long currentUserId() {
        return DEV_USER_ID;
    }

    private static String buildHint(String apiKey) {
        int len = apiKey.length();
        String suffix = apiKey.substring(Math.max(0, len - 4));
        return "••••" + suffix;
    }

    /** Decrypted key + optional model preference, used only within the service layer. */
    public record StoredKey(String apiKey, String model) {}
}
