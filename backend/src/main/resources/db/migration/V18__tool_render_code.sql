-- ── Sandboxed AI-written renderers (Phase 2) ────────────────────────────────
-- Optional per-tool render code. When present, the tool is drawn by this
-- AI-written module inside an isolated, opaque-origin sandbox iframe (no
-- cookies, no parent DOM, no network) instead of the schema-driven renderer.
-- Stored as inert TEXT — NEVER executed in the parent app, only in the sandbox.
-- The schema stays authoritative for typing/validating records.
-- See docs/ai-tools-sandbox-plan.md.

ALTER TABLE tool_definitions
    ADD COLUMN render_code TEXT;
