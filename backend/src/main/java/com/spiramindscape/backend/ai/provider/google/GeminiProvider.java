package com.spiramindscape.backend.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import com.spiramindscape.backend.ai.provider.VisionSupport;
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
 * Google Gemini chat completions with SSE streaming.
 *
 * <p>Uses Gemini's OpenAI-compatibility layer at
 * {@code generativelanguage.googleapis.com/v1beta/openai} — so request, tool
 * calling and streaming share the OpenAI/Mistral schema rather than Gemini's
 * native JSON. The API key is a Google AI Studio key (prefix {@code AIza}),
 * sent as a Bearer token; the system prompt is injected as the first
 * {@code role=system} message.
 *
 * <p>Streaming format (OpenAI-compatible):
 * <pre>
 * data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}
 * data: [DONE]
 * </pre>
 */
public class GeminiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final int MAX_TOKENS = 8192;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiProvider(String apiKey, String model, HttpClient httpClient, ObjectMapper objectMapper) {
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
                        "Gemini API error " + response.statusCode() + ": " + errorBody));
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
        // Gemini 2.5 attaches an `extra_content` (holding the function call's
        // thought_signature) that must be echoed back verbatim on the follow-up
        // turn, or a multi-turn tool call (read_resource / web_search) is rejected.
        Map<Integer, String> toolExtra = new HashMap<>();
        final boolean[] sawToolCalls = {false};
        final String[] firstToolChunk = {null}; // kept only to diagnose a missing signature

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
                            // Raw chunk logging (DEBUG) so we can confirm where Gemini
                            // puts the thought_signature across API changes.
                            log.debug("Gemini tool_call chunk: {}", tc);
                            if (firstToolChunk[0] == null) firstToolChunk[0] = tc.toString();
                            int index = tc.path("index").asInt(0);
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty()) toolIds.put(index, id);
                            // Capture the thought_signature carrier if present on any chunk
                            // for this call. Gemini surfaces it as `extra_content` on the
                            // tool call in the OpenAI-compatibility layer.
                            JsonNode extra = tc.path("extra_content");
                            if (extra.isObject() && !extra.isEmpty()) {
                                toolExtra.put(index, extra.toString());
                            }
                            String name = tc.path("function").path("name").asText("");
                            if (!name.isEmpty()) {
                                toolNames.put(index, name);
                                toolArgs.putIfAbsent(index, new StringBuilder());
                            }
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
                    // Gemini's OpenAI-compat stream sometimes omits the tool-call id.
                    // Without one, the agentic loop's follow-up request would send a
                    // tool result with a null tool_call_id and Gemini would reject it —
                    // breaking web_search / read_url / read_resource. Synthesize a stable
                    // id per index so the assistant echo and its tool_result still pair.
                    String id = toolIds.get(entry.getKey());
                    if (id == null || id.isBlank()) id = "gemini_call_" + entry.getKey();
                    onToolCall.accept(new ToolCall(
                            id, entry.getValue(), args, toolExtra.get(entry.getKey())));
                    emitted++;
                }
            }
            log.info("Gemini stream finished: sawToolCalls={}, toolCallsEmitted={}, withThoughtSignature={}",
                    sawToolCalls[0], emitted, toolExtra.size());
            // If Gemini sent tool calls but we found no thought_signature, dump the raw
            // shape once so we can see exactly where it lives (diagnostic for BUG-016).
            if (sawToolCalls[0] && toolExtra.isEmpty() && firstToolChunk[0] != null) {
                log.warn("Gemini tool call had no extra_content/thought_signature; raw chunk: {}",
                        firstToolChunk[0]);
            }

            onComplete.run();

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    String buildRequestBody(List<LlmMessage> messages, String systemPrompt, List<ToolSpec> tools) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            allMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (LlmMessage m : messages) {
            allMessages.add(toGeminiMessage(m));
            // The tool role can't hold image parts here, so a read_resource that
            // returned an image is followed by a user message carrying the image.
            if (m.hasImages()) {
                allMessages.add(VisionSupport.openAiImageUserMessage(m.images()));
            }
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
     * Converts an {@link LlmMessage} to the OpenAI-compatible format Gemini
     * expects, expanding tool-call echoes and tool results.
     */
    private Map<String, Object> toGeminiMessage(LlmMessage m) {
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
                // function.arguments must be a JSON-encoded STRING (OpenAI spec).
                // Default to "{}" when empty so it is always valid JSON.
                String args = (tc.argumentsJson() == null || tc.argumentsJson().isBlank())
                        ? "{}"
                        : tc.argumentsJson();
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", tc.name());
                fn.put("arguments", args);
                Map<String, Object> call = new LinkedHashMap<>();
                if (tc.id() != null) call.put("id", tc.id());
                call.put("type", "function");
                call.put("function", fn);
                // Echo Gemini's thought_signature (carried in extra_content) back
                // verbatim — required for a multi-turn tool call to be accepted.
                if (tc.extraContentJson() != null && !tc.extraContentJson().isBlank()) {
                    try {
                        call.put("extra_content",
                                objectMapper.readValue(tc.extraContentJson(), Map.class));
                    } catch (Exception e) {
                        log.debug("Could not attach Gemini extra_content: {}", e.getMessage());
                    }
                }
                calls.add(call);
            }
            msg.put("role", "assistant");
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
        return ProviderType.GEMINI;
    }
}
