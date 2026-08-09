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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    @DisplayName("Anonymous POST /api/client-errors is accepted — it is deliberately public")
    void anonymousClientErrorReportIsAccepted() throws Exception {
        // The narrowest possible hole in "/api/** requires auth": browser errors from
        // logged-out users (login page, expired session) are the ones we could never see
        // otherwise. This test is the guard in both directions — if it starts returning
        // 401 the reports stop arriving, and if a future matcher change opens more of
        // /api/** the other tests in this class fail.
        mockMvc.perform(post("/api/client-errors")
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"window-error","message":"boom"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Anonymous GET /api/client-errors is still 401 — only POST is public")
    void clientErrorEndpointOpensPostOnly() throws Exception {
        mockMvc.perform(get("/api/client-errors").with(anonymous()))
                .andExpect(status().isUnauthorized());
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

    // ─── Content-Security-Policy ───────────────────────────────────────────────

    @Test
    @DisplayName("CSP lets the SPA frame its own blob: PDF previews but keeps the site un-frameable")
    void cspAllowsBlobFramesButForbidsBeingFramed() throws Exception {
        mockMvc.perform(get("/health").with(anonymous()))
                .andExpect(status().isOk())
                // The resource PDF preview embeds an app-created blob: URL in an <iframe>;
                // without frame-src blob: the browser's default-src 'self' would block it.
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-src 'self' blob:")))
                // …while the app itself must never be embeddable by another origin.
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")));
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
