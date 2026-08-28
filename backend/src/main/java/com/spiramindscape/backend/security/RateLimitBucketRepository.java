package com.spiramindscape.backend.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three statements the shared limiter needs. All native, all portable between Postgres and
 * the H2 the tests run against — only {@code LEAST} and integer arithmetic, no interval maths
 * and no {@code ON CONFLICT}, which H2 does not implement in the form Postgres does.
 *
 * <p><b>The transactions live here, not in {@link SharedRateLimitStore}.</b> Spring's
 * {@code @Transactional} is applied by a proxy, so it does nothing on a method a bean calls on
 * itself — and the store's calls are all internal. Annotating the repository puts the boundary
 * where the call actually crosses a proxy. It also gives each statement its own short
 * transaction, which is what the duplicate-key retry below needs: a violation marks its own
 * transaction rollback-only, and the retry has to run on a fresh one.
 */
public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucket, String> {

    /**
     * Refills the bucket for the time that has passed and takes one token, in <b>one</b>
     * statement — the refill and the spend cannot be separated, or two instances could each
     * read "1 token left" and each spend it.
     *
     * <p>The same {@code LEAST(...)} expression appears twice on purpose: once in {@code SET}
     * to write the new balance and once in {@code WHERE} to decide whether there is anything
     * to take. SQL has no way to name it once here, and a stored intermediate would need a
     * second round trip.
     *
     * <p><b>The {@code CAST}s are load-bearing, not decoration.</b> A bare {@code :tokensPerMilli}
     * next to a {@code bigint} lets the database infer the placeholder's type from its
     * neighbour: H2 read the rate as an integer, so {@code 30063 * 0.001} came out as
     * <b>0</b> and no bucket ever refilled — while Postgres would very likely have got it
     * right, which is the worse half of the trap, since the two would then disagree between
     * the tests and production. The cast states the type instead of leaving it to be guessed.
     *
     * @return 1 when a token was taken, 0 when the bucket is empty <b>or does not exist yet</b>
     *         — the caller tells those apart by trying {@link #insertBucket}
     */
    @Modifying
    @Query(value = """
            UPDATE rate_limit_bucket
               SET tokens = LEAST(CAST(:capacity AS DOUBLE PRECISION),
                                  tokens + (:nowMillis - refilled_at_millis)
                                           * CAST(:tokensPerMilli AS DOUBLE PRECISION)) - 1,
                   refilled_at_millis = :nowMillis
             WHERE bucket_key = :key
               AND LEAST(CAST(:capacity AS DOUBLE PRECISION),
                         tokens + (:nowMillis - refilled_at_millis)
                                  * CAST(:tokensPerMilli AS DOUBLE PRECISION)) >= 1
            """, nativeQuery = true)
    @Transactional
    int refillAndConsume(
            @Param("key") String key,
            @Param("capacity") double capacity,
            @Param("tokensPerMilli") double tokensPerMilli,
            @Param("nowMillis") long nowMillis);

    /**
     * Creates a bucket that has already had its first token taken.
     *
     * <p>A plain {@code INSERT} rather than {@code save()}: with an assigned id, Spring Data
     * merges, which is a {@code SELECT} followed by an {@code UPDATE} and would silently
     * overwrite a bucket another instance created a millisecond earlier. Here a race throws a
     * duplicate-key violation, which is the honest answer and something the caller can act on.
     */
    @Modifying
    @Query(value = """
            INSERT INTO rate_limit_bucket (bucket_key, tokens, refilled_at_millis)
            VALUES (:key, :tokens, :nowMillis)
            """, nativeQuery = true)
    @Transactional
    int insertBucket(
            @Param("key") String key,
            @Param("tokens") double tokens,
            @Param("nowMillis") long nowMillis);

    /**
     * Drops buckets untouched since {@code cutoffMillis}. Anything idle for longer than its own
     * refill window is back at full capacity and therefore says nothing that a missing row does
     * not say, so deleting it changes no decision.
     */
    @Modifying
    @Query(value = "DELETE FROM rate_limit_bucket WHERE refilled_at_millis < :cutoffMillis",
            nativeQuery = true)
    @Transactional
    int purgeIdleSince(@Param("cutoffMillis") long cutoffMillis);
}
