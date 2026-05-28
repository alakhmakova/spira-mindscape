# AI Configuration Plan

This document is the planning and implementation guide for Spira's AI system. It is a living document — updated as decisions are made and implementation progresses.

---

## Core Philosophy

Spira's AI is not a chatbot or a task assistant. It is a coaching intelligence.

The distinction matters at every level of implementation: how we write system prompts, how we structure context, how we handle GROW sessions, how we define what the AI is allowed to do.

The source of truth for how the AI should behave in coaching mode is:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`

The AI must be grounded in these materials. It should feel like a real coach — asking questions that raise awareness, not giving advice. It should not follow a rigid script or fill in a GROW template. GROW structure may emerge as a byproduct of a well-run session, but it is never the goal. The goal is that the user gets closer to understanding and acting on what matters to them.

> "A coach is not a problem solver, a counselor, a teacher, an adviser, an instructor, or even an expert; a coach is a sounding board, a facilitator, an awareness raiser, a supporter."
> — Coaching for Performance

---

## Provider Strategy: Bring Your Own Key (BYOK)

### Decision

Spira does not manage AI costs centrally. Users bring their own API keys. This gives users control over spend and provider choice.

Supported providers at launch:

| Provider | Models |
|---|---|
| Anthropic | claude-sonnet-4, claude-opus-4 |
| OpenAI | gpt-4o, o1 |
| Mistral | mistral-large, mistral-medium |

### Key Storage

API keys must be stored securely. They should:

- be encrypted at rest in the database (AES-256 or equivalent)
- never appear in logs, error messages, or API responses
- never be sent to the frontend after the initial save (only a masked display like `sk-ant-••••••••••1234` is acceptable)
- be associated with the user's account, not the goal

### Provider Abstraction Layer

The AI orchestration layer must abstract over all providers. The rest of the system (GROW sessions, chat, proposals) should not know or care which provider is active.

**Recommended pattern in Spring Boot:**

```
AIProvider (interface)
  ├── AnthropicProvider
  ├── OpenAIProvider
  └── MistralProvider
```

Each provider implementation handles:
- authentication headers
- request format (Anthropic and OpenAI have different message formats)
- response parsing
- streaming (if used)
- error normalization into a common `AIError` type

The user selects their active provider in account settings. The backend looks up the key and routes every AI call through the correct implementation.

### Context Window Limits by Provider

This matters for how much conversation history and goal data we can include per request:

| Provider | Context window |
|---|---|
| Claude (Anthropic) | 200 000 tokens |
| GPT-4o (OpenAI) | 128 000 tokens |
| Mistral Large | 128 000 tokens |

Design to the lowest common denominator: 128 000 tokens. In practice, even a large goal with full history will rarely approach this limit in the MVP.

---

## Context Architecture

### What the AI sees in every request

For all AI interactions (both regular chat and GROW sessions), the AI receives:

1. **System prompt** — defines the AI's role, coaching philosophy, and constraints
2. **Goal context** — the current goal's full data: title, description, reality (actions + obstacles), options, targets, confidence
3. **Relevant book passages** — retrieved from the coaching source material via semantic search (see RAG section below)
4. **Conversation history** — the current goal-scoped chat transcript (or GROW session transcript)
5. **User message** — the current message

### History Management

For MVP: send the full conversation history for a given goal or session.

This is safe because:
- GROW sessions are bounded by time (15–60 min → roughly 30–80 messages)
- Regular goal chats in the early stages will be short

When a goal accumulates a long history (200+ messages), we will add summarization: the backend compresses older messages into a rolling summary, and only the summary + recent messages are sent. This is a post-MVP concern.

### Conversation History Budget

Reserve approximately:
- ~2 000 tokens for system prompt
- ~3 000 tokens for goal context
- ~1 000 tokens for retrieved book passages (3–5 chunks)
- ~remainder for conversation history and current message

At 128k total, this leaves ~122 000 tokens for conversation history — comfortably large for any realistic session.

---

## RAG: Coaching Knowledge Retrieval

### Why RAG

The coaching books together are approximately 200 000 tokens. Including the full text in every request would:
- exceed the context window of OpenAI and Mistral
- cost significantly more per request
- fill the context with irrelevant material

RAG (Retrieval-Augmented Generation) solves this. The books are indexed once. At runtime, we retrieve only the passages most relevant to what the user just said.

### How It Works

```
User message
     │
     ▼
Embed the message (convert to a vector — a list of numbers representing meaning)
     │
     ▼
Search the book index for the 3–5 passages closest in meaning
     │
     ▼
Include those passages in the AI's context
     │
     ▼
AI responds, grounded in real coaching material
```

### Storage: pgvector

We use **pgvector**, a PostgreSQL extension. This means no separate vector database — we extend the existing PostgreSQL instance that already holds all Spira data.

**Installation (PostgreSQL 15+):**
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**Schema:**

```sql
CREATE TABLE coaching_chunks (
    id          BIGSERIAL PRIMARY KEY,
    source      TEXT NOT NULL,         -- e.g. 'Coaching for Performance'
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,         -- the raw text of this passage
    embedding   vector(1536)           -- 1536 dimensions for OpenAI embeddings
                                       -- use 1024 for Mistral, 1024 for Anthropic
);

CREATE INDEX ON coaching_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 50);
```

### Embedding Model

Embeddings are generated once, during indexing. They are separate from the chat model and can use a cheaper dedicated embedding model:

| Provider | Embedding model | Dimensions | Notes |
|---|---|---|---|
| OpenAI | text-embedding-3-small | 1536 | Fast, cheap, good quality |
| Anthropic | (use OpenAI or Mistral for embeddings) | — | Anthropic does not offer a standalone embedding API |
| Mistral | mistral-embed | 1024 | Good quality |

**Practical recommendation:** For MVP, use OpenAI `text-embedding-3-small` regardless of which chat provider the user has selected. This simplifies the embedding pipeline — embeddings are computed once at indexing time, not at query time per user. If the user has no OpenAI key, use Mistral embed.

### Chunking Strategy

The books need to be split into passages before indexing. Chunking rules:

- Target chunk size: **400–600 tokens** (~300–450 words)
- Overlap between adjacent chunks: **50–80 tokens** (prevents meaning from being cut at boundaries)
- Prefer to split at paragraph boundaries, not mid-sentence
- Each chunk stores its source book and position

**Implementation steps (one-time setup):**

```
1. Extract text from both .docx files (python-docx or Apache POI)
2. Split into chunks (LangChain's RecursiveCharacterTextSplitter or manual)
3. Generate embeddings for each chunk
4. Store chunks + embeddings in coaching_chunks table
```

This is a one-time offline process run by a backend admin command or startup migration.

### Query-Time Retrieval

At each AI request, the backend:

```java
// 1. Embed the user's message (or a summary of the recent turn)
float[] queryEmbedding = embeddingService.embed(userMessage);

// 2. Query pgvector for the most similar passages
List<CoachingChunk> relevant = coachingRepository.findSimilar(queryEmbedding, topK: 4);

// 3. Format into the context block
String knowledgeBlock = relevant.stream()
    .map(c -> "[" + c.source + "]\n" + c.content)
    .collect(joining("\n\n---\n\n"));

// 4. Inject into the system prompt or as a separate context message
```

**Top-k recommendation:** 3–5 chunks. More is not better — irrelevant passages add noise.

---

## System Prompt Architecture

### Structure

The system prompt is assembled dynamically per request from three parts:

```
[1] ROLE AND PHILOSOPHY
    Who the AI is, what it is doing, what it must never do.
    This is static — written once, stored in code/config.

[2] COACHING KNOWLEDGE (RAG output)
    The retrieved passages from the source books.
    This changes per request based on what the user is discussing.

[3] GOAL CONTEXT
    The current goal's full data: title, description, reality, options, targets.
    This changes per goal.
```

For GROW sessions, a fourth part is added:

```
[4] SESSION CONTEXT
    Current phase (derived from conversation, not imposed rigidly),
    time remaining, session intent.
```

### Role Prompt (static core)

The role prompt must be grounded in the coaching books, not in generic AI assistant language. Draft:

```
You are a coaching intelligence embedded in Spira, a goal achievement platform.

Your role is not to advise, instruct, or solve problems for the user.
Your role is to raise awareness and responsibility through focused questioning.

You listen carefully. You ask one good question at a time.
You do not give unsolicited advice.
You do not rush the user toward conclusions.
You follow the user's thinking, not a predetermined agenda.

The GROW framework (Goal, Reality, Options, Will) may naturally emerge from
a session, but you do not announce phases or lead the user through a checklist.

When you need to deepen the conversation, draw on the coaching principles
provided in the knowledge sections below. These come from real coaching
source material and represent how a skilled professional coach thinks and works.

You communicate in the language the user writes in.
You are warm, patient, and genuinely curious about this person's situation.

You are not a therapist, a medical professional, a legal adviser, or a
financial adviser. If a user's situation requires professional support,
you acknowledge this honestly and encourage them to seek it.
```

### Where the Prompt Lives

**Decision pending** (discussed below): whether prompts live in code/config or in the database.

For MVP, storing the role prompt as a file in the repository (`specs/ai/role-prompt.md` or similar) is simpler and version-controlled. Product owners can update it via a pull request.

Database storage (editable via admin UI without a deploy) is a Phase 2 consideration.

---

## GROW Session Architecture

### What a GROW session is

A GROW session is an explicitly started, time-bounded coaching conversation focused on one goal. It is separate from the regular goal chat.

Key distinction: the session is not a form. The AI does not announce "Now we are in the Reality phase." It conducts a real coaching conversation that, if done well, naturally covers the ground that GROW describes — but the user experiences it as a human conversation, not a process.

### What the session produces (important)

The session has no formal summary that the user reads. The outputs are:

**For the AI (internal):**
- The full transcript is kept in the database so the AI has memory of what was discussed. It is not shown to the user as a document.
- At the end of the session, the AI creates a compressed memory block (key insights, context, decisions). If the user says "no" when asked whether to save — this memory is discarded. The AI has nothing to remember. This is intentional: if it wasn't worth saving for the user, it wasn't meaningful enough to carry forward.

**For the goal (user-visible, requires approval):**
- During the session, the AI periodically and contextually notices when something discussed should update the goal — a new obstacle, a clearer description, a new option. It asks the user naturally: "It sounds like X is actually a key constraint here — want me to add that to your goal?" Not a scheduled check, not a form. Only when the conversation makes it relevant.
- If the user wants to save a specific insight or piece of information, they can ask the AI to save it. The AI creates a resource note on the goal (requires approval before saving).
- Proposed targets, option selections, or goal edits all go through the standard approval workflow.

### Session State (backend)

```
GROWSession
├── id
├── goal_id
├── user_id
├── selected_duration_minutes  (15 | 30 | 45 | 60)
├── started_at
├── scheduled_end_at           (started_at + duration)
├── closing_starts_at          (started_at + duration * 0.80)
├── ended_at
├── status                     (ACTIVE | CLOSING | COMPLETE | ABANDONED)
├── transcript                 (list of messages — internal, not shown to user)
├── ai_memory                  (nullable — compressed context block, saved only if user approves at end)
└── proposals                  (approval-required changes to goal data)
```

### Time Management

The backend owns time tracking. At each AI request during a session, the backend computes how much time remains and passes this to the AI as context.

When `now >= closing_starts_at`:
- Session status transitions to `CLOSING`
- The AI context includes a signal: "The session is in its closing phase. Guide the conversation toward a natural conclusion — consolidating what has been clarified, naming decisions, and identifying next steps. Do not cut off the user abruptly. The goal is a graceful, useful ending."

The AI does not announce "We have 10 minutes left." It shifts its questioning style — becoming more integrative and action-oriented, less exploratory.

When `now >= scheduled_end_at`:
- The AI wraps up with a closing summary
- It asks the user whether they want to save a summary of the session
- If yes: summary, insights, and proposals are persisted; proposals are queued for user approval
- If no: transcript is kept for reference but no formal summary is saved

### Session Opening

The AI opens a GROW session by:
- Acknowledging that the user has chosen to spend time on this goal
- Setting a light frame for the session (what is available, what the user might get from it)
- Beginning with an open question about where the user would like to start

It does not announce the GROW framework. It does not say "Let's begin with the Goal phase."

### Separation from Regular Chat

Regular goal chat is flexible — users can ask anything, request research, discuss options informally.

GROW sessions are explicitly different: the AI stays in coaching mode for the duration. If the user asks for execution work (e.g. "can you search for surf schools?"), the AI acknowledges the request and offers to note it as a next action to pursue after the session.

---

## AI Proposal and Approval System

All important AI-generated changes require explicit user approval before they are applied. This is a core safety principle.

### What requires approval

- New targets proposed by the AI
- Changes to goal description or structure
- Selected option changes
- New resource notes created by the AI
- Mini tool creation
- Any external action (drafting a message to send, etc.)

### What does not require approval

- The AI's conversational responses
- Reading goal data and resources
- Suggesting ideas without persisting them
- Asking questions

### Proposal Lifecycle

```
AI generates proposal
      │
      ▼
Proposal stored in DB with status: PENDING
      │
      ▼
User sees proposal in UI with Accept / Reject
      │
   ┌──┴──┐
Accept   Reject
  │        │
  ▼        ▼
Applied  Discarded
  │
  ▼
Audit log entry created
```

---

## Safety and Boundaries

Safety checks must run before the AI responds to any request.

### Hard rules (always refuse, regardless of framing)

- Do not help with illegal, harmful, dangerous, or exploitative goals
- Do not provide medical diagnosis, treatment, or psychiatric advice
- Do not provide legal or financial advice
- Do not provide crisis intervention — redirect to qualified services

### Soft rules (acknowledge and redirect)

- If the user seems distressed beyond the scope of coaching, acknowledge this with care and suggest professional support
- If a goal involves significant personal risk, name this honestly before continuing

### Implementation

Safety checks should be implemented as a pre-processing step in the AI orchestration layer — a lightweight prompt that evaluates the user's message before the main coaching prompt is sent. This is faster and cheaper than including safety logic in the full coaching prompt.

---

## Language

The AI responds in the language the user writes in.

No language detection library is needed — modern LLMs handle this automatically if the system prompt does not force a language. The role prompt must not specify a language; it should only say "communicate in the language the user writes in."

---

## Implementation Phases

The AI system should be built in phases aligned with the product roadmap.

### Phase 5 (current roadmap): Foundation

- Provider abstraction layer (Anthropic, OpenAI, Mistral)
- BYOK key storage (encrypted)
- Goal-scoped AI sessions
- Basic chat with goal context in system prompt
- Proposal + approval workflow
- Safety check layer

### Phase 6: Coaching Knowledge

- Extract text from both .docx source books
- Chunk and embed (one-time process)
- Store in pgvector (coaching_chunks table)
- Build retrieval service
- Integrate into system prompt assembly

### Phase 7: GROW Sessions

- Session state model in database
- Time tracking (started_at, closing_starts_at, scheduled_end_at)
- CLOSING status transition
- Session opening behavior
- Session closing + summary save flow
- Proposal generation at session end

### Phase 8 onwards: Execution Agent, Mini Tools

Per the product roadmap.

---

## Open Questions

These are items that need a decision before implementation:

- [ ] **Prompt storage**: Role prompt in code/config (version-controlled, requires deploy to change) vs. database (editable via admin UI). For MVP, code/config is simpler.
- [x] **Streaming**: Token-by-token streaming is required. Waiting for a complete response breaks the coaching atmosphere. Implement server-sent events (SSE) or WebSocket streaming from the AI provider through the Spring Boot backend to the frontend. This is part of Phase 5.
- [ ] **Embedding provider**: For users who have only an Anthropic key, which embedding model do we use? Options: require an OpenAI key for embeddings, use a local/free embedding model, or store pre-computed embeddings at deploy time.
- [x] **Session transcript privacy**: Transcripts are internal AI memory only — not shown to the user. User-visible outputs are: approved goal updates, and resource notes the user explicitly asked to save. If the user declines to save the AI memory block at session end, it is discarded.
- [x] **Multiple provider keys**: Users can store keys for multiple providers simultaneously and switch the active provider in settings. All stored keys are retained when switching. Only one provider is active at a time for AI calls.

## AI Memory (Goal-Level)

### Where memory lives

Each goal has one `ai_memory TEXT` field in the database. This is a living document — it accumulates and updates over time. It represents "what the AI knows about this goal and this person at this point in time."

### How it grows

When a GROW session ends and the user approves saving:
- The AI merges the previous memory block with new insights from the session
- It writes an updated version — not a replacement, but a synthesis
- Old memory is not discarded; it is compressed into the new version

### How it is used

At every AI request — both in regular goal chat and in GROW sessions — the `ai_memory` block is automatically included in the context. The AI knows the history without scrolling through transcripts.

### Memory in regular chat

If something meaningful is said during regular goal chat (a new constraint, a decision, a shift in thinking), the AI can update the memory block — either by asking the user, or quietly for clear factual details about the goal.

### Memory size management

The memory block must not grow unboundedly. Every few updates the AI performs a compression pass: removes stale context, retains the essence. This happens automatically and is invisible to the user.

### Schema

```sql
-- added to the goals table (or as a separate table if preferred)
ALTER TABLE goals ADD COLUMN ai_memory TEXT;
```

---

## Personal Tools (AI-Created Widgets)

### Concept

Beyond structured goals, users need small personal instruments that don't fit neatly into the goal model — a job application tracker, a period tracker, a habit log, a trash collection reminder. These are part of a personal operating system, even if they aren't goals in the GROW sense.

Spira supports two kinds of AI-created tools:

**Goal-scoped tools** — created in the context of a specific goal when the standard goal interface isn't enough. A job search goal might generate an application tracker. A fitness goal might generate a weight log.

**Global personal tools** — not tied to any goal. Created by the user for personal life management needs. These live in the user's personal workspace regardless of their goals.

### Placement — user controlled

The user decides where each tool appears. This is a setting on the tool, not determined by the AI or the product. Options:

- **Goal page** — shown on the page of the specific goal it belongs to (goal-scoped tools only)
- **All Goals page** — pinned as a widget visible at all times, for things the user doesn't want to miss
- **Tools page** — a dedicated page collecting all created tools; the default for things needed occasionally

A tool can appear in more than one location if the user chooses. The All Goals placement is intentional: it gives users a way to keep important trackers in peripheral vision without opening a separate page.

### How tools are built

AI does not generate arbitrary code. Tools are assembled from a controlled set of approved UI primitives:

- numeric input
- date input / reminder
- checkbox / checklist
- table with user-defined columns
- line or bar chart (from entered data)
- progress display
- text note
- calendar-style log

The AI proposes a tool schema (which primitives, what layout, what data it stores). The user approves before the tool is created. The tool is then rendered by a generic frontend renderer — the same renderer for all tools, not custom code per tool.

### Data storage

Tool definitions and tool data are stored in the backend:

- `tool_definitions` — schema, primitive config, placement settings, goal association (nullable), created by AI or user
- `tool_records` — user-entered data rows for each tool instance

No tool data lives only in generated frontend code.

### Web search in regular chat

The regular goal chat supports web search as an AI capability. When the user asks for information (schools, job listings, legal requirements, prices, resources), the AI can search the web and return structured results. This is part of the AI Execution Agent (Phase 8).

Web search results can be:
- summarized in the chat
- saved as a resource note on the goal (with user approval)
- used as the basis for a proposed target or option

---

## ⚠️ AiPanel Frontend — требует отдельного обсуждения

Компонент `src/components/ai/AiPanel.tsx` существует, но требует серьёзной переработки — как визуальной, так и функциональной.

**Текущее состояние:**
- Вся AI-логика — это `setTimeout` с хардкодными GROW-вопросами. Реального AI нет.
- GROW реализован как жёсткий скрипт из 4 шагов — противоречит коучинговой философии проекта.
- Переключатель режимов (Assistant / Coaching) скрыт через `className="hidden"`.
- Markdown рендерится минимально — только bold.
- Стриминг архитектурно не поддержан.
- Визуальная часть также требует переработки.

**Статус: необходимо провести отдельную сессию планирования по фронтенду AI-чата.** Не начинать реализацию до этого обсуждения.

---

*Last updated: 2026-05-28*
*Status: Planning — decisions in progress*
