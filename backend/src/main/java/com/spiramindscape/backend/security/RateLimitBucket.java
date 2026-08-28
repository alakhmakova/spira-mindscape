package com.spiramindscape.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One caller's token bucket for one limit, shared across instances (BUG-057).
 *
 * <p>Every read and write goes through {@link SharedRateLimitStore}'s native statements rather
 * than through this class: taking a token has to be one atomic operation, and load-modify-save
 * through JPA is three. <b>The entity exists so the table does.</b> Flyway is switched off in
 * the test profile and the schema there comes from {@code ddl-auto=create-drop}, so a table
 * declared only in a migration would not exist for the integration tests — which is the
 * opposite of what is wanted for a table whose whole point is that its behaviour is checked
 * against a real database.
 *
 * @see SharedRateLimitStore
 */
@Entity
@Table(name = "rate_limit_bucket")
@Getter
@Setter
public class RateLimitBucket {

    /** {@code "<limit name>:<caller>"} — see {@code RateLimitFilter.callerKey}. */
    @Id
    @Column(name = "bucket_key", length = 200)
    private String bucketKey;

    /**
     * Tokens left, fractional. A bucket refills continuously, so a caller who waited 1.5
     * seconds against a 20-per-minute limit is owed half a token; rounding that down would
     * make every limit quietly stricter than it is documented to be.
     */
    @Column(name = "tokens", nullable = false)
    private double tokens;

    /**
     * When the bucket was last refilled, as epoch millis.
     *
     * <p>Not a timestamp, because the refill is computed inside a single SQL statement and
     * integer arithmetic reads identically on Postgres and on the H2 the tests use, while
     * interval arithmetic does not.
     */
    @Column(name = "refilled_at_millis", nullable = false)
    private long refilledAtMillis;
}
