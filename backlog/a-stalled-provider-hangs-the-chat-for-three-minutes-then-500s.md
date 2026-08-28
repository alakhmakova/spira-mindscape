# A stalled provider hangs the chat for three minutes, then 500s

- **ID:** BUG-055
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-27 — "что-то случилось с ai со всеми провайдерами в том числе
  сейчас на проде — они медленно отвечают, иногда ошибка 500 или 524"
- **Area:** Backend (`ai/provider/*`, `ai/chat/AiChatService`)
- **Severity:** **High** — the chat is the product's centre, and its worst case was three minutes
  of an empty bubble followed by "Server error: 500"

## Summary

Chats on production got slow from **25 Aug 2026 around 16:00** and some stopped answering at all.
Cloud Run's request log for `POST /api/ai/chat`:

| When | Revision | Latency |
|---|---|---|
| 15–24 Aug | 00037–00041 | 2–25 s, occasional outliers |
| **25 Aug 16:17 onward** | **00041** | 20–105 s |
| 26 Aug 07:09–07:24 | 00041 | 184, 182, 181, 180 s |
| 27 Aug 15:13–15:32 | 00041, 00045 | 4 × ~181 s → **500** |

The degradation begins on revision **00041**, deployed 22 Aug — the same build that answered in
4–18 s on the morning of the 25th. So it is not a code change: something outside the app got
slower, and the app had no way to cope with it.

Every 500 is `AsyncRequestTimeoutException on POST /api/ai/chat` at 180.5–184 s — exactly the
`SseEmitter` timeout — and every one has a response body of **1107 bytes**, byte for byte, meaning
**not a single token had been streamed**. Two of them carried only 4 KB of request, so it is not a
context-size problem: the provider simply never answered.

The `524` the owner saw is not ours. Cloud Run does not emit it; Cloudflare does, and several model
APIs sit behind Cloudflare. `friendlyError` passes a provider's own error text through to the user
verbatim, so a Cloudflare "the origin took too long" page in front of a model API surfaces inside
Spira as a Spira error.

## Root cause

Two omissions, both on the path every provider shares.

**1. No deadline on the model call.** `LlmProviderFactory` built the shared `HttpClient` with a
connect timeout only, and not one of the four providers put `.timeout(…)` on the request:

```java
this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))   // and nothing else
        .build();
```

Everything else in the app that calls out has one — `TavilySearchService` 20/30 s,
`UrlReadService` 20 s, `MistralOcrService` 90 s. The model call was the exception, so a provider
that accepted the connection and then said nothing held the worker thread until the emitter's own
three-minute limit fired and Spring turned the request into a bare 500.

**2. No retry.** `grep -rn "retry" ai/provider/` returned nothing. The one provider error the logs
did contain is Gemini's:

```
Gemini API error 503: "This model is currently experiencing high demand.
Spikes in demand are usually temporary. Please try again later."
```

An error that says, in words, to try again — and nothing did.

And nothing told the user any of it: the connection was silent for three minutes and then produced
an HTTP status, not a sentence.

## Steps to reproduce

Hard to reproduce on demand, because it needs a provider to stall. Deterministically:

1. Point a provider's endpoint at a socket that accepts the connection and never responds
   (`nc -l 443` with the URL overridden, or a proxy that black-holes the request).
2. Send a chat message.
3. **Before the fix:** nothing for 180 s, then `Server error: 500` in the panel and
   `AsyncRequestTimeoutException` in the log.

## Fix approach

Three parts.

- **`ai/provider/LlmHttp`** — one place all four providers send through. A 45 s per-request
  deadline, and up to three attempts with backoff on transient failures: 408, 425, 429, 5xx,
  Cloudflare's 520–524, and Anthropic's 529, plus transport failures. A provider's own
  `Retry-After` is honoured up to a 5 s cap. Deliberately *not* retried: 400, 401, 403, 404, 413,
  422 — a bad key stays bad, and retrying it would triple the delay before the user reads the one
  message that would help them. The discarded attempt's body is closed, because `ofLines()` hands
  back a stream still holding the connection and leaking one per retry would exhaust the pool under
  exactly the trouble that caused the retry.
- **`ai/chat/ChatStreamGuard`** — a heartbeat every 15 s so the connection is not silent while the
  model thinks, and a 150 s deadline of our own that ends the turn with a readable `error` event
  instead of letting the servlet's 180 s produce a 500. The heartbeat is an SSE **comment**
  (`:ping`), because both clients already skip comment lines, so it works against every build
  already on a phone without a release.
- All SSE writes now go through one `synchronized (emitter)` helper: the heartbeat writes from a
  scheduler thread while the agentic loop writes from a worker thread, and interleaved writes would
  corrupt the framing.

The three clocks have to stay in order — retries (45 + 100 s) < guard deadline (150 s) < servlet
timeout (180 s) — or the retry only ever produces the 500 it exists to prevent. A test asserts it.

**What the request timeout does not cover:** `HttpRequest.timeout` bounds the wait for the response
*headers*; with a streaming body handler, a stall after the first byte is the guard's job. They are
two mechanisms because they are two different failures.

## How to verify fixed

- `LlmHttpTest` — the retry set, the non-retry set, exhaustion, backoff, `Retry-After` and its cap,
  and that a discarded attempt's body is closed.
- `LlmProviderHttpContractTest` — a table over all four providers: each sets the deadline, and each
  recovers from a 503 instead of failing the turn. A fifth provider is a row in it.
- `ChatStreamGuardTest` — the heartbeat is a comment, a stalled stream ends with the readable
  error, the deadline fires once, the guard stops when the stream does, and the three clocks are in
  order.
- On production, after deploy: `POST /api/ai/chat` should stop showing 180 s latencies, and
  `chat_stream_deadline_exceeded` / `llm_http_retry` in Cloud Logging show how often providers are
  actually misbehaving.

## Resolution

Fixed 2026-08-27, alongside BUG-054 (found while reading this code) and BUG-056.

Two lessons:

- **Every call out of this app needs a deadline, and the AI ones were the only ones without.**
  They were also the slowest and least reliable — the exact place the discipline mattered most.
- **A copied-and-pasted class multiplies an omission.** Four providers, one missing line, four
  places to forget it. The contract test is one table over all of them so the next provider cannot
  quietly skip it.

Two things deliberately **not** done here. The Cloud Run sizing — `maxScale: 1`, 512 Mi — is left
alone, because nothing in three days of logs points at resources: no OOM, ordinary endpoints
answering in 100–400 ms, and the provider stall fully explains the 500s. (A first draft of this
file blamed the 15:32 500 on a deployment dropping a live chat. **That was wrong**: it ran on a
healthy `spira-00045` whose own shutdowns were at 15:55 and 16:18, and it simply timed out like the
other three. `maxScale` would not have changed it either way — a rollout drains the old revision
whatever the ceiling is.) The follow-up is GRO-159, which says what to measure and when.

And the deadline is absolute rather than idle-based, because an idle clock needs every producer of
output to report activity. If long multi-tool turns start getting cut off at 150 s, the answer is
the idle clock, not a bigger number.


## Corrections from the code review (2026-08-28)

Three defects in the first version of this fix, all found by `/code-review` and all fixed:

- **One scheduler thread for every stream.** A heartbeat write can block — a client on a bad
  mobile link with a full TCP window stalls `emitter.send` inside the lock — and with a single
  shared thread every other conversation then stopped ticking. Their deadlines would have arrived
  late, past the servlet's own timeout, producing exactly the bare 500 this change exists to
  remove, for turns that had nothing to do with the stuck client. Now a pool of four.
- **The deadline rode on the heartbeat's cadence.** `scheduleWithFixedDelay` re-schedules only
  after each tick finishes, so slow writes pushed the deadline check later and later. The deadline
  is now its own one-shot task and fires at its own time regardless.
- **Nothing stopped the agentic loop when the stream ended.** The user was told the provider had
  stopped responding, and the worker carried on calling the provider for up to six more turns —
  billed, invisible — while `sendProposal` kept persisting proposal rows, so cards appeared for a
  conversation the user had already been told was over. `runAgenticLoop` now takes the guard and
  checks `isFinished()` between iterations, before the forced final turn, and before surfacing any
  proposal.

Plus a latent one: `ChatStreamGuard`'s task field was assigned from inside the constructor while
the scheduled lambda already captured `this`, so on a short clock a first tick reaching the
deadline could `cancel()` a null reference. The field is volatile now and scheduling happens after
construction.
