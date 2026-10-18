package com.spiramindscape.backend.push.web;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Subscribing a browser, through the real security chain: a session and CSRF are required, an
 * endpoint off the push services' allowlist is refused (the SSRF boundary), and so are keys that
 * are not a P-256 point and a 16-byte secret.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spira.webpush.public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
        "spira.webpush.private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
})
class WebPushControllerIntegrationTest {

    static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private WebPushSubscriptionRepository subscriptions;

    private OAuth2AuthenticationToken auth;
    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setGoogleSub("push-sub");
        user.setEmail("push@example.com");
        user.setName("Push");
        user.setRole("USER");
        user = appUserRepository.save(user);
        OidcIdToken token = OidcIdToken.withTokenValue("t").subject(user.getGoogleSub())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail()).build();
        AppUserOidcUser principal = new AppUserOidcUser(
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token), user);
        auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    @AfterEach
    void tearDown() {
        subscriptions.deleteAll();
        appUserRepository.deleteAll();
    }

    private static String body(String endpoint, String p256dh, String auth) {
        return """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}
                """.formatted(endpoint, p256dh, auth);
    }

    @Test
    @DisplayName("The public key is handed out; nothing secret is")
    void key() throws Exception {
        mockMvc.perform(get("/api/push/web/key").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(
                        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"))
                .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test
    @DisplayName("A test push needs CSRF, and reports how many browsers it reached")
    void testPush() throws Exception {
        mockMvc.perform(post("/api/push/web/test").with(authentication(auth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/push/web/test").with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.subscriptions").value(0))
                .andExpect(jsonPath("$.sent").value(0));
    }

    @Test
    @DisplayName("Anonymous is 401 and a missing CSRF token is 403; neither stores anything")
    void authAndCsrf() throws Exception {
        String ok = body("https://fcm.googleapis.com/fcm/send/abc", P256DH, AUTH);
        mockMvc.perform(post("/api/push/web/subscribe").with(anonymous()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/push/web/subscribe").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        assertThat(subscriptions.count()).isZero();
    }

    @Test
    @DisplayName("A browser subscribes, re-subscribing refreshes the same row, and it can leave")
    void subscribeAndUnsubscribe() throws Exception {
        String ok = body("https://fcm.googleapis.com/fcm/send/abc", P256DH, AUTH);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/push/web/subscribe").with(authentication(auth)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(ok))
                    .andExpect(status().isNoContent());
        }
        assertThat(subscriptions.findAll()).singleElement()
                .satisfies(s -> assertThat(s.getUserId()).isEqualTo(user.getId()));

        mockMvc.perform(post("/api/push/web/unsubscribe").with(authentication(auth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.count()).isZero();
    }

    @Test
    @DisplayName("An endpoint off the push services, or keys that are not keys, are refused")
    void refusesBadSubscriptions() throws Exception {
        for (String bad : List.of(
                body("http://169.254.169.254/computeMetadata/v1/", P256DH, AUTH),
                body("https://internal.example/push", P256DH, AUTH),
                body("https://fcm.googleapis.com/fcm/send/abc", "AAAA", AUTH),
                body("https://fcm.googleapis.com/fcm/send/abc", P256DH, "c2hvcnQ"),
                "{}")) {
            mockMvc.perform(post("/api/push/web/subscribe").with(authentication(auth)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
        assertThat(subscriptions.count()).isZero();
    }
}
