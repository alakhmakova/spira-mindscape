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
     * Ollama — a locally-run LLM runtime (ollama.com) exposing an
     * OpenAI-compatible API (default http://localhost:11434/v1). No real API key
     * is needed; the stored "key" holds the base URL of the Ollama server.
     */
    OLLAMA;

    public static ProviderType fromString(String value) {
        return ProviderType.valueOf(value.toUpperCase());
    }
}
