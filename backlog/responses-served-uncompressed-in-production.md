# Every response is served uncompressed in production

- **ID:** BUG-044
- **Status:** ✅ Fixed (2026-08-21) — see Resolution. **Takes effect on the next Cloud Run deploy.**
- **Reported by:** Found while answering the owner's question about ngrok's bandwidth (2026-08-21)
- **Area:** Backend — `backend/src/main/resources/application.properties`
- **Type:** Defect (performance / cost)

## Summary

The live site sends every asset and every API response **uncompressed**. The main JS bundle goes
out at **431 KB where it gzips to 128 KB**, and a plain goals query at **41 KB where it gzips to
6 KB**.

Nothing errors, nothing looks broken — the app is simply ~3.4× heavier than it needs to be for
every visitor on every cold load, and every API call is ~6.5× heavier than it needs to be. It hurts
most on mobile data, and it is billable egress on Cloud Run.

It went unnoticed for the same reason it is worth writing down: **there is no failure to see.** No
log line, no error state, no test that could have caught it. Only a measurement.

## Steps to reproduce

Against the deployed site, before the fix:

```bash
curl -s -o /dev/null -w "no gzip: %{size_download}\n" \
  https://spira-952567559986.europe-west1.run.app/assets/index-CwHNEnYc.js
curl -s -H "Accept-Encoding: gzip" -o /dev/null -w "gzip:    %{size_download}\n" \
  https://spira-952567559986.europe-west1.run.app/assets/index-CwHNEnYc.js
```

Both print `431218`. The response carries **no `Content-Encoding` header at all**:

```
content-type: text/javascript
x-content-type-options: nosniff
server: Google Frontend
```

Vite's own build output states what that file should weigh on the wire:
`index-CwHNEnYc.js  430.43 kB │ gzip: 128.00 kB`.

## Root cause

Two things had to both be absent, and both were:

1. **Spring Boot does not compress by default.** `server.compression.enabled` defaults to `false`,
   and `application.properties` never set it. Every other config knob in that file was tuned;
   this one was simply never reached for.
2. **Nothing in front of the app compresses either.** Cloud Run's Google Frontend passes response
   bodies through untouched — it does not add gzip the way a CDN would. `server: Google Frontend`
   in the headers above is the proxy admitting it forwarded the body as it found it.

So the assumption "something upstream handles this" was never true, and nothing said so.

A second, narrower gap sits inside the fix: Spring's **default** `server.compression.mime-types`
list covers `text/javascript`, `text/css` and `application/json`, but **not**
`application/graphql-response+json` — which is the content type of every API answer this app
returns. Enabling compression without spelling the list out would have compressed the SPA's assets
and left the largest single responses raw.

## Fix approach

`backend/src/main/resources/application.properties`:

```properties
server.compression.enabled=true
server.compression.min-response-size=1024
server.compression.mime-types=text/html,text/xml,text/plain,text/css,text/javascript,\
application/javascript,application/json,application/xml,application/graphql-response+json,image/svg+xml
```

- The **type list is explicit**, for the `application/graphql-response+json` reason above.
- The **1 KB floor** skips bodies where the CPU costs more than the bytes saved.

## How to verify fixed

Locally, with the backend running:

```bash
Q='{"query":"query{goals{id title description confidence deadline createdAt}}"}'
curl -s -o /dev/null -w "raw  %{size_download}\n" \
  -X POST -H "content-type: application/json" -d "$Q" http://localhost:8080/graphql
curl -s -H "Accept-Encoding: gzip" -o /dev/null -w "gzip %{size_download}\n" \
  -X POST -H "content-type: application/json" -d "$Q" http://localhost:8080/graphql
```

Expect roughly `41441` then `6403`, and `Content-Encoding: gzip` plus `accept-encoding` in `Vary`.

A body **under 1 KB staying uncompressed is correct**, not a failure — `graphiql.html` (935 B)
returns 935 B either way, which is the floor doing its job.

After the next deploy, repeat the `curl` pair from "Steps to reproduce" against Cloud Run: the
`gzip` line should read roughly `128000`, and the response should carry `Content-Encoding: gzip`.

## Resolution

Fixed 2026-08-21 in `backend/src/main/resources/application.properties` (three lines added, with a
comment recording why the type list is spelled out).

Verified on the running backend: a goals query went from **41 441 B to 6 403 B** (6.5×), with
`Content-Encoding: gzip` and `Vary: …,accept-encoding` correct. `graphiql.html` at 935 B correctly
stayed uncompressed. The full backend suite passes — **866 tests, 0 failures**.

**Not yet live.** This is a change to the container's config, so the deployed site keeps sending
raw bytes until the next `gcloud run deploy spira --source .`.

### What this does not cover

- **Static assets could do better than gzip.** Brotli typically beats it by another 15–20% on JS,
  and the assets are built once, so they could be pre-compressed at build time and served with
  `Content-Encoding: br` rather than gzipped per request. Not done: gzip is the large, safe win and
  Brotli is a separate change.
- **The Vite dev server is still uncompressed**, and that is correct — on localhost compression
  costs CPU and saves nothing. It only matters if a dev server is pushed through a metered tunnel,
  which is what surfaced this: one cold load of the dev server measured **6.17 MB over 103
  requests**, and roughly 165 of those exhaust a 1 GB monthly ngrok allowance. The answer there is
  to tunnel the built bundle, not to compress the dev server.
