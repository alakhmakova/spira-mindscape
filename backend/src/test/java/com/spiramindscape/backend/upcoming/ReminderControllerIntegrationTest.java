package com.spiramindscape.backend.upcoming;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The service worker's two calls, through the real chain: a notification's words and a button's
 * answer — for the owner only. Another user's event is a 404, exactly like a missing one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private UpcomingEventRepository events;

    private OAuth2AuthenticationToken owner;
    private OAuth2AuthenticationToken stranger;
    private Long eventId;

    @BeforeEach
    void setUp() {
        AppUser a = user("owner");
        AppUser b = user("stranger");
        owner = auth(a);
        stranger = auth(b);
        UpcomingEvent e = new UpcomingEvent();
        e.setUser(a);
        e.setKind("important");
        e.setTitle("Mom's birthday");
        e.setEventDate(Instant.parse("2030-06-18T22:00:00Z"));
        eventId = events.save(e).getId();
    }

    @AfterEach
    void tearDown() {
        events.deleteAll();
        users.deleteAll();
    }

    private AppUser user(String name) {
        AppUser u = new AppUser();
        u.setGoogleSub(name + "-sub");
        u.setEmail(name + "@example.com");
        u.setName(name);
        u.setRole("USER");
        return users.save(u);
    }

    private static OAuth2AuthenticationToken auth(AppUser user) {
        OidcIdToken token = OidcIdToken.withTokenValue("t").subject(user.getGoogleSub())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail()).build();
        AppUserOidcUser principal = new AppUserOidcUser(
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token), user);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    @Test
    @DisplayName("The owner gets the words; a stranger and an anonymous caller do not")
    void notice() throws Exception {
        mockMvc.perform(get("/api/reminders/{id}/notice", eventId).with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mom's birthday"));
        mockMvc.perform(get("/api/reminders/{id}/notice", eventId).with(authentication(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reminders/{id}/notice", eventId).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An answer needs CSRF, the owner, and a known action")
    void answer() throws Exception {
        String done = "{\"action\": \"done\"}";
        mockMvc.perform(post("/api/reminders/{id}/answer", eventId).with(authentication(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(done))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reminders/{id}/answer", eventId).with(authentication(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(done))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/reminders/{id}/answer", eventId).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"delete\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reminders/{id}/answer", eventId).with(authentication(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(done))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false));
    }
}
