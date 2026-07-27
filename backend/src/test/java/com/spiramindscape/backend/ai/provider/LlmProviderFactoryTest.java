package com.spiramindscape.backend.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.anthropic.AnthropicProvider;
import com.spiramindscape.backend.ai.provider.google.GeminiProvider;
import com.spiramindscape.backend.ai.provider.mistral.MistralProvider;
import com.spiramindscape.backend.ai.provider.openai.OpenAiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The factory must return the correct provider implementation for each chat
 * {@link ProviderType}, and each provider must report its own type. OpenAI is
 * now a real provider (previously a stub) and Gemini replaces the removed
 * Ollama.
 */
class LlmProviderFactoryTest {

    private final LlmProviderFactory factory = new LlmProviderFactory(new ObjectMapper());

    @Test
    void createsAnthropicProvider() {
        LlmProvider provider = factory.create(ProviderType.ANTHROPIC, "key", null);
        assertThat(provider).isInstanceOf(AnthropicProvider.class);
        assertThat(provider.providerType()).isEqualTo(ProviderType.ANTHROPIC);
    }

    @Test
    void createsOpenAiProvider() {
        LlmProvider provider = factory.create(ProviderType.OPENAI, "sk-test", null);
        assertThat(provider).isInstanceOf(OpenAiProvider.class);
        assertThat(provider.providerType()).isEqualTo(ProviderType.OPENAI);
    }

    @Test
    void createsMistralProvider() {
        LlmProvider provider = factory.create(ProviderType.MISTRAL, "key", null);
        assertThat(provider).isInstanceOf(MistralProvider.class);
        assertThat(provider.providerType()).isEqualTo(ProviderType.MISTRAL);
    }

    @Test
    void createsGeminiProvider() {
        LlmProvider provider = factory.create(ProviderType.GEMINI, "AIza-test", null);
        assertThat(provider).isInstanceOf(GeminiProvider.class);
        assertThat(provider.providerType()).isEqualTo(ProviderType.GEMINI);
    }

    @Test
    void tavilyIsNotAChatProvider() {
        assertThatThrownBy(() -> factory.create(ProviderType.TAVILY, "key", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ollamaIsNoLongerAKnownProvider() {
        // Ollama was removed in favor of Gemini — the enum value must not resolve.
        assertThatThrownBy(() -> ProviderType.fromString("OLLAMA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geminiResolvesFromLowercaseString() {
        assertThat(ProviderType.fromString("gemini")).isEqualTo(ProviderType.GEMINI);
    }
}
