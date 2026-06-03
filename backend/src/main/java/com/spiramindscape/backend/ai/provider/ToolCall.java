package com.spiramindscape.backend.ai.provider;

/**
 * A tool invocation emitted by the model during streaming.
 * {@code argumentsJson} is the complete, valid JSON object the model produced
 * as the tool's arguments (assembled from streamed deltas).
 * {@code id} is the provider-assigned call id, needed to echo the tool result
 * back to the model in a follow-up request (the agentic loop).
 */
public record ToolCall(String id, String name, String argumentsJson) {}
