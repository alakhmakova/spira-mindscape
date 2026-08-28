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
            case OPENAI    -> fetchOpenAiModels(key.apiKey());
            case MISTRAL   -> fetchMistralModels(key.apiKey());
            case GEMINI    -> fetchGeminiModels(key.apiKey());
            case COHERE    -> fetchCohereModels(key.apiKey());
            case TAVILY    -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tavily is a search key, not a chat provider");
        };
    }

    /**
     * Lists the Cohere models that can hold a conversation.
     *
     * <p>{@code GET /v1/models?endpoint=chat} — the filter is Cohere's own: their model list
     * carries the endpoints each model serves, and asking for the chat ones is the provider
     * stating what works rather than us reading meaning into names. That is the lesson of
     * BUG-059, where an unfiltered Gemini list offered embedding and text-to-speech models in a
     * chat picker.
     */
    private List<String> fetchCohereModels(String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.com/v1/models?endpoint=chat&page_size=100"))
                    .header("authorization", "Bearer " + apiKey)
                    .GET().build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            }

            JsonNode root = objectMapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("models")) {
                String id = item.path("name").asText("");
                if (!id.isBlank()) ids.add(id);
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch Cohere models", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Cohere: " + e.getMessage());
        }
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
            log.warn("Failed to fetch Mistral models", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Mistral: " + e.getMessage());
        }
    }

    private List<String> fetchOpenAiModels(String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/models"))
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
            log.warn("Failed to fetch OpenAI models", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from OpenAI: " + e.getMessage());
        }
    }

    /**
     * Lists the Gemini models that can actually hold a conversation (BUG-059).
     *
     * <p><b>Google's NATIVE endpoint, not the OpenAI-compatibility one</b>, and that is the
     * whole point. The compat layer at {@code /v1beta/openai/models} returns nothing but ids,
     * so the picker offered all 54 of them — including {@code gemini-embedding-001},
     * {@code aqa}, the {@code -tts} voices, {@code -transcribe}, {@code -image} and
     * {@code computer-use}. Roughly two in five could not answer a chat message at all, and
     * choosing one failed at the first send with a provider error rather than at the moment of
     * choosing. The native {@code /v1beta/models} returns
     * {@code supportedGenerationMethods}, which is Google stating which models take a
     * {@code generateContent} call — so the list is filtered on their answer rather than on a
     * guess about what the names mean.
     */
    private List<String> fetchGeminiModels(String apiKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200"))
                    .header("x-goog-api-key", apiKey)
                    .GET().build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            }

            JsonNode root = objectMapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode item : root.path("models")) {
                if (!supportsChat(item)) continue;
                // Google prefixes names with "models/" — strip it so the value matches what
                // the chat endpoint expects (e.g. "gemini-3.6-flash").
                String id = item.path("name").asText("");
                if (id.startsWith("models/")) id = id.substring("models/".length());
                if (!id.isBlank()) ids.add(id);
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch Gemini models", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Gemini: " + e.getMessage());
        }
    }

    /** Whether Google says this model takes the call the chat actually makes. */
    private static boolean supportsChat(JsonNode model) {
        for (JsonNode method : model.path("supportedGenerationMethods")) {
            if ("generateContent".equals(method.asText())) return true;
        }
        return false;
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
            log.warn("Failed to fetch Anthropic models", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch models from Anthropic: " + e.getMessage());
        }
    }
}
