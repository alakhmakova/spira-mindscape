package com.spiramindscape.backend.security;

/**
 * Where {@link RateLimitFilter} keeps its counters.
 *
 * <p>An interface with one production implementation, for one reason: it lets
 * {@code RateLimitFilterTest} check the filter's own job — which paths are limited, how a
 * caller is identified, what a blocked request looks like — without a database, while
 * {@code SharedRateLimitStoreIntegrationTest} checks the counting against a real one. Those are
 * two different questions and were previously answered by one class that could only be tested
 * on the first.
 */
public interface RateLimitStore {

    /**
     * Takes one token from {@code key}'s bucket, refilling it for elapsed time first.
     *
     * @param key       {@code "<limit name>:<caller>"}
     * @param perMinute the bucket's capacity, and its refill rate per minute
     * @return {@code true} when the request may proceed
     */
    boolean tryConsume(String key, int perMinute);
}
