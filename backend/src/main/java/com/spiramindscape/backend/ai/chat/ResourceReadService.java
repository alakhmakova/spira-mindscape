package com.spiramindscape.backend.ai.chat;

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

    public ResourceReadService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    /**
     * Returns the readable content of the resource, or a short explanatory
     * message if it is missing, not part of {@code goalId}, or unreadable.
     * Never throws — the result is fed back to the model as a tool result.
     */
    @Transactional(readOnly = true)
    public String read(Long goalId, Long resourceId) {
        if (goalId == null || resourceId == null) return "Resource not found.";
        Optional<Resource> opt = resourceRepository.findById(resourceId);
        if (opt.isEmpty()) return "Resource not found.";

        Resource r = opt.get();
        if (r.getGoal() == null || !goalId.equals(r.getGoal().getId())) {
            return "Resource not found."; // not part of this goal
        }

        String type = r.getType() == null ? "" : r.getType();
        return switch (type) {
            case "note" -> {
                String body = stripHtml(r.getBody() == null ? "" : r.getBody());
                yield body.isBlank() ? "(empty note)" : truncate(body, NOTE_MAX_CHARS);
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
            return "(image file — not readable as text; ask the user to describe it or paste any text)";
        }
        return "(unsupported file type: " + mime + ")";
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
