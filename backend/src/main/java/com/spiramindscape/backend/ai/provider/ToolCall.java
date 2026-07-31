package com.spiramindscape.backend.ai.provider;

/**
 * A tool invocation emitted by the model during streaming.
 * {@code argumentsJson} is the complete, valid JSON object the model produced
 * as the tool's arguments (assembled from streamed deltas).
 * {@code id} is the provider-assigned call id, needed to echo the tool result
 * back to the model in a follow-up request (the agentic loop).
 *
 * <p>{@code extraContentJson} carries provider-specific metadata that MUST be
 * echoed back verbatim on the follow-up turn. For Gemini 2.5 this is the
 * {@code extra_content} that holds the function call's {@code thought_signature}
 * — omitting it makes Gemini reject the tool-call echo in a multi-turn
 * (agentic) loop such as {@code read_resource}. Null for providers that don't
 * use it (Anthropic/OpenAI/Mistral).
 */
public record ToolCall(String id, String name, String argumentsJson, String extraContentJson) {

    /** A tool call with no provider-specific echo metadata. */
    public ToolCall(String id, String name, String argumentsJson) {
        this(id, name, argumentsJson, null);
    }
}
