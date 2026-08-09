import { afterEach, describe, expect, it, vi } from "vitest";

import {
  approveProposal,
  getTranscriptRevision,
  listApiKeys,
  saveApiKey,
  streamChat,
} from "./ai-api";

// The AI client must echo Spring Security's CSRF token on mutations. Mock the
// shared helper so the test does not depend on a browser `document.cookie`.
vi.mock("../../lib/spira/auth", () => ({
  getCsrfToken: () => "test-csrf-token",
}));

type FetchInit = {
  method?: string;
  credentials?: string;
  headers?: Record<string, string>;
};

function okJson(body: unknown) {
  return {
    ok: true,
    status: 200,
    json: async () => body,
    text: async () => "",
  };
}

function firstCall(): [string, FetchInit] {
  const mock = globalThis.fetch as unknown as ReturnType<typeof vi.fn>;
  return mock.mock.calls[0] as [string, FetchInit];
}

describe("ai-api auth wiring (CSRF + credentials)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("saveApiKey POSTs with credentials and the X-XSRF-TOKEN header", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson({ provider: "MISTRAL" })),
    );

    await saveApiKey("MISTRAL", "sk-test-123456", "mistral-large");

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/keys");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("test-csrf-token");
    expect(init.headers?.["Content-Type"]).toBe("application/json");
  });

  it("approveProposal POSTs with credentials and the CSRF header", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson(null)),
    );

    await approveProposal(5);

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/proposals/5/approve");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("test-csrf-token");
  });

  it("listApiKeys sends credentials on the GET (cookie auth, no CSRF needed)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson([])),
    );

    await listApiKeys();

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/keys");
    expect(init.credentials).toBe("include");
  });

  it("streamChat sends message attachments in the request body", async () => {
    // A minimal SSE response that immediately emits `done` and closes.
    const encoder = new TextEncoder();
    let sent = false;
    const response = {
      ok: true,
      status: 200,
      body: {
        getReader() {
          return {
            read: async () =>
              sent
                ? { done: true, value: undefined }
                : ((sent = true),
                  {
                    done: false,
                    value: encoder.encode("event: done\ndata: \n\n"),
                  }),
            cancel: async () => {},
          };
        },
      },
    };
    const fetchMock = vi.fn(async () => response);
    vi.stubGlobal("fetch", fetchMock);

    const attachments = [
      {
        name: "cv.pdf",
        mime: "application/pdf",
        dataUrl: "data:application/pdf;base64,AAAA",
      },
    ];
    await streamChat({
      message: "read this",
      history: [],
      attachments,
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    });

    const [url, init] = fetchMock.mock.calls[0] as unknown as [
      string,
      FetchInit & { body: string },
    ];
    expect(url).toBe("/api/ai/chat");
    const body = JSON.parse(init.body) as { attachments: typeof attachments };
    expect(body.attachments).toEqual(attachments);
  });

  it("streamChat omits attachments (null) when none are provided", async () => {
    const encoder = new TextEncoder();
    let sent = false;
    const response = {
      ok: true,
      status: 200,
      body: {
        getReader: () => ({
          read: async () =>
            sent
              ? { done: true, value: undefined }
              : ((sent = true),
                {
                  done: false,
                  value: encoder.encode("event: done\ndata: \n\n"),
                }),
          cancel: async () => {},
        }),
      },
    };
    const fetchMock = vi.fn(async () => response);
    vi.stubGlobal("fetch", fetchMock);

    await streamChat({
      message: "hi",
      history: [],
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    });

    const [, init] = fetchMock.mock.calls[0] as unknown as [
      string,
      FetchInit & { body: string },
    ];
    const body = JSON.parse(init.body) as { attachments: unknown };
    expect(body.attachments).toBeNull();
  });
});

// The chat panel polls while it is open. It must ask for a timestamp, not the conversation —
// fetching the whole transcript just to compare `updatedAt` was a steady drain on the database's
// metered egress (BUG-019).
describe("getTranscriptRevision (the cheap poll target)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("GETs the revision endpoint, not the transcript itself", async () => {
    const fetchMock = vi.fn(async () =>
      okJson({ goalId: 7, updatedAt: "2026-08-09T10:00:00Z" }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const revision = await getTranscriptRevision("7");

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/chat/transcript/revision?goalId=7");
    expect(init.credentials).toBe("include");
    expect(revision).toBe("2026-08-09T10:00:00Z");
  });

  it("omits goalId for the global chat scope", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson({ goalId: null, updatedAt: null })),
    );

    const revision = await getTranscriptRevision();

    const [url] = firstCall();
    expect(url).toBe("/api/ai/chat/transcript/revision");
    // null = "nothing stored for this scope", which is different from "couldn't tell".
    expect(revision).toBeNull();
  });

  it("returns undefined when the check fails, so the caller falls back to fetching", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("offline");
      }),
    );

    // Undefined must not be mistaken for "unchanged" — that would freeze the panel on a
    // stale conversation for as long as the network stayed flaky.
    expect(await getTranscriptRevision("7")).toBeUndefined();
  });
});
