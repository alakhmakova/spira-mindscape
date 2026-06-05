-- Store the user's Google OAuth refresh token (encrypted at rest) so the backend
-- can mint fresh access tokens for Drive API calls without forcing a re-login.
-- Requires access_type=offline on the authorization request (see SecurityConfig).
ALTER TABLE app_user ADD COLUMN enc_refresh_token TEXT;
