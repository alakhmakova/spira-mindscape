package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.provider.LlmHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * **A chat that is working must say so, and a chat that is stuck must say that too**
 * (BUG-055).
 *
 * <p>Before this guard existed a stalled turn produced nothing whatsoever for three minutes
 * and then a bare 500 — the user watched an empty bubble and was told "Server error: 500",
 * which names neither the cause nor anything they could do. Production showed it as
 * {@code POST /api/ai/chat} at 180.5–184s with a 1107-byte body and not a single token.
 *
 * <p>The real clock is two and a half minutes, so every test here runs the same code on a
 * short one via the package-private {@code start(…, heartbeat, deadline)}. The one test
 * that uses the real constants is {@link #theDeadlineFitsBetweenTheRetriesAndTheServlet()},
 * because those numbers only mean anything relative to each other.
 */
class ChatStreamGuardTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    /**
     * Polls until {@code condition} holds, failing with a readable message rather than
     * hanging. A hand-rolled wait instead of Awaitility because the backend has no such
     * dependency and one assertion helper does not justify adding one.
     */
    private static void awaitUntil(String what, java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    /** An emitter that records what was written instead of needing a servlet response. */
    private SseEmitter recordingEmitter() throws Exception {
        SseEmitter emitter = spy(new SseEmitter(60_000L));
        doAnswer(inv -> {
            sent.add(render(inv.getArgument(0)));
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        return emitter;
    }

    /** The wire form of one event: "event:error", "data:…", or ":ping" for a comment. */
    private static String render(SseEmitter.SseEventBuilder builder) {
        StringBuilder out = new StringBuilder();
        builder.build().forEach(d -> out.append(d.getData()));
        return out.toString();
    }

    // ─── The heartbeat ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A working stream is kept alive with periodic pings")
    void sendsAHeartbeat() throws Exception {
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(40), Duration.ofSeconds(30));

        awaitUntil("three heartbeats",
                () -> sent.stream().filter(s -> s.contains("ping")).count() >= 3);
    }

    @Test
    @DisplayName("The heartbeat is an SSE comment, so no client has to know about it")
    void theHeartbeatIsAComment() throws Exception {
        // Both parsers already skip anything that is not `event:` or `data:`. A named event
        // would have needed a release on the web AND on Android before the server could send
        // it; a comment is safe against every build already in the wild.
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(40), Duration.ofSeconds(30));

        awaitUntil("the first heartbeat", () -> !sent.isEmpty());
        assertThat(sent.get(0)).startsWith(":").contains("ping");
        assertThat(sent.get(0)).doesNotContain("event:");
    }

    // ─── The deadline ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("A stalled stream is ended with a readable error, not left to the servlet")
    void endsAStalledStreamWithAnErrorEvent() throws Exception {
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(30), Duration.ofMillis(60));

        awaitUntil("the timeout error",
                () -> sent.stream().anyMatch(s -> s.contains("event:error")));

        String all = String.join("", sent);
        assertThat(all).contains(ChatStreamGuard.TIMED_OUT_MESSAGE);
    }

    @Test
    @DisplayName("The timeout message names a cause and something the user can do")
    void theMessageIsActionable() {
        // "Server error: 500" was neither. This is the whole user-facing point of the class,
        // so it is asserted rather than left to whoever edits the string next.
        assertThat(ChatStreamGuard.TIMED_OUT_MESSAGE)
                .contains("overloaded")
                .contains("again")
                .contains("provider");
        // The app's rule: no emoji anywhere, including in messages from the server.
        assertThat(ChatStreamGuard.TIMED_OUT_MESSAGE).doesNotContainPattern("[\\x{1F300}-\\x{1FAFF}]");
    }

    @Test
    @DisplayName("The stream is completed after the deadline, so nothing writes to it again")
    void completesTheEmitterOnDeadline() throws Exception {
        // Asserted on complete() rather than on an onCompletion callback: those are invoked
        // by Spring's return-value handler, which only exists once there is a real servlet
        // response behind the emitter.
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(30), Duration.ofMillis(60));

        verify(emitter, timeout(5_000)).complete();
    }

    @Test
    @DisplayName("The deadline fires once, not once per tick")
    void theDeadlineFiresOnce() throws Exception {
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(20), Duration.ofMillis(40));

        awaitUntil("the timeout error",
                () -> sent.stream().anyMatch(s -> s.contains("event:error")));
        // Long enough for several more ticks, had anything still been scheduled.
        Thread.sleep(300);

        assertThat(sent.stream().filter(s -> s.contains("event:error"))).hasSize(1);
    }

    // ─── Stopping cleanly ─────────────────────────────────────────────────────

    @Test
    @DisplayName("A turn that finishes normally leaves nothing scheduled behind it")
    void completingTheStreamStopsTheHeartbeat() throws Exception {
        // The guard stops itself through the emitter's own completion callback, so the test
        // fires that callback rather than calling complete() — which, with no servlet behind
        // the emitter, would not invoke it.
        SseEmitter emitter = recordingEmitter();
        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(30), Duration.ofSeconds(30));
        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(onCompletion.capture());

        awaitUntil("the first heartbeat", () -> !sent.isEmpty());
        onCompletion.getValue().run();
        int afterComplete = sent.size();

        Thread.sleep(300);   // many ticks' worth

        assertThat(sent).hasSize(afterComplete);
    }

    @Test
    @DisplayName("The guard hooks all three ways a stream can end")
    void hooksEveryWayTheStreamCanEnd() throws Exception {
        // Completion, the servlet timeout and an error are three separate callbacks, and a
        // guard wired to only one of them would leave a heartbeat ticking against a dead
        // response for the rest of the deadline.
        SseEmitter emitter = recordingEmitter();

        ChatStreamGuard.start(emitter, scheduler,
                Duration.ofSeconds(30), Duration.ofSeconds(30));

        verify(emitter).onCompletion(any(Runnable.class));
        verify(emitter).onTimeout(any(Runnable.class));
        verify(emitter).onError(any());
    }

    @Test
    @DisplayName("Cancelling twice is harmless — all three emitter callbacks may fire")
    void cancelIsIdempotent() throws Exception {
        SseEmitter emitter = recordingEmitter();
        ChatStreamGuard guard = ChatStreamGuard.start(emitter, scheduler,
                Duration.ofMillis(30), Duration.ofSeconds(30));

        guard.cancel();
        guard.cancel();
        int afterCancel = sent.size();

        Thread.sleep(200);

        assertThat(sent).hasSize(afterCancel);
    }

    // ─── The one thing the numbers have to satisfy ────────────────────────────

    @Test
    @DisplayName("The deadline sits between the provider retries and the servlet's own timeout")
    void theDeadlineFitsBetweenTheRetriesAndTheServlet() {
        // Two orderings, and both matter:
        //
        //  * the retry budget must finish INSIDE the deadline, or a turn is cut off while it
        //    is still legitimately trying and the retry never gets to help;
        //  * the deadline must fire BEFORE the servlet's, or Spring's
        //    AsyncRequestTimeoutException wins the race and the user gets the same bare 500
        //    this whole change exists to remove.
        Duration retriesWorstCase = LlmHttp.TOTAL_BUDGET.plus(LlmHttp.REQUEST_TIMEOUT);

        assertThat(retriesWorstCase).isLessThan(ChatStreamGuard.DEADLINE);
        assertThat(ChatStreamGuard.DEADLINE).isLessThan(AiChatService.SSE_TIMEOUT);
    }

    @Test
    @DisplayName("The heartbeat is frequent enough to be seen well before the deadline")
    void theHeartbeatIsFrequentEnoughToMeanAnything() {
        assertThat(ChatStreamGuard.HEARTBEAT).isLessThan(ChatStreamGuard.DEADLINE.dividedBy(4));
    }
}
