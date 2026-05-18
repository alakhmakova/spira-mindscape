import { afterEach, describe, expect, it, vi } from "vitest";

import { SpiraApiError, spiraApi } from "./api";

describe("spiraApi errors", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("hides raw GraphQL internal errors from the public error message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              errors: [
                {
                  message:
                    "INTERNAL_ERROR for 7e901fea-79f0-e437-5be4-80c16f860918",
                  extensions: { classification: "INTERNAL_ERROR" },
                },
              ],
            }),
            {
              status: 200,
              headers: { "content-type": "application/json" },
            },
          ),
      ),
    );

    await expect(spiraApi.fetchGoals()).rejects.toMatchObject({
      name: "SpiraApiError",
      message: "We couldn't sync with the backend. Please try again.",
      details: "INTERNAL_ERROR for 7e901fea-79f0-e437-5be4-80c16f860918",
    });
  });

  it("uses a safe backend-unavailable message for network failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("Failed to fetch");
      }),
    );

    await expect(spiraApi.fetchGoals()).rejects.toBeInstanceOf(SpiraApiError);
    await expect(spiraApi.fetchGoals()).rejects.toMatchObject({
      message:
        "We couldn't reach the backend. Check that it is running, then retry.",
    });
  });

  it("shows backend validation messages to users", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              errors: [
                {
                  message:
                    "Note resource body must be 50000 characters or fewer",
                  extensions: { classification: "ValidationError" },
                },
              ],
            }),
            {
              status: 200,
              headers: { "content-type": "application/json" },
            },
          ),
      ),
    );

    await expect(
      spiraApi.createResource("1", {
        type: "note",
        title: "Research notes",
        body: "too long",
      }),
    ).rejects.toMatchObject({
      name: "SpiraApiError",
      message: "Note resource body must be 50000 characters or fewer",
      details: "Note resource body must be 50000 characters or fewer",
    });
  });

  it("sends explicit nulls when clearing goal dates", async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            data: {
              updateGoal: goalResponse({
                id: "1",
                deadline: null,
                achievedAt: null,
              }),
            },
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await spiraApi.updateGoal("1", {
      title: "Goal",
      deadline: null,
      achievedAt: null,
    });

    const body = JSON.parse(fetchMock.mock.calls[0][1].body as string);
    expect(body.variables.input).toMatchObject({
      title: "Goal",
      deadline: null,
      achievedAt: null,
    });
  });

  it("sends explicit nulls when clearing target dates", async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            data: {
              updateTarget: {
                id: "target-1",
                type: "binary",
                title: "Book session",
                start: null,
                current: null,
                total: null,
                unit: null,
                done: false,
                deadline: null,
                achievedAt: null,
                items: [],
              },
            },
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await spiraApi.updateTarget("target-1", {
      type: "binary",
      title: "Book session",
      done: false,
      deadline: undefined,
      achievedAt: undefined,
    });

    const body = JSON.parse(fetchMock.mock.calls[0][1].body as string);
    expect(body.variables.input).toMatchObject({
      title: "Book session",
      deadline: null,
      achievedAt: null,
    });
  });
});

function goalResponse(
  overrides: Partial<{
    id: string;
    deadline: string | null;
    achievedAt: string | null;
  }> = {},
) {
  return {
    id: overrides.id ?? "goal-1",
    title: "Goal",
    description: "",
    confidence: 5,
    deadline: overrides.deadline,
    createdAt: "2026-05-15T00:00:00.000Z",
    achievedAt: overrides.achievedAt,
    reality: { id: "goal-1", actions: [], obstacles: [] },
    options: [],
    resources: [],
    targets: [],
  };
}
