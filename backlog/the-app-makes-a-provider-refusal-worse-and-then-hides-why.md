# The app spends more of an exhausted quota, then truncates away the reason

- **ID:** BUG-058
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-28 — "очень часто от gemeni я получаю такую ошибку … * Quota
  exceeded for metric: generativelanguage.googleapis.c…"
- **Area:** Backend (`ai/provider/LlmHttp`, `ai/chat/AiChatService`)
- **Severity:** **High** — the app was consuming three times the quota per failure, on a free tier
  of twenty requests a day, and then hiding which model and which allowance from the user

## Summary

Two defects, both introduced or left in place by the retry work of BUG-055, and both of which made
a provider's refusal worse rather than better.

**1. A quota refusal was retried, three times.** 429 was in the retryable set, so every "you have
run out" cost three requests instead of one. The local log, from the owner's own session:

```
llm_http_retry host=generativelanguage.googleapis.com attempt=1 reason=status_429
llm_http_retry host=generativelanguage.googleapis.com attempt=2 reason=status_429
```

Against what Google actually said:

```
"quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
limit: 20, model: gemini-3.5-flash
Please retry in 57.880548541s.
```

**Twenty requests per day.** The retry was spending three of them per failure and finishing 56
seconds before the window Google named — the backoff is 0.7 s and 1.4 s, so it could not have
helped even in principle.

**2. The message that explained it was cut off.** `friendlyError` truncated the provider's text at
300 characters. Google's refusal is 405 characters and spends its first 235 on an apology and two
documentation URLs, so the cut landed here:

```
…To monitor your current usage, head to: https://ai.dev/rate-limit.
* Quota exceeded for metric: generativelanguage.googleapis.c…
```

Everything actionable — `limit: 20`, `model: gemini-3.5-flash`, `Please retry in 57.8s` — was in
the discarded half. The owner reported the failure to us as that exact truncated string, because
that is all the app would show them.

## Steps to reproduce

1. Configure a Gemini key on the free tier and pick a model with a small daily allowance.
2. Send more than the allowance in a day.
3. **Before the fix:** each further message costs three requests, and the error stops mid-word at
   `generativelanguage.googleapis.c…`.

## Root cause

**The retry** treated 429 as one thing. It is two wearing one number: "too fast", where the
provider names a wait in `Retry-After` and waiting works; and "out of quota", where nothing helps
inside one request and every attempt spends more of what is already gone. The retry set was written
from the status code alone.

**The truncation** was written to keep a chat bubble a bubble, and picked the wrong end. Every
provider opens with an apology and closes with the specifics, so a head-truncation always throws
away the useful half.

## Fix approach

- 429 is out of `RETRYABLE_STATUSES` and has its own rule in `LlmHttp.shouldRetry`: **retry only
  when the response carries a `Retry-After` we can afford** (≤ 5 s). A bare 429 goes straight back.
- The provider-message cap goes from 300 to **600** characters (`PROVIDER_MESSAGE_MAX_CHARS`), which
  fits Google's refusal whole.

**Neither change favours one provider.** Anthropic and OpenAI both send `Retry-After` on a genuine
rate limit, so they keep their retry unchanged; OpenAI's `insufficient_quota` — a bare 429 — now
surfaces at once instead of after two wasted calls. Every 5xx, 52x and 529 is retried exactly as
before. The owner's constraint was explicit: *"нужно исправлять так чтобы и для Gemini стало лучше
и для других ничего не испортилось"*, and the rule is one rule for all of them.

The only case that loses a retry is a 429 with no `Retry-After` that was genuinely transient — and
the old 0.7 s backoff was far shorter than any per-minute window, so that retry was almost
certainly failing anyway.

## How to verify fixed

- `LlmHttpTest`: `doesNotRetryAQuotaExhausted429` (one attempt, handed back),
  `retriesA429ThatNamesAnAffordableWait` (two attempts),
  `doesNotRetryA429ThatAsksForTooLong` (a 300-second `Retry-After` is not slept on),
  `the429RuleIsNotTheStatusList`.
- `ProviderErrorMessageTest`, built on Google's real message copied from the log:
  `keepsTheActionablePartOfAQuotaError` asserts `limit: 20`, `gemini-3.5-flash` and `57.8` all
  survive; `aVeryLongMessageIsStillBounded` keeps the bubble bounded.

## Resolution

Fixed 2026-08-28.

Worth keeping: **a retry policy written from status codes alone will eventually retry something
that must not be retried.** 429 looked obviously transient and was, half the time. The signal that
tells the halves apart was in the response all along — whether the provider was willing to say when
to come back.

And: **truncating a message from the front assumes the important part is first.** For an error it
never is.
