package com.spiramindscape.backend.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the Tavily web-search API (tavily.com), an LLM-oriented
 * search service that returns ready-to-use snippets.
 *
 * <p>The Tavily API key is the user's own (BYOK), stored in the same
 * {@code ai_api_keys} table under provider {@code TAVILY} and passed in per call.
 *
 * <p>The result is formatted as a compact plain-text block suitable for feeding
 * straight back to the model as a tool result.
 */
@Service
public class TavilySearchService {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);
    private static final String ENDPOINT = "https://api.tavily.com/search";
    private static final String EXTRACT_ENDPOINT = "https://api.tavily.com/extract";
    private static final int MAX_RESULTS = 5;
    private static final int MAX_EXTRACT_CHARS = 12_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TavilySearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Runs a search and returns a formatted result block. On any failure it
     * returns a short error string (never throws) so the model can react
     * gracefully instead of the whole stream dying.
     */
    public String search(String apiKey, String query) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("max_results", MAX_RESULTS);
            body.put("include_answer", true);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Tavily search failed: HTTP {}", response.statusCode());
                return "Web search failed (HTTP " + response.statusCode() + "). Answer from your own knowledge and say the search was unavailable.";
            }

            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder sb = new StringBuilder();

            String answer = root.path("answer").asText("");
            if (!answer.isBlank()) {
                sb.append("Summary: ").append(answer).append("\n\n");
            }

            sb.append("Sources:\n");
            int i = 1;
            for (JsonNode r : root.path("results")) {
                sb.append(i++).append(". ")
                  .append(r.path("title").asText("")).append("\n   ")
                  .append(r.path("url").asText("")).append("\n   ")
                  .append(r.path("content").asText("")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Tavily search error: {}", e.getMessage());
            return "Web search error: " + e.getMessage() + ". Answer from your own knowledge and say the search was unavailable.";
        }
    }

    /**
     * Extracts the readable content of a single page via Tavily's Extract API
     * (better than a raw fetch for dynamic/cluttered pages). Returns the page
     * text, or an empty string on any failure/empty result so the caller can
     * fall back to a plain fetch. Never throws.
     */
    public String extract(String apiKey, String url) {
        if (url == null || url.isBlank()) return "";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("urls", url);
            body.put("extract_depth", "advanced");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXTRACT_ENDPOINT))
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Tavily extract failed: HTTP {}", response.statusCode());
                return "";
            }

            JsonNode results = objectMapper.readTree(response.body()).path("results");
            if (results.isArray() && !results.isEmpty()) {
                String content = results.get(0).path("raw_content").asText("");
                if (content.isBlank()) return "";
                return content.length() > MAX_EXTRACT_CHARS
                        ? content.substring(0, MAX_EXTRACT_CHARS) + "…[truncated]"
                        : content;
            }
            return "";
        } catch (Exception e) {
            log.warn("Tavily extract error: {}", e.getMessage());
            return "";
        }
    }
}
