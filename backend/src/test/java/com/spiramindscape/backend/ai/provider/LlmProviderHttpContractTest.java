package com.spiramindscape.backend.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.provider.anthropic.AnthropicProvider;
import com.spiramindscape.backend.ai.provider.cohere.CohereProvider;
import com.spiramindscape.backend.ai.provider.google.GeminiProvider;
import com.spiramindscape.backend.ai.provider.mistral.MistralProvider;
import com.spiramindscape.backend.ai.provider.openai.OpenAiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * **What every provider owes the caller, whichever one the user picked** (BUG-055).
 *
 * <p>The providers are near-identical copies of one another, which is exactly how the
 * missing request deadline survived in all four at once: each was written by copying the
 * last, and none of them had one to copy. A per-provider test would have been four chances
 * to forget. This is one table instead, and a fifth provider is a row in it.
 *
 * <p>Two things are asserted, and they are the two that were absent in production:
 * <ol>
 *   <li>the request carries {@link LlmHttp#REQUEST_TIMEOUT}, so a provider that goes quiet
 *       is abandoned rather than held until the SSE emitter expires;</li>
 *   <li>the call goes through {@link LlmHttp}, so a transient refusal is retried — visible
 *       here as a 503 followed by a 200 producing a token instead of an error.</li>
 * </ol>
 */
class LlmProviderHttpContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Each provider, built around a client the test controls. */
    static Stream<Arguments> providers() {
        ObjectMapper mapper = new ObjectMapper();
        return Stream.of(
                Arguments.of("Anthropic",
                        (Function<HttpClient, LlmProvider>)
                                c -> new AnthropicProvider("key", null, c, mapper)),
                Arguments.of("OpenAI",
                        (Function<HttpClient, LlmProvider>)
                                c -> new OpenAiProvider("key", null, c, mapper)),
                Arguments.of("Mistral",
                        (Function<HttpClient, LlmProvider>)
                                c -> new MistralProvider("key", null, c, mapper)),
                Arguments.of("Gemini",
                        (Function<HttpClient, LlmProvider>)
                                c -> new GeminiProvider("key", null, c, mapper)),
                Arguments.of("Cohere",
                        (Function<HttpClient, LlmProvider>)
                                c -> new CohereProvider("key", null, c, mapper)));
    }

    @ParameterizedTest(name = "{0} sets a request deadline")
    @MethodSource("providers")
    @DisplayName("Every provider puts a deadline on the request it sends")
    void everyProviderSetsARequestTimeout(
            String name, Function<HttpClient, LlmProvider> build) throws Exception {

        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        HttpClient client = clientReturning(sent, 200);

        build.apply(client).streamChat(
                List.of(new LlmMessage("user", "hello")), "system", List.of(),
                token -> { }, call -> { }, () -> { }, error -> { });

        assertThat(sent.get()).as("%s never sent a request", name).isNotNull();
        assertThat(sent.get().timeout())
                .as("%s sent a request with no deadline — a silent provider would hold the "
                        + "worker thread until the SSE emitter gave up", name)
                .contains(LlmHttp.REQUEST_TIMEOUT);
    }

    @ParameterizedTest(name = "{0} retries a 503")
    @MethodSource("providers")
    @DisplayName("Every provider retries a transient refusal instead of failing the turn")
    void everyProviderRetriesATransientRefusal(
            String name, Function<HttpClient, LlmProvider> build) throws Exception {

        AtomicInteger sends = new AtomicInteger();
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> sends.incrementAndGet() == 1
                        ? emptyResponse(503)
                        : emptyResponse(200));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        build.apply(client).streamChat(
                List.of(new LlmMessage("user", "hello")), "system", List.of(),
                token -> { }, call -> { }, completions::incrementAndGet, failure::set);

        assertThat(sends).as("%s did not retry", name).hasValue(2);
        assertThat(failure.get())
                .as("%s reported a failure for a 503 that the retry recovered from", name)
                .isNull();
        assertThat(completions).as("%s never completed", name).hasValue(1);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HttpClient clientReturning(AtomicReference<HttpRequest> sent, int status)
            throws IOException, InterruptedException {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    sent.set(inv.getArgument(0));
                    return emptyResponse(status);
                });
        return client;
    }

    /** A response whose body is an empty SSE stream — enough for a clean completion. */
    @SuppressWarnings("unchecked")
    private HttpResponse<Stream<String>> emptyResponse(int status) {
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(Stream.of());
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        return response;
    }
}
