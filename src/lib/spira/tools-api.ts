import { getCsrfToken } from "./auth";

// ── Personal Tools (AI mini-apps) API client ────────────────────────────────
// Talks to the backend ToolController (/api/tools). A tool is a SCHEMA
// (definition) plus RECORDS (the user's entered rows). One renderer draws any
// tool from its schema; see docs/ai-mini-apps-plan.md.

const BASE = "/api/tools";

function mutationHeaders(): Record<string, string> {
  return { "X-XSRF-TOKEN": getCsrfToken(), "Content-Type": "application/json" };
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

export type ToolColumn = {
  key: string;
  label?: string;
  primitive: ToolPrimitive;
  options?: string[];
};

export type ToolSchema = {
  layout: "table" | "fields";
  columns: ToolColumn[];
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
  if (!res.ok) throw new Error(`Failed to load tools: ${res.status}`);
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
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(body || `Failed to create tool: ${res.status}`);
  }
  return res.json();
}

export async function deleteTool(id: number): Promise<void> {
  const res = await fetch(`${BASE}/${id}`, {
    method: "DELETE",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": getCsrfToken() },
  });
  if (!res.ok) throw new Error(`Failed to delete tool: ${res.status}`);
}

export async function listRecords(toolId: number): Promise<ToolRecord[]> {
  const res = await fetch(`${BASE}/${toolId}/records`, {
    credentials: "include",
  });
  if (!res.ok) throw new Error(`Failed to load records: ${res.status}`);
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
  if (!res.ok) throw new Error(`Failed to add record: ${res.status}`);
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
  if (!res.ok) throw new Error(`Failed to update record: ${res.status}`);
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
  if (!res.ok) throw new Error(`Failed to delete record: ${res.status}`);
}
