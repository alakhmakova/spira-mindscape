import { HOST_SOURCE, FRAME_SOURCE } from "./protocol";

// ── Sandbox iframe runtime ───────────────────────────────────────────────────
// `buildSrcdoc()` returns the FULL html for the sandboxed iframe. It contains a
// strict CSP and a small TRUSTED bootstrap (written by us). The bootstrap:
//   • receives `init` (schema, records, code, theme) from the parent,
//   • compiles the AI-written render code (a function body using `ctx`),
//   • runs it to draw into #root, re-running on every `records` update,
//   • posts `rendered` after each successful draw (so the parent can detect a
//     module that hangs and never responds),
//   • bridges all data changes back to the parent as `mutate` requests.
//
// The iframe is mounted with sandbox="allow-scripts" and WITHOUT
// allow-same-origin, so it runs on an opaque origin: no cookies, no parent DOM,
// and (with this CSP) no network. `'unsafe-eval'` only lets the bootstrap run
// the render code — which is the whole point and is fully contained here.

const BOOTSTRAP = `
(function () {
  var ctx = null;
  var render = null;
  var rafPending = false;

  function post(msg) {
    msg.source = "${FRAME_SOURCE}";
    parent.postMessage(msg, "*");
  }
  function fail(e) {
    post({ type: "error", message: String((e && e.message) || e) });
  }
  // Coalesce resize reports to one per frame (a busy module can resize a lot).
  function reportSize() {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(function () {
      rafPending = false;
      post({ type: "resize", height: document.documentElement.scrollHeight });
    });
  }

  function applyTheme(theme) {
    var dark = theme === "dark";
    var root = document.documentElement;
    root.style.colorScheme = dark ? "dark" : "light";
    var v = dark
      ? { fg: "#e8eeec", muted: "#9bb0ab", border: "#2b3a37", accent: "#4db6ac", bg: "#0f1715" }
      : { fg: "#0b1f1d", muted: "#5b6b67", border: "#e3e6e3", accent: "#006d67", bg: "#ffffff" };
    Object.keys(v).forEach(function (k) { root.style.setProperty("--" + k, v[k]); });
  }

  function makeApi() {
    return {
      schema: ctx.schema,
      records: ctx.records.slice(),
      theme: ctx.theme,
      root: document.getElementById("root"),
      addRow: function (data) { post({ type: "mutate", op: "add", data: data || {} }); },
      editRow: function (id, data) { post({ type: "mutate", op: "edit", recordId: id, data: data || {} }); },
      deleteRow: function (id) { post({ type: "mutate", op: "delete", recordId: id }); }
    };
  }

  function draw() {
    var root = document.getElementById("root");
    if (!render || !root) return;
    try {
      root.innerHTML = "";
      render(makeApi());
      post({ type: "rendered" });
      reportSize();
    } catch (e) { fail(e); }
  }

  window.addEventListener("message", function (ev) {
    if (ev.source !== parent) return;
    var m = ev.data;
    if (!m || m.source !== "${HOST_SOURCE}") return;
    if (m.type === "init") {
      ctx = {
        schema: m.schema,
        records: m.records || [],
        theme: m.theme === "dark" ? "dark" : "light"
      };
      applyTheme(ctx.theme);
      try { render = new Function("ctx", m.code); }
      catch (e) { fail(e); return; }
      draw();
    } else if (m.type === "records") {
      if (!ctx) return;
      ctx.records = m.records || [];
      draw();
    }
  });

  // Keep the parent's iframe height in sync with content.
  if (window.ResizeObserver) {
    new ResizeObserver(reportSize).observe(document.documentElement);
  }

  post({ type: "ready" });
})();
`;

/** The static HTML document for the sandbox iframe (same for every tool). */
export function buildSrcdoc(): string {
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline' 'unsafe-eval'; img-src data:;">
<style>
  :root {
    color-scheme: light;
    --fg: #0b1f1d; --muted: #5b6b67; --border: #e3e6e3; --accent: #006d67; --bg: #ffffff;
  }
  html, body { margin: 0; }
  body {
    font: 14px/1.5 system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
    color: var(--fg); background: var(--bg); padding: 4px;
  }
  #root { min-height: 1px; }
</style>
</head>
<body>
<div id="root"></div>
<script>${BOOTSTRAP}</script>
</body>
</html>`;
}
