package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserOidcUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer security tests: these verify the Spring Security filter chain rules
 * (401, 403, CSRF, /api/auth/me) using MockMvc — not the GraphQL business logic.
 *
 * <p>Why MockMvc here and not {@link org.springframework.graphql.test.tester.GraphQlTester}?
 * {@code @AutoConfigureGraphQlTester} uses the GraphQL engine directly, bypassing HTTP
 * security filters. To test "does Spring Security block unauthenticated requests?" we need
 * to go through the actual HTTP stack, which MockMvc provides.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String GRAPHQL_QUERY = """
            {"query": "{ goals { id } }"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private GoalRepository goalRepository;

    private AppUser testUser;
    private OAuth2AuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testUser = appUserRepository.save(buildTestUser());
        testAuth = buildAuth(testUser);
    }

    @AfterEach
    void tearDown() {
        goalRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    // ─── Anonymous requests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Anonymous POST /graphql returns 401")
    void anonymousGraphQlReturns401() throws Exception {
        // Include a valid CSRF token so the CSRF filter passes and we reach
        // the authentication check — which then rejects with 401.
        // (Without csrf(), the CSRF filter runs first and returns 403 before
        //  authentication is ever evaluated.)
        mockMvc.perform(post("/graphql")
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated POST /graphql returns 200")
    void authenticatedGraphQlReturns200() throws Exception {
        mockMvc.perform(post("/graphql")
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isOk());
    }

    // ─── /api/auth/me ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me anonymous returns 401")
    void getMeAnonymousReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me authenticated returns user JSON")
    void getMeAuthenticatedReturnsUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    // ─── CSRF ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mutation without CSRF token returns 403")
    void mutationWithoutCsrfTokenReturns403() throws Exception {
        // No .with(csrf()) — should be rejected
        mockMvc.perform(post("/graphql")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mutation with valid CSRF token returns 200")
    void mutationWithCsrfTokenReturns200() throws Exception {
        mockMvc.perform(post("/graphql")
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isOk());
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout invalidates session and returns 204")
    void logoutInvalidatesSessionAndReturns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(testAuth))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private AppUser buildTestUser() {
        AppUser user = new AppUser();
        user.setGoogleSub("security-test-sub");
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setRole("USER");
        return user;
    }

    private OAuth2AuthenticationToken buildAuth(AppUser user) {
        OidcIdToken token = OidcIdToken.withTokenValue("test-token")
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
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
