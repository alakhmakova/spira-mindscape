package com.spiramindscape.backend.ai.provider.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Anthropic Messages API implementation with server-sent-event (SSE) streaming.
 *
 * <p>Reference: <a href="https://docs.anthropic.com/en/api/messages">
 * Anthropic Messages API</a>
 *
 * <p>Streaming events:
 * <ul>
 *   <li>{@code message_start} — ignored</li>
 *   <li>{@code content_block_start} — ignored</li>
 *   <li>{@code content_block_delta} with {@code text_delta} — token forwarded via onToken</li>
 *   <li>{@code message_stop} — triggers onComplete</li>
 *   <li>HTTP error (non-200) — triggers onError</li>
 * </ul>
 */
public class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 8192;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(String apiKey, String model, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void streamChat(
            List<LlmMessage> messages,
            String systemPrompt,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        try {
            String bodyJson = buildRequestBody(messages, systemPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofLines()
            );

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                onError.accept(new AnthropicApiException(response.statusCode(), errorBody));
                return;
            }

            processStream(response.body(), onToken, onComplete, onError);

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private void processStream(
            java.util.stream.Stream<String> lines,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        try {
            lines.forEach(line -> {
                if (!line.startsWith("data: ")) return;

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;

                try {
                    JsonNode node = objectMapper.readTree(data);
                    String type = node.path("type").asText();

                    if ("content_block_delta".equals(type)) {
                        String deltaType = node.path("delta").path("type").asText();
                        if ("text_delta".equals(deltaType)) {
                            String text = node.path("delta").path("text").asText();
                            if (!text.isEmpty()) {
                                onToken.accept(text);
                            }
                        }
                    } else if ("error".equals(type)) {
                        String message = node.path("error").path("message").asText("Unknown error");
                        onError.accept(new AnthropicApiException(0, message));
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparseable SSE data: {}", data);
                }
            });

            onComplete.run();

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private String buildRequestBody(List<LlmMessage> messages, String systemPrompt) throws Exception {
        // Use LinkedHashMap to guarantee key order in JSON output
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        List<Map<String, String>> anthropicMessages = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();
        body.put("messages", anthropicMessages);

        return objectMapper.writeValueAsString(body);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ANTHROPIC;
    }
}
