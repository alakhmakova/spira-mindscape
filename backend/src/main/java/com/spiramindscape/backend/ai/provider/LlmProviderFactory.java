package com.spiramindscape.backend.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.anthropic.AnthropicProvider;
import com.spiramindscape.backend.ai.provider.mistral.MistralProvider;
import com.spiramindscape.backend.ai.provider.ollama.OllamaProvider;
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
            case MISTRAL   -> new MistralProvider(apiKey, model, httpClient, objectMapper);
            case OLLAMA    -> new OllamaProvider(apiKey, model, httpClient, objectMapper);
            case OPENAI    -> throw new UnsupportedOperationException("OpenAI provider not yet implemented");
            case TAVILY    -> throw new UnsupportedOperationException("Tavily is a search key, not a chat provider");
        };
    }
}
