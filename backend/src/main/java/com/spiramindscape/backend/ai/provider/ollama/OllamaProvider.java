package com.spiramindscape.backend.ai.provider.ollama;

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
 * Ollama (ollama.com) — a locally-run LLM runtime with an OpenAI-compatible
 * chat-completions API.
 *
 * <p>No API key/auth: Ollama runs on the user's machine. The "stored key" is
 * repurposed as the server's base URL (default {@code http://localhost:11434}),
 * so a user can point at a custom host/port. Streaming and tool-calling format
 * are identical to OpenAI/Mistral.
 *
 * <p>Tool calling works only with tool-capable local models (e.g. llama3.1,
 * qwen2.5, mistral). Pull a model first with {@code ollama pull <model>}.
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    static final String LOCAL_BASE_URL = "http://localhost:11434";
    static final String CLOUD_BASE_URL = "https://ollama.com";
    static final String DEFAULT_MODEL = "gpt-oss:120b";
    private static final int MAX_TOKENS = 4096;

    private final String baseUrl;
    private final String apiKey; // Bearer token for Ollama Cloud; null for a local/self-hosted server
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * The stored value is either an http(s):// base URL (local / self-hosted
     * Ollama, no auth) or an Ollama Cloud API key (used as a Bearer token
     * against {@code https://ollama.com}).
     */
    public OllamaProvider(String keyOrUrl, String model, HttpClient httpClient, ObjectMapper objectMapper) {
        String v = keyOrUrl == null ? "" : keyOrUrl.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) {
            this.baseUrl = stripBase(v);
            this.apiKey = null;                 // local / self-hosted
        } else {
            this.baseUrl = CLOUD_BASE_URL;
            this.apiKey = v;                    // Ollama Cloud API key
        }
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** Strips a trailing slash and an optional /v1 suffix from a base URL. */
    static String stripBase(String url) {
        String v = url.trim().replaceAll("/+$", "");
        if (v.endsWith("/v1")) v = v.substring(0, v.length() - 3);
        return v;
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

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("authorization", "Bearer " + apiKey);
            }
            HttpRequest request = builder.build();

            HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofLines()
            );

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                onError.accept(new RuntimeException(
                        "Ollama API error " + response.statusCode() + ": " + errorBody));
                return;
            }

            processStream(response.body(), onToken, onToolCall, onComplete, onError);

        } catch (Exception e) {
            String hint = apiKey == null
                    ? " — is the local server running?"
                    : ""; // cloud
            onError.accept(new RuntimeException(
                    "Could not reach Ollama at " + baseUrl + hint + " (" + e.getMessage() + ")", e));
        }
    }

    private void processStream(
            java.util.stream.Stream<String> lines,
            Consumer<String> onToken,
            Consumer<ToolCall> onToolCall,
            Runnable onComplete,
            Consumer<Throwable> onError) {

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
                    JsonNode choices = node.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) return;

                    JsonNode delta = choices.get(0).path("delta");

                    String text = delta.path("content").asText("");
                    if (!text.isEmpty()) onToken.accept(text);

                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int index = tc.path("index").asInt(0);
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty()) toolIds.put(index, id);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            allMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (LlmMessage m : messages) {
            allMessages.add(toMessage(m));
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

    private Map<String, Object> toMessage(LlmMessage m) {
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
                // Ollama's OpenAI-compatible API expects function.arguments as a
                // JSON-encoded STRING (per the OpenAI spec). Send the raw JSON
                // string; default to "{}" when empty so it's always valid JSON.
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
        return ProviderType.OLLAMA;
    }
}
