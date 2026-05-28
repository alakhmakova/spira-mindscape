package com.spiramindscape.backend.ai.provider;

/**
 * Supported AI provider types. Stored as a VARCHAR in the database so new
 * providers can be added without schema changes.
 */
public enum ProviderType {
    ANTHROPIC,
    OPENAI,
    MISTRAL;

    public static ProviderType fromString(String value) {
        return ProviderType.valueOf(value.toUpperCase());
    }
}
