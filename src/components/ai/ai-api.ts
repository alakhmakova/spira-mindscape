import { getCsrfToken } from "../../lib/spira/auth";

const AI_BASE = "/api/ai";

/**
 * Headers for a mutating request (POST/PATCH/DELETE). The backend enforces CSRF
 * via the double-submit cookie pattern: echo the readable XSRF-TOKEN cookie in
 * the X-XSRF-TOKEN header, or Spring Security rejects the request with 403.
 */
function mutationHeaders(extra?: Record<string, string>): Record<string, string> {
  return { "X-XSRF-TOKEN": getCsrfToken(), ...extra };
}

export type HistoryEntry = { role: "user" | "assistant"; content: string };

export type StreamChatParams = {
  goalId?: string;
  message: string;
  history: HistoryEntry[];
  provider?: string;
  sessionType?: "chat" | "grow";
  /** GROW only: session length the user picked, in minutes. */
  sessionTotalMinutes?: number;
  /** GROW only: seconds left on the timer; <= 0 tells the coach to close now. */
  sessionRemainingSeconds?: number;
  onToken: (token: string) => void;
  onProposal?: (argsJson: string) => void;
  /** Progress lines (e.g. one-time coaching-library indexing in GROW).
   *  Ephemeral UI only — never part of the transcript or history. */
  onStatus?: (msg: string) => void;
  onDone: () => void;
  onError: (msg: string) => void;
};

export async function streamChat(params: StreamChatParams): Promise<void> {
  const { goalId, message, history, provider = "ANTHROPIC", sessionType = "chat", sessionTotalMinutes, sessionRemainingSeconds, onToken, onProposal, onStatus, onDone, onError } = params;

  let response: Response;
  try {
    response = await fetch(`${AI_BASE}/chat`, {
      method: "POST",
      credentials: "include",
      headers: mutationHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        goalId: goalId ? parseInt(goalId, 10) : null,
        message,
        history,
        provider,
        sessionType,
        sessionTotalMinutes: sessionTotalMinutes ?? null,
        sessionRemainingSeconds: sessionRemainingSeconds ?? null,
      }),
    });
  } catch {
    onError("NETWORK");
    return;
  }

  if (!response.ok) {
    if (response.status === 422) {
      onError("NO_KEY");
    } else {
      onError(`Server error: ${response.status}`);
    }
    return;
  }

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  // SSE event being assembled. Per the spec, an event may span several
  // `data:` lines (joined with "\n") and is dispatched on a blank line.
  let eventName = "";
  let dataLines: string[] = [];
  let finished = false;

  // Returns true if the stream should stop (done/error dispatched).
  const dispatch = (): boolean => {
    if (!eventName && dataLines.length === 0) return false;
    const data = dataLines.join("\n");
    dataLines = [];
    const name = eventName;
    eventName = "";

    switch (name) {
      case "token": {
        // Tokens are JSON-encoded by the backend so they survive newlines.
        let text = data;
        try { text = JSON.parse(data); } catch { /* fall back to raw */ }
        onToken(text);
        return false;
      }
      case "proposal":
        onProposal?.(data.trim());
        return false;
      case "status":
        onStatus?.(data.trim());
        return false;
      case "done":
        onDone();
        return true;
      case "error":
        onError(data.trim() || "AI service error");
        return true;
      default:
        return false;
    }
  };

  try {
    while (!finished) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      let nl: number;
      while ((nl = buffer.indexOf("\n")) >= 0) {
        let line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        if (line.endsWith("\r")) line = line.slice(0, -1);

        if (line === "") {
          if (dispatch()) { finished = true; break; }
        } else if (line.startsWith("event:")) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataLines.push(line.slice(5)); // keep value verbatim; JSON handles spaces
        }
        // lines starting with ":" (comments) or unknown fields are ignored
      }
    }
  } finally {
    // cancel() aborts the underlying connection and releases the lock.
    reader.cancel().catch(() => {});
  }

  // Flush a final event that arrived without a trailing blank line (the stream
  // can close right after an `error`/`done` event) — otherwise the error would
  // be swallowed and reported as a normal completion.
  if (!finished) {
    const dispatched = (eventName || dataLines.length > 0) ? dispatch() : false;
    if (!dispatched) onDone();
  }
}

export async function saveApiKey(provider: string, apiKey: string, model?: string) {
  const res = await fetch(`${AI_BASE}/keys`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ provider, apiKey, model: model ?? null }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(body || `Failed to save key: ${res.status}`);
  }
  return res.json();
}

export async function listApiKeys() {
  const res = await fetch(`${AI_BASE}/keys`, { credentials: "include" });
  if (!res.ok) throw new Error(`Failed to list keys: ${res.status}`);
  return res.json();
}

export async function fetchProviderModels(provider: string): Promise<string[]> {
  const res = await fetch(`${AI_BASE}/keys/${provider}/models`, { credentials: "include" });
  if (!res.ok) throw new Error(`Failed to fetch models: ${res.status}`);
  return res.json();
}

export async function updateKeyModel(provider: string, model: string) {
  const res = await fetch(`${AI_BASE}/keys/${provider}`, {
    method: "PATCH",
    credentials: "include",
    headers: mutationHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ model }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(body || `Failed to update model: ${res.status}`);
  }
  return res.json();
}

// ── GROW session memory ─────────────────────────────────────────────────────

/**
 * Persist a GROW session summary on the goal ("Save memory" on the end card).
 * Later GROW sessions read it back so the coach continues the thread.
 */
export async function saveSessionMemory(goalId: string, summary: string): Promise<void> {
  const res = await fetch(`${AI_BASE}/goals/${parseInt(goalId, 10)}/memory`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ summary }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(body || `Failed to save session memory: ${res.status}`);
  }
}

// ── Proposals ───────────────────────────────────────────────────────────────

/** A proposal as persisted server-side (status is the source of truth). */
export type ServerProposal = {
  id: number;
  goalId: number | null;
  type: string;
  payload: string; // the propose_goal_change arguments JSON (string)
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
};

/** Fetch pending proposals for a goal (used to restore cards after a reload). */
export async function listGoalProposals(goalId: string): Promise<ServerProposal[]> {
  const res = await fetch(`${AI_BASE}/proposals/goal/${goalId}`, { credentials: "include" });
  if (!res.ok) throw new Error(`Failed to load proposals: ${res.status}`);
  return res.json();
}

/** Mark a proposal approved on the server. Best-effort: caller ignores failures. */
export async function approveProposal(id: number): Promise<void> {
  await fetch(`${AI_BASE}/proposals/${id}/approve`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders(),
  });
}

/** Mark a proposal rejected on the server. Best-effort: caller ignores failures. */
export async function rejectProposal(id: number): Promise<void> {
  await fetch(`${AI_BASE}/proposals/${id}/reject`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders(),
  });
}