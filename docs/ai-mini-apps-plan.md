# Personal Tools (AI Mini-Apps) — Design Plan

**Status: design only — not implemented.** This document specifies how the "Personal Tools" feature from [`ai-configuration.md`](./ai-configuration.md) should be built. It is a large feature and should be implemented as its own focused effort.

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
| `date` | date / reminder | ISO date string |
| `checkbox` | single boolean | `boolean` |
| `checklist` | list of toggle items | `{ label, done }[]` |
| `select` | choice from fixed options | `string` |
| `table` | rows with user-defined columns | `Record<col, cell>[]` |
| `progress` | progress display (derived or entered) | `number` (0–100) |
| `chart` | line/bar from a numeric column | (derived from records) |

Charts and progress are **read-only / derived** — they visualise data entered through other primitives, they are not inputs.

---

## 3. Data model (backend)

```sql
CREATE TABLE tool_definitions (
    id          BIGSERIAL PRIMARY KEY,
    app_user_id BIGINT NOT NULL,
    goal_id     BIGINT REFERENCES goal(id) ON DELETE CASCADE,  -- null = global
    name        TEXT NOT NULL,
    schema      JSONB NOT NULL,     -- primitives, layout, column defs
    placement   TEXT NOT NULL,      -- 'goal' | 'all_goals' | 'tools'  (may be multiple)
    created_by  TEXT NOT NULL,      -- 'ai' | 'user'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tool_records (
    id            BIGSERIAL PRIMARY KEY,
    tool_def_id   BIGINT NOT NULL REFERENCES tool_definitions(id) ON DELETE CASCADE,
    data          JSONB NOT NULL,   -- one row/entry, shape matches the schema
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

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

- [ ] Can the AI edit an existing tool's schema, or only create new ones? (Schema migration of existing records is tricky — start with create-only.)
- [ ] Per-record reminders (the `date` primitive as a reminder) — do they integrate with the existing calendar?
- [ ] Limits: max tools per user, max columns, max records — needed to bound storage and prompt size.
