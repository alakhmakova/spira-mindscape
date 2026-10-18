-- V43: the server's own Web Push (VAPID) key pair, kept in the database.
--
-- The pair used to come only from the environment (the private key from Secret Manager), so a
-- deployment nobody had configured had push switched off — and turning it on was a manual step in
-- Google Cloud. Now the first start creates a pair and stores it here; every instance and every
-- later start reads the same row. One row only: a browser subscribed under one public key stops
-- receiving pushes when it changes. SPIRA_WEBPUSH_* in the environment still win when set.
--
-- The private key is stored ENCRYPTED (AES-256-GCM, the same EncryptionService and key as the AI
-- provider keys), so a database dump or a backup alone does not hand it over. See
-- docs/security-model.md section 8.
CREATE TABLE web_push_vapid_key (
    id          SMALLINT     PRIMARY KEY CHECK (id = 1),
    public_key  VARCHAR(128) NOT NULL,
    private_key VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
