import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SpiraApiError } from "./spira/api";
import { __resetLoggerState, logger } from "./logger";

/**
 * The logger has two jobs that are easy to get wrong and impossible to notice in
 * production: it must not send anything the user typed, and it must not flood.
 */
describe("logger", () => {
  let sendBeacon: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    __resetLoggerState();
    sendBeacon = vi.fn(() => true);
    vi.stubGlobal("navigator", { sendBeacon });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(null, { status: 204 })),
    );
    vi.stubGlobal("location", {
      href: "https://spira.app/goals?q=secret+search",
    });
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.spyOn(console, "warn").mockImplementation(() => {});
    vi.spyOn(console, "debug").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  /** The logger branches on import.meta.env.DEV, which Vitest sets true by default. */
  function inProduction() {
    vi.stubEnv("DEV", false);
  }

  async function lastPayload() {
    const blob = sendBeacon.mock.calls[0][1] as Blob;
    return JSON.parse(await blob.text());
  }

  describe("in development", () => {
    it("logs to the console and sends nothing to the server", () => {
      logger.reportError(new Error("boom"), { kind: "render" });
      expect(console.error).toHaveBeenCalled();
      expect(sendBeacon).not.toHaveBeenCalled();
    });

    it("surfaces SpiraApiError.details, which is invisible everywhere else", () => {
      const error = new SpiraApiError("Sync failed", {
        details: "Goal title must not be blank",
      });
      logger.reportError(error, { kind: "api" });
      expect(console.error).toHaveBeenCalledWith(
        expect.any(String),
        error,
        expect.objectContaining({ details: "Goal title must not be blank" }),
      );
    });
  });

  describe("in production", () => {
    beforeEach(inProduction);

    it("sends one report with the expected shape", async () => {
      logger.reportError(new Error("boom"), { kind: "window-error" });
      expect(sendBeacon).toHaveBeenCalledTimes(1);
      const payload = await lastPayload();
      expect(payload).toMatchObject({
        kind: "window-error",
        name: "Error",
        message: "boom",
      });
      expect(payload.stack).toContain("Error");
    });

    it("strips the query string from the reported url", async () => {
      // A query string can carry a search term or an id the user typed.
      logger.reportError(new Error("boom"), { kind: "render" });
      const payload = await lastPayload();
      expect(payload.url).toBe("https://spira.app/goals");
      expect(payload.url).not.toContain("secret");
    });

    it("never sends SpiraApiError.details or raw GraphQL messages", async () => {
      // details joins the backend's error messages, which can echo submitted field
      // values — the one thing that must stay out of the report.
      logger.reportError(
        new SpiraApiError("Sync failed", {
          details: 'Title "my private goal" is too long',
          status: 500,
          errors: [
            {
              message: 'Title "my private goal" is too long',
              extensions: { classification: "INTERNAL_ERROR" },
            },
          ],
        }),
        { kind: "api" },
      );
      const payload = await lastPayload();
      expect(JSON.stringify(payload)).not.toContain("my private goal");
      expect(payload.message).toContain("status=500");
      expect(payload.message).toContain("classification=INTERNAL_ERROR");
    });

    it("truncates over-long fields to the server's caps", async () => {
      const error = new Error("m".repeat(500));
      error.stack = "s".repeat(6000);
      logger.reportError(error, { kind: "render" });
      const payload = await lastPayload();
      expect(payload.message).toHaveLength(300);
      expect(payload.stack).toHaveLength(4000);
    });

    it("sends the same error only once", () => {
      // A render loop would otherwise fire a beacon on every frame.
      const error = new Error("boom");
      logger.reportError(error, { kind: "render" });
      logger.reportError(error, { kind: "render" });
      logger.reportError(error, { kind: "render" });
      expect(sendBeacon).toHaveBeenCalledTimes(1);
    });

    it("caps how many distinct errors one page session sends", () => {
      for (let i = 0; i < 20; i++) {
        logger.reportError(new Error(`boom ${i}`), { kind: "render" });
      }
      expect(sendBeacon).toHaveBeenCalledTimes(5);
    });

    it("does not report a 401 — the session simply expired", () => {
      logger.reportError(new SpiraApiError("Sync failed", { status: 401 }), {
        kind: "api",
      });
      expect(sendBeacon).not.toHaveBeenCalled();
    });

    it("does not report a network failure — being offline is not a defect", () => {
      logger.reportError(
        new SpiraApiError("Unreachable", { kind: "network" }),
        { kind: "api" },
      );
      expect(sendBeacon).not.toHaveBeenCalled();
    });

    it("falls back to keepalive fetch when sendBeacon refuses the payload", () => {
      sendBeacon.mockReturnValue(false);
      logger.reportError(new Error("boom"), { kind: "render" });
      expect(fetch).toHaveBeenCalledWith(
        "/api/client-errors",
        expect.objectContaining({ method: "POST", keepalive: true }),
      );
    });

    it("never throws, and still falls back to fetch, when sendBeacon throws", () => {
      // An extension can make sendBeacon throw rather than return false. Reporting must
      // not be able to cause an error — and the report must still get out.
      sendBeacon.mockImplementation(() => {
        throw new Error("blocked by extension");
      });
      expect(() =>
        logger.reportError(new Error("boom"), { kind: "render" }),
      ).not.toThrow();
      expect(fetch).toHaveBeenCalledWith(
        "/api/client-errors",
        expect.objectContaining({ method: "POST", keepalive: true }),
      );
    });

    it("accepts a non-Error value without throwing", () => {
      // An unhandledrejection can carry literally anything as its reason.
      expect(() =>
        logger.reportError("just a string", { kind: "unhandled-rejection" }),
      ).not.toThrow();
      expect(sendBeacon).toHaveBeenCalledTimes(1);
    });

    it("keeps warnings local — they are not incidents", () => {
      logger.warn("clipboard failed", new Error("nope"));
      expect(sendBeacon).not.toHaveBeenCalled();
    });
  });
});
