// Minimal static origin for the sandbox-isolation e2e tests. It serves a blank
// page over real HTTP so the parent has a true origin with cookies — which the
// tests then prove the sandboxed iframe cannot read. Nothing app-specific.
import { createServer } from "node:http";

const port = Number(process.env.PORT) || 4321;

createServer((_req, res) => {
  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
  res.end(
    "<!doctype html><html><head><meta charset='utf-8'><title>sandbox harness</title></head><body></body></html>",
  );
}).listen(port, () => {
  console.log(`sandbox harness server on http://localhost:${port}`);
});
