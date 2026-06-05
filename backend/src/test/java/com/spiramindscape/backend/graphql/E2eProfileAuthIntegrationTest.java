package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the CI-only {@code e2e} profile test-auth ({@code E2eTestAuthFilter}) that
 * lets the black-box Python E2E suite drive the auth-gated, user-scoped API:
 * the {@code X-E2E-Auth} header authenticates the request as a seeded test user, and
 * CSRF is disabled under this profile — while an anonymous request is still 401.
 *
 * <p>Runs with both {@code test} (H2) and {@code e2e} profiles active.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "e2e"})
class E2eProfileAuthIntegrationTest {

    private static final String GOALS_QUERY = """
            {"query": "{ goals { id } }"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private GoalRepository goalRepository;

    @AfterEach
    void cleanup() {
        goalRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous POST /graphql (no X-E2E-Auth) returns 401 even under the e2e profile")
    void anonymousStillReturns401() throws Exception {
        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOALS_QUERY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("X-E2E-Auth header authenticates the request (200) and CSRF is disabled")
    void headerAuthenticatesAndCsrfDisabled() throws Exception {
        // No .with(csrf()) — CSRF is disabled under the e2e profile, so this 200s.
        mockMvc.perform(post("/graphql")
                        .header("X-E2E-Auth", "e2e@test.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOALS_QUERY))
                .andExpect(status().isOk());
    }
}
