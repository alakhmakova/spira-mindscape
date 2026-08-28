-- Rate-limit token buckets, shared by every instance (BUG-057).
--
-- The limiter used to keep buckets in a ConcurrentHashMap, which made the effective limit
-- "N per minute TIMES the number of live Cloud Run instances" — a number nobody can know,
-- that grows exactly when the limit starts to matter — and lost every counter on the instance
-- recycling Cloud Run does constantly, so the limit could be walked around by waiting.
--
-- One row per (limit name, caller). The caller is a user id when authenticated and a client IP
-- when not, so 200 characters is generous for both; the key is the primary key because every
-- read and write is by exactly that.
CREATE TABLE rate_limit_bucket (
    bucket_key         varchar(200)     PRIMARY KEY,
    -- Fractional on purpose: a bucket refills continuously, so a caller who waited 1.5 seconds
    -- against a 20/minute limit is owed half a token. Rounding that down to zero would make
    -- every limit quietly stricter than it says.
    tokens             double precision NOT NULL,
    -- Epoch millis rather than timestamptz: the whole refill calculation happens inside one
    -- portable SQL statement, and plain integer arithmetic behaves identically on Postgres and
    -- on the H2 the tests run against. Interval arithmetic does not.
    refilled_at_millis bigint           NOT NULL
);

-- Rows are deleted once they have been idle long enough to have refilled to full, at which
-- point they carry no information. Without this the table would grow one row per caller ever
-- seen — and for the unauthenticated endpoints the caller is an IP address, so it would grow
-- on demand from outside. That is the same unbounded growth the in-memory map had.
CREATE INDEX idx_rate_limit_bucket_refilled_at ON rate_limit_bucket (refilled_at_millis);
