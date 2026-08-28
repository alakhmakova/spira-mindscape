package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.provider.LlmImage;
import com.spiramindscape.backend.ai.provider.VisionSupport;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Reads the textual content of a single resource on demand, for the AI's
 * {@code read_resource} tool. Content is loaded only when the model asks for
 * it (rather than embedded in every request), and is bounded so a large file
 * cannot blow up the chat context.
 */
@Service
public class ResourceReadService {

    private static final int NOTE_MAX_CHARS = 8000;
    private static final int PDF_MAX_CHARS = 12000;

    private final ResourceRepository resourceRepository;
    private final CurrentUserProvider currentUserProvider;

    public ResourceReadService(ResourceRepository resourceRepository,
                               CurrentUserProvider currentUserProvider) {
        this.resourceRepository = resourceRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * What a resource attached to a chat message (BUG-030) resolves to, once the server has
     * confirmed it belongs to the requesting user. A **file/image** resource yields its
     * {@code dataUrl} so it can ride the same vision / PDF / DOCX pipeline as a device file; a
     * **note / link / contact** yields already-extracted {@code text}. Exactly one of the two is
     * non-null.
     */
    public record AttachmentContent(String name, String mime, String dataUrl, String text) {
        static AttachmentContent file(String name, String mime, String dataUrl) {
            return new AttachmentContent(name, mime, dataUrl, null);
        }

        static AttachmentContent text(String name, String text) {
            return new AttachmentContent(name, "text/plain", null, text);
        }

        public boolean isFile() {
            return dataUrl != null;
        }
    }

    /**
     * Resolves a resource the user attached to a message, **owner-scoped**.
     *
     * The resource id is user-supplied and untrusted (BUG-030): a request could name any id,
     * including another user's. So this loads the resource and returns it **only if its goal
     * belongs to the current user** — otherwise {@link Optional#empty()}, which the caller turns
     * into a neutral "unavailable" note. This is the same boundary
     * {@code CrossUserIsolationIntegrationTest} guards elsewhere, checked here rather than trusted
     * from the client.
     *
     * File/image resources come back as a {@code dataUrl}; notes, links and contacts come back as
     * text (reusing the same readable forms the {@code read_resource} tool produces).
     */
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> resolveOwnedAttachment(Long resourceId) {
        if (resourceId == null) return Optional.empty();
        Optional<Resource> opt = resourceRepository.findById(resourceId);
        if (opt.isEmpty()) return Optional.empty();

        Resource r = opt.get();
        Long ownerId = r.getGoal() == null || r.getGoal().getUser() == null
                ? null : r.getGoal().getUser().getId();
        Long currentId = currentUserProvider.getCurrentUser().getId();
        if (ownerId == null || !ownerId.equals(currentId)) {
            return Optional.empty(); // not this user's resource — never read it
        }

        String name = r.getName() == null || r.getName().isBlank() ? "resource" : r.getName();
        String type = r.getType() == null ? "" : r.getType();
        return switch (type) {
            case "file" -> {
                String dataUrl = r.getDataUrl();
                yield (dataUrl == null || dataUrl.isBlank())
                        ? Optional.empty()
                        : Optional.of(AttachmentContent.file(name, r.getMime(), dataUrl));
            }
            case "note"  -> Optional.of(AttachmentContent.text(name,
                    r.getBody() == null || r.getBody().isBlank() ? "(empty note)" : truncate(r.getBody(), NOTE_MAX_CHARS)));
            case "link"  -> Optional.of(AttachmentContent.text(name,
                    r.getUrl() == null ? "(no URL)" : "URL: " + r.getUrl()));
            case "email" -> Optional.of(AttachmentContent.text(name, contactDetails(r)));
            default -> Optional.empty();
        };
    }

    /**
     * Returns the readable content of the resource, or a short explanatory
     * message if it is missing, not part of {@code goalId}, owned by someone else,
     * or unreadable. Never throws — the result is fed back to the model as a tool
     * result.
     *
     * <p><b>Both the goal and the owner are checked</b> (BUG-054). Matching the
     * resource against {@code goalId} alone was not a boundary at all, because
     * {@code goalId} itself comes from the request body: naming another person's goal
     * and one of its resource ids made the {@code read_resource} tool hand back their
     * note body, link or extracted PDF text. Every failure returns the same
     * "Resource not found." so the model — and through it the user — cannot tell the
     * three cases apart.
     */
    @Transactional(readOnly = true)
    public String read(Long goalId, Long resourceId) {
        if (goalId == null || resourceId == null) return "Resource not found.";
        Optional<Resource> opt = resourceRepository.findById(resourceId);
        if (opt.isEmpty()) return "Resource not found.";

        Resource r = opt.get();
        if (!belongsToCurrentUsersGoal(r, goalId)) {
            return "Resource not found."; // missing, another goal, or another user
        }

        String type = r.getType() == null ? "" : r.getType();
        return switch (type) {
            case "note" -> {
                // Return the note's HTML (not stripped text) so the model can SEE the
                // existing formatting and preserve it when asked to edit the note —
                // stripping it here is why edits used to come back as plain text.
                String html = r.getBody() == null ? "" : r.getBody();
                yield html.isBlank() ? "(empty note)" : truncate(html, NOTE_MAX_CHARS);
            }
            case "link"  -> r.getUrl() == null ? "(no URL)" : "URL: " + r.getUrl();
            case "email" -> contactDetails(r);
            case "file"  -> readFile(r);
            default -> "(nothing to read)";
        };
    }

    private String readFile(Resource r) {
        String mime = r.getMime() == null ? "" : r.getMime().toLowerCase();
        if (mime.contains("pdf")) {
            String text = ResourceTextExtractor.extractPdfText(r.getDataUrl(), PDF_MAX_CHARS);
            return text.isBlank()
                    ? "(this PDF has no extractable text — it is likely scanned/image-only; "
                      + "ask the user to paste the text)"
                    : text;
        }
        if (mime.startsWith("image/")) {
            // Vision-capable image types are delivered as an actual image via
            // readImage(); this text path only reaches image subtypes the
            // providers can't view (e.g. image/svg+xml).
            return VisionSupport.isVisionMime(mime)
                    ? "(image is attached below for you to view)"
                    : "(image type " + mime + " can't be viewed; ask the user to describe it)";
        }
        return "(unsupported file type: " + mime + ")";
    }

    /**
     * Returns the image to SHOW the model for an image resource the model asked
     * to read, or empty if the resource is missing, not part of {@code goalId},
     * owned by someone else, not a file, or not a vision-viewable image type. It
     * applies exactly the boundary {@link #read} does — see the note there on why
     * the goal match alone was not one.
     */
    @Transactional(readOnly = true)
    public Optional<LlmImage> readImage(Long goalId, Long resourceId) {
        if (goalId == null || resourceId == null) return Optional.empty();
        Optional<Resource> opt = resourceRepository.findById(resourceId);
        if (opt.isEmpty()) return Optional.empty();

        Resource r = opt.get();
        if (!belongsToCurrentUsersGoal(r, goalId)) {
            return Optional.empty(); // missing, another goal, or another user
        }
        if (!"file".equals(r.getType())) return Optional.empty();
        String mime = r.getMime() == null ? "" : r.getMime().toLowerCase();
        if (!VisionSupport.isVisionMime(mime)) return Optional.empty();
        return Optional.ofNullable(VisionSupport.fromDataUrl(r.getDataUrl()));
    }

    /**
     * True when {@code r} hangs off {@code goalId} <b>and</b> that goal belongs to the
     * user making the request. The second half is the part that was missing: the goal
     * id travels in the request body, so trusting it made the first half circular.
     */
    private boolean belongsToCurrentUsersGoal(Resource r, Long goalId) {
        if (r.getGoal() == null || !goalId.equals(r.getGoal().getId())) return false;
        Long ownerId = r.getGoal().getUser() == null ? null : r.getGoal().getUser().getId();
        return ownerId != null && ownerId.equals(currentUserProvider.getCurrentUser().getId());
    }

    private String contactDetails(Resource r) {
        StringBuilder c = new StringBuilder();
        if (r.getName() != null)  c.append("Name: ").append(r.getName());
        if (r.getRole() != null)  c.append("\nRole: ").append(r.getRole());
        if (r.getEmail() != null) c.append("\nEmail: ").append(r.getEmail());
        if (r.getPhone() != null) c.append("\nPhone: ").append(r.getPhone());
        return c.length() == 0 ? "(no contact details)" : c.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…[truncated]" : s;
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .trim();
    }
}
