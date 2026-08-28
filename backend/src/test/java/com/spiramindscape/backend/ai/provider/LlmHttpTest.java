package com.spiramindscape.backend.ai.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * **The model call must be able to give up, and must be willing to try again** (BUG-055).
 *
 * <p>Neither was true before. The providers' shared {@code HttpClient} had a connect
 * timeout and nothing more, and no provider put a deadline on the request, so a model API
 * that accepted the connection and then went quiet held a worker thread until the SSE
 * emitter's own three-minute limit expired — which reached the user as a 500 after three
 * minutes of an empty bubble. And the one provider error the production logs did contain,
 * Gemini's {@code 503 "experiencing high demand… try again later"}, was never tried again.
 *
 * <p>What each test here pins down is a decision that is easy to get wrong later: which
 * statuses are worth repeating, that a wrong API key is <b>not</b> one of them, that the
 * whole thing fits inside the caller's budget, and that a discarded attempt lets go of its
 * connection.
 */
class LlmHttpTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/v1/messages"))
            .timeout(LlmHttp.REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    // ─── A stub client that plays a scripted sequence of outcomes ─────────────

    /** One scripted outcome: either a response to return or a failure to throw. */
    private sealed interface Turn {
        record Respond(int status, Map<String, List<String>> headers) implements Turn {}
        record Fail(IOException error) implements Turn {}
    }

    private static Turn ok() {
        return new Turn.Respond(200, Map.of());
    }

    private static Turn status(int code) {
        return new Turn.Respond(code, Map.of());
    }

    private final Deque<Turn> script = new ArrayDeque<>();
    private final AtomicInteger sends = new AtomicInteger();
    private final AtomicInteger closedBodies = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private HttpClient clientPlaying(Turn... turns) throws Exception {
        script.clear();
        script.addAll(List.of(turns));
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    sends.incrementAndGet();
                    Turn turn = script.isEmpty()
                            ? new Turn.Fail(new IOException("script exhausted"))
                            : script.removeFirst();
                    if (turn instanceof Turn.Fail f) throw f.error();
                    Turn.Respond r = (Turn.Respond) turn;
                    return response(r.status(), r.headers());
                });
        return client;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Stream<String>> response(int status, Map<String, List<String>> headers) {
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        // A real ofLines() body still holds the connection, so the close is what matters.
        when(response.body()).thenReturn(
                Stream.of("data: {}").onClose(closedBodies::incrementAndGet));
        when(response.headers()).thenReturn(
                HttpHeaders.of(headers, (a, b) -> true));
        return response;
    }

    // ─── The happy path is not slowed down ────────────────────────────────────

    @Test
    @DisplayName("A response that arrives first time is returned with no retry")
    void succeedsOnTheFirstAttempt() throws Exception {
        HttpClient client = clientPlaying(ok());

        HttpResponse<Stream<String>> response = LlmHttp.sendStreaming(client, REQUEST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sends).hasValue(1);
    }

    // ─── Transient failures are retried ───────────────────────────────────────

    @Test
    @DisplayName("Gemini's 503 'high demand' is retried and the second answer is used")
    void retriesGeminisOverloadedResponse() throws Exception {
        // The exact failure the production logs showed on 27 Aug 2026, and the one this
        // whole mechanism was built for: an error whose own text says to try again.
        HttpClient client = clientPlaying(status(503), ok());

        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(200);
        assertThat(sends).hasValue(2);
    }

    @ParameterizedTest(name = "HTTP {0} counts as transient")
    @ValueSource(ints = {408, 425, 500, 502, 503, 504, 520, 521, 522, 523, 524, 529})
    @DisplayName("Every transient status is on the retry list")
    void everyTransientStatusIsRetryable(int code) {
        // 52x are Cloudflare's, and they are on the list because several model APIs sit
        // behind it — 524 ("the origin took too long") is the number the owner saw in the
        // app, because provider error text is passed through to the user verbatim.
        // Asserted on the predicate rather than end to end, so thirteen cases do not spend
        // thirteen real backoffs; retriesGeminisOverloadedResponse covers the loop itself.
        assertThat(LlmHttp.isRetryable(code)).isTrue();
    }

    @Test
    @DisplayName("A read timeout is retried like any other transport failure")
    void retriesATimeout() throws Exception {
        HttpClient client = clientPlaying(
                new Turn.Fail(new HttpTimeoutException("request timed out")), ok());

        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(200);
        assertThat(sends).hasValue(2);
    }

    // ─── Permanent failures are not ───────────────────────────────────────────

    @ParameterizedTest(name = "HTTP {0} counts as permanent")
    @ValueSource(ints = {400, 401, 403, 404, 413, 422})
    @DisplayName("A permanent failure is not on the retry list")
    void permanentFailuresAreNotRetryable(int code) {
        // A wrong API key stays wrong, and a malformed body is malformed the second time
        // too. Retrying these would triple the delay before the user reads the one message
        // that would actually help them ("your key is invalid").
        assertThat(LlmHttp.isRetryable(code)).isFalse();
    }

    @Test
    @DisplayName("A 401 is handed straight back after one attempt")
    void doesNotRetryABadKey() throws Exception {
        HttpClient client = clientPlaying(status(401), ok());

        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(401);
        assertThat(sends).hasValue(1);
    }

    // ─── Giving up ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A provider that is down throughout returns its last response, not an exception")
    void exhaustsRetriesAndReturnsTheLastResponse() throws Exception {
        HttpClient client = clientPlaying(status(503), status(503), status(503));

        // Handed back rather than thrown, so the provider can word its own error — the
        // user sees "…experiencing high demand…", not a stack-trace class name.
        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(503);
        assertThat(sends).hasValue(LlmHttp.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("A transport failure on every attempt is thrown, carrying the earlier ones")
    void exhaustsRetriesAndThrows() throws Exception {
        HttpClient client = clientPlaying(
                new Turn.Fail(new IOException("connection reset")),
                new Turn.Fail(new IOException("connection reset")),
                new Turn.Fail(new IOException("final failure")));

        assertThatThrownBy(() -> LlmHttp.sendStreaming(client, REQUEST))
                .isInstanceOf(IOException.class)
                .hasMessage("final failure")
                .satisfies(e -> assertThat(e.getSuppressed()).isNotEmpty());
        assertThat(sends).hasValue(LlmHttp.MAX_ATTEMPTS);
    }

    // ─── Not leaking the connection while retrying ────────────────────────────

    @Test
    @DisplayName("The body of a retried attempt is closed before the next one opens")
    void closesTheBodyOfADiscardedAttempt() throws Exception {
        // ofLines() hands back a lazy stream still holding the connection. Dropping one per
        // retry would exhaust the pool under exactly the provider trouble that causes the
        // retry — the failure would compound itself.
        HttpClient client = clientPlaying(status(503), status(503), ok());

        LlmHttp.sendStreaming(client, REQUEST);

        assertThat(closedBodies).hasValue(2);       // the two discarded attempts
        assertThat(sends).hasValue(3);
    }

    @Test
    @DisplayName("The body of the response that is returned is left for the caller to read")
    void doesNotCloseTheReturnedBody() throws Exception {
        HttpClient client = clientPlaying(ok());

        HttpResponse<Stream<String>> response = LlmHttp.sendStreaming(client, REQUEST);

        assertThat(closedBodies).hasValue(0);
        assertThat(response.body()).isNotNull();
    }

    // ─── Timing ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A retry backs off rather than hammering a provider that is already struggling")
    void backsOffBetweenAttempts() throws Exception {
        HttpClient client = clientPlaying(status(503), ok());

        long start = System.nanoTime();
        LlmHttp.sendStreaming(client, REQUEST);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(LlmHttp.BASE_BACKOFF);
    }

    // ─── 429: two different failures wearing one number ───────────────────────

    @Test
    @DisplayName("A bare 429 — 'out of quota' — is handed back at once, not retried")
    void doesNotRetryAQuotaExhausted429() throws Exception {
        // What Google's free tier answers with: "You exceeded your current quota, please check
        // your plan and billing details." Waiting inside one request cannot help, and every
        // attempt spends more of the allowance that has already run out — so retrying tripled
        // the consumption at the exact moment the user had none left, and delayed the one
        // message that would have explained it.
        HttpClient client = clientPlaying(status(429), ok());

        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(429);
        assertThat(sends).hasValue(1);
    }

    @Test
    @DisplayName("A 429 that names a short wait — 'too fast' — is retried after it")
    void retriesA429ThatNamesAnAffordableWait() throws Exception {
        // A per-minute rate limit: the provider knows when the window reopens and says so.
        // Anthropic and OpenAI both send this header, so the rule keeps their retry while
        // dropping the pointless one — it is not a special case for any provider.
        HttpClient client = clientPlaying(
                new Turn.Respond(429, Map.of("retry-after", List.of("1"))), ok());

        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(200);
        assertThat(sends).hasValue(2);
    }

    @Test
    @DisplayName("A 429 asking for longer than we can wait is handed back rather than slept on")
    void doesNotRetryA429ThatAsksForTooLong() throws Exception {
        // 300 seconds is a real thing for a rate-limited model API to ask for. Nobody waits
        // five minutes inside a chat, and retrying earlier than asked is guaranteed to fail
        // and to cost another request — so the honest answer is the provider's own message,
        // now.
        HttpClient client = clientPlaying(
                new Turn.Respond(429, Map.of("retry-after", List.of("300"))), ok());

        long start = System.nanoTime();
        HttpResponse<Stream<String>> response = LlmHttp.sendStreaming(client, REQUEST);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(sends).hasValue(1);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("429 is not on the plain retry list — it has its own rule")
    void the429RuleIsNotTheStatusList() {
        assertThat(LlmHttp.isRetryable(429)).isFalse();
    }

    @Test
    @DisplayName("An HTTP-date Retry-After is ignored rather than misread as seconds")
    void ignoresANonNumericRetryAfter() throws Exception {
        // Parsing "Wed, 21 Oct 2026 07:28:00 GMT" as a number would either throw or, worse,
        // silently become zero and remove the backoff.
        HttpClient client = clientPlaying(
                new Turn.Respond(503, Map.of("retry-after", List.of("Wed, 21 Oct 2026 07:28:00 GMT"))),
                ok());

        long start = System.nanoTime();
        assertThat(LlmHttp.sendStreaming(client, REQUEST).statusCode()).isEqualTo(200);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(LlmHttp.BASE_BACKOFF);
    }

    @Test
    @DisplayName("The retry budget stays inside the stream deadline it has to fit in")
    void theBudgetFitsInsideTheStreamDeadline() {
        // The arithmetic that makes the retry worth having: if the attempts plus their
        // backoff could outlast the SSE emitter, the retry would only ever produce the very
        // 500 it exists to prevent.
        Duration worstCase = LlmHttp.TOTAL_BUDGET.plus(LlmHttp.REQUEST_TIMEOUT);
        assertThat(worstCase).isLessThan(Duration.ofSeconds(180));
        assertThat(LlmHttp.REQUEST_TIMEOUT).isLessThan(LlmHttp.TOTAL_BUDGET);
    }

    @Test
    @DisplayName("Interrupting the thread during backoff stops the retry")
    void interruptDuringBackoffPropagates() throws Exception {
        HttpClient client = clientPlaying(status(503), ok());
        AtomicBoolean interrupted = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                LlmHttp.sendStreaming(client, REQUEST);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } catch (IOException ignored) {
                // not the case under test
            }
        });
        worker.start();
        Thread.sleep(100);   // let it reach the backoff
        worker.interrupt();
        worker.join(5000);

        assertThat(interrupted).isTrue();
    }
}
