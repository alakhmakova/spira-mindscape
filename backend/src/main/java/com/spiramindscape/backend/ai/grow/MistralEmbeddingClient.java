package com.spiramindscape.backend.ai.grow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Mistral's embeddings API ({@code mistral-embed}, 1024-dim,
 * multilingual — Russian queries match English book passages).
 *
 * <p>Embeddings are a separate API from chat: Anthropic offers none, so the
 * GROW coaching library is pinned to a Mistral key regardless of which
 * provider the user chats with. The key is the user's own (BYOK), passed per
 * call — same pattern as {@link com.spiramindscape.backend.ai.search.TavilySearchService}.
 */
@Component
public class MistralEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(MistralEmbeddingClient.class);
    private static final String ENDPOINT = "https://api.mistral.ai/v1/embeddings";

    /** 16 × ~400-token chunks ≈ 6.5k tokens — comfortably under per-request limits. */
    static final int BATCH_SIZE = 16;
    static final String MODEL = "mistral-embed";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MistralEmbeddingClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Embeds the inputs in order (batching internally). Throws on failure —
     * GROW must refuse rather than degrade, so callers never get partial
     * silence. The exception message carries the provider's response body so
     * the existing {@code friendlyError} extraction can surface it.
     */
    public List<float[]> embed(List<String> inputs, String apiKey) {
        List<float[]> all = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += BATCH_SIZE) {
            List<String> batch = inputs.subList(from, Math.min(from + BATCH_SIZE, inputs.size()));
            all.addAll(embedBatch(batch, apiKey));
        }
        return all;
    }

    private List<float[]> embedBatch(List<String> batch, String apiKey) {
        try {
            HttpResponse<String> response = send(batch, apiKey);
            if (response.statusCode() == 429) {
                log.info("Mistral embeddings rate-limited, retrying once after backoff");
                Thread.sleep(2_000);
                response = send(batch, apiKey);
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Mistral embeddings failed: HTTP "
                        + response.statusCode() + " " + response.body());
            }
            return parse(response.body(), batch.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Mistral embeddings interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Mistral embeddings error: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(List<String> batch, String apiKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("input", batch);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<float[]> parse(String responseBody, int expected) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        List<float[]> vectors = new ArrayList<>(expected);
        for (JsonNode entry : data) {
            JsonNode emb = entry.path("embedding");
            float[] v = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) v[i] = (float) emb.get(i).asDouble();
            vectors.add(v);
        }
        if (vectors.size() != expected) {
            throw new RuntimeException("Mistral embeddings returned " + vectors.size()
                    + " vectors for " + expected + " inputs");
        }
        return vectors;
    }
}
