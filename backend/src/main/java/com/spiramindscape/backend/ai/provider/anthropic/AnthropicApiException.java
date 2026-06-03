package com.spiramindscape.backend.ai.provider.anthropic;

/** Thrown when the Anthropic API returns a non-200 response or an error event. */
public class AnthropicApiException extends RuntimeException {

    private final int statusCode;

    public AnthropicApiException(int statusCode, String message) {
        super("Anthropic API error [" + statusCode + "]: " + message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
