package com.spiramindscape.backend.ai.chat.dto;

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
        @Pattern(regexp = "ANTHROPIC|OPENAI|MISTRAL|OLLAMA|anthropic|openai|mistral|ollama")
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
        Integer sessionRemainingSeconds
) {
    public record MessageEntry(String role, String content) {}
}
