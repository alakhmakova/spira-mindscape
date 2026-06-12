-- ── GROW coaching library ────────────────────────────────────────────────────
-- Chunks of the coaching books that ground GROW sessions. Embeddings are
-- computed lazily with the user's Mistral key (BYOK — no key at startup),
-- so the column is nullable until the first GROW session fills it.
-- Requires the pgvector extension (image: pgvector/pgvector:pg16).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE book_chunk (
    id              BIGSERIAL    PRIMARY KEY,
    book            VARCHAR(128) NOT NULL,
    ord             INT          NOT NULL,
    content         TEXT         NOT NULL,
    embedding       vector(1024),
    embedding_model VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_book_chunk_book_ord UNIQUE (book, ord)
);

-- No vector index: the corpus is ~1k rows, where an exact sequential scan is
-- sub-millisecond and lossless. Revisit (HNSW) only if it grows past ~50k.
