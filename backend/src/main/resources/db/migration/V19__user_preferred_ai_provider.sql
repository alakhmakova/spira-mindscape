-- ── Preferred AI provider ────────────────────────────────────────────────────
-- The chat provider the user last selected, so the choice follows them across
-- devices (BUG-018 follow-up). The per-provider MODEL is already server-side on
-- ai_api_keys; only the "which provider is active" selection was device-local.
ALTER TABLE app_user ADD COLUMN preferred_ai_provider VARCHAR(32);
