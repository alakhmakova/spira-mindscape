package com.spiramindscape.backend.ai.key;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import com.spiramindscape.backend.ai.key.dto.KeyInfoResponse;
import com.spiramindscape.backend.ai.key.dto.SaveKeyRequest;
import com.spiramindscape.backend.ai.preference.AiPreferenceService;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Manages per-user API keys for AI providers.
 *
 * <p>Keys are encrypted before being stored and are never returned in
 * decrypted form through the public API — only the {@link #getKey} method
 * (used internally by the AI chat service) decrypts them.
 *
 * <p>All keys are scoped to the authenticated user resolved from the Spring
 * Security context via {@link CurrentUserProvider}.
 */
@Service
@Transactional
public class AiKeyService {

    private final AiApiKeyRepository repo;
    private final EncryptionService encryption;
    private final CurrentUserProvider currentUserProvider;
    private final AiPreferenceService preferences;

    public AiKeyService(AiApiKeyRepository repo, EncryptionService encryption,
                        CurrentUserProvider currentUserProvider,
                        AiPreferenceService preferences) {
        this.repo = repo;
        this.encryption = encryption;
        this.currentUserProvider = currentUserProvider;
        this.preferences = preferences;
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

    /** Update only the model preference for an existing key. */
    public KeyInfoResponse updateModel(String provider, String model) {
        Long userId = currentUserId();
        String normalized = ProviderType.fromString(provider).name();
        AiApiKey entity = repo.findByAppUserIdAndProvider(userId, normalized)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No key configured for provider " + normalized));
        entity.setModel(model);
        repo.save(entity);
        return new KeyInfoResponse(normalized, entity.getKeyHint(), model);
    }

    /** Delete the key for the given provider. No-op if no key exists. */
    public void deleteKey(String provider) {
        String normalized = ProviderType.fromString(provider).name();
        repo.deleteByAppUserIdAndProvider(currentUserId(), normalized);
        // **And stop pointing at it.** The active provider is a per-user preference that
        // syncs across devices, so deleting the key it names left every surface selecting
        // a provider with no key — which fails at the first message, on the phone as well
        // as the laptop, with nothing to say why. Clearing it makes the next surface fall
        // back to its default instead.
        // Compared through the enum, not by string equality: `PUT /api/ai/preferences`
        // accepts a lowercase provider name, so a stored "mistral" would never have matched
        // the normalised "MISTRAL" and the app would go on pointing at a key that is gone.
        if (namesTheSameProvider(preferences.getProvider(), normalized)) {
            preferences.setProvider(null);
        }
    }

    private static boolean namesTheSameProvider(String stored, String normalized) {
        if (stored == null || stored.isBlank()) return false;
        try {
            return ProviderType.fromString(stored).name().equals(normalized);
        } catch (RuntimeException e) {
            // A preference we cannot parse names no provider we could be deleting.
            return false;
        }
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

    /** The id of the authenticated user, used to scope every key operation. */
    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    private static String buildHint(String apiKey) {
        int len = apiKey.length();
        String suffix = apiKey.substring(Math.max(0, len - 4));
        return "••••" + suffix;
    }

    /** Decrypted key + optional model preference, used only within the service layer. */
    public record StoredKey(String apiKey, String model) {}
}
