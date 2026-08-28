package com.spiramindscape.backend.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps a chat stream <b>alive while it is working</b> and <b>ends it readably when it is
 * not</b> (BUG-055).
 *
 * <h2>The failure it replaces</h2>
 *
 * <p>A chat whose provider went quiet did nothing at all for three minutes — no bytes, no
 * message, an empty bubble — and then Spring's own {@code AsyncRequestTimeoutException}
 * turned the request into a bare <b>500</b>. Cloud Run's logs for 25–27 Aug 2026 are full of
 * it: {@code POST /api/ai/chat} at 180.5–184s, 1107 bytes of response, not one token.
 *
 * <p>Two separate things were missing, and this class is both:
 * <ul>
 *   <li>a <b>heartbeat</b>, so the connection is not silent while the model thinks and the
 *       client can tell "working" from "dead";</li>
 *   <li>a <b>deadline of our own</b>, hit before the servlet's, so the turn ends with an
 *       {@code error} event the user can read instead of an HTTP status they cannot.</li>
 * </ul>
 *
 * <h2>Why the deadline is absolute rather than idle-based</h2>
 *
 * <p>An idle deadline — "nothing for 60s" — would be kinder to a legitimately long agentic
 * run, but it needs every producer of output to report activity, which means threading a
 * handle through the whole loop. The absolute deadline needs nothing threaded anywhere and
 * is strictly better than what it replaces: the turn was already being killed at 180s, so
 * the only thing that changes is that the user is told why, 30 seconds sooner. If long
 * multi-tool turns ever become common enough to be cut off by this, the answer is an idle
 * clock, not a bigger number.
 *
 * <h2>The heartbeat is an SSE comment, deliberately</h2>
 *
 * <p>{@code :ping} rather than a named event, because both clients already ignore comment
 * lines ({@code ai-api.ts} and {@code AiApi.kt} each skip anything that is not
 * {@code event:} or {@code data:}). A new event name would have needed a release on both
 * surfaces before the server could safely send it; a comment works against every build ever
 * shipped, including whatever is on the owner's phone right now.
 *
 * <h2>Threading</h2>
 *
 * <p>The heartbeat writes from a scheduler thread while the agentic loop writes from a
 * worker thread, and interleaved writes would corrupt the SSE framing. <b>The emitter is
 * its own monitor</b>: this class and every send in {@code AiChatService} hold
 * {@code synchronized (emitter)} while writing.
 *
 * <p><b>The deadline is its own scheduled task, not a check inside the heartbeat.</b> Sharing
 * one repeating task made the deadline depend on the heartbeat's cadence, and a heartbeat
 * write can block: a client on a bad mobile link with a full TCP window stalls
 * {@code emitter.send} inside the lock, and with {@code scheduleWithFixedDelay} every later
 * tick is pushed back behind it. The deadline would then arrive late — past the servlet's own
 * timeout — and produce the bare 500 this class exists to replace, for a turn that had nothing
 * to do with the stuck client. A one-shot task fires at the right moment regardless, and the
 * scheduler is a small pool rather than one thread so a blocked write cannot hold up every
 * other conversation.
 */
final class ChatStreamGuard {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamGuard.class);

    /** How often the connection says it is still there. */
    static final Duration HEARTBEAT = Duration.ofSeconds(15);

    /**
     * The longest a single turn may run. Must stay under {@code AiChatService.SSE_TIMEOUT},
     * or Spring reaches its 500 first and this class does nothing at all — which is the whole
     * bug — and above {@code LlmHttp.TOTAL_BUDGET}, or a turn is cut off while the retry is
     * still legitimately trying. {@code ChatStreamGuardTest} asserts both orderings.
     */
    static final Duration DEADLINE = Duration.ofSeconds(150);

    /**
     * What the user reads when the deadline is hit. It names the likely cause and the two
     * things they can actually do, because "Server error: 500" named neither.
     */
    static final String TIMED_OUT_MESSAGE =
            "The AI provider stopped responding. It is probably overloaded — send that "
            + "again, or switch to another provider under \"Bring your own key\".";

    private final SseEmitter emitter;
    private final Duration deadline;
    private final AtomicBoolean finished = new AtomicBoolean();

    /**
     * Volatile, and assigned <b>after</b> scheduling rather than during construction: the
     * scheduled lambda captures {@code this} and, on a short clock, can run before the
     * assignment completes. A tick that reached the deadline first would then call
     * {@code cancel} on a null field. {@link #cancelTasks()} tolerates the gap and
     * {@link #schedule} closes it.
     */
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> deadlineTask;

    private ChatStreamGuard(SseEmitter emitter, Duration deadline) {
        this.emitter = emitter;
        this.deadline = deadline;
    }

    private void schedule(
            ScheduledExecutorService scheduler, Duration heartbeat, Duration deadlineAfter) {
        ScheduledFuture<?> beats = scheduler.scheduleWithFixedDelay(
                this::beat, heartbeat.toMillis(), heartbeat.toMillis(), TimeUnit.MILLISECONDS);
        ScheduledFuture<?> expiry = scheduler.schedule(
                this::endWithTimeout, deadlineAfter.toMillis(), TimeUnit.MILLISECONDS);
        this.heartbeatTask = beats;
        this.deadlineTask = expiry;
        // A task may have run and finished the guard before these fields were assigned.
        if (finished.get()) cancelTasks();
    }

    /**
     * Starts guarding {@code emitter} and wires itself to stop when the stream does, so a
     * normal turn leaves nothing scheduled behind it.
     */
    static ChatStreamGuard start(SseEmitter emitter, ScheduledExecutorService scheduler) {
        return start(emitter, scheduler, HEARTBEAT, DEADLINE);
    }

    /**
     * The same guard on a shorter clock. Only {@code ChatStreamGuardTest} uses it: the real
     * deadline is two and a half minutes, and a test that waited it out would be two and a
     * half minutes of nothing rather than a test.
     */
    static ChatStreamGuard start(
            SseEmitter emitter,
            ScheduledExecutorService scheduler,
            Duration heartbeat,
            Duration deadline) {
        ChatStreamGuard guard = new ChatStreamGuard(emitter, deadline);
        emitter.onCompletion(guard::cancel);
        emitter.onTimeout(guard::cancel);
        emitter.onError(e -> guard.cancel());
        guard.schedule(scheduler, heartbeat, deadline);
        return guard;
    }

    /** Stops both tasks. Safe to call more than once — all three callbacks can fire. */
    void cancel() {
        if (finished.compareAndSet(false, true)) {
            cancelTasks();
        }
    }

    /** Whether this stream is over — by completion, by error, or by the deadline. */
    boolean isFinished() {
        return finished.get();
    }

    private void cancelTasks() {
        ScheduledFuture<?> beats = heartbeatTask;
        if (beats != null) beats.cancel(false);
        ScheduledFuture<?> expiry = deadlineTask;
        if (expiry != null) expiry.cancel(false);
    }

    private void beat() {
        if (finished.get()) return;
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("ping"));
            }
        } catch (Exception e) {
            // The client hung up, or the turn completed between the check and the write.
            // Either way there is nothing left to keep alive.
            log.debug("chat_heartbeat_send_failed {}", e.toString());
            cancel();
        }
    }

    private void endWithTimeout() {
        if (!finished.compareAndSet(false, true)) return;
        cancelTasks();
        // WARN, not ERROR: the provider being slow is not a defect in this app, but it is
        // the number worth watching if chats start failing again. No user text, no ids.
        log.warn("chat_stream_deadline_exceeded seconds={}", deadline.toSeconds());
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("error").data(TIMED_OUT_MESSAGE));
                emitter.complete();
            }
        } catch (Exception e) {
            log.debug("chat_stream_deadline_close_failed {}", e.toString());
            emitter.completeWithError(e);
        }
    }
}
