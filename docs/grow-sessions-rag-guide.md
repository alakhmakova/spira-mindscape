# GROW Sessions: Book-Grounded AI Coaching with RAG (pgvector)

> ## ⚠️ Superseded for the coaching method (2026-08-22)
>
> **The GROW coach no longer retrieves book excerpts per turn.** Its method is now
> written out as prose in
> **`backend/src/main/resources/prompts/grow/coach-method.md`**, distilled by
> hand from *Coach the Person, Not the Problem*, and loaded by
> `ai/prompt/PromptResources.java`.
>
> **Why it was retired.** Embedding search matches the **topic of the user's
> message**, but the thing the coach needs help with is the **coaching
> situation** — "the user is stuck", "the session is closing", "the user is
> angry" — which is not a topic in that message. So the model was handed six
> passages about, say, coaching ROI in organisations and told they were its only
> method. Worse, the doctrine was re-rolled every turn, so the coach had no
> stable persona and no session arc, and the prompt actively forbade it from
> using stable competence ("never substitute … from outside the excerpts").
> Sessions read as pleasant conversations that went nowhere.
>
> **It is not a saving on tokens.** The hand-written method is ~24 KB (~6.0k
> tokens) against the ~2.2k tokens of excerpts it replaces, so the GROW system
> prompt grew from roughly 3.0k to 6.8k tokens before goal context. What it buys
> is that those tokens are *always the right ones*: the same method every turn,
> covering the situations that actually break a session, instead of a fresh
> lucky dip. Retrieval only earns its keep when the corpus is too big to inline;
> once curated, this one is not.
>
> **What changed in the code:** no Mistral key is needed to start a session, the
> SSE timeout is the ordinary 3 minutes, and there are no
> "Preparing the coaching library…" `status` events. Sections 6, 10 and 11 below
> have been rewritten; §7 (timing), §8 (memory) and §9 (proposals) are unchanged
> and still accurate.
>
> **What is still true and still in the codebase:** everything in sections 1–5.
> `BookIngestionRunner`, `BookChunker`, `MistralEmbeddingClient`,
> `GrowLibraryService` and the pgvector `book_chunk` table are all still there,
> still ingest both books at startup, and are still covered by their own tests.
> Nothing in the chat path calls them any more. Keep them: if a
> *situation-keyed* second tier is ever wanted, the machinery is ready.

This document explains everything that was built for GROW coaching sessions in
Spira: how the AI coach is grounded in real coaching books instead of a generic
prompt, how session timing and session memory work, and which tests cover it.
It is written for a beginner — if you read it top to bottom, you should be able
to repeat the same setup in your own project.

> **The one-sentence summary of RAG:** search your documents for the passages
> most relevant to what the user just said, paste those passages into the AI's
> instructions, and tell the model to answer from them. Spira still builds the
> whole pipeline (sections 1–5); it just no longer uses it to decide *how the
> coach coaches* — see the banner above.

---

## Table of contents

1. [What is RAG and why we need it](#1-what-is-rag-and-why-we-need-it)
2. [What is an embedding](#2-what-is-an-embedding)
3. [Why the retrieval pipeline is pinned to Mistral](#3-why-a-mistral-key-is-required)
4. [pgvector: turning a regular Postgres into an "AI database"](#4-pgvector-turning-a-regular-postgres-into-an-ai-database)
5. [The pipeline, step by step](#5-the-pipeline-step-by-step)
6. [How the AI actually gets its coaching method](#6-how-the-ai-actually-gets-its-coaching-method)
7. [Session timing: the coach knows the clock](#7-session-timing-the-coach-knows-the-clock)
8. [Session memory: continuing where you left off](#8-session-memory-continuing-where-you-left-off)
9. [Proposals during sessions (and surviving page reloads)](#9-proposals-during-sessions)
9a. [Ending a session: three steps, and what "End" really does](#9a-ending-a-session-three-steps-and-what-end-really-does)
9b. [A live session follows you between devices](#9b-a-live-session-follows-you-between-devices)
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
(Claude), OpenAI, Mistral, Google Gemini.

Embeddings, however, are a **separate API** from chat — and **Anthropic does
not offer an embeddings API at all** (they point customers to third parties
such as Voyage AI). Out of the providers Spira supports, Mistral has a strong
multilingual embedding model:

| | Chat API | Embeddings API |
|---|---|---|
| Anthropic (Claude) | ✅ | ❌ none |
| OpenAI | ✅ | ✅ (not used for GROW — the library is pinned to Mistral) |
| Mistral | ✅ | ✅ `mistral-embed`, 1024 dimensions, multilingual |
| Google Gemini | ✅ | ✅ (not used for GROW — the library is pinned to Mistral) |

So the rule was:

- ~~**A Mistral API key is mandatory for GROW sessions.**~~ **No longer true** —
  see the banner at the top. A key is needed only if you actually run a
  retrieval query; nothing in the chat path does any more, so a GROW session
  starts on the user's chat key alone. The rest of this section still explains
  why *the retrieval pipeline* is pinned to Mistral.
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

> **Nothing triggers this today.** `ensureEmbedded` was called by the GROW chat
> path, which no longer retrieves (see the banner). The method still works and
> is still tested; it just has no caller, so the 10-minute timeout and the
> `status` progress events described below are not reachable from the app. The
> design is documented because it is the part worth copying if you build RAG
> yourself.

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

## 6. How the AI actually gets its coaching method

*(Rewritten 2026-08-22 — this section used to describe per-turn retrieval.)*

The system prompt for a GROW turn is assembled in `AiChatService`, in this order:

```
GROW_ROLE              ← who is speaking, and where instruction ends and data
                         begins (a Java text block)
+ coach-method.md      ← the whole coaching method, loaded by PromptResources
+ GROW_PLUMBING        ← propose_goal_change, no execution work, untrusted
                         tool content, language, professional boundaries
─────────────────────── everything below here is DATA, not instruction ───────
+ goal context         ← the goal's data: targets, options, obstacles…
+ SESSION TIMING block ← how much time remains (section 7)
+ PREVIOUS GROW SESSIONS block ← saved memory, if any (section 8)
```

The method comes **before** the plumbing on purpose: it is the longest and most
important part, and what the coach *is* must not be prefaced by what it may do
to a database row.

`GROW_ROLE` also draws the **instruction/data boundary**, and that is a security
control, not tidiness. The goal context carries resource *titles*, and an `email`
resource's title is a subject line — text that arrived from outside the user. It
sits in the same system prompt as the coach's instructions, so the prompt has to
say out loud where its own orders stop. The `<<UNTRUSTED_CONTENT>>` fencing in
`GROW_PLUMBING` covers only text returned by *tools*; this covers the goal block.
`AiChatServiceGrowTest.growPromptTreatsGoalContextAsData` is the guard.

`prompts/grow/coach-method.md` is hand-distilled from *Coach the Person, Not the
Problem* and covers, in order: **who you are** (curiosity and care; warm and
challenging; keep yourself out of it) · **how you speak** (a turn is a
reflection plus at most one question; the recap / paraphrase / label /
bottom-line / distinction toolkit; noticing emotional shifts without
interpreting them) · **what you aim at** (the person, not the problem; story →
context → frame) · **what the session may be about** · **the arc of the session** (contract an outcome, hold the
thread, keep it moving, convert an insight into a commitment, close) · **a
five-rung ladder for a client who cannot name an outcome** (the rule being:
never repeat a question they did not answer) · **a table of failure modes**
(annoyed, circling, defensive, silent, "just tell me what to do") · **a Never
list**.

Three rules in the arc are about the clock, and they exist because a timed
session fails in two opposite ways:

- **The length is named once, in the opening turn, and never again.** That is
  Whitmore's contracting question verbatim ("We have half an hour — where would
  you like to have got to by then?"). It puts the pacing in the client's hands,
  which works far better than a coach hurrying them along. Mid-session the coach
  changes *what it asks*, never *what it says about time* — which is the rule
  `docs/ai-configuration.md` already asked for.
- **Reaching a commitment is the coach's job, not the clock's.** A session that
  drifts pleasantly until the timer kills it is the most common way coaching
  comes to nothing. Ending without a commitment stays legitimate — but only when
  the client genuinely is not ready, never as the residue of drifting. The two
  look identical from outside and are opposites.
- **A finished session gets closed, even with time left.** Once outcome, block
  and commitment all exist, the coach closes rather than padding the remaining
  minutes or fishing for another topic. The only end-of-session question is
  Reynolds' closing one, "Are we complete?", never "what else shall we discuss?"

Two more rules govern what happens at the end, and both are owner decisions
(2026-08-22) that **contradict the older spec** in `docs/ai-configuration.md`
("during the session, the AI periodically and contextually notices when
something discussed should update the goal"). That is superseded:

- **Nothing is proposed while the session runs.** Not one card. An offer to
  record something pulls the client out of their own thinking, and it lands at
  exactly the moments that matter most — because those are the moments worth
  recording. The gate is the client's own "yes" to *"Are we complete?"*.
- **After the close, two steps in order.** First the record of the session — the
  client summarises in their own words *first*, then the coach writes it as one
  sentence held to Whitmore's full quality bar (SMART + PURE + CLEAR: specific,
  measurable, time-framed, realistic *and* challenging, **positively stated**,
  agreed, understood, relevant, appropriate, ethical), plus the blocks named and
  classified — belief, assumption, bias, fear, values conflict, unmet need,
  inherited "should" — for the coach's own use, never in words shown to the user.
  Then, and only then, what belongs in the goal, subject to a relevance test:
  *would someone reading this goal, who was not in the session, understand why
  this line is here?* Breathing exercises are a real conclusion of a job-search
  session and still not a job-search strategy. If nothing fits, propose a note;
  proposing nothing is also a valid answer.

> **Known gaps — both need app work, not prompt work:**
>
> 1. **The coach cannot end the session.** It can *say* the closing, but
>    `AiPanel.tsx` reaches `grow-end` only on the timer or the **End** button, so
>    after an early close the UI sits open.
> 2. **The proposal timing is advisory only.** The frontend renders a `proposal`
>    event whenever it arrives, so "propose nothing until the end" is a request to
>    the model, not a guarantee, and the ordering (confirm → record → memory
>    saved or discarded → *then* cards) does not exist yet: the memory card comes
>    from the timer wrap-up and proposals are independent of it. Enforcing this
>    means gating proposal events on session state.

One rule in there is a product decision rather than a distillation, and is easy
to undo by accident: **a session sits inside a goal but does not have to be about
it.** The coach may ask what prompted an apparently unrelated topic *once*, after
the story and never as a precondition, and then coaches it either way. There is
no relevance gate and no "this belongs in a different goal" nudge — the user
already decides what is kept, on the memory card at the end of the session
(§8), so an off-goal session costs nothing. Over-policing costs a lot: the
moment a client feels steered, they resist or check out.

Two clauses in `GROW_PLUMBING` are load-bearing and must survive any rewrite:

- capturing the user's own commitments via `propose_goal_change` is **part of
  the coaching**, not a departure from it. The clause exists because the old
  "library only" rule made the model too shy to call the tool; the same shyness
  risk exists under a method that says "coach, don't do things for them".
- "Respond in the language the user writes in" — the frontend's timed wrap-up
  instruction (`AiPanel.tsx`) is written in English and relies on this to close
  a Russian session in Russian.

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

## 9a. Ending a session: three steps, and what "End" really does

Two things had to be separated here, because merging them cost the owner several whole sessions
(BUG-079, BUG-080).

**A close is three steps, not one turn.** "Ending the session" used to ask the coach for the session
record, the goal proposals and the goodbye all at once. Models wrote the record, said goodbye and
skipped the middle — a record reads like a summary, so once it is written the turn feels finished.
The close is now explicit and sequential, in `coach-method.md` and in both clients:

| Step | What it is | Who asks for it |
|---|---|---|
| 1. **The record** | What *this* session was about, written for the next one | the coach's closing turn |
| 2. **The proposals** | Changes to the goal itself, as reviewable cards | `askForProposals()` — a dedicated turn that asks for nothing else |
| 3. **The goodbye** | Only once the review is done | `askForGoodbye` |

The record and the proposals are **different things and must not be confused**: the record is memory
for the coach, the proposals are edits to the user's goal. The memory block itself now says so
(`GoalMemoryService.memoryBlock()`: *context, never material for the record*), because models were
reading the previous session's memory as a template for what to write.

A subtlety worth keeping: the ending flag is `endRecord !== null && !proposalsTurn`. Without the
second half, a model that re-calls `end_session` on the proposals turn has its proposals treated as
part of the ending and silently dropped — the original defect, arriving by a second route.

**"End" is local, unconditional, and involves no AI at all.** It used to send a hidden "wrap this up
now" instruction and wait for the model. A dead provider therefore meant a session that could not be
closed, and pressing Stop afterwards latched the wrap-up flag and disabled End permanently — the
owner was trapped in a session with no exit (2026-09-08).

> **End ends the session. It cancels any stream in flight, stops the timer, clears the draft and any
> held proposals, and leaves. Nothing is sent, nothing is saved, and nothing about the provider can
> take the exit away.**

`canEndEarly` is therefore simply "we are in a session". Anything that ends without the coach
speaking leaves **no record**, and the closing card says so rather than offering to save an empty one
over the previous session's (BUG-084).

## 9b. A live session follows you between devices

The ordinary chat has synced through `ai_chat_transcript` for a while. A session *in progress* did
not — it lived in `localStorage` on the web and in the ViewModel on Android, so a session begun on
the phone did not exist on the laptop (BUG-081).

`ai/grow/session/` gives it a row: `GET` / `PUT` / `DELETE /api/ai/grow/session`, one per
(user, goal), unique-indexed — a second row would be two clocks running on one conversation.

- **Owner-scoped** through `goalRepository.findByIdAndUserId`, like every other goal-owned resource.
- `content` is **the client's own JSON** — the minutes, when it ends, and the messages. The server
  never reads it, so the schema does not move when the session's shape does. Both surfaces write the
  same shape, which is what makes them interchangeable.
- It is deliberately **not** the transcript's row. A session row is created when the session starts
  and **deleted the moment it ends**; what outlives it is the record the user chose to keep and
  whatever they approved into the goal.
- Web: `saveGrowSession` also `PUT`s and `clearGrowSession` also `DELETE`s; `resumeFromServer`
  (guarded by `resumeAskedRef`, so it asks once) offers whatever the server holds. Android:
  `persistGrowSession` / `restoreGrowSession` / `clearGrowSessionRemote`.

## 10. Hard guarantees

The product requirement is *"the coach must never speak from a simplified
generic prompt."* That is still enforced structurally, just at a different
point: the method is a file, and **a missing or empty file fails startup**
rather than degrading into a coachless coach.

| Situation | Behaviour |
|---|---|
| `prompts/grow/coach-method.md` missing | `PromptResources` throws in its constructor → the application does not start |
| The file is present but blank | Same: `IllegalStateException`, no boot |
| No Mistral key saved | A GROW session runs normally — the method does not depend on embeddings any more |
| Library table empty / embeddings missing | Irrelevant to a session; `GrowLibraryService` still throws if anything ever calls it |
| Regular chat (`sessionType: "chat"`) | Untouched — never sees the coaching method or the session memory |

Failing to boot is the right trade here: a silently method-less coach would
look like it was working and would waste the user's session, whereas a boot
failure is caught by whoever deployed it.

## 11. Tests that were written

Backend (JUnit 5 + Mockito + AssertJ; all pure unit tests — no database, no
network):

| Test class | What it proves |
|---|---|
| `BookChunkerTest` (6 tests) | Empty input → no chunks; short paragraphs (TOC noise) dropped; paragraphs packed up to the target size; the overlap really repeats a chunk's tail at the next chunk's start; over-long paragraphs split on sentence boundaries; no paragraph text is lost |
| `GrowLibraryServiceTest` (10 tests) | `ensureEmbedded` is a no-op when everything is embedded; loops batch-by-batch with progress callbacks; an embedding failure propagates (session must refuse); retrieval **throws** on an empty library and on zero hits; adjacent chunks merge; the opening message's query is enriched with goal title/description; mid-session queries use the message as-is |
| `GoalMemoryServiceTest` (8 tests) | First save creates a dated entry; later saves append after earlier ones; the 6k cap trims the **oldest** content, never the newest; an oversized single entry is truncated; blank summaries rejected; writing to a foreign goal → 404, no write; `memoryBlock` wraps stored memory in the continuation instruction; empty for no goal / no memory / foreign goal |
| `AiChatServiceGrowTest` (5 tests) | A GROW session runs on the chat key alone and the Mistral key is **never** requested; the coaching method reaches the prompt and precedes the plumbing; session timing reaches the prompt (and expired time demands a closing reply); saved memory reaches the prompt; regular chat picks up neither the method nor the memory. Uses the **real** `PromptResources`, not a mock — otherwise it would only prove that a mock returns what it was told to |
| `PromptResourcesTest` (3 tests) | The method file loads from the classpath and is not a truncated stub; it still covers persona, session arc, the can't-name-an-outcome ladder, the failure table and the Never list; the two rules the owner asked for by name ("never repeat a question they did not answer", "warm and challenging") are actually in the shipped file |

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
