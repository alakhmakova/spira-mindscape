package com.spiramindscape.backend.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppUserService} — the business logic that creates and refreshes
 * user accounts from Google OIDC sign-ins.
 *
 * <p>These are pure unit tests (no Spring, no database). We mock the repository and
 * build minimal OIDC tokens with only the claims that {@link AppUserService} reads.
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private AppUserService appUserService;

    // ─── findOrCreateFromOidc: first sign-in ──────────────────────────────────

    @Test
    @DisplayName("First sign-in: creates a new AppUser from the OIDC claims")
    void firstSignInCreatesNewUser() {
        OidcUser oidcUser = oidcUser("google-sub-123", "alice@example.com", "Alice Smith", "https://pic.url/alice");
        when(appUserRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserService.findOrCreateFromOidc(oidcUser);

        // The saved user must have all fields from the token
        assertThat(result.getGoogleSub()).isEqualTo("google-sub-123");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getName()).isEqualTo("Alice Smith");
        assertThat(result.getPictureUrl()).isEqualTo("https://pic.url/alice");
        assertThat(result.getLastLoginAt()).isNotNull();
        // Role defaults to USER
        assertThat(result.getRole()).isEqualTo("USER");

        // Save must be called exactly once (create path)
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    @DisplayName("First sign-in: identity key is google_sub, not email")
    void firstSignInLooksUpByGoogleSub() {
        OidcUser oidcUser = oidcUser("sub-abc", "bob@example.com", "Bob", null);
        when(appUserRepository.findByGoogleSub("sub-abc")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        appUserService.findOrCreateFromOidc(oidcUser);

        verify(appUserRepository).findByGoogleSub("sub-abc");
        // findByEmail should NOT be called — sub is the canonical key
        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("First sign-in: null picture URL is allowed")
    void firstSignInAllowsNullPicture() {
        OidcUser oidcUser = oidcUser("sub-nopic", "carol@example.com", "Carol", null);
        when(appUserRepository.findByGoogleSub("sub-nopic")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserService.findOrCreateFromOidc(oidcUser);

        assertThat(result.getPictureUrl()).isNull();
    }

    // ─── findOrCreateFromOidc: returning sign-in ──────────────────────────────

    @Test
    @DisplayName("Returning sign-in: loads existing user and refreshes name, email, picture, lastLoginAt")
    void returningSignInRefreshesExistingUser() {
        AppUser existing = existingUser("sub-xyz", "old@example.com", "Old Name", null);
        OidcUser oidcUser = oidcUser("sub-xyz", "new@example.com", "New Name", "https://new.pic");
        when(appUserRepository.findByGoogleSub("sub-xyz")).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserService.findOrCreateFromOidc(oidcUser);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getPictureUrl()).isEqualTo("https://new.pic");
        assertThat(result.getLastLoginAt()).isNotNull();

        // Must save the existing user (update path)
        verify(appUserRepository).save(existing);
    }

    @Test
    @DisplayName("Returning sign-in: does NOT create a duplicate user row")
    void returningSignInDoesNotDuplicate() {
        AppUser existing = existingUser("sub-dup", "same@example.com", "Same", null);
        OidcUser oidcUser = oidcUser("sub-dup", "same@example.com", "Same", null);
        when(appUserRepository.findByGoogleSub("sub-dup")).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        appUserService.findOrCreateFromOidc(oidcUser);

        // Only one save (update, not insert)
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        // The saved entity is the same instance (id preserved)
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    @DisplayName("Returning sign-in: email change with same sub updates email on the same user row")
    void emailChangeWithSameSubUpdatesEmail() {
        AppUser existing = existingUser("sub-email", "original@example.com", "User", null);
        existing.setId(99L);

        OidcUser oidcUser = oidcUser("sub-email", "changed@example.com", "User", null);
        when(appUserRepository.findByGoogleSub("sub-email")).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserService.findOrCreateFromOidc(oidcUser);

        // Same user id — no new row
        assertThat(result.getId()).isEqualTo(99L);
        // Email updated
        assertThat(result.getEmail()).isEqualTo("changed@example.com");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Builds a minimal OidcUser with the given claims (what Spring gets from Google). */
    private static OidcUser oidcUser(String sub, String email, String name, String picture) {
        OidcIdToken token = OidcIdToken.withTokenValue("test-token")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", email)
                .claim("name", name)
                .claim("picture", picture)
                .build();
        OidcUserInfo info = new OidcUserInfo(Map.of(
                "sub", sub,
                "email", email,
                "name", name != null ? name : "",
                "picture", picture != null ? picture : ""
        ));
        return new DefaultOidcUser(List.of(), token, info);
    }

    /** Builds an existing AppUser as it would exist in the database. */
    private static AppUser existingUser(String sub, String email, String name, String pictureUrl) {
        AppUser user = new AppUser();
        user.setGoogleSub(sub);
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(pictureUrl);
        user.setRole("USER");
        return user;
    }
}
