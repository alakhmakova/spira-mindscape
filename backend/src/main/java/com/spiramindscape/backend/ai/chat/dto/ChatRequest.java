package com.spiramindscape.backend.ai.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        /** Goal ID to scope the conversation. Null means global (all-goals) context. */
        Long goalId,

        @NotBlank
        @Size(max = 10_000)
        String message,

        /**
         * Which provider to use. Defaults to {@code ANTHROPIC} if omitted.
         * Must match a key that the user has previously saved.
         */
        @Pattern(regexp = "ANTHROPIC|OPENAI|MISTRAL|GEMINI|anthropic|openai|mistral|gemini")
        String provider,

        /**
         * Session type — controls which system prompt is used.
         * {@code "chat"} (default): regular assistant mode.
         * {@code "grow"}: GROW coaching session mode.
         */
        String sessionType,

        /**
         * Optional conversation history to maintain context across messages.
         * Each entry has role ("user"|"assistant") and content.
         */
        java.util.List<MessageEntry> history,

        /**
         * GROW only: the session length the user chose, in minutes. Lets the
         * coach pace the conversation instead of being cut off by the UI timer.
         */
        Integer sessionTotalMinutes,

        /**
         * GROW only: seconds left on the session timer when this message was
         * sent. {@code <= 0} means time is up — the coach must close the
         * session in this reply.
         */
        Integer sessionRemainingSeconds,

        /**
         * Files attached directly to THIS message (images, PDFs, DOCX) — a
         * lightweight alternative to saving a Resource. Ephemeral: they inform
         * only this turn and are never persisted. Capped in count so a request
         * can't smuggle in a huge payload.
         */
        @Valid
        @Size(max = 6, message = "At most 6 files can be attached to a message")
        java.util.List<Attachment> attachments
) {
    /** Backwards-compatible constructor for callers/tests that predate attachments. */
    public ChatRequest(
            Long goalId,
            String message,
            String provider,
            String sessionType,
            java.util.List<MessageEntry> history,
            Integer sessionTotalMinutes,
            Integer sessionRemainingSeconds) {
        this(goalId, message, provider, sessionType, history,
                sessionTotalMinutes, sessionRemainingSeconds, null);
    }

    public record MessageEntry(String role, String content) {}

    /**
     * One directly-attached file. {@code dataUrl} is a
     * {@code data:<mime>;base64,<payload>} URL (same shape as a stored file
     * resource); {@code mime} decides how it is used (image → vision, PDF/DOCX →
     * extracted text). Size is bounded to keep a request within sane limits
     * (~5 MB of file ≈ 6.8 MB base64; the cap leaves headroom).
     */
    public record Attachment(
            @Size(max = 300) String name,
            @NotBlank @Size(max = 200) String mime,
            @NotBlank @Size(max = 7_500_000, message = "Attached file is too large") String dataUrl
    ) {}
}
