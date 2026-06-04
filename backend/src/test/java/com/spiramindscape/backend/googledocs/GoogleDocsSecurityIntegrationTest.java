package com.spiramindscape.backend.googledocs;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserOidcUser;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceService;
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
 * HTTP-layer tests for the note ↔ Google Doc endpoints. Both services are mocked
 * (the real Google call can't run in a test), so these verify the security
 * contract (auth + CSRF) and the controller wiring, not Google itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleDocsSecurityIntegrationTest {

    private static final long RESOURCE_ID = 5L;
    private static final String REQUEST_JSON = """
            {"title":"My note","html":"<p>hello</p>"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleDriveService driveService;

    @MockitoBean
    private ResourceService resourceService;

    private OAuth2AuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testAuth = buildAuth(buildTestUser());
    }

    @Test
    @DisplayName("Anonymous POST /api/notes/{id}/google-doc returns 401")
    void anonymousReturns401() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/google-doc", RESOURCE_ID)
                        .with(anonymous())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated POST without CSRF token returns 403")
    void authenticatedWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/google-doc", RESOURCE_ID)
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Open/create: creates + links a doc when the note has none, returns the link")
    void openOrCreateReturnsLink() throws Exception {
        Resource note = new Resource();
        note.setType("note"); // driveWebViewLink == null → create path
        when(resourceService.findOwned(RESOURCE_ID)).thenReturn(note);
        when(driveService.createDoc(any(AppUser.class), eq("My note"), eq("<p>hello</p>")))
                .thenReturn(new GoogleDriveService.CreatedDoc("file-1", "https://docs.google.com/document/d/abc/edit"));

        mockMvc.perform(post("/api/notes/{id}/google-doc", RESOURCE_ID)
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webViewLink").value("https://docs.google.com/document/d/abc/edit"));
    }

    @Test
    @DisplayName("Open: reopens the already-linked doc without creating a new one")
    void openReturnsExistingLink() throws Exception {
        Resource note = new Resource();
        note.setType("note");
        note.setDriveFileId("file-1");
        note.setDriveWebViewLink("https://docs.google.com/document/d/existing/edit");
        when(resourceService.findOwned(RESOURCE_ID)).thenReturn(note);

        mockMvc.perform(post("/api/notes/{id}/google-doc", RESOURCE_ID)
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webViewLink").value("https://docs.google.com/document/d/existing/edit"));
    }

    @Test
    @DisplayName("Sync: pushes the note to the linked doc and returns the link")
    void syncUpdatesLinkedDoc() throws Exception {
        Resource note = new Resource();
        note.setType("note");
        note.setDriveFileId("file-1");
        note.setDriveWebViewLink("https://docs.google.com/document/d/abc/edit");
        when(resourceService.findOwned(RESOURCE_ID)).thenReturn(note);
        when(driveService.updateDoc(any(AppUser.class), eq("file-1"), eq("My note"), eq("<p>hello</p>")))
                .thenReturn("https://docs.google.com/document/d/abc/edit");

        mockMvc.perform(post("/api/notes/{id}/google-doc/sync", RESOURCE_ID)
                        .with(authentication(testAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webViewLink").value("https://docs.google.com/document/d/abc/edit"));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private AppUser buildTestUser() {
        AppUser user = new AppUser();
        user.setId(1L);
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
