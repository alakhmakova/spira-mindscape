import { afterEach, describe, expect, it, vi } from "vitest";

import {
  approveProposal,
  createCvApplication,
  deleteApiKey,
  fetchCvApplications,
  fetchCvTranscript,
  getTranscriptRevision,
  listApiKeys,
  putCvTranscript,
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

  it("deleteApiKey DELETEs the provider's key with credentials and the CSRF header", async () => {
    // The endpoint shipped with BYOK and nothing ever called it, so a key could be
    // replaced but never removed (owner, 2026-09-09).
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 200, text: async () => "" })),
    );

    await deleteApiKey("MISTRAL");

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/keys/MISTRAL");
    expect(init.method).toBe("DELETE");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("test-csrf-token");
  });

  it("deleteApiKey reports a failure rather than pretending the key is gone", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: false,
        status: 400,
        text: async () => JSON.stringify({ detail: "Unknown provider" }),
        json: async () => ({ detail: "Unknown provider" }),
      })),
    );

    await expect(deleteApiKey("MISTRALL")).rejects.toThrow();
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

  /**
   * A refused request carries a reason written to be read, and this path used to throw it away
   * and report "Server error: 400" — which is how a pasted job advert (over the message cap)
   * reached the owner as "no provider works even with keys" on 2026-09-08.
   */
  it("streamChat surfaces the server's reason for a 4xx", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: false,
        status: 400,
        json: async () => ({
          detail: "That message is too long — keep it under 50000 characters.",
        }),
      })),
    );

    const errors: string[] = [];
    await streamChat({
      message: "x",
      history: [],
      onToken: () => {},
      onDone: () => {},
      onError: (m) => errors.push(m),
    });

    expect(errors).toEqual([
      "That message is too long — keep it under 50000 characters.",
    ]);
  });

  it("streamChat keeps the generic line for a 5xx (its detail is an internal reference)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: false,
        status: 500,
        json: async () => ({
          detail: "Something went wrong. Reference: abc123",
        }),
      })),
    );

    const errors: string[] = [];
    await streamChat({
      message: "x",
      history: [],
      onToken: () => {},
      onDone: () => {},
      onError: (m) => errors.push(m),
    });

    expect(errors).toEqual(["Server error: 500"]);
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

describe("CV applications", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("createCvApplication POSTs the advert with credentials and the CSRF header", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson({ id: 12, title: "QA-testare" })),
    );

    await createCvApplication({
      goalId: "7",
      title: "QA-testare — iFacts",
      vacancyUrl: "https://example.com/job",
      vacancyText: "Vi söker en QA-testare",
    });

    const [url, init] = firstCall();
    expect(url).toBe("/api/ai/cv/applications");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("test-csrf-token");
    const body = JSON.parse(
      (init as unknown as { body: string }).body,
    ) as Record<string, unknown>;
    expect(body.goalId).toBe(7);
    expect(body.vacancyText).toBe("Vi söker en QA-testare");
  });

  it("createCvApplication THROWS on failure rather than returning null", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: false, status: 400, json: async () => ({}) })),
    );

    // The caller is about to open a session against this row. A silent null would
    // interview the user against an application that does not exist — the server holds
    // the requirement queue, so with no row there is no queue and every answer is lost.
    await expect(
      createCvApplication({ goalId: "7", vacancyText: "advert" }),
    ).rejects.toThrow();
  });

  it("fetchCvApplications returns an empty list when the call fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("offline");
      }),
    );

    // The list is a convenience on the start card; failing it must not block starting
    // a new application, which is the other half of that card.
    expect(await fetchCvApplications("7")).toEqual([]);
  });

  it("a CV turn sends its session type and the application it belongs to", async () => {
    vi.stubGlobal(
      "fetch",
      // An SSE response that ends immediately: streamChat reads the body, so a null
      // one throws before the assertion is ever reached.
      vi.fn(async () => ({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: async () => ({ done: true, value: undefined }),
            cancel: async () => {},
          }),
        },
        json: async () => ({}),
        text: async () => "",
      })),
    );

    await streamChat({
      goalId: "7",
      message: "Here is the vacancy",
      history: [],
      sessionType: "cv",
      cvApplicationId: 12,
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    });

    const [, init] = firstCall();
    const body = JSON.parse(
      (init as unknown as { body: string }).body,
    ) as Record<string, unknown>;
    expect(body.sessionType).toBe("cv");
    expect(body.cvApplicationId).toBe(12);
    // A CV session has no clock at all — sending timing would tell a piece of work it
    // is running out of time.
    expect(body.sessionTotalMinutes).toBeNull();
    expect(body.sessionRemainingSeconds).toBeNull();
  });
});

describe("the guided CV writer", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function sse(text: string) {
    const bytes = new TextEncoder().encode(text);
    let sent = false;
    return {
      ok: true,
      status: 200,
      body: {
        getReader: () => ({
          read: async () => {
            if (sent) return { done: true, value: undefined };
            sent = true;
            return { done: false, value: bytes };
          },
          cancel: async () => {},
        }),
      },
      json: async () => ({}),
      text: async () => "",
    };
  }

  it("a control turn says which, with the UI language, and step events reach their handlers", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        sse(
          [
            "event: cv_step",
            'data: {"step":2,"of":6,"title":"Job analysis","activity":"Analyzing the job requirements","phase":"analysis"}',
            "",
            "event: cv_continue",
            "data: ",
            "",
            "event: done",
            "data: ",
            "",
            "",
          ].join("\n"),
        ),
      ),
    );
    const steps: string[] = [];
    let continued = false;

    await streamChat({
      goalId: "7",
      message: "[Carry on]",
      history: [],
      sessionType: "cv",
      cvApplicationId: 12,
      cvControl: "continue",
      language: "ru-RU",
      onCvStep: (json) => steps.push(json),
      onCvContinue: () => {
        continued = true;
      },
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    });

    const [, init] = firstCall();
    const body = JSON.parse(
      (init as unknown as { body: string }).body,
    ) as Record<string, unknown>;
    expect(body.cvControl).toBe("continue");
    expect(body.language).toBe("ru-RU");
    expect(JSON.parse(steps[0]).activity).toBe(
      "Analyzing the job requirements",
    );
    expect(continued).toBe(true);
  });

  it("a transcript save names the revision it is based on, and a 409 is reported as a conflict", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: false,
        status: 409,
        json: async () => ({}),
        text: async () => "",
      })),
    );

    const result = await putCvTranscript(12, "[]", 4);

    const [, init] = firstCall();
    expect(
      JSON.parse((init as unknown as { body: string }).body).baseRevision,
    ).toBe(4);
    expect(result).toEqual({ ok: false, conflict: true });
  });

  it("the stored conversation comes back with its revision", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        okJson({ applicationId: 12, content: "[]", revision: 7 }),
      ),
    );

    expect(await fetchCvTranscript(12)).toEqual({ content: "[]", revision: 7 });
  });
});
