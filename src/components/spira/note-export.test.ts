import { afterEach, describe, expect, it, vi } from "vitest";

import { postGoogleDoc } from "./note-export";

// Mock the CSRF helper so the test runs in the Node environment (no document).
vi.mock("@/lib/spira/auth", () => ({
  getCsrfToken: () => "csrf-xyz",
}));

type FetchInit = {
  method?: string;
  credentials?: string;
  headers?: Record<string, string>;
  body?: string;
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

describe("postGoogleDoc (note ↔ Google Doc linking)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("open/create POSTs to the resource-scoped endpoint with credentials + CSRF", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        okJson({ webViewLink: "https://docs.google.com/document/d/abc/edit" }),
      ),
    );

    const link = await postGoogleDoc("42", "My Note", "<p>hi</p>", false);

    const [url, init] = firstCall();
    expect(url).toBe("/api/notes/42/google-doc");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("csrf-xyz");
    const sent = JSON.parse(init.body ?? "{}") as {
      title: string;
      html: string;
    };
    expect(sent.title).toBe("My Note");
    expect(sent.html).toBe("<p>hi</p>");
    expect(link).toBe("https://docs.google.com/document/d/abc/edit");
  });

  it("sync POSTs to the /sync endpoint (push note → doc)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        okJson({ webViewLink: "https://docs.google.com/document/d/abc/edit" }),
      ),
    );

    await postGoogleDoc("42", "My Note", "<p>hi</p>", true);

    const [url, init] = firstCall();
    expect(url).toBe("/api/notes/42/google-doc/sync");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("csrf-xyz");
  });

  it("throws with the server message when the backend rejects the request", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: false,
        status: 428,
        json: async () => ({}),
        text: async () => "Google Drive access not granted",
      })),
    );

    await expect(postGoogleDoc("42", "t", "<p>x</p>", false)).rejects.toThrow(
      "Google Drive access not granted",
    );
  });
});
