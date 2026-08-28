package com.spiramindscape.backend.ai.provider.cohere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmHttp;
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
 * Cohere Chat v2 ({@code POST https://api.cohere.com/v2/chat}), streaming.
 *
 * <p>Reference: <a href="https://docs.cohere.com/reference/chat">Cohere Chat API</a>.
 *
 * <h2>How it differs from the other four</h2>
 *
 * <p>The request is close to OpenAI's — {@code model}, {@code messages}, {@code tools} in the
 * {@code {"type":"function","function":{…}}} shape, {@code stream: true} — with two differences
 * that matter:
 *
 * <ul>
 *   <li>An assistant message's {@code tool_calls[].function.arguments} is a <b>JSON-encoded
 *       string</b>, as with OpenAI, but a <b>tool result</b> message carries
 *       {@code tool_call_id} and plain {@code content}.</li>
 *   <li><b>The stream is typed events, not deltas on a choice.</b> Cohere sends
 *       {@code message-start}, {@code content-start}, {@code content-delta},
 *       {@code tool-call-start}, {@code tool-call-delta}, {@code tool-call-end},
 *       {@code message-end}, and the text sits at
 *       {@code delta.message.content.text} rather than {@code choices[0].delta.content}.</li>
 * </ul>
 *
 * <h2>Why the framing is read from the payload, not the {@code event:} line</h2>
 *
 * <p>Each frame carries its own {@code "type"} field <i>inside</i> the JSON, and the parser
 * switches on that. Cohere also emits an {@code event:} line, but relying on it would mean this
 * provider breaks if that ever stops — while the {@code type} field is part of the documented
 * payload. Reading only {@code data:} lines is also what the other four providers do, so the
 * shape of this class stays comparable to theirs.
 *
 * <h2>Vision</h2>
 *
 * <p>Cohere takes images as content parts in <b>exactly</b> OpenAI's shape —
 * {@code {"type":"image_url","image_url":{"url":"data:image/png;base64,…"}}} — so the same
 * {@link VisionSupport#imageUrlUserMessage} the other three use serializes them here too.
 *
 * <p>Only the <b>vision</b> models accept them; the default {@code command-a-03-2025} does not.
 * That gate lives in {@code VisionSupport.modelCanSeeImages}, and it matters: a photo sent to a
 * text-only Command model is silently dropped by the provider while the turn still says an image
 * was attached, and the model then answers as though it had looked (BUG-027). A user on a
 * text-only model gets the photo read by Mistral OCR instead, which is the same path every other
 * blind model takes.
 */
public class CohereProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CohereProvider.class);

    private static final String ENDPOINT = "https://api.cohere.com/v2/chat";

    /**
     * The model used when the user saved a key without choosing one.
     *
     * <p>{@code command-a-03-2025} rather than a newer preview: on Cohere's own rate-limit page
     * the newest variants are "contact sales" on a production key, while Command A is listed at
     * 500 requests a minute — so it is the one a default can rely on. The lesson of BUG-059
     * applies here too, and Cohere publishes no {@code -latest} alias to hide behind, so this id
     * is a value with an expiry date: {@code CohereModelsTest} is where it is asserted, and the
     * model picker lists what the account can actually reach.
     */
    static final String DEFAULT_MODEL = "command-a-03-2025";

    private static final int MAX_TOKENS = 8192;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CohereProvider(String apiKey, String model, HttpClient httpClient,
                          ObjectMapper objectMapper) {
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
                    // Bounds the wait for the response headers (BUG-055).
                    .timeout(LlmHttp.REQUEST_TIMEOUT)
                    .header("content-type", "application/json")
                    .header("accept", "text/event-stream")
                    .header("authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    LlmHttp.sendStreaming(httpClient, request);

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                onError.accept(new RuntimeException(
                        "Cohere API error " + response.statusCode() + ": " + errorBody));
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

        // Per tool-call index, assembled across tool-call-start / -delta / -end.
        Map<Integer, String> toolIds = new HashMap<>();
        Map<Integer, String> toolNames = new HashMap<>();
        Map<Integer, StringBuilder> toolArgs = new HashMap<>();

        try {
            lines.forEach(line -> {
                if (!line.startsWith("data:")) return;
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;

                try {
                    JsonNode node = objectMapper.readTree(data);
                    switch (node.path("type").asText("")) {
                        case "content-delta" -> {
                            String text = node.path("delta").path("message")
                                    .path("content").path("text").asText("");
                            if (!text.isEmpty()) onToken.accept(text);
                        }
                        case "tool-call-start" -> {
                            int index = node.path("index").asInt(0);
                            JsonNode call = node.path("delta").path("message").path("tool_calls");
                            String id = call.path("id").asText("");
                            if (!id.isEmpty()) toolIds.put(index, id);
                            String name = call.path("function").path("name").asText("");
                            if (!name.isEmpty()) toolNames.put(index, name);
                            toolArgs.putIfAbsent(index, new StringBuilder());
                            // Cohere may send the opening arguments with the start frame.
                            appendArguments(toolArgs, index, call);
                        }
                        case "tool-call-delta" -> {
                            int index = node.path("index").asInt(0);
                            appendArguments(toolArgs, index,
                                    node.path("delta").path("message").path("tool_calls"));
                        }
                        // tool-plan-delta is the model narrating why it is about to call a tool.
                        // It is not an answer to the user, so it is deliberately dropped rather
                        // than streamed into the transcript as if it were one.
                        default -> { }
                    }
                } catch (Exception e) {
                    // Length + cause only — the frame body is the model's answer, i.e. the
                    // user's own content (see docs/logging.md).
                    log.debug("Skipping unparseable SSE frame ({} chars)", data.length(), e);
                }
            });

            for (Map.Entry<Integer, String> entry : toolNames.entrySet()) {
                String args = toolArgs.getOrDefault(entry.getKey(), new StringBuilder()).toString();
                onToolCall.accept(new ToolCall(
                        toolIds.get(entry.getKey()),
                        entry.getValue(),
                        args.isBlank() ? "{}" : args));
            }

            onComplete.run();

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /** Appends one frame's worth of streamed argument text to the call being assembled. */
    private static void appendArguments(
            Map<Integer, StringBuilder> toolArgs, int index, JsonNode toolCalls) {
        JsonNode args = toolCalls.path("function").path("arguments");
        String chunk = args.isTextual() ? args.asText()
                : (args.isMissingNode() || args.isNull()) ? "" : args.toString();
        if (!chunk.isEmpty()) {
            toolArgs.computeIfAbsent(index, k -> new StringBuilder()).append(chunk);
        }
    }

    String buildRequestBody(List<LlmMessage> messages, String systemPrompt, List<ToolSpec> tools)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("max_tokens", MAX_TOKENS);

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            allMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (LlmMessage m : messages) {
            allMessages.add(toCohereMessage(m));
            // The tool role cannot hold image parts, so a read_resource that returned an image
            // is followed by a user message carrying it — the same shape OpenAI needs.
            if (m.hasImages()) {
                allMessages.add(VisionSupport.imageUrlUserMessage(m.images()));
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

    private Map<String, Object> toCohereMessage(LlmMessage m) {
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
                // A JSON-encoded STRING, as with OpenAI — "{}" when empty so it is always valid.
                String args = (tc.argumentsJson() == null || tc.argumentsJson().isBlank())
                        ? "{}" : tc.argumentsJson();
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", tc.name());
                fn.put("arguments", args);
                Map<String, Object> call = new LinkedHashMap<>();
                if (tc.id() != null) call.put("id", tc.id());
                call.put("type", "function");
                call.put("function", fn);
                calls.add(call);
            }
            msg.put("role", "assistant");
            // Cohere rejects an assistant turn with neither content nor tool_calls; content is
            // included only when it is actually there.
            if (m.content() != null && !m.content().isBlank()) {
                msg.put("content", m.content());
            }
            msg.put("tool_calls", calls);
            return msg;
        }

        msg.put("role", m.role());
        msg.put("content", m.content() == null ? "" : m.content());
        return msg;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.COHERE;
    }
}
