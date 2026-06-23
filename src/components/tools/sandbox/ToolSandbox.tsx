import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import {
  addRecord,
  deleteRecord,
  listRecords,
  parseSchema,
  updateRecord,
  type Tool,
  type ToolRecord,
} from "@/lib/spira/tools-api";
import { HOST_SOURCE, parseFrameMessage, type SandboxRecord } from "./protocol";
import { buildSrcdoc } from "./runtime";
import { DEFAULT_RENDER_CODE } from "./default-render";

/** If a tool's render code doesn't paint anything within this window, we assume
 *  it hung (e.g. an infinite loop) and tear the frame down. */
const WATCHDOG_MS = 6000;

/**
 * Renders a Personal Tool through AI-written code inside an isolated,
 * opaque-origin sandbox iframe (see docs/ai-tools-sandbox-plan.md). The iframe
 * has no cookies, no parent DOM and no network; every data change comes back as
 * a `mutate` request that THIS component validates server-side via the normal
 * tools API. Falls back to {@link DEFAULT_RENDER_CODE} when a tool has no code.
 */
export function ToolSandbox({
  tool,
  code,
  preview = false,
}: {
  tool: Tool;
  code?: string;
  /** Preview (proposal card): render the code with no data and no writes. */
  preview?: boolean;
}) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const readyRef = useRef(false);
  const recordsRef = useRef<SandboxRecord[]>([]);
  const watchdogRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [height, setHeight] = useState(160);
  const [failed, setFailed] = useState(false);
  const srcdoc = useMemo(() => buildSrcdoc(), []);
  const renderCode = code ?? DEFAULT_RENDER_CODE;

  // Only keys the schema defines may be written (drops stale/forged keys before
  // the server validates them).
  const schemaKeys = useMemo(() => {
    const s = parseSchema(tool.schemaJson);
    return new Set((s?.columns ?? []).map((c) => c.key));
  }, [tool.schemaJson]);
  const project = useCallback(
    (data: Record<string, unknown>): Record<string, unknown> => {
      const out: Record<string, unknown> = {};
      for (const k of Object.keys(data))
        if (schemaKeys.has(k)) out[k] = data[k];
      return out;
    },
    [schemaKeys],
  );

  const post = useCallback((msg: Record<string, unknown>) => {
    iframeRef.current?.contentWindow?.postMessage(
      { source: HOST_SOURCE, ...msg },
      "*", // the iframe is opaque-origin; "*" targets only our own frame
    );
  }, []);

  const toSandbox = (rows: ToolRecord[]): SandboxRecord[] =>
    rows.map((r) => ({ id: r.id, data: safeParse(r.dataJson) }));

  const pushRecords = useCallback(
    (rows: ToolRecord[]) => {
      recordsRef.current = toSandbox(rows);
      if (readyRef.current)
        post({ type: "records", records: recordsRef.current });
    },
    [post],
  );

  // Load the tool's records once (skipped in preview — no real tool/data yet).
  useEffect(() => {
    if (preview) return;
    let cancelled = false;
    listRecords(tool.id)
      .then((rows) => !cancelled && pushRecords(rows))
      .catch(
        () => !cancelled && toast.error("Couldn't load this tool's data."),
      );
    return () => {
      cancelled = true;
    };
  }, [tool.id, preview, pushRecords]);

  // Apply a mutate request from the sandbox via the server-validated API, then
  // refresh and push the canonical rows back into the frame.
  const applyMutate = useCallback(
    async (msg: ReturnType<typeof parseFrameMessage>) => {
      if (preview || !msg || msg.type !== "mutate") return;
      try {
        if (msg.op === "add") await addRecord(tool.id, project(msg.data));
        else if (msg.op === "edit")
          await updateRecord(tool.id, msg.recordId, project(msg.data));
        else if (msg.op === "delete") await deleteRecord(tool.id, msg.recordId);
        const fresh = await listRecords(tool.id);
        pushRecords(fresh);
      } catch (e) {
        toast.error(
          e instanceof Error ? e.message : "Couldn't save the change.",
        );
      }
    },
    [tool.id, preview, project, pushRecords],
  );

  useEffect(() => {
    const clearWatchdog = () => {
      if (watchdogRef.current) clearTimeout(watchdogRef.current);
      watchdogRef.current = null;
    };
    const onMessage = (ev: MessageEvent) => {
      // Only trust messages from OUR iframe, and only well-formed ones.
      if (ev.source !== iframeRef.current?.contentWindow) return;
      const msg = parseFrameMessage(ev.data);
      if (!msg) return;
      if (msg.type === "ready") {
        readyRef.current = true;
        post({
          type: "init",
          schema: parseSchema(tool.schemaJson),
          records: recordsRef.current,
          code: renderCode,
          theme: document.documentElement.classList.contains("dark")
            ? "dark"
            : "light",
        });
        // Watchdog: a module that hangs (e.g. an infinite loop) never reports
        // back. If nothing renders within the timeout, tear the frame down and
        // show a fallback instead of leaving a dead/blank window.
        clearWatchdog();
        watchdogRef.current = setTimeout(() => setFailed(true), WATCHDOG_MS);
      } else if (msg.type === "rendered") {
        clearWatchdog(); // the module responded — it isn't hung
      } else if (msg.type === "resize") {
        setHeight(Math.max(80, Math.min(2000, Math.ceil(msg.height) + 8)));
      } else if (msg.type === "error") {
        clearWatchdog(); // responsive, just a render error
        toast.error(`Tool error: ${msg.message}`);
      } else if (msg.type === "mutate") {
        void applyMutate(msg);
      }
    };
    window.addEventListener("message", onMessage);
    return () => {
      window.removeEventListener("message", onMessage);
      clearWatchdog();
    };
  }, [tool.schemaJson, renderCode, post, applyMutate]);

  if (failed) {
    return (
      <p role="alert" className="text-sm text-destructive">
        This tool’s custom layout didn’t load (it may be too slow or broken).
        Ask the AI to fix or simplify it.
      </p>
    );
  }

  return (
    <iframe
      ref={iframeRef}
      title={tool.name}
      // Opaque origin: scripts run, but NO allow-same-origin → no cookies, no
      // parent DOM, no credentialed network. This is the security boundary.
      sandbox="allow-scripts"
      srcDoc={srcdoc}
      className="w-full border-0"
      style={{ height }}
    />
  );
}

function safeParse(json: string): Record<string, unknown> {
  try {
    const v = JSON.parse(json);
    return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}
