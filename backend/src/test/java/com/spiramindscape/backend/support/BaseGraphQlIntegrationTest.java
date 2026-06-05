package com.spiramindscape.backend.support;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserOidcUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Base class for GraphQL integration tests that need an authenticated user.
 *
 * <p>What this does in plain English:
 * <ol>
 *   <li>Before each test: creates a real {@link AppUser} row in the H2 test DB and puts
 *       that user into Spring Security's security context — so every service call to
 *       {@code CurrentUserProvider.getCurrentUser()} returns the test user.</li>
 *   <li>After each test: deletes all goals (and their children via cascade), then deletes
 *       the test user, and clears the security context. This keeps tests independent.</li>
 * </ol>
 *
 * <p>Extend this class in every {@code @SpringBootTest + @AutoConfigureGraphQlTester} test
 * that exercises authenticated business logic. Remove any {@code @BeforeEach} that creates
 * users or sets up auth — this class handles it.
 *
 * <p>If a test needs a <em>second</em> user (e.g., isolation tests), call
 * {@link #createAdditionalUser(String, String)} and switch between them via
 * {@link #setCurrentUser(AppUser)}.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
public abstract class BaseGraphQlIntegrationTest {

    @Autowired
    protected GraphQlTester graphQlTester;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected GoalRepository goalRepository;

    /** The default test user — created fresh before each test. */
    protected AppUser testUser;

    @BeforeEach
    void setUpTestUser() {
        testUser = appUserRepository.save(buildTestUser("test-sub", "test@example.com", "Test User"));
        setCurrentUser(testUser);
    }

    @AfterEach
    void tearDownTestData() {
        goalRepository.deleteAll();   // cascades to all children
        appUserRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    // ---- helpers for subclasses ----

    /**
     * Creates and persists a second test user (e.g., for isolation tests).
     * Does NOT switch the current user — call {@link #setCurrentUser(AppUser)} for that.
     */
    protected AppUser createAdditionalUser(String googleSub, String email) {
        return appUserRepository.save(buildTestUser(googleSub, email, "Other User"));
    }

    /**
     * Switches the authenticated user in the security context.
     * Use this in tests that verify cross-user isolation by acting as different users.
     */
    protected void setCurrentUser(AppUser user) {
        // Build a minimal OidcIdToken (just the sub claim is required)
        OidcIdToken token = OidcIdToken.withTokenValue("test-token-" + user.getId())
                .subject(user.getGoogleSub())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail())
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                token
        );
        AppUserOidcUser principal = new AppUserOidcUser(oidcUser, user);

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---- private factory ----

    private AppUser buildTestUser(String googleSub, String email, String name) {
        AppUser user = new AppUser();
        user.setGoogleSub(googleSub);
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(null);
        user.setRole("USER");
        return user;
    }
}
