package com.spiramindscape.backend.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmImage;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In the OpenAI-compatible format the {@code tool} role can't hold image parts,
 * so a tool-result image is followed by a separate {@code role:"user"} message
 * carrying an {@code image_url} data URL. Mistral and Gemini share this exact
 * path (same VisionSupport helper), so testing OpenAI covers all three.
 */
class OpenAiProviderVisionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiProvider provider =
            new OpenAiProvider("sk-test", "gpt-4o", null, mapper);

    @Test
    void toolResultImageAddsFollowUpUserImageMessage() throws Exception {
        LlmMessage msg = LlmMessage.toolResultWithImages(
                "call_1", "see image", List.of(new LlmImage("image/jpeg", "BBBB")));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(msg), null, List.of()));

        JsonNode messages = body.get("messages");
        assertThat(messages).hasSize(2); // the tool result + a follow-up user image message
        assertThat(messages.get(0).get("role").asText()).isEqualTo("tool");

        JsonNode userMsg = messages.get(1);
        assertThat(userMsg.get("role").asText()).isEqualTo("user");
        JsonNode imagePart = userMsg.get("content").get(0);
        assertThat(imagePart.get("type").asText()).isEqualTo("image_url");
        assertThat(imagePart.get("image_url").get("url").asText())
                .isEqualTo("data:image/jpeg;base64,BBBB");
    }

    /**
     * A directly-attached image (BUG-017) rides on the user turn as its {@code images}
     * — the same VisionSupport path adds a follow-up user {@code image_url} message.
     */
    @Test
    void userMessageImageAddsFollowUpUserImageMessage() throws Exception {
        LlmMessage msg = new LlmMessage(
                "user", "what's in this?", null, null,
                List.of(new LlmImage("image/png", "CCCC")));

        JsonNode body = mapper.readTree(provider.buildRequestBody(List.of(msg), null, List.of()));

        JsonNode messages = body.get("messages");
        assertThat(messages).hasSize(2); // the user text + a follow-up user image message
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("what's in this?");

        JsonNode imagePart = messages.get(1).get("content").get(0);
        assertThat(imagePart.get("type").asText()).isEqualTo("image_url");
        assertThat(imagePart.get("image_url").get("url").asText())
                .isEqualTo("data:image/png;base64,CCCC");
    }
}
