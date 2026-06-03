import { afterEach, describe, expect, it, vi } from "vitest";

import { createGoogleDocFromHtml } from "./note-export";

// Mock the CSRF helper so the test runs in the Node environment (no document).
vi.mock("@/lib/spira/auth", () => ({
  getCsrfToken: () => "csrf-xyz",
}));

type FetchInit = { method?: string; credentials?: string; headers?: Record<string, string>; body?: string };

function okJson(body: unknown) {
  return { ok: true, status: 200, json: async () => body, text: async () => "" };
}

function firstCall(): [string, FetchInit] {
  const mock = globalThis.fetch as unknown as ReturnType<typeof vi.fn>;
  return mock.mock.calls[0] as [string, FetchInit];
}

describe("createGoogleDocFromHtml", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("POSTs the note with credentials + CSRF header and returns the doc link", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => okJson({ webViewLink: "https://docs.google.com/document/d/abc/edit" })),
    );

    const link = await createGoogleDocFromHtml("My Note", "<p>hi</p>");

    const [url, init] = firstCall();
    expect(url).toBe("/api/notes/google-doc");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("csrf-xyz");
    expect(init.headers?.["Content-Type"]).toBe("application/json");

    const sent = JSON.parse(init.body ?? "{}") as { title: string; html: string };
    expect(sent.title).toBe("My Note");
    expect(sent.html).toBe("<p>hi</p>");

    expect(link).toBe("https://docs.google.com/document/d/abc/edit");
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

    await expect(createGoogleDocFromHtml("t", "<p>x</p>")).rejects.toThrow(
      "Google Drive access not granted",
    );
  });
});
