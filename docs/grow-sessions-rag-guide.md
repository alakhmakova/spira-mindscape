# GROW Sessions: Book-Grounded AI Coaching with RAG (pgvector)

This document explains everything that was built for GROW coaching sessions in
Spira: how the AI coach is grounded in real coaching books instead of a generic
prompt, how session timing and session memory work, and which tests cover it.
It is written for a beginner — if you read it top to bottom, you should be able
to repeat the same setup in your own project.

> **The one-sentence summary:** before every reply, the backend *searches two
> coaching books for the passages most relevant to what the user just said*,
> pastes those passages into the AI's instructions, and tells the model:
> "coach strictly by this method." That technique is called **RAG**.

---

## Table of contents

1. [What is RAG and why we need it](#1-what-is-rag-and-why-we-need-it)
2. [What is an embedding](#2-what-is-an-embedding)
3. [Why a Mistral key is required (and why Claude can't do this part)](#3-why-a-mistral-key-is-required)
4. [pgvector: turning a regular Postgres into an "AI database"](#4-pgvector-turning-a-regular-postgres-into-an-ai-database)
5. [The pipeline, step by step](#5-the-pipeline-step-by-step)
6. [How the AI actually uses the books](#6-how-the-ai-actually-uses-the-books)
7. [Session timing: the coach knows the clock](#7-session-timing-the-coach-knows-the-clock)
8. [Session memory: continuing where you left off](#8-session-memory-continuing-where-you-left-off)
9. [Proposals during sessions (and surviving page reloads)](#9-proposals-during-sessions)
10. [Hard guarantees (no silent fallbacks)](#10-hard-guarantees)
11. [Tests that were written](#11-tests-that-were-written)
12. [How to repeat this in your own project — checklist](#12-how-to-repeat-this-in-your-own-project)

---

## 1. What is RAG and why we need it

**RAG** stands for **Retrieval-Augmented Generation**. It is a pattern for
making a large language model (LLM) answer *from your documents* instead of
from its general training data:

1. **Retrieval** — find the few passages of your documents that are most
   relevant to the current question.
2. **Augmented** — paste those passages into the prompt you send to the model.
3. **Generation** — the model writes its reply using (and constrained by) them.

Why we needed it here: GROW sessions used to run on a short hand-written
"act like a coach" prompt. The product requirement was that the coach must
follow the method of two specific books — *Coaching for Performance* (John
Whitmore) and *Coach the Person, Not the Problem* (Marcia Reynolds) — **and
never fall back to generic coaching advice**. The books are far too big to
paste into every request (hundreds of pages; models have a context limit and
you pay per token), so instead we retrieve only the ~6 most relevant excerpts
per turn.

```
user message ──► find 6 most relevant book passages ──► system prompt =
                                                        coaching rules
                                                        + goal data
                                                        + those passages
                                                        ──► model replies
```

## 2. What is an embedding

Computers can't compare *meaning* of texts directly. An **embedding** is the
trick that makes it possible: a special AI model (an *embedding model* — not a
chat model) reads a piece of text and outputs a fixed-length list of numbers,
called a **vector**. In our case each text becomes **1024 numbers**.

The key property: **texts with similar meaning get vectors that are close to
each other** (mathematically: small cosine distance). The words don't have to
match at all:

| Text | Vector (sketch) |
|---|---|
| "I keep postponing work on my goal" | `[0.12, -0.83, 0.44, …]` |
| "Procrastination often hides a fear of failure" (from a book) | `[0.10, -0.79, 0.41, …]` ← close! |
| "Recipe for apple pie" | `[-0.91, 0.22, -0.05, …]` ← far away |

So "search by meaning" becomes: *embed the query, embed all the documents
once, and find the stored vectors nearest to the query vector.* That is the
entire magic of semantic search.

A bonus that matters for Spira: good embedding models are **multilingual**.
Our users write in Russian, the books are in English — a Russian sentence
about procrastination still lands close to the English book passage about
procrastination. Plain keyword search (SQL `LIKE`, full-text search) would
fail completely across languages; embeddings handle it natively.

## 3. Why a Mistral key is required

Spira is **BYOK** ("bring your own key") — the server stores no AI keys of its
own; every user saves their own provider keys (encrypted with AES-256-GCM in
the `ai_api_keys` table). Chat can run on any configured provider: Anthropic
(Claude), Mistral, Ollama.

Embeddings, however, are a **separate API** from chat — and **Anthropic does
not offer an embeddings API at all** (they point customers to third parties
such as Voyage AI). Out of the providers Spira supports, Mistral has a strong
multilingual embedding model:

| | Chat API | Embeddings API |
|---|---|---|
| Anthropic (Claude) | ✅ | ❌ none |
| Mistral | ✅ | ✅ `mistral-embed`, 1024 dimensions, multilingual |
| Ollama (local) | ✅ | ✅ (model-dependent; not wired up in Spira yet) |

So the rule is:

- **A Mistral API key is mandatory for GROW sessions.** It powers the book
  search (embedding the books once + embedding each user message at query
  time). Without it, a GROW session refuses to start with a clear error —
  there is deliberately no fallback to the generic prompt.
- **The chat provider stays the user's choice.** Claude can be the coach while
  Mistral only does the searching. The two keys never interact; the embedding
  client receives the decrypted Mistral key per call, exactly like the
  existing Tavily web-search integration does.

Nothing is hardcoded: the key is looked up per user
(`AiKeyService.getKey(ProviderType.MISTRAL)`) on every request.

## 4. pgvector: turning a regular Postgres into an "AI database"

You do **not** need a separate "vector database" product (Pinecone, Chroma,
Weaviate…) for a corpus of this size. Postgres has an extension called
**pgvector** that adds:

- a new column type `vector(N)` for storing embeddings,
- distance operators, e.g. `<=>` for cosine distance,
- (optionally) special indexes for huge datasets.

### Converting an existing Postgres setup

Two steps were needed; both keep all existing data intact.

**Step 1 — swap the Docker image.** The stock `postgres:16` image does not
ship the extension files. The drop-in replacement `pgvector/pgvector:pg16` is
the same Postgres 16 plus the extension. Changed in *both* compose files
(`backend/docker-compose.yml` and `deploy/production/docker-compose.yml`):

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16   # was: postgres:16
```

The data volume attaches unchanged (same Postgres major version, same data
directory layout). One real-world caveat we hit: the new image is built on a
slightly different Debian, so Postgres warned about a **collation version
mismatch**. Fix (run once per database, including `template1` and `postgres`):

```sql
REINDEX DATABASE spira;
ALTER DATABASE spira REFRESH COLLATION VERSION;
```

**Step 2 — enable the extension and create the table** via a normal Flyway
migration (`V13__grow_books.sql`):

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE book_chunk (
    id              BIGSERIAL    PRIMARY KEY,
    book            VARCHAR(128) NOT NULL,   -- "Coaching for Performance"
    ord             INT          NOT NULL,   -- chunk order inside the book
    content         TEXT         NOT NULL,   -- the passage itself
    embedding       vector(1024),            -- NULL until embedded
    embedding_model VARCHAR(64),             -- e.g. "mistral-embed"
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_book_chunk_book_ord UNIQUE (book, ord)
);
```

Notes a beginner should not skip:

- `vector(1024)` — the dimension **must equal your embedding model's output**
  (mistral-embed → 1024). Vectors of different models/dimensions are not
  comparable; changing models means re-embedding everything.
- **No vector index was created — on purpose.** Indexes like HNSW/IVFFlat are
  for tens of thousands of vectors and trade accuracy for speed. Our corpus is
  ~657 rows; an exact sequential scan is sub-millisecond and 100% accurate.
  Don't add infrastructure you don't need yet.
- The search query is plain SQL — cosine distance, smallest first:

```sql
SELECT book, ord, content
FROM book_chunk
WHERE embedding IS NOT NULL
ORDER BY embedding <=> CAST(? AS vector)   -- ? is '[0.12,-0.83,…]'
LIMIT 6;
```

### A testing trap worth knowing (H2 vs pgvector)

Spira's backend tests run on **H2** (an in-memory database) with Flyway
disabled and the schema generated from JPA entities. H2 has no `vector` type —
so if `book_chunk` were a JPA entity, every test would explode. The solution:

- `book_chunk` is **deliberately NOT a JPA entity**. It exists only via the
  Flyway migration and is accessed with `JdbcTemplate` and raw SQL
  (`BookChunkRepository`).
- The startup ingestion is gated by a property: `spira.books.enabled=false`
  in `application-test.properties` keeps it out of test contexts entirely
  (`@ConditionalOnProperty` on the runner bean).

## 5. The pipeline, step by step

All new backend code lives in one package:
`backend/src/main/java/com/spiramindscape/backend/ai/grow/`.

### 5.1 Books → plain text (manual, one-time)

The source books are `.docx`. They were converted to plain **UTF-8 `.txt`**
files (paragraphs separated by a blank line) and placed in
`backend/src/main/resources/books/`. The file name becomes the book title:
`coaching-for-performance.txt` → "Coaching for Performance".

Gotchas we actually hit while converting — check your files for the same:
- **BOM** (an invisible byte-order mark at the start) — strip it;
- **CRLF** line endings — normalise to LF;
- paragraphs separated by a *single* newline instead of a blank line — the
  chunker needs blank-line separation to see paragraph boundaries.

### 5.2 Chunking (`BookChunker`)

You can't embed a whole book as one vector — retrieval needs *passages*. The
chunker splits the text into pieces of **~1500 characters** built from whole
paragraphs, with a **~200-character overlap** carried from each chunk into the
next (so an idea cut at a boundary is findable from either side). Paragraphs
shorter than 40 characters (page numbers, table-of-contents noise) are
dropped. Result for our two books: **657 chunks**.

### 5.3 Ingestion at startup (`BookIngestionRunner`)

An `ApplicationRunner` (Spring code that runs once at startup) reads every
`classpath:books/*.txt`, chunks it, and inserts rows with **NULL embeddings**.
It is idempotent per book — if a book's chunks already exist, it skips. To
re-ingest after editing a book: `DELETE FROM book_chunk WHERE book = '<Title>'`
and restart.

Why NULL embeddings? Because of BYOK: at server startup there is **no API key
available** to compute them. Which brings us to…

### 5.4 Lazy embedding on first use (`GrowLibraryService.ensureEmbedded`)

The first time any user starts a GROW session, the backend notices unembedded
chunks and embeds them all using **that user's Mistral key**, in batches of 16
(`MistralEmbeddingClient` → `POST https://api.mistral.ai/v1/embeddings` with
`{"model":"mistral-embed","input":[…16 texts…]}`). This takes ~1–2 minutes,
once ever. Progress is streamed to the UI as SSE `status` events
("Preparing the coaching library… 240/657") so the user sees what's happening.

Details that matter:
- the method is `synchronized` and re-checks the count inside, so two
  simultaneous sessions can't embed the same chunks twice;
- one retry with a 2s backoff on HTTP 429 (rate limit);
- the GROW SSE connection gets a **10-minute timeout** (regular chat: 3 min)
  so the one-time indexing can't kill the first session;
- the progress is sent as a *separate SSE event type* (`status`), **not** as
  chat tokens — tokens would contaminate the visible transcript *and* the
  conversation history replayed to the model on the next turn.

### 5.5 Retrieval on every turn (`GrowLibraryService.retrieveExcerpts`)

For each GROW message:

1. Build the search query. Normally it's the user's message. **Special case:**
   the session's opening message is just "Let's start." with empty history —
   useless as a query — so it is enriched with the goal's title + description.
2. Embed the query (one fast API call with the user's Mistral key).
3. `SELECT … ORDER BY embedding <=> query LIMIT 6` — top 6 passages.
4. Merge adjacent chunks of the same book (they overlap by design; two
   neighbours in the top-6 read better as one passage).
5. Format them as a block headed `COACHING LIBRARY — the ONLY source for your
   coaching method…`, each excerpt labelled with its book title.

## 6. How the AI actually uses the books

The final system prompt for a GROW turn is assembled from four parts:

```
GROW_PROMPT            ← coaching rules (rewritten — see below)
+ SESSION TIMING block ← how much time remains (section 7)
+ PREVIOUS GROW SESSIONS block ← saved memory, if any (section 8)
+ goal context         ← the goal's data: targets, options, obstacles…
+ COACHING LIBRARY     ← the 6 retrieved excerpts for THIS turn
```

The rewritten `GROW_PROMPT` mandates, in essence:

- every coaching move (which question to ask, how to frame it, when to
  reflect or summarise) must be **grounded in and consistent with the supplied
  excerpts**;
- never substitute generic coaching advice from outside the excerpts; if they
  don't cover the moment, stay with their questioning *style*;
- don't quote or mention the books to the user — *embody* the method;
- capturing the user's own commitments via the `propose_goal_change` tool is
  **part of the method** (GROW's "Will" stage — commitments get written
  down), not outside advice. This last clause exists because the strict
  "library only" rule initially made the model too shy to call the tool.

So the model isn't "trained on" the books and doesn't read them whole — every
turn it receives a fresh, message-relevant slice of them and is instructed to
coach by that slice.

## 7. Session timing: the coach knows the clock

The session timer used to live only in the UI; the model knew nothing and the
chat was simply cut off when time ran out. Now:

- The frontend sends `sessionTotalMinutes` and `sessionRemainingSeconds` with
  every GROW request (fields added to `ChatRequest`).
- The backend renders a `SESSION TIMING` block into the prompt with three
  modes:
  - **plenty of time** → "there is room to explore; pace yourself";
  - **last 20%** → "begin consolidating, invite commitments, don't open new
    threads";
  - **time is up (≤ 0)** → "close the session in THIS reply: reflect the key
    insights, confirm commitments (propose capturing them as goal data), say
    a warm goodbye, no new questions."
- When the UI timer hits zero, the frontend does **not** kill the chat.
  It silently sends a hidden wrap-up instruction (never shown in the
  transcript), the coach delivers a proper closing reply — including proposal
  cards for any commitments the user voiced — and only then the "Session
  wrap-up" card appears. An unfinished draft in the input box is preserved
  and restored after the session closes.
- The timer pill in the header used to be a hidden **"Skip (demo)" button**
  that fast-forwarded the session to its closing stretch on a stray click —
  it made the session length feel fake. It is now a display-only element.

## 8. Session memory: continuing where you left off

"Save memory" on the end card used to be decorative — it saved nothing
(the `goal.ai_memory` column existed since migration V7 but no code used it).
Now it is real:

- **Write:** the frontend takes the coach's closing reflection (which already
  summarises the session: insights + commitments) and POSTs it to
  `POST /api/ai/goals/{goalId}/memory`. `GoalMemoryService` appends it as a
  dated entry (`[2026-06-12] …`) to `goal.ai_memory`. The total is capped at
  6,000 characters — when it overflows, the *oldest* sessions fall off; a
  single entry is capped at 2,000 characters. Ownership is enforced: writing
  to another user's goal returns 404.
- **Read:** every GROW request loads the goal's memory and injects it as a
  `PREVIOUS GROW SESSIONS` prompt block with the instruction *"continue from
  it: don't re-ask what it already answers."*

Result: a new session starts with the coach already knowing what was clarified
last time, instead of asking the same opening questions again.

## 9. Proposals during sessions

During (and at the end of) a session the coach can call the
`propose_goal_change` tool — each call becomes a **card** the user can accept
or reject; nothing is ever applied without approval. Two reliability fixes
were part of this work:

- Cards render in the panel's footer, which was hidden on the session-end
  screen — proposals created by the closing reply were visible but
  *unclickable*. The footer now stays active in `grow-end` mode.
- Pending proposals are persisted server-side (`ai_proposals`, status
  `PENDING`) but the UI could lose their cards: GROW transcripts are
  intentionally ephemeral, and the regular-chat transcript lives in
  localStorage with a 100-message cap. Now, on opening the panel / switching
  goals, the frontend calls `GET /api/ai/proposals/goal/{id}` and re-surfaces
  any pending proposals whose cards the restored transcript no longer carries
  ("These proposals from an earlier session are still waiting for your
  review"). De-duplication is by the server-side proposal id.

## 10. Hard guarantees

The product requirement was *"the coach must never speak from the simplified
generic prompt."* The code enforces it structurally — there is no code path
that reaches the LLM in GROW mode without book excerpts:

| Situation | Behaviour |
|---|---|
| No Mistral key saved | Immediate SSE `error`: "GROW sessions need a Mistral API key…" — checked **before** any LLM/library work |
| Library table empty (no book files ingested) | Error: session refuses |
| Search returns zero passages | Error: session refuses |
| Embedding API fails (bad key, 4xx/5xx) | Error propagates; the LLM is never called |
| Regular chat (`sessionType: "chat"`) | Completely untouched — no Mistral requirement, no retrieval |

(The "no Mistral key" case is sent as an SSE error rather than HTTP 422 on
purpose: the frontend maps any 422 to "no key for the chat provider" and would
open the wrong key dialog.)

## 11. Tests that were written

Backend (JUnit 5 + Mockito + AssertJ; all pure unit tests — no database, no
network):

| Test class | What it proves |
|---|---|
| `BookChunkerTest` (6 tests) | Empty input → no chunks; short paragraphs (TOC noise) dropped; paragraphs packed up to the target size; the overlap really repeats a chunk's tail at the next chunk's start; over-long paragraphs split on sentence boundaries; no paragraph text is lost |
| `GrowLibraryServiceTest` (10 tests) | `ensureEmbedded` is a no-op when everything is embedded; loops batch-by-batch with progress callbacks; an embedding failure propagates (session must refuse); retrieval **throws** on an empty library and on zero hits; adjacent chunks merge; the opening message's query is enriched with goal title/description; mid-session queries use the message as-is |
| `GoalMemoryServiceTest` (8 tests) | First save creates a dated entry; later saves append after earlier ones; the 6k cap trims the **oldest** content, never the newest; an oversized single entry is truncated; blank summaries rejected; writing to a foreign goal → 404, no write; `memoryBlock` wraps stored memory in the continuation instruction; empty for no goal / no memory / foreign goal |
| `AiChatServiceGrowTest` (6 tests) | GROW without a Mistral key refuses **before** any LLM or library call; a retrieval failure means the LLM is never called; a successful turn's system prompt actually contains the retrieved excerpts; session timing reaches the prompt (and expired time demands a closing reply); saved memory reaches the prompt; regular chat never touches the library, the Mistral key, or the 10-minute timeout |

The async-streaming tests use Mockito's `verify(…, timeout(2000))` (wait for
the background thread) and `after(300).never()` (prove a call *never* happens).

Frontend: the existing Vitest suite (72 tests) keeps passing; the SSE parser
change is additive (unknown event types were already ignored, `status` now has
a handler).

Everything was also verified **live** against the running app: first-session
indexing with progress events, a Russian question retrieving English book
passages, the coach answering "what did we talk about last time?" from saved
memory, and the wrap-up turn producing acceptable proposal cards.

## 12. How to repeat this in your own project

A condensed checklist:

1. **Get your documents as clean plain text** (UTF-8, no BOM, blank-line
   paragraphs). Do the conversion once, offline; don't parse binary formats at
   runtime if you don't have to.
2. **Pick an embedding model** and note its dimension (e.g. `mistral-embed` →
   1024, OpenAI `text-embedding-3-small` → 1536). If your users and documents
   speak different languages, make sure the model is multilingual.
3. **Add pgvector**: use the `pgvector/pgvector:pgXX` image (or
   `CREATE EXTENSION vector` if your hosting already ships it), then a
   migration with a `vector(N)` column. Watch for the collation-version
   warning after an image swap (`REINDEX` + `REFRESH COLLATION VERSION`).
4. **Chunk** your text into ~1–2k character passages with a small overlap,
   on paragraph boundaries. Drop noise (very short lines).
5. **Ingest** chunks with NULL embeddings; **embed in batches** (respect the
   provider's per-request token limits; handle 429 with a retry); store the
   model name next to each vector so you notice mismatches later.
6. **Retrieve** with `ORDER BY embedding <=> $query LIMIT k` (cosine). Skip
   vector indexes below ~50k rows. Merge overlapping neighbours.
7. **Prompt**: paste the excerpts into the system prompt with an explicit
   instruction about how strictly to follow them, and **fail loudly** when
   retrieval is unavailable — silent fallbacks erode the guarantee that made
   you build RAG in the first place.
8. **Keep the vector table out of your ORM** if your test database (H2,
   SQLite…) doesn't understand the `vector` type; plain SQL + a feature flag
   for ingestion keeps the test suite clean.
9. **Test the guards, not just the happy path**: empty library, zero hits,
   API failure, missing key — each must refuse, not degrade.
