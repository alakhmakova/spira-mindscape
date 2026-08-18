# Mobile sign-in fails on a Cloud Run cold start: the client gives up one second early

- **ID:** BUG-040
- **Status:** ✅ Fixed (2026-08-18) — see Resolution.
- **Reported by:** User (2026-08-18, "google sign in failed on android")
- **Area:** Android — `data/net/Network.kt`, `ui/auth/AuthViewModel.kt`
- **Type:** Defect

## Summary

"Continue with Google" fails on a real phone with **"Google sign-in failed. Please try again."**,
while the backend logs `auth_signin_success` for that very request. Trying again usually works.

The message is doubly misleading: nothing about Google failed, and the sign-in *did* happen on the
server — the phone simply stopped listening before the answer arrived, so it never received the
session cookie.

## Steps to reproduce

1. Leave the app closed long enough for Cloud Run to scale the backend to zero (~15 min idle).
2. Open the app on a phone and tap **Continue with Google**.
3. Pick an account. The Google sheet completes normally.
4. The app shows "Google sign-in failed. Please try again." — and Cloud Logging shows
   `auth_signin_success method=mobile userId=…` at that moment.

Warm, it never reproduces, which is what makes it look intermittent.

## Root cause — measured in production logs (2026-08-18)

`Network.kt` built its `OkHttpClient` **with no timeouts at all**, so OkHttp's defaults applied:
connect 10s, **read 10s**, write 10s, and no overall call timeout.

The backend runs on Cloud Run with no minimum instance, so the request that arrives after a
scale-to-zero pays for a JVM cold start. Every sign-in that day shows the same shape — container
start, then the request served about **eleven seconds** later:

| Container start | `auth_signin_success` | Gap |
|---|---|---|
| 12:02:48 | 12:02:59 | 11s |
| 12:51:05 | 12:51:16 | 11s |
| 13:13:03 | 13:13:14 | 11s |

Eleven seconds against a ten-second read timeout. The first call after an idle period loses that
race by about a second; a retry hits a warm container and returns instantly, which is why it reads
as flaky rather than as a timeout.

The second half of the defect is the **message**. A `SocketTimeoutException` fell into
`AuthViewModel`'s catch-all `Exception` branch, whose wording — "Google sign-in failed" — names the
one component that was working. That sent the first diagnosis of this report towards SHA-1 and
OAuth-client checks (both fine, see `mobile-sign-in-developer-error-10.md`) before the logs showed
the server answering successfully all along.

## Fix approach

- Set the client's timeouts explicitly, sized for a cold start rather than for a warm call, and add
  an overall **call** timeout so a dead network still fails promptly instead of hanging.
- Give a timeout its **own** message, so "the server was slow" is never again reported as "Google
  failed".

## How to verify fixed

- Leave the app idle until the backend scales to zero, then sign in: it completes rather than
  failing on the first attempt.
- The AI chat stream still runs longer than the new read timeout without being cut
  (`AiApi.streamClient` overrides read/call to "no limit" — check a long answer still finishes).
- Turn the network off entirely and sign in: the failure appears within the connect timeout, not
  after a minute.

## Resolution

**2026-08-18 — fixed.** `Network.kt` now sets connect **20s**, read **45s**, write **30s** and an
overall call timeout of **60s**. Read is sized at roughly four times the measured cold start; the
call timeout is the cap that keeps a long read timeout from turning a dead network into a frozen
screen. `AiApi.streamClient` already derives from this client and overrides read/call to "no limit",
so streaming is unaffected — its comment, which referred to "OkHttp's default 10s read timeout",
was corrected to point at the shared client instead.

`AuthViewModel` gained a branch for `InterruptedIOException` (the supertype of both
`SocketTimeoutException` and OkHttp's call-timeout error): **"The server took too long to answer.
Please try again."**

### What this does not fix

The cold start itself. Eleven seconds is still eleven seconds of waiting on the first launch after
an idle period. Removing it means giving the Cloud Run service a **minimum instance of 1**, which
costs money around the clock — an owner's decision, not a code change, so it is deliberately not
done here.

Related: `mobile-sign-in-developer-error-10.md` (BUG-002) — the *other* reason mobile sign-in fails,
ruled out for this one: the debug SHA-1 is unchanged and both OAuth clients still exist.
