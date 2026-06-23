// ── Sandbox postMessage protocol ─────────────────────────────────────────────
// The parent (trusted app) and the sandboxed iframe (runs AI-written render
// code) talk ONLY through these messages. The iframe has an opaque origin, no
// network and no credentials, so it can never touch the backend or the parent
// DOM directly — every data change is a *request* the parent validates and
// performs. See docs/ai-tools-sandbox-plan.md.

/** Tags so each side ignores stray/foreign messages. */
export const HOST_SOURCE = "spira-tool-host";
export const FRAME_SOURCE = "spira-tool-sandbox";

/** A record handed to / from the sandbox: an id plus its decoded data object. */
export type SandboxRecord = { id: number; data: Record<string, unknown> };

/** Parent → iframe. */
export type HostMessage =
  | {
      source: typeof HOST_SOURCE;
      type: "init";
      schema: unknown;
      records: SandboxRecord[];
      code: string;
      theme: "light" | "dark";
    }
  | { source: typeof HOST_SOURCE; type: "records"; records: SandboxRecord[] };

/** iframe → parent. */
export type FrameMessage =
  | { source: typeof FRAME_SOURCE; type: "ready" }
  | { source: typeof FRAME_SOURCE; type: "rendered" }
  | { source: typeof FRAME_SOURCE; type: "resize"; height: number }
  | { source: typeof FRAME_SOURCE; type: "error"; message: string }
  | {
      source: typeof FRAME_SOURCE;
      type: "mutate";
      op: "add";
      data: Record<string, unknown>;
    }
  | {
      source: typeof FRAME_SOURCE;
      type: "mutate";
      op: "edit";
      recordId: number;
      data: Record<string, unknown>;
    }
  | {
      source: typeof FRAME_SOURCE;
      type: "mutate";
      op: "delete";
      recordId: number;
    };

function isObject(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === "object" && !Array.isArray(v);
}

/**
 * Validates an untrusted `event.data` from the iframe into a typed message, or
 * null if it isn't a well-formed sandbox message. The iframe is contained, but
 * we still never trust the *shape* of what it sends.
 */
export function parseFrameMessage(data: unknown): FrameMessage | null {
  if (!isObject(data) || data.source !== FRAME_SOURCE) return null;
  switch (data.type) {
    case "ready":
      return { source: FRAME_SOURCE, type: "ready" };
    case "rendered":
      return { source: FRAME_SOURCE, type: "rendered" };
    case "resize":
      return typeof data.height === "number"
        ? { source: FRAME_SOURCE, type: "resize", height: data.height }
        : null;
    case "error":
      return {
        source: FRAME_SOURCE,
        type: "error",
        message: String(data.message ?? "Unknown error"),
      };
    case "mutate": {
      if (data.op === "add" && isObject(data.data)) {
        return {
          source: FRAME_SOURCE,
          type: "mutate",
          op: "add",
          data: data.data,
        };
      }
      if (
        data.op === "edit" &&
        typeof data.recordId === "number" &&
        isObject(data.data)
      ) {
        return {
          source: FRAME_SOURCE,
          type: "mutate",
          op: "edit",
          recordId: data.recordId,
          data: data.data,
        };
      }
      if (data.op === "delete" && typeof data.recordId === "number") {
        return {
          source: FRAME_SOURCE,
          type: "mutate",
          op: "delete",
          recordId: data.recordId,
        };
      }
      return null;
    }
    default:
      return null;
  }
}
