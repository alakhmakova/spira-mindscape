# Tech Stack

This document is a production architecture guide for agents building Spira.

Its purpose is not only to describe the current prototype. Its purpose is to give future agents enough technical direction to build a production-ready backend and frontend while preserving the current Spira product model.

Agents must treat `specs/mission.md` as product intent and this file as technical direction.

## Product Type

Spira is a structured goal achievement platform with an AI goal-support agent.

It is not a simple CRUD app. It is a behavioral structure system for clarifying goals, organizing complex context, tracking progress, and helping users move from intention to action.

The technical architecture must support:

- complex goal workspaces
- structured GROW-based goal modeling
- dedicated GROW coaching sessions
- resources attached to goals
- progress calculation from targets
- AI-assisted coaching and execution
- AI-created goal-specific mini tools
- user approval before AI applies important changes
- production-grade persistence, testing, security, and scalability

## Production Target Stack

### Frontend

- React
- TypeScript
- TanStack Router / TanStack Start or an equivalent React routing/application framework
- Tailwind CSS
- Radix UI primitives or equivalent accessible headless UI primitives
- Playwright for E2E tests

The frontend should remain a calm, structured personal operating system. It should prioritize inline editing, strong visual hierarchy, low-friction progress tracking, and clear goal context.

### Backend

- Spring Boot
- GraphQL API
- PostgreSQL

The backend owns durable product data, user accounts, permissions, AI sessions, AI proposals, approvals, file metadata, extracted resource text, notifications, and audit logs.

### File Storage

Uploaded resources must not be stored as large blobs in PostgreSQL.

Production file handling should use object storage such as S3, Cloudflare R2, or an equivalent storage service.

PostgreSQL stores metadata and extracted text. Object storage stores the original file.

### AI System

The AI system should be implemented as a backend-controlled orchestration layer.

It may be a Spring Boot module initially, but the architecture should allow it to become a separate service later if needed.

The AI system must:

- read permitted goal data
- read permitted resources
- create proposed changes
- create or update text resources with user approval
- propose and create goal-specific mini tools with user approval
- run dedicated GROW sessions using backend-controlled session state
- never apply important mutations without user approval
- maintain goal-scoped AI sessions
- support safety checks before helping with a goal or action

## Current Frontend Prototype Note

The current frontend prototype stores data locally in the browser using Zustand persistence.

This is useful for UI development and frontend-only iteration, but it is not the production persistence model.

If an agent is asked to work on frontend only before backend exists:

- keep using the current frontend model as the source of truth
- do not invent backend-only fields in frontend state
- preserve the current goal structure
- keep data in local persisted state only as a temporary prototype mechanism
- avoid designing UI around data that does not exist in the current frontend model

When production backend work begins, local persisted state must be replaced by GraphQL data from the backend.

## Domain Model Source of Truth

The current Spira frontend model is the source of truth for the MVP goal structure.

Agents must not add fields just because they are common in generic productivity apps. Add fields only when they are required by the product model, current UI, production persistence, AI access, safety, or explicit user instruction.

## Goal System

Each goal contains:

- title
- description
- created date
- deadline
- achieved date
- confidence rating from 1 to 10
- reality
- resources
- options
- targets

The description is stored as a single description field. UX and AI should guide users toward SMART-quality goal descriptions, but the data model does not need a separate SMART object unless explicitly introduced later.

Goal progress is calculated from targets.

Goal status is not part of the current model. Achievement is represented by achieved date and calculated progress.

## Reality

Reality contains two context lists:

- Actions: actions already taken
- Obstacles: blockers or constraints

These are intentionally simple text lists.

Reality items are for context, coaching, and user reflection. They are not analytics objects and should remain simple text lists unless the product model is explicitly changed later.

## Resources

Resources are a critical part of Spira.

Resources are attached to goals and provide context for both the user and the AI.

Supported resource types:

- Note
- Link
- File
- Email

### Notes

Notes must be editable.

They should support rich text or markdown-style formatting.

The AI should be able to read notes and propose creating or updating notes. User approval is required before AI-generated changes are saved.

### Links

Links must be openable in the browser.

Production backend may fetch and extract readable page text when permitted, but link fetching must respect safety, privacy, robots/rate limits where applicable, and user intent.

### Files

Files must support:

- open
- download
- delete

PDF preview should be supported.

DOCX support may be limited to open/download and text extraction.

The AI should be able to read extractable text from:

- text files
- markdown
- non-scanned PDF
- DOCX

The AI may not fully understand:

- scanned PDFs without OCR
- complex formatted documents
- images without text extraction

### Email

Email resources represent relevant people or organizations connected to a goal.

They may include:

- name
- role or relationship
- email address
- optional phone or notes if needed by the UI

Use the product language "Email" for this resource type.

## Options

Options are possible strategies for achieving the goal.

Each option contains:

- description text
- selected state

The AI can help compare options, surface tradeoffs, and recommend a selected strategy, but user approval is required before changing selected state.

## Targets

Targets define measurable progress.

Supported target types:

- Numeric
- Done / Not Done
- Checklist

Use user-facing language in the product UI. Internal enums may use technical names, but the product should avoid exposing formal labels such as "binary" to users.

Each target:

- has a title
- is editable
- is removable
- may have a deadline
- may have an achieved date

All targets contribute equally to goal progress unless the product model is explicitly changed later.

## Progress Calculation

Progress is calculated only from targets.

Reality, resources, and options provide context but do not directly affect progress.

### Numeric Target

A numeric target tracks progress from a starting value toward a total value.

If no explicit start value exists, use the current frontend behavior as the reference:

- if current is greater than total, infer start from current
- otherwise infer start as 0

Progress is clamped between 0 and 1.

### Done / Not Done Target

Progress is:

- 1 when done
- 0 when not done

### Checklist Target

Progress is:

```text
completed checklist items / total checklist items
```

If a checklist has no items, progress is 0.

### Goal Progress

Goal progress is:

```text
average progress across all targets
```

If a goal has no targets, progress is 0.

## Goals Dashboard

The global goals page must support:

- card view
- table or timeline-style view
- search
- filtering
- sorting
- progress overview

The current frontend has card and timeline/table behavior. Future agents should preserve the intent: users must be able to scan all goals and quickly find what needs attention.

## Goal Page

Each goal has a dedicated page structured by the goal system:

- Goal
- Reality
- Resources
- Options
- Targets

The page should prioritize:

- inline editing
- visual hierarchy
- progress bars
- confidence signal
- deadlines
- low modal usage
- destructive action confirmation
- AI interaction in goal context
- goal-specific mini tools when they are useful for execution

### Side Panel Design Rules

Side panels (Sheet, Drawer, ResizableSheet) must follow these rules:

**No dividers between content and sticky input areas.**
When a panel contains a scrollable list and a sticky bottom input (chat-style), there must be no `border-t` or any other visual separator between them. The transition should be seamless — the input floats at the bottom without a line dividing it from the list above.

**Unchecked task placeholder icon.**
Use the `SquareDashed` icon from Lucide (`lucide-square-dashed`) for all unchecked/empty checkbox placeholders in task input rows and in task preview rows inside forms. Do not use a hand-crafted `<span>` or `<div>` with `border-dashed` for this purpose.

```tsx
import { SquareDashed } from "lucide-react";
// ...
<SquareDashed className="h-4 w-4 text-muted-foreground/50" />
```

**Panel content padding.**
Panels should use consistent horizontal padding (`px-4` or `px-6` for desktop, `px-2` for compact/mobile). The sticky bottom input area inherits the same horizontal padding as the scrollable list.

## AI-Created Mini Tools

Spira should support AI-created mini tools for goals that need a specialized interaction model beyond the standard goal sections.

These are small frontend applications or widgets generated for a specific goal or reusable pattern.

Examples:

- weight tracker with daily entries and a trend chart
- habit tracker
- job application tracker
- savings calculator
- study streak tracker
- sports practice log

Mini tools are not a replacement for goals, targets, resources, or options. They are execution aids attached to a goal.

### Tool Placement

Mini tools may appear:

- as a section on the goal page
- as a widget on the global goals page
- as a compact dashboard element connected to a specific goal

The UI should make it clear which goal a tool belongs to.

### Tool Data

Tool data must be stored as structured data owned by the backend.

A production implementation should include:

- tool definition metadata
- goal association
- tool instance data
- user-entered records
- created by AI or user metadata
- version information

Do not store production tool data only in generated frontend code.

### Tool Generation and Approval

The AI may propose a mini tool when it identifies that the standard Spira interface is not enough for a specific goal.

The user must approve tool creation before it appears in the workspace.

The user must approve important changes to existing tool data or tool structure.

### Tool Safety and Sandboxing

AI-created tools must be constrained.

They should not be allowed to run arbitrary unsafe code, access data outside their goal context, bypass authorization, perform external actions without approval, or introduce unreviewed dependencies.

A production implementation should prefer a controlled component/schema system over unrestricted generated code.

For example, the AI can generate a tool from approved primitives:

- numeric input
- date input
- checklist
- chart
- table
- progress display
- text note
- calendar-like log

This keeps the system flexible while preserving safety, testability, and maintainability.

## AI Provider System: Bring Your Own Key (BYOK)

Spira does not manage AI costs centrally. Users bring their own API keys. Supported providers at launch:

- **Anthropic** — claude-sonnet-4, claude-opus-4
- **OpenAI** — gpt-4o, o1
- **Mistral** — mistral-large, mistral-medium

### Key Storage

API keys must be:

- encrypted at rest in the database (AES-256 or equivalent)
- never logged, never returned to the frontend after initial save
- masked in the UI (e.g. `sk-ant-••••••1234`)
- associated with the user account, not individual goals

Users may store keys for multiple providers simultaneously and switch the active provider in account settings. Only one provider is active at a time for AI calls.

### Provider Abstraction Layer

The AI orchestration layer must abstract over all providers. GROW sessions, chat, and proposals must not depend on which provider is active.

```
AIProvider (interface)
  ├── AnthropicProvider
  ├── OpenAIProvider
  └── MistralProvider
```

Each implementation handles authentication headers, request format, response parsing, streaming, and error normalization into a common `AIError` type.

### Streaming

AI responses must stream token-by-token to the frontend. Waiting for a complete response breaks the coaching atmosphere. Use server-sent events (SSE) or WebSocket streaming from the provider through the Spring Boot backend to the frontend.

### Context Window

Design to the lowest common denominator: 128 000 tokens (OpenAI and Mistral limit). Claude supports 200 000 but the system must work across all providers.

---

## Coaching Knowledge Retrieval (RAG)

The coaching source books (`grow/Coaching for Performance.docx` and `grow/Coach the Person.docx`) must be indexed and made retrievable at query time. Including the full text in every AI request would exceed the context window of OpenAI and Mistral and waste context on irrelevant material.

### Storage: pgvector

Use the `pgvector` PostgreSQL extension — no separate vector database is needed.

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE coaching_chunks (
    id          BIGSERIAL PRIMARY KEY,
    source      TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536)
);

CREATE INDEX ON coaching_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 50);
```

### Chunking

Split the books into chunks of 400–600 tokens with 50–80 token overlap. Prefer paragraph boundaries. Index once at setup time.

### Embedding Model

Use OpenAI `text-embedding-3-small` (1536 dimensions) for indexing. If the user has no OpenAI key, use Mistral `mistral-embed` (1024 dimensions). Embeddings are computed once at index time.

### Query-Time Retrieval

At each AI request, embed the user's message, retrieve the 3–5 most similar chunks from `coaching_chunks`, and inject them into the system prompt. This grounds coaching responses in real source material without bloating every request.

---

## AI Memory (Goal-Level)

Each goal stores a single `ai_memory TEXT` field. This is a living document that represents what the AI knows about this goal and the user's progress on it.

### How it is used

The `ai_memory` block is included in the context of every AI request for that goal — both regular chat and GROW sessions. The AI always starts with this context rather than re-deriving it from conversation history.

### How it grows

When a GROW session ends and the user approves saving, the AI merges the previous memory block with new insights and writes an updated version. Meaningful exchanges in regular chat may also update the memory block, either explicitly (user asks to save something) or automatically for clear factual goal details.

### Memory compression

The memory block must not grow unboundedly. Every few updates the AI performs a compression pass — removing stale context and retaining the essence. This is invisible to the user.

### Schema addition

```sql
ALTER TABLE goals ADD COLUMN ai_memory TEXT;
```

---

## AI Chat Surfaces

Spira has two distinct AI chat surfaces. They share the same provider and streaming infrastructure but differ in context and capabilities.

### Global Chat (All Goals page)

The global chat has no active goal. The AI sees all goals belonging to the user.

Appropriate use cases: daily prioritization, cross-goal overview, deciding what to focus on, reflecting on overall progress.

The global chat AI can:

- discuss and compare all goals
- help the user prioritize
- propose creating a new goal (requires user approval)
- propose creating a global personal tool not attached to any goal (requires user approval)
- suggest navigating to a specific goal

The global chat AI cannot:

- start a GROW session (GROW is always goal-scoped)
- propose changes to existing goal data (those must happen inside the goal page)
- attach proposals to a goal without the user opening that goal

### Goal-Scoped Chat (Goal page)

The goal chat has one active goal. The AI sees the full goal context: title, description, reality, options, targets, resources, and `ai_memory`.

The goal chat AI can do everything the global chat can, plus:

- propose targets, options, resource notes, and goal edits for the active goal
- start a GROW session on this goal
- create goal-specific mini tools attached to this goal

All proposals are attached to the active goal and go through the approval workflow.

---

## Personal Tools (AI-Created Widgets)

Spira supports two kinds of AI-created tools:

**Goal-scoped tools** are created in the context of a specific goal when the standard goal interface is not enough.

**Global personal tools** are not tied to any goal. They are created for personal life management needs that exist outside the goal model (e.g. a period tracker, a habit log, a trash collection reminder).

### Placement — user controlled

The user decides where each tool appears. Options:

- **Goal page** — shown on the specific goal's page (goal-scoped tools only)
- **All Goals page** — pinned as a persistent widget for things the user wants to keep visible at all times
- **Tools page** — a dedicated page collecting all created tools; the default for tools needed occasionally

A tool may appear in more than one location. Placement is a per-tool user setting.

### Tool generation

Tools are assembled from approved UI primitives (numeric input, date input, checkbox, table, chart, progress display, text note, calendar log). The AI proposes a schema; the user approves before the tool is created. The frontend renders all tools through a single generic renderer.

### Data storage

```
tool_definitions   — schema, primitive config, placement settings, goal association (nullable)
tool_records       — user-entered data rows per tool instance
```

No tool data lives only in generated frontend code.

---

## AI Agent Integration

Spira's AI has two clearly separated modes. There is no automatic posture switching by context.

**Regular goal chat** is the default mode. The AI acts as an execution assistant — helping the user move the goal forward through research, drafts, target proposals, option analysis, next steps, and resource creation. Regular chat is always on. The user never selects it.

**GROW sessions** are a separate mode, explicitly started by the user via a dedicated "Start GROW session" button on the goal page. In a GROW session, the AI stays in coaching mode for the entire duration. If the user asks for execution work during a session, the AI acknowledges the request and offers to note it as a next action to pursue after the session ends.

The AI has access to:

- goal data
- targets
- reality
- options
- permitted resources
- goal-scoped AI conversation history

The AI can:

- suggest actions
- suggest targets
- analyze goal structure
- compare options
- create draft resources
- update text resources with user approval
- prepare messages or next-step plans
- propose and create goal-specific mini tools with user approval

The AI cannot:

- execute important changes without user approval
- send messages externally without user approval
- mutate goal data silently
- generate unrestricted production code that bypasses the approved tool system
- assist with illegal, unethical, exploitative, dangerous, self-harming, or harmful goals

## GROW Session System

GROW sessions are a dedicated AI feature, separate from ordinary AI chat.

Ordinary AI chat can be flexible and context-aware. A GROW session is explicitly started by the user for a specific goal when they want focused coaching work.

The backend must control GROW session structure. The AI should not freely improvise the session flow.

### Source Material

GROW coaching behavior must be derived from the source materials in:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`

Agents must not rely only on a generic prompt such as "use GROW." Before implementing production GROW sessions, the project should extract, review, and distill the source material into internal coaching guidance documents.

Recommended derived documents:

- `specs/coaching/grow-method.md`
- `specs/coaching/coaching-principles.md`
- `specs/coaching/session-rules.md`

These documents should summarize the coaching method, session rules, question style, boundaries, and behaviors to avoid.

### Session Model

A GROW session belongs to one goal.

It should persist:

- session id
- goal id
- user id
- selected duration: 15, 30, 45, or 60 minutes
- started at
- closing_starts_at (started_at + duration × 0.80)
- scheduled end time
- ended at
- status: ACTIVE | CLOSING | COMPLETE | ABANDONED
- transcript
- ai_memory (nullable — compressed context block, saved only if user approves at end)
- proposals (approval-required changes to goal data)

The backend tracks time state, not coaching phases. At 80% of session duration, the backend transitions status to CLOSING and signals the AI to begin guiding the conversation toward a natural conclusion. The AI does not announce this transition to the user.

### Session Opening and Closing

At the start of a GROW session:

- The AI acknowledges that the user has chosen to spend time on this goal
- It opens with an open question about where the user would like to start
- It does not announce the GROW framework or any phase names

During the session:

- The AI conducts a real coaching conversation
- GROW structure (Goal, Reality, Options, Will) may naturally emerge from the conversation
- The AI never announces phases or leads the user through a checklist
- The AI asks one good question at a time and follows the user's thinking

At 80% of session duration, the backend transitions to CLOSING status:

- The AI shifts toward integrative, action-oriented questioning without announcing it
- It guides the conversation toward a natural conclusion: consolidating what has been clarified, naming decisions, identifying next steps
- It does not cut off the user abruptly

At session end:

- The AI wraps up gracefully
- It asks the user whether to save the session memory
- If yes: the AI memory block and any proposals are persisted
- If no: memory is discarded
- Proposed changes to goal data go through the standard approval workflow

### Response Rules

During a GROW session, the AI should:

- ask focused coaching questions
- keep the conversation tied to the selected goal
- avoid generic motivational monologues
- avoid giving unsolicited advice or solving problems for the user
- offer to note execution requests as next actions to pursue after the session ends
- maintain safety and professional-boundary disclaimers where needed

### Approval Rules

GROW sessions may produce proposed changes, but they do not apply important changes automatically.

Examples of approval-required outputs:

- new targets
- edited goal description
- selected option
- new resource note
- mini tool suggestion

The user must approve before changes are applied.

### Validation

GROW session implementation must be tested with deterministic scenarios:

- session starts from a specific goal
- status transitions from ACTIVE to CLOSING at 80% of duration
- status transitions to COMPLETE when session ends
- AI does not give unsolicited advice or jump directly to solutions
- session memory is saved only when user approves
- memory is discarded when user declines
- proposed changes to goal data go through the approval workflow
- unsafe goals or unsafe requests are refused or redirected
- approved proposals mutate goal data correctly

## Backend Responsibilities

The backend must provide:

- authentication and user identity
- GraphQL schema and resolvers
- PostgreSQL persistence
- resource metadata persistence
- object storage integration
- file upload/download flow
- text extraction pipeline for readable resources
- BYOK API key storage (encrypted)
- provider abstraction layer (Anthropic, OpenAI, Mistral)
- streaming AI responses (SSE or WebSocket)
- coaching knowledge index (pgvector, one-time setup)
- coaching chunk retrieval at query time
- goal-level ai_memory field persistence and compression
- global AI sessions (All Goals chat)
- goal-scoped AI sessions
- GROW session state and phase persistence
- AI proposal and approval workflow
- AI-created mini tool definitions and tool data (goal-scoped and global)
- tool placement settings per tool
- web search capability for execution-mode AI requests
- audit log for important AI actions
- notification/reminder system
- safety checks for AI-assisted actions

## GraphQL Responsibilities

GraphQL should expose structured operations for:

- goals
- reality actions and obstacles
- resources
- options
- targets
- confidence updates
- AI sessions (global and goal-scoped)
- GROW sessions
- AI messages
- AI proposals
- approvals/rejections
- goal-specific mini tools
- global personal tools
- mini tool data records
- tool placement settings
- BYOK provider key management
- notifications

GraphQL should not be used to upload large files directly as base64 payloads.

File upload should use a dedicated upload flow, such as signed URLs or multipart upload endpoints coordinated by GraphQL metadata mutations.

## Testing Requirements

Production development must support:

- unit tests
- integration tests
- E2E tests with Playwright
- backend tests for GraphQL resolvers and services
- database integration tests
- AI orchestration tests using deterministic fixtures/mocks
- GROW session phase-flow tests
- CI/CD pipeline

Frontend changes must be validated across desktop and mobile behavior when they affect layout or interaction.

Backend changes must be validated with automated tests and migration checks.

AI changes must be validated with scenario-based tests, including refusal/safety cases and approval workflow cases.

AI-created mini tools must be validated with schema tests, rendering tests, permission tests, and data persistence tests.

## CI/CD

The production system should include a CI/CD pipeline that runs:

- frontend build
- frontend lint/type checks
- backend build
- backend unit and integration tests
- database migration validation
- Playwright E2E tests where appropriate

No code should be considered production-ready if it bypasses tests, type checks, or migration validation.

## User-Facing Error Handling Standards

Established: 2026-05-12.

### Language Rules

Technical internals must never appear in user-facing messages.

Prohibited phrases in the UI:
- "backend"
- "sync with the backend"
- "GraphQL"
- "HTTP 500"
- "endpoint"
- "check that it is running"

All error messages must be written from the user's perspective: what happened, and what they can do about it.

### Error Kind Classification

`SpiraApiError` carries a `kind` discriminator:

- `"network"` — the fetch itself failed (no connection, DNS failure, timeout). The user is likely offline.
- `"service"` — the server responded but returned an error (HTTP 4xx / 5xx or a GraphQL error body).

The kind is set in `api.ts` at the point of the `fetch` call.

### Icon Convention

| Error kind | Icon | Use |
|---|---|---|
| `"network"` | `GlobeOff` (Lucide) | User is offline or cannot reach the server |
| `"service"` | `Cable` (Lucide) | Server responded with an error |

### Global Sync Banner (AppShell)

The `AppShell` renders a thin banner above `<main>` for two states:

**Loading** (`isLoading && !syncError`):
- Background: `bg-primary-soft`
- Text: "Loading your goals…"
- No icon

**Error** (`syncError` set):
- Background: `bg-destructive/10`
- Icon: `GlobeOff` or `Cable` depending on `syncErrorKind`
- Text: the user-friendly message from the store
- Button: "Try again" with `RefreshCw` icon, calls `refreshGoals()`

### Goals Dashboard Empty States

The dashboard (`/`) handles three distinct empty states when `filtered.length === 0`:

| Condition | Heading | Icon | CTA |
|---|---|---|---|
| `isLoading && !hasLoaded` | "Loading your goals…" | — | — |
| `goals.length === 0 && syncError` (network) | "You appear to be offline" | `GlobeOff` (destructive) | "Try again" |
| `goals.length === 0 && syncError` (service) | "Couldn't load your goals" | `Cable` (destructive) | "Try again" |
| `goals.length === 0` (no error) | "Your journey starts here" | `Trophy` (primary) | "Create your first goal" (no `+` icon) |
| `filtered.length === 0` (has goals, filters active) | "No goals match" | — | — |

The "Create your first goal" button must **not** appear when `syncError` is set, because creation would fail.

## Offline Resilience

The frontend must remain fully visible and usable when the server is unreachable.

### Principle

The Spira frontend uses an optimistic, local-first model via Zustand:

- All mutations are applied immediately to local state
- Network sync happens asynchronously in the background
- If the network call fails, `syncError` is set and the banner is shown
- Local state is never wiped because of a network failure

This means users can continue reading, editing, and navigating even when offline or when the server is temporarily unavailable.

### On Initial Load With No Network

If the app loads for the first time with no network connection:

- `loadGoals()` sets `isLoading: true` then catches the fetch error
- `hasLoaded` is set to `true` even on failure (prevents infinite loading)
- `syncError` and `syncErrorKind` are set
- The dashboard renders the appropriate error empty state with a "Try again" button

Previously loaded goals are not yet persisted across sessions. When local persistence is added, the app should display stale cached goals with the offline banner instead of an error empty state.

### Offline Banner

When `syncError` is set, `AppShell` renders a persistent banner. This banner:

- Stays visible on all pages while the error persists
- Is cleared when `refreshGoals()` succeeds
- Shows the correct icon (`GlobeOff` for network, `Cable` for service)
- Does not use the word "backend"

### Future: Service Worker / Cache

When the app is production-deployed, a service worker should cache the app shell and last-known GraphQL responses so that:

- The app loads instantly even on slow connections
- Users can read their goals while offline
- Mutations are queued locally and replayed when connection is restored

This is not implemented in the current prototype but must be part of the production deployment plan.

## Architectural Principles

- Preserve the current Spira goal structure unless the constitution is intentionally updated.
- Keep the frontend calm, structured, and low-friction.
- Keep the backend modular and testable.
- Keep AI actions explicit, reviewable, and approval-based.
- Keep resources central to the system because they provide goal context.
- Allow AI-created mini tools only through controlled, safe, reviewable primitives.
- Do not design Spira as a generic task manager.
- Do not overcomplicate Reality; it is contextual, not analytical.
- Do not expose technical enum names in user-facing UI.
- Build for production safety from the start.
- Never expose technical internals (backend, GraphQL, HTTP status codes) in user-facing messages.
- The frontend must remain visible and navigable when the server is unreachable.
