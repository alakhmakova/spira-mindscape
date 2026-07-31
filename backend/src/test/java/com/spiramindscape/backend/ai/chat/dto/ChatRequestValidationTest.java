package com.spiramindscape.backend.ai.chat.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The attachment boundary (BUG-017): a chat request can't smuggle in more than
 * the allowed number of files, an oversized file, or a malformed attachment. The
 * controller applies {@code @Valid}, so these violations reject the request
 * before any file is processed.
 */
class ChatRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static ChatRequest.Attachment tinyImage() {
        return new ChatRequest.Attachment("a.png", "image/png", "data:image/png;base64,AAAA");
    }

    private static ChatRequest withAttachments(List<ChatRequest.Attachment> attachments) {
        return new ChatRequest(1L, "hello", "ANTHROPIC", "chat", List.of(), null, null, attachments);
    }

    private static Set<String> violatingPaths(ChatRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsUpToSixValidAttachments() {
        ChatRequest req = withAttachments(List.of(
                tinyImage(), tinyImage(), tinyImage(), tinyImage(), tinyImage(), tinyImage()));

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void rejectsMoreThanSixAttachments() {
        ChatRequest req = withAttachments(List.of(
                tinyImage(), tinyImage(), tinyImage(), tinyImage(),
                tinyImage(), tinyImage(), tinyImage())); // 7

        assertThat(violatingPaths(req)).contains("attachments");
    }

    @Test
    void rejectsAnOversizedAttachment() {
        String huge = "data:application/pdf;base64," + "A".repeat(7_500_001);
        ChatRequest req = withAttachments(List.of(
                new ChatRequest.Attachment("big.pdf", "application/pdf", huge)));

        Set<String> paths = violatingPaths(req);
        assertThat(paths).anyMatch(p -> p.contains("dataUrl"));
    }

    @Test
    void rejectsAttachmentWithBlankMimeOrDataUrl() {
        ChatRequest req = withAttachments(List.of(
                new ChatRequest.Attachment("x", "", "")));

        Set<String> paths = violatingPaths(req);
        assertThat(paths).anyMatch(p -> p.contains("mime"));
        assertThat(paths).anyMatch(p -> p.contains("dataUrl"));
    }

    @Test
    void acceptsNoAttachments() {
        assertThat(validator.validate(withAttachments(null))).isEmpty();
    }
}
