import { getCsrfToken } from "./auth";

// ── Personal Tools (AI mini-apps) API client ────────────────────────────────
// Talks to the backend ToolController (/api/tools). A tool is a SCHEMA
// (definition) plus RECORDS (the user's entered rows). One renderer draws any
// tool from its schema; see docs/ai-mini-apps-plan.md.

const BASE = "/api/tools";

function mutationHeaders(): Record<string, string> {
  return { "X-XSRF-TOKEN": getCsrfToken(), "Content-Type": "application/json" };
}

/**
 * Turns a failed response into a user-facing Error. Prefers the server's
 * human-readable message (RFC-7807 ProblemDetail `detail` — e.g. "This tool has
 * reached its record limit (500)."), and NEVER surfaces a bare status code.
 */
async function failure(res: Response, fallback: string): Promise<Error> {
  try {
    const body = await res.json();
    const detail = typeof body?.detail === "string" ? body.detail.trim() : "";
    if (detail) return new Error(detail);
  } catch {
    /* non-JSON or empty body — use the friendly fallback below */
  }
  return new Error(fallback);
}

export type ToolPrimitive =
  | "number"
  | "text"
  | "textarea"
  | "date"
  | "time"
  | "checkbox"
  | "checklist"
  | "select"
  | "tags"
  | "rating"
  | "url"
  | "table"
  | "progress"
  | "chart";

/** Safe, closed colour palette for select badges (maps to fixed CSS classes). */
export type ToolColor =
  | "gray"
  | "red"
  | "amber"
  | "green"
  | "blue"
  | "purple"
  | "pink"
  | "teal";

export type ToolColumn = {
  key: string;
  label?: string;
  primitive: ToolPrimitive;
  options?: string[];
  /** Display-only: cell text alignment (table). */
  align?: "left" | "center" | "right";
  /** Display-only: per-option colour for a `select` (option value → colour). */
  colors?: Record<string, ToolColor>;
};

export type ToolSchema = {
  layout: "table" | "fields";
  columns: ToolColumn[];
  /** Display-only: default row ordering for a `table`. */
  sort?: { key: string; dir: "asc" | "desc" };
};

export type Tool = {
  id: number;
  goalId: number | null;
  name: string;
  schemaJson: string;
  placement: "goal" | "all_goals" | "tools";
  createdBy: "ai" | "user";
  createdAt: string;
};

export type ToolRecord = {
  id: number;
  toolDefId: number;
  dataJson: string;
  createdAt: string;
  updatedAt: string;
};

/** Parse a tool's schema JSON, returning null if malformed (renderer guards). */
export function parseSchema(schemaJson: string): ToolSchema | null {
  try {
    const s = JSON.parse(schemaJson) as ToolSchema;
    if (!s || !Array.isArray(s.columns)) return null;
    return s;
  } catch {
    return null;
  }
}

export async function listTools(goalId?: string | number): Promise<Tool[]> {
  const q = goalId != null ? `?goalId=${goalId}` : "";
  const res = await fetch(`${BASE}${q}`, { credentials: "include" });
  if (!res.ok)
    throw await failure(res, "Couldn't load your tools. Please try again.");
  return res.json();
}

export async function createTool(input: {
  name: string;
  schemaJson: string;
  placement?: string;
  goalId?: number | null;
  createdBy?: "ai" | "user";
}): Promise<Tool> {
  const res = await fetch(BASE, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders(),
    body: JSON.stringify({
      name: input.name,
      schemaJson: input.schemaJson,
      placement: input.placement ?? "tools",
      goalId: input.goalId ?? null,
      createdBy: input.createdBy ?? "user",
    }),
  });
  if (!res.ok)
    throw await failure(res, "Couldn't create the tool. Please try again.");
  return res.json();
}

/** Change a tool's structure/appearance (name and/or schema). Returns the
 *  updated tool. Used when the AI's edit_tool proposal is approved. */
export async function updateTool(
  id: number,
  patch: { name?: string; schemaJson?: string },
): Promise<Tool> {
  return patchTool(id, patch);
}

async function patchTool(
  id: number,
  body: Record<string, unknown>,
): Promise<Tool> {
  const res = await fetch(`${BASE}/${id}`, {
    method: "PATCH",
    credentials: "include",
    headers: mutationHeaders(),
    body: JSON.stringify(body),
  });
  if (!res.ok)
    throw await failure(res, "Couldn't update the tool. Please try again.");
  return res.json();
}

export async function deleteTool(id: number): Promise<void> {
  const res = await fetch(`${BASE}/${id}`, {
    method: "DELETE",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() },
  });
  if (!res.ok)
    throw await failure(res, "Couldn't delete the tool. Please try again.");
}

export async function listRecords(toolId: number): Promise<ToolRecord[]> {
  const res = await fetch(`${BASE}/${toolId}/records`, {
    credentials: "include",
  });
  if (!res.ok)
    throw await failure(
      res,
      "Couldn't load this tool's entries. Please try again.",
    );
  return res.json();
}

export async function addRecord(
  toolId: number,
  data: unknown,
): Promise<ToolRecord> {
  const res = await fetch(`${BASE}/${toolId}/records`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders(),
    body: JSON.stringify({ dataJson: JSON.stringify(data) }),
  });
  if (!res.ok)
    throw await failure(res, "Couldn't save this entry. Please try again.");
  return res.json();
}

export async function updateRecord(
  toolId: number,
  recordId: number,
  data: unknown,
): Promise<ToolRecord> {
  const res = await fetch(`${BASE}/${toolId}/records/${recordId}`, {
    method: "PATCH",
    credentials: "include",
    headers: mutationHeaders(),
    body: JSON.stringify({ dataJson: JSON.stringify(data) }),
  });
  if (!res.ok)
    throw await failure(res, "Couldn't update this entry. Please try again.");
  return res.json();
}

export async function deleteRecord(
  toolId: number,
  recordId: number,
): Promise<void> {
  const res = await fetch(`${BASE}/${toolId}/records/${recordId}`, {
    method: "DELETE",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() },
  });
  if (!res.ok)
    throw await failure(res, "Couldn't delete this entry. Please try again.");
}
