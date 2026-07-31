package com.spiramindscape.backend.ai.provider.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmImage;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tool-result carrying an image is serialized as an image block INSIDE the
 * Anthropic {@code tool_result} content array (Anthropic supports this natively,
 * so no extra message is added and role alternation is preserved).
 */
class AnthropicProviderVisionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicProvider provider =
            new AnthropicProvider("key", "claude-sonnet-4-6", null, mapper);

    @Test
    void toolResultImageBecomesImageBlockInsideToolResult() throws Exception {
        LlmMessage msg = LlmMessage.toolResultWithImages(
                "toolu_1", "see image", List.of(new LlmImage("image/png", "AAAA")));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(msg), null, List.of()));

        JsonNode messages = body.get("messages");
        assertThat(messages).hasSize(1); // single user message, no extra follow-up
        JsonNode toolResult = messages.get(0).get("content").get(0);
        assertThat(toolResult.get("type").asText()).isEqualTo("tool_result");
        JsonNode content = toolResult.get("content");
        assertThat(content.isArray()).isTrue();
        // last block is the image
        JsonNode image = content.get(content.size() - 1);
        assertThat(image.get("type").asText()).isEqualTo("image");
        assertThat(image.get("source").get("type").asText()).isEqualTo("base64");
        assertThat(image.get("source").get("media_type").asText()).isEqualTo("image/png");
        assertThat(image.get("source").get("data").asText()).isEqualTo("AAAA");
    }

    /**
     * A directly-attached image (BUG-017) on a plain user message is rendered as
     * inline image block(s) alongside the text in the content array.
     */
    @Test
    void userMessageImageBecomesInlineImageBlock() throws Exception {
        LlmMessage msg = new LlmMessage(
                "user", "what's in this?", null, null,
                List.of(new LlmImage("image/png", "DDDD")));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(msg), null, List.of()));

        JsonNode messages = body.get("messages");
        assertThat(messages).hasSize(1); // single user message carrying text + image
        JsonNode content = messages.get(0).get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        assertThat(content.get(0).get("text").asText()).isEqualTo("what's in this?");
        JsonNode image = content.get(content.size() - 1);
        assertThat(image.get("type").asText()).isEqualTo("image");
        assertThat(image.get("source").get("media_type").asText()).isEqualTo("image/png");
        assertThat(image.get("source").get("data").asText()).isEqualTo("DDDD");
    }
}
