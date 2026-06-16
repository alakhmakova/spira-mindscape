# Personal Tools (AI Mini-Apps) — Design Plan

**Status: MVP implemented (2026-06-13).** Steps 1–5 of the build order are done:
the AI can propose a tool, the user previews and approves it, and it renders and
stores records. Deferred (step 6): `chart`/`progress` primitives and the
`all_goals` placement. This document is the source of truth; the
"Implementation notes" below record where the code lives and where the design
was adjusted during the build.

This document specifies how the "Personal Tools" feature from
[`ai-configuration.md`](./ai-configuration.md) should be built.

This is **different** from the `propose_goal_change` LLM tool (see [`ai-integration.md`](./ai-integration.md) §4a). "Mini-apps" are small, user-facing widgets — a period tracker, a job-application tracker, a habit log — that the AI assembles from a fixed set of approved UI primitives. The AI never writes arbitrary code.

---

## 1. Concept

A tool is a small data app defined by a **schema** (which primitives, what layout, what data it stores) and filled with **records** (the user's entered data). One generic renderer draws every tool from its schema — there is no custom code per tool.

Two scopes:
- **Goal-scoped** — belongs to a specific goal (e.g. a weight log on a fitness goal).
- **Global** — belongs to the user, not a goal (e.g. a period tracker).

The AI proposes a tool; the user approves; the tool is created and rendered where the user chose.

---

## 2. Approved UI primitives

The AI may only compose these. Each maps to a renderer component and a data shape.

| Primitive | Purpose | Stored value |
|---|---|---|
| `number` | numeric input | `number` |
| `text` | short text / note | `string` |
| `textarea` | long / multi-line text | `string` |
| `date` | date / reminder | ISO date string (`YYYY-MM-DD`) |
| `time` | time of day | `string` (`HH:MM`, 24h) |
| `checkbox` | single boolean | `boolean` |
| `checklist` | list of toggle items | `{ label, done }[]` |
| `select` | choice from fixed options | `string` |
| `tags` | free-text labels | `string[]` |
| `rating` | 0–5 star rating | `number` (0–5) |
| `url` | a link | `string` |
| `table` | rows with user-defined columns | `Record<col, cell>[]` |
| `progress` | progress display (derived or entered) | `number` (0–100) |
| `chart` | line/bar from a numeric column | (derived from records) |

Charts and progress are **read-only / derived** — they visualise data entered through other primitives, they are not inputs.

This is a **curated, closed set** — the security boundary. The AI cannot invent
primitives; the backend `ToolSchemaValidator` rejects anything outside this list
and `ToolRecordValidator` type-checks every stored value against it. When the AI
proposes a schema using something not here, the rejection is recorded by
`ToolDemandLogger` (privacy-safe: primitive name only, no user content) so the
catalog can be grown deliberately rather than opened to arbitrary UI.

---

## 3. Data model (backend)

> **Build note — TEXT, not JSONB.** The JSON columns are stored as `TEXT`, not
> `JSONB`. Reason: the backend test suite runs on H2 (Flyway off, schema from
> JPA entities) which has no `JSONB` type — a `JSONB` column would make
> `tool_definitions`/`tool_records` untestable as JPA entities (the same trap
> hit by `book_chunk`). We never query *inside* the JSON (always fetch a whole
> tool/record by id), so `TEXT` loses nothing and keeps these as ordinary,
> fully-testable JPA entities, like `ai_proposals.payload`. Validation happens
> in Java (`ToolSchemaValidator`), not in the DB.

```sql
CREATE TABLE tool_definitions (
    id           BIGSERIAL PRIMARY KEY,
    app_user_id  BIGINT NOT NULL,
    goal_id      BIGINT REFERENCES goal(id) ON DELETE CASCADE,  -- null = global
    name         VARCHAR(120) NOT NULL,
    schema_json  TEXT NOT NULL,      -- primitives, layout, column defs (JSON)
    placement    VARCHAR(16) NOT NULL,  -- 'goal' | 'all_goals' | 'tools'
    created_by   VARCHAR(8) NOT NULL,   -- 'ai' | 'user'
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tool_records (
    id            BIGSERIAL PRIMARY KEY,
    tool_def_id   BIGINT NOT NULL REFERENCES tool_definitions(id) ON DELETE CASCADE,
    data_json     TEXT NOT NULL,     -- one row/entry, shape matches the schema (JSON)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Limits (enforced by `ToolSchemaValidator`, bounding storage and prompt size):
max **20 tools/user**, **12 columns/fields** per tool, **schema 8 KB**,
**record 16 KB**, **500 records/tool**, name ≤ 120 chars.

`schema` example (job-application tracker):

```json
{
  "layout": "table",
  "columns": [
    { "key": "company",  "label": "Company",  "primitive": "text" },
    { "key": "role",     "label": "Role",     "primitive": "text" },
    { "key": "applied",  "label": "Applied",  "primitive": "date" },
    { "key": "status",   "label": "Status",   "primitive": "select",
      "options": ["applied", "interview", "offer", "rejected"] }
  ]
}
```

---

## 4. The `propose_tool` LLM tool

A new tool alongside `propose_goal_change`. The model proposes a tool **schema**; nothing is created until the user approves (same approval principle as goal changes).

```
propose_tool({
  "name": "Job Applications",
  "scope": "global",              // or "goal"
  "placement": "tools",            // 'goal' | 'all_goals' | 'tools'
  "schema": { ...as above... },
  "reasoning": "You're tracking several applications; a table keeps them in one place."
})
```

Flow (mirrors the proposal flow in `ai-integration.md`):
1. Backend emits a `tool_proposal` SSE event with the schema.
2. Frontend renders a **preview** of the tool (rendered read-only from the schema) inside a proposal card.
3. On Accept → `POST /api/tools` persists the `tool_definition`; the tool appears at its placement.

Validation: the backend must validate the schema against the allowed primitives before storing (reject unknown primitives, enforce size limits).

---

## 5. The generic renderer (frontend)

One component, `<ToolRenderer definition={...} records={...} />`, switches on `schema.layout` / per-field `primitive` and renders inputs. Editing a value calls `POST/PATCH /api/tools/{id}/records`. The same renderer is used everywhere a tool appears.

Placement wiring:
- `goal` → rendered on the goal page (goal-scoped tools only).
- `all_goals` → pinned widget on the All Goals page.
- `tools` → a new dedicated `/tools` route listing all tools.

A tool may have more than one placement.

---

## 6. API surface (proposed)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/tools` | Create a tool definition (from an approved proposal) |
| `GET` | `/api/tools?placement=&goalId=` | List tools for a location |
| `PATCH` | `/api/tools/{id}` | Update name / placement |
| `DELETE`| `/api/tools/{id}` | Delete a tool |
| `GET` | `/api/tools/{id}/records` | List records |
| `POST` | `/api/tools/{id}/records` | Add a record |
| `PATCH` | `/api/tools/{id}/records/{recordId}` | Edit a record |
| `DELETE`| `/api/tools/{id}/records/{recordId}` | Delete a record |

---

## 7. Build order (suggested)

1. DB migration (`tool_definitions`, `tool_records`).
2. Backend CRUD + schema validation (reject non-approved primitives).
3. `ToolRenderer` supporting `text`, `number`, `checkbox`, `checklist`, `table`, `select` (defer `chart`/`progress`).
4. `/tools` page + goal-page placement.
5. `propose_tool` LLM tool + `tool_proposal` SSE event + preview card.
6. `all_goals` placement + `chart`/`progress` primitives.

A minimal MVP is steps 1–4 with a manually-created tool, before wiring the AI in step 5.

---

## 8. Open questions

- [x] Can the AI edit an existing tool's schema, or only create new ones?
  → **Create-only** for now (record-migration of a changed schema is out of
  scope). The user can delete a tool and ask for a new one.
- [ ] Per-record reminders (the `date` primitive as a reminder) — calendar
  integration deferred; `date` is stored/displayed but not yet surfaced on the
  calendar.
- [x] Limits → chosen and enforced (see §3): 20 tools/user, 12 fields, 8 KB
  schema, 16 KB record, 500 records/tool.

---

## 9. Implementation notes (2026-06-13)

- **Backend** (`backend/.../tools/`): `ToolDefinition` + `ToolRecord` JPA
  entities (TEXT json), Spring Data repos, `ToolSchemaValidator` (approved
  primitives + limits), `ToolService` (per-user ownership via
  `CurrentUserProvider`, schema validation on create, limit enforcement),
  `ToolController` (the REST surface in §6). Migration `V15__personal_tools.sql`.
- **AI** (`ai/chat/AiChatService.java`): `propose_tool` tool spec; the model's
  schema is validated server-side and surfaced as a `tool_proposal` SSE event;
  rejected if it uses non-approved primitives or exceeds limits.
- **Frontend**: `ToolRenderer` (text/number/date/checkbox/checklist/select/
  table), `src/lib/spira/tools-api.ts`, the `/tools` route, goal-page placement,
  and the AI proposal **preview + Accept** card in `AiPanel`.
- **Deferred (step 6):** `chart`/`progress` primitives and the `all_goals`
  pinned-widget placement.
