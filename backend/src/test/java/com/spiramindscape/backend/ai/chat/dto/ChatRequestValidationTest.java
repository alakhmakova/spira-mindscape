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
    void rejectsAttachmentWithNeitherFileNorResource() {
        // Blank dataUrl and no resource id — a chip that carries nothing (BUG-030).
        ChatRequest req = withAttachments(List.of(
                new ChatRequest.Attachment("x", "", "")));

        assertThat(violatingPaths(req)).anyMatch(p -> p.contains("exactlyOneSource"));
    }

    @Test
    void rejectsAttachmentWithBothFileAndResource() {
        // A dataUrl AND a resource id is ambiguous — exactly one source is allowed.
        ChatRequest req = withAttachments(List.of(
                new ChatRequest.Attachment("x", "application/pdf",
                        "data:application/pdf;base64,AAAA", 42L)));

        assertThat(violatingPaths(req)).anyMatch(p -> p.contains("exactlyOneSource"));
    }

    @Test
    void acceptsAResourceAttachmentWithNoBytes() {
        // A resource attachment carries only an id; the server inlines the bytes it already holds.
        ChatRequest req = withAttachments(List.of(
                new ChatRequest.Attachment("My PDF", null, null, 42L)));

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void acceptsNoAttachments() {
        assertThat(validator.validate(withAttachments(null))).isEmpty();
    }

    @Test
    void acceptsAttachmentOnlyWithBlankMessage() {
        // A photo/resource with no typed question is a valid send — the server supplies a default
        // prompt. This used to 400 with "must not be blank" (the attachment-only send from Android).
        ChatRequest req = new ChatRequest(
                1L, "", "ANTHROPIC", "chat", List.of(), null, null,
                List.of(new ChatRequest.Attachment("p.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA")));

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void rejectsAnEmptyMessageWithNoAttachments() {
        // Nothing to send at all — blank text AND no attachments.
        ChatRequest req = new ChatRequest(1L, "  ", "ANTHROPIC", "chat", List.of(), null, null, null);

        assertThat(violatingPaths(req)).anyMatch(p -> p.contains("notEmpty"));
    }

    /**
     * A pasted job advert and a CV together run well past the old 10 000-character cap, and the
     * refusal reached the user as a bare 400 (owner, 2026-09-08). The cap is now 50 000 — the
     * longest text the app takes anywhere.
     */
    @Test
    void acceptsALongPastedMessage() {
        String pasted = "x".repeat(ChatRequest.MAX_MESSAGE_CHARS);
        ChatRequest req = new ChatRequest(
                1L, pasted, "ANTHROPIC", "chat", List.of(), null, null, null);

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void rejectsAMessageBeyondTheCapWithAReadableReason() {
        String tooLong = "x".repeat(ChatRequest.MAX_MESSAGE_CHARS + 1);
        ChatRequest req = new ChatRequest(
                1L, tooLong, "ANTHROPIC", "chat", List.of(), null, null, null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(req);
        assertThat(violatingPaths(req)).contains("message");
        // The message is what the panel now shows the user, so it has to say what to do — not
        // "size must be between 0 and 50000".
        assertThat(violations).allSatisfy(v ->
                assertThat(v.getMessage()).contains("too long"));
    }
}
