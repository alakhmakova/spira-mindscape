package com.spiramindscape.backend.ai.provider;

/**
 * Supported AI provider types. Stored as a VARCHAR in the database so new
 * providers can be added without schema changes.
 */
public enum ProviderType {
    ANTHROPIC,
    OPENAI,
    MISTRAL,
    /**
     * Not an LLM provider — used only as a key slot for the Tavily web-search
     * API. Stored in the same {@code ai_api_keys} table (BYOK). Never passed to
     * {@code LlmProviderFactory}.
     */
    TAVILY,
    /**
     * Google Gemini — accessed via its OpenAI-compatibility layer
     * ({@code generativelanguage.googleapis.com/v1beta/openai}). The stored key
     * is a Google AI Studio API key (prefix {@code AIza}).
     */
    GEMINI,
    /**
     * Cohere — the native Chat v2 API ({@code api.cohere.com/v2/chat}), not an
     * OpenAI-compatibility layer: its stream is typed events rather than deltas on a choice.
     * The stored key is a Cohere API key.
     */
    COHERE;

    /**
     * Parse a provider name from a request. Case-insensitive.
     *
     * <p>An unknown name is the CLIENT's mistake, so it answers <b>400</b> and says what
     * is accepted. It used to be a bare {@code valueOf}, whose
     * {@code IllegalArgumentException} surfaced as a 500 with a {@code Reference:} and
     * nothing else — every key endpoint (save, model, delete) turned a typo into "the
     * server is broken". One caller wrapped it and the rest did not; doing it here means
     * none of them can forget.
     */
    public static ProviderType fromString(String value) {
        if (value != null) {
            for (ProviderType t : values()) {
                if (t.name().equalsIgnoreCase(value.strip())) return t;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unknown provider \"" + value + "\". Expected one of: "
                        + java.util.Arrays.stream(values()).map(Enum::name)
                                .collect(java.util.stream.Collectors.joining(", ")));
    }
}
