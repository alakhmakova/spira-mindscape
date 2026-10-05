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

    public static ProviderType fromString(String value) {
        return ProviderType.valueOf(value.toUpperCase());
    }
}
