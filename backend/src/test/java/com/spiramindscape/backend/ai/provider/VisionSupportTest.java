package com.spiramindscape.backend.ai.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared vision helpers: MIME gating, data-URL parsing, and the two wire formats. */
class VisionSupportTest {

    @Test
    void acceptsCommonImageMimesRejectsOthers() {
        assertThat(VisionSupport.isVisionMime("image/png")).isTrue();
        assertThat(VisionSupport.isVisionMime("IMAGE/JPEG")).isTrue();
        assertThat(VisionSupport.isVisionMime("image/svg+xml")).isFalse();
        assertThat(VisionSupport.isVisionMime("application/pdf")).isFalse();
        assertThat(VisionSupport.isVisionMime(null)).isFalse();
    }

    @Test
    void parsesDataUrlIntoMimeAndPayload() {
        LlmImage img = VisionSupport.fromDataUrl("data:image/png;base64,AAAABBBB");
        assertThat(img).isNotNull();
        assertThat(img.mediaType()).isEqualTo("image/png");
        assertThat(img.base64Data()).isEqualTo("AAAABBBB");
    }

    @Test
    void rejectsNonImageOrMalformedDataUrl() {
        assertThat(VisionSupport.fromDataUrl("data:image/svg+xml;base64,AAAA")).isNull();
        assertThat(VisionSupport.fromDataUrl("data:image/png;base64,")).isNull();
        assertThat(VisionSupport.fromDataUrl("https://example.com/x.png")).isNull();
        assertThat(VisionSupport.fromDataUrl(null)).isNull();
    }

    /**
     * Which model may be shown a picture (BUG-027). Getting this wrong is not cosmetic: an
     * image sent to a blind model is dropped by the provider while the turn still announces
     * it, and the model then answers as if it had seen it — a confident, fabricated reading.
     */
    @Test
    void mistralVisionIsAnAllowListAndTheDefaultIsBlind() {
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "pixtral-large-latest")).isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "mistral-medium-latest")).isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "mistral-small-latest")).isTrue();

        // The app's default, and what the user had selected when this was reported.
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "mistral-large-latest")).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "codestral-latest")).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "ministral-8b-latest")).isFalse();
        // No model stored → the provider default (mistral-large-latest), which is blind.
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, null)).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.MISTRAL, "  ")).isFalse();
    }

    @Test
    void anthropicOpenAiAndGeminiChatModelsCanSee() {
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.ANTHROPIC, "claude-sonnet-4-6")).isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.ANTHROPIC, null)).isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.OPENAI, "gpt-4o")).isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.GEMINI, "gemini-2.5-flash")).isTrue();

        assertThat(VisionSupport.modelCanSeeImages(ProviderType.OPENAI, "gpt-3.5-turbo")).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.OPENAI, "text-embedding-3-large")).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.TAVILY, "whatever")).isFalse();
    }

    @Test
    void buildsAnthropicImageBlock() {
        List<Map<String, Object>> blocks =
                VisionSupport.anthropicImageBlocks(List.of(new LlmImage("image/png", "AAAA")));
        Map<String, Object> block = blocks.get(0);
        assertThat(block.get("type")).isEqualTo("image");
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) block.get("source");
        assertThat(source.get("type")).isEqualTo("base64");
        assertThat(source.get("media_type")).isEqualTo("image/png");
        assertThat(source.get("data")).isEqualTo("AAAA");
    }

    @Test
    void buildsOpenAiImageUserMessageWithDataUrl() {
        Map<String, Object> msg =
                VisionSupport.openAiImageUserMessage(List.of(new LlmImage("image/jpeg", "BBBB")));
        assertThat(msg.get("role")).isEqualTo("user");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) msg.get("content");
        Map<String, Object> imagePart = parts.get(0);
        assertThat(imagePart.get("type")).isEqualTo("image_url");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageUrl = (Map<String, Object>) imagePart.get("image_url");
        assertThat(imageUrl.get("url")).isEqualTo("data:image/jpeg;base64,BBBB");
    }
}
