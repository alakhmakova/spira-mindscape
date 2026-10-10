# One GraphQL request can do the work of hundreds, and the rate limit counts it once

- **ID:** BUG-098
- **Status:** ✅ Fixed
- **Reported by:** Found in a security review the owner asked for (2026-10-10: "что если ddos когда
  отправлено слишком много запросов автоматически?") — not by a user, and not by any test
- **Area:** Backend / security (`graphql/`, `security/RateLimitFilter`)
- **Severity:** **Medium** — denial of service and a bypass of the per-user rate limit by any
  signed-in user. No data of anyone else's is reachable through it

## Summary

`RateLimitFilter` allows **120 `POST /graphql` a minute** per user. It counts **HTTP requests**,
and in GraphQL a request is not a unit of work. Nothing else bounded what one request could ask
for, so three shapes multiplied it:

| Shape | Example | What one request did |
|---|---|---|
| **Aliased mutations** | `mutation { a1: createUpcomingEvent(…) a2: createUpcomingEvent(…) … a500: … }` | 500 writes — each a full transaction — counted as one call. At 120 calls a minute that is **60,000 writes a minute** from one account |
| **Aliased queries** | `{ a1: goals { … } a2: goals { … } … }` | the whole goal graph loaded and serialised once per alias, all on one request thread |
| **Deep nesting** | `goals { targets { items … } }` or introspection's `ofType { ofType { … } }` | as deep as the schema allows, unchecked |

The gap was **known and written down** — `docs/security-model.md` §12 listed "GraphQL
query-complexity limits" as still to do — but nothing tracked it as a bug, so it stayed there.

**The test suites were using the hole themselves.** `tests-e2e/test_goal_cap_e2e.py` and
`e2e/goal-cap.spec.ts` filled the 50-goal allowance with **one request of 50 aliased
`createGoal`s**, and cleaned up with one request of 50 `deleteGoal`s — documented in their own
headers as the way to stay under the rate limit. That is precisely the amplifier: a pattern that
works for a test works for a script.

## Steps to reproduce

Signed in (any account; locally the `local` profile signs every request in as `dev@local`):

```
POST /graphql
{"query": "mutation { m0: createUpcomingEvent(input: {kind: \"important\", title: \"x\", date: \"2026-12-31T00:00:00Z\"}) { id } m1: … m499: … }"}
```

**Before the fix:** one 200 response and 500 rows in `upcoming_event` (up to the per-user cap of
500), with the rate limiter's bucket down by one. The same body with `goals { id title }` aliased
300 times returns 300 copies of the goal list.

## Root cause

The defence was built for REST, where one request is one operation: a per-request token bucket is
the right shape there and the wrong one for GraphQL. graphql-java ships the instrumentations that
close this (`MaxQueryDepthInstrumentation`, `MaxQueryComplexityInstrumentation`), and Spring Boot
registers any `Instrumentation` bean automatically — none was configured. There is no built-in cap
on the number of root mutation fields at all.

What *was* already bounded, and why it was not enough: graphql-java stops parsing a document at
15,000 tokens, which still leaves room for hundreds of aliases, and Cloud Run caps a body at
32 MiB.

## Fix approach

Make the request the unit the rate limit already assumes — `graphql/GraphQlLimitsConfig.java`,
three `Instrumentation` beans:

- **Depth ≤ 15** — `MaxQueryDepthInstrumentation`. The web's deepest query is 4; 15 leaves the
  `local` profile's introspection tooling (≈13) working.
- **Complexity ≤ 500** — `MaxQueryComplexityInstrumentation`, one point per field. The web's
  largest operation (`Goals`) is about 70; Android's `GetGoal` is smaller.
- **≤ 10 root fields in a mutation** — `MaxMutationFieldsInstrumentation`, written for this, since
  graphql-java has none. It **follows fragment spreads and inline fragments**, so
  `mutation { ...F } fragment F on Mutation { a1: … a11: … }` counts the same as writing them out.
  Every mutation either client sends has exactly one root field.

All three are tunable (`spira.graphql.max-depth`, `max-complexity`, `max-mutation-fields`). They
refuse **before execution**, so a refused mutation writes nothing — not the first ten of its
copies.

The two test files batch at the cap instead: ten per request, five requests for fifty, still far
inside the rate limit.

## How to verify fixed

- `GraphQlLimitsIntegrationTest` (5 tests): the web client's real `Goals` query passes; 300
  aliased queries are refused for complexity; a 16-deep query is refused for depth; one mutation
  is fine and **eleven aliased ones are refused and write nothing**; eleven hidden in a fragment
  are refused too. **Confirmed red on the old code**: with the config disabled, 4 of the 5 fail
  (the "real query passes" one passes either way, as it should).
- `test_goal_cap_e2e.py` and `e2e/goal-cap.spec.ts` pass in batches of ten, and leave 0 goals
  behind.
- By hand: the reproduction above answers with
  `Too many mutations in one request (500 > 10)` and no rows.

## Resolution

Fixed 2026-10-10. Files: `backend/.../graphql/GraphQlLimitsConfig.java` (new),
`backend/.../graphql/GraphQlLimitsIntegrationTest.java` (new), `tests-e2e/test_goal_cap_e2e.py`,
`e2e/goal-cap.spec.ts`, `docs/security-model.md` §7 (and the item struck from §12).

Three things worth keeping:

- **A per-request limit only bounds work if a request is a bounded amount of work.** In GraphQL it
  is not, until something makes it one.
- **When a test needs a workaround to stay under a limit, look at what the workaround is.** Fifty
  writes in one request was written down as a convenience and was the attack, step for step.
- **Not covered, deliberately:** a volumetric flood is not something the app can absorb. Google's
  front end takes the network-level floods, and `--max-instances 1` caps the bill at the price of
  an outage under a sustained application-level flood. Putting the domain behind Cloudflare's free
  proxy is the next step if that ever matters — an infrastructure decision for the owner, not a
  code change.
