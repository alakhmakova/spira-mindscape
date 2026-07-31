package com.spiramindscape.backend.ai.preference;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores per-user AI chat preferences that should follow the user across devices
 * — currently the selected chat provider (BUG-018 follow-up). The per-provider
 * model already lives server-side on the API key; only the "which provider is
 * active" choice was device-local. Reads/writes go through the repository (not
 * the possibly-stale session principal) so the value is always the DB truth.
 */
@Service
public class AiPreferenceService {

    private final AppUserRepository users;
    private final CurrentUserProvider currentUserProvider;

    public AiPreferenceService(AppUserRepository users, CurrentUserProvider currentUserProvider) {
        this.users = users;
        this.currentUserProvider = currentUserProvider;
    }

    /** The current user's saved provider, or null if they haven't chosen one. */
    @Transactional
    public String getProvider() {
        return currentUser().getPreferredAiProvider();
    }

    /** Persist the current user's selected provider. */
    @Transactional
    public String setProvider(String provider) {
        AppUser user = currentUser();
        user.setPreferredAiProvider(provider);
        users.save(user);
        return provider;
    }

    private AppUser currentUser() {
        Long id = currentUserProvider.getCurrentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
