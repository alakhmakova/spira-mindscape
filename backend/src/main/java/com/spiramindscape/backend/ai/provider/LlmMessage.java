package com.spiramindscape.backend.ai.provider;

import java.util.List;

/**
 * A single message in an LLM conversation.
 *
 * <p>Most messages are plain text ({@code role} = "user"/"assistant"). To
 * support the web-search agentic loop, a message may also carry:
 * <ul>
 *   <li>{@code toolCalls} — non-null on an assistant message that echoes the
 *       model's tool calls back into the next request</li>
 *   <li>{@code toolResultFor} — non-null on a tool-result message ({@code role}
 *       = "tool"), giving the id of the tool call this answers</li>
 * </ul>
 */
public record LlmMessage(String role, String content, List<ToolCall> toolCalls, String toolResultFor) {

    /** Plain text message. */
    public LlmMessage(String role, String content) {
        this(role, content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }

    /** Assistant message echoing the model's tool calls (for the follow-up request). */
    public static LlmMessage assistantToolCalls(String text, List<ToolCall> calls) {
        return new LlmMessage("assistant", text == null ? "" : text, calls, null);
    }

    /** Result of executing a tool, fed back to the model. */
    public static LlmMessage toolResult(String toolCallId, String resultText) {
        return new LlmMessage("tool", resultText, null, toolCallId);
    }

    public boolean isToolResult() {
        return toolResultFor != null;
    }
}
