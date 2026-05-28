package com.spiramindscape.backend.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.anthropic.AnthropicProvider;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Constructs the correct {@link LlmProvider} for a given provider type and
 * API key. Each call returns a new, stateless provider instance.
 */
@Component
public class LlmProviderFactory {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LlmProviderFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @param providerType which provider to use
     * @param apiKey       the decrypted API key
     * @param model        optional model override; if null, the provider's
     *                     default model is used
     */
    public LlmProvider create(ProviderType providerType, String apiKey, String model) {
        return switch (providerType) {
            case ANTHROPIC -> new AnthropicProvider(apiKey, model, httpClient, objectMapper);
            case OPENAI    -> throw new UnsupportedOperationException("OpenAI provider not yet implemented");
            case MISTRAL   -> throw new UnsupportedOperationException("Mistral provider not yet implemented");
        };
    }
}
