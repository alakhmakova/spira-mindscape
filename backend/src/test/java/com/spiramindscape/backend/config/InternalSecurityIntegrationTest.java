package com.spiramindscape.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dispatcher is not a public trigger: only a token the scheduler's verifier accepts gets in.
 * No token is 401, a refused one 403 — and a signed-in user's session is no way in either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SchedulerTokenVerifier verifier;

    @BeforeEach
    void tokens() {
        when(verifier.verify(anyString())).thenReturn(Optional.empty());
        when(verifier.verify("good")).thenReturn(Optional.of("scheduler@project.iam.gserviceaccount.com"));
    }

    @Test
    @DisplayName("No token: 401")
    void noToken() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token for the wrong audience or account, expired or forged: 403")
    void refusedToken() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch").header("Authorization", "Bearer forged"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user's session is not the scheduler")
    void userSession() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch").with(user("someone")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The scheduler's token runs the dispatcher, with no CSRF token needed")
    void schedulerToken() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch").header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fired").isNumber());
    }
}
