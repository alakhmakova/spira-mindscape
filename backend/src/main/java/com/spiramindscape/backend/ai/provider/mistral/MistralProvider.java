package com.spiramindscape.backend.ai.provider.mistral;

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
 * Mistral AI chat completions with SSE streaming.
 *
 * <p>Uses the OpenAI-compatible endpoint at api.mistral.ai. The system prompt
 * is injected as the first message with {@code role=system} — Mistral does not
 * have a separate top-level system field.
 *
 * <p>Streaming format (same as OpenAI):
 * <pre>
 * data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}
 * data: [DONE]
 * </pre>
 */
public class MistralProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(MistralProvider.class);

    private static final String ENDPOINT = "https://api.mistral.ai/v1/chat/completions";
    static final String DEFAULT_MODEL = "mistral-large-latest";
    private static final int MAX_TOKENS = 8192;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MistralProvider(String apiKey, String model, HttpClient httpClient, ObjectMapper objectMapper) {
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
                    .header("authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofLines()
            );

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                onError.accept(new RuntimeException(
                        "Mistral API error " + response.statusCode() + ": " + errorBody));
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

        // Per tool-call index: accumulated id, function name and partial JSON arguments
        Map<Integer, String> toolIds = new HashMap<>();
        Map<Integer, String> toolNames = new HashMap<>();
        Map<Integer, StringBuilder> toolArgs = new HashMap<>();
        final boolean[] sawToolCalls = {false};

        try {
            lines.forEach(line -> {
                if (!line.startsWith("data: ")) return;

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;

                try {
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode choices = node.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) return;

                    JsonNode delta = choices.get(0).path("delta");

                    String text = delta.path("content").asText("");
                    if (!text.isEmpty()) onToken.accept(text);

                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                        sawToolCalls[0] = true;
                        for (JsonNode tc : toolCalls) {
                            int index = tc.path("index").asInt(0);
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty()) toolIds.put(index, id);
                            String name = tc.path("function").path("name").asText("");
                            if (!name.isEmpty()) {
                                toolNames.put(index, name);
                                toolArgs.putIfAbsent(index, new StringBuilder());
                            }
                            // Arguments usually stream as string chunks, but some
                            // responses send the whole object at once — handle both so
                            // the tool call isn't dropped (which left no proposal card).
                            JsonNode argNode = tc.path("function").path("arguments");
                            String argChunk = argNode.isTextual()
                                    ? argNode.asText()
                                    : (argNode.isMissingNode() || argNode.isNull()) ? "" : argNode.toString();
                            if (!argChunk.isEmpty()) {
                                toolArgs.computeIfAbsent(index, k -> new StringBuilder()).append(argChunk);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparseable SSE data: {}", data);
                }
            });

            int emitted = 0;
            for (Map.Entry<Integer, String> entry : toolNames.entrySet()) {
                String args = toolArgs.getOrDefault(entry.getKey(), new StringBuilder()).toString();
                if (!args.isBlank()) {
                    onToolCall.accept(new ToolCall(toolIds.get(entry.getKey()), entry.getValue(), args));
                    emitted++;
                }
            }
            log.info("Mistral stream finished: sawToolCalls={}, toolCallsEmitted={}", sawToolCalls[0], emitted);

            onComplete.run();

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private String buildRequestBody(List<LlmMessage> messages, String systemPrompt, List<ToolSpec> tools) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            allMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (LlmMessage m : messages) {
            allMessages.add(toMistralMessage(m));
        }
        body.put("messages", allMessages);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ToolSpec t : tools) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.put("parameters", t.inputSchema());
                toolList.add(Map.of("type", "function", "function", fn));
            }
            body.put("tools", toolList);
        }

        return objectMapper.writeValueAsString(body);
    }

    /**
     * Converts an {@link LlmMessage} to Mistral's (OpenAI-compatible) format,
     * expanding tool-call echoes and tool results.
     */
    private Map<String, Object> toMistralMessage(LlmMessage m) {
        Map<String, Object> msg = new LinkedHashMap<>();

        if (m.isToolResult()) {
            msg.put("role", "tool");
            msg.put("tool_call_id", m.toolResultFor());
            msg.put("content", m.content());
            return msg;
        }

        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall tc : m.toolCalls()) {
                calls.add(Map.of(
                        "id", tc.id(),
                        "type", "function",
                        "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())));
            }
            msg.put("role", "assistant");
            // Mistral rejects an assistant message with empty content AND tool_calls.
            // Omit content entirely when blank — tool_calls alone is valid.
            if (m.content() != null && !m.content().isBlank()) {
                msg.put("content", m.content());
            }
            msg.put("tool_calls", calls);
            return msg;
        }

        msg.put("role", m.role());
        msg.put("content", m.content());
        return msg;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.MISTRAL;
    }
}
