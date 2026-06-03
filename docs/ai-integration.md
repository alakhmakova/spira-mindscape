# AI Integration — Technical Reference

This document describes the complete technical implementation of the AI chat system in Spira: how API keys are stored, how messages travel from the browser to an LLM and back, what context the AI receives, and how the frontend renders the response in real time.

---

## Architecture Overview

```
Browser (React)
    │
    │  POST /api/ai/chat  (JSON body)
    ▼
Vite dev-server proxy  ──────────────────────►  Spring Boot  :8080
(vite.config.ts)                                     │
                                                     │  1. Safety check
                                                     │  2. Load + decrypt API key
                                                     │  3. Build system prompt
                                                     │  4. Forward to LLM (SSE)
                                                     │
                                               Anthropic API
                                                     │
                                          SSE stream of tokens
                                                     │
                                    ◄────────────────┘
Browser receives tokens one by one
and appends them to the chat message
```

There are three separate HTTP flows:
1. **Key management** — `POST/GET/DELETE /api/ai/keys` — save, list, or delete API keys
2. **Chat** — `POST /api/ai/chat` — streams a response as Server-Sent Events
3. **Proposals** — `GET/POST /api/ai/proposals` — AI-generated goal changes waiting for approval

---

## 1. Database Schema

The AI system adds three things to the database, defined in [`backend/src/main/resources/db/migration/V7__ai_schema.sql`](../backend/src/main/resources/db/migration/V7__ai_schema.sql):

```sql
-- Stores per-user, per-provider API keys (encrypted at rest)
CREATE TABLE ai_api_keys (
    id          BIGSERIAL    PRIMARY KEY,
    app_user_id BIGINT,                     -- the owning user (from CurrentUserProvider)
    provider    VARCHAR(32)  NOT NULL,       -- 'ANTHROPIC' | 'OPENAI' | 'MISTRAL'
    model       VARCHAR(64),                 -- optional model override
    enc_key     TEXT         NOT NULL,       -- AES-256-GCM ciphertext, Base64
    key_hint    VARCHAR(16)  NOT NULL,       -- '••••1234' for display only
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (app_user_id, provider)           -- one key per provider per user
);

-- Queues AI-generated changes pending user approval
CREATE TABLE ai_proposals (
    id          BIGSERIAL    PRIMARY KEY,
    app_user_id BIGINT,
    goal_id     BIGINT       REFERENCES goal(id) ON DELETE CASCADE,
    type        VARCHAR(64)  NOT NULL,       -- 'target' | 'option' | 'note' | 'edit'
    payload     TEXT         NOT NULL,       -- JSON blob with proposed change
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Adds a persistent memory field to each goal (updated after GROW sessions)
ALTER TABLE goal ADD COLUMN ai_memory TEXT;
```

Migrations run automatically via Flyway when the Spring Boot app starts. The schema is versioned — if you add a new migration file it must have a version number higher than the current highest (`V7`). Running `docker compose down -v && docker compose up -d` resets the dev database entirely if you need a clean slate.

---

## 2. API Key Storage (BYOK)

**Bring Your Own Key** means users supply their own Anthropic/OpenAI/Mistral keys. Spira stores them encrypted — the raw key is never written to the database or returned through the API.

### Entity

[`backend/src/main/java/com/spiramindscape/backend/ai/key/AiApiKey.java`](../backend/src/main/java/com/spiramindscape/backend/ai/key/AiApiKey.java)

A standard JPA entity mapping to the `ai_api_keys` table. The critical field is `enc_key` — it holds the ciphertext, never the plaintext key.

### Service

[`backend/src/main/java/com/spiramindscape/backend/ai/key/AiKeyService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/key/AiKeyService.java)

- `saveKey(SaveKeyRequest)` — encrypts the raw key, stores it, returns a `KeyInfoResponse` with only the hint
- `listKeys()` — returns provider name + hint + model for all configured providers (no keys)
- `getKey(ProviderType)` — **internal only**, decrypts and returns the key to `AiChatService`
- `deleteKey(String)` — removes the key for a provider

```java
// How save works — the raw key never touches the DB column directly
entity.setEncKey(encryption.encrypt(request.apiKey()));  // ciphertext in DB
entity.setKeyHint(buildHint(request.apiKey()));           // '••••1234' in DB
```

The `StoredKey` record — `record StoredKey(String apiKey, String model)` — is the internal type used to pass the decrypted key between `AiKeyService` and `AiChatService`. It is never serialized to JSON.

### DTOs

- [`SaveKeyRequest.java`](../backend/src/main/java/com/spiramindscape/backend/ai/key/dto/SaveKeyRequest.java) — `{ provider, apiKey, model }` — received from frontend
- [`KeyInfoResponse.java`](../backend/src/main/java/com/spiramindscape/backend/ai/key/dto/KeyInfoResponse.java) — `{ provider, hint, model }` — sent to frontend; never contains the key

---

## 3. Encryption

[`backend/src/main/java/com/spiramindscape/backend/ai/crypto/EncryptionService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/crypto/EncryptionService.java)

Algorithm: **AES-256-GCM** — authenticated encryption that detects tampering.

### How it works

```
Encrypt("sk-ant-xxxxx")
    │
    ├── generate 12 random bytes  (IV — unique per encryption)
    ├── AES-256-GCM encrypt with the secret key
    ├── concatenate  [IV (12 bytes)] + [ciphertext]
    └── Base64-encode the combined bytes  →  stored in enc_key column

Decrypt(enc_key)
    │
    ├── Base64-decode
    ├── split: first 12 bytes = IV, remainder = ciphertext
    └── AES-256-GCM decrypt with the secret key  →  original API key
```

Because a fresh random IV is generated on every call, the same key produces a different ciphertext each time it is saved — this prevents pattern analysis even if two users have the same API key.

### The secret key

The 32-byte secret is configured via `ai.encryption.key` in [`application.properties`](../backend/src/main/resources/application.properties):

```properties
ai.encryption.key=${AI_ENCRYPTION_KEY:c3BpcmEtZGV2LW9ubHkta2V5LTMyYnl0ZXMteHl6eiE=}
```

- In development: the default hardcoded value is used (Base64 of a dev-only string)
- In production: set the `AI_ENCRYPTION_KEY` environment variable to a fresh 32-byte Base64 value

To generate a production key:
```bash
openssl rand -base64 32
```

**Never commit the production key.** If it leaks, all stored API keys are compromised — generate a new one and re-save all keys.

---

## 4. Provider Abstraction

The system is designed so that the rest of the codebase never knows which LLM is in use.

### Interface

[`backend/src/main/java/com/spiramindscape/backend/ai/provider/LlmProvider.java`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/LlmProvider.java)

```java
public interface LlmProvider {
    void streamChat(
        List<LlmMessage> messages,
        String systemPrompt,
        List<ToolSpec> tools,          // tools the model may call (empty = none)
        Consumer<String> onToken,      // called once per text chunk
        Consumer<ToolCall> onToolCall, // called once per completed tool call
        Runnable onComplete,           // called when stream ends normally
        Consumer<Throwable> onError    // called on failure; onComplete is NOT called
    );
    ProviderType providerType();
}
```

Every implementation blocks the calling thread until the stream ends. The caller runs this in a thread pool (`Executors.newCachedThreadPool()` in `AiChatService`).

### Implementations

Two providers are implemented: **Anthropic** and **Mistral**. OpenAI is a stub.

[`AnthropicProvider.java`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/anthropic/AnthropicProvider.java) — Anthropic Messages API.
[`MistralProvider.java`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/mistral/MistralProvider.java) — Mistral chat completions (OpenAI-compatible format).

It uses Java's built-in `HttpClient` (no extra HTTP library needed):

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.anthropic.com/v1/messages"))
    .header("x-api-key", apiKey)
    .header("anthropic-version", "2023-06-01")
    .header("content-type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
    .build();

// ofLines() delivers the response as a stream of strings, one per line
HttpResponse<Stream<String>> response = httpClient.send(
    request,
    HttpResponse.BodyHandlers.ofLines()
);
```

The Anthropic streaming API returns lines like:
```
event: content_block_delta
data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}
```

The provider parses `content_block_delta` events with `type=text_delta` and forwards the `text` field to `onToken`. Tool calls arrive as `content_block_start` (type `tool_use`) + `input_json_delta` chunks; the provider accumulates the partial JSON and emits it via `onToolCall` when the stream ends. Mistral does the same with its OpenAI-style `delta.tool_calls[].function.arguments` deltas.

### Factory

[`LlmProviderFactory.java`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/LlmProviderFactory.java) constructs the right implementation given a `ProviderType`. Anthropic and Mistral are implemented; OpenAI throws `UnsupportedOperationException` until implemented.

### Adding a new provider

1. Implement `LlmProvider` in a new package under `ai/provider/<name>/`. Parse the provider's SSE stream: forward text to `onToken`, accumulate tool-call argument deltas and emit via `onToolCall`, call `onComplete` at the end.
2. Add a case to `LlmProviderFactory.create(...)`.
3. Add the enum value to [`ProviderType.java`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/ProviderType.java).
4. Add the provider to `PROVIDERS_DEFAULT` in [`AiPanel.tsx`](../src/components/ai/AiPanel.tsx) (vendor name, key prefix, fallback model list).

The rest of the system (chat orchestration, key storage, prompts, proposals) needs no changes — that is the point of the abstraction.

---

## 4a. Tool Calling & the Proposal System

The AI cannot write to the database directly. Instead it uses **native tool calling** (function calling) to *propose* changes, which the user approves in the UI. This is far more reliable than asking the model to emit a delimited text block — the provider API guarantees the tool arguments are valid, structured JSON, even for small models.

### The flow

```
User: "rename the goal to X"
   │
   ▼
AiChatService offers the `propose_goal_change` tool in the request
   │
   ▼
Model streams optional text + a tool call:
   propose_goal_change({"kind":"edit","field":"title","value":"X","reasoning":"..."})
   │
   ▼
Provider accumulates the tool-call JSON, fires onToolCall
   │
   ▼
AiChatService persists the proposal (ai_proposals, status=PENDING) when the
chat is scoped to a goal, then sends an SSE  event: proposal  whose data is the
args JSON with the persisted `proposalId` merged in
   │
   ▼
ai-api.ts parses the `proposal` event → onProposal(argsJson)
   │
   ▼
AiPanel.tsx: proposalFromToolArgs() builds a Proposal (capturing serverId), attaches it to the message
   │
   ▼
ProposalCard renders Accept / Dismiss.
  • Accept  → applyProposal() calls the real Zustand store action
              (updateGoal / addTarget / addOption / addReality / addResource), which
              persists the actual goal change via GraphQL; AND POST /proposals/{id}/approve
  • Dismiss → POST /proposals/{id}/reject
```

### Proposal persistence (survives reload)

A proposal lives in three places, by design:

| Where | Purpose | Lifetime |
|---|---|---|
| `ai_proposals` table (server) | Durable record + status source of truth | Until approved/rejected |
| React state (`msgs[].proposals`) | Inline card in the conversation | Until panel state is rebuilt |
| `localStorage` (chat transcript) | Replays the conversation incl. cards | Until cleared |

The wiring:

- **Create** — [`AiChatService.sendProposal`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/AiChatService.java) calls [`AiProposalService.create(goalId, kind, argsJson)`](../backend/src/main/java/com/spiramindscape/backend/ai/proposal/AiProposalService.java) and embeds the new row id into the SSE event as `proposalId`. Global chats (no `goalId`) are **not** persisted — `propose_goal_change` only applies to a goal. Persistence is best-effort: if the DB write fails the card is still streamed, it just won't survive a reload.
- **Approve / reject** — `AiPanel.tsx` calls `approveProposal(id)` / `rejectProposal(id)` ([`ai-api.ts`](../src/components/ai/ai-api.ts)) when the card is resolved, so the server status matches the UI.
- **Restore** — on opening a goal, the panel calls `GET /api/ai/proposals/goal/{goalId}` and surfaces any still-`PENDING` proposals not already present in the restored transcript (e.g. localStorage was cleared, or the proposal was made in another session). Approving a change still persists the underlying goal edit via GraphQL regardless.

### The one tool: `propose_goal_change`

There is a single tool with a `kind` discriminator rather than many tools. One tool keeps the model's decision simple and reliable; the `kind` field covers every goal-data mutation. Defined in [`AiChatService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/AiChatService.java) as `PROPOSAL_TOOLS`:

**Create / goal-level** (no `id`):

| `kind` | Required args | Applied by (frontend) |
|---|---|---|
| `edit` | `field` (`title`\|`description`), `value` | `updateGoal(id, { [field]: value })` |
| `confidence` | `value` (1–10) | `updateGoal(id, { confidence })` |
| `deadline` | `value` (ISO date) | `updateGoal(id, { deadline })` |
| `target` / `task` | `title`, opt. `deadline_value` | `addTarget(id, { type:"binary", title, done:false, deadline? })` |
| `option` | `value` | `addOption(id, value)` |
| `obstacle` / `action` | `value` | `addReality(id, kind, value)` |
| `note` | `title`, `value` (body) | `addResource(id, { type:"note", title, body })` |

**Edit existing / change state** (require `id` — the item id taken from the goal context, see below):

| `kind` | Required args | Applied by (frontend) |
|---|---|---|
| `edit_target` | `id`, `value` (title), opt. `deadline_value` | `updateTarget(goalId, id, { title, deadline? })` |
| `edit_option` | `id`, `value` | `updateOption(goalId, id, { text })` |
| `edit_obstacle` / `edit_action` | `id`, `value` | `updateReality(goalId, kind, id, value)` |
| `edit_note` | `id`, `title`, `value` (body) | `updateResource(goalId, id, { title, body })` |
| `complete_target` | `id`, `done` (`"true"`/`"false"`) | `updateTarget(goalId, id, { done })` |
| `target_progress` | `id`, `value` (number) | `updateTarget(goalId, id, { current })` |
| `select_option` | `id` | `selectOption(goalId, id)` |
| `checklist_item` | `id` (item id), opt. `value`/`done`/`deadline_value` | `updateTarget(goalId, parentId, { items })` — text/done/**due date** of one item |
| `add_checklist_item` | `id` (checklist **target** id), `value`, opt. `deadline_value`/`done` | `updateTarget(goalId, id, { items:[…, newItem] })` — adds a sub-task |

So "one tool" does **not** mean "only create" — one entry point can create data, **edit existing items**, and change their state. Every change still requires explicit user approval (per the spec's Proposal & Approval System).

### Referencing existing items: ids in the context

To edit or change an existing item, the model needs its id. [`GoalContextBuilder`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/GoalContextBuilder.java) therefore prints each item's id inline, e.g. `- [binary id=42] …`, `- [x] (id=7) …`, `- (id=12) obstacle text`, checklist items as `- (id=88) [ ] …`. The model copies that number into the tool's `id` argument; the frontend (`proposalFromToolArgs` → `itemId`) then routes the approved change to the right store action. Ids are the DB ids, which match the frontend store ids after a goal is loaded.

### What the AI cannot do: deletion

There is **no delete tool** — by design (deleting via free-form chat is risky and an approval card for "remove everything" is easy to mis-issue). Instead the system prompt instructs the AI to **explain where to delete in the UI** (goal card menu, a target row's trash icon, an item's Remove ×, the deadline picker's Clear) and never to claim it deleted anything.

**Proposal card "Edit" = instruct the AI.** Each pending card has Accept / Edit / Dismiss. **Edit is not a manual form** — it opens an instruction box; sending it dismisses the current card and sends the user's instruction back to the AI ("Revise your proposed … : <instruction>") so the AI re-proposes. This is how the user refines a suggestion (e.g. "in English", "make it shorter") without hand-editing. (The system prompt also makes a stated goal-data language preference persist for the rest of the conversation.)

### Available in both chat and GROW

Proposals work in **both** regular chat and GROW sessions. A GROW session must be able to improve the goal — capture an obstacle, refine the description, add a target — otherwise the session has no lasting value. The difference is tone, set by the system prompt: in chat the AI proposes changes directly on request; in GROW it surfaces them naturally as the conversation reveals them, and asks before proposing.

### Adding a new tool

1. Add a `ToolSpec` to `PROPOSAL_TOOLS` (or a new list) in `AiChatService` with a JSON-Schema `inputSchema`. The same schema is sent as `input_schema` (Anthropic) and `parameters` (Mistral) automatically.
2. Handle the new tool name in `AiChatService` if it needs different routing (currently every tool call becomes a `proposal` SSE event).
3. On the frontend, extend `proposalFromToolArgs()` and `applyProposal()` in `AiPanel.tsx` to map the new arguments to a Zustand store action.

> **Note — "tools" vs "mini-app tools":** the `propose_goal_change` tool is an LLM *function call*. This is different from the **Personal Tools / mini-apps** feature in `ai-configuration.md` (period tracker, job-application tracker, etc.), which is a separate, larger capability that lets the AI assemble small user-facing widgets from approved UI primitives. That feature is **planned, not built** — see [`ai-mini-apps-plan.md`](./ai-mini-apps-plan.md).

---

## 4b. Result-producing tools & the Agentic Loop

Two tools return a result the model then uses: `web_search` (Tavily — current/external info) and `read_resource` (the content of one of the goal's resources, §"Reading resource content"). Unlike `propose_goal_change` (fire-and-forget — the model calls it and the turn ends), these need an **agentic loop**: the model calls the tool, the backend produces the result, feeds it back, and the model continues generating using it.

### How they differ from proposals

| | `propose_goal_change` | `web_search` / `read_resource` |
|---|---|---|
| Needs a result back? | No | Yes |
| Loops? | No — turn ends | Yes — result fed back, model continues |
| Surfaced to UI as | `proposal` SSE event → card | normal streamed text |

### The loop ([`AiChatService.runAgenticLoop`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/AiChatService.java))

```
for up to MAX_TOOL_ITERATIONS (4):
  stream a model turn  →  forward text tokens to the client live
  collect tool calls
  emit any propose_goal_change calls as `proposal` events
  if any result-producing call (read_resource, or web_search with a Tavily key):
      append an assistant message echoing ALL the turn's tool calls
      append a `tool` result for EACH call:
        • web_search        → Tavily results
        • read_resource     → the resource's text (ResourceReadService)
        • propose_goal_change → "surfaced to the user for approval" (synthetic ack)
      loop  (model now continues using the results)
  else:
      send `done` and stop
```

Every `tool_use` must be answered with a `tool_result` when the conversation continues, so when we loop we provide a result for **all** of the turn's calls — including a synthetic acknowledgement for any `propose_goal_change` that shared the turn. Each provider call **blocks** until its stream ends, so the loop is a simple `for`. All iterations stream into the **same** SSE response — the client just sees continuous tokens.

### Feeding results back: the message model

The follow-up request must contain the model's tool call and the tool result in the provider's native format. [`LlmMessage`](../backend/src/main/java/com/spiramindscape/backend/ai/provider/LlmMessage.java) carries this:

- `LlmMessage.assistantToolCalls(text, calls)` — the assistant turn that made the call(s)
- `LlmMessage.toolResult(callId, resultText)` — the result, keyed by the tool-call id

Each provider serialises these in `buildRequestBody`:
- **Anthropic**: assistant `content` array with a `tool_use` block; result as a `user` message with a `tool_result` block (`toAnthropicMessage`).
- **Mistral**: assistant message with `tool_calls[]`; result as a `tool` role message with `tool_call_id` (`toMistralMessage`).

This is why `ToolCall` carries an `id` — it ties the result to the call.

### Availability

`web_search` is added to the tool list only when a Tavily key exists **and** the session is regular chat (not GROW — GROW defers execution work per the spec). If Tavily fails, [`TavilySearchService`](../backend/src/main/java/com/spiramindscape/backend/ai/search/TavilySearchService.java) returns an error string instead of throwing, so the model can gracefully say search was unavailable rather than crashing the stream.

---

## 5. System Prompt Assembly

[`AiChatService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/AiChatService.java) assembles the system prompt from two parts:

```
[1] BASE PROMPT (static) — one of two, chosen by sessionType:
    • CHAT_PROMPT — a capable general assistant (like Claude/ChatGPT). This is
      the DEFAULT. It answers, analyses, drafts, recommends, and proposes goal
      changes. It is NOT a coach by default.
    • GROW_PROMPT — pure coaching mode, used only during a GROW session
      (sessionType="grow"). Asks one question at a time, follows the user's
      thinking, and proposes goal changes naturally as the conversation reveals them.

[2] GOAL CONTEXT (dynamic, present only when a goalId is provided)
    A plain-text representation of the current goal's full data.
```

```java
private String buildSystemPrompt(Long goalId, String sessionType) {
    String basePrompt = "grow".equalsIgnoreCase(sessionType) ? GROW_PROMPT : CHAT_PROMPT;
    String goalContext = goalContextBuilder.build(goalId);
    if (goalContext.isBlank()) return basePrompt;
    return basePrompt + "\n\n" + goalContext;
}
```

The frontend sends `sessionType: "chat"` for the normal panel and `sessionType: "grow"` for GROW sessions (see `streamChat` calls in [`AiPanel.tsx`](../src/components/ai/AiPanel.tsx)).

> **Coaching is only for GROW sessions.** Regular chat is a normal assistant — this split is intentional and matches `ai-configuration.md` ("Separation from Regular Chat"). Do not make the chat prompt ask reflective coaching questions.

The role prompt must **not** force an output language. It only says "respond in the language the user writes in". When the user writes in a non-English language, the chat prompt asks once which language to use for goal *data* (titles, descriptions, targets).

### Goal Context

[`GoalContextBuilder.java`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/GoalContextBuilder.java) loads a `Goal` from the database and serializes it into a structured markdown block:

```
## Current Goal

**Title:** Learn Spanish
**Confidence:** 6/10
**Deadline:** 2026-12-31

**Description:**
I want to reach B2 level...

**Current actions:**
- Using Duolingo daily
- Watching TV shows in Spanish

**Current obstacles:**
- No speaking practice

**Options:**
- [ ] (id=3) Find a language partner
- [x] (id=4) Join a group class

**Targets:**
- [binary id=42] Complete 30-day streak: not done
- [numeric id=43] Vocabulary words: 240/1000

**Resources** (use the read_resource tool with the id to read one):
- [link id=7] SpanishPod101
- [note id=9] Recruiter notes
- [file id=11] CV_Anastasiya.pdf (application/pdf)
```

This block is injected before the conversation history in every request, so the AI always has current goal data without needing to store it in the conversation. Each item carries its **id** (`id=…`) so the model can reference it when proposing an edit or state change (see §4a), or when reading its content (see below).

### Reading resource content (on demand)

The context lists resources by **id / type / title only** — never their content. The model loads content when it actually needs it by calling the **`read_resource`** tool with the id. This keeps tokens down (a CV isn't re-sent on every message) and means content is fetched just-in-time. `read_resource` is offered whenever the chat is scoped to a goal (chat **and** GROW), and runs through the same agentic loop as web search (§4b).

[`ResourceReadService`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/ResourceReadService.java) produces the text (scoped to the goal — a resource from another goal returns "not found"):

| Resource type | What `read_resource` returns | Limit |
|---|---|---|
| `note` | the note body (HTML stripped) | 8 000 chars |
| `link` | the URL | — |
| `email` | name / role / email / phone | — |
| `file` (PDF) | text extracted with **Apache PDFBox** ([`ResourceTextExtractor`](../backend/src/main/java/com/spiramindscape/backend/ai/chat/ResourceTextExtractor.java)) | 15 pages / 12 000 chars |
| `file` (image) | "(image file — not readable as text)" | — |

Extraction is **text-only** and provider-agnostic (the file never leaves the backend — only extracted text enters the prompt, so it works with Anthropic, Mistral, Ollama). Scanned/image-only PDFs have no text layer and come back as "(no extractable text)"; the prompt tells the AI to ask the user to paste the text rather than invent it. When asked to **rewrite** a document (e.g. a CV), the AI proposes a **new `note`** rather than touching the original file.

> **Notes are the rich-text container.** A note's `body` is HTML (TipTap editor), so pasting from Word/Docs keeps common formatting (headings, bold, lists, links). The note detail view exports a note to **.txt / Word (.doc) / PDF** ([`note-export.ts`](../src/components/spira/note-export.ts)) with no backend dependency — txt strips HTML, Word uses an openable Word-HTML document, PDF uses the browser's Save-as-PDF. This closes the CV loop: paste or upload a CV → AI reads it → AI proposes a rewritten note → you export it. (DOCX *files* are intentionally not an upload type; paste the content into a note instead.)

### Role Prompt

The coaching role prompt is hardcoded in `AiChatService.ROLE_PROMPT`. Key elements:
- The AI is a coaching intelligence, not an assistant or advisor
- It raises awareness through questions; it does not give unsolicited advice
- It responds in the language the user writes in
- It acknowledges its limits (not a therapist, doctor, lawyer)

See [`docs/ai-configuration.md`](./ai-configuration.md) for the full philosophy and planned evolution of this prompt.

---

## 6. Safety Check

[`SafetyService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/safety/SafetyService.java)

Runs synchronously **before** any key lookup or API call. If the message matches a blocked pattern, a safe response is streamed back immediately and the LLM is never called.

Current implementation: keyword list (MVP). Matched patterns include self-harm keywords, requests for illegal activity instructions, and CSAM. No pattern is exposed to the user in the response message.

```java
if (!safety.isSafe(request.message())) {
    // stream the blocked message directly, no LLM involved
    blocked.send(SseEmitter.event().name("token").data(safety.blockedMessage()));
    blocked.send(SseEmitter.event().name("done").data(""));
    blocked.complete();
    return blocked;
}
```

---

## 7. Full Request Lifecycle

Here is what happens from the moment the user presses Enter in the chat:

```
1. Frontend (AiPanel.tsx)
   sendChat("Where am I stuck?")
   → builds history array from existing messages
   → calls streamChat({ goalId, message, history, provider })

2. ai-api.ts: streamChat()
   POST /api/ai/chat
   Body: { goalId: 42, message: "Where am I stuck?",
           history: [{role:"user", content:"..."}, ...],
           provider: "ANTHROPIC" }

3. Vite proxy (vite.config.ts)
   /api → http://localhost:8080
   (in production: the request goes directly to the backend)

4. AiController.chat()
   → delegates to AiChatService.chat(request)

5. AiChatService.chat()
   a. safety.isSafe(message)  — blocks if flagged
   b. resolveProvider("ANTHROPIC")  → ProviderType.ANTHROPIC
   c. keyService.getKey(ANTHROPIC)  → decrypts and returns StoredKey
      (throws HTTP 422 if no key configured → frontend opens ProviderSheet)
   d. buildSystemPrompt(goalId)  → ROLE_PROMPT + goalContextBuilder.build(42)
   e. buildMessages(request)  → history + current message as LlmMessage list
   f. providerFactory.create(ANTHROPIC, apiKey, model)  → AnthropicProvider
   g. new SseEmitter(3 minutes)  — returned to controller immediately
   h. executor.submit(() → provider.streamChat(...))  — runs in background thread

6. AnthropicProvider.streamChat()
   POST https://api.anthropic.com/v1/messages
   Headers: x-api-key, anthropic-version
   Body: { model, max_tokens: 8192, stream: true, system, messages }

7. Anthropic API streams back:
   data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"You"}}
   data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" seem"}}
   ...
   data: {"type":"message_stop"}

8. AnthropicProvider calls onToken("You"), onToken(" seem"), ...
   → AiChatService.sendToken(emitter, token)
   → emitter.send(...name("token").data(jsonEncode(token)))   // JSON-encoded

9. The HTTP response to the browser is the SSE stream (tokens are JSON strings,
   so newlines inside a token are escaped and never break framing):
   event: token
   data: "You"

   event: token
   data: " seem"
   ...
   event: done
   data:

10. ai-api.ts: SSE reader loop
    Accumulates data lines per event; on the blank line it dispatches.
    For "token" it JSON.parses the data → onToken(text). On "done" → onDone()

11. AiPanel.tsx: onToken callback
    accumulated += tok
    setMsgs(p => p.map(m => m.id === id ? {...m, content: accumulated} : m))
    → React re-renders the message in real time as tokens arrive

12. onDone callback
    setMsgs(p => p.map(m => m.id === id ? {...m, streaming: false} : m))
    → removes the blinking cursor
```

---

## 8. Frontend: API Layer

[`src/components/ai/ai-api.ts`](../src/components/ai/ai-api.ts)

Three exported functions:

```typescript
// Streams a chat response. Calls onToken for each token, onDone when complete.
export async function streamChat(params: StreamChatParams): Promise<void>

// Saves an API key for a provider. Throws on failure.
export async function saveApiKey(provider: string, apiKey: string, model?: string)

// Lists all configured providers (returns KeyInfoResponse array).
export async function listApiKeys()
```

The SSE reader does not use the browser's `EventSource` API (which only supports GET). Instead it uses `fetch` + `ReadableStream`, with a **spec-compliant SSE parser**: it accumulates `data:` lines per event and dispatches on the blank line that terminates an event.

```typescript
// One event being assembled. An event may span several `data:` lines
// (joined with "\n") and is dispatched on a blank line.
let eventName = "";
let dataLines: string[] = [];

const dispatch = () => {
    const data = dataLines.join("\n");
    if (eventName === "token") {
        // tokens are JSON-encoded by the backend (see below) — decode them
        let text = data;
        try { text = JSON.parse(data); } catch { /* fall back to raw */ }
        onToken(text);
    } else if (eventName === "proposal") onProposal?.(data.trim());
    else if (eventName === "done")  { onDone();  /* stop */ }
    else if (eventName === "error") { onError(data.trim()); /* stop */ }
    eventName = ""; dataLines = [];
};
// read loop: split on "\n"; "" → dispatch(); "event:x" → eventName; "data:y" → dataLines.push
```

> **Why tokens are JSON-encoded.** An LLM text token frequently contains newlines (Markdown headings, lists, blank lines between paragraphs). A raw newline inside an SSE `data:` value breaks event framing — the part after the newline is no longer prefixed with `data:` and is silently dropped, which **truncates the message**. The backend therefore serialises every token as a JSON string (`"...\n..."`), guaranteeing one `data:` line per token; the frontend `JSON.parse`s it back. The `proposal`/`done`/`error` events carry single-line payloads and are sent verbatim.

Error mapping:
- HTTP 422 → `onError("NO_KEY")` → frontend opens the provider key sheet
- Network failure → `onError("NETWORK")` → toast shown
- Any other error → `onError("Server error: 500")` → toast shown

### Authentication & CSRF (every AI request)

All `/api/ai/**` endpoints require an authenticated session and — for mutating
requests — a CSRF token. This is enforced by `SecurityConfig` (Google OAuth login,
session cookie, CSRF via the double-submit cookie pattern). The AI client therefore
**must** mirror the main GraphQL client:

```typescript
// mutating requests (POST /chat, POST /keys, PATCH /keys/:p, POST /proposals/:id/...)
fetch(url, {
  method: "POST",
  credentials: "include",                          // send the session cookie
  headers: { "X-XSRF-TOKEN": getCsrfToken(), ... } // echo the XSRF-TOKEN cookie
});
// reads (GET) only need credentials: "include"
```

`getCsrfToken()` (in [`src/lib/spira/auth.ts`](../src/lib/spira/auth.ts)) reads the
non-HttpOnly `XSRF-TOKEN` cookie that Spring Security writes.

> ### 🐞 Bug & fix: "can't save the Mistral key" (and any AI mutation)
>
> **Symptom:** after Google auth was merged in, saving *any* provider key failed
> (it surfaced first on Mistral). The same broke every AI mutation and would have
> broken chat itself.
>
> **Root cause — two parts:**
> 1. **Frontend:** `ai-api.ts` predated auth and sent plain `fetch` calls with no
>    `credentials` and no `X-XSRF-TOKEN`. Spring Security rejected the `POST` with
>    **403 (missing CSRF token)** — or **401** if no session existed. It was never
>    a Mistral-specific problem; every provider and every mutation was affected.
> 2. **Backend:** `AiKeyService` / `AiProposalService` still used a hard-coded
>    `DEV_USER_ID = 1L` stub instead of the authenticated user, a leftover from
>    before auth existed.
>
> **Fix:**
> - `ai-api.ts` now adds `credentials: "include"` to every request and
>   `X-XSRF-TOKEN: getCsrfToken()` to every mutation (`mutationHeaders()` helper).
> - `AiKeyService` and `AiProposalService` resolve the real user via
>   `CurrentUserProvider.getCurrentUser().getId()`.
> - **Threading gotcha:** the chat SSE loop runs on a background thread pool, but
>   `CurrentUserProvider` reads the Spring Security context from a `ThreadLocal`.
>   The executor is therefore wrapped in `DelegatingSecurityContextExecutorService`
>   so the caller's security context propagates to the worker thread — otherwise
>   creating a proposal mid-stream throws `IllegalStateException: No authenticated
>   AppUser`.
>
> **Regression tests:** `AiKeySecurityIntegrationTest` (anonymous→401, no-CSRF→403,
> auth+CSRF→200), `AiKeyServiceTest` / `AiProposalServiceTest` (user scoping),
> and `ai-api.test.ts` (client sends the CSRF header + credentials).
>
> **Reminder:** you must be **logged in** (visit `/oauth2/authorization/google`,
> or the SPA login page) before saving a key — otherwise you get a 401.

---

## 9. Frontend: State Machine

[`src/components/ai/AiPanel.tsx`](../src/components/ai/AiPanel.tsx)

The panel has five modes:

| Mode | Meaning |
|---|---|
| `chat` | Regular goal chat (or global chat when no goal selected) |
| `grow-start` | Duration picker overlay is shown before a GROW session |
| `grow-active` | GROW session in progress, timer counting down |
| `grow-closing` | Timer passed 80% — AI shifts toward conclusion |
| `grow-end` | Session finished, "Save memory?" card shown |

The panel reads `context.goalId` from the AI store ([`src/components/ai/ai-store.ts`](../src/components/ai/ai-store.ts)) and looks up the full goal from the Spira store. When no goal is in context (All Goals page), GROW is hidden and different suggestions are shown.

### Key state variables

```typescript
const [msgs,  setMsgs]  = useState<Msg[]>([]);   // regular chat messages
const [gmsgs, setGmsgs] = useState<Msg[]>([]);   // GROW session messages
const [busy,  setBusy]  = useState(false);        // stream in progress
const [session, setSession] = useState<{ total, remaining, mins } | null>(null);
const stopRef = useRef(false);                    // set true to abort stream
```

### Transcript persistence

Regular chat (`msgs`) is cached in `localStorage` so the conversation — and the proposal cards in it — survive closing the panel or reloading the page. Buckets are **per scope**: one key per goal (`spira:ai-chat:<goalId>`) plus a `…:global` bucket for the All-Goals chat. Helpers `loadTranscript` / `saveTranscript` (top of `AiPanel.tsx`) handle this; storage is capped at the last `CHAT_MAX_MESSAGES` (100) and skips the in-flight streaming placeholder. On a goal switch the panel reloads the matching bucket.

**GROW sessions (`gmsgs`) are intentionally NOT persisted** — they are timer-bound and ephemeral. Proposals made *during* a GROW session are still persisted server-side and reappear (via the `GET /proposals/goal/{id}` restore) when the goal's regular chat is next opened.

### Stopping a stream

The stop button sets `stopRef.current = true`. The `onToken` callback checks this flag before appending each token, effectively discarding the rest of the stream without closing the connection. This is intentional — closing the connection mid-stream can cause errors on some LLM APIs.

### Provider connection status

On mount, `listApiKeys()` is called to populate which providers have keys saved. This drives the green/amber dot and model label in the header strip. If the call fails (backend not running), the error is silently swallowed — the panel still opens.

---

## 10. Where the Panel Appears

[`src/components/shell/AppShell.tsx`](../src/components/shell/AppShell.tsx) renders `<AiPanel />` once, at the top level. The panel is always mounted; it uses `display: none` (via conditional rendering in the `AiPanel` component itself) when closed.

The context (which goal the AI is aware of) is set by the goal page:

```typescript
// src/routes/goals.$goalId.tsx
useEffect(() => {
    setContext({ goalId });           // set goal context on enter
    return () => setContext({});      // clear goal context on leave
}, [goalId, setContext]);
```

When the panel is opened from the All Goals page, `context.goalId` is undefined and the panel operates in global mode (no GROW, different suggestions).

---

## 11. Configuration Summary

| What | Where | Default (dev) | Production |
|---|---|---|---|
| Backend port | `application.properties` | `8080` | `PORT` env var |
| Database URL | `application.properties` | `localhost:5432/spira` | `DATABASE_URL` env var |
| Encryption key | `application.properties` | hardcoded dev value | `AI_ENCRYPTION_KEY` env var |
| Frontend dev port | `vite.config.ts` | `5173` | — |
| API proxy | `vite.config.ts` | `/api → :8080` | nginx / reverse proxy |

### Running locally

```bash
# Start the database
docker compose up -d

# Start the backend (from project root)
cd backend && .\mvnw.cmd spring-boot:run       # Windows
cd backend && ./mvnw spring-boot:run           # Linux/Mac

# Start the frontend
npm run dev
```

The Vite dev server on port 5173 proxies all `/api/*` and `/graphql/*` requests to the Spring Boot backend on port 8080. In production, a reverse proxy (nginx, Caddy, etc.) handles this routing.

### Connecting the 3 providers (BYOK)

Each provider is connected the same way: the user pastes their own key into the panel's **"Bring your own key"** sheet, picks a model, and it is stored encrypted (one key per provider). Only Anthropic and Mistral actually run; OpenAI is a stub.

| Provider | Where to get a key | Key format | Default model | Models endpoint (live list) |
|---|---|---|---|---|
| Anthropic | console.anthropic.com → API Keys | `sk-ant-…` | `claude-sonnet-4-6` | `GET https://api.anthropic.com/v1/models` |
| Mistral | console.mistral.ai → API Keys | (no fixed prefix) | `mistral-large-latest` | `GET https://api.mistral.ai/v1/models` |
| OpenAI | platform.openai.com → API Keys | `sk-…` | — (not implemented) | — |
| Tavily (web search) | tavily.com → API Keys | `tvly-…` | n/a (search, not chat) | n/a |

Tavily is stored the same way (BYOK, encrypted) under provider `TAVILY`, but it is a **search** key, not a chat provider — it never appears as an active LLM and has its own small section in the key sheet.

The model dropdown in the panel calls `GET /api/ai/keys/{provider}/models` ([`AiModelService.java`](../backend/src/main/java/com/spiramindscape/backend/ai/model/AiModelService.java)), which fetches the **real** model list from the provider using the stored key. Picking a model calls `PATCH /api/ai/keys/{provider}` with `{ "model": "…" }`.

> **Model capability matters for tool calling.** Small models (e.g. `mistral-small`, `magistral-small`) may call tools unreliably. If proposals don't appear, switch to a flagship model (`mistral-large-latest`, `claude-sonnet-4-6`).

---

## 12. What Is Not Yet Implemented

These items exist in the plan ([`docs/ai-configuration.md`](./ai-configuration.md)) but not in the code:

| Feature | Status | Notes |
|---|---|---|
| Anthropic provider | ✅ Done | Streaming + tool calling |
| Mistral provider | ✅ Done | Streaming + tool calling (OpenAI-compatible) |
| Goal-data proposals (create) | ✅ Done | `propose_goal_change` tool: title, description, confidence, deadline, target, option, obstacle, action, note. Works in chat **and** GROW. |
| Edit existing items & state | ✅ Done | Same tool, edit/state kinds (`edit_target`, `edit_option`, `edit_obstacle`, `edit_action`, `edit_note`, `complete_target`, `target_progress`, `select_option`, `checklist_item`, `add_checklist_item`) referencing item `id` from the context. See §4a. |
| Checklist sub-tasks | ✅ Done | A checklist target holds sub-tasks (items). The AI can add one (`add_checklist_item`), edit its text, check/uncheck it, and set its **due date** (`checklist_item` with `deadline_value`). Items exist only on checklist targets. |
| Reading resources | ✅ Done (text, on demand) | `read_resource` tool loads a note/PDF/link/contact's text only when needed (not embedded in every request). PDF via PDFBox. Provider-agnostic. Images / scanned PDFs aren't readable (no text layer). Rewrites are proposed as a new note. |
| Deletion | ⛔ By design | No delete tool; the AI explains where to delete in the UI instead. |
| Web search (Tavily) | ✅ Done (needs live testing) | `web_search` tool via Tavily (BYOK key). Agentic loop in `AiChatService.runAgenticLoop`. Offered only in chat when a Tavily key exists. See section 4b. |
| Proposal persistence | ✅ Done | Goal-scoped proposals are stored in `ai_proposals` (PENDING), the id is streamed to the client, approve/reject hit the server, and pending cards are restored on reload. See section 4a. |
| Chat transcript persistence | ✅ Done (client-side) | Regular chat cached in `localStorage` per goal/global scope. GROW sessions are not persisted (ephemeral). See section 9. |
| OpenAI provider | Stub | `LlmProviderFactory` throws `UnsupportedOperationException` |
| **Mini-app tools (Personal Tools)** | ❌ Planned only | Spec's "Personal Tools" — AI assembles widgets (trackers) from approved UI primitives. Design is in [`ai-mini-apps-plan.md`](./ai-mini-apps-plan.md); not yet coded. |
| RAG (coaching book retrieval) | ❌ Not started | Requires `pgvector`, book chunking, embedding pipeline |
| GROW session backend state | ❌ Not started | `GROWSession` entity, timing fields not in DB; timer is currently frontend-only |
| AI memory persistence (GROW) | ❌ Not started | `ai_memory` column exists, nothing writes to it; the GROW "Save memory" card is a stub |
| Chat transcript on the server | ❌ Not started | Transcript is `localStorage`-only — not synced across devices and lost if storage is cleared (pending proposals are still recoverable from the server) |
| Google OAuth user binding | ✅ Done | `AiKeyService` / `AiProposalService` resolve the user via `CurrentUserProvider`; the frontend sends the session cookie + CSRF token (see §8). |
| AI-based safety check | Planned | Currently keyword-only in `SafetyService` |

The current implementation supports a real, goal-aware conversation with an Anthropic or Mistral key, and lets the AI propose concrete goal changes (which the user approves) in both chat and GROW modes.

See [`ai-testing.md`](./ai-testing.md) for what to test and how.
