package com.spiramindscape.backend.googledocs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.crypto.EncryptionService;
import com.spiramindscape.backend.auth.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates Google Docs in the user's Drive from note HTML.
 *
 * <p>Flow: the user's encrypted Google refresh token (captured at login —
 * {@code OAuth2LoginSuccessHandler}) is exchanged for a fresh access token at
 * Google's token endpoint, then a Drive {@code files.create} multipart upload
 * sends the note HTML with the Google-Docs mime type so Drive converts it into an
 * editable document. Returns the document's {@code webViewLink}.
 *
 * <p>Scope required: {@code https://www.googleapis.com/auth/drive.file} (the app
 * can only see files it created). No Google client library is used — a plain
 * {@link HttpClient} keeps the dependency surface small, matching the LLM providers.
 */
@Service
public class GoogleDriveService {

    private static final String DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink";
    private static final String GOOGLE_DOC_MIME = "application/vnd.google-apps.document";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final EncryptionService encryptionService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public GoogleDriveService(ClientRegistrationRepository clientRegistrationRepository,
                              EncryptionService encryptionService) {
        this(clientRegistrationRepository, encryptionService, HttpClient.newHttpClient());
    }

    /** Test seam: inject a stubbed {@link HttpClient}. */
    GoogleDriveService(ClientRegistrationRepository clientRegistrationRepository,
                       EncryptionService encryptionService,
                       HttpClient httpClient) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.encryptionService = encryptionService;
        this.httpClient = httpClient;
    }

    /** A created or updated Google Doc: its Drive file id and shareable link. */
    public record CreatedDoc(String fileId, String webViewLink) {}

    /**
     * Creates a NEW Google Doc from the given HTML in the user's Drive.
     *
     * @return the new document's file id + shareable {@code webViewLink}
     */
    public CreatedDoc createDoc(AppUser user, String title, String html) {
        String accessToken = requireAccessToken(user);
        return uploadNewDoc(accessToken, safeTitle(title), html == null ? "" : html);
    }

    /**
     * Overwrites an EXISTING linked Google Doc with the note's current HTML.
     * Note: this replaces the document content — edits made in Google Docs are lost.
     *
     * @return the document's shareable {@code webViewLink}
     */
    public String updateDoc(AppUser user, String fileId, String title, String html) {
        String accessToken = requireAccessToken(user);
        return overwriteDoc(accessToken, fileId, safeTitle(title), html == null ? "" : html).webViewLink();
    }

    /** Decrypts the stored refresh token and exchanges it for a fresh access token. */
    private String requireAccessToken(AppUser user) {
        if (user.getEncRefreshToken() == null || user.getEncRefreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Google Drive access not granted. Sign out and sign in again to enable Google Docs export.");
        }
        return exchangeRefreshTokenForAccessToken(encryptionService.decrypt(user.getEncRefreshToken()));
    }

    // ── Step 1: refresh token → access token ──────────────────────────────────

    private String exchangeRefreshTokenForAccessToken(String refreshToken) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        if (google == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Google client registration not configured");
        }
        String form = "grant_type=refresh_token"
                + "&client_id=" + enc(google.getClientId())
                + "&client_secret=" + enc(google.getClientSecret())
                + "&refresh_token=" + enc(refreshToken);

        HttpRequest request = HttpRequest.newBuilder(URI.create(google.getProviderDetails().getTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            // A revoked/expired refresh token lands here — the user must re-consent.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google rejected the refresh token. Sign out and sign in again to re-grant access.");
        }
        JsonNode node = readJson(response.body());
        String accessToken = node.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google did not return an access token");
        }
        return accessToken;
    }

    // ── Step 2: multipart upload → Google Doc ─────────────────────────────────

    private CreatedDoc uploadNewDoc(String accessToken, String title, String html) {
        String boundary = "spira-" + UUID.randomUUID();
        String body = buildMultipartBody(boundary, metadataJson(title), html);

        HttpRequest request = HttpRequest.newBuilder(URI.create(DRIVE_UPLOAD_URL))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return parseDoc(send(request));
    }

    private CreatedDoc overwriteDoc(String accessToken, String fileId, String title, String html) {
        String boundary = "spira-" + UUID.randomUUID();
        String body = buildMultipartBody(boundary, metadataJson(title), html);
        String url = "https://www.googleapis.com/upload/drive/v3/files/" + fileId
                + "?uploadType=multipart&fields=id,webViewLink";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return parseDoc(send(request));
    }

    private CreatedDoc parseDoc(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Drive API error " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = readJson(response.body());
        String webViewLink = node.path("webViewLink").asText(null);
        if (webViewLink == null || webViewLink.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Drive did not return a document link");
        }
        return new CreatedDoc(node.path("id").asText(null), webViewLink);
    }

    /**
     * Builds a {@code multipart/related} body: a JSON metadata part (document name
     * + Google-Docs mime type, which triggers conversion) followed by the HTML media
     * part. Package-private for unit testing.
     */
    static String buildMultipartBody(String boundary, String metadataJson, String html) {
        return "--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + metadataJson + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n"
                + html + "\r\n"
                + "--" + boundary + "--";
    }

    private String metadataJson(String title) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", title);
        meta.put("mimeType", GOOGLE_DOC_MIME);
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build metadata");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Google: " + e.getMessage(), e);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from Google");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String safeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Spira note";
        }
        return title.trim();
    }
}
