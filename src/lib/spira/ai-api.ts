/**
 * Client for the Spira AI REST API (/api/ai/*).
 *
 * All requests go through the Vite dev proxy in development (proxied to
 * http://localhost:8080). In production, /api is served from the same origin
 * as the frontend, so no CORS handling is needed.
 *
 * Streaming chat uses native EventSource / fetch + ReadableStream rather than
 * a WebSocket, matching the backend's SseEmitter output format.
 */

const AI_BASE = "/api/ai";

// ── Types ─────────────────────────────────────────────────────────────────────

export type AiProviderType = "ANTHROPIC" | "OPENAI" | "MISTRAL";

export type KeyInfo = {
  provider: AiProviderType;
  hint: string;
  model: string | null;
};

export type ProposalStatus = "PENDING" | "APPROVED" | "REJECTED";

export type AiProposal = {
  id: number;
  goalId: number | null;
  type: string;
  payload: string; // JSON string — parse client-side based on type
  status: ProposalStatus;
  createdAt: string;
};

export type ChatHistoryEntry = {
  role: "user" | "assistant";
  content: string;
};

// ── Key management ────────────────────────────────────────────────────────────

export async function saveAiKey(
  provider: AiProviderType,
  apiKey: string,
  model?: string,
): Promise<KeyInfo> {
  const res = await fetch(`${AI_BASE}/keys`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ provider, apiKey, model }),
  });
  if (!res.ok) throw new Error(`Failed to save key: ${res.status}`);
  return res.json();
}

export async function listAiKeys(): Promise<KeyInfo[]> {
  const res = await fetch(`${AI_BASE}/keys`);
  if (!res.ok) throw new Error(`Failed to list keys: ${res.status}`);
  return res.json();
}

export async function deleteAiKey(provider: AiProviderType): Promise<void> {
  const res = await fetch(`${AI_BASE}/keys/${provider}`, { method: "DELETE" });
  if (!res.ok) throw new Error(`Failed to delete key: ${res.status}`);
}

// ── Streaming chat ────────────────────────────────────────────────────────────

export type ChatStreamCallbacks = {
  onToken: (token: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
};

/**
 * Start a streaming chat request.
 *
 * Returns a cleanup function — call it to abort the stream (e.g., when the
 * component unmounts or the user clicks "Stop").
 *
 * @param goalId  - goal to scope the conversation; null for global chat
 * @param message - the user's message
 * @param history - previous messages in the conversation
 * @param provider - which provider to use (must have a saved key)
 * @param callbacks - token/done/error handlers
 */
export function streamChat(
  goalId: string | null,
  message: string,
  history: ChatHistoryEntry[],
  provider: AiProviderType = "ANTHROPIC",
  callbacks: ChatStreamCallbacks,
): () => void {
  const controller = new AbortController();

  (async () => {
    try {
      const res = await fetch(`${AI_BASE}/chat`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          accept: "text/event-stream",
        },
        body: JSON.stringify({
          goalId: goalId ? Number(goalId) : null,
          message,
          provider,
          history,
        }),
        signal: controller.signal,
      });

      if (!res.ok) {
        const errorText = await res.text();
        callbacks.onError(`Request failed (${res.status}): ${errorText}`);
        return;
      }

      if (!res.body) {
        callbacks.onError("No response body from server");
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let currentEvent = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        // Keep the last (possibly incomplete) line in the buffer
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (line.startsWith("event:")) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            const data = line.slice(5).trim();
            if (currentEvent === "token") {
              callbacks.onToken(data);
            } else if (currentEvent === "done") {
              callbacks.onDone();
              return;
            } else if (currentEvent === "error") {
              callbacks.onError(data || "AI service error");
              return;
            } else if (!currentEvent && data) {
              // Unnamed data event — treat as token (fallback)
              callbacks.onToken(data);
            }
          } else if (line === "") {
            // Empty line resets the current event field (SSE spec)
            currentEvent = "";
          }
        }
      }

      callbacks.onDone();
    } catch (err: unknown) {
      if (err instanceof Error && err.name === "AbortError") return;
      callbacks.onError(
        err instanceof Error ? err.message : "Unknown streaming error",
      );
    }
  })();

  return () => controller.abort();
}

// ── Proposals ─────────────────────────────────────────────────────────────────

export async function listProposals(goalId?: string): Promise<AiProposal[]> {
  const url =
    goalId != null
      ? `${AI_BASE}/proposals/goal/${goalId}`
      : `${AI_BASE}/proposals`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to load proposals: ${res.status}`);
  return res.json();
}

export async function approveProposal(proposalId: number): Promise<AiProposal> {
  const res = await fetch(`${AI_BASE}/proposals/${proposalId}/approve`, {
    method: "POST",
  });
  if (!res.ok) throw new Error(`Failed to approve proposal: ${res.status}`);
  return res.json();
}

export async function rejectProposal(proposalId: number): Promise<AiProposal> {
  const res = await fetch(`${AI_BASE}/proposals/${proposalId}/reject`, {
    method: "POST",
  });
  if (!res.ok) throw new Error(`Failed to reject proposal: ${res.status}`);
  return res.json();
}
