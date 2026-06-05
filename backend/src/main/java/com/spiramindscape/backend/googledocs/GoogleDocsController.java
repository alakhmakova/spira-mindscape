package com.spiramindscape.backend.googledocs;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.googledocs.dto.CreateDocRequest;
import com.spiramindscape.backend.googledocs.dto.CreateDocResponse;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports a note to Google Docs and keeps it linked to a single document.
 *
 * <ul>
 *   <li>{@code POST /api/notes/{resourceId}/google-doc} — open the note's linked Doc,
 *       or create + link one on first export. Does NOT overwrite an existing Doc.</li>
 *   <li>{@code POST /api/notes/{resourceId}/google-doc/sync} — push the note's current
 *       content to the linked Doc (creating + linking one if none exists yet). This
 *       overwrites the Doc — edits made in Google Docs are replaced.</li>
 * </ul>
 *
 * Both require an authenticated session + CSRF token (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/notes")
public class GoogleDocsController {

    private final GoogleDriveService driveService;
    private final ResourceService resourceService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleDocsController(GoogleDriveService driveService,
                                ResourceService resourceService,
                                CurrentUserProvider currentUserProvider) {
        this.driveService = driveService;
        this.resourceService = resourceService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Open the linked Doc, or create + link it the first time. */
    @PostMapping("/{resourceId}/google-doc")
    public CreateDocResponse openOrCreate(@PathVariable Long resourceId,
                                          @RequestBody @Valid CreateDocRequest request) {
        Resource note = resourceService.findOwned(resourceId);
        if (note.getDriveWebViewLink() != null && !note.getDriveWebViewLink().isBlank()) {
            return new CreateDocResponse(note.getDriveWebViewLink()); // reopen the same doc
        }
        AppUser user = currentUserProvider.getCurrentUser();
        GoogleDriveService.CreatedDoc doc = driveService.createDoc(user, request.title(), request.html());
        resourceService.linkGoogleDoc(resourceId, doc.fileId(), doc.webViewLink());
        return new CreateDocResponse(doc.webViewLink());
    }

    /** Push the note's current content to the linked Doc (create + link if none yet). */
    @PostMapping("/{resourceId}/google-doc/sync")
    public CreateDocResponse sync(@PathVariable Long resourceId,
                                  @RequestBody @Valid CreateDocRequest request) {
        Resource note = resourceService.findOwned(resourceId);
        AppUser user = currentUserProvider.getCurrentUser();

        if (note.getDriveFileId() != null && !note.getDriveFileId().isBlank()) {
            String link = driveService.updateDoc(user, note.getDriveFileId(), request.title(), request.html());
            resourceService.linkGoogleDoc(resourceId, note.getDriveFileId(), link);
            return new CreateDocResponse(link);
        }
        GoogleDriveService.CreatedDoc doc = driveService.createDoc(user, request.title(), request.html());
        resourceService.linkGoogleDoc(resourceId, doc.fileId(), doc.webViewLink());
        return new CreateDocResponse(doc.webViewLink());
    }
}
