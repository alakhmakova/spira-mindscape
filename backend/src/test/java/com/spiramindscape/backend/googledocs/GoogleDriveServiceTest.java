package com.spiramindscape.backend.googledocs;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import com.spiramindscape.backend.auth.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GoogleDriveService}. The live Google calls can't run in a
 * unit test, so we cover the guard path (no refresh token) and the multipart body
 * construction, which is the part most likely to break silently.
 */
@ExtendWith(MockitoExtension.class)
class GoogleDriveServiceTest {

    @Mock private ClientRegistrationRepository clientRegistrationRepository;
    @Mock private EncryptionService encryptionService;

    @Test
    @DisplayName("createGoogleDoc rejects with PRECONDITION_REQUIRED when the user has no refresh token")
    void throwsWhenNoRefreshToken() {
        GoogleDriveService service = new GoogleDriveService(clientRegistrationRepository, encryptionService);
        AppUser user = new AppUser(); // encRefreshToken == null

        assertThatThrownBy(() -> service.createDoc(user, "Note", "<p>hi</p>"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Google Drive access not granted");
    }

    @Test
    @DisplayName("buildMultipartBody puts the JSON metadata part before the HTML media part")
    void buildsMultipartRelatedBody() {
        String body = GoogleDriveService.buildMultipartBody(
                "BOUND",
                "{\"name\":\"My Note\",\"mimeType\":\"application/vnd.google-apps.document\"}",
                "<h1>Hello</h1>");

        assertThat(body)
                .contains("--BOUND")
                .contains("Content-Type: application/json")
                .contains("application/vnd.google-apps.document")
                .contains("Content-Type: text/html")
                .contains("<h1>Hello</h1>")
                .endsWith("--BOUND--");
        // metadata part must come before the HTML media part
        assertThat(body.indexOf("application/json")).isLessThan(body.indexOf("text/html"));
    }
}
