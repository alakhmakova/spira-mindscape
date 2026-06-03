package com.spiramindscape.backend.googledocs;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.googledocs.dto.CreateDocRequest;
import com.spiramindscape.backend.googledocs.dto.CreateDocResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports notes to Google Docs.
 *
 * <p>{@code POST /api/notes/google-doc} — creates a Google Doc in the authenticated
 * user's Drive from the note's HTML and returns the document link. Requires an
 * authenticated session + CSRF token (enforced by {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/notes")
public class GoogleDocsController {

    private final GoogleDriveService driveService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleDocsController(GoogleDriveService driveService, CurrentUserProvider currentUserProvider) {
        this.driveService = driveService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/google-doc")
    public CreateDocResponse createGoogleDoc(@RequestBody @Valid CreateDocRequest request) {
        String webViewLink = driveService.createGoogleDoc(
                currentUserProvider.getCurrentUser(), request.title(), request.html());
        return new CreateDocResponse(webViewLink);
    }
}
