package com.spiramindscape.backend.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the production login 500: spring-session-jdbc
 * JDK-serializes the whole SecurityContext into PostgreSQL on login. If the
 * principal ({@link AppUserOidcUser} wrapping the {@link AppUser} entity) is
 * not serializable, EVERY login fails at session save with
 * "Failed to convert ... SecurityContextImpl ... to byte[]".
 *
 * <p>This test does exactly what the session store does — serialize and
 * deserialize the authenticated context — so the breakage can never sneak
 * back in unnoticed.
 */
class SessionSerializationTest {

    @Test
    @DisplayName("the authenticated SecurityContext survives JDK serialization round-trip")
    void securityContextSerializes() throws Exception {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setGoogleSub("google-sub-123");
        user.setEmail("user@example.com");
        user.setName("Test User");
        user.setRole("USER");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        OidcIdToken token = OidcIdToken.withTokenValue("token-value")
                .subject(user.getGoogleSub())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail())
                .build();
        AppUserOidcUser principal = new AppUserOidcUser(
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token),
                user);
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google");
        SecurityContextImpl context = new SecurityContextImpl(auth);

        // Serialize — this is the step that blew up in production.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(context);
        }

        // Deserialize and check the business identity survived intact.
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            SecurityContextImpl restored = (SecurityContextImpl) in.readObject();
            AppUserOidcUser restoredPrincipal =
                    (AppUserOidcUser) restored.getAuthentication().getPrincipal();
            assertThat(restoredPrincipal.getAppUser().getId()).isEqualTo(42L);
            assertThat(restoredPrincipal.getAppUser().getEmail()).isEqualTo("user@example.com");
        }
    }
}
