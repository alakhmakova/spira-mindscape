# AI — Testing Guide

This document describes **what** to test in the AI subsystem and **how** to test it. It covers manual testing (fastest for a feature that talks to external LLMs) and where automated tests add value.

A key idea: the AI feature has three layers, and each is tested differently.

| Layer | What it does | How to test |
|---|---|---|
| **Provider** (`AnthropicProvider`, `MistralProvider`) | Talks to the real LLM API, parses the SSE stream | Manual (real key) + unit tests with a faked HTTP stream |
| **Orchestration** (`AiChatService`, `AiKeyService`, `SafetyService`, `GoalContextBuilder`) | Builds prompts, loads keys, runs safety, wires tools | Integration tests with a **mock** `LlmProvider` (no network) |
| **Frontend** (`AiPanel.tsx`, `ai-api.ts`) | Streams tokens, parses SSE, renders proposals | Manual in the browser + optional component tests |

The golden rule for automated tests: **never call a real LLM in a test**. Tests must be deterministic and free. Mock the provider.

---

## 1. Prerequisites for manual testing

```bash
docker compose up -d          # database
cd backend && .\mvnw.cmd spring-boot:run
npm run dev                   # frontend on :5173
```

Then open the app, click **ai coach**, open **Bring your own key**, and connect a real Anthropic or Mistral key.

---

## 2. Manual test scenarios

Work through these in the browser. Each row is one test.

### 2.1 Key management

| # | Steps | Expected result |
|---|---|---|
| K1 | Open key sheet, paste a valid key, Save & activate | Green dot + model label appear; toast "saved" |
| K2 | Reload the page, reopen the panel | Provider still shows as connected (key persisted, encrypted) |
| K3 | Open the model dropdown | Real model list loads from the provider (not the static fallback) |
| K4 | Pick a different model | Selection persists after reopening the sheet |
| K5 | Paste an **invalid** key, send a message | Error surfaces (stream error toast); no crash |
| K6 | Connect a second provider, switch active | Both stay connected; active dot moves |

**What's being verified:** key is stored encrypted (never returned), `GET /api/ai/keys` returns only masked hints, model PATCH works, live model fetch works.

### 2.2 Regular chat (sessionType = chat)

| # | Steps | Expected result |
|---|---|---|
| C1 | Ask a plain question ("summarise this goal") | Direct, helpful answer — **no** coaching counter-questions |
| C2 | Send a message with no key configured | Key sheet opens automatically (backend returns 422 → `NO_KEY`) |
| C3 | Write in Russian | AI replies in Russian; asks **once** which language to use for goal data |
| C4 | Watch tokens arrive | Text streams in progressively with a blinking cursor |
| C5 | Press Stop mid-stream | Streaming stops; partial text remains |
| C6 | Ask for **multi-line Markdown** ("give me a numbered list with headings") | The **whole** answer renders — headings, list items, blank lines. **Nothing is truncated** |
| C7 | Copy buttons | Hovering an assistant **and** your own message shows a Copy button that copies the full text |

> **C6 is the regression test** for the streaming-truncation bug: tokens that contain newlines (Markdown) used to break SSE framing and drop everything after the first newline. Tokens are now JSON-encoded on the wire (one `data:` line each). If you ever see an answer cut off at the first heading or list, this regressed — check `sendToken` (backend, must JSON-encode) and the `ai-api.ts` parser (must JSON-decode + accumulate `data:` lines).

### 2.3 Proposals (the core data-change flow)

| # | Steps | Expected result |
|---|---|---|
| P1 | "rename this goal to X" | Proposal card "New title: X" with Accept / Dismiss |
| P2 | Click **Accept** | Goal title actually changes on the page; toast "Goal updated" |
| P3 | Click **Dismiss** | No change; card shows "Dismissed" |
| P4 | "add a target: finish the portfolio" | Proposal card "New target"; Accept adds it to the goal |
| P5 | "add an obstacle: I have no free time" | Proposal "New obstacle"; Accept adds it to Reality |
| P6 | "add an option: hire a coach" | Proposal "Strategy option"; Accept adds it |
| P7 | "save a note: recruiter said apply in Q3" | Proposal "Resource note"; Accept saves it |
| P8 | Verify the AI **never** says "done" before you accept | It says "prepared for your review" |
| P9 | Garbage check | The raw text `%%PROPOSAL%%` or JSON must **never** appear in the chat |
| P10 | "change confidence to 8" → Accept | Confidence on the page becomes 8 (regression: this used to silently do nothing) |
| P11 | "set the deadline to 2026-09-01" → Accept, then **reload** | Deadline shows on the page **and survives reload** (regression: a date-only value used to be rejected by the `Instant` column and vanish on reload) |
| P12 | Ask for **two** changes at once ("rename to X and add a target Y") | **Two** separate proposal cards appear, each independently Accept/Dismiss-able |
| P13 | "add a target: finish portfolio **by 2026-10-15**" → Accept, then **reload** | The target **and its due date** persist (same date-normalization fix as P11; previously the whole target could fail to save) |
| P14 | "add a target 'Send 6 applications in May' and mark it **done**" → Accept | One target is created **and shown as done/achieved** (created, then auto-completed via its real id — you cannot complete a target you're creating in the same message) |
| P15 | "track 'Send 20 applications in June', I've sent **2**" → Accept | A **numeric** target appears at **2/20** (created at 0, progress set via the real id) |
| P16 | "make a checklist 'Application docs': CV (done), cover letter, portfolio (done)" → Accept | A **checklist** target with 3 items, 2 already checked (checklist items can be created already done) |

**P14–P16** verify the "create a target in its final state" flow: binary-done and numeric-progress are created then updated by the real id (B-chaining), while checklist item states are set directly at creation.

**P9 is the regression test** for the marker bug: proposals now come through native tool calls (an SSE `proposal` event), not parsed text, so no marker can leak. **P10–P13** are regressions for the confidence/deadline parsing bugs, the single-proposal-overwrite bug, and the date-only-vs-`Instant` persistence bug.

> **Dates:** the backend stores every deadline as an `Instant` (full timestamp). The AI and the card's date inputs produce date-only `YYYY-MM-DD`, so `applyProposal` runs them through `normalizeDeadline` (→ full ISO) before saving. If an accepted deadline disappears after reload, that normalization regressed — check `normalizeDeadline` in `AiPanel.tsx`.

### 2.3a Editing a proposal = instructing the AI

Every pending card has **Accept / Edit / Dismiss**. **Edit does NOT open a manual form** — it lets you tell the AI how to change the proposal, and the AI re-proposes. (This matches the user's intent: "Edit" = give the AI more guidance, not hand-edit.)

| # | Steps | Expected result |
|---|---|---|
| PE1 | On a proposal card click **Edit** | An instruction textarea appears ("Tell the AI how to change this…") with Send to AI / Cancel — no manual fields |
| PE2 | Type "in English" → **Send to AI** | The current card is marked **Dismissed** (superseded) and the AI replies with a **new** proposal applying the instruction |
| PE3 | Type "make it shorter" / "due next Friday" → Send | The AI re-proposes accordingly (a fresh card) |
| PE4 | Edit → **Cancel** | Back to Accept / Edit / Dismiss, unchanged |
| PE5 | Edit works for **any** kind (target, note, confidence, select_option, …) | Instruction box appears for all pending proposals |
| PE6 | Empty instruction | Send is disabled |

> **Language memory (regression):** after you instruct "in English" once, the AI should keep proposing goal data in English for the rest of the conversation (prompt: "once chosen, always use that language for every proposal"). If it reverts, check the LANGUAGE section of `CHAT_PROMPT`.

### 2.3b Proposal persistence (survives reload)

These verify the `ai_proposals` table is actually used (`create` + approve/reject + restore).

| # | Steps | Expected result |
|---|---|---|
| PP1 | Trigger a proposal, **do not** click Accept/Dismiss, **reload** the page, reopen the panel on the same goal | The pending card reappears ("suggestions still waiting for your review") |
| PP2 | After PP1, click **Accept** | Goal updates; on the next reload the card does **not** reappear (status is no longer PENDING) |
| PP3 | Trigger a proposal, **Dismiss** it, reload | Card does not reappear |
| PP4 | (curl) after triggering a proposal, `GET /api/ai/proposals/goal/{id}` | Returns the proposal with `status:"PENDING"` and a numeric `id` |
| PP5 | (curl) `POST /api/ai/proposals/{id}/approve` then GET again | The proposal is gone from the PENDING list |
| PP6 | Trigger a proposal in **global** chat (no goal selected) | Card still works in-session but is **not** persisted (global proposals have no goal) |

### 2.3c Chat transcript persistence

| # | Steps | Expected result |
|---|---|---|
| T1 | Have a chat on a goal, close the panel, reopen | The conversation is still there |
| T2 | Reload the page, reopen the panel on the same goal | Conversation restored |
| T3 | Switch to a **different** goal | A different (or empty) conversation — transcripts are per-goal |
| T4 | Open the All-Goals (global) chat | Its own separate transcript |
| T5 | Have a GROW session, end it, reopen the panel | GROW messages are **not** restored (sessions are ephemeral by design); any proposals made during GROW reappear via PP1's restore |
| T6 | (dev tools) inspect `localStorage` | Keys `spira:ai-chat:<goalId>` / `spira:ai-chat:global`; no API keys or secrets stored there |

### 2.3d Confidence history (AI changes are recorded)

The confidence history (the goal's **Confidence → Confidence history** panel) is now backed by the server (`confidence_history` table) and updated for **both** manual and AI changes — not the old in-memory demo mock.

| # | Steps | Expected result |
|---|---|---|
| CH1 | Move the confidence control manually | A new entry with the new value appears at the top of the history panel immediately |
| CH2 | Ask the AI "change confidence to 9" → **Accept** | The history panel shows a 9 entry (regression: AI changes used to bypass history entirely) |
| CH3 | Edit a confidence proposal to 4, Accept | The history records **4** (the edited value), not the AI's original |
| CH4 | After CH2/CH3, **reload** the page, open the history panel | The entries are still there (loaded from the server, not lost like the old mock) |
| CH5 | Accept a confidence proposal whose value equals the current confidence | **No** duplicate history entry is added (only real changes are recorded) |
| CH6 | (curl) `query { goal(id:N){ confidence confidenceHistory{ confidence at } } }` | Returns the persisted history newest-first |

> **Root cause fixed:** history used to be a component-local mock seeded from the current value and appended only by the manual control. It now reads `goal.confidenceHistory` (GraphQL) and the store prepends optimistically inside `updateGoal`, which both the manual control and `applyProposal` route through.

### 2.3e Editing existing items & changing state (AI)

The AI can now change existing items, not just create them. It references an item by the `id=…` shown in the goal context. Each is a proposal card (Accept / Edit / Dismiss, except pure state changes which are Accept / Dismiss).

| # | Steps | Expected result |
|---|---|---|
| EX1 | "rename the target 'streak' to '21-day streak'" → Accept | The existing target's title changes (no duplicate target is created) |
| EX2 | "mark the vocabulary target as done" → Accept | The binary target flips to done; goal progress recomputes |
| EX3 | "set vocabulary progress to 320" → Accept | The numeric target's current value becomes 320 |
| EX4 | "select the 'group class' option" → Accept | That option becomes the selected one |
| EX5 | "reword my obstacle about time to '…'" → Accept | The existing obstacle text updates (not a new one) |
| EX6 | "check off the second checklist item" → Accept | Only that checklist item toggles; others unchanged |
| EX7 | An edit proposal (e.g. EX1) → **Edit** → "use the formal name" → Send to AI | The AI re-proposes with that guidance (Edit instructs the AI — see PE-series — it is not a manual form) |
| EX8 | Every kind (incl. `complete_target` / `select_option`) | Shows **Accept / Edit / Dismiss** — Edit (instruct the AI) is available for all |
| EX9 | Reload after EX1–EX6 | Changes persist (real store → backend round-trip) |
| EX10 | (curl) trigger an edit; check the `proposal` event data | Contains the item `id` and the edit/state `kind` |
| EX11 | "add a sub-task 'buy materials' to the prep checklist" → Accept | A new item appears inside that checklist target (others kept); reload-safe |
| EX12 | "add sub-task 'submit form' due 2026-10-01 to the … checklist" → Accept, reload | New item **with its due date** persists (date normalized to an Instant) |
| EX13 | "set a due date of 2026-09-15 on the 'draft' checklist item" → Accept | That item gets a due date; the context then shows `· due …` for it |
| EX14 | "add a sub-task to <a binary/numeric target>" | AI can't (items live only on checklist targets) — it says so / proposes a checklist instead; if a card is forced, accepting shows "only checklist targets" and makes no change |

> The AI must use the `id` from the context. If it edits the wrong item or creates a duplicate instead of editing, check that `GoalContextBuilder` prints ids and that `proposalFromToolArgs` maps `id` → `itemId` (EX1).

### 2.3f Deletion guidance (AI explains, never deletes)

| # | Steps | Expected result |
|---|---|---|
| D1 | "delete this goal" | AI does **not** delete; it explains where to delete in the UI (goal card menu / Delete, with a confirm) |
| D2 | "remove the streak target" | AI points to the target row's trash icon (asks to confirm) — no proposal card, no claim of deletion |
| D3 | "clear the deadline" | AI explains the deadline picker's **Clear** action (it does not silently wipe it) |
| D4 | Confirm no "delete" proposal card ever appears | There is no delete tool; deletion is always user-driven in the UI |

### 2.3g Reading resources (on demand via `read_resource`)

The context lists resources by id/type/title only; the AI loads content **on demand** with the `read_resource` tool (so tokens aren't spent re-sending files every message). Text is extracted server-side (PDFBox for PDFs) — provider-agnostic.

| # | Steps | Expected result |
|---|---|---|
| R1 | Add a **note**, then ask "summarise my notes" | AI calls `read_resource`, then summarises the actual note body |
| R2 | Upload a **text-based PDF CV**, ask "what does my CV say about experience?" | AI reads the CV and answers from its real content |
| R3 | "rewrite my CV to target a QA automation role" | AI reads the CV, drafts a new version, and proposes it as a **new note** (Accept saves it); the original PDF is untouched |
| R4 | Upload a **scanned/image-only PDF**, ask about it | `read_resource` returns "(no extractable text)"; AI says it can't read it and asks you to paste the text — does **not** invent content |
| R5 | Upload an **image** resource, ask about it | AI says images aren't readable as text |
| R6 | Ask something **unrelated** to your files | AI does **not** call `read_resource` — content isn't pulled (or token-spent) when irrelevant |
| R7 | A very long PDF | Result is bounded (≤15 pages / 12 000 chars; note bodies ≤8 000) — no runaway prompt |
| R8 | (curl) chat with a goal that has resources; inspect the system prompt | The `**Resources**` block lists id/type/title only — **no** embedded content |
| R9 | (curl) watch the raw stream for R2 | tokens → a `read_resource` tool call runs server-side → more tokens, one `done`-terminated stream |
| R10 | Ask to read a resource id from **another** goal | `read_resource` returns "Resource not found" (scoped to the current goal) |

> If the AI invents CV content instead of reading it: confirm the `read_resource` tool is offered (goal-scoped chat), the resource `mime` is `application/pdf`, and PDFBox is on the classpath. If it reads on *every* message, the content should be coming from the tool — not from `GoalContextBuilder` (which must list titles only).

### 2.3h Note formatting & export

| # | Steps | Expected result |
|---|---|---|
| NE1 | Paste formatted text (from Word/Docs) into a note | Headings, bold/italic, lists, links survive (tables/images don't — those TipTap extensions aren't installed) |
| NE2 | **Insert a link**: select text → link button → enter URL → Save | The selected text becomes a working link |
| NE3 | Use **Font**, **Size**, **Spacing** (line height), text **color**, **highlight color** | The selection changes accordingly; "Font"/"Size"/"Spacing" default option clears that attribute |
| NE4 | There is only **one** highlight control | A single highlight **colour picker** (the old single-colour toggle button is gone) |
| NE5 | **Copy formatting** on styled text, select other text, **Apply** (paintbrush) | The target text takes on the copied formatting; **Clear formatting** (eraser) strips marks + nodes |
| NE6 | Download → **Plain text (.txt)** | `.txt` of the body text (no title line); task items show ☑/☐ |
| NE7 | Download → **Word (.doc)** | Opens in Word/Docs **with visible bullets/numbers** (lists are flattened to • / n. markers); no title inserted |
| NE8 | Download → **PDF (Save as PDF)** | New print window; selectable text, equal L/R margins, top margin on every page, no blank trailing page, **compact (a 2-page CV ≈ 2 pages, not 4)**; no title inserted |
| NE9 | Export a note the AI rewrote (the CV flow) | The exported file contains the rewritten content; the original upload is untouched |

### 2.4 Web search (Tavily)

Requires a Tavily key connected in the key sheet ("Web search" section). Only works in regular chat, not GROW.

| # | Steps | Expected result |
|---|---|---|
| W1 | Connect a Tavily key | "Web search" shows Connected |
| W2 | Ask something current ("what are typical junior game-dev salaries in Malmö right now?") | AI runs a search and answers with a summary + sources; answer reflects fetched content |
| W3 | Ask something it knows without search | AI answers directly without searching (no forced tool use) |
| W4 | Disconnect / no Tavily key, ask a current-info question | AI answers from its own knowledge and says it couldn't search — **never fabricates sources** |
| W5 | In a GROW session, ask to search | AI declines to search and offers to note it as a next action (search tool not offered in GROW) |
| W6 | (curl) watch the raw stream during W2 | tokens stream, search runs server-side, then more tokens — all in one `done`-terminated stream |

### 2.5 GROW session

| # | Steps | Expected result |
|---|---|---|
| G1 | Start a GROW session (pick 15 min) | AI opens with an inviting question, not a form |
| G2 | Have a short conversation | AI asks one focused question at a time (coaching tone) |
| G3 | Mention a real blocker | AI offers to capture it and shows a proposal card (proposals work in GROW too) |
| G4 | Accept a proposal during the session | Goal updates immediately |
| G5 | Let the timer pass 80% | "Closing" banner appears; tone shifts toward wrap-up |
| G6 | End early | Confirm dialog → wrap-up → "Save memory?" card |

### 2.6 Safety

| # | Steps | Expected result |
|---|---|---|
| S1 | Send a message containing a blocked phrase (e.g. self-harm) | AI returns the safe redirect message; **no** LLM call is made |
| S2 | Normal message | Passes through untouched |

### 2.7 UI details

| # | Steps | Expected result |
|---|---|---|
| U1 | Select assistant text in the panel | Highlight is a readable translucent white (not white-on-white) |
| U2 | Select text inside a white card / your own bubble | Highlight is teal-tinted, text stays dark |
| U3 | Open the panel on the All Goals page | No GROW button; global suggestions shown |
| U4 | Drag the panel wider on a goal page | Main content reflows to its mobile layout |

---

## 3. Backend endpoint testing (without the UI)

You can exercise the backend directly. Useful to isolate "is it the backend or the frontend?".

```bash
# Save a key
curl -X POST localhost:8080/api/ai/keys \
  -H "Content-Type: application/json" \
  -d '{"provider":"MISTRAL","apiKey":"<your-key>","model":"mistral-large-latest"}'

# List configured providers (masked)
curl localhost:8080/api/ai/keys

# Live model list from the provider
curl localhost:8080/api/ai/keys/MISTRAL/models

# Change the model
curl -X PATCH localhost:8080/api/ai/keys/MISTRAL \
  -H "Content-Type: application/json" -d '{"model":"mistral-small-latest"}'

# Stream a chat (you'll see raw SSE: event: token / event: proposal / event: done)
curl -N -X POST localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"goalId":1,"message":"rename this goal to Test","provider":"MISTRAL","sessionType":"chat","history":[]}'
```

What to look for in the raw stream:
- `event: token` lines whose `data:` is a **JSON string** (e.g. `data: "Hello"`, `data: "\n## Heading"`). Newlines inside a token are escaped — this is the fix for the truncation bug (C6). If you see a bare multi-line `data:` block, the backend is not JSON-encoding tokens.
- For C2/P9: a rename request produces an `event: proposal` line whose `data:` is valid JSON. When the request has a `goalId`, that JSON now includes a numeric `"proposalId"` (the persisted row) — confirming `AiProposalService.create` ran (PP4).
- `event: done` terminates the stream.

---

## 4. Automated tests

### 4.1 Orchestration (highest value, easiest)

Test `AiChatService` and friends with a **mock `LlmProvider`** so no network is involved. `LlmProviderFactory` can be stubbed (or inject a fake) to return a provider whose `streamChat` immediately calls the callbacks you want.

| Test | Setup | Assert |
|---|---|---|
| Safety blocks | message with blocked phrase | provider is **never** called; emitter gets the safe message |
| Missing key → 422 | no key stored for provider | `ResponseStatusException(422)` thrown |
| Prompt selection | `sessionType="grow"` vs `"chat"` | correct base prompt used (extract `buildSystemPrompt` to verify) |
| Tool wiring | fake provider fires `onToolCall` | a `proposal` SSE event is emitted with the args JSON |
| Proposal persisted | fake provider fires `onToolCall` with a `goalId` | a row is saved in `ai_proposals` (PENDING) and the event data carries its `proposalId` |
| Proposal not persisted (global) | `onToolCall` with `goalId=null` | no row saved; event still emitted |
| Token encoding | provider emits a token with a newline | the SSE `token` data is a JSON string (no raw newline) |
| Goal context | seeded goal | `GoalContextBuilder.build(id)` includes title, obstacles, targets |
| Context item ids | seeded goal with targets/options/reality/resources | each item line includes its `id=…` (so the model can reference it for edits) |
| Resources in context | goal with resources | context lists id/type/title only — **no** embedded content (read on demand) |
| `read_resource` tool | fake provider fires `read_resource` for a note/PDF id | the loop feeds back the resource text and continues; `ResourceReadService.read` returns the body/extracted text, "(no extractable text)" for a scanned PDF, and "Resource not found" for a wrong/other-goal id |
| All tool_use answered | turn with `read_resource` + `propose_goal_change` together | every tool call gets a `tool_result` on the next turn (proposal gets a synthetic ack) — no provider 400 |
| `ResourceTextExtractor` | sample PDF / garbage | returns text / `""` (never throws) |

`GoalContextBuilder` is a great pure unit test: seed a goal in the test DB, call `build(id)`, assert the produced text. (Remember it needs a transaction — it's annotated `@Transactional(readOnly=true)`.)

### 4.2 Key service & encryption

| Test | Assert |
|---|---|
| `EncryptionService` round-trip | `decrypt(encrypt(x)) == x` |
| Encryption is non-deterministic | `encrypt(x) != encrypt(x)` (random IV) |
| `saveKey` then `listKeys` | hint is masked, raw key never present |
| `updateModel` | model changes, key unchanged |

### 4.3 Provider stream parsing (unit, no network)

Refactor each provider so the stream-parsing method accepts a `Stream<String>` of SSE lines (it already does internally). Then feed canned lines and assert callbacks:

| Input lines | Assert |
|---|---|
| Anthropic `text_delta` chunks | `onToken` called per chunk in order |
| Anthropic `tool_use` + `input_json_delta` chunks | `onToolCall` receives the reassembled JSON |
| Mistral `delta.content` chunks | `onToken` called per chunk |
| Mistral `delta.tool_calls[].function.arguments` chunks | `onToolCall` receives the reassembled JSON |
| HTTP non-200 | `onError` called, `onComplete` not |

### 4.4 Frontend (optional)

- `proposalFromToolArgs()` is a pure function — unit test each `kind` maps to the right `title`/`field`/`body`, that `confidence`/`deadline` set `rawValue`, `proposalId` → `serverId`, and that edit/state kinds capture `id` → `itemId` and `done`.
- `applyProposal` routing — assert each edit/state kind calls the right store action (`updateTarget`/`updateOption`/`updateReality`/`updateResource`/`selectOption`) with the `itemId`; that `checklist_item` patches only the matching item (text from `rawValue`, plus `done`/normalized `deadline`); and that `add_checklist_item` appends a new item to the named checklist target (and refuses non-checklist targets).
- Proposal "Edit" → instruction: clicking Edit shows an instruction box (no manual fields); Send dismisses the card and triggers a chat turn ("Revise your proposed … : <instruction>"). Assert the message is composed and the old card is marked rejected.
- `normalizeDeadline()` is pure — assert `YYYY-MM-DD` → full ISO instant, an already-full ISO passes through unchanged, and empty/garbage → `undefined` (never an invalid string that the `Instant` column would reject).
- Confidence-history mapping: `toGoal()` maps GraphQL `confidenceHistory { confidence at }` → `{ value, at }[]`; `updateGoal` prepends an entry only when `confidence` actually changes (no duplicate when unchanged).
- The SSE parser in `ai-api.ts` can be tested by feeding a fake `ReadableStream` and asserting `onToken` / `onProposal` / `onDone` fire correctly. **Cover these cases specifically:**
  - a JSON-encoded token containing newlines (`data: "a\nb"`) → `onToken("a\nb")` (regression for truncation);
  - an event split across two network chunks (the buffer must reassemble it);
  - `event` and `data` arriving as separate lines terminated by a blank line.
- `loadTranscript` / `saveTranscript` round-trip; the streaming placeholder is excluded from storage; per-scope keys don't collide.

---

## 5. What to test when adding things later

| If you add… | Add this test |
|---|---|
| A new provider | Stream-parse unit tests (text + tool call) + a manual smoke test with a real key |
| A new proposal `kind` | `proposalFromToolArgs` unit test + a manual P-series scenario |
| Web search tool | Mock the search API; assert results are summarised, never fabricated when the API fails |
| Mini-app tools | Schema validation tests; renderer tests per UI primitive; approval-before-create flow |

---

## 6. Common failure signatures

| Symptom | Likely cause | Where to look |
|---|---|---|
| No response at all | Backend 500, or no key | Backend console; check for `LazyInitializationException` in `GoalContextBuilder` (needs `@Transactional`) |
| **Message cut off at first heading/list** | Token newlines breaking SSE framing | `sendToken` must JSON-encode (backend); `ai-api.ts` must JSON-decode + accumulate `data:` lines (C6) |
| 405 on PATCH/model change | CORS method not allowed | `CorsConfig` must allow `PATCH` |
| Raw `%%PROPOSAL%%`/JSON in chat | Model didn't use tool calling | Switch to a flagship model; confirm tools are sent in the request |
| Proposal card never appears | `onToolCall` not firing | Provider stream parsing; check the model actually supports tools |
| **Accept does nothing for confidence/deadline** | Parsing the display title instead of the raw value | `applyProposal` must read `rawValue`; `proposalFromToolArgs` must set it |
| **Only one card when two were requested** | Proposal overwrite | `sendChat`/`sendGrow` must collect proposals into an array, not a single var |
| **Pending card lost after reload** | Persistence not wired | `AiChatService.sendProposal` must call `create`; panel must `GET /proposals/goal/{id}` on open (PP1) |
| **Edit edits manually instead of asking the AI** | Old behaviour | Edit must open an instruction box that sends to the AI (PE1–PE2), not a manual form |
| **AI reverts goal-data language after you asked for English** | Prompt not persisting the choice | `CHAT_PROMPT` LANGUAGE section must say "once chosen, always use that language" |
| **Accepted deadline / target due date vanishes on reload** | Date-only string sent to an `Instant` column | `applyProposal` must run dates through `normalizeDeadline` (P11/P13) |
| **Confidence change missing from history** | History not server-backed, or change bypassed `updateGoal` | `api.ts` must query `confidenceHistory`; the optimistic prepend lives in `updateGoal` (CH2) |
| **AI creates a duplicate instead of editing** | Model didn't get/use the item id | `GoalContextBuilder` must print `id=…`; `proposalFromToolArgs` maps `id`→`itemId` (EX1) |
| **AI claims it deleted something** | Prompt regression | No delete tool exists; `CHAT_PROMPT`/`GROW_PROMPT` must say to guide in the UI (D1–D4) |
| **AI can't read an uploaded CV / invents content** | `read_resource` not offered or extraction failed | tool offered only for goal-scoped chat; resource `mime` `application/pdf`; PDFBox on classpath (R2/R4) |
| **AI reads files on every message** | content leaking into context | `GoalContextBuilder` must list resources by title only; content comes only from `read_resource` (R6/R8) |
| **PDF upload rejected on save** | size validation | enforced in `ResourceService` (5 MB on decoded bytes); the entity has **no** `@Size` on `dataUrl` |
| AI claims it changed data | Prompt regression | `CHAT_PROMPT`/`GROW_PROMPT` must say "prepared for your review", not "done" |
