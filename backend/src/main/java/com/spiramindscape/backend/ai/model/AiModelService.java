package com.spiramindscape.backend.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the real list of available models from each provider's API.
 * Requires a valid saved key for the given provider.
 */
@Service
public class AiModelService {

    private static final Logger log = LoggerFactory.getLogger(AiModelService.class);

    private final AiKeyService keyService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiModelService(AiKeyService keyService, ObjectMapper objectMapper) {
        this.keyService = keyService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<String> listModels(String provider) {
        ProviderType type = ProviderType.fromString(provider);
        AiKeyService.StoredKey key = keyService.getKey(type)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No key configured for " + type.name()));
        return switch (type) {
            case ANTHROPIC -> fetchAnthropicModels(key.apiKey());
            case MISTRAL   -> fetchMistralModels(key.apiKey());
            case OLLAMA    -> fetchOllamaModels(key.apiKey());
            case OPENAI    -> throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED, "OpenAI provider not yet implemented");
            case TAVILY    -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tavily is a search key, not a chat provider");
        };
    }

    private List<String> fetchMistralModels(String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mistral.ai/v1/models"))
                    .header("authorization", "Bearer " + apiKey)
                    .GET().build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            }

            JsonNode root = objectMapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) ids.add(id);
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch Mistral models: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Mistral: " + e.getMessage());
        }
    }

    /**
     * Lists available Ollama models. The stored value is either an http(s) base
     * URL (local/self-hosted, no auth) or an Ollama Cloud API key (Bearer against
     * https://ollama.com). Either way we query the OpenAI-compatible
     * {@code /v1/models} endpoint.
     */
    private List<String> fetchOllamaModels(String keyOrUrl) {
        String v = keyOrUrl == null ? "" : keyOrUrl.trim();
        boolean cloud = !(v.startsWith("http://") || v.startsWith("https://"));
        String base = cloud ? "https://ollama.com" : v.replaceAll("/+$", "").replaceAll("/v1$", "");
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v1/models"))
                    .GET();
            if (cloud && !v.isBlank()) b.header("authorization", "Bearer " + v);

            HttpResponse<String> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            }

            JsonNode root = objectMapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) ids.add(id);
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch Ollama models: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Ollama at " + base + (cloud ? "" : " — is it running?")
                            + " (" + e.getMessage() + ")");
        }
    }

    private List<String> fetchAnthropicModels(String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/models"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .GET().build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            }

            JsonNode root = objectMapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) ids.add(id);
            }
            return ids;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch Anthropic models: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Anthropic: " + e.getMessage());
        }
    }
}
