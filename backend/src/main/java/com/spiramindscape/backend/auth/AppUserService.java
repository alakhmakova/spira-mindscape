package com.spiramindscape.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Business logic for creating and refreshing user accounts from Google OIDC sign-ins.
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;

    /**
     * Called on every successful Google sign-in.
     * <ul>
     *   <li>First sign-in: creates a new {@link AppUser} row.</li>
     *   <li>Subsequent sign-ins: refreshes {@code email}, {@code name}, {@code pictureUrl}
     *       and sets {@code lastLoginAt}.</li>
     * </ul>
     * Identity is keyed by the stable Google {@code sub}, not the email.
     */
    @Transactional
    public AppUser findOrCreateFromOidc(OidcUser oidcUser) {
        return findOrCreateFromGoogle(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getFullName(),
                oidcUser.getPicture());
    }

    /**
     * Find-or-create keyed on the Google {@code sub}, from already-extracted claims.
     * Shared by the web OIDC login ({@link #findOrCreateFromOidc}) and the native mobile
     * sign-in ({@code POST /api/auth/google/mobile}), which verifies a Google ID token and
     * has the same claims but no {@link OidcUser} wrapper.
     */
    @Transactional
    public AppUser findOrCreateFromGoogle(String sub, String email, String name, String pictureUrl) {
        return appUserRepository.findByGoogleSub(sub)
                .map(existing -> refresh(existing, email, name, pictureUrl))
                .orElseGet(() -> create(sub, email, name, pictureUrl));
    }

    @Transactional(readOnly = true)
    public AppUser findById(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByGoogleSub(String sub) {
        return appUserRepository.findByGoogleSub(sub);
    }

    // ---- private helpers ----

    private AppUser create(String sub, String email, String name, String pictureUrl) {
        AppUser user = new AppUser();
        user.setGoogleSub(sub);
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(pictureUrl);
        user.setLastLoginAt(Instant.now());
        return appUserRepository.save(user);
    }

    private AppUser refresh(AppUser user, String email, String name, String pictureUrl) {
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(pictureUrl);
        user.setLastLoginAt(Instant.now());
        return appUserRepository.save(user);
    }
}
