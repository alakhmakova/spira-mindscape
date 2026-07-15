package com.spiramindscape.backend.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer integration test for the native mobile sign-in endpoint.
 *
 * <p>Unlike {@link MobileAuthControllerTest} (pure unit), this drives the real Spring Security
 * filter chain via MockMvc and proves the whole contract end-to-end: a valid token starts a
 * session, and that <em>same session</em> then authenticates subsequent requests
 * ({@code /api/auth/me} and a protected {@code /graphql} call) — exactly like a web login.
 *
 * <p>The Google token verification is mocked ({@link MobileTokenVerifier}); the {@code test}
 * profile uses in-memory servlet sessions (spring-session-jdbc is excluded there), so the
 * MockMvc session can be captured and reused reliably without a database session store.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileAuthIntegrationTest {

    private static final String GRAPHQL_QUERY = """
            {"query": "{ goals { id } }"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockitoBean
    private MobileTokenVerifier tokenVerifier;

    @AfterEach
    void tearDown() {
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Valid token: logs in (no CSRF needed), creates the user, and the session authenticates /api/auth/me and /graphql")
    void validTokenStartsSessionThatAuthenticatesFollowUpRequests() throws Exception {
        when(tokenVerifier.verify("valid-token")).thenReturn(Optional.of(
                new VerifiedGoogleUser("mobile-sub-1", "mobile@example.com", "Mobile User", "https://pic")));

        // 1) Mobile login — no .with(csrf()) proves the endpoint is CSRF-exempt.
        MvcResult login = mockMvc.perform(post("/api/auth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mobile@example.com"))
                .andExpect(jsonPath("$.name").value("Mobile User"))
                .andReturn();

        // The user was persisted (find-or-create), keyed on the Google sub.
        assertThat(appUserRepository.findByGoogleSub("mobile-sub-1")).isPresent();

        // Grab the session the login established.
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // 2) Reuse ONLY the session (no per-request authentication) → /api/auth/me authenticates.
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mobile@example.com"));

        // 3) The same session authorizes a protected GraphQL request (with a CSRF token).
        mockMvc.perform(post("/graphql").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Invalid token → 401 and no user created")
    void invalidTokenReturns401() throws Exception {
        when(tokenVerifier.verify("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(appUserRepository.count()).isZero();
    }

    @Test
    @DisplayName("Without the session cookie, a protected request stays unauthenticated (401)")
    void withoutSessionGraphQlIs401() throws Exception {
        // Proves it is genuinely the session (not something ambient) doing the authentication.
        mockMvc.perform(post("/graphql").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRAPHQL_QUERY))
                .andExpect(status().isUnauthorized());
    }
}
