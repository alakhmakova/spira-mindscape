package com.spiramindscape.backend.ai.provider;

import java.util.Map;

/**
 * A tool/function the model may call. The {@code inputSchema} is a JSON Schema
 * object describing the tool's parameters — used as {@code input_schema} for
 * Anthropic and {@code parameters} for OpenAI/Mistral.
 */
public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {}
