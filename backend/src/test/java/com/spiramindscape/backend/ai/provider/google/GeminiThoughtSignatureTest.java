package com.spiramindscape.backend.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini 2.5 requires the function call's {@code thought_signature} (carried in
 * {@code extra_content}) to be echoed back on the follow-up turn, or a multi-turn
 * tool call such as {@code read_resource} is rejected. The provider must replay
 * whatever {@code extra_content} it captured (BUG-016).
 */
class GeminiThoughtSignatureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiProvider provider =
            new GeminiProvider("AIza-test", "gemini-2.5-flash", null, mapper);

    @Test
    void echoesThoughtSignatureBackOnTheAssistantToolCall() throws Exception {
        String extra = "{\"google\":{\"thought_signature\":\"SIG-123\"}}";
        ToolCall call = new ToolCall("call_1", "read_resource", "{\"id\":\"5\"}", extra);
        LlmMessage echo = LlmMessage.assistantToolCalls("", List.of(call));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(echo), null, List.of()));

        JsonNode toolCall = body.get("messages").get(0).get("tool_calls").get(0);
        assertThat(toolCall.get("extra_content").get("google").get("thought_signature").asText())
                .isEqualTo("SIG-123");
    }

    @Test
    void omitsExtraContentWhenTheToolCallHasNone() throws Exception {
        ToolCall call = new ToolCall("call_1", "read_resource", "{\"id\":\"5\"}");
        LlmMessage echo = LlmMessage.assistantToolCalls("", List.of(call));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(echo), null, List.of()));

        JsonNode toolCall = body.get("messages").get(0).get("tool_calls").get(0);
        assertThat(toolCall.has("extra_content")).isFalse();
    }
}
