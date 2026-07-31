import { getCsrfToken } from "../../lib/spira/auth";

const AI_BASE = "/api/ai";

/**
 * Headers for a mutating request (POST/PATCH/DELETE). The backend enforces CSRF
 * via the double-submit cookie pattern: echo the readable XSRF-TOKEN cookie in
 * the X-XSRF-TOKEN header, or Spring Security rejects the request with 403.
 */
function mutationHeaders(
  extra?: Record<string, string>,
): Record<string, string> {
  return { "X-XSRF-TOKEN": getCsrfToken(), ...extra };
}

/**
 * Turns a failed `Response` into a short, human-readable message — never the raw
 * body. The backend returns Spring **ProblemDetail** JSON (and Bean-Validation
 * errors) on failure; dumping that JSON into a toast is meaningless to a user, so
 * we pull out a readable field when there is one and otherwise map by status.
 *
 * - Validation errors (`400`/`422`) surface their `detail`/field message (e.g.
 *   "apiKey must be between 8 and 512 characters") — these are already written
 *   for humans.
 * - `401`/`403` → a sign-in / permission hint.
 * - `5xx` → a generic "try again" (the ProblemDetail `detail` for a 500 is just
 *   "Something went wrong… Reference: <uuid>", which we deliberately hide).
 * - A plain-text (non-JSON) body is safe to show as-is.
 */
async function friendlyError(res: Response, fallback: string): Promise<string> {
  let body = "";
  try {
    body = await res.text();
  } catch {
    /* body already consumed / unavailable */
  }

  const trimmed = body.trim();
  if (trimmed) {
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        const j = JSON.parse(trimmed) as {
          detail?: string;
          message?: string;
          errors?: Array<{ defaultMessage?: string }>;
          status?: number;
        };
        const fieldMsg = Array.isArray(j.errors) && j.errors[0]?.defaultMessage;
        // Only trust `detail`/`message` for non-5xx — a 500's detail carries an
        // internal reference id, not something the user can act on.
        const problemMsg = res.status < 500 ? j.detail || j.message : undefined;
        const msg = fieldMsg || problemMsg;
        if (msg) return msg;
      } catch {
        /* not valid JSON — fall through to status mapping */
      }
    } else {
      // Plain-text error body — safe to show directly.
      return trimmed;
    }
  }

  if (res.status === 401 || res.status === 403)
    return "Your session expired. Please sign in again.";
  if (res.status === 400 || res.status === 422) return fallback;
  if (res.status >= 500)
    return "Something went wrong on the server. Please try again.";
  return fallback;
}

export type HistoryEntry = { role: "user" | "assistant"; content: string };

/**
 * A file attached directly to a chat message (BUG-017): an image, PDF, or DOCX.
 * `dataUrl` is a `data:<mime>;base64,…` URL. Ephemeral — sent with this message
 * only, never saved as a resource.
 */
export type ChatAttachment = { name: string; mime: string; dataUrl: string };

export type StreamChatParams = {
  goalId?: string;
  message: string;
  history: HistoryEntry[];
  provider?: string;
  sessionType?: "chat" | "grow";
  /** Files attached directly to this message (images/PDF/DOCX). */
  attachments?: ChatAttachment[];
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
  const {
    goalId,
    message,
    history,
    provider = "ANTHROPIC",
    sessionType = "chat",
    attachments,
    sessionTotalMinutes,
    sessionRemainingSeconds,
    onToken,
    onProposal,
    onStatus,
    onDone,
    onError,
  } = params;

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
        attachments: attachments?.length ? attachments : null,
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
        try {
          text = JSON.parse(data);
        } catch {
          /* fall back to raw */
        }
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
          if (dispatch()) {
            finished = true;
            break;
          }
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
    const dispatched = eventName || dataLines.length > 0 ? dispatch() : false;
    if (!dispatched) onDone();
  }
}

export async function saveApiKey(
  provider: string,
  apiKey: string,
  model?: string,
) {
  const res = await fetch(`${AI_BASE}/keys`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ provider, apiKey, model: model ?? null }),
  });
  if (!res.ok) {
    throw new Error(
      await friendlyError(
        res,
        `Couldn't save the ${provider} key. Please try again.`,
      ),
    );
  }
  return res.json();
}

export async function listApiKeys() {
  const res = await fetch(`${AI_BASE}/keys`, { credentials: "include" });
  if (!res.ok) throw new Error(`Failed to list keys: ${res.status}`);
  return res.json();
}

export async function fetchProviderModels(provider: string): Promise<string[]> {
  const res = await fetch(`${AI_BASE}/keys/${provider}/models`, {
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(
      await friendlyError(
        res,
        `Couldn't load ${provider} models — check the key is valid.`,
      ),
    );
  }
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
    throw new Error(
      await friendlyError(res, "Couldn't change the model. Please try again."),
    );
  }
  return res.json();
}

// ── Provider preference sync (cross-device) ─────────────────────────────────

/** The current user's saved chat provider (null if none chosen). Best-effort. */
export async function getAiProvider(): Promise<string | null> {
  try {
    const res = await fetch(`${AI_BASE}/preferences`, {
      credentials: "include",
    });
    if (!res.ok) return null;
    const body = (await res.json()) as { provider: string | null };
    return body.provider ?? null;
  } catch {
    return null;
  }
}

/** Persist the selected chat provider so it follows the user across devices. */
export async function saveAiProvider(provider: string): Promise<void> {
  try {
    await fetch(`${AI_BASE}/preferences`, {
      method: "PUT",
      credentials: "include",
      headers: mutationHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ provider }),
    });
  } catch {
    /* best-effort — local choice still applies on this device */
  }
}

// ── Chat transcript sync (cross-device) ─────────────────────────────────────

/** A stored transcript for one scope. `content` is the JSON array of messages. */
export type StoredTranscript = {
  goalId: number | null;
  content: string;
  updatedAt: string | null;
};

const transcriptUrl = (goalId?: string) =>
  `${AI_BASE}/chat/transcript${goalId ? `?goalId=${parseInt(goalId, 10)}` : ""}`;

/**
 * Fetch the current user's stored transcript for a scope (a goal, or the global
 * chat when `goalId` is omitted). Best-effort: returns null on any failure so the
 * chat still works offline from its local cache.
 */
export async function getTranscript(
  goalId?: string,
): Promise<StoredTranscript | null> {
  try {
    const res = await fetch(transcriptUrl(goalId), { credentials: "include" });
    if (!res.ok) return null;
    return (await res.json()) as StoredTranscript;
  } catch {
    return null;
  }
}

/**
 * Persist the current user's transcript for a scope (last write wins). `content`
 * is the JSON array of messages (attachment file bytes already stripped).
 * Best-effort: a failure never blocks the chat.
 */
export async function putTranscript(
  goalId: string | undefined,
  content: string,
): Promise<string | null> {
  try {
    const res = await fetch(`${AI_BASE}/chat/transcript`, {
      method: "PUT",
      credentials: "include",
      headers: mutationHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        goalId: goalId ? parseInt(goalId, 10) : null,
        content,
      }),
    });
    if (!res.ok) return null;
    // Return the server's new `updatedAt` so the caller can record it and avoid
    // re-adopting its own write during polling.
    const body = (await res.json()) as StoredTranscript;
    return body.updatedAt ?? null;
  } catch {
    /* best-effort — local cache still holds the transcript */
    return null;
  }
}

/** Clear the current user's stored transcript for a scope ("New chat"). */
export async function deleteTranscript(goalId?: string): Promise<void> {
  try {
    await fetch(transcriptUrl(goalId), {
      method: "DELETE",
      credentials: "include",
      headers: mutationHeaders(),
    });
  } catch {
    /* best-effort */
  }
}

// ── GROW session memory ─────────────────────────────────────────────────────

/**
 * Persist a GROW session summary on the goal ("Save memory" on the end card).
 * Later GROW sessions read it back so the coach continues the thread.
 */
export async function saveSessionMemory(
  goalId: string,
  summary: string,
): Promise<void> {
  const res = await fetch(`${AI_BASE}/goals/${parseInt(goalId, 10)}/memory`, {
    method: "POST",
    credentials: "include",
    headers: mutationHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ summary }),
  });
  if (!res.ok) {
    throw new Error(
      await friendlyError(
        res,
        "Couldn't save the session memory. Please try again.",
      ),
    );
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
export async function listGoalProposals(
  goalId: string,
): Promise<ServerProposal[]> {
  const res = await fetch(`${AI_BASE}/proposals/goal/${goalId}`, {
    credentials: "include",
  });
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
