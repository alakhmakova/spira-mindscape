# Rate limits multiply by the number of instances, and the bucket map never empties

- **ID:** BUG-057
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-27 — "нам нужно делать с мыслью о многих пользователях сразу, а
  не получить потом кучу проблем, когда пользователей станет больше", after the in-memory design
  surfaced while sizing the Cloud Run service
- **Area:** Backend / security (`security/RateLimitFilter`, `security/SharedRateLimitStore`)
- **Severity:** **Medium today, High as soon as there is more than one user** — the guard on the
  endpoints that cost real money weakens in proportion to load, and the structure holding it grows
  without bound from unauthenticated input

## Summary

`RateLimitFilter` counts requests per caller per minute and answers `429` over the limit. It
protects the endpoints where abuse costs something: `POST /api/ai/chat` (20/min — every call spends
the user's provider key), `POST /graphql` (120/min), `POST /api/ai/keys` (10/min), Google sign-in
(10/min), and the unauthenticated `POST /api/client-errors` (10/min).

The counters lived in a field:

```java
private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
```

with a comment that named the problem and left it:

> *Single-instance design: a ConcurrentHashMap of buckets is enough for this app's scale (Cloud Run
> rarely runs more than one instance). If it ever scales out, swap to a shared store.*

Two defects follow from that, and both get worse exactly as the app gets busier.

**1. The limit is multiplied by the instance count.** Each instance keeps its own counters and Cloud
Run spreads requests across them, so "20 chat messages a minute" really means 20 × however many
instances happen to be running:

| Instances | Real chat limit |
|---|---|
| 1 | 20/min |
| 2 | 40/min |
| 10 (autoscaled under load) | 200/min |

The number rises with traffic — that is, the guard is weakest at the moment it is most needed — and
nobody can state what the limit is without looking up the current instance count.

**2. Nothing is ever evicted from the map.** Every distinct caller key creates a `Bucket` that lives
for the life of the instance. For the unauthenticated endpoints the key is the client's IP:

```java
return "ip:" + request.getRemoteAddr();
```

so the map's size is a function of how many addresses have ever sent a request — something a
stranger can grow on demand, in a 512 MiB container.

A third, smaller consequence: Cloud Run recycles instances constantly, and each new one starts with
empty counters. A caller who hit a limit could get a fresh budget by waiting for a restart rather
than for the refill.

## Steps to reproduce

**The multiplied limit** (needs more than one instance, which is why nothing had ever seen it):

1. Set `maxScale` above 1 on the Cloud Run service.
2. Send `POST /api/ai/chat` as one user faster than 20 a minute.
3. **Before the fix:** roughly 20 × the number of live instances get through before the first `429`.

**The unbounded map:**

1. `POST /api/client-errors` from many different source addresses.
2. **Before the fix:** one `Bucket` per address is retained for the life of the instance, and
   nothing removes any of them.

## Root cause

The limiter was written as a single-process component and the app then became a horizontally
scalable one, without anything connecting those two facts. The comment shows it was a known
trade-off rather than an oversight — but a trade-off recorded in a comment is not one anybody is
reminded of, and the only test that existed created one filter in one JVM, so no test could
possibly have failed.

## Fix approach

The filter now decides only **which** limit applies and **who** the caller is; the counting moved
behind a `RateLimitStore` interface, whose one production implementation keeps buckets in Postgres
(`rate_limit_bucket`, migration `V21`).

- **One statement per token.** Refilling and spending happen in a single `UPDATE` whose `WHERE`
  clause is the check, because two instances that each read "one token left" would each spend it.
  A read-then-write that is correct inside one process is precisely what does not survive sharing.
- **Creation is separate from spending.** A missing bucket is inserted **full** and the token is
  then taken by the same `UPDATE` that takes every other token, so there is exactly one place a
  token is ever spent. A duplicate key means someone else created it first, which is success —
  and waiting on the unique index is what makes it correct, since the second inserter blocks until
  the first commits and therefore sees a committed row afterwards.
- **Idle buckets are deleted** (`IDLE_BEFORE_PURGE` = 30 min, far past the one-minute refill window,
  so a deleted bucket was full anyway and its absence decides nothing differently). Triggered by
  bucket creation rather than a scheduler — creation is exactly when the table grows, and a
  `@Scheduled` task would have been the app's first, a whole mechanism enabled for one `DELETE`.
- **A database failure allows the request**, and logs it. Failing closed would let a blip in the
  limiter become an outage of the endpoints it guards, and there is nothing to protect anyway:
  every one of them needs the same database to do its work.

**Why Postgres and not Redis.** Redis is the textbook answer and the wrong one here: a managed
service to pay for and operate, for a table holding one small row per active caller. Postgres is
already on the critical path of every protected endpoint, so this adds no new dependency — only one
round trip, on requests that were going to query the database anyway.

**Cost, since Neon egress has been a real problem on this project.** Measured against production:
the busiest hour in two days had **137** `POST /graphql` requests, and the app's highest-volume
request — the 10-second `GET /api/ai/chat/transcript/revision` poll, 360/hour — is **not rate
limited at all**, so it adds nothing. 137 extra single-row `UPDATE`s returning a row count is on the
order of **5 KB of egress in a peak hour**, against the ~51 KB that one `fetchGoals` costs. And
none of it wakes a suspended database: every one of those requests already queries.

## The bug inside the fix, and why the tests caught it

The refill arithmetic silently produced **zero tokens** — buckets never refilled at all — and the
integration test is what surfaced it:

```
RAW tokens=0.0  elapsed=30063  accrued=0.0
```

Thirty seconds of elapsed time, a rate of 0.001 tokens per millisecond, and an accrual of nothing.
A bare placeholder next to a `bigint` column lets the database infer the placeholder's type from its
neighbour, so H2 read the rate as an integer, `0.001` became `0`, and every product was zero. The
fix is an explicit `CAST(:tokensPerMilli AS DOUBLE PRECISION)`.

**The nastier half is that Postgres would very likely have got it right.** Left alone, this would
have been a limiter that behaved one way in the tests and another in production — the worst
possible outcome for a security control. It is recorded here because "clever SQL that leans on type
inference" is a trap this codebase can meet again.

## How to verify fixed

- `SharedRateLimitStoreIntegrationTest` (14 cases, against a real database):
  - `twoInstancesShareOneLimit` — two stores over one database allow five, not ten. **This is the
    case that could not be written before**, and the whole reason the class exists.
  - `aNewInstanceInheritsTheSpentBudget` — a restart is not a fresh budget.
  - `refillsOverTime`, `refillIsFractional`, `refillIsCappedAtCapacity` — the arithmetic, with time
    **seeded explicitly** rather than slept through: a sleep-based refill test is slow and dishonest,
    because the loop emptying the bucket takes real milliseconds during which it is already
    refilling, so what it proves depends on how fast the machine ran it.
  - `concurrentRequestsDoNotOverspend` — eight threads, forty attempts, capacity ten.
  - `aCreationRaceDoesNotRefuseTheFirstRequest` — four instances racing to create one bucket; all
    four are served. This one failed at 9-of-10 during development and is what found the
    create-versus-spend seam described above.
  - `idleBucketsArePurged`, `activeBucketsSurviveThePurge` — the eviction half.
- `RateLimitFilterTest` (6 cases) — unchanged in what it asserts, now against a fake store: which
  paths are limited, keying by principal versus IP, the `429` and its `Retry-After`, and that the
  filter is inert when disabled.

## Resolution

Fixed 2026-08-27.

Three things worth keeping:

- **A known trade-off written only in a comment is not a decision anyone revisits.** The comment
  correctly predicted the failure and sat there through every review; what was missing was a test
  that could fail.
- **"It works in one process" is not a property the tests were checking.** The old test created one
  filter in one JVM, so the defect was invisible by construction. `twoInstancesShareOneLimit` is
  four lines and would have caught it any day of the last year.
- **Type inference in SQL is a portability bug waiting to happen.** An untyped placeholder took its
  type from the column beside it and turned a rate into zero, on one engine and not the other.


## Corrections from the code review (2026-08-28)

- **The documented fail-open did not cover the expected failure.** The catch was
  `DataAccessException`, but Postgres being unreachable or the pool being exhausted surfaces from
  `@Transactional` as `CannotCreateTransactionException`, which descends from `TransactionException`
  and is **not** a `DataAccessException`. It would have escaped the store, escaped the filter, and
  turned every rate-limited endpoint into a 500 — the precise outage the class doc claims to
  prevent. Now catches `RuntimeException`, which is the right breadth for a guard whose failure
  mode must be "step aside".
- **A blocked request cost the database more than an allowed one.** `refillAndConsume` returning 0
  is ambiguous, and the code assumed "absent" and always attempted the INSERT — so every throttled
  request ran an update, a failing insert, a rollback, a second update, and left a Postgres error
  line. That is backwards for the request a flood is made of. It now asks `existsById` first: one
  cheap indexed read tells the two cases apart.
