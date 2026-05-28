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
        @Pattern(regexp = "ANTHROPIC|OPENAI|MISTRAL|anthropic|openai|mistral")
        String provider,

        /**
         * Optional conversation history to maintain context across messages.
         * Each entry has role ("user"|"assistant") and content.
         */
        java.util.List<MessageEntry> history
) {
    public record MessageEntry(String role, String content) {}
}
