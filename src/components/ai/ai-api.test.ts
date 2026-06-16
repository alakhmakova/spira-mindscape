import { afterEach, describe, expect, it, vi } from "vitest";

import { approveProposal, listApiKeys, saveApiKey } from "./ai-api";

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
});
