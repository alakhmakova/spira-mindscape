package com.spiramindscape.backend.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The one place every LLM provider's HTTP call goes through: a <b>deadline</b> and a
 * <b>bounded retry</b>.
 *
 * <h2>Why this exists (BUG-055)</h2>
 *
 * <p>{@code LlmProviderFactory} built its shared client with a connect timeout and nothing
 * else, and none of the four providers put a timeout on the request itself. Everything else
 * that calls out of this app has one — {@code TavilySearchService} 20/30s,
 * {@code UrlReadService} 20s, {@code MistralOcrService} 90s — the model call was the
 * exception. So when a provider accepted the connection and then said nothing, the worker
 * thread sat on it until the {@code SseEmitter}'s own three-minute timeout fired, Spring
 * raised {@code AsyncRequestTimeoutException}, and the user got a <b>500 after three
 * minutes of an empty chat bubble</b>. Cloud Run's logs for 25–27 Aug 2026 show it plainly:
 * every failing {@code POST /api/ai/chat} lasted 180.5–184s and returned the same 1107-byte
 * error body, meaning not one token had been streamed.
 *
 * <p>The second half is the retry. The only provider error in those logs was Gemini's
 * {@code 503 "This model is currently experiencing high demand. Spikes in demand are
 * usually temporary. Please try again later."} — an error that says, in words, to try
 * again, which nothing did. One retry turns most of those into an answer.
 *
 * <h2>What the timeout does and does not cover</h2>
 *
 * <p>{@link HttpRequest.Builder#timeout} bounds the wait for the <b>response headers</b>.
 * With a streaming body handler ({@code ofLines}) {@code send} returns as soon as they
 * arrive, so a stall <i>after</i> the first byte is not covered here — that is the
 * stream deadline's job, in {@code AiChatService}. The two are deliberately different
 * mechanisms because they are different failures: nothing came back at all, versus it
 * started and then stopped.
 *
 * <h2>The budget</h2>
 *
 * <p>Three attempts of {@link #REQUEST_TIMEOUT} plus backoff must finish inside the
 * emitter's own limit, or the retry would only ever produce the 500 it exists to prevent.
 * {@link #TOTAL_BUDGET} enforces that directly: an attempt is not started unless the whole
 * of it fits in what is left.
 */
public final class LlmHttp {

    private static final Logger log = LoggerFactory.getLogger(LlmHttp.class);

    /** How long one attempt may wait for the provider's response headers. */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    /** Attempts in total, not retries after the first — 3 means at most two retries. */
    static final int MAX_ATTEMPTS = 3;

    /**
     * The wall-clock ceiling for all attempts together. Comfortably inside
     * {@code ChatStreamGuard.DEADLINE}, so a caller that has burned this budget still has
     * time to report the failure as a readable message rather than being cut off.
     *
     * <p>Public because that relationship is a contract between two packages and nothing
     * else enforces it — {@code ChatStreamGuardTest} asserts the ordering, and it can only
     * do that if it can read the number.
     */
    public static final Duration TOTAL_BUDGET = Duration.ofSeconds(100);

    /** Backoff before attempt 2, doubled before attempt 3. */
    static final Duration BASE_BACKOFF = Duration.ofMillis(700);

    /** The longest we will honour a provider's own {@code Retry-After}. */
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);

    /**
     * Statuses worth trying again on their own.
     *
     * <p>5xx is the ordinary transient set. <b>520–524 are Cloudflare's</b>, and they are in
     * here because several model APIs sit behind it: 524 in particular is "the origin took too
     * long", which is exactly the failure this class is about, and it is the number the owner
     * saw in the app — {@code friendlyError} passes the provider's own text through to the
     * user, so a Cloudflare page in front of a model API surfaces as a Spira error. 529 is
     * Anthropic's "overloaded".
     *
     * <p>Deliberately absent: 400 (a malformed request retries identically), 401/403 (a bad
     * key stays bad), 404, 413, and 422 — <b>and 429</b>, which has its own rule in
     * {@link #shouldRetry}.
     */
    static final Set<Integer> RETRYABLE_STATUSES =
            Set.of(408, 425, 500, 502, 503, 504, 520, 521, 522, 523, 524, 529);

    /** Too Many Requests — see {@link #shouldRetry} for why it is not in the set above. */
    static final int TOO_MANY_REQUESTS = 429;

    private LlmHttp() {}

    /**
     * Sends {@code request} and returns the response to stream from, retrying transient
     * failures.
     *
     * <p>The returned response may be a non-2xx one: a status this class does not consider
     * transient, or a transient one that survived every attempt. Formatting that into a
     * message is the caller's job, because each provider words its own errors and the user
     * sees them.
     *
     * <p><b>The body of a discarded attempt is closed</b>, not left to the garbage
     * collector: {@code ofLines} hands back a stream still holding the connection, and
     * leaking one per retry would exhaust the pool under exactly the provider trouble that
     * triggers the retry.
     *
     * @throws IOException          if every attempt failed at the transport level (the last
     *                              failure is thrown, with the earlier ones suppressed)
     * @throws InterruptedException if the thread is interrupted while backing off
     */
    public static HttpResponse<Stream<String>> sendStreaming(
            HttpClient client, HttpRequest request) throws IOException, InterruptedException {

        long deadlineNanos = System.nanoTime() + TOTAL_BUDGET.toNanos();
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpResponse<Stream<String>> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            } catch (IOException e) {
                // Connect failure, read timeout, connection reset mid-handshake — all the
                // same shape of "it did not answer", all worth one more go.
                if (lastFailure != null) e.addSuppressed(lastFailure);
                lastFailure = e;
                if (!canRetry(attempt, deadlineNanos)) throw e;
                logRetry(request, attempt, e.getClass().getSimpleName());
                sleep(backoffFor(attempt, null, deadlineNanos));
                continue;
            }

            int status = response.statusCode();
            if (!shouldRetry(status, response) || !canRetry(attempt, deadlineNanos)) {
                return response;
            }

            // Retrying: let go of this attempt's connection before opening another.
            closeQuietly(response);
            logRetry(request, attempt, "status_" + status);
            sleep(backoffFor(attempt, response, deadlineNanos));
        }

        // Only reachable when the last attempt threw and canRetry said no — which rethrows
        // above — so this is defensive rather than expected.
        throw lastFailure != null ? lastFailure : new IOException("No attempt was made");
    }

    /**
     * Whether this status is worth sending the same request for a second time. Split out
     * from the loop so the decision can be tested without waiting through a real backoff.
     */
    static boolean isRetryable(int status) {
        return RETRYABLE_STATUSES.contains(status);
    }

    /**
     * Whether to repeat this request, given both the status and what the response said about
     * coming back.
     *
     * <h4>Why 429 is special</h4>
     *
     * <p>A 429 is two different things wearing one number, and they want opposite treatment:
     *
     * <ul>
     *   <li><b>"too fast"</b> — a per-minute rate limit. The provider knows when the window
     *       reopens and says so in {@code Retry-After}. Worth waiting for, if the wait is
     *       short.</li>
     *   <li><b>"out of quota"</b> — the plan's allowance for the day is spent. Google's
     *       free tier answers exactly this way: <i>"You exceeded your current quota, please
     *       check your plan and billing details."</i> No amount of waiting inside one request
     *       helps, and <b>each attempt spends more of the quota that is already gone</b>.</li>
     * </ul>
     *
     * <p>Retrying blindly turned every quota error into three, tripling the consumption at the
     * exact moment the user had none left, and delayed the one message that would have told
     * them why. So: <b>retry a 429 only when the provider named a wait we can afford.</b>
     * A bare 429 is handed straight back, and the user reads the provider's own words
     * immediately.
     *
     * <p>This is one rule for every provider, not a special case for Google — Anthropic and
     * OpenAI send {@code Retry-After} on a genuine rate limit too, so they keep their retry,
     * while OpenAI's {@code insufficient_quota} (a bare 429) now surfaces at once instead of
     * after two more wasted calls. Nobody is disadvantaged by it.
     */
    static boolean shouldRetry(int status, HttpResponse<?> response) {
        if (status == TOO_MANY_REQUESTS) {
            Duration after = retryAfter(response);
            return after != null && after.compareTo(MAX_RETRY_AFTER) <= 0;
        }
        return isRetryable(status);
    }

    /** True when another attempt is both allowed and able to finish inside the budget. */
    private static boolean canRetry(int attempt, long deadlineNanos) {
        if (attempt >= MAX_ATTEMPTS) return false;
        long remaining = deadlineNanos - System.nanoTime();
        return remaining > REQUEST_TIMEOUT.toNanos();
    }

    /**
     * How long to wait before the next attempt: the provider's own {@code Retry-After} when
     * it sent one (capped — a model API asking for a minute is not something a user waits
     * through), otherwise exponential backoff. Never longer than the budget left.
     */
    private static Duration backoffFor(
            int attempt, HttpResponse<?> response, long deadlineNanos) {

        Duration wait = BASE_BACKOFF.multipliedBy(1L << (attempt - 1));
        if (response != null) {
            Duration retryAfter = retryAfter(response);
            if (retryAfter != null && retryAfter.compareTo(wait) > 0) {
                wait = retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
            }
        }
        // Leave enough of the budget for the attempt the wait is for.
        long spare = deadlineNanos - System.nanoTime() - REQUEST_TIMEOUT.toNanos();
        if (spare <= 0) return Duration.ZERO;
        Duration cap = Duration.ofNanos(spare);
        return wait.compareTo(cap) > 0 ? cap : wait;
    }

    /** {@code Retry-After} in delta-seconds; null when absent or an HTTP-date. */
    private static Duration retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("retry-after")
                .map(String::trim)
                .filter(v -> v.chars().allMatch(Character::isDigit) && !v.isEmpty())
                .map(v -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(v));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static void closeQuietly(HttpResponse<Stream<String>> response) {
        try (Stream<String> body = response.body()) {
            body.close();
        } catch (RuntimeException e) {
            log.debug("llm_http_body_close_failed {}", e.toString());
        }
    }

    /**
     * One line per retry. The host and the reason only — never the request body, which
     * carries the user's goal text and the whole conversation.
     */
    private static void logRetry(HttpRequest request, int attempt, String reason) {
        log.warn("llm_http_retry host={} attempt={} reason={}",
                request.uri().getHost(), attempt, reason);
    }

    private static void sleep(Duration d) throws InterruptedException {
        if (!d.isZero() && !d.isNegative()) Thread.sleep(d.toMillis());
    }
}
