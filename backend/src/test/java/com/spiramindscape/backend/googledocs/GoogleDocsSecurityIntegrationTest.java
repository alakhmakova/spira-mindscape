package com.spiramindscape.backend.googledocs;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer security tests for {@code POST /api/notes/google-doc}: the endpoint
 * is auth + CSRF protected like every other mutation. The Drive service is mocked
 * (the real Google call can't run in a test) so the 200 path exercises the
 * controller + security, not Google itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleDocsSecurityIntegrationTest {

    private static final String REQUEST_JSON = """
            {"title":"My note","html":"<p>hello</p>"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockitoBean
    private GoogleDriveService driveService;

    private OAuth2AuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        AppUser user = appUserRepository.save(buildTestUser());
        testAuth = buildAuth(user);
    }

    @AfterEach
    void tearDown() {
        appUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous POST /api/notes/google-doc returns 401")
    void anonymousReturns401() throws Exception {
        mockMvc.perform(post("/api/notes/google-doc")
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated POST without CSRF token returns 403")
    void authenticatedWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/notes/google-doc")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated POST with CSRF returns the created doc link (200)")
    void authenticatedWithCsrfReturnsLink() throws Exception {
        when(driveService.createGoogleDoc(any(AppUser.class), eq("My note"), eq("<p>hello</p>")))
                .thenReturn("https://docs.google.com/document/d/abc/edit");

        mockMvc.perform(post("/api/notes/google-doc")
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webViewLink").value("https://docs.google.com/document/d/abc/edit"));
    }

    // ─── helpers (mirror SecurityIntegrationTest) ──────────────────────────────

    private AppUser buildTestUser() {
        AppUser user = new AppUser();
        user.setGoogleSub("gdocs-test-sub");
        user.setEmail("gdocs-test@example.com");
        user.setName("GDocs Test User");
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
