import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import type { Tool } from "@/lib/spira/tools-api";
import { ToolSandbox } from "./ToolSandbox";
import { HOST_SOURCE, FRAME_SOURCE } from "./protocol";

// Mock the API: keep the real parseSchema, stub the network calls so we can
// assert the parent's data round-trip (frame asks → parent validates+persists).
vi.mock("@/lib/spira/tools-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/spira/tools-api")>();
  return {
    ...actual,
    listRecords: vi.fn().mockResolvedValue([]),
    addRecord: vi.fn().mockResolvedValue({}),
    updateRecord: vi.fn().mockResolvedValue({}),
    deleteRecord: vi.fn().mockResolvedValue({}),
  };
});

const toastError = vi.fn();
vi.mock("sonner", () => ({ toast: { error: (m: string) => toastError(m) } }));

import { listRecords, addRecord, deleteRecord } from "@/lib/spira/tools-api";

const tool: Tool = {
  id: 42,
  goalId: null,
  name: "Custom",
  schemaJson: JSON.stringify({
    layout: "table",
    columns: [{ key: "a", primitive: "text" }],
  }),
  placement: "tools",
  createdBy: "ai",
  renderCode: "ctx.root.textContent = 'hi';",
  createdAt: "2026-06-23T00:00:00Z",
};

/** Dispatch a message as if it came FROM the sandbox iframe (sets ev.source so
 *  the component's `ev.source === iframe.contentWindow` check passes). */
function fromFrame(iframe: HTMLIFrameElement, data: unknown) {
  const ev = new MessageEvent("message", { data });
  Object.defineProperty(ev, "source", { value: iframe.contentWindow });
  window.dispatchEvent(ev);
}

function mountSandbox(code?: string, preview = false) {
  const { container } = render(
    <ToolSandbox
      tool={tool}
      code={code ?? tool.renderCode!}
      preview={preview}
    />,
  );
  const iframe = container.querySelector("iframe") as HTMLIFrameElement;
  const postSpy = vi.spyOn(iframe.contentWindow!, "postMessage");
  return { iframe, postSpy };
}

beforeEach(() => {
  vi.clearAllMocks();
  (listRecords as ReturnType<typeof vi.fn>).mockResolvedValue([]);
});

describe("ToolSandbox", () => {
  it("loads records and initialises the frame when it reports ready", async () => {
    const { iframe, postSpy } = mountSandbox();
    await waitFor(() => expect(listRecords).toHaveBeenCalledWith(42));
    fromFrame(iframe, { source: FRAME_SOURCE, type: "ready" });
    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith(
        expect.objectContaining({ source: HOST_SOURCE, type: "init" }),
        "*",
      ),
    );
  });

  it("an add request is validated server-side (projected to schema keys) and the fresh rows are pushed back", async () => {
    const { iframe, postSpy } = mountSandbox();
    // The frame must be ready before the parent pushes records back to it.
    fromFrame(iframe, { source: FRAME_SOURCE, type: "ready" });
    fromFrame(iframe, {
      source: FRAME_SOURCE,
      type: "mutate",
      op: "add",
      data: { a: "x", evil: "drop-me" },
    });
    // Only the schema key 'a' reaches the API — 'evil' is stripped.
    await waitFor(() => expect(addRecord).toHaveBeenCalledWith(42, { a: "x" }));
    // After saving, the parent re-reads and pushes records into the frame.
    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith(
        expect.objectContaining({ source: HOST_SOURCE, type: "records" }),
        "*",
      ),
    );
  });

  it("a delete request goes through the API", async () => {
    const { iframe } = mountSandbox();
    fromFrame(iframe, {
      source: FRAME_SOURCE,
      type: "mutate",
      op: "delete",
      recordId: 7,
    });
    await waitFor(() => expect(deleteRecord).toHaveBeenCalledWith(42, 7));
  });

  it("surfaces a render error as a toast", async () => {
    const { iframe } = mountSandbox();
    fromFrame(iframe, { source: FRAME_SOURCE, type: "error", message: "boom" });
    await waitFor(() => expect(toastError).toHaveBeenCalled());
  });

  it("ignores messages that aren't from its own iframe", async () => {
    mountSandbox();
    // A message with no matching source must be ignored (no API call).
    window.dispatchEvent(
      new MessageEvent("message", {
        data: {
          source: FRAME_SOURCE,
          type: "mutate",
          op: "delete",
          recordId: 1,
        },
      }),
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(deleteRecord).not.toHaveBeenCalled();
  });

  it("does not load records in preview mode", async () => {
    mountSandbox("ctx.root.textContent='x';", true);
    await new Promise((r) => setTimeout(r, 0));
    expect(listRecords).not.toHaveBeenCalled();
  });
});
