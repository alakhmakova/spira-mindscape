package com.spiramindscape.backend.ai.provider.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
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
            List<ToolSpec> tools,
            Consumer<String> onToken,
            Consumer<ToolCall> onToolCall,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        try {
            String bodyJson = buildRequestBody(messages, systemPrompt, tools);

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

            processStream(response.body(), onToken, onToolCall, onComplete, onError);

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private void processStream(
            java.util.stream.Stream<String> lines,
            Consumer<String> onToken,
            Consumer<ToolCall> onToolCall,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        // Per content-block index: accumulated tool-call id, name and partial JSON args
        Map<Integer, String> toolIds = new HashMap<>();
        Map<Integer, String> toolNames = new HashMap<>();
        Map<Integer, StringBuilder> toolArgs = new HashMap<>();

        try {
            lines.forEach(line -> {
                if (!line.startsWith("data: ")) return;

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;

                try {
                    JsonNode node = objectMapper.readTree(data);
                    String type = node.path("type").asText();

                    switch (type) {
                        case "content_block_start" -> {
                            JsonNode block = node.path("content_block");
                            if ("tool_use".equals(block.path("type").asText())) {
                                int index = node.path("index").asInt();
                                toolIds.put(index, block.path("id").asText());
                                toolNames.put(index, block.path("name").asText());
                                toolArgs.put(index, new StringBuilder());
                            }
                        }
                        case "content_block_delta" -> {
                            String deltaType = node.path("delta").path("type").asText();
                            if ("text_delta".equals(deltaType)) {
                                String text = node.path("delta").path("text").asText();
                                if (!text.isEmpty()) onToken.accept(text);
                            } else if ("input_json_delta".equals(deltaType)) {
                                int index = node.path("index").asInt();
                                StringBuilder sb = toolArgs.get(index);
                                if (sb != null) sb.append(node.path("delta").path("partial_json").asText());
                            }
                        }
                        case "error" -> {
                            String message = node.path("error").path("message").asText("Unknown error");
                            onError.accept(new AnthropicApiException(0, message));
                        }
                        default -> { /* message_start, content_block_stop, message_stop, ping — ignored */ }
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparseable SSE data: {}", data);
                }
            });

            // Emit any tool calls collected during the stream
            for (Map.Entry<Integer, String> entry : toolNames.entrySet()) {
                String args = toolArgs.getOrDefault(entry.getKey(), new StringBuilder()).toString();
                if (!args.isBlank()) {
                    onToolCall.accept(new ToolCall(toolIds.get(entry.getKey()), entry.getValue(), args));
                }
            }

            onComplete.run();

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private String buildRequestBody(List<LlmMessage> messages, String systemPrompt, List<ToolSpec> tools) throws Exception {
        // Use LinkedHashMap to guarantee key order in JSON output
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        List<Map<String, Object>> anthropicMessages = new ArrayList<>();
        for (LlmMessage m : messages) {
            anthropicMessages.add(toAnthropicMessage(m));
        }
        body.put("messages", anthropicMessages);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ToolSpec t : tools) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", t.name());
                tool.put("description", t.description());
                tool.put("input_schema", t.inputSchema());
                toolList.add(tool);
            }
            body.put("tools", toolList);
        }

        return objectMapper.writeValueAsString(body);
    }

    /**
     * Converts an {@link LlmMessage} to Anthropic's message format, expanding
     * tool-call echoes and tool results into the content-block representation.
     */
    private Map<String, Object> toAnthropicMessage(LlmMessage m) throws Exception {
        Map<String, Object> msg = new LinkedHashMap<>();

        if (m.isToolResult()) {
            // tool result → a user message carrying a tool_result block
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_result");
            block.put("tool_use_id", m.toolResultFor());
            block.put("content", m.content());
            msg.put("role", "user");
            msg.put("content", List.of(block));
            return msg;
        }

        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            // assistant echo: optional text + one tool_use block per call
            List<Map<String, Object>> blocks = new ArrayList<>();
            if (m.content() != null && !m.content().isBlank()) {
                blocks.add(Map.of("type", "text", "text", m.content()));
            }
            for (ToolCall tc : m.toolCalls()) {
                Map<String, Object> use = new LinkedHashMap<>();
                use.put("type", "tool_use");
                use.put("id", tc.id());
                use.put("name", tc.name());
                use.put("input", objectMapper.readValue(tc.argumentsJson(), Map.class));
                blocks.add(use);
            }
            msg.put("role", "assistant");
            msg.put("content", blocks);
            return msg;
        }

        // plain text
        msg.put("role", m.role());
        msg.put("content", m.content());
        return msg;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ANTHROPIC;
    }
}
