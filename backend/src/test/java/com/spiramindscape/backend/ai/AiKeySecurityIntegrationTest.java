package com.spiramindscape.backend.ai;

import com.spiramindscape.backend.ai.key.AiApiKeyRepository;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserOidcUser;
import com.spiramindscape.backend.auth.AppUserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer security tests for saving an AI provider key — the exact path that
 * was broken after auth was merged (the SPA could not save a key because the
 * request was missing the session / CSRF token).
 *
 * <p>Verifies the Spring Security contract for {@code POST /api/ai/keys}:
 * <ul>
 *   <li>anonymous → 401</li>
 *   <li>authenticated but no CSRF token → 403</li>
 *   <li>authenticated + CSRF token → 200, and the key is persisted for that user</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiKeySecurityIntegrationTest {

    private static final String SAVE_KEY_JSON = """
            {"provider":"MISTRAL","apiKey":"sk-test-12345678","model":"mistral-large"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiApiKeyRepository aiApiKeyRepository;

    private OAuth2AuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        AppUser user = appUserRepository.save(buildTestUser());
        testAuth = buildAuth(user);
    }

    @AfterEach
    void tearDown() {
        aiApiKeyRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous POST /api/ai/keys returns 401")
    void anonymousReturns401() throws Exception {
        // Include a CSRF token so the CSRF filter passes and we reach the
        // authentication check, which then rejects with 401.
        mockMvc.perform(post("/api/ai/keys")
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_KEY_JSON))
                .andExpect(status().isUnauthorized());

        assertThat(aiApiKeyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Authenticated POST /api/ai/keys without CSRF token returns 403")
    void authenticatedWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/ai/keys")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_KEY_JSON))
                .andExpect(status().isForbidden());

        assertThat(aiApiKeyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Authenticated POST /api/ai/keys with CSRF token saves the key (200)")
    void authenticatedWithCsrfSavesKey() throws Exception {
        mockMvc.perform(post("/api/ai/keys")
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_KEY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MISTRAL"))
                .andExpect(jsonPath("$.model").value("mistral-large"));

        assertThat(aiApiKeyRepository.count()).isEqualTo(1);
    }

    // ─── helpers (mirror SecurityIntegrationTest) ──────────────────────────────

    private AppUser buildTestUser() {
        AppUser user = new AppUser();
        user.setGoogleSub("ai-key-test-sub");
        user.setEmail("ai-key-test@example.com");
        user.setName("AI Key Test User");
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
