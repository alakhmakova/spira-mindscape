# A newly uploaded PDF resource disappears right after opening it (mobile)

- **ID:** BUG-011
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** User
- **Area:** Frontend web app — resources (`src/lib/spira/api.ts`, `src/lib/spira/store.ts`)
- **Severity:** High (looks like data loss — the user believes the upload was lost)

## Summary

On a phone (responsive web app over ngrok), uploading a PDF resource and then tapping it to
preview made the **resource vanish from the Resources list entirely**. It looked like the file
had never been saved. In reality the resource **was persisted on the server** — only the
client rolled it back, so a page reload brought it back.

## Steps to reproduce

1. Open the web app on a phone (or any flaky/slow connection).
2. Open a goal → **Resources** → **Add resource** → type **File** → pick a **PDF** (a real,
   multi-MB one — a tiny PDF often succeeds and hides the bug).
3. Submit, then tap the new resource chip to preview it.

**Expected:** the PDF opens in the preview and the chip stays in the list.
**Actual:** the chip disappears from the list. Reloading the page brings it back (proving the
server had stored it all along).

## Root cause (confirmed)

The `createResource` GraphQL mutation requested the **full `RESOURCE_FIELDS` back, including
`dataUrl`** — which for a file is the entire file re-encoded as a multi-MB base64 data URL. So
every upload transferred the file **twice**: once up, once back down in the mutation response.

On a mobile/ngrok connection that large response frequently failed mid-write. The backend log
showed it plainly:

```
ERROR ... Unhandled REST exception ... on POST /graphql
org.springframework.web.context.request.async.AsyncRequestNotUsableException:
  ServletOutputStream failed to write: java.io.IOException: Connection reset by peer
Caused by: org.apache.catalina.connector.ClientAbortException: ... Connection reset by peer
```

The server had **already committed** the resource; only the response write failed. The client's
`fetch` therefore rejected, and `store.ts → addResource(...).catch(...)` did what it is
supposed to do on a failed create — **roll back the optimistic resource** by filtering out the
temp id. Net effect: saved on the server, removed from the UI. Hence "it disappeared".

(Two separate things made the same class of failure worse — see also **BUG-012**, which removed
file bodies from the goals-list query.)

## Fix approach

Stop echoing file bytes in mutation responses. The client just sent the `dataUrl`; it does not
need the server to send it back.

## How to verify fixed

1. On a phone, add a **multi-MB PDF** resource to a goal and open it.
2. The chip stays in the list and the PDF renders; no `Connection reset by peer` for that
   request in the backend log.
3. Reload — the resource is still there (server and client agree).
4. `npm run test:e2e` → `e2e/pdf.spec.ts` (add a PDF resource → open → canvas renders) passes.

## Resolution

Fixed client-side only (no backend or schema change). Files changed:

- **`src/lib/spira/api.ts`** — added `RESOURCE_META_FIELDS`: the same selection set as
  `RESOURCE_FIELDS` but **without `dataUrl`**. `createResource` and `updateResource` now select
  `RESOURCE_META_FIELDS`, so mutation responses are small. Because the response no longer
  carries the bytes, both methods **re-attach the `dataUrl` the caller just sent** to the
  returned object, so the in-memory resource still has its contents.
- Also hardened in the same pass: `resourceInput()` never sends an **empty** `dataUrl`
  (`dataUrl: "dataUrl" in resource && resource.dataUrl ? … : undefined`), so an update of a
  file whose bytes are not loaded cannot blank the stored file.

Verification: `npx tsc --noEmit` clean; `npm test` → 75 passed; `npm run test:e2e` →
`pdf.spec.ts` passed.
