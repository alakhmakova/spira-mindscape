package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserOidcUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.upcoming.UpcomingEventRepository;
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
 * The Upcoming operations through the real HTTP security chain — what {@code GraphQlTester} skips.
 * {@code SecurityIntegrationTest} pins the chain for {@code goals}; this pins it for the new
 * query and mutations, and checks the part that matters for a write: a refused request leaves
 * <b>nothing</b> behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpcomingEventSecurityIntegrationTest {

    private static final String LIST = """
            {"query": "{ upcomingEvents { id } }"}
            """;
    private static final String CREATE = """
            {"query": "mutation { createUpcomingEvent(input: { kind: \\"important\\", title: \\"Trip\\", date: \\"2026-12-31T00:00:00Z\\" }) { id } }"}
            """;
    private static final String DELETE = """
            {"query": "mutation { deleteUpcomingEvent(id: 1) }"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private UpcomingEventRepository upcomingEventRepository;

    private OAuth2AuthenticationToken auth;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setGoogleSub("upcoming-security-sub");
        user.setEmail("upcoming-security@example.com");
        user.setName("Upcoming Security");
        user.setRole("USER");
        user = appUserRepository.save(user);

        OidcIdToken token = OidcIdToken.withTokenValue("t")
                .subject(user.getGoogleSub())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail())
                .build();
        DefaultOidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
        AppUserOidcUser principal = new AppUserOidcUser(oidcUser, user);
        auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    @AfterEach
    void tearDown() {
        upcomingEventRepository.deleteAll();
        goalRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous: the list, a create and a delete are all 401")
    void anonymousIs401() throws Exception {
        for (String body : List.of(LIST, CREATE, DELETE)) {
            mockMvc.perform(post("/graphql").with(anonymous()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(upcomingEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Signed in but without a CSRF token: a create is 403 and writes nothing")
    void createWithoutCsrfIs403() throws Exception {
        mockMvc.perform(post("/graphql").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden());
        assertThat(upcomingEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Signed in with a CSRF token: the create goes through")
    void createWithCsrfIs200() throws Exception {
        mockMvc.perform(post("/graphql").with(authentication(auth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createUpcomingEvent.id").exists());
        assertThat(upcomingEventRepository.count()).isEqualTo(1);
    }
}
