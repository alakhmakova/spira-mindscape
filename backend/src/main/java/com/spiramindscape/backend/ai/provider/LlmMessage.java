package com.spiramindscape.backend.ai.provider;

/**
 * A single message in an LLM conversation.
 * Role is always {@code "user"} or {@code "assistant"}.
 */
public record LlmMessage(String role, String content) {

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
