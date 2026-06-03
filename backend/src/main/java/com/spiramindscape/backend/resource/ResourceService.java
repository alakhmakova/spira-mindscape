package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.UpdateResourceInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_NOTE_BODY_LENGTH = 50_000;
    public static final int MAX_RESOURCE_LABEL_LENGTH = 200;
    public static final int MAX_LINK_URL_LENGTH = 1_000;

    private static final Set<String> ALLOWED_FILE_MIME_TYPES = Set.of("application/pdf");
    private static final Set<String> COMMON_CREATE_FIELDS = Set.of("type");
    private static final Set<String> NOTE_FIELDS = Set.of("title", "body");
    private static final Set<String> LINK_FIELDS = Set.of("title", "url");
    private static final Set<String> FILE_FIELDS = Set.of("title", "mime", "dataUrl");
    private static final Set<String> EMAIL_FIELDS = Set.of("name", "role", "email", "phone");

    private final ResourceRepository resourceRepository;
    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public List<Resource> findByGoal(Long goalId) {
        goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        return resourceRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Resource>> findByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return resourceRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(resource -> resource.getGoal().getId()));
    }

    @Transactional(readOnly = true)
    public Resource findById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
    }

    @Transactional
    public Resource create(Long goalId, CreateResourceInput input) {
        return create(goalId, input, Map.of());
    }

    @Transactional
    public Resource create(Long goalId, CreateResourceInput input, Map<String, Object> rawInput) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        Resource resource = new Resource();
        resource.setGoal(goal);
        resource.setType(normalizeType(input.type()));
        validateAllowedFields(resource.getType(), rawInput, true);
        applyFields(resource, input.title(), input.body(), input.url(), input.mime(),
                input.dataUrl(), input.name(), input.role(), input.email(), input.phone());
        validateResource(resource);
        return resourceRepository.save(resource);
    }

    @Transactional
    public Resource update(Long id, UpdateResourceInput input) {
        return update(id, input, Map.of());
    }

    @Transactional
    public Resource update(Long id, UpdateResourceInput input, Map<String, Object> rawInput) {
        Resource resource = findById(id);
        validateAllowedFields(resource.getType(), rawInput, false);
        String previousUrl = resource.getUrl();
        String previousTitle = resource.getTitle();
        String previousEmail = resource.getEmail();
        String previousName = resource.getName();
        applyUpdateFields(resource, input, rawInput);
        refreshGeneratedLabels(resource, rawInput, previousUrl, previousTitle, previousEmail, previousName);
        validateResource(resource);
        return resourceRepository.save(resource);
    }

    @Transactional
    public void delete(Long id) {
        resourceRepository.delete(findById(id));
    }

    private void applyFields(Resource r, String title, String body, String url, String mime,
                              String dataUrl, String name, String role, String email, String phone) {
        if (title != null)   r.setTitle(title.trim());
        if (body != null)    r.setBody(body);
        if (url != null)     r.setUrl(url.trim());
        if (mime != null)    r.setMime(mime);
        if (dataUrl != null) r.setDataUrl(dataUrl);
        if (name != null)    r.setName(name.trim());
        if (role != null)    r.setRole(role.trim());
        if (email != null)   r.setEmail(email.trim());
        if (phone != null)   r.setPhone(phone.trim());
    }

    private void applyUpdateFields(Resource r, UpdateResourceInput input, Map<String, Object> rawInput) {
        if (rawInput == null || rawInput.isEmpty()) {
            applyFields(r, input.title(), input.body(), input.url(), input.mime(),
                    input.dataUrl(), input.name(), input.role(), input.email(), input.phone());
            return;
        }

        if (rawInput.containsKey("title"))   r.setTitle(trim(input.title()));
        if (rawInput.containsKey("body"))    r.setBody(input.body());
        if (rawInput.containsKey("url"))     r.setUrl(trim(input.url()));
        if (rawInput.containsKey("mime"))    r.setMime(input.mime());
        if (rawInput.containsKey("dataUrl")) r.setDataUrl(input.dataUrl());
        if (rawInput.containsKey("name"))    r.setName(trim(input.name()));
        if (rawInput.containsKey("role"))    r.setRole(trim(input.role()));
        if (rawInput.containsKey("email"))   r.setEmail(trim(input.email()));
        if (rawInput.containsKey("phone"))   r.setPhone(trim(input.phone()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private void refreshGeneratedLabels(Resource resource, Map<String, Object> rawInput, String previousUrl,
                                        String previousTitle, String previousEmail, String previousName) {
        if (rawInput == null || rawInput.isEmpty()) {
            return;
        }
        if ("link".equals(resource.getType())
                && rawInput.containsKey("url")
                && !rawInput.containsKey("title")
                && Objects.equals(previousTitle, titleFromUrl(previousUrl))) {
            resource.setTitle(titleFromUrl(resource.getUrl()));
        }
        if ("email".equals(resource.getType())
                && rawInput.containsKey("email")
                && !rawInput.containsKey("name")
                && Objects.equals(previousName, previousEmail)) {
            resource.setName(resource.getEmail());
        }
    }

    private String normalizeType(String type) {
        String normalized = Objects.requireNonNull(type).toLowerCase(Locale.ROOT);
        if (!List.of("note", "link", "file", "email").contains(normalized)) {
            throw new IllegalArgumentException("Unknown resource type: " + type);
        }
        return normalized;
    }

    private void validateAllowedFields(String type, Map<String, Object> rawInput, boolean includeTypeField) {
        if (rawInput == null || rawInput.isEmpty()) {
            return;
        }

        Set<String> allowed = allowedFields(type);
        for (String field : rawInput.keySet()) {
            if (includeTypeField && COMMON_CREATE_FIELDS.contains(field)) {
                continue;
            }
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        "Field '" + field + "' is not allowed for " + type + " resources");
            }
        }
    }

    private Set<String> allowedFields(String type) {
        return switch (type) {
            case "note" -> NOTE_FIELDS;
            case "link" -> LINK_FIELDS;
            case "file" -> FILE_FIELDS;
            case "email" -> EMAIL_FIELDS;
            default -> Set.of();
        };
    }

    private void validateResource(Resource resource) {
        switch (resource.getType()) {
            case "note" -> validateNote(resource);
            case "link" -> validateLink(resource);
            case "file" -> validateFile(resource);
            case "email" -> validateEmail(resource);
            default -> throw new IllegalArgumentException("Unknown resource type: " + resource.getType());
        }
    }

    private void validateNote(Resource resource) {
        requireText(resource.getTitle(), "Note resource requires title");
        validateLabelLength(resource.getTitle(), "Note resource title");
        if (resource.getBody() != null && resource.getBody().length() > MAX_NOTE_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Note resource body must be " + MAX_NOTE_BODY_LENGTH + " characters or fewer");
        }
    }

    private void validateLink(Resource resource) {
        requireText(resource.getUrl(), "Link resource requires URL");
        validateHttpUrl(resource.getUrl());
        if (resource.getUrl().length() > MAX_LINK_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Link resource URL must be " + MAX_LINK_URL_LENGTH + " characters or fewer");
        }
        if (!hasText(resource.getTitle())) {
            resource.setTitle(titleFromUrl(resource.getUrl()));
        }
        requireText(resource.getTitle(), "Link resource requires title");
        validateLabelLength(resource.getTitle(), "Link resource title");
    }

    private void validateFile(Resource resource) {
        requireText(resource.getTitle(), "File resource requires title");
        validateLabelLength(resource.getTitle(), "File resource title");
        requireText(resource.getMime(), "File resource requires MIME type");
        requireText(resource.getDataUrl(), "File resource requires data URL");
        validateFileMime(resource.getMime());
        validateDataUrl(resource.getDataUrl(), resource.getMime());
    }

    private void validateEmail(Resource resource) {
        requireText(resource.getEmail(), "Email resource requires email");
        if (!resource.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Email resource email must be valid");
        }
        if (!hasText(resource.getName())) {
            resource.setName(resource.getEmail());
        }
        validateLabelLength(resource.getName(), "Email resource name");
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateLabelLength(String value, String fieldName) {
        if (value != null && value.length() > MAX_RESOURCE_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " must be " + MAX_RESOURCE_LABEL_LENGTH + " characters or fewer");
        }
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || uri.getHost() == null) {
                throw new IllegalArgumentException("Link resource URL must be a valid http(s) URL");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Link resource URL must be a valid http(s) URL");
        }
    }

    private String titleFromUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            String host = new URI(value).getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            String normalized = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            int dotIndex = normalized.indexOf('.');
            return dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    private void validateFileMime(String mime) {
        if (!mime.startsWith("image/") && !ALLOWED_FILE_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException("File resource MIME type must be an image or PDF");
        }
    }

    private void validateDataUrl(String dataUrl, String mime) {
        String prefix = "data:" + mime + ";base64,";
        if (!dataUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("File resource data URL must match MIME type");
        }
        int commaIndex = dataUrl.indexOf(',');
        String base64 = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : "";
        int padding = 0;
        if (base64.endsWith("==")) {
            padding = 2;
        } else if (base64.endsWith("=")) {
            padding = 1;
        }
        long decodedBytes = (long) base64.length() * 3 / 4 - padding;
        if (decodedBytes > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File resource must be 5 MB or smaller");
        }
        try {
            Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("File resource data URL must be valid base64");
        }
    }
}
