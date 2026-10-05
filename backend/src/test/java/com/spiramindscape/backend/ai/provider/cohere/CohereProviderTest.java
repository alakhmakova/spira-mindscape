package com.spiramindscape.backend.ai.provider.cohere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.LlmImage;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import com.spiramindscape.backend.ai.provider.VisionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * **Cohere Chat v2** — the fifth provider, and the first that is not an OpenAI-shaped API.
 *
 * <p>The other four all answer with deltas on a choice ({@code choices[0].delta.content}).
 * Cohere streams <b>typed events</b> instead — {@code content-delta}, {@code tool-call-start},
 * {@code tool-call-delta} — and puts the text at {@code delta.message.content.text}. That is the
 * part a copied-from-OpenAI implementation would get silently wrong: the stream would parse
 * without error and produce no tokens at all, which reads to the user as the model saying
 * nothing.
 *
 * <p>Written against the documented shapes rather than a live key — the owner has not connected
 * one yet, so nothing here has been through the real API. Those are the assertions to re-check
 * first if the first real conversation misbehaves.
 */
class CohereProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private CohereProvider provider(HttpClient client) {
        return new CohereProvider("test-key", null, client, mapper);
    }

    /** A client that replays scripted SSE lines and records the request it was given. */
    @SuppressWarnings("unchecked")
    private HttpClient clientStreaming(AtomicReference<HttpRequest> sent, String... lines)
            throws IOException, InterruptedException {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(Stream.of(lines));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    sent.set(inv.getArgument(0));
                    return response;
                });
        return client;
    }

    private static String frame(String json) {
        return "data: " + json;
    }

    // ─── The stream ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Text arrives from content-delta, where Cohere actually puts it")
    void streamsTextFromContentDelta() throws Exception {
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientStreaming(sent,
                frame("{\"type\":\"message-start\",\"id\":\"x\"}"),
                frame("{\"type\":\"content-start\",\"index\":0}"),
                frame("{\"type\":\"content-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"content\":{\"text\":\"Hello\"}}}}"),
                frame("{\"type\":\"content-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"content\":{\"text\":\" there\"}}}}"),
                frame("{\"type\":\"content-end\",\"index\":0}"),
                frame("{\"type\":\"message-end\"}"));

        StringBuilder text = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean();
        provider(client).streamChat(
                List.of(LlmMessage.user("hi")), "system", List.of(),
                text::append, call -> { }, () -> completed.set(true), e -> { });

        assertThat(text.toString()).isEqualTo("Hello there");
        assertThat(completed).isTrue();
    }

    @Test
    @DisplayName("A tool call is assembled across start, delta and end")
    void assemblesAToolCall() throws Exception {
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientStreaming(sent,
                frame("{\"type\":\"tool-call-start\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"tool_calls\":{\"id\":\"tc_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"propose_goal_change\",\"arguments\":\"\"}}}}}"),
                frame("{\"type\":\"tool-call-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"tool_calls\":{\"function\":{\"arguments\":\"{\\\"kind\\\":\"}}}}}"),
                frame("{\"type\":\"tool-call-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"tool_calls\":{\"function\":{\"arguments\":\"\\\"note\\\"}\"}}}}}"),
                frame("{\"type\":\"tool-call-end\",\"index\":0}"),
                frame("{\"type\":\"message-end\"}"));

        List<ToolCall> calls = new ArrayList<>();
        provider(client).streamChat(
                List.of(LlmMessage.user("hi")), "system", List.of(),
                t -> { }, calls::add, () -> { }, e -> { });

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).id()).isEqualTo("tc_1");
        assertThat(calls.get(0).name()).isEqualTo("propose_goal_change");
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"kind\":\"note\"}");
    }

    @Test
    @DisplayName("tool-plan-delta is not streamed to the user as if it were the answer")
    void doesNotStreamTheToolPlan() throws Exception {
        // Cohere narrates why it is about to call a tool. That is not a reply, and putting it in
        // the transcript would show the user the model's working as though it were an answer.
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientStreaming(sent,
                frame("{\"type\":\"tool-plan-delta\",\"delta\":{\"message\":"
                        + "{\"tool_plan\":\"I will look this up\"}}}"),
                frame("{\"type\":\"content-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"content\":{\"text\":\"Done.\"}}}}"),
                frame("{\"type\":\"message-end\"}"));

        StringBuilder text = new StringBuilder();
        provider(client).streamChat(
                List.of(LlmMessage.user("hi")), "system", List.of(),
                text::append, c -> { }, () -> { }, e -> { });

        assertThat(text.toString()).isEqualTo("Done.");
    }

    @Test
    @DisplayName("An unparseable frame is skipped, not fatal")
    void survivesAJunkFrame() throws Exception {
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientStreaming(sent,
                frame("{not json"),
                frame("{\"type\":\"content-delta\",\"index\":0,\"delta\":{\"message\":"
                        + "{\"content\":{\"text\":\"ok\"}}}}"),
                frame("{\"type\":\"message-end\"}"));

        StringBuilder text = new StringBuilder();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        provider(client).streamChat(
                List.of(LlmMessage.user("hi")), "system", List.of(),
                text::append, c -> { }, () -> { }, failure::set);

        assertThat(text.toString()).isEqualTo("ok");
        assertThat(failure.get()).isNull();
    }

    @Test
    @DisplayName("A non-200 is reported with the provider's own words")
    void surfacesTheProviderError() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn(Stream.of("{\"message\":\"model not found\"}"));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        provider(client).streamChat(
                List.of(LlmMessage.user("hi")), "system", List.of(),
                t -> { }, c -> { }, () -> { }, failure::set);

        assertThat(failure.get()).isNotNull();
        assertThat(failure.get().getMessage())
                .contains("Cohere API error 400")
                .contains("model not found");
    }

    // ─── The request ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("The request is v2 chat, streaming, with the system prompt as a message")
    void buildsTheRequest() throws Exception {
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientStreaming(sent,
                frame("{\"type\":\"message-end\"}"));

        provider(client).streamChat(
                List.of(LlmMessage.user("hello")), "you are a coach", List.of(),
                t -> { }, c -> { }, () -> { }, e -> { });

        assertThat(sent.get().uri()).isEqualTo(URI.create("https://api.cohere.com/v2/chat"));
        assertThat(sent.get().headers().firstValue("authorization")).contains("Bearer test-key");
        // The deadline every provider owes the caller (BUG-055).
        assertThat(sent.get().timeout()).isPresent();

        JsonNode body = mapper.readTree(
                new CohereProvider("k", null, null, mapper)
                        .buildRequestBody(List.of(LlmMessage.user("hello")), "you are a coach",
                                List.of()));
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(0).get("content").asText())
                .isEqualTo("you are a coach");
        assertThat(body.get("messages").get(1).get("role").asText()).isEqualTo("user");
    }

    @Test
    @DisplayName("A tool result carries tool_call_id, and the echoed call carries string arguments")
    void serializesTheAgenticLoop() throws Exception {
        // The shape the agentic loop depends on: the model's call is echoed back, then answered.
        List<LlmMessage> messages = List.of(
                LlmMessage.user("search for it"),
                LlmMessage.assistantToolCalls("", List.of(
                        new ToolCall("tc_9", "web_search", "{\"query\":\"x\"}"))),
                LlmMessage.toolResult("tc_9", "the results"));

        JsonNode body = mapper.readTree(new CohereProvider("k", null, null, mapper)
                .buildRequestBody(messages, null, List.of()));

        JsonNode echoed = body.get("messages").get(1);
        assertThat(echoed.get("role").asText()).isEqualTo("assistant");
        // A STRING, not an object — the same rule OpenAI has, and a silent 400 if broken.
        assertThat(echoed.get("tool_calls").get(0).get("function").get("arguments").isTextual())
                .isTrue();
        // An assistant turn with no text must not carry an empty content field.
        assertThat(echoed.has("content")).isFalse();

        JsonNode result = body.get("messages").get(2);
        assertThat(result.get("role").asText()).isEqualTo("tool");
        assertThat(result.get("tool_call_id").asText()).isEqualTo("tc_9");
        assertThat(result.get("content").asText()).isEqualTo("the results");
    }

    @Test
    @DisplayName("Tools go in the OpenAI-style function wrapper Cohere v2 expects")
    void serializesTools() throws Exception {
        ToolSpec spec = new ToolSpec("web_search", "Search the web",
                Map.of("type", "object", "properties", Map.of()));

        JsonNode body = mapper.readTree(new CohereProvider("k", null, null, mapper)
                .buildRequestBody(List.of(LlmMessage.user("hi")), null, List.of(spec)));

        JsonNode tool = body.get("tools").get(0);
        assertThat(tool.get("type").asText()).isEqualTo("function");
        assertThat(tool.get("function").get("name").asText()).isEqualTo("web_search");
        assertThat(tool.get("function").get("parameters")).isNotNull();
    }

    // ─── Capabilities ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Only the vision models are allowed to be shown an image")
    void onlyVisionModelsCanSee() {
        // An allow-list, like Mistral's, because most of the Command line is text-only — the
        // DEFAULT included. Getting this wrong is BUG-027: the provider silently drops the image,
        // the turn still says one was attached, and the model answers as though it had looked.
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, "command-a-vision-07-2025"))
                .isTrue();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, "c4ai-aya-vision-32b"))
                .isTrue();

        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, "command-a-03-2025"))
                .isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, "command-r-plus-08-2024"))
                .isFalse();
        // No model chosen means the default, which is text-only.
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, null)).isFalse();
        assertThat(VisionSupport.modelCanSeeImages(ProviderType.COHERE, "")).isFalse();
    }

    @Test
    @DisplayName("An image rides in a follow-up user message, in Cohere's image_url part shape")
    void serializesAnImage() throws Exception {
        // The tool role cannot carry image parts, so a read_resource that returned a picture is
        // followed by a user message holding it — the same shape OpenAI needs, which is why the
        // two share one helper.
        LlmMessage withImage = LlmMessage.toolResultWithImages(
                "tc_3", "see image", List.of(new LlmImage("image/png", "QUJD")));

        JsonNode body = mapper.readTree(new CohereProvider("k", "command-a-vision-07-2025", null,
                mapper).buildRequestBody(List.of(withImage), null, List.of()));

        assertThat(body.get("messages")).hasSize(2);
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("tool");

        JsonNode follow = body.get("messages").get(1);
        assertThat(follow.get("role").asText()).isEqualTo("user");
        JsonNode part = follow.get("content").get(0);
        assertThat(part.get("type").asText()).isEqualTo("image_url");
        assertThat(part.get("image_url").get("url").asText())
                .isEqualTo("data:image/png;base64,QUJD");
    }

    @Test
    @DisplayName("A message with no image adds no follow-up")
    void doesNotInventAFollowUp() throws Exception {
        JsonNode body = mapper.readTree(new CohereProvider("k", null, null, mapper)
                .buildRequestBody(List.of(LlmMessage.user("hello")), null, List.of()));

        assertThat(body.get("messages")).hasSize(1);
    }

    @Test
    @DisplayName("It reports itself as COHERE")
    void reportsItsType() {
        assertThat(new CohereProvider("k", null, null, mapper).providerType())
                .isEqualTo(ProviderType.COHERE);
    }
}
